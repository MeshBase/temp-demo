package com.example.ble_dummy;

// BLEPeripheral.java

import static com.example.ble_dummy.CommonConstants.MESSAGE_UUID;
import static com.example.ble_dummy.CommonConstants.SERVICE_UUID;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BLEPeripheral {
    public static final String TAG = "my_peripheral";

    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic messageCharacteristic;
    private final Context context;
    private final MessageCallback callback;
    private boolean isOn;
    private final ConcurrentHashMap<String, BluetoothDevice> devices = new ConcurrentHashMap<>();
    private BluetoothLeAdvertiser advertiser;

    public BLEPeripheral(Context context, MessageCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void start() {
        if (isOn) {
            Log.d(TAG, "is already on");
            return;
        }
        isOn = true;
        setupGattServer();
        startAdvertising();
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        if (!isOn) {
            Log.d(TAG, "is already off");
            return;
        }
        isOn = false;
        advertiser.stopAdvertising(advertisementCallback);

        Collection<BluetoothDevice> deviceList = devices.values();
        for (BluetoothDevice device : deviceList) {
            gattServer.cancelConnection(device);
        }

        if (devices.isEmpty()) {
            gattServer.close();
        }
    }


    //Order of call backs overrides resembles the "handshake" steps
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            String address = device.getAddress();
            String name = device.getName();

            boolean wasConnected = devices.containsKey(address);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (wasConnected) {
                    Log.w(TAG, name + " (" + address + ") attempted to connect twice. Ignoring. Thread:"+ Thread.currentThread().getId() );
                    gattServer.cancelConnection(device);
                    return;
                }

                devices.put(address, device);
                Log.d(TAG, "Central connected: " + name + address+". Now have " + devices.size() + " devices. Thread:"+ Thread.currentThread().getId() );
                callback.onDeviceConnected(device);

               //so that server.cancelConnection() causes disconnect events. According to https://stackoverflow.com/questions/38762758/bluetoothgattserver-cancelconnection-does-not-cancel-the-connection
                gattServer.connect(device, false);
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (!wasConnected) {
                    Log.w(TAG, name + address + "was already not connected. Ignoring disconnect."+"thread:"+Thread.currentThread().getId() );
                    return;
                }

                Log.d(TAG, "Central disconnected: " + name + address+" thread:"+Thread.currentThread().getId() );
                callback.onDeviceDisconnected(device);
                devices.remove(address);

                if (!isOn && devices.isEmpty()) {
                    gattServer.close();
                    Log.d(TAG, "GATT server closed.");
                }
            } else {
                Log.w(TAG, "Unknown state: " + newState);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d(TAG, "descriptor write request received");
            if (descriptor.getUuid().equals(CommonConstants.NOTIF_DESCRIPTOR_UUID)) {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            }
        }


        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(TAG, "Read request received from " + device.getName() + ":" + characteristic.getUuid());

            if (characteristic.getUuid().equals(CommonConstants.ID_UUID)) {
                UUID deviceUUID = UUID.randomUUID();
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ConvertUUID.uuidToBytes(deviceUUID));
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

            if (characteristic.getUuid().equals(MESSAGE_UUID)) {
                String message = new String(value, StandardCharsets.UTF_8);
                Log.d(TAG, "Received (string?): " + message+" from "+device.getName()+device.getAddress());
                callback.onMessageReceived(value, device);

                // Required response
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                }
            } else if (characteristic.getUuid().equals(CommonConstants.ID_UUID)) {
                Log.d(TAG, "id has been given");
                UUID deviceUUID = ConvertUUID.bytesToUUID(value);
                Log.d(TAG, "Device UUID! of " + device.getName() + " is : " + deviceUUID);
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            }
        }


    };

    public interface MessageCallback {
        void onMessageReceived(byte[] data, BluetoothDevice device);

        void onDeviceConnected(BluetoothDevice device);

        void onDeviceDisconnected(BluetoothDevice device);
    }

    private final AdvertiseCallback advertisementCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertisement started");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.d(TAG, "Advertisement failed:" + errorCode);
        }
    };


    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        gattServer = btManager.openGattServer(context, gattServerCallback);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        messageCharacteristic = new BluetoothGattCharacteristic(
                MESSAGE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE |
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY |
                        BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE |
                        BluetoothGattCharacteristic.PERMISSION_READ
        );


        BluetoothGattDescriptor writeDescriptor = new BluetoothGattDescriptor(CommonConstants.NOTIF_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE);
        messageCharacteristic.addDescriptor(writeDescriptor);

        BluetoothGattCharacteristic idCharacteristic = new BluetoothGattCharacteristic(
                CommonConstants.ID_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        service.addCharacteristic(messageCharacteristic);
        service.addCharacteristic(idCharacteristic);
        assert gattServer != null;
        gattServer.addService(service);
    }


    @SuppressLint("MissingPermission")
    private void startAdvertising() {
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

        advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        advertiser.startAdvertising(settings, data, advertisementCallback);

    }

    @SuppressLint("MissingPermission")
    public boolean send(byte[] message, String address) {
        BluetoothDevice device = devices.get(address);
        if (device == null) {
            Log.d(TAG, "address doesn't exist");
            return false;
        }

        messageCharacteristic.setValue(message);
        return gattServer.notifyCharacteristicChanged(device, messageCharacteristic, false);
    }
}
