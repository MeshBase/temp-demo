package com.example.mesh_base.wifi_direct;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.UUID;

public class ClientSocketHandler extends Thread {
    private static final String TAG = "ClientSocketHandler";
    private final InetAddress groupOwnerAddress;
    private final UUID myUuid;
    private final WifiDirectConnectionHandler handler;
    private Socket socket;
    private boolean running = true;

    public ClientSocketHandler(InetAddress groupOwnerAddress, UUID myUuid, WifiDirectConnectionHandler handler) {
        this.groupOwnerAddress = groupOwnerAddress;
        this.myUuid = myUuid;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(groupOwnerAddress, 8888);
            Log.d(TAG, "Connected to group owner at " + groupOwnerAddress);
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            // Send my UUID and name
            outputStream.writeUTF(myUuid.toString());
            outputStream.writeUTF(android.os.Build.MODEL);
            // Read group owner's UUID and name
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            String ownerUuidStr = inputStream.readUTF();
            UUID ownerUuid = UUID.fromString(ownerUuidStr);
            String ownerName = inputStream.readUTF();
            // Create CommunicationManager
            CommunicationManager manager = new CommunicationManager(socket, ownerUuid, ownerName, handler);
            handler.chatManagers.put(ownerUuid, manager);
            Log.d(TAG, "Connected to group owner: " + ownerName + " (" + ownerUuid + ")");
            // Handle incoming data
            while (running) {
                int length = inputStream.readInt();
                byte[] data = new byte[length];
                inputStream.readFully(data);
                Log.d(TAG, "Received " + length + " bytes from " + ownerName);
                // Process data as needed
            }
        } catch (IOException e) {
            Log.e(TAG, "Client socket error", e);
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
    }

    public void stopClient() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing client socket", e);
        }
    }
}