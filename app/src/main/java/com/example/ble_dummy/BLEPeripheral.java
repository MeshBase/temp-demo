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
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class BLEPeripheral {
    public static final String TAG = "my_peripheral";

    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic messageCharacteristic;
    private final Context context;
    private final MessageCallback callback;
    private BluetoothManager btManager;
    private  boolean isOn;
    private final HashSet<BluetoothDevice> devices = new HashSet<>();
    private BluetoothLeAdvertiser advertiser;

    public BLEPeripheral(Context context, MessageCallback callback) {
        this.context = context;
        this.callback = callback;
        setupGattServer();
    }

    public void  start(){
        if (isOn){
            Log.d(TAG, "is already on");
            return;
        }
        isOn= true;
        setupGattServer();
        startAdvertising();
    }

    @SuppressLint("MissingPermission")
    public void  stop(){
        if (!isOn){
            Log.d(TAG, "is already off");
            return;
        }
        isOn = false;
        advertiser.stopAdvertising(advertisementCallback);

        for (BluetoothDevice device : devices){
            gattServer.cancelConnection(device);
        }

        if (devices.isEmpty()){
            gattServer.close();
        }
    }

    @SuppressLint("MissingPermission")
    private void disconnect(BluetoothDevice device){
        Log.d(TAG, "Central disconnected, restarting advertising");
        callback.onDeviceDisconnected(device.getName() == null ? "unknown" : device.getName());
        devices.remove(device);


        if (!isOn && devices.isEmpty()){
            gattServer.close();
        }
    }

    //Order of call backs overrides resembles the "handshake" steps
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {

            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                disconnect(device);
            } else if(newState == BluetoothGatt.STATE_CONNECTED) {
                callback.onDeviceConnected(device.getName() == null ? "unknown" : device.getName());
               devices.add(device);
               //so that server.cancelConnection works according to https://stackoverflow.com/questions/38762758/bluetoothgattserver-cancelconnection-does-not-cancel-the-connection
                gattServer.connect(device, false);
            }else{
                Log.d(TAG, "unknown state"+newState);
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
                Log.d(TAG, "Received: " + message);
                callback.onMessageSent(message);

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
        void onMessageSent(String message);

        void onDeviceConnected(String deviceName);

        void onDeviceDisconnected(String deviceName);
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
        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
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
