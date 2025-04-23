package com.example.mesh_base.wifi_direct;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.annotation.RequiresPermission;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WifiDirectConnectionHandler extends ConnectionHandler implements ConnectionInfoListener, Handler.Callback {

    private static final String TAG = "my_WifiP2pHandler";
    private static final String SERVICE_INSTANCE = "_myapp";
    private static final String SERVICE_REG_TYPE = "_presence._tcp";
    private static final int MESSAGE_READ = 3;

    private final Context context;
    private final WifiP2pManager manager;
    private final Channel channel;
    private final IntentFilter intentFilter;
    private final Handler handler;
    private final ArrayList<Device> neighborDevices = new ArrayList<>();
    private final Map<UUID, ChatManager> chatManagers = new HashMap<>();
    private final ArrayList<WiFiP2pService> discoveredServices = new ArrayList<>();
    private final WifiDirectPermissions permission;
    private final BroadcastReceiver receiver;
    private final WifiP2pManager.PeerListListener peerListListener = peerList -> {
        // Make a copy of the previous list of discovered devices
        List<Device> previousDevices = new ArrayList<>(neighborDevices);

        // Build a new list from the current peerList
        List<Device> newDevices = new ArrayList<>();
        for (WifiP2pDevice device : peerList.getDeviceList()) {
            newDevices.add(new WifiDirectDeviceWrapper(UUID.randomUUID(), device));
        }

        // For each device in the new list that wasn’t in the previous list, call the connected listener
        for (Device newDevice : newDevices) {
            boolean found = false;
            for (Device oldDevice : previousDevices) {
                if (oldDevice.equals(newDevice)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                onNeighborConnected(newDevice);
            }
        }

        // For each device in the previous list that isn’t in the new list, call the disconnected listener
        for (Device oldDevice : previousDevices) {
            boolean stillPresent = false;
            for (Device newDevice : newDevices) {
                if (newDevice.equals(oldDevice)) {
                    stillPresent = true;
                    break;
                }
            }
            if (!stillPresent) {
                onNeighborDisconnected(oldDevice);
            }
        }

        // Update the discoveredPeers list
        neighborDevices.clear();
        neighborDevices.addAll(newDevices);
    };
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pDnsSdServiceInfo localServiceInfo;
    private boolean isConnected = false;
    private boolean isEnabled = false;

    public WifiDirectConnectionHandler(ComponentActivity context, UUID id) {
        super(context, id);
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper(), this);
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(context, Looper.getMainLooper(), null);
        this.intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        this.receiver = new WifiDirectBroadcastReceiver(manager, channel, this);
        this.permission = new WifiDirectPermissions(context, new WifiDirectPermissions.WifiDirectPermissionListener() {
            @Override
            public void onEnabled() {
                isEnabled = true;
                start();
            }

            @Override
            public void onPermissionsDenied() {
                isEnabled = false;
                stop();
            }

            @Override
            public void onWifiDisabled() {
                isEnabled = false;
                stop();
            }

            @Override
            public void onLocationDisabled() {
                isEnabled = false;
                stop();
            }
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void start() {
        isConnected = true;

        context.registerReceiver(receiver, intentFilter);
        Map<String, String> record = new HashMap<>();
        record.put("available", "visible");
        localServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, localServiceInfo, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Added local service");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to add local service: " + reason);
            }
        });

        manager.setDnsSdResponseListeners(channel, new DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
                if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                    WiFiP2pService service = new WiFiP2pService();
                    service.device = srcDevice;
                    service.instanceName = instanceName;
                    service.serviceRegistrationType = registrationType;
                    discoveredServices.add(service);
                    Log.d(TAG, "Discovered service: " + srcDevice.deviceName);
                    if (!isConnected) {
                        connectToService(service);
                    }
                }
            }
        }, (fullDomainName, record1, device) -> Log.d(TAG, "TxtRecord available for " + device.deviceName + ": " + record1));

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Added service request");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to add service request: " + reason);
            }
        });

        manager.discoverServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Service discovery failed: " + reason);
            }
        });

        onConnected();
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    private void connectToService(WiFiP2pService service) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        manager.connect(channel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connecting to " + service.device.deviceName);
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to connect to " + service.device.deviceName + ": " + reason);
            }
        });
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {
        if (p2pInfo.groupFormed) {
            isConnected = true;
            if (p2pInfo.isGroupOwner) {
                Log.d(TAG, "Connected as group owner");
                try {
                    GroupOwnerSocketHandler socketHandler = new GroupOwnerSocketHandler(handler, id, this);
                    socketHandler.start();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to start group owner socket handler", e);
                }
            } else {
                Log.d(TAG, "Connected as client");
                ClientSocketHandler socketHandler = new ClientSocketHandler(handler, p2pInfo.groupOwnerAddress, id, this);
                socketHandler.start();
            }
        } else {
            isConnected = false;
        }
    }

    @Override
    public ArrayList<Device> getNeighbourDevices() {
        return neighborDevices;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void stop() {
        try {
            context.unregisterReceiver(receiver);

            if (localServiceInfo != null) {
                removeLocalService();
            }

            if (serviceRequest != null) {
                removeServiceRequest();
            }

            removeGroup();
        } catch (Exception e) {
            Log.e(TAG, "Failed to Stop WifiConnectionConnectionHandler - ", e);
        } finally {
            isConnected = false;
            onDisconnected();
        }
    }

    @Override
    public boolean isOn() {
        return isConnected;
    }

    @Override
    public void send(byte[] data) throws SendError {
        Log.d(TAG, "Send data called" + Arrays.toString(data) + "-" + chatManagers.isEmpty());
//        if (chatManagers.isEmpty()) {
//            throw new SendError("No connected neighbors to send data to");
//        }
        for (ChatManager chatManager : chatManagers.values()) {
            chatManager.write(data);
        }
    }

    @Override
    public void send(byte[] data, Device neighbor) throws SendError {
        WifiDirectDeviceWrapper device = (WifiDirectDeviceWrapper) neighbor;
        UUID uuid = device.uuid;
        ChatManager chatManager = chatManagers.get(uuid);
        if (chatManager != null) {
            chatManager.write(data);
        } else {
            throw new SendError("No ChatManager for neighbor: " + device.name);
        }
    }

    @Override
    public void enable() {
        permission.enable();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isSupported() {
        return manager != null;
    }

    public void addNeighbor(UUID uuid, String name, ChatManager chatManager) {
        WifiP2pDevice p2pDevice = new WifiP2pDevice();
        p2pDevice.deviceName = name;
        Device device = new WifiDirectDeviceWrapper(uuid, p2pDevice);
        neighborDevices.add(device);
        chatManagers.put(uuid, chatManager);
        onNeighborConnected(device);
    }

    public void removeNeighbor(UUID uuid) {
        for (Device device : neighborDevices) {
            if (device.uuid.equals(uuid)) {
                neighborDevices.remove(device);
                chatManagers.remove(uuid);
                onNeighborDisconnected(device);
                break;
            }
        }
    }

    public void onDisconnected() {
        isConnected = false;
        neighborDevices.clear();
        chatManagers.clear();
        Log.d(TAG, "Disconnected from WifiDirect group");
    }

    @Override
    public boolean handleMessage(Message msg) {
        Log.d(TAG, "handleMessage called");
        if (msg.what == MESSAGE_READ) {
            byte[] data = (byte[]) msg.obj;
            int length = msg.arg1;
            String message = new String(data, 0, length);
            Log.d(TAG, "Received message: " + message);

            onDataReceived(
                    new WifiDirectDeviceWrapper(UUID.randomUUID(), new WifiP2pDevice()),
                    msg.toString().getBytes()
            );
        }

        return true;
    }

    private void removeGroup() {
        manager.removeGroup(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed group");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to remove group: " + reason);
            }
        });
    }

    private void removeServiceRequest() {
        manager.removeServiceRequest(channel, serviceRequest, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed service request");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to remove service request: " + reason);
            }
        });
        serviceRequest = null;
    }

    private void removeLocalService() {
        manager.removeLocalService(channel, localServiceInfo, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed local service");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to remove local service: " + reason);
            }
        });
        localServiceInfo = null;
    }

    // Supporting classes defined within the same file for simplicity

    static class WiFiP2pService {
        WifiP2pDevice device;
        String instanceName;
        String serviceRegistrationType;
    }

    static class GroupOwnerSocketHandler extends Thread {
        private static final String TAG = "GroupOwnerSocketHandler";
        private static final int PORT = 4545;
        private final ServerSocket serverSocket;
        private final Handler handler;
        private final UUID myUuid;
        private final WifiDirectConnectionHandler connectionHandler;

        public GroupOwnerSocketHandler(Handler handler, UUID myUuid, WifiDirectConnectionHandler connectionHandler) throws IOException {
            this.handler = handler;
            this.myUuid = myUuid;
            this.connectionHandler = connectionHandler;
            serverSocket = new ServerSocket(PORT);
            Log.d(TAG, "Server socket started on port " + PORT);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Log.d(TAG, "Client connected: " + clientSocket.getInetAddress());
                    ChatManager chatManager = new ChatManager(clientSocket, handler, myUuid, connectionHandler);
                    new Thread(chatManager).start();
                } catch (IOException e) {
                    Log.e(TAG, "Error accepting client connection", e);
                    try {
                        if (!serverSocket.isClosed()) {
                            serverSocket.close();
                        }
                    } catch (IOException ioe) {
                        Log.e(TAG, "Error closing server socket", ioe);
                    }
                    break;
                }
            }
        }
    }

    static class ClientSocketHandler extends Thread {
        private static final String TAG = "ClientSocketHandler";
        private static final int PORT = 4545;
        private final Handler handler;
        private final InetAddress groupOwnerAddress;
        private final UUID myUuid;
        private final WifiDirectConnectionHandler connectionHandler;

        public ClientSocketHandler(Handler handler, InetAddress groupOwnerAddress, UUID myUuid, WifiDirectConnectionHandler connectionHandler) {
            this.handler = handler;
            this.groupOwnerAddress = groupOwnerAddress;
            this.myUuid = myUuid;
            this.connectionHandler = connectionHandler;
        }

        @Override
        public void run() {
            Socket socket = new Socket();
            try {
                socket.bind(null);
                socket.connect(new InetSocketAddress(groupOwnerAddress, PORT), 5000);
                Log.d(TAG, "Connected to group owner");
                ChatManager chatManager = new ChatManager(socket, handler, myUuid, connectionHandler);
                new Thread(chatManager).start();
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to group owner", e);
                try {
                    socket.close();
                } catch (IOException e1) {
                    Log.e(TAG, "Error closing socket", e1);
                }
            }
        }
    }

    static class ChatManager implements Runnable {
        private static final String TAG = "ChatManager";
        private final Socket socket;
        private final Handler handler;
        private final UUID myUuid;
        private final WifiDirectConnectionHandler connectionHandler;
        private InputStream inputStream;
        private OutputStream outputStream;
        private UUID remoteUuid;

        public ChatManager(Socket socket, Handler handler, UUID myUuid, WifiDirectConnectionHandler connectionHandler) {
            this.socket = socket;
            this.handler = handler;
            this.myUuid = myUuid;
            this.connectionHandler = connectionHandler;
        }

        @Override
        public void run() {
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                // Send my UUID and name
                // TODO: Replace with actual device name
                String myInfo = myUuid.toString() + "|" + "MyDeviceName";
                outputStream.write(myInfo.getBytes());
                outputStream.flush();

                // Read remote info
                byte[] buffer = new byte[1024];
                int bytesRead = inputStream.read(buffer);
                if (bytesRead > 0) {
                    String infoStr = new String(buffer, 0, bytesRead);
                    String[] parts = infoStr.split("\\|");
                    if (parts.length == 2) {
                        remoteUuid = UUID.fromString(parts[0]);
                        String remoteName = parts[1];
                        connectionHandler.addNeighbor(remoteUuid, remoteName, this);
                    } else {
                        Log.e(TAG, "Invalid remote info received");
                    }
                }

                // Handle message reading
                while (true) {
                    try {
                        bytesRead = inputStream.read(buffer);
                        if (bytesRead == -1) {
                            break;
                        }
                        handler.obtainMessage(MESSAGE_READ, bytesRead, -1, buffer.clone()).sendToTarget();
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading from socket", e);
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error in ChatManager", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing socket", e);
                }
                if (remoteUuid != null) {
                    connectionHandler.removeNeighbor(remoteUuid);
                }
            }
        }

        public void write(byte[] data) {
            try {
                outputStream.write(data);
                outputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error writing to socket", e);
            }
        }
    }

    static class WifiDirectBroadcastReceiver extends BroadcastReceiver {
        private final WifiP2pManager manager;
        private final Channel channel;
        private final WifiDirectConnectionHandler connectionHandler;

        public WifiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel, WifiDirectConnectionHandler connectionHandler) {
            this.manager = manager;
            this.channel = channel;
            this.connectionHandler = connectionHandler;
        }

        @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received action: " + action);
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d(TAG, "Wifi P2P State Enabled!");
                } else {
                    Log.d(TAG, "Wifi P2P State Disabled!");
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    if (networkInfo.isConnected()) {
                        manager.requestConnectionInfo(channel, connectionHandler);
                    } else {
                        connectionHandler.onDisconnected();
                    }
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (device != null) {
                    Log.d(TAG, "Device status: " + device.status);
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                manager.requestPeers(channel, connectionHandler.peerListListener);
            }
        }
    }
}