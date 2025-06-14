package com.example.mesh_base.wd;
import static android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_STATE;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.content.Context.LOCATION_SERVICE;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_ENABLED;

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
import android.net.wifi.WifiManager;
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
    Log.d(TAG, "Constructor called");
    this.activity = context;
    manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
    channel = manager.initialize(context, context.getMainLooper(), null);
    Log.d(TAG, "WifiP2pManager initialized");

    permissions = new WifiDirectPermissions(context, new WifiDirectPermissions.Listener() {
      @Override public void onEnabled() {
        Log.d(TAG, "Permissions onEnabled callback");
        start();
      }
      @Override public void onDisabled() {
        Log.d(TAG, "Permissions onDisabled callback");
        stop();
      }
    });
    Log.d(TAG, "WifiDirectPermissions created");

    wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
    wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
    Log.d(TAG, "Intent filters configured");
  }

  @Override public void enable() {
    Log.d(TAG, "enable() called");
    registerReceivers();
    permissions.enable();
  }

  public void onPermissionResult(int code) {
    Log.d(TAG, "onPermissionResult() received with code: " + code);
    permissions.onPermissionResult(code);
  }

  @Override public boolean isEnabled() {
    boolean enabled = permissions.isEnabled();
    Log.d(TAG, "isEnabled() returning: " + enabled);
    return enabled;
  }

  @Override public boolean isSupported() {
    boolean supported = manager != null;
    Log.d(TAG, "isSupported() returning: " + supported);
    return supported;
  }

  @Override public void start() {
    Log.d(TAG, "start() called - running: " + running + ", enabled: " + isEnabled());
    if (!running && isEnabled()) {
      running = true;
      onConnected();
      discoverPeers();
      Log.d(TAG, "Started successfully");
    } else {
      Log.w(TAG, "Already running or not enabled - no action taken");
    }
  }

  @SuppressLint("MissingPermission")
  private void registerReceivers() {
    Log.d(TAG, "registerReceivers() called");
    if (wifiReceiver == null) {
      wifiReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
          String action = intent.getAction();
          Log.d(TAG, "Broadcast received: " + action);

          if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "Peers changed action");
            manager.requestPeers(channel, peers -> {
              Log.d(TAG, "Discovered " + peers.getDeviceList().size() + " peers");
              for (WifiP2pDevice d : peers.getDeviceList()) {
                connectToPeer(d);
              }
            });
          } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "Connection changed action");
            manager.requestConnectionInfo(channel, info -> {
              Log.d(TAG, "Connection info received - group formed: " + info.groupFormed);
              if (info.groupFormed) {
                Log.d(TAG, "Group formed - isOwner: " + info.isGroupOwner + ", owner: " + info.groupOwnerAddress);
                runSocketLoop(info.isGroupOwner, info.groupOwnerAddress);
              } else {
                Log.w(TAG, "Group not formed - scheduling restart");
                scheduleRestartDiscovery();
              }
            });
          } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int raw = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -2);
            String text = raw == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                ? "ENABLED" : raw == WifiP2pManager.WIFI_P2P_STATE_DISABLED
                ? "DISABLED" : "UNKNOWN(" + raw + ")";
            Log.d(TAG, "P2P state changed: " + text);
          }
        }
      };
      activity.registerReceiver(wifiReceiver, wifiIntentFilter);
      Log.d(TAG, "Broadcast receiver registered");
    } else {
      Log.d(TAG, "Broadcast receiver already registered");
    }
  }

  @SuppressLint("MissingPermission")
  private void discoverPeers() {
    Log.d(TAG, "discoverPeers() called");
    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
      @Override public void onSuccess() {
        Log.d(TAG, "discoverPeers: success");
      }
      @Override public void onFailure(int reason) {
        Log.e(TAG, "discoverPeers: failed with reason: " + reason);
        Log.d(TAG, "Scheduling peer discovery retry in 1s");
        scheduler.schedule(WiFiDirectConnectionHandler.this::discoverPeers, 1, TimeUnit.SECONDS);
      }
    });
  }

  @SuppressLint("MissingPermission")
  private void connectToPeer(WifiP2pDevice d) {
    Log.d(TAG, "connectToPeer() called for: " + d.deviceName + " (" + d.deviceAddress + ")");
    WifiP2pConfig cfg = new WifiP2pConfig();
    cfg.deviceAddress = d.deviceAddress;
    cfg.groupOwnerIntent = 15;
    manager.connect(channel, cfg, new WifiP2pManager.ActionListener() {
      @Override public void onSuccess() {
        Log.d(TAG, "connectToPeer: success for " + d.deviceAddress);
      }
      @Override public void onFailure(int r) {
        Log.e(TAG, "connectToPeer: failure for " + d.deviceAddress + " reason: " + r);
      }
    });
  }

  private void runSocketLoop(boolean isOwner, InetAddress host) {
    Log.d(TAG, "runSocketLoop() called - isOwner: " + isOwner + ", host: " + host);
    scheduler.execute(() -> {
      try {
        if (isOwner) {
          Log.d(TAG, "Creating server socket as group owner");
          if (serverSocket != null && !serverSocket.isClosed()) {
            Log.d(TAG, "Closing existing server socket");
            serverSocket.close();
          }
          serverSocket = new ServerSocket(8888);
          serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
          Log.d(TAG, "Server socket created on port 8888");

          while (running) {
            Log.d(TAG, "Waiting for client connections...");
            Socket clientSocket = serverSocket.accept();
            Log.d(TAG, "New client connected: " + clientSocket.getInetAddress());
            handleSocket(clientSocket);
          }
        } else {
          Log.d(TAG, "Connecting to group owner: " + host);
          handleSocket(new Socket(host, 8888));
        }
      } catch (IOException e) {
        Log.e(TAG, "runSocketLoop: error", e);
        scheduleRestartDiscovery();
      }
    });
  }

  private void handleSocket(Socket s) {
    Log.d(TAG, "handleSocket() called for: " + s.getInetAddress());
    openSockets.add(s);
    scheduler.execute(() -> {
      Device dev = null;
      try (DataInputStream in = new DataInputStream(s.getInputStream());
           DataOutputStream out = new DataOutputStream(s.getOutputStream())) {
        s.setSoTimeout(SOCKET_TIMEOUT_MS);
        Log.d(TAG, "Exchanging UUIDs with peer: " + s.getInetAddress());

        // Send our UUID
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());

        // Receive peer UUID
        UUID peer = new UUID(in.readLong(), in.readLong());
        Log.d(TAG, "Peer UUID: " + peer);

        int currentRetry = retryCount.getOrDefault(peer, 0);
        if (currentRetry >= RETRY_LIMIT) {
          Log.w(TAG, "Retry limit reached for peer " + peer + " - skipping connection");
          return;
        }

        dev = new Device(peer, s.getInetAddress().getHostAddress()){};
        retryCount.put(peer, 0);
        connectedById.put(peer, dev);
        Log.d(TAG, "New device connected: " + dev.uuid + " (" + dev.name + ")");
        onNeighborConnected(dev);

        while (running) {
          Log.v(TAG, "Waiting for data from " + peer);
          int len = in.readInt();
          byte[] data = new byte[len];
          in.readFully(data);
          Log.d(TAG, "Received " + len + " bytes from " + peer);
          onDataReceived(dev, data);
        }
      } catch (IOException e) {
        Log.e(TAG, "handleSocket: error for socket " + s.getInetAddress(), e);
        if (dev != null) {
          Log.w(TAG, "Removing disconnected device: " + dev.uuid);
          connectedById.remove(dev.uuid);
          onNeighborDisconnected(dev);
          int newRetryCount = retryCount.compute(dev.uuid, (k,v)-> (v==null)?1:v+1);
          Log.d(TAG, "Retry count for " + dev.uuid + " now: " + newRetryCount);
        }
        scheduleRestartDiscovery();
      } finally {
        try {
          s.close();
          Log.d(TAG, "Socket closed: " + s.getInetAddress());
        } catch (IOException ignored) {}
        openSockets.remove(s);
      }
    });
  }

  private void disconnectAll() {
    Log.d(TAG, "disconnectAll() called");
    int count = connectedById.size();
    connectedById.values().forEach(this::onNeighborDisconnected);
    connectedById.clear();
    Log.d(TAG, "Disconnected " + count + " devices");
    scheduleRestartDiscovery();
  }

  private void scheduleRestartDiscovery() {
    Log.d(TAG, "scheduleRestartDiscovery() called");
    if (running) {
      Log.d(TAG, "Scheduling discovery restart in 1s");
      scheduler.schedule(this::restartDiscovery, 1, TimeUnit.SECONDS);
    }
  }

  private void restartDiscovery() {
    Log.d(TAG, "restartDiscovery() called - running: " + running);
    if (running) {
      disconnectAll();
      discoverPeers();
    }
  }

  @Override public void stop() {
    Log.d(TAG, "stop() called");
    running = false;
    try {
      if (serverSocket != null) {
        Log.d(TAG, "Closing server socket");
        serverSocket.close();
      }
    } catch (IOException ignored) {}

    openSockets.forEach(sock->{
      try {
        Log.d(TAG, "Closing client socket: " + sock.getInetAddress());
        sock.close();
      } catch(Exception ignored) {}
    });

    if (wifiReceiver != null) {
      Log.d(TAG, "Unregistering broadcast receiver");
      activity.unregisterReceiver(wifiReceiver);
      wifiReceiver = null;
    }

    permissions.cleanup();
    connectedById.clear();
    retryCount.clear();
    openSockets.clear();
    Log.d(TAG, "All connections and state cleared");
    onDisconnected();
  }

  @Override public boolean isOn() {
    Log.v(TAG, "isOn() returning: " + running);
    return  running;
  }

  @Override public ArrayList<Device> getNeighbourDevices() {
    ArrayList<Device> devices = new ArrayList<>(connectedById.values());
    Log.d(TAG, "getNeighbourDevices() returning " + devices.size() + " devices");
    return devices;
  }

  @Override public void send(byte[] data) throws SendError {
    Log.d(TAG, "Broadcast send() called with " + data.length + " bytes");
    for (Device d : getNeighbourDevices()) {
      Log.d(TAG, "Sending to neighbor: " + d.uuid);
      send(data, d);
    }
  }

  @Override public void send(byte[] data, Device n) throws SendError {
    Log.d(TAG, "send() to device " + n.uuid + " with " + data.length + " bytes");
    if (!connectedById.containsKey(n.uuid)) {
      Log.e(TAG, "Not connected to device: " + n.uuid);
      throw new SendError("Not connected to " + n.uuid);
    }

    try (Socket s = new Socket(n.name, 8888)) {
      Log.d(TAG, "Connected to " + n.name + " for data transfer");
      DataOutputStream out = new DataOutputStream(s.getOutputStream());
      out.writeInt(data.length);
      out.write(data);
      Log.d(TAG, "Data sent successfully to " + n.uuid);
    } catch (IOException e) {
      Log.e(TAG, "send: error sending to " + n.uuid, e);
      throw new SendError(e.getMessage());
    }
  }

  private final Handler handler = new Handler(Looper.getMainLooper());

  private void restartDiscoveryWithDelay() {
    Log.d(TAG, "restartDiscoveryWithDelay() scheduled in 1s");
    handler.postDelayed(this::discoverPeers, 1000);
  }
}

