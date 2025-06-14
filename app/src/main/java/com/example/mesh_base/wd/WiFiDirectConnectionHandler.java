package com.example.mesh_base.wd;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.content.Context.LOCATION_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.mesh_base.global_interfaces.ConnectionHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.example.mesh_base.global_interfaces.*;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;

public class WiFiDirectConnectionHandler extends ConnectionHandler {
  private static final String TAG = "my_WiFiDirectHandler";
  private static final int SOCKET_TIMEOUT_MS = 5000;
  private static final int RETRY_LIMIT = 7;

  private final Activity activity;
  private final WifiP2pManager manager;
  private final WifiP2pManager.Channel channel;
  private volatile boolean running = false;

  // Connected peers
  private final Map<UUID, Device> connectedById = Collections.synchronizedMap(new HashMap<>());
  private final Map<UUID, Integer> retryCount = Collections.synchronizedMap(new HashMap<>());
  private final Set<Socket> openSockets = Collections.synchronizedSet(new HashSet<>());
  private ServerSocket serverSocket;

  private BroadcastReceiver wifiReceiver;
  private final IntentFilter wifiIntentFilter = new IntentFilter();

  // Use a scheduled executor for both immediate tasks and delayed retries
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final WifiDirectPermissions permissions;

  public WiFiDirectConnectionHandler(Activity context, UUID id) {
    super(context, id);
    this.activity = context;
    manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
    channel = manager.initialize(context, context.getMainLooper(), null);

    permissions = new WifiDirectPermissions(context, new WifiDirectPermissions.Listener() {
      @Override public void onEnabled() { start(); }
      @Override public void onDisabled() { stop(); }
    });

    wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
    wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
  }

  @Override public void enable() { registerReceivers(); permissions.enable(); }
   public void onPermissionResult(int code) { permissions.onPermissionResult(code); }
  @Override public boolean isEnabled() { return permissions.isEnabled(); }
  @Override public boolean isSupported() { return manager != null; }
  @Override public void start() {
    if (!running && isEnabled()) {
      running = true;
      discoverPeers();
    }
  }

