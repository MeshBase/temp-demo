package com.example.ble_dummy;

// BLEPeripheral.java
import static com.example.ble_dummy.CommonConstants.CHAR_UUID;
import static com.example.ble_dummy.CommonConstants.SERVICE_UUID;

import android.annotation.SuppressLint;
import android.bluetooth.*;
        import android.bluetooth.le.*;
        import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BLEPeripheral {
    public static final String TAG = "my_peripheral";

    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic messageCharacteristic;
    private Context context;
    private MessageCallback callback;
    private BluetoothManager btManager; // Add this

        private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

                if (characteristic.getUuid().equals(CHAR_UUID)) {
                    String message = new String(value, StandardCharsets.UTF_8);
                    Log.d(TAG, "Received: " + message);
                    callback.onMessageSent(message);

                    // Required response
                    if (responseNeeded) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                    }
                }
            }
            @SuppressLint("MissingPermission")
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "Central disconnected, restarting advertising");

                    callback.onDeviceDisconnected(device.getName()==null?"unknown": device.getName());
                }else{
                    callback.onDeviceConnected(device.getName()==null?"unknown": device.getName());
                }
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onDescriptorWriteRequest( BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value ) {
                Log.d(TAG,"descriptor write request received");
                if (descriptor.getUuid().equals(CommonConstants.CENTRAL_NOTIF_UUID)) {
                    if (responseNeeded) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }
                }
            }
        };

    public interface MessageCallback {
        void onMessageSent(String message);
        void onDeviceConnected(String deviceName);
        void  onDeviceDisconnected(String deviceName);
    }
    private AdvertiseCallback  advertisementCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertisement started");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.d(TAG, "Advertisement failed:"+errorCode);
        }
    };

    public BLEPeripheral(Context context, MessageCallback callback) {
        this.context = context;
        this.callback = callback;
        setupGattServer();
    }



    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        gattServer = btManager.openGattServer(context, gattServerCallback);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        messageCharacteristic = new BluetoothGattCharacteristic(
                CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE |
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY |
                        BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE |
                        BluetoothGattCharacteristic.PERMISSION_READ
        );


        BluetoothGattDescriptor writeDescriptor = new BluetoothGattDescriptor( CommonConstants.CENTRAL_NOTIF_UUID, BluetoothGattDescriptor.PERMISSION_WRITE );
        messageCharacteristic.addDescriptor(writeDescriptor);

//        the uuid of the characteristic is already the value, no need to read
        BluetoothGattCharacteristic idCharacterstic = new BluetoothGattCharacteristic(
                UUID.randomUUID(),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        service.addCharacteristic(messageCharacteristic);
//        service.addCharacteristic(idCharacterstic);
        gattServer.addService(service);
    }


    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        Log.d(TAG, "Starting advertising");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0) // No timeout for Samsung
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID))
                .build();

        BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser()
                .startAdvertising(settings, data, advertisementCallback);

    }

    @SuppressLint("MissingPermission")
    public void sendMessage(String message) {
        messageCharacteristic.setValue(message);
        List<BluetoothDevice> connectedDevices = btManager.getConnectedDevices(BluetoothProfile.GATT);

        Log.d(TAG, "Sending message to " + connectedDevices.size() + " devices");
        for (BluetoothDevice device : connectedDevices) {
            // Ensure notifications are enabled before sending
            gattServer.notifyCharacteristicChanged(device, messageCharacteristic, false);
            Log.d(TAG, "Notified device: " + device.getAddress());
        }
    }
}
