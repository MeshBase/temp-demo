package com.example.mesh_base.wifi_direct;

import static androidx.core.content.ContextCompat.getSystemService;
import static android.Manifest.permission.*;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Looper;
import android.util.Log;

import androidx.activity.ComponentActivity;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.Device;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class WifiDirectConnectionHandler extends ConnectionHandler {

    private final String TAG = "my_WifiP2pHandler";
    private final Context context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final IntentFilter intentFilter;

    // Hold lists of devices (you may need to update these as events occur)
    private final ArrayList<Device> neighborDevices = new ArrayList<>();
    private final ArrayList<Device> discoveredPeers = new ArrayList<>();
    private final WifiDirectPermissions permission;

    public WifiDirectConnectionHandler(ComponentActivity context, UUID id) {
        super(context, id);

        this.context = context;
        this.permission = new WifiDirectPermissions(context);

        // Set up the intent filter for Wi-Fi P2P actions.
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        // Use the main looper for channel callbacks
        channel = manager.initialize(context, getLooper(), null);
    }


    // BroadcastReceiver for Wi-Fi Direct events
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received action: " + action);
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Handle Wi-Fi P2P state change if needed
                // I'll check what the state change is here
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // set something to true here, maybe modify a flag of state
                } else {
                    // here too: to false
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // When peers change, request the current list
                manager.requestPeers(channel, peerListListener);
                // then update the peers list
                // then call the notify change

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Handle connection changes, update neighborDevices as needed
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Update this device's details if needed
            }
        }
    };

    // Listener to get the list of available peers
    private final WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            // Make a copy of the previous list of discovered devices
            List<Device> previousDevices = new ArrayList<>(discoveredPeers);

            // Build a new list from the current peerList
            List<Device> newDevices = new ArrayList<>();
            for (WifiP2pDevice device : peerList.getDeviceList()) {
                newDevices.add(new WifiP2pDeviceWrapper(device));
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
                if (!found && neighborConnectedListener != null) {
                    neighborConnectedListener.onEvent(newDevice);
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
                if (!stillPresent && neighborDisconnectedListener != null) {
                    neighborDisconnectedListener.onEvent(oldDevice);
                }
            }

            // Update the discoveredPeers list
            discoveredPeers.clear();
            discoveredPeers.addAll(newDevices);

            // Optionally, notify the general discovered listener with the updated list
            if (nearbyDevicesListener != null) {
                nearbyDevicesListener.onEvent(discoveredPeers);
            }
        }

    };

    // Return the main looper
    private Looper getLooper() {
        return Looper.getMainLooper();
    }

    @Override
    public ArrayList<WifiP2pDeviceWrapper> getNeighbourDevices() {
        // Return list of connected/paired devices
        return neighborDevices;
    }


    @SuppressLint("MissingPermission")
    @Override
    public void start() throws Exception {
        // Register the broadcast receiver
        context.registerReceiver(receiver, intentFilter);
        // Start discovering peers
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Peer discovery started successfully");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Peer discovery failed: " + reason);
            }
        });
    }

    @Override
    public void stop() {
        try {
            // Optionally cancel discovery or disconnect from any groups
            manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Peer discovery stopped");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to stop peer discovery: " + reason);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error stopping peer discovery", e);
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
    }

    @Override
    public ArrayList<Device> getNearbyDevices() {
        // Return the list of discovered peers
        return discoveredPeers;
    }

    @Override
    public void send(byte[] data) throws SendError {
        // Broadcast data to all connected neighbor devices.
        for (Device neighbor : getNeighbourDevices()) {
            send(data, neighbor);
        }
    }

    @Override
    public void send(byte[] data, Device neighbor) throws SendError {
        // Implement socket-based communication for sending data.
        // This is a placeholder – you'll need to set up a Wi-Fi P2P socket connection.
        try {
            // Example pseudo-code:
            // Socket socket = neighbor.getSocket();
            // OutputStream os = socket.getOutputStream();
            // os.write(data);
            // os.flush();
            Log.d(TAG, "Sending data to neighbor: " + neighbor.name);
        } catch (Exception e) {
            throw new SendError("Failed to send data: " + e.getMessage());
        }
    }
}
