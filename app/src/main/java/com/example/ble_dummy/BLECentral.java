package com.example.ble_dummy;
// BLECentral.java
import android.annotation.SuppressLint;
import android.bluetooth.*;
        import android.bluetooth.le.*;
        import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.*;

public class BLECentral {
    public static final String TAG = "my_central";
    private static final UUID SERVICE_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb");

    private BluetoothLeScanner scanner;
    private final Map<String, BluetoothGatt> connectedDevices = new HashMap<>();
    private Context context;
    private ConnectionCallback callback;

    public interface ConnectionCallback {
        void onDeviceFound(BluetoothDevice device);
        void onMessageReceived(String message);
        void onDeviceConnected(BluetoothDevice device);

        void onMessageForwarded(String message);
    }

    public BLECentral(Context context, ConnectionCallback callback) {
        this.context = context;
        this.callback = callback;
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        scanner = btManager.getAdapter().getBluetoothLeScanner();
    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(SERVICE_UUID)).build();
        scanner.startScan(Collections.singletonList(filter),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback
        );
        Log.d(TAG, "Scanning started");
    }
    @SuppressLint("MissingPermission")
    public void cancelConnection(BluetoothDevice device) {
        String address = device.getAddress();
        BluetoothGatt gatt = connectedDevices.get(address);

        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            connectedDevices.remove(address);
            Log.d(TAG, "Connection canceled for: " + address);
        } else {
            Log.w(TAG, "No active connection to cancel for: " + address);
        }
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        // Samsung requires explicit transport type
        if (Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            device.connectGatt(context, false, gattCallback);
        }

        // Add connection timeout
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!connectedDevices.containsKey(device.getAddress())) {
                Log.w(TAG, "Connection timeout for " + device.getAddress());
                cancelConnection(device);
            }
        }, 8000); // 8-second timeout for Samsung
    }

    @SuppressLint("MissingPermission")
    public void sendMessageToAllDevices(String message) {
        for (BluetoothGatt gatt : connectedDevices.values()) {
            BluetoothGattCharacteristic characteristic = gatt.getService(SERVICE_UUID).getCharacteristic(CHAR_UUID);
            characteristic.setValue(message);
            gatt.writeCharacteristic(characteristic);
            Log.d(TAG, "Forwarding message to: " + gatt.getDevice().getAddress());
        }
        callback.onMessageForwarded(message); // Notify UI
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            callback.onDeviceFound(device);
            scanner.stopScan(this);//Stop now
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            BluetoothDevice device = gatt.getDevice();
            String deviceName = device.getName() != null ? device.getName() : "Unknown Device";
            Log.d(TAG, "Connection state changed - Status: " + status +
                    ", New State: " + newState +
                    ", Device: " + gatt.getDevice().getAddress()+deviceName);

            String address = gatt.getDevice().getAddress();
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectedDevices.put(address, gatt);
                gatt.discoverServices();
                callback.onDeviceConnected(gatt.getDevice());
            }
            if (status == 133) { // Handle specific error
                Log.w(TAG, "Status 133 detected - retrying connection");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    gatt.close();
                    connectToDevice(gatt.getDevice());
                }, 500); // Retry after 500ms
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String message = new String(characteristic.getValue());
            callback.onMessageReceived(message);
            sendMessageToAllDevices(message); // Forward message
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services discovered for " + gatt.getDevice().getAddress());
            // ... rest unchanged
        }
    };
}
