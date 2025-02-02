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

import java.lang.reflect.Method;
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

    private final Map<String, Integer> retryCounts = new HashMap<>();

    private void handleConnectionRetry(BluetoothDevice device) {
        String address = device.getAddress();
        int count = retryCounts.getOrDefault(address, 0);

        if (count < 3) { // Max 3 retries
            retryCounts.put(address, count + 1);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                connectToDevice(device);
            }, (long) Math.pow(2, count) * 1000); // Exponential backoff
        } else {
            Log.w(TAG, "Max retries reached for " + address);
            retryCounts.remove(address);
        }
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        final boolean isSamsung = Build.MANUFACTURER.equalsIgnoreCase("samsung");
        if (isSamsung) {
            try {
                // Use reflection for Samsung-specific connection parameters
                Method connectMethod = BluetoothDevice.class.getMethod("connectGatt",
                        Context.class,
                        boolean.class,
                        BluetoothGattCallback.class,
                        int.class,
                        int.class,
                        int.class);

                connectMethod.invoke(device,
                        context,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE,
                        BluetoothDevice.PHY_LE_1M_MASK,
                        BluetoothDevice.ADDRESS_TYPE_RANDOM);
            } catch (Exception e) {
                Log.e(TAG, "Samsung connection failed, falling back", e);
                device.connectGatt(context, false, gattCallback);
            }
        } else {
            device.connectGatt(context, false, gattCallback);
        }

        // Add connection timeout
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!connectedDevices.containsKey(device.getAddress())) {
                Log.w(TAG, "Connection timeout, retrying...");
                cancelConnection(device);
                connectToDevice(device); // Retry with backoff
            }
        }, isSamsung ? 8000 : 5000); // Longer timeout for Samsung
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
                handleConnectionRetry(gatt.getDevice());
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
