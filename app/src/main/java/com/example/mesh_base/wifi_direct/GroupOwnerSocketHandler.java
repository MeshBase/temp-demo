package com.example.mesh_base.wifi_direct;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class GroupOwnerSocketHandler extends Thread {
    private static final String TAG = "GroupOwnerSocketHandler";
    private final UUID myUuid;
    private final WifiDirectConnectionHandler handler;
    private ServerSocket serverSocket;
    private boolean running = true;

    public GroupOwnerSocketHandler(UUID myUuid, WifiDirectConnectionHandler handler) throws IOException {
        this.myUuid = myUuid;
        this.handler = handler;
        this.serverSocket = new ServerSocket(8888); // Fixed port
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                Log.d(TAG, "Accepted client connection");
                // Start a new thread to handle this client
                new ClientHandler(clientSocket).start();
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Error accepting client", e);
                }
            }
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private DataInputStream inputStream;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                inputStream = new DataInputStream(socket.getInputStream());
                // Read UUID and name from client
                String clientUuidStr = inputStream.readUTF();
                UUID clientUuid = UUID.fromString(clientUuidStr);
                String clientName = inputStream.readUTF();
                // Create CommunicationManager and add to chatManagers
                CommunicationManager manager = new CommunicationManager(socket, clientUuid, clientName, handler);
                handler.chatManagers.put(clientUuid, manager);
                Log.d(TAG, "Added client: " + clientName + " (" + clientUuid + ")");
                // Send my UUID and name to client
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                outputStream.writeUTF(myUuid.toString());
                outputStream.writeUTF(android.os.Build.MODEL);
                // Handle incoming data
                while (running) {
                    int length = inputStream.readInt();
                    byte[] data = new byte[length];
                    inputStream.readFully(data);
                    Log.d(TAG, "Received " + length + " bytes from " + clientName);
                    // Process data as needed
                }
            } catch (IOException e) {
                Log.e(TAG, "Client handler error", e);
            } finally {
                try {
                    inputStream.close();
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing client socket", e);
                }
            }
        }
    }
}