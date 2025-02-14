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
import java.nio.charset.StandardCharsets;
import java.util.*;

class CommonConstants {
    public static final UUID SERVICE_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb");
    public static final UUID CHAR_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb");
}

public class BLECentral {
    public static final String TAG = "my_central";

    private BluetoothLeScanner scanner;

    private final Set<String> connectingDevices = new HashSet<>();
    private final Map<String, BluetoothGatt> connectedDevices = new HashMap<>();
    private Context context;
    private ConnectionCallback callback;

    public interface ConnectionCallback {
        void onDeviceFound(BluetoothDevice device);

        void onMessageReceived(String message);

        void onDeviceConnected(BluetoothDevice device);

        void onDeviceDisconnected(BluetoothDevice device);

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
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(CommonConstants.SERVICE_UUID)).build();
        scanner.startScan(Collections.singletonList(filter), new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
        Log.d(TAG, "Scanning started");
    }


    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        attemptConnect(device);
    }

    @SuppressLint("MissingPermission")
    public void sendMessageToAllDevices(String message) {
        for (BluetoothGatt gatt : connectedDevices.values()) {
            BluetoothGattCharacteristic characteristic =
                    gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(CommonConstants.CHAR_UUID); // Use CHAR_UUID

            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(message.getBytes(StandardCharsets.UTF_8));
            boolean success = gatt.writeCharacteristic(characteristic);
            Log.v(TAG, "Write status: " + success + " to " + gatt.getDevice().getName() + " with address " + gatt.getDevice().getAddress());
        }

        callback.onMessageForwarded(message + " to " + connectedDevices.size() + " devices");
    }


    @SuppressLint("MissingPermission")
    private void attemptConnect(BluetoothDevice device) {
        String address = device.getAddress();
        int count = retryCount.getOrDefault(address, 0);

        boolean tooManyRetries = count >= MAX_RETRIES;
        boolean alreadyConnecting = connectingDevices.contains(address);
        boolean alreadyConnected = connectedDevices.containsKey(address);

        if (tooManyRetries || alreadyConnecting || alreadyConnected) {
            Log.d(TAG, "dropping connecting to" + device.getName() + "due to" + "too many retries:" + tooManyRetries + " already connecting:" + alreadyConnecting + " already connected:" + alreadyConnected);
            return;
        }

        long delay = count * 150L; //0 wait time for first try
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            connectingDevices.add(address);
            retryCount.put(address, count + 1);
            device.connectGatt(context, false, gattCallback);
        }, delay);
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
            connectingDevices.remove(address);

            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from: " + address);
                if (connectedDevices.containsKey(address)) {
                    callback.onDeviceDisconnected(gatt.getDevice());
                }
                connectedDevices.remove(address);
                attemptConnect(gatt.getDevice());
            } else if (newState == BluetoothGatt.STATE_CONNECTED && !connectedDevices.containsKey(address)) {
                callback.onDeviceConnected(gatt.getDevice());
                Log.d(TAG, "Connected to: " + address);
                connectedDevices.put(address, gatt);
                gatt.discoverServices();
                retryCount.put(address, 0);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String message = new String(characteristic.getValue());
            callback.onMessageReceived(message);
            sendMessageToAllDevices(message);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services discovered for " + gatt.getDevice().getAddress());
            // ... rest unchanged
            BluetoothGattService service = gatt.getService(CommonConstants.SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CommonConstants.CHAR_UUID);

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
