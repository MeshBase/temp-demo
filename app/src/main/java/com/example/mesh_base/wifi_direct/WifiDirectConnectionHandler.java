package com.example.mesh_base.wifi_direct;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;
import com.example.mesh_base.wifi_direct.WifiDirectPermissions.Listener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WifiDirectConnectionHandler extends ConnectionHandler implements ConnectionInfoListener {

    private static final String TAG = "my_WifiP2pHandler";
    private static final String SERVICE_INSTANCE = "_my mesh base";
    private static final String SERVICE_REG_TYPE = "_presence._tcp";
    private static final long DISCOVERY_INTERVAL = 5000; // 5 seconds
    protected final Map<UUID, CommunicationManager> chatManagers = new ConcurrentHashMap<>();
    private final Context context;
    private final WifiP2pManager manager;
    private final Channel channel;
    private final Map<UUID, WifiDirectDeviceWrapper> neighborDevices = new ConcurrentHashMap<>();
    private final ArrayList<WifiP2pService> discoveredServices = new ArrayList<>();
    private final WifiDirectPermissions permission;
    private final BroadcastReceiver receiver;
    private final boolean supported;
    private final Handler discoveryHandler;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pDnsSdServiceInfo localServiceInfo;
    private boolean isOn = false;
    private boolean wifiDirectEnabled = false;
    private final Runnable discoveryRunnable = new Runnable() {
        @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
        @Override
        public void run() {
            if (isOn && wifiDirectEnabled) {
                discoverServices();
                discoveryHandler.postDelayed(this, DISCOVERY_INTERVAL);
            }
        }
    };
    private GroupOwnerSocketHandler groupOwnerSocketHandler;

    public WifiDirectConnectionHandler(ComponentActivity context, UUID id) {
        super(context, id);
        this.context = context;
        this.discoveryHandler = new Handler(Looper.getMainLooper());
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager != null ? manager.initialize(context, Looper.getMainLooper(), null) : null;
        this.supported = manager != null;
        IntentFilter intentFilter = getIntentFilter();
        this.receiver = new WifiDirectBroadcastReceiver(manager, channel, this);
        this.permission = new WifiDirectPermissions(context, new Listener() {
            @Override
            public void onEnabled() {
                Log.d(TAG, "WifiDirect Enabled");
                start();
            }

            @Override
            public void onPermissionsDenied() {
                Log.d(TAG, "WifiDirect Disabled");
                stop();
            }
        });

        ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @NonNull
    private static IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        // Comment for future me:
        // Indicates whether Wi-Fi Direct is enabled
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indicates that the available peer list has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indicates the state of Wi-Fi Direct connectivity has changed. Starting
        // with Android 10, this is not sticky. If your app has relied on receiving
        // these broadcasts at registration because they had been sticky, use the
        // appropriate get method at initialization to obtain the information instead.
        // In Short it's kind of deprecated
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // Indicates this device's configuration details have changed. Starting with
        // Android 10, this is not sticky. If your app has relied on receiving these
        // broadcasts at registration because they had been sticky, use the appropriate
        // get method at initialization to obtain the information instead.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        return intentFilter;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void start() {
        Log.d(TAG, "Start Called");
        isOn = true;

        if (!supported) {
            Log.e(TAG, "Cannot start: WiFi Direct not supported");
            return;
        }

        startPeerDiscovery();
        // Advertise service with UUID
        Map<String, String> record = new HashMap<>() {
        };
        record.put("available", "visible");
        record.put("uuid", id.toString());

        localServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        addLocalService();
        setupDNSResponseListener();
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        setServiceResponseListener();
        addServiceRequest();
        discoverServices();
        this.discoveryHandler.postDelayed(discoveryRunnable, DISCOVERY_INTERVAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.requestP2pState(
                    channel,
                    state -> {
                        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            wifiDirectEnabled = true;
                            onConnected();
                        } else {
                            wifiDirectEnabled = false;
                            onDisconnected();
                        }
                    }
            );
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    private void setupDNSResponseListener() {
        manager.setDnsSdResponseListeners(channel,
                (instanceName, registrationType, srcDevice) -> {
                    if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                        WifiP2pService service = new WifiP2pService();
                        service.device = srcDevice;
                        service.instanceName = instanceName;
                        service.serviceRegistrationType = registrationType;
                        synchronized (discoveredServices) {
                            discoveredServices.add(service);
                        }
                        Log.d(TAG, "* Discovered service: " + srcDevice.deviceName);
                        connectToService(service);
//                         this.addNeighbor(remoteUuid, device.deviceName);
                    }
                },
                (fullDomainName, record1, device) -> {
                    String uuidStr = record1.get("uuid");
                    if (uuidStr != null) {
                        try {
                            UUID remoteUuid = UUID.fromString(uuidStr);
                            synchronized (discoveredServices) {
                                for (WifiP2pService service : discoveredServices) {
                                    if (service.device.equals(device)) {
                                        service.uuid = remoteUuid;
                                        Log.d(TAG, "Assigned UUID " + remoteUuid + " to service: " + device.deviceName);


                                        if (!this.neighborDevices.containsKey(remoteUuid)) {
                                            this.addNeighbor(remoteUuid, device.deviceName);
                                        }

                                        break;
                                    }
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            Log.e(TAG, "Invalid UUID format: " + uuidStr, e);
                        }
                    } else {
                        Log.w(TAG, "No UUID found in TXT record for device: " + device.deviceName);
                    }
                }
        );
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    private void addLocalService() {
        if (localServiceInfo == null) {
            Log.e(TAG, "Localserviceinfo is null");
            return;
        }

        manager.addLocalService(channel, localServiceInfo, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Added local service with UUID: " + id);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to add local service: " + reason);
            }
        });
    }

    private void addServiceRequest() {
        if (serviceRequest != null) {
            manager.removeServiceRequest(channel, serviceRequest, new ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Cleared previous service request");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to clear service request: " + reason);
                }
            });
        }
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Added service request");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to add service request: " + reason);
            }
        });
    }

    private void setServiceResponseListener() {
        manager.setServiceResponseListener(channel, new WifiP2pManager.ServiceResponseListener() {
            @RequiresApi(api = Build.VERSION_CODES.R)
            @Override
            public void onServiceAvailable(int i, byte[] bytes, WifiP2pDevice wifiP2pDevice) {
                String str = new String(bytes, StandardCharsets.UTF_8); // for UTF-8 encoding
                Log.d(TAG, "Bytes : " + str);
                Log.d(TAG, "i : " + i);
                Log.d(TAG, "Device : " + wifiP2pDevice.deviceAddress + wifiP2pDevice.deviceName + wifiP2pDevice.getWfdInfo());
            }
        });
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    private void discoverServices() {
        manager.discoverServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "**** Service discovery started");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "**** Service discovery failed due to : " + reason);
            }
        });
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    public void connectToService(WifiP2pService service) {
        Log.d(TAG, "Connecting to service called ..." + service.uuid);

        if (service.device == null || service.device.deviceAddress == null) {
            Log.e(TAG, "Cannot connect: Invalid device or device address");
            return;
        }
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        manager.connect(channel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Initiated connection to " + service.device.deviceName);
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "Failed to connect to " + service.device.deviceName + ": " + i);
            }
        });
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {
        if (p2pInfo.groupFormed) {
            isOn = true;
            if (p2pInfo.isGroupOwner) {
                Log.d(TAG, "Connecting as group owner ...");
                try {
                    groupOwnerSocketHandler = new GroupOwnerSocketHandler(id, this);
                    groupOwnerSocketHandler.start();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to start group owner socket handler", e);
                }
            } else {
                Log.d(TAG, "Connecting as client ...");
                ClientSocketHandler socketHandler = new ClientSocketHandler(p2pInfo.groupOwnerAddress, id, this);
                socketHandler.start();
            }
        } else {
            isOn = false;
            onDisconnected();
        }
    }

    @Override
    public ArrayList<Device> getNeighbourDevices() {
        synchronized (neighborDevices) {
            return new ArrayList<>(neighborDevices.values());
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void stop() {
        Log.d(TAG, "Stop Called;");
        if (groupOwnerSocketHandler != null) {
            groupOwnerSocketHandler.stopServer();
            groupOwnerSocketHandler = null;
        }

        for (Device device : neighborDevices.values()) {
            this.onNeighborDisconnected(device);
        }

        synchronized (neighborDevices) {
            neighborDevices.clear();
        }

        for (CommunicationManager manager : chatManagers.values()) {
            manager.close();
        }
        chatManagers.clear();

        removeLocalService();
        removeServiceRequest();
        cancelConnection();
        removeGroup();
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
        isOn = false;
        onDisconnected();
        discoveryHandler.removeCallbacks(discoveryRunnable);
    }

    @Override
    public boolean isOn() {
        return isOn;
    }

    @Override
    public void send(byte[] data) throws SendError {
        if (chatManagers.isEmpty()) {
            throw new SendError("No connected neighbors to send data to");
        }
        for (CommunicationManager chatManager : chatManagers.values()) {
            chatManager.write(data);
            Log.d(TAG, "Sent data to all neighbors: " + Arrays.toString(data));
        }
    }

    @Override
    public void send(byte[] data, Device neighbor) throws SendError {
        WifiDirectDeviceWrapper device = (WifiDirectDeviceWrapper) neighbor;
        UUID uuid = device.uuid;
        CommunicationManager chatManager = chatManagers.get(uuid);
        if (chatManager != null) {
            chatManager.write(data);
            Log.d(TAG, "Sent data to " + device.name + ": " + Arrays.toString(data));
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
        return wifiDirectEnabled;
    }

    @Override
    public boolean isSupported() {
        return supported;
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    public void addNeighbor(UUID uuid, String name) {
        Log.d(TAG, "addNeighbor called with UUID: " + uuid + ", Name: " + name);
        WifiP2pDevice p2pDevice = new WifiP2pDevice();
        p2pDevice.deviceName = name;
        WifiDirectDeviceWrapper device = new WifiDirectDeviceWrapper(uuid, p2pDevice);
        synchronized (neighborDevices) {
            neighborDevices.put(device.uuid, device);
        }
        onNeighborConnected(device);
        Log.d(TAG, "Added neighbor: " + name + " with UUID: " + uuid);
        // Find corresponding service and connect
        synchronized (discoveredServices) {
            for (WifiP2pService service : discoveredServices) {
                if (service.uuid != null && service.uuid.equals(uuid)) {
                    connectToService(service);
                    break;
                }
            }
        }
    }

//    public void addNeighbor(UUID uuid, String name) {
//        Log.d(TAG, "addNeighbor called with" + "UUID" + uuid + "Name" + name);
//        WifiP2pDevice p2pDevice = new WifiP2pDevice();
//        p2pDevice.deviceName = name;
//        WifiDirectDeviceWrapper device = new WifiDirectDeviceWrapper(uuid, p2pDevice);
//        synchronized (neighborDevices) {
//            neighborDevices.put(device.uuid, device);
//        }
//        chatManagers.put(uuid, communicationManager);
//        onNeighborConnected(device);
//        Log.d(TAG, "Added neighbor: " + name + " with UUID: " + uuid);
//    }

    public void removeNeighbor(UUID uuid) {
        synchronized (neighborDevices) {
            for (Device device : neighborDevices.values()) {
                if (device.uuid.equals(uuid)) {
                    neighborDevices.remove(device);
                    chatManagers.remove(uuid);
                    onNeighborDisconnected(device);
                    Log.d(TAG, "Removed neighbor with UUID: " + uuid);
                    break;
                }
            }
        }
    }

    public void onDisconnected() {
        isOn = false;
        synchronized (neighborDevices) {
            neighborDevices.clear();
        }
        chatManagers.clear();
        Log.d(TAG, "Disconnected from WifiDirect group");
    }

    private void removeGroup() {
        manager.removeGroup(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Removed group");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to remove group: " + reason);
            }
        });
    }

    private void cancelConnection() {
        manager.cancelConnect(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Cancelled connect");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to cancel connect: " + reason);
            }
        });
    }

    private void removeServiceRequest() {
        if (serviceRequest != null) {
            manager.removeServiceRequest(channel, serviceRequest, new ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Removed service request");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to remove service request: " + reason);
                }
            });
            serviceRequest = null;
        }
    }

    private void removeLocalService() {
        if (localServiceInfo != null) {
            manager.removeLocalService(channel, localServiceInfo, new ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Removed local service");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to remove local service: " + reason);
                }
            });
            localServiceInfo = null;
        }
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    public void startPeerDiscovery() {
        if (!isEnabled()) {
            return;
        }

        try {
            Log.d(TAG, "Requesting peers list");
//            manager.requestPeers(channel,
//                    peerList -> {
//                        Collection<WifiP2pDevice> refreshedPeers = peerList.getDeviceList();
//
//                        for (WifiDirectDeviceWrapper removedDevice : neighborDevices) {
//                            WifiDirectConnectionHandler.this.onNeighborDisconnected(removedDevice);
//                        }
//
//                        neighborDevices.clear();
//
//                        for (WifiP2pDevice newDevice : refreshedPeers) {
//                            Log.d(TAG, "New Device Address" + newDevice.deviceAddress);
//                            WifiDirectDeviceWrapper addedDevice = new WifiDirectDeviceWrapper(UUID.randomUUID(), newDevice);
//                            WifiDirectConnectionHandler.this.onNeighborConnected(addedDevice);
//                            neighborDevices.add(addedDevice);
//                        }
//                    }
//            );
        } catch (Exception e) {
            Log.d(TAG, "Something went wrong here!!!!");
        }

        manager.discoverPeers(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Peer discovery Successful;");
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "Peer discovery Failed;");
            }
        });
    }
}
