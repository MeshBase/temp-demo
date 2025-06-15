package com.example.mesh_base.wd;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WiFiDirectConnectionHandler extends ConnectionHandler {
    private static final String TAG = "my_WiFiDirectHandler";
    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final int RETRY_LIMIT = 7;
    private static final int SERVER_PORT = 8888;
    private static final int CHANNEL_RECOVERY_DELAY_MS = 3000;
    private static final int DISCOVERY_INTERVAL_SEC = 15;
    private static final int DISCOVERY_DURATION_SEC = 120;
    private static final int MAX_DATA_SIZE = 10 * 1024 * 1024; // 10MB

    private final Activity activity;
    private final WifiP2pManager manager;
    // Connected peers and sockets
    private final Map<UUID, Device> connectedById = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> retryCount = new ConcurrentHashMap<>();
    private final Map<UUID, Socket> activeSockets = new ConcurrentHashMap<>();
    private final Map<String, UUID> macToUuid = new ConcurrentHashMap<>();
    private final IntentFilter wifiIntentFilter = new IntentFilter();
    private final ExecutorService socketExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final WifiDirectPermissions permissions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WifiP2pManager.Channel channel;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private BroadcastReceiver wifiReceiver;
    private ScheduledFuture<?> discoveryRetryFuture;
    private ScheduledFuture<?> periodicDiscoveryFuture;
    private boolean discoveryActive = false;
    private long lastDiscoveryStartTime = 0;

    public WiFiDirectConnectionHandler(Activity context, UUID id) {
        super(context, id);
        this.activity = context;
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        initializeChannel();

        permissions = new WifiDirectPermissions(context, new WifiDirectPermissions.Listener() {
            @Override
            public void onEnabled() {
                start();
            }

            @Override
            public void onDisabled() {
                stop();
            }
        });

        wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void initializeChannel() {
        if (manager != null) {
            channel = manager.initialize(activity, activity.getMainLooper(), new WifiP2pManager.ChannelListener() {
                @Override
                public void onChannelDisconnected() {
                    Log.e(TAG, "Channel disconnected! Attempting recovery...");
                    handler.postDelayed(() -> {
                        if (running) {
                            Log.w(TAG, "Reinitializing channel after disconnect");
                            initializeChannel();
                            restartDiscovery();
                        }
                    }, CHANNEL_RECOVERY_DELAY_MS);
                }
            });
        }
    }

    @Override
    public void enable() {
        Log.d(TAG, "Enabling...");
        //EDIT: incase isEnabled is checked, then start() is called without this being invoked
//        registerReceivers();
        permissions.enable();
    }

    public void onPermissionResult(int code) {
        permissions.onPermissionResult(code);
    }

    @Override
    public boolean isEnabled() {
        return permissions.isEnabled();
    }

    @Override
    public boolean isSupported() {
        boolean supported = manager != null && channel != null;
        Log.d(TAG, "isSupported: " + supported);
        return supported;
    }

    @Override
    public void start() {
        Log.d(TAG, "Starting...");
        if (!running && isEnabled()) {
            running = true;
            registerReceivers();
            startPeriodicDiscovery();
            onConnected();
        }
    }

    private void startPeriodicDiscovery() {
        if (periodicDiscoveryFuture != null) {
            periodicDiscoveryFuture.cancel(false);
        }

        periodicDiscoveryFuture = scheduler.scheduleWithFixedDelay(() -> {
            if (running) {
                long timeSinceLastDiscovery = System.currentTimeMillis() - lastDiscoveryStartTime;
                if (timeSinceLastDiscovery > DISCOVERY_DURATION_SEC * 1000 && !hasConnections()) {
                    Log.d(TAG, "Restarting discovery after idle period");
                    restartDiscovery();
                } else {
                    discoverPeers();
                }
            }
        }, 0, DISCOVERY_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void registerReceivers() {
        if (wifiReceiver == null) {
            wifiReceiver = new BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (!running) {
                        Log.d(TAG, "Ignoring broadcast - stopped");
                        return;
                    }

                    String action = intent.getAction();
                    if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                        handlePeersChanged(intent);
                    } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                        handleConnectionChanged(intent);
                    } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                        handleStateChanged(intent);
                    } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                        Log.d(TAG, "This device changed");
                    }
                }
            };
            try {
                activity.registerReceiver(wifiReceiver, wifiIntentFilter);
            } catch (Exception e) {
                Log.e(TAG, "Receiver registration failed", e);
                //EDIT: not an expected problem
                stop();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void handlePeersChanged(Intent intent) {
        Log.d(TAG, "Peers changed action");

        if (!permissions.hasRequiredPermissions()) {
            Log.w(TAG, "Skipping peer connection - permissions missing");
            return;
        }

        if (manager == null || channel == null) {
            Log.e(TAG, "Cannot request peers - manager or channel is null");
            scheduleChannelRecovery();
            return;
        }

        manager.requestPeers(channel, peerList -> {
            if (peerList == null) {
                Log.e(TAG, "requestPeers returned null peer list");
                return;
            }

            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
                Log.d(TAG, "Already connecting - skipping new connections");
                return;
            }

            if (peerList.getDeviceList().isEmpty()) {
                Log.d(TAG, "No devices available");
                return;
            }

            Log.d(TAG, "Found " + peerList.getDeviceList().size() + " peers");

            // Find first available peer not already connected
            for (WifiP2pDevice device : peerList.getDeviceList()) {
                if (isConnectableDevice(device)) {
                    Log.d(TAG, "trying to connect to " + device.deviceName);
                    connectToDevice(device);
                    break; // Connect to one peer at a time
                } else {
                    Log.d(TAG, "peer " + device.deviceName + "is not connectable, skipping");
                }
            }
        });
    }

    private boolean isConnectableDevice(WifiP2pDevice device) {
        if (device == null) return false;

        UUID existingUuid = macToUuid.get(device.deviceAddress);
        boolean alreadyConnected = existingUuid != null && connectedById.containsKey(existingUuid);
        boolean retryExceeded = existingUuid != null &&
                retryCount.getOrDefault(existingUuid, 0) >= RETRY_LIMIT;

        boolean canConnect = (device.status == WifiP2pDevice.AVAILABLE) &&
                !alreadyConnected &&
                !retryExceeded;
        Log.d(TAG, device.deviceName + " is connectable=" + canConnect + " due to availability= (3)" + device.status + ") alreadyConnected=" + alreadyConnected + " retryExceeded=" + retryExceeded);
        return canConnect;
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(WifiP2pDevice device) {
        if (device == null) return;

        Log.d(TAG, "Connecting to: " + device.deviceName + " (" + device.deviceAddress + ")");

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.groupOwnerIntent = 4;
        config.wps.setup = WpsInfo.PBC;

        if (manager == null || channel == null) {
            Log.e(TAG, "Cannot connect - manager or channel is null");
            return;
        }

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connect success");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Connect failed: " + reason);
                if (!hasConnections()) {
                    scheduleRestartDiscovery(false);
                } else {
                    Log.d(TAG, "deciding not to restart discovery because there are existing connections");
                }
            }
        });
    }

    private void handleConnectionChanged(Intent intent) {
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        WifiP2pInfo p2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);

        if (networkInfo == null || p2pInfo == null) {
            Log.e(TAG, "Connection changed with null network info or p2p info");
            scheduleRestartDiscovery(true);
            return;
        }

        if (networkInfo.isConnected() && p2pInfo.groupOwnerAddress != null) {
            Log.d(TAG, "Connection formed - Group Owner: " + p2pInfo.isGroupOwner);
            runSocketLoop(p2pInfo.isGroupOwner, p2pInfo.groupOwnerAddress);
        } else {
            Log.d(TAG, "connection state change not good isConnected:" + networkInfo.isConnected() + " ownerAddress:" + p2pInfo.groupOwnerAddress);
            scheduleRestartDiscovery(false);
        }
    }

    private void handleStateChanged(Intent intent) {
        int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -2);
        String stateText = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED ?
                "ENABLED" : state == WifiP2pManager.WIFI_P2P_STATE_DISABLED ?
                "DISABLED" : "UNKNOWN (" + state + ")";
        Log.d(TAG, "P2P state: " + stateText);

        if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
            stop();
        } else if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED && running) {
            restartDiscovery();
        }
    }

    @SuppressLint("MissingPermission")
    private void discoverPeers() {
        if (discoveryActive) {
            Log.d(TAG, "Discovery already active");
            return;
        }

        if (hasConnections()) {
            Log.d(TAG, "not discovering due to having existing connections");
            return;
        }

        Log.d(TAG, "Discovering peers...");
        lastDiscoveryStartTime = System.currentTimeMillis();

        if (!permissions.hasRequiredPermissions()) {
            Log.w(TAG, "Skipping discovery - permissions missing");
            return;
        }

        if (manager == null || channel == null) {
            Log.e(TAG, "Cannot discover peers - manager or channel is null");
            scheduleChannelRecovery();
            return;
        }

        discoveryActive = true;
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery started");
                discoveryActive = false;
                cancelDiscoveryRetry();

                // Schedule automatic restart after discovery duration
                scheduler.schedule(() -> {
                    if (running && !hasConnections()) {
                        Log.d(TAG, "Restarting discovery after full cycle");
                        restartDiscovery();
                    }
                }, DISCOVERY_DURATION_SEC, TimeUnit.SECONDS);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Discovery failed: " + reason);
                discoveryActive = false;

                switch (reason) {
                    case WifiP2pManager.BUSY:
                        Log.w(TAG, "Discovery busy - retrying");
                        scheduleDiscoveryRetry(5);
                        break;
                    case WifiP2pManager.ERROR:
                    case WifiP2pManager.P2P_UNSUPPORTED:
                        Log.e(TAG, "Critical discovery error - delaying retry");
                        scheduleChannelRecovery();
                        break;
                    default:
                        scheduleDiscoveryRetry(5);
                }
            }
        });
    }

    private void scheduleDiscoveryRetry(int delaySeconds) {
        if (running) {
            Log.d(TAG, "Scheduling discovery retry in " + delaySeconds + "s");
            cancelDiscoveryRetry();
            discoveryRetryFuture = scheduler.schedule(() -> {
                discoverPeers();
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private void scheduleChannelRecovery() {
        if (running) {
            Log.w(TAG, "Scheduling channel recovery");
            handler.postDelayed(() -> {
                if (running) {
                    Log.w(TAG, "Attempting channel recovery");
                    initializeChannel();
                    restartDiscovery();
                }
            }, CHANNEL_RECOVERY_DELAY_MS);
        }
    }

    private void cancelDiscoveryRetry() {
        if (discoveryRetryFuture != null && !discoveryRetryFuture.isDone()) {
            discoveryRetryFuture.cancel(false);
            discoveryRetryFuture = null;
        }
    }

    private void runSocketLoop(boolean isGroupOwner, InetAddress groupOwnerAddress) {
        socketExecutor.execute(() -> {
            try {
                if (isGroupOwner) {
                    startServerSocket();
                } else {
                    connectToGroupOwner(groupOwnerAddress);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket setup failed", e);
                scheduleRestartDiscovery(false);
            }
        });
    }

    private void startServerSocket() throws IOException {
        Log.d(TAG, "Starting as Group Owner");

        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        serverSocket = new ServerSocket(SERVER_PORT);
        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                socketExecutor.execute(() -> handleSocket(clientSocket));
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue accepting
            } catch (IOException e) {
                if (running) Log.w(TAG, "Server socket error", e);
            }
        }
    }

    private void connectToGroupOwner(InetAddress groupOwnerAddress) throws IOException {
        if (groupOwnerAddress == null) {
            throw new IOException("Group owner address is null");
        }

        Log.d(TAG, "Connecting to Group Owner: " + groupOwnerAddress);

        Socket socket = new Socket(groupOwnerAddress, SERVER_PORT);
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        handleSocket(socket);
    }

    private void handleSocket(Socket socket) {
        UUID peerUuid = null;
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Exchange UUIDs
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
            out.flush();

            peerUuid = new UUID(in.readLong(), in.readLong());

            // Check retry limit
            if (retryCount.getOrDefault(peerUuid, 0) >= RETRY_LIMIT) {
                Log.w(TAG, "Retry limit exceeded for: " + peerUuid);
                return;
            }

            // Register new device
            Device device = new Device(peerUuid, socket.getInetAddress().getHostAddress()) {
            };
            retryCount.remove(peerUuid);  // Reset retry count
            connectedById.put(peerUuid, device);
            activeSockets.put(peerUuid, socket);
            onNeighborConnected(device);

            Log.d(TAG, "Socket established with: " + peerUuid);

            // Data receiving loop
            while (running) {
                try {
                    int length = in.readInt();
                    if (length <= 0 || length > MAX_DATA_SIZE) {
                        throw new IOException("Invalid data length: " + length);
                    }

                    byte[] data = new byte[length];
                    in.readFully(data);
                    onDataReceived(device, data);
                } catch (EOFException e) {
                    Log.w(TAG, "Orderly disconnect by peer: " + peerUuid);
                    break;
                } catch (SocketTimeoutException e) {
                    // Timeout is normal, continue listening
                    Log.d(TAG, "Socket timeout for: " + peerUuid);
                } catch (SocketException e) {
                    Log.w(TAG, "Socket error for: " + peerUuid + " - " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Socket error", e);
        } finally {
            if (peerUuid != null) {
                handleDisconnection(peerUuid);
            }
            closeSocket(socket);
        }
    }

    private void handleDisconnection(UUID peerUuid) {
        Device device = connectedById.remove(peerUuid);
        activeSockets.remove(peerUuid);

        if (device != null) {
            Log.d(TAG, "handling disconnection for " + device.name);
            onNeighborDisconnected(device);
            retryCount.compute(peerUuid, (k, v) -> (v == null) ? 1 : v + 1);
        }

        scheduleRestartDiscovery(false);
    }

    private void closeSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Error closing socket", e);
        }
    }

    private void disconnectAll() {
        Log.d(TAG, "Disconnecting all peers");

        // Close all sockets
        new ArrayList<>(activeSockets.values()).forEach(this::closeSocket);
        activeSockets.clear();

        // Notify disconnections
        new ArrayList<>(connectedById.values()).forEach(this::onNeighborDisconnected);
        connectedById.clear();

        retryCount.clear();
        macToUuid.clear();
    }

    private void scheduleRestartDiscovery(boolean force) {
        if (running) {
            long seconds = 1;
            Log.d(TAG, "scheduling forced=" + force + " restart discovery after " + seconds + "s");
            scheduler.schedule(() -> {
                if (force || (!hasConnections())) {
                    restartDiscovery();
                } else {
                    Log.d(TAG, "skipping restartDiscovery forced=" + force + " due to force=" + force + " hasConnections" + hasConnections());
                }
            }, seconds, TimeUnit.SECONDS);
        } else {
            Log.d(TAG, "skipping scheduling restart discovery because its stopped()");
        }
    }

    private boolean hasConnections() {
        boolean hasCons = !connectedById.isEmpty();
        Log.d(TAG, "has connections ==" + hasCons);
        return hasCons;
    }

    private void restartDiscovery() {
        if (running) {
            Log.d(TAG, "Restarting discovery");
            disconnectAll();
            discoverPeers();
        }
    }

    @Override
    public void stop() {
        Log.d(TAG, "Stopping...");
        running = false;

        // Cancel any pending operations
        cancelDiscoveryRetry();

        if (periodicDiscoveryFuture != null) {
            periodicDiscoveryFuture.cancel(true);
            periodicDiscoveryFuture = null;
        }

        // Close server socket
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }

        // Close all client sockets
        new ArrayList<>(activeSockets.values()).forEach(this::closeSocket);
        activeSockets.clear();

        // Unregister receiver
        if (wifiReceiver != null) {
            try {
                activity.unregisterReceiver(wifiReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Receiver unregistration failed", e);
            }
            wifiReceiver = null;
        }

        // Shutdown executors
        socketExecutor.shutdownNow();
        scheduler.shutdownNow();

        // Cleanup
        permissions.cleanup();
        connectedById.clear();
        retryCount.clear();
        macToUuid.clear();

        onDisconnected();
    }

    @Override
    public boolean isOn() {
        return running;
    }

    @Override
    public ArrayList<Device> getNeighbourDevices() {
        return new ArrayList<>(connectedById.values());
    }

    @Override
    public void send(byte[] data) throws SendError {
        for (Device d : getNeighbourDevices()) {
            send(data, d);
        }
    }


    @Override
    public void send(byte[] data, Device device) throws SendError {
        // Offload to background thread
        Socket socket = activeSockets.get(device.uuid);
        if (socket == null || socket.isClosed()) {
            throw new SendError("No active connection to " + device.uuid);
        }

        socketExecutor.execute(() -> {
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            } catch (IOException e) {
                Log.e(TAG, "Send failed to: " + device.uuid, e);
                handleDisconnection(device.uuid);
                // Throw wrapped in runtime exception since we're in Runnable
                throw new RuntimeException(new SendError(e.getMessage()));
            }
        });
    }
}

class WifiDirectPermissions {
    private static final String TAG = "my_wifiDirect-permissions";
    private static final int PERMISSIONS_REQUEST_CODE = 1768;
    private static final int LOCATION_REQUEST_CODE = 1798;
    private final Activity activity;
    private final Listener listener;
    private final BroadcastReceiver locationCallback;
    private final BroadcastReceiver wifiStateCallback;

    public WifiDirectPermissions(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;

        // 1) Listen for Wi‑Fi being turned on/off
        IntentFilter wifiFilter = new IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifiStateCallback = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                    int raw = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -2);
                    Log.d(TAG, "RAW P2P_EXTRA_WIFI_STATE = " + raw);
                    String text = raw == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                            ? "ENABLED" : raw == WifiP2pManager.WIFI_P2P_STATE_DISABLED
                            ? "DISABLED" : "UNKNOWN(" + raw + ")";
                    if (raw == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        enable();
                    } else {
                        listener.onDisabled();
                    }
                }
            }
        };
        activity.registerReceiver(wifiStateCallback, wifiFilter);

        // 2) Listen for Location toggles
        IntentFilter locationFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        locationCallback = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "Location toggle broadcast: " + action + " → isOn=" + locationIsOn());
                if (!locationIsOn()) {
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
    }

    /**
     * Begin—or re‐enter—the enable flow
     */
    public void enable() {
        Log.d(TAG, "enable()");
        // 0) Ensure Wi‑Fi is ON
        if (!isWifiOn()) {
            Log.d(TAG, "Wi‑Fi OFF → prompting user to enable");
            promptWifi();
            return;
        }

        // 1) Runtime permissions
        String[] perms = buildPermissionList();
        if (perms.length > 0) {
            Log.d(TAG, "Requesting permissions: " + Arrays.toString(perms));
            activity.requestPermissions(perms, PERMISSIONS_REQUEST_CODE);
            return;
        }

        // 2) Location toggle
        if (!locationIsOn()) {
            Log.d(TAG, "Location OFF → prompting location settings");
            promptLocation();
            return;
        }

        // 3) All requirements met!
        Log.d(TAG, "All requirements OK → onEnabled");
        listener.onEnabled();
    }

    /**
     * Handle both permission and location‐settings results
     */
    public void onPermissionResult(int requestCode) {
        Log.d(TAG, "onPermissionResult(" + requestCode + ")");
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            String[] miss = buildPermissionList();
            if (miss.length > 0) {
                Log.d(TAG, "Permissions still missing → onDisabled");
                listener.onDisabled();
            } else {
                Log.d(TAG, "Permissions granted → re-enter enable()");
                enable();
            }

        } else if (requestCode == LOCATION_REQUEST_CODE) {
            boolean locOn = locationIsOn();
            Log.d(TAG, "Location settings result → isOn=" + locOn);
            if (locOn && buildPermissionList().length == 0 && isWifiOn()) {
                listener.onEnabled();
            } else {
                listener.onDisabled();
            }
        }
    }

    public boolean hasRequiredPermissions() {
        return buildPermissionList().length == 0;
    }

    /**
     * Clean up both receivers
     */
    public void cleanup() {
        try {
            activity.unregisterReceiver(locationCallback);
        } catch (Exception e) {
            Log.w(TAG, "Location receiver unregister failed", e);
        }
        try {
            activity.unregisterReceiver(wifiStateCallback);
        } catch (Exception e) {
            Log.w(TAG, "WiFi state receiver unregister failed", e);
        }
    }

    private boolean isWifiOn() {
        WifiManager mgr = (WifiManager) activity.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        return mgr != null && mgr.isWifiEnabled();
    }

    // — Helper methods below —

    private String[] buildPermissionList() {
        ArrayList<String> list = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.NEARBY_WIFI_DEVICES)
                        != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        String[] permissions = list.toArray(new String[0]);
        Log.d(TAG, "permissions are:" + Arrays.toString(permissions));
        return permissions;
    }

    private boolean locationIsOn() {
        LocationManager mgr = (LocationManager)
                activity.getSystemService(Context.LOCATION_SERVICE);
        if (mgr == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return mgr.isLocationEnabled();
        }
        return mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void promptWifi() {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        activity.startActivity(i);
    }

    private void promptLocation() {
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
                    ((ResolvableApiException) e)
                            .startResolutionForResult(activity, LOCATION_REQUEST_CODE);
                } catch (IntentSender.SendIntentException ex) {
                    Log.e(TAG, "Error launching location settings", ex);
                    listener.onDisabled();
                }
            } else {
                listener.onDisabled();
            }
        });
    }

    public boolean isEnabled() {
        boolean wifiOn = isWifiOn();
        boolean permsOk = buildPermissionList().length == 0;
        boolean locOn = locationIsOn();
        Log.d(TAG, "isEnabled(): wifiOn=" + wifiOn
                + " permsOk=" + permsOk
                + " locationOn=" + locOn);
        return wifiOn && permsOk && locOn;
    }

    public interface Listener {
        void onEnabled();

        void onDisabled();
    }
}