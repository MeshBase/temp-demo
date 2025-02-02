package com.example.ble_dummy;
// BLECentral.java
import android.annotation.SuppressLint;
import android.bluetooth.*;
        import android.bluetooth.le.*;
        import android.content.Context;
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
    public void connectToDevice(BluetoothDevice device) {
        device.connectGatt(context, false, gattCallback);
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
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection state changed: " + newState + " for " + gatt.getDevice().getAddress());
            String address = gatt.getDevice().getAddress();
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectedDevices.put(address, gatt);
                gatt.discoverServices();
                callback.onDeviceConnected(gatt.getDevice());
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
