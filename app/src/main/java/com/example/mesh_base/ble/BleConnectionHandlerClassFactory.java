package com.example.mesh_base.ble;

import androidx.activity.ComponentActivity;

import com.example.mesh_base.mesh_manager.MeshManagerListener;

import java.util.UUID;


public class BleConnectionHandlerClassFactory {
    private final String TAG = "my_BleConnectionHandlerClassFactory";

    public BLEConnectionHandler create(ComponentActivity context, UUID id, MeshManagerListener listener) {
        return new BLEConnectionHandler(context, id);

        // subscribe to all topics
//        bleCOnnectionHandler.subscribe(
//                new ConnectionHandlerListener() {
//                    @Override
//                    public void onNeighborConnected(Device device) {
//                        Log.d(TAG, "Neighbor Connected");
//                        listener.onNeighborConnected(device);
//                    }
//
//                    @Override
//                    public void onNeighborDisconnected(Device device) {
//                        Log.d(TAG, "Neighbor Disconnected");
//                        listener.onNeighborDisconnected(device);
//                    }
//
//                    @Override
//                    public void onDisconnected() {
//                        Log.d(TAG, "Connection Handler Disconnected!");
//                    }
//
//                    @Override
//                    public void onConnected() {
//                        Log.d(TAG, "Connection Handler Connected!");
//                    }
//
//                    @Override
//                    public void onDataReceived(Device device, byte[] data) {
//                        Log.d(TAG, "Received Data");
//                    }
//                }
//        );
//        return bleConnectionHandler;
    }
}

