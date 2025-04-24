package com.example.mesh_base.wifi_direct;

import android.net.wifi.p2p.WifiP2pDevice;
import android.util.Log;

import com.example.mesh_base.global_interfaces.Device;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;

public class CommunicationManager implements Runnable {
    private static final String TAG = "ChatManager";
    private final Socket socket;
    private final UUID myUuid;
    private final WifiDirectConnectionHandler connectionHandler;
    private InputStream inputStream;
    private OutputStream outputStream;
    private UUID remoteUuid;
    private Device device;

    public CommunicationManager(Socket socket, UUID myUuid, WifiDirectConnectionHandler connectionHandler) {
        this.socket = socket;
        this.myUuid = myUuid;
        this.connectionHandler = connectionHandler;
    }

    @Override
    public void run() {
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            // Send my UUID and name
            String myInfo = myUuid.toString() + "|" + "MyDeviceName"; // TODO: Replace with actual device name
            outputStream.write(myInfo.getBytes());
            outputStream.flush();
            Log.d(TAG, "Sent my info: " + myInfo);

            // Read remote info
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead > 0) {
                String infoStr = new String(buffer, 0, bytesRead);
                String[] parts = infoStr.split("\\|");
                if (parts.length == 2) {
                    remoteUuid = UUID.fromString(parts[0]);
                    String remoteName = parts[1];
                    WifiP2pDevice p2pDevice = new WifiP2pDevice();
                    p2pDevice.deviceName = remoteName;
                    device = new WifiDirectDeviceWrapper(remoteUuid, p2pDevice);
                    connectionHandler.addNeighbor(remoteUuid, remoteName, this);
                    Log.d(TAG, "Added neighbor: " + remoteName + " with UUID: " + remoteUuid);
                } else {
                    Log.e(TAG, "Invalid remote info received: " + infoStr);
                }
            }

            // Handle message reading
            while (true) {
                bytesRead = inputStream.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                byte[] data = Arrays.copyOf(buffer, bytesRead);
                connectionHandler.onDataReceived(device, data);
                Log.d(TAG, "Received data from " + (device != null ? device.name : "unknown") + ": " + new String(data));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error in ChatManager", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
            if (remoteUuid != null) {
                connectionHandler.removeNeighbor(remoteUuid);
            }
        }
    }

    public void write(byte[] data) {
        try {
            outputStream.write(data);
            outputStream.flush();
            Log.d(TAG, "Wrote data: " + Arrays.toString(data));
        } catch (IOException e) {
            Log.e(TAG, "Error writing to socket", e);
        }
    }
}