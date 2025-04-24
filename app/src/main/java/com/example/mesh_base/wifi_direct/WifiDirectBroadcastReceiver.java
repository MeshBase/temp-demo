package com.example.mesh_base.wifi_direct;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.Objects;

public class WifiDirectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "my_WifiDirectBroadcastReceiver";
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final WifiDirectConnectionHandler connectionHandler;

    public WifiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, WifiDirectConnectionHandler connectionHandler) {
        this.manager = manager;
        this.channel = channel;
        this.connectionHandler = connectionHandler;
    }

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        switch (Objects.requireNonNull(action)) {
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d(TAG, "WiFi P2P enabled");
                } else {
                    Log.d(TAG, "WiFi P2P disabled");
                    connectionHandler.onDisconnected();
                }
                break;
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    if (networkInfo.isConnected()) {
                        manager.requestConnectionInfo(channel, connectionHandler);
                    } else {
                        connectionHandler.onDisconnected();
                    }
                }
                break;
            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
//                connectionHandler.startPeerDiscovery();
                if (device != null) {
                    Log.d(TAG, "This device status: " + device.status);
                }
                break;
            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                Log.d(TAG, "Peers list changed");
                connectionHandler.startPeerDiscovery();
                break;
        }
    }
}