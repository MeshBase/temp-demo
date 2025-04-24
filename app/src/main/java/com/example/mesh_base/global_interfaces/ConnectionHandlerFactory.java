package com.example.mesh_base.global_interfaces;

import androidx.activity.ComponentActivity;

import com.example.mesh_base.wifi_direct.WifiDirectConnectionHandler;

import java.util.UUID;


public class ConnectionHandlerFactory {
    private static final String TAG = "ConnectionHandlerFactory";

    public ConnectionHandler createConnectionHandler(
            ConnectionHandlersEnum type,
            ComponentActivity context,
            UUID id
    ) {
        ConnectionHandler handler;
        switch (type) {
//            case BLE:
//                handler = new BLEConnectionHandler(context, id);
//                break;

            case WifiDirect:
                handler = new WifiDirectConnectionHandler(context, id);
                break;
            default:
                throw new RuntimeException("Unhandled Connection Type: " + type);
        }
        return handler;
    }
}