class WifiDirectPermissions {
  public interface Listener { void onEnabled(); void onDisabled(); }

  private static final String TAG                    = "my_wifiDirect-permissions";
  private static final int PERMISSIONS_REQUEST_CODE  = 1768;
  private static final int LOCATION_REQUEST_CODE     = 1798;

  private final Activity activity;
  private final Listener listener;

  private final BroadcastReceiver locationCallback;
  private final BroadcastReceiver p2pStateCallback;  // Changed from wifiStateCallback to p2pStateCallback
  private int p2pState = WifiP2pManager.WIFI_P2P_STATE_DISABLED;  // Track P2P state

  public WifiDirectPermissions(Activity activity, Listener listener) {
    Log.d(TAG, "Constructor called");
    this.activity = activity;
    this.listener = listener;

    // 1) Listen for Wi-Fi Direct (P2P) being turned on/off
    IntentFilter p2pFilter = new IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    p2pStateCallback = new BroadcastReceiver() {
      @Override public void onReceive(Context c, Intent intent) {
        p2pState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED);
        Log.d(TAG, "Wi-Fi Direct state changed: " + p2pState);

        if (p2pState == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
          Log.d(TAG, "Wi-Fi Direct ON → re-enter enable flow");
          enable();
        } else {
          Log.d(TAG, "Wi-Fi Direct OFF → onDisabled");
          listener.onDisabled();
        }
      }
    };
    activity.registerReceiver(p2pStateCallback, p2pFilter);
    Log.d(TAG, "Wi-Fi Direct state receiver registered");

