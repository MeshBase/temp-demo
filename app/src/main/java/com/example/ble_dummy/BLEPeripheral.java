package com.example.ble_dummy;

// BLEPeripheral.java
import android.annotation.SuppressLint;
import android.bluetooth.*;
        import android.bluetooth.le.*;
        import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public class BLEPeripheral {
    public static final String TAG = "my_peripheral";
    private static final UUID SERVICE_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb");

    private static final UUID CHAR_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb");

    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic messageCharacteristic;
    private Context context;
    private MessageCallback callback;
    private BluetoothManager btManager; // Add this
    private static final boolean IS_SAMSUNG = Build.MANUFACTURER.equalsIgnoreCase("samsung");

    public interface MessageCallback {
        void onMessageSent(String message);
        void onDeviceConnected(String deviceName);
    }

    public BLEPeripheral(Context context, MessageCallback callback) {
        this.context = context;
        this.callback = callback;
        setupGattServer();
    }

    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        gattServer = btManager.openGattServer(context, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                Log.d(TAG, "Peripheral connection state - Status: " + status +
                        ", New State: " + newState +
                        ", Device: " + device.getAddress()+device.getName());
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    callback.onDeviceConnected(device.getName());
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                     BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                                                     boolean responseNeeded, int offset, byte[] value) {
                String message = new String(value);
                callback.onMessageSent(message);
                Log.d(TAG, "Received message from " + device.getName() + ": " + message);
            }
        });

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        messageCharacteristic = new BluetoothGattCharacteristic(CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(messageCharacteristic);
        gattServer.addService(service);
    }

    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        Log.d(TAG, "Starting advertising");
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true);

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID))
                .build();


        if (IS_SAMSUNG) {
            // Samsung requires different interval configuration
            settingsBuilder.setTimeout(0); // Disable timeout
        }

        AdvertiseSettings settings = settingsBuilder.build();

        BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser()
                .startAdvertising(settings, data, new AdvertiseCallback() {});
    }

    @SuppressLint("MissingPermission")
    public void sendMessage(String message) {
        // Use BluetoothManager to get connected devices
        List<BluetoothDevice> connectedDevices = btManager.getConnectedDevices(BluetoothProfile.GATT);
        Log.d(TAG, "Sending message to " + connectedDevices.size() + " devices");

        messageCharacteristic.setValue(message);
        for (BluetoothDevice device : connectedDevices) {
            gattServer.notifyCharacteristicChanged(device, messageCharacteristic, false);
        }
    }
}
