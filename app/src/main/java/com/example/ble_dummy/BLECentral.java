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
    private final Map<String, BluetoothGatt> connectedDevices = new HashMap<>();
    private Context context;
    private ConnectionCallback callback;
    private final Map<String, Boolean> reconnectMap = new HashMap<>();
    private Set<String> messageSet = new HashSet<String>();

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
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(CommonConstants.SERVICE_UUID)).build();
        scanner.startScan(Collections.singletonList(filter),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback
        );
        Log.d(TAG, "Scanning started");
    }


    private final Set<String> connectingDevices = new HashSet<>();


    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        String address = device.getAddress();
        retryCount.put(address, 0);

        // Prevent duplicate connection attempts
        if (connectingDevices.contains(address)) {
            Log.d(TAG, "Already connecting to: " + address);
            return;
        }
        if (connectedDevices.containsKey(address)) {
            Log.d(TAG, "Already connected to: " + address);
            return;
        }

        connectingDevices.add(address);
        device.connectGatt(context, false, gattCallback);
    }




    @SuppressLint("MissingPermission")
    public void sendMessageToAllDevices(String message) {
        if (messageSet.contains(message)){
            Log.d(TAG, "already sent message :"+message);
            return;
        }
        messageSet.add(message);
        for (BluetoothGatt gatt : connectedDevices.values()) {
            BluetoothGattCharacteristic characteristic =
                    gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(CommonConstants.CHAR_UUID); // Use CHAR_UUID

            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(message.getBytes(StandardCharsets.UTF_8));
            boolean success = gatt.writeCharacteristic(characteristic);
            Log.v(TAG, "Write status: "+success+" to "+gatt.getDevice().getName()+" with address "+gatt.getDevice().getAddress());
        }

        callback.onMessageForwarded(message + " to "+connectedDevices.size()+" devices");
    }


    @SuppressLint("MissingPermission")
    private void attemptReconnect(BluetoothDevice device) {
        String address = device.getAddress();
        int count = retryCount.getOrDefault(address, 0);

        Log.d(TAG, "retrying..."+count);
        if (count >= MAX_RETRIES) {
            Log.d(TAG, "too many tries already for connecting to"+device.getName());
            return;
        }

        Log.d(TAG, "decided to retry after some milliseconds"+device.getName());
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (retryCount.getOrDefault(address, 0) >= MAX_RETRIES || connectingDevices.contains(address)|| connectedDevices.containsKey(address)){
                if (connectedDevices.containsKey(address)){
                    Log.d(TAG, "dropping retryign due to "+device.getName()+"being already connected");
                }else if (connectingDevices.contains(address)){
                    Log.d(TAG, "dropping retryign due to "+device.getName()+"being already connecting");
                }else if (retryCount.getOrDefault(address, 0) >= MAX_RETRIES){
                    Log.d(TAG, "dropping retryign due to "+device.getName()+"being already retried too many times");
                }
                return;
            }

            Log.d(TAG, "Reconnecting attempt " + (count+1) + "/"+MAX_RETRIES);
            device.connectGatt(context, false, gattCallback);
            connectingDevices.add(address);
            retryCount.put(address, count + 1);
        }, 500); // 5-second delay
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
                    attemptReconnect(gatt.getDevice());
                    callback.onDeviceDisconnected(gatt.getDevice());
                    connectedDevices.remove(address);
                }else if (newState == BluetoothGatt.STATE_CONNECTED && !connectedDevices.containsKey(address)){
                    callback.onDeviceConnected(gatt.getDevice());
                    Log.d(TAG, "Connected to: " + address);
                    connectedDevices.put(address, gatt);
                    gatt.discoverServices();
                    retryCount.put(address, 0);

            }
        };

           @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String message = new String(characteristic.getValue());
            callback.onMessageReceived(message);

               new Handler(Looper.getMainLooper()) .postDelayed(() -> {
                   sendMessageToAllDevices(message); // Forward message
               }, 500);
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
