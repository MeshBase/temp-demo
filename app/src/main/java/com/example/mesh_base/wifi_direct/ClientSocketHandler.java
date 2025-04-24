package com.example.mesh_base.wifi_direct;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

public class ClientSocketHandler extends Thread {
    private static final String TAG = "ClientSocketHandler";
    private static final int PORT = 4545;
    private final InetAddress groupOwnerAddress;
    private final UUID myUuid;
    private final WifiDirectConnectionHandler connectionHandler;

    public ClientSocketHandler(InetAddress groupOwnerAddress, UUID myUuid, WifiDirectConnectionHandler connectionHandler) {
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
            Log.d(TAG, "Connected to group owner at " + groupOwnerAddress);
            CommunicationManager chatManager = new CommunicationManager(socket, myUuid, connectionHandler);
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