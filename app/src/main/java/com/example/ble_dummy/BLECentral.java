package com.example.ble_dummy;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

class CommonConstants {
    public static final UUID SERVICE_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb");
    public static final UUID MESSAGE_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb");
    public static final UUID ID_UUID = UUID.fromString("0000beef-0000-1000-8000-10005f9b34fb");
    public  static  final  UUID NOTIF_DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb" );
}

class ConvertUUID {
    public static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public static UUID bytesToUUID(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Invalid UUID byte array length: " + bytes.length);
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long mostSigBits = bb.getLong();
        long leastSigBits = bb.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }
}

public class BLECentral {
    public static final String TAG = "my_central";

    private final BluetoothLeScanner scanner;

    private final Set<String> connectingDevices = new HashSet<>();
    private final Map<String, BluetoothGatt> connectedDevices = new HashMap<>();
    private final Context context;
    private final ConnectionCallback callback;
    private boolean isOn = false;
    // To help prevent calling scanner.scan if already scanning
    private  boolean isScanning = false;


    public interface ConnectionCallback {
        void onDeviceFound(BluetoothDevice device);

        void onMessageReceived(String message);

        void onDeviceConnected(BluetoothDevice device);

        void onDeviceDisconnected(BluetoothDevice device);

        void onMessageForwarded(String message);
    }

    public final Map<String, Integer> retryCount = new HashMap<>();
    private static final int MAX_RETRIES = 5;

    public BLECentral(Context context, ConnectionCallback callback) {
        this.context = context;
        this.callback = callback;
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        scanner = btManager.getAdapter().getBluetoothLeScanner();
    }

    @SuppressLint("MissingPermission")
    public void start() {
        if (isOn) {
            Log.d(TAG, "already on");
            return;
        }
        isOn = true;
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(CommonConstants.SERVICE_UUID)).build();
        if (!isScanning){
            Log.d(TAG, "Scanning started");
            scanner.startScan(Collections.singletonList(filter), new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), scanCallback);
            isScanning = true;
        }
        Log.d(TAG, "central started");
    }


    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        attemptConnect(device);
    }

    @SuppressLint("MissingPermission")
    private void attemptConnect(BluetoothDevice device) {
        String address = device.getAddress();
        int count = retryCount.getOrDefault(address, 0);


        boolean tooManyRetries = count >= MAX_RETRIES;
        boolean alreadyConnecting = connectingDevices.contains(address);
        boolean alreadyConnected = connectedDevices.containsKey(address);

        if (!isOn || tooManyRetries || alreadyConnecting || alreadyConnected) {
            Log.d(TAG, "dropping connecting to" + device.getName() + "due to" + "too many retries:" + tooManyRetries + " already connecting:" + alreadyConnecting + " already connected:" + alreadyConnected);
            return;
        }

        long delay = count * 150L; //0 wait time for first try
        connectingDevices.add(address); //
        retryCount.put(address, count + 1);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            device.connectGatt(context, false, gattCallback);
        }, delay);
    }

    @SuppressLint("MissingPermission")
    public void sendMessageToAllDevices(String message) {
        for (BluetoothGatt gatt : connectedDevices.values()) {
            BluetoothGattCharacteristic messageCharacteristic = gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(CommonConstants.MESSAGE_UUID); // Use CHAR_UUID

            messageCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            messageCharacteristic.setValue(message.getBytes(StandardCharsets.UTF_8));
            boolean success = gatt.writeCharacteristic(messageCharacteristic);
            Log.v(TAG, "Write status: " + success + " to " + gatt.getDevice().getName() + " with address " + gatt.getDevice().getAddress());
        }

        callback.onMessageForwarded(message + " to " + connectedDevices.size() + " devices");
    }

    @SuppressLint("MissingPermission")
    public void stop(){
        if (!isOn){
            Log.d(TAG, "already off");
            return;
        }
        isOn = false;
        Log.d(TAG, "disconnecting to all devices:"+ connectedDevices.size());
        for (BluetoothGatt gatt : connectedDevices.values()) {
            gatt.disconnect();
        }
        retryCount.clear();


        //Because android only allows 5 scanner.scan() calls per 30seconds, so always delay by 10s to be safe
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isOn & isScanning){
                Log.d(TAG,"stopped scanning after 10seconds");
                isScanning = false;
                scanner.stopScan(scanCallback);
            }
        }, 10_000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();

            boolean tooManyRetries = retryCount.getOrDefault(address, 0) >= MAX_RETRIES;
            boolean alreadyConnecting = connectingDevices.contains(address);
            boolean alreadyConnected = connectedDevices.containsKey(address);

            if (!isOn || tooManyRetries || alreadyConnecting || alreadyConnected) {
                return;
            }

            Log.d(TAG, "Found device: " + device.getName() + " with address: " + device.getAddress());
            callback.onDeviceFound(device);
            connectToDevice(device);
        }
    };


    //Order of call backs overrides resembles the "handshake" steps
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String address = gatt.getDevice().getAddress();
            String name = gatt.getDevice().getName();


            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from: " + name + address);
                if (connectedDevices.containsKey(address)) {
                    callback.onDeviceDisconnected(gatt.getDevice());
                }

                connectingDevices.remove(address);
                connectedDevices.remove(address);

                if (isOn){
                    attemptConnect(gatt.getDevice());
                }else{
                    gatt.close();
                }

            } else if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (connectedDevices.containsKey(address)){
                    Log.e(TAG, "Same device connected twice!"+name+address);
                }

                if (isOn){
                    callback.onDeviceConnected(gatt.getDevice());
                    Log.d(TAG, "Connected to: " + name+address);
                    connectingDevices.remove(address);
                    connectedDevices.put(address, gatt);
                    gatt.discoverServices();
                    retryCount.remove(address);
                }else{
                    Log.d(TAG, "rejecting new connection after being turned off: "+name+address);
                    gatt.disconnect();
                }
            }
        }
        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status != BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG, "Services discovery failed for"+gatt.getDevice().getName());
                return;
            }
            Log.d(TAG, "Services discovered for " + gatt.getDevice().getAddress());

            try{
                BluetoothGattService service = gatt.getService(CommonConstants.SERVICE_UUID);

                //enable notification
                BluetoothGattCharacteristic messageCharacteristic = service.getCharacteristic(CommonConstants.MESSAGE_UUID);
                gatt.setCharacteristicNotification(messageCharacteristic, true);
                BluetoothGattDescriptor descriptor = messageCharacteristic.getDescriptor( CommonConstants.NOTIF_DESCRIPTOR_UUID);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);

            }catch (Exception e){
                //see if any null errors
                Log.e(TAG, "error on discover services"+e);
                throw  e;
            }
        }
        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notifications failed to be enabled!");
                return;
            }
            if (descriptor.getUuid().equals(CommonConstants.NOTIF_DESCRIPTOR_UUID)){
                Log.d(TAG, "Notifications enabled for"+ gatt.getDevice().getName()+gatt.getDevice().getAddress());

                //then get device uuid
                BluetoothGattCharacteristic idCharacteristic = gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(CommonConstants.ID_UUID);
                boolean success = gatt.readCharacteristic(idCharacteristic);
                if (!success){
                    Log.d(TAG, "Failed to read characteristic"+idCharacteristic.getUuid()+" from "+gatt.getDevice().getName());
                }
            }
        }
        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Failed to read characteristic from "+gatt.getDevice().getName());
                return;
            }
            Log.d(TAG, "Read characteristic from "+gatt.getDevice().getName()+" char:"+characteristic.getUuid()+" val:"+ Arrays.toString(characteristic.getValue()));

            if (characteristic.getUuid().equals(CommonConstants.ID_UUID)) {
                Log.d(TAG, "id requested");
                UUID uuid = ConvertUUID.bytesToUUID(characteristic.getValue());
                Log.d(TAG, "Device UUID! of " + gatt.getDevice().getName() + " is : " + uuid);

                UUID myId = UUID.randomUUID();
                characteristic.setValue(ConvertUUID.uuidToBytes(myId));
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                gatt.writeCharacteristic(characteristic);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String message = new String(characteristic.getValue());
            Log.d(TAG, "received message from"+gatt.getDevice().getName()+gatt.getDevice().getAddress());
            callback.onMessageReceived(message);
            sendMessageToAllDevices(message);
        }
       };
}