  @SuppressLint("MissingPermission")
  private void registerReceivers() {
    if (wifiReceiver == null) {
      wifiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
          String action = intent.getAction();
          if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            manager.requestPeers(channel, peers -> {
              for (WifiP2pDevice d : peers.getDeviceList()) {
                connectToPeer(d);
              }
            });
          } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            manager.requestConnectionInfo(channel, info -> {
              if (info.groupFormed) runSocketLoop(info.isGroupOwner, info.groupOwnerAddress);
              else scheduleRestartDiscovery();
            });
          }
        }
      };
      activity.registerReceiver(wifiReceiver, wifiIntentFilter);
    }
  }

  @SuppressLint("MissingPermission")
  private void discoverPeers() {
    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
      @Override public void onSuccess() { Log.d(TAG, "discoverPeers: success"); }
      @Override public void onFailure(int reason) {
        Log.e(TAG, "discoverPeers: failed=" + reason);
        // retry after 1s
        scheduler.schedule(WiFiDirectConnectionHandler.this::discoverPeers, 1, TimeUnit.SECONDS);
      }
    });
  }

  @SuppressLint("MissingPermission")
  private void connectToPeer(WifiP2pDevice d) {
    Log.d(TAG, "connectToPeer: " + d.deviceAddress);
    WifiP2pConfig cfg = new WifiP2pConfig();
    cfg.deviceAddress = d.deviceAddress;
    cfg.groupOwnerIntent = 15;
    manager.connect(channel, cfg, new WifiP2pManager.ActionListener() {
      @Override public void onSuccess() { Log.d(TAG, "connect: success"); }
      @Override public void onFailure(int r) { Log.e(TAG, "connect: failure=" + r); }
    });
  }

  private void runSocketLoop(boolean isOwner, InetAddress host) {
    scheduler.execute(() -> {
      try {
        if (isOwner) {
          if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
          serverSocket = new ServerSocket(8888);
          serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
          while (running) handleSocket(serverSocket.accept());
        } else handleSocket(new Socket(host, 8888));
      } catch (IOException e) {
        Log.e(TAG, "runSocketLoop: error", e);
        scheduleRestartDiscovery();
      }
    });
  }

  private void handleSocket(Socket s) {
    openSockets.add(s);
    scheduler.execute(() -> {
      Device dev = null;
      try (DataInputStream in = new DataInputStream(s.getInputStream());
           DataOutputStream out = new DataOutputStream(s.getOutputStream())) {
        s.setSoTimeout(SOCKET_TIMEOUT_MS);
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
        UUID peer = new UUID(in.readLong(), in.readLong());
        if (retryCount.getOrDefault(peer,0) >= RETRY_LIMIT) return;
        dev = new Device(peer, s.getInetAddress().getHostAddress()){};
        retryCount.put(peer, 0);
        connectedById.put(peer, dev);
        onNeighborConnected(dev);
        while (running) {
          int len = in.readInt();
          byte[] data = new byte[len];
          in.readFully(data);
          onDataReceived(dev, data);
        }
      } catch (IOException e) {
        Log.e(TAG, "handleSocket: error", e);
        if (dev != null) {
          connectedById.remove(dev.uuid);
          onNeighborDisconnected(dev);
          retryCount.compute(dev.uuid, (k,v)-> (v==null)?1:v+1);
        }
        scheduleRestartDiscovery();
      } finally {
        try { s.close(); } catch (IOException ignored) {}
        openSockets.remove(s);
      }
    });
  }

  private void disconnectAll() {
    connectedById.values().forEach(this::onNeighborDisconnected);
    connectedById.clear();
    scheduleRestartDiscovery();
  }

  private void scheduleRestartDiscovery() {
    if (running) {
      // give a small delay to avoid tight loops
      scheduler.schedule(this::restartDiscovery, 1, TimeUnit.SECONDS);
    }
  }

  private void restartDiscovery() {
    if (running) {
      disconnectAll();
      discoverPeers();
    }
  }

  @Override public void stop() {
    running = false;
    try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    openSockets.forEach(sock->{try{sock.close();}catch(Exception ignored){}});
    if (wifiReceiver != null) {
      activity.unregisterReceiver(wifiReceiver);
      wifiReceiver = null;
    }
    permissions.cleanup();
    connectedById.clear(); retryCount.clear(); openSockets.clear();
    onDisconnected();
  }

  @Override public boolean isOn() { return isEnabled() && running; }
  @Override public ArrayList<Device> getNeighbourDevices() { return new ArrayList<>(connectedById.values()); }
  @Override public void send(byte[] data) throws SendError { for (Device d : getNeighbourDevices()) send(data, d); }
  @Override public void send(byte[] data, Device n) throws SendError {
    if (!connectedById.containsKey(n.uuid)) throw new SendError("Not connected to " + n.uuid);
    try (Socket s = new Socket(n.name, 8888)) {
      DataOutputStream out = new DataOutputStream(s.getOutputStream());
      out.writeInt(data.length); out.write(data);
    } catch (IOException e) {
      Log.e(TAG, "send: error", e);
      throw new SendError(e.getMessage());
    }


  }
  private final Handler handler = new Handler(Looper.getMainLooper());

  private void restartDiscoveryWithDelay() {
    handler.postDelayed(this::discoverPeers, 1000);
  }

}


class WifiDirectPermissions {
  private final String TAG = "my_wifiDirect-permissions";
  private final int PERMISSIONS_REQUEST_CODE = 1768;
  private final int LOCATION_REQUEST_CODE = 1798;

  private final Activity activity;
  private final Listener defaultListener = new Listener() {
    @Override
    public void onEnabled() {
      Log.d(TAG, "wifi direct enabled (listener not set yet)");
    }

    @Override
    public void onDisabled() {
      Log.d(TAG, "wifi direct disabled (listener not set yet)");
    }
  };
  private Listener listener;

  BroadcastReceiver locationCallback = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      Log.d(TAG, "broadcast received" + action);

      //may risk receiving events that are not about the location being turned on or off
      //assuming that multiple listener.enable() calls don't cause problems

      if (!locationIsOn()) {
        listener.onDisabled();
      } else if (isEnabled()) {
        listener.onEnabled();
      } else {
        enable();
      }
    }
  };

  public WifiDirectPermissions(Activity activity, Listener listener) {
    this.activity = activity;
    this.listener = listener;

    IntentFilter locationFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
    activity.registerReceiver(locationCallback, locationFilter);
  }

  public void enable() {
    if (listener == defaultListener) {
      throw new RuntimeException("please set a listener first before calling enable on wifidirect Permissions");
    }
    //TODO: handle permanent denial of permissions
    Log.d(TAG, "checking if wifidirect is enabled") ;
    if (isEnabled()) {
      Log.d(TAG, "wifi direct is enabled!") ;
      listener.onEnabled();
      return;
    }

    if (!isSupported()) {
      Log.e(TAG, "wifi direct is not supported, ignoring enable() call");
      return;
    }
    Log.d(TAG, "trying to enable permissions and location");

    if (!hasPermissions()) {
      Log.d(TAG, "requesting permissions");
      activity.requestPermissions(getPermissions(), PERMISSIONS_REQUEST_CODE);
    } else if (!locationIsOn()) {
      Log.d(TAG, "requesting location");
      promptLocation();
    }
  }

  public void onPermissionResult(int requestCode){
    Log.d(TAG, "activity result called! requestCode:"+ requestCode +" hasPermissions:"+hasPermissions() + " locationIsOn:"+locationIsOn());
    if (requestCode == PERMISSIONS_REQUEST_CODE && !hasPermissions()){
      listener.onDisabled();
    }else if(requestCode == LOCATION_REQUEST_CODE && !locationIsOn() ){
      listener.onDisabled();
    }else {
      enable();
    }
  }
  public boolean isSupported() {
    WifiP2pManager p2pManager = activity.getSystemService(WifiP2pManager.class);
    return p2pManager != null;
  }

  private String[] getPermissions() {
      ArrayList<String> list = new ArrayList<>();
      if (ContextCompat.checkSelfPermission(activity,
          android.Manifest.permission.ACCESS_FINE_LOCATION)
          != PackageManager.PERMISSION_GRANTED) {
        list.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          ContextCompat.checkSelfPermission(activity,
              android.Manifest.permission.NEARBY_WIFI_DEVICES)
              != PackageManager.PERMISSION_GRANTED) {
        list.add(android.Manifest.permission.NEARBY_WIFI_DEVICES);
      }
      return list.toArray(new String[0]);
  }

  private boolean hasPermissions() {
    for (String permission : this.getPermissions()) {
      if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean locationIsOn() {
    LocationManager manager = (LocationManager) activity.getSystemService(LOCATION_SERVICE);
    if (manager == null) return false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return manager.isLocationEnabled();
    } else {
      return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
  }

  public boolean isEnabled() {
    return hasPermissions() && locationIsOn();
  }

  private void promptLocation() {
    Log.d(TAG, "prompting location");
    LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY).setMinUpdateIntervalMillis(5000).build();
    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

    //doesn't trigger any prompt, just checking the settings
    Task<LocationSettingsResponse> task = LocationServices.getSettingsClient(activity).checkLocationSettings(builder.build());
    task.addOnSuccessListener(activity, locationSettingsResponse -> {
      Log.e(TAG, "location is already configured properly in the settings but promptLocation() was still called!");
      enable();
    });

    //triggers prompt
    task.addOnFailureListener(activity, taskFailError -> {
      Log.e(TAG, "user needs to enable location in settings" + taskFailError);
      if (!(taskFailError instanceof ResolvableApiException)) {
        Log.e(TAG, "is not resolvable exception" + taskFailError);
        listener.onDisabled();
        return;
      }

      try{
        ((ResolvableApiException) taskFailError).startResolutionForResult(activity, LOCATION_REQUEST_CODE);
      }catch (Exception startResolutionError){
        Log.e(TAG, "error when starting intent sender for result" + startResolutionError);
        listener.onDisabled();
      }
    });
  }

  public void cleanup() {
    Log.e(TAG, "unimplemented");
  }

  public interface Listener {
    void onEnabled();

    void onDisabled();
  }
}
