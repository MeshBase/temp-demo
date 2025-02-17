package com.example.ble_dummy;

// BLEPeripheral.java

import static com.example.ble_dummy.CommonConstants.MESSAGE_UUID;
import static com.example.ble_dummy.CommonConstants.SERVICE_UUID;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class BLEPeripheral {
    public static final String TAG = "my_peripheral";

    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic messageCharacteristic;
    private final Context context;
    private final MessageCallback callback;
    private boolean isOn;
    private final HashMap<String, BluetoothDevice> connectedDevices = new HashMap<>();
    private final HashMap<String, BluetoothDevice> connectingDevices = new HashMap<>();
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
        if (gattServer == null){
            setupGattServer();
        }
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

        for (BluetoothDevice device : new HashSet<>(connectedDevices.values())) {
            gattServer.cancelConnection(device);
        }

        for (BluetoothDevice device: new HashSet<>(connectingDevices.values())){
            gattServer.cancelConnection(device);
        }

        if (connectedDevices.isEmpty()) {
            closeGatt();
        }else{
            //Some devices refuse to disconnect, so force disconnection after some time
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                if (!isOn && gattServer != null){
                    Log.d(TAG, "closed gatt server after 5 seconds with "+connectedDevices.size() + "connected devices and "+connectingDevices.size()+ "connecting devices");
                    closeGatt();
                }

            }, 5_000L);
        }
    }

    @SuppressLint("MissingPermission")
    private void closeGatt(){
        gattServer.close();
        gattServer = null;
    }


    //Order of call backs overrides resembles the "handshake" steps
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            String address = device.getAddress();
            String name = device.getName();


            boolean exists = connectedDevices.containsKey(address) || connectingDevices.containsKey(address);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (exists) {
                    Log.w(TAG, name + " (" + address + ") attempted to connect twice. Ignoring");
                    gattServer.cancelConnection(device);
                    return;
                }

                connectingDevices.put(device.getAddress(), device);
                Log.d(TAG, "Central connected (not fully though): " + name + address+". Now have " + connectingDevices.size() + "connecting devices. status:"+ status );
                //so that server.cancelConnection() causes disconnect events. According to https://stackoverflow.com/questions/38762758/bluetoothgattserver-cancelconnection-does-not-cancel-the-connection
                gattServer.connect(device, false);

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (!exists) {
                    Log.w(TAG, name + address + " was already not connected. Ignoring disconnect.");
                    return;
                }

                Log.d(TAG, "Central disconnected: " + name + address+" status:"+status );
                callback.onDeviceDisconnected(device);
                connectedDevices.remove(address);
                connectingDevices.remove(address);

                if (!isOn && connectedDevices.isEmpty()) {
                    closeGatt();
                    Log.d(TAG, "GATT server closed after all devices were disconnected");
                }
            } else {
                Log.w(TAG, "Unknown state: " + newState + " status: "+status);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.d(TAG, "descriptor write request received"+"response needed:"+responseNeeded+" descriptor:"+descriptor.getUuid());
            if (descriptor.getUuid().equals(CommonConstants.NOTIF_DESCRIPTOR_UUID)) {
                if (responseNeeded) {
                    boolean res = gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    Log.d(TAG, "descriptor response sent"+res);
                }
            }else{
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, value);
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
            }else{
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, new byte[] {0,1,2});
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

            if (characteristic.getUuid().equals(MESSAGE_UUID)) {
                String message = new String(value, StandardCharsets.UTF_8);
                Log.d(TAG, "Received (string?): " + message+" from "+device.getName()+device.getAddress());
                callback.onMessageReceived(value, device);

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                }
            } else if (characteristic.getUuid().equals(CommonConstants.ID_UUID)) {
                Log.d(TAG, "id has been given");
                UUID deviceUUID = ConvertUUID.bytesToUUID(value);
                Log.d(TAG, "Device UUID! of " + device.getName() + " is : " + deviceUUID+" now fully connected."+connectedDevices.size()+" connected devices");

                //Fully connected
                connectedDevices.put(device.getAddress(), device);
                connectingDevices.remove(device.getAddress());
                callback.onDeviceConnected(device);

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                }
            }else if (responseNeeded){
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
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
        BluetoothDevice device = connectedDevices.get(address);
        if (device == null) {
            Log.d(TAG, "address doesn't exist");
            return false;
        }

        messageCharacteristic.setValue(message);
        return gattServer.notifyCharacteristicChanged(device, messageCharacteristic, false);
    }
}
