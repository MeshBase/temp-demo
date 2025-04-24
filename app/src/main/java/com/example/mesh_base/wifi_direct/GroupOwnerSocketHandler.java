package com.example.mesh_base.wifi_direct;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class GroupOwnerSocketHandler extends Thread {
    private static final String TAG = "GroupOwnerSocketHandler";
    private static final int PORT = 4545;
    private final ServerSocket serverSocket;
    private final UUID myUuid;
    private final WifiDirectConnectionHandler connectionHandler;
    private volatile boolean running = true;

    public GroupOwnerSocketHandler(UUID myUuid, WifiDirectConnectionHandler connectionHandler) throws IOException {
        this.myUuid = myUuid;
        this.connectionHandler = connectionHandler;
        serverSocket = new ServerSocket(PORT);
        Log.d(TAG, "Server socket started on port " + PORT);
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                Log.d(TAG, "Client connected: " + clientSocket.getInetAddress());
                CommunicationManager chatManager = new CommunicationManager(clientSocket, myUuid, connectionHandler);
                new Thread(chatManager).start();
            } catch (IOException e) {
                if (!running) {
                    Log.d(TAG, "Server socket closed");
                } else {
                    Log.e(TAG, "Error accepting client connection", e);
                }
            }
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
    }
}