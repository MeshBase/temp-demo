package com.example.mesh_base.wd;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;


public class WiFiDirectConnectionHandler extends ConnectionHandler {
    private static final String TAG = "my_wifihandler";
    private static final String SERVICE_TYPE = "_http._tcp."; // mDNS service type
    private final Activity activity;
    private final WifiDirectPermissions permissions;
    private final int AD_PORT = 50000;
    private final int SERVER_PORT = 50000;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private String SERVICE_NAME_PREFIX = "MeshBase|";
    private HashMap<UUID, WifiDevice> connectedDevices = new HashMap<>();

    private volatile boolean running = false;
    // Connected peers and sockets

    public WiFiDirectConnectionHandler(Activity context, UUID id) {
        super(context, id);
        this.activity = context;
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);

        this.permissions = new WifiDirectPermissions(context, new WifiDirectPermissions.Listener() {
            @Override
            public void onEnabled() {
                start();
            }

            @Override
            public void onDisabled() {
                stop();
            }
        });

    }


    private void startDiscovery() {
        //Stop if already listening
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed: Error code:" + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Service discovery stopped");
            }


            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo);
                // Resolve service to get details like IP and port
                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        Log.e(TAG, "Resolve failed: " + errorCode);
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        Log.d(TAG, "Resolved: " + serviceInfo.getServiceName() +
                                ", Host: " + serviceInfo.getHost() +
                                ", Port: " + serviceInfo.getPort());
                        // Use host and port for communication

                        //check if is a mesh base wifi device
                        String serviceName = serviceInfo.getServiceName();

                        if (!serviceName.startsWith(SERVICE_NAME_PREFIX) || serviceName.length() != (SERVICE_NAME_PREFIX.length() + id.toString().length())) {
                            Log.d(TAG, "not a mesh base device, not connecting");
                            return;
                        }

                        UUID neighborUUID = UUID.fromString(serviceName.substring(SERVICE_NAME_PREFIX.length()));
                        if (neighborUUID.equals(id)) {
                            Log.d(TAG, "discovered self, skipping connection");
                            return;
                        }
                        if (neighborUUID.compareTo(id) < 0) {
                            Log.d(TAG, "has lesser uuid, skipp initiating connection as client");
                            return;
                        }

                        WifiDevice device = new WifiDevice(serviceInfo.getHost().getHostName(), serviceInfo.getHost(), serviceInfo.getPort());
                        WifiDevice existingDevice = connectedDevices.get(neighborUUID);
                        if (existingDevice != null && device.equals(existingDevice)) {
                            Log.d(TAG, "rediscovered existing connection, skipping connecting");
                            return;
                        }

                        device.connect(getConnectListener(device), id, true);
                    }
                });
            }


            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo);
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private void handleAcceptedClient(WifiDevice device, Socket clientSocket, UUID selfId) {
        new Thread(() -> {
            try {
                device.setSocket(clientSocket); // Set the socket directly
                // Perform UUID exchange (server-side: receive first, then send)
                device.receiveAndStoreUUID();
                device.sendUUID(selfId);
                Log.d(TAG, "Completed UUID exchange with: " + device.uuid);
                getConnectListener(device).onConnectionSucceeded();

                // Start listening for data
                try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream())) {
                    while (true) {
                        int size = inputStream.readInt();
                        byte[] data = new byte[size];
                        inputStream.readFully(data);
                        Log.d(TAG, "received data: " + Arrays.toString(data));
                        getConnectListener(device).onData(data);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Client handling failed: " + e);
                getConnectListener(device).onConnectionFail();
            } finally {
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not close client socket: " + e);
                }
            }
        }).start();
    }

    private WifiDevice.Listener getConnectListener(WifiDevice device) {
        return new WifiDevice.Listener() {
            @Override
            public void onData(byte[] data) {
                onDataReceived(device, data);
            }

            @Override
            public void onConnectionFail() {
                connectedDevices.remove(device.uuid, device);
                onNeighborDisconnected(device);
            }

            @Override
            public void onConnectionSucceeded() {
                connectedDevices.put(device.uuid, device);
                onNeighborConnected(device);
            }
        };
    }

    private void startAdvertisement() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("MeshBase|" + id);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(AD_PORT);

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Unregistration failed: " + errorCode);
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service registered: " + serviceInfo.getServiceName());
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service unregistered");
            }
        });
    }

    private void startAcceptingClients() {
        new Thread(() -> {
            try (ServerSocket socket = new ServerSocket(SERVER_PORT);) {
                socket.setReuseAddress(true);
                Log.d(TAG, "server socket started");
                while (true) {
                    Socket clientSocket = socket.accept();
                    Log.d(TAG, "client socket accepted");
                    InetAddress ip = clientSocket.getInetAddress();
                    String name = "WifiDev:" + ip.toString();
                    WifiDevice device = new WifiDevice(name, ip, SERVER_PORT);
                    handleAcceptedClient(device, clientSocket, id);
                }
            } catch (Exception e) {
                Log.e(TAG, "could not create server socket:" + e);
            }
        }).start();
    }

    @Override
    public void enable() {
        Log.d(TAG, "Enabling...");
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
        return nsdManager != null;
    }

    @Override
    public void start() {
        if (!running && isEnabled()) {
            Log.d(TAG, "Starting...");
            running = true;
            startDiscovery();
            startAdvertisement();
            startAcceptingClients();
        } else {
            Log.d(TAG, "not starting. already started=" + running + " isEnabled()=" + isEnabled());
        }
    }

    public void stop() {
        Log.d(TAG, "Stopping...");
        running = false;
        if (discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener);
        }
        onDisconnected();
    }

    @Override
    public boolean isOn() {
        return running;
    }

    @Override
    public ArrayList<Device> getNeighbourDevices() {
        return new ArrayList<>(connectedDevices.values());
    }

    @Override
    public void send(byte[] data) throws SendError {
        for (Device d : getNeighbourDevices()) {
            send(data, d);
        }
    }

    @Override
    public void send(byte[] data, Device device) throws SendError {
        WifiDevice foundDevice = connectedDevices.get(device.uuid);
        if (foundDevice == null) {
            throw new SendError("device:" + device.name + " not found");
        }

        if (!foundDevice.send(data)) {
            throw new SendError("could not send data to :" + device.name);
        }
    }
}

class WifiDirectPermissions {
    private static final String TAG = "my_wifiDirect-permissions";
    private static final int PERMISSIONS_REQUEST_CODE = 1368;
    private final Activity activity;
    private final Listener listener;

    public WifiDirectPermissions(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;

        IntentFilter wifiFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);

        BroadcastReceiver wifiStateCallback = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                switch (wifiState) {
                    case WifiManager.WIFI_STATE_ENABLED:
                        Log.d(TAG, "Wi-Fi enabled");
                        enable();
                        break;
                    case WifiManager.WIFI_STATE_DISABLED:
                        Log.d(TAG, "Wi-Fi disabled");
                        listener.onDisabled();
                        break;
                    case WifiManager.WIFI_STATE_ENABLING:
                        Log.d(TAG, "Wi-Fi enabling");
                        break;
                    case WifiManager.WIFI_STATE_DISABLING:
                        Log.d(TAG, "Wi-Fi disabling");
                        break;
                    default:
                        Log.d(TAG, "Wi-Fi state unknown");
                        break;
                }
            }
        };
        activity.registerReceiver(wifiStateCallback, wifiFilter);
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

        } else {
            Log.d(TAG, "unknown request code");
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

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_WIFI_STATE);
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.INTERNET);
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE);
        }

        String[] permissions = list.toArray(new String[0]);
        Log.d(TAG, "permissions are:" + Arrays.toString(permissions));
        return permissions;
    }

    private void promptWifi() {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        activity.startActivity(i);
    }

    public boolean isEnabled() {
        boolean wifiOn = isWifiOn();
        boolean permsOk = buildPermissionList().length == 0;
        Log.d(TAG, "isEnabled(): wifiOn=" + wifiOn
                + " permsOk=" + permsOk);
        return wifiOn && permsOk;
    }

    public interface Listener {
        void onEnabled();

        void onDisabled();
    }
}

class WifiDevice extends Device {
    private final String TAG;
    private final int port;
    private InetAddress ip;
    private Socket socket;
    private Listener listener;

    public WifiDevice(String name, InetAddress ip, int port) {
        super(null, name);
        TAG = "my_wifidevice:" + name;
        this.port = port;
        this.ip = ip;
    }

    public UUID bytesToUUID(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Byte array must be 16 bytes long");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }

    public byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public void sendUUID(UUID selfId) throws IOException {
        //send uuid
        socket.getOutputStream().write(uuidToBytes(selfId));
        socket.getOutputStream().flush();
    }

    public void receiveAndStoreUUID() throws Exception {
        //accept uuid
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        int UUID_SIZE = 16;
        byte[] uuidBytes = new byte[UUID_SIZE];
        int readBytes = inputStream.read(uuidBytes);
        if (readBytes != UUID_SIZE) {
            throw new Exception("received uuid of size:" + readBytes + " instead of:" + UUID_SIZE);
        }

        this.uuid = bytesToUUID(uuidBytes);
        Log.d(TAG, "Resolved Bytes ... " + bytesToUUID(uuidBytes));
    }

    public void connect(Listener listener, UUID selfId, boolean asClient) {
        Log.d(TAG, "trying to connect to " + name);
        if (this.listener != null) {
            Log.e(TAG, "should not connect twice");
            return;
        }

        this.listener = listener;
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                Log.d(TAG, "socket opened with:" + ip);
                listener.onConnectionSucceeded();

                if (asClient) {
                    sendUUID(selfId);
                    receiveAndStoreUUID();
                } else {
                    receiveAndStoreUUID();
                    sendUUID(selfId);
                }

                Log.d(TAG, "completed uuid exchange with:" + uuid);
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                while (true) {
                    int size = inputStream.readInt();
                    byte[] data = new byte[size];
                    inputStream.readFully(data);
                    Log.d(TAG, "received data:" + Arrays.toString(data));
                    listener.onData(data);
                }
            } catch (Exception e) {
                Log.e(TAG, "connection failed:" + e);
            } finally {
                listener.onConnectionFail();
                try {
                    if (socket != null) {
                        socket.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "could not close socket:" + e);
                }
            }

        }).start();
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public boolean send(byte[] data) {
        if (socket == null || uuid == null) {
            Log.d(TAG, "cant send because socketIsNull=" + (socket == null) + " uuidIsNull=" + (uuid == null));
            return false;
        }

        new Thread(() -> {
            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(data.length);
                outputStream.write(data);
                outputStream.flush();
                Log.d(TAG, "send data:" + Arrays.toString(data));
            } catch (Exception e) {
                Log.e(TAG, "sending failed" + e);
                listener.onConnectionFail();
                try {
                    if (socket != null) {
                        socket.close();
                    }
                } catch (Exception e2) {
                    Log.e(TAG, "could not close socket after send fail:" + e2);
                }
            }
        }
        ).start();
        return true;
    }

    public boolean equals(WifiDevice other) {
        return other.uuid.equals(uuid) && other.port == port && other.ip.equals(ip);
    }


    public interface Listener {
        void onData(byte[] data);

        void onConnectionFail();

        void onConnectionSucceeded();
    }
}