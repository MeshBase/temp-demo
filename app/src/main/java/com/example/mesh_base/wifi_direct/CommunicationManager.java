package com.example.mesh_base.wifi_direct;

import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

public class CommunicationManager {
    private static final String TAG = "CommunicationManager";
    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final UUID deviceUuid;
    private final String deviceName;
    private final WifiDirectConnectionHandler handler;
    private boolean isRunning = true;

    public CommunicationManager(Socket socket, UUID uuid, String name, WifiDirectConnectionHandler handler) throws IOException {
        this.socket = socket;
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.deviceUuid = uuid;
        this.deviceName = name;
        this.handler = handler;
    }

    public UUID getDeviceUuid() {
        return deviceUuid;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void write(byte[] data) {
        try {
            outputStream.writeInt(data.length);
            outputStream.write(data);
            outputStream.flush();
            Log.d(TAG, "Sent " + data.length + " bytes to " + deviceName);
        } catch (IOException e) {
            Log.e(TAG, "Failed to send data to " + deviceName, e);
        }
    }

    private void readData() {
        while (isRunning) {
            try {
                int length = inputStream.readInt();
                byte[] data = new byte[length];
                inputStream.readFully(data);
                Log.d(TAG, "Received " + length + " bytes from " + deviceName);
                // Notify handler
                WifiDirectDeviceWrapper sender = new WifiDirectDeviceWrapper(deviceUuid, new WifiP2pDevice());
                sender.name = deviceName;
                handler.onDataReceived(sender, data);
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Error reading data from " + deviceName, e);
                }
                break;
            }
        }
    }

    public void close() {
        isRunning = false;
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }
}