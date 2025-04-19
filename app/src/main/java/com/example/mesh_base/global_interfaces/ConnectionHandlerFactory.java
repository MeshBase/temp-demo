package com.example.mesh_base.global_interfaces;

import android.util.Log;

import androidx.activity.ComponentActivity;

import com.example.mesh_base.ble.BleConnectionHandlerClassFactory;
import com.example.mesh_base.mesh_manager.MeshManagerListener;

import java.util.UUID;

import kotlin.NotImplementedError;


public class ConnectionHandlerFactory {
    private static final String TAG = "ConnectionHandlerFactory";
    private UUID id;

    public ConnectionHandler createConnectionHandler(ConnectionHandlersEnum type, ComponentActivity context, UUID id, MeshManagerListener meshListener) {
        this.id = id;
        ConnectionHandler handler;
        switch (type) {
            case BLE:
                handler = new BleConnectionHandlerClassFactory().create(context, id, meshListener);
                break;
            case WifiDirect:
                handler = new BleConnectionHandlerClassFactory().create(context, id, meshListener);
                break;
            default:
                throw new NotImplementedError();
        }
//        handler.subscribe(createListener(meshListener));
        return handler;
    }

    private ConnectionHandlerListener createListener(MeshManagerListener meshListener) {
        return new ConnectionHandlerListener() {
            @Override
            public void onNeighborConnected(Device device) {
                Log.d(TAG, "Neighbor Connected");
                meshListener.onNeighborConnected(device);
            }

            @Override
            public void onNeighborDisconnected(Device device) {
                Log.d(TAG, "Neighbor Disconnected");
                meshListener.onNeighborDisconnected(device);
            }

            @Override
            public void onConnected() {
                Log.d(TAG, "Connected");
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "Disconnected");
            }

            @Override
            public void onDataReceived(Device device, byte[] data) {
                Log.d(TAG, "Received Data");
                if (device.uuid == id) {
                    meshListener.onDataReceivedForSelf(data);
                }
            }

        };
    }
}