    // 2) Listen for Location toggles
    IntentFilter locationFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
    locationCallback = new BroadcastReceiver() {
      @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Location toggle broadcast: " + action);
        boolean locOn = locationIsOn();
        Log.d(TAG, "Location enabled: " + locOn);

        if (!locOn) {
          Log.d(TAG, "Location OFF → onDisabled");
          listener.onDisabled();
        } else if (isEnabled()) {
          Log.d(TAG, "Location ON & all other requirements OK → onEnabled");
          listener.onEnabled();
        } else {
          Log.d(TAG, "Location ON but other requirements missing → enable()");
          enable();
        }
      }
    };
    activity.registerReceiver(locationCallback, locationFilter);
    Log.d(TAG, "Location state receiver registered");

    // Get initial P2P state
    WifiManager wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    if (wifiManager != null) {
      p2pState = wifiManager.isWifiEnabled() ?
          WifiP2pManager.WIFI_P2P_STATE_ENABLED :
          WifiP2pManager.WIFI_P2P_STATE_DISABLED;
    }
    Log.d(TAG, "Initial P2P state: " + p2pState);
  }

  /** Begin—or re‐enter—the enable flow */
  public void enable() {
    Log.d(TAG, "enable() entered");
    // 0) Ensure Wi-Fi Direct (P2P) is enabled
    if (!isP2pEnabled()) {
      Log.d(TAG, "Wi-Fi Direct OFF → prompting user to enable");
      promptP2p();
      return;
    }
    Log.d(TAG, "Wi-Fi Direct is enabled");

    // 1) Runtime permissions
    String[] perms = buildPermissionList();
    if (perms.length > 0) {
      Log.d(TAG, "Requesting " + perms.length + " permissions: " + Arrays.toString(perms));
      activity.requestPermissions(perms, PERMISSIONS_REQUEST_CODE);
      return;
    }
    Log.d(TAG, "All permissions granted");

    // 2) Location toggle
    if (!locationIsOn()) {
      Log.d(TAG, "Location OFF → prompting location settings");
      promptLocation();
      return;
    }
    Log.d(TAG, "Location is ON");

    // 3) All requirements met!
    Log.d(TAG, "All requirements OK → onEnabled");
    listener.onEnabled();
  }

  /** Handle both permission and location‐settings results */
  public void onPermissionResult(int requestCode) {
    Log.d(TAG, "onPermissionResult() received: " + requestCode);
    if (requestCode == PERMISSIONS_REQUEST_CODE) {
      String[] miss = buildPermissionList();
      if (miss.length > 0) {
        Log.d(TAG, "Permissions still missing (" + miss.length + ") → onDisabled");
        listener.onDisabled();
      } else {
        Log.d(TAG, "Permissions granted → re-enter enable()");
        enable();
      }
    } else if (requestCode == LOCATION_REQUEST_CODE) {
      boolean locOn = locationIsOn();
      Log.d(TAG, "Location settings result → isOn=" + locOn);
      if (locOn && buildPermissionList().length == 0 && isP2pEnabled()) {
        Log.d(TAG, "All requirements met after location prompt → onEnabled");
        listener.onEnabled();
      } else {
        Log.d(TAG, "Requirements not met after location prompt → onDisabled");
        listener.onDisabled();
      }
    } else {
      Log.d(TAG, "Unknown request code: " + requestCode);
    }
  }

  /** Clean up both receivers */
  public void cleanup() {
    Log.d(TAG, "cleanup() called");
    activity.unregisterReceiver(p2pStateCallback);
    activity.unregisterReceiver(locationCallback);
    Log.d(TAG, "Receivers unregistered");
  }

  // — Helper methods below —

  private boolean isP2pEnabled() {
    boolean enabled = p2pState == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
    Log.d(TAG, "isP2pEnabled() returning: " + enabled);
    return enabled;
  }

  private String[] buildPermissionList() {
    ArrayList<String> list = new ArrayList<>();
    if (ContextCompat.checkSelfPermission(activity,
        Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      list.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(activity,
            Manifest.permission.NEARBY_WIFI_DEVICES)
            != PackageManager.PERMISSION_GRANTED) {
      list.add(Manifest.permission.NEARBY_WIFI_DEVICES);
    }
    Log.d(TAG, "buildPermissionList() found " + list.size() + " missing permissions");
    return list.toArray(new String[0]);
  }

  private boolean locationIsOn() {
    LocationManager mgr = (LocationManager)
        activity.getSystemService(Context.LOCATION_SERVICE);
    if (mgr == null) {
      Log.w(TAG, "locationIsOn() - LocationManager is null");
      return false;
    }

    boolean enabled;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      enabled = mgr.isLocationEnabled();
    } else {
      enabled = mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)
          || mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
    Log.d(TAG, "locationIsOn() returning: " + enabled);
    return enabled;
  }

  private void promptP2p() {
    Log.d(TAG, "promptP2p() - launching Wi-Fi Direct settings");
    try {
      Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        // Add flag to open advanced settings where Wi-Fi Direct is usually located
        intent.putExtra("extra_prefs_show_button_bar", true);
        intent.putExtra("extra_prefs_set_next_text", "Advanced");
        intent.putExtra("extra_prefs_set_back_text", "Back");
      }
      activity.startActivity(intent);
    } catch (Exception e) {
      Log.e(TAG, "Error launching Wi-Fi Direct settings", e);
      // Fallback to general Wi-Fi settings
      activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }
  }

  private void promptLocation() {
    Log.d(TAG, "promptLocation() called");
    LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY).build();
    LocationSettingsRequest settingsReq = new LocationSettingsRequest.Builder()
        .addLocationRequest(req).build();

    Task<LocationSettingsResponse> task = LocationServices
        .getSettingsClient(activity)
        .checkLocationSettings(settingsReq);

    task.addOnSuccessListener(activity, r -> {
      Log.d(TAG, "Location already ON → re-enter enable()");
      enable();
    });
    task.addOnFailureListener(activity, e -> {
      Log.d(TAG, "Need Location settings resolution: " + e);
      if (e instanceof ResolvableApiException) {
        try {
          Log.d(TAG, "Starting resolution for location settings");
          ((ResolvableApiException)e)
              .startResolutionForResult(activity, LOCATION_REQUEST_CODE);
        } catch (IntentSender.SendIntentException ex) {
          Log.e(TAG, "Error launching location settings", ex);
          listener.onDisabled();
        }
      } else {
        Log.e(TAG, "Non-resolvable location error", e);
        listener.onDisabled();
      }
    });
  }

  public boolean isEnabled() {
    boolean p2pEnabled = isP2pEnabled();
    boolean permsOk  = buildPermissionList().length == 0;
    boolean locOn    = locationIsOn();
    boolean enabled = p2pEnabled && permsOk && locOn;
    Log.d(TAG, "isEnabled() - Wi-Fi Direct: " + p2pEnabled +
        ", permissions: " + permsOk +
        ", location: " + locOn +
        " → returning: " + enabled);
    return enabled;
  }
}
