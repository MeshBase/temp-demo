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
    private final Map<String, Boolean> reconnectMap = new HashMap<>();

    public interface ConnectionCallback {
        void onDeviceFound(BluetoothDevice device);
        void onMessageReceived(String message);
        void onDeviceConnected(BluetoothDevice device);
        void  onDeviceDisconnected(BluetoothDevice device);

        void onMessageForwarded(String message);
    }
    public final Map<String, Integer> retryCount = new HashMap<>();
    private static final int MAX_RETRIES = 3;

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


    private final Map<String, Integer> retryCounts = new HashMap<>();
    private final Set<String> connectingDevices = new HashSet<>();


    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        String address = device.getAddress();

        // Prevent duplicate connection attempts
        if (connectingDevices.contains(address)) {
            Log.d(TAG, "Already connecting to: " + address);
            return;
        }

        connectingDevices.add(address);
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


    @SuppressLint("MissingPermission")
    private void attemptReconnect(BluetoothDevice device) {
        String address = device.getAddress();
        int count = retryCount.getOrDefault(address, 0);

        if (count < MAX_RETRIES) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "Reconnecting attempt " + (count+1) + "/3");
                device.connectGatt(context, false, gattCallback);
                retryCount.put(address, count + 1);
            }, 5000); // 5-second delay
        } else {
            Log.w(TAG, "Max retries reached for " + address);
            retryCount.remove(address);
        }
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
                String address = gatt.getDevice().getAddress();

                if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.w(TAG, "Disconnected from: " + address);
                    attemptReconnect(gatt.getDevice());
                    callback.onDeviceDisconnected(gatt.getDevice());
                }else{
                    callback.onDeviceConnected(gatt.getDevice());
                    Log.d(TAG, "Connected to: " + address);
                    connectedDevices.put(address, gatt);
                    gatt.discoverServices();

            }
        };

           @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String message = new String(characteristic.getValue());
            callback.onMessageReceived(message);
            sendMessageToAllDevices(message); // Forward message
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services discovered for " + gatt.getDevice().getAddress());
            // ... rest unchanged
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHAR_UUID);

                // Enable notifications
                gatt.setCharacteristicNotification(characteristic, true);

                // Write to CCCD
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                );
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }
        }
    };
}
