package com.example.ble_dummy;

import static android.bluetooth.BluetoothGattCharacteristic.*;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class BlEPeripheral implements BLEPeripheralI {
    private BLEConnectListener connectListener;
    private BLEDisconnectListener disconnectListener;
    private BLEDataListener dataListener;

    private final Context context;

    private final String TAG = "my_BlePeripheral";

    private BluetoothGattCharacteristic idCharacteristic;

    private BluetoothGattCharacteristic dataCharacteristic;

    private BluetoothGattServer server;
    private HashMap<String, BluetoothDevice> devices = new HashMap<>();
    private final UUID id;

    private final HashSet<String> avoidedAddresses = new HashSet<>();

    BlEPeripheral(Context context, UUID id) {
        this.context = context;
        this.id = id;
    }

    @Override
    public void setListeners(BLEConnectListener connectListener, BLEDisconnectListener disconnectListener, BLEDataListener dataListener) {
        this.connectListener = connectListener;
        this.disconnectListener = disconnectListener;
        this.dataListener = dataListener;
    }



    @SuppressLint("MissingPermission")
    @Override
    public void startServer() throws Exception {
        if (server != null) return;


        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) throw new Exception("There is no support for Bluetooth");
        if (!adapter.isEnabled()) throw new Exception("Bluetooth is not enabled");

        server = manager.openGattServer(context, new BluetoothGattServerCallback() {
            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                super.onServiceAdded(status, service);
                Log.d(TAG, "service is listening");
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                if (characteristic.getUuid() == BLEConstants.IDCharacteristicUUID) {
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, id.toString().getBytes());
                } else if (characteristic.getUuid() == BLEConstants.DataCharacteristicUUID) {
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, "hello world".getBytes());
                } else {
                    Log.d(TAG, "unexpected read request from device:" + device.getName());
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                if (preparedWrite) {
                    Log.d(TAG, "got a prepared write request, dont know how to handle!!");
                } else {
                    Log.d(TAG, "recieved data" + value);
                }

                if (responseNeeded) {
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[0]);
                }
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

                if (descriptor.getUuid().equals(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))) {
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        Log.d(TAG, "Client enabled notifications");
                    } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        Log.d(TAG, "Client disabled notifications");
                    }

                    if (responseNeeded) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }
                } else {
                    Log.d(TAG, "Unknown descriptor write request");
                    if (responseNeeded) {
                        server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, value);
                    }
                }
            }


            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                super.onExecuteWrite(device, requestId, execute);
                Log.d(TAG, "execute write request, dont know how to handle!!");
            }
            //mine
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);

                if (newState != BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "Central not connected: "+device.getName()+newState);
                    return;
                }


                String nameTab = "Galaxy Tab A";
                String namePhone = "Galaxy A20s";
                if (!namePhone.equals(device.getName())){
                    Log.d(TAG, "preventing connection with "+device.getName()+device.getAddress());
                    server.cancelConnection(device);
                    return;
                }
                Log.d(TAG, "Connection state has worked!!"+device.getName()+device.getAddress());
//                connectListener.onEvent(new BLEDevice(null, device.getName(), device.getAddress()));
            }
        });

        //
        BluetoothGattService service = new BluetoothGattService(BLEConstants.ServiceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);


        idCharacteristic = new BluetoothGattCharacteristic(
                BLEConstants.IDCharacteristicUUID,
                PROPERTY_READ | PROPERTY_WRITE,
                PERMISSION_READ | PERMISSION_WRITE
        );
        dataCharacteristic = new BluetoothGattCharacteristic(
                BLEConstants.DataCharacteristicUUID,
                PROPERTY_READ | PROPERTY_WRITE | PROPERTY_NOTIFY,
                PERMISSION_READ | PERMISSION_WRITE
        );

        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor( BLEConstants.CCCD,
BluetoothGattDescriptor.PERMISSION_READ |
            BluetoothGattDescriptor.PERMISSION_WRITE );

        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        dataCharacteristic.addDescriptor(descriptor);

        service.addCharacteristic(idCharacteristic);
        service.addCharacteristic(dataCharacteristic);
        boolean added = server.addService(service);
                if (!added){
                    Log.d(TAG, "Could not add service");
                }else {
                    Log.d(TAG, "added service");
                }
        //

        BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(BLEConstants.ServiceUUID))
                .setIncludeTxPowerLevel(false).build();

        //


        advertiser.startAdvertising(settings, data,
                new AdvertiseCallback() {
                    @Override
                    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        Log.d(TAG, "Advertising started successfully.");
                    }

                    @Override
                    public void onStartFailure(int errorCode) {
                        Log.e(TAG, "Advertising failed with error code: " + errorCode);
                    }
                });


        Log.d(TAG, "started peripheral server");
    }

//    private BluetoothGattServerCallback createRequestHandler() {
//
//        BlEPeripheral self = this;
//        return new BluetoothGattServerCallback() {
//            @SuppressLint("MissingPermission")
//            @Override
//            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
//                super.onConnectionStateChange(device, status, newState);
//                if (status != BluetoothGatt.GATT_SUCCESS){
//                    Log.d(TAG, "central failed to either connect or disconnect. name :"+device.getName());
//                    return;
//                }
//
//                if (newState == BluetoothGatt.STATE_CONNECTED){
//                    Log.d(TAG, "central named:"+device.getName()+" has connected ...");
//                }else if (newState == BluetoothGatt.STATE_DISCONNECTED){
//                    if (devices.containsKey(device.getAddress())){
//                        Log.d(TAG, "central named:"+device.getName()+" has disconnected ...");
//                        devices.remove(device.getAddress());
//                        try {
//                            disconnectListener.onEvent(device.getAddress());
//                        } catch (Exception e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
//                }
//            }
//
//
//            @SuppressLint("MissingPermission")
//            @Override
//            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
//                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
//
//                Log.d(TAG, "unexpected read request from device:"+device.getName());
//                if (characteristic.getUuid().equals(BLEConstants.IDCharacteristicUUID)) {
//                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, id.toString().getBytes());
//                } else {
//                    server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
//                }
//            }
//
//            @SuppressLint("MissingPermission")
//            @Override
//            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
//                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
//
//                if (characteristic.getUuid() == BLEConstants.IDCharacteristicUUID){
//                    if (avoidedAddresses.contains(device.getAddress())) return;
//
//                    //hooray
//                    String uuidString = new String(value); // Convert byte array to string
//                    UUID id = UUID.fromString(uuidString); // Parse UUID from the string
//                    BLEDevice bleDevice = new BLEDevice(id, device.getName(), device.getAddress());
//
//                    //TODO: handle if send response fails
//                    server.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,self.id.toString().getBytes());
//                    devices.put(device.getAddress(), device);
//                    connectListener.onEvent(bleDevice);
//                    Log.d(TAG, "central device: " + device.getName() + "has connected");
//                }else if (characteristic.getUuid() == BLEConstants.DataCharacteristicUUID){
//                    Log.d(TAG, "data received from central:" + device.getName());
//                    dataListener.onEvent(value, device.getAddress());
//                }else if (responseNeeded) {
//                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
//                }
//
//            }
//
//            @SuppressLint("MissingPermission")
//            @Override
//            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
//                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
//                if (descriptor.getUuid().equals(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))) {
//                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
//                        Log.d(TAG, "Notifications enabled for device: " + device.getName());
//                    } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
//                        Log.d(TAG, "Notifications disabled for device: " + device.getName());
//                    }
//                    if (responseNeeded) {
//                        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
//                    }
//                }
//            }
//        };
//    }

    @SuppressLint("MissingPermission")
    @Override
    public void stopServer() {
        if (this.server == null) return;
        server.clearServices();
        server.close();
        this.server = null;
        this.devices = new HashMap<>();
        avoidedAddresses.clear();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void send(byte[] data, String address) throws Exception {
        BluetoothDevice device = devices.get(address);
        if (device == null){
            throw new Exception("central with address:"+address+" not found to send");
        }
        if (server == null){
            throw  new Exception("please start server first before sending data");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int code = server.notifyCharacteristicChanged(device,dataCharacteristic,true, data);
            Log.d(TAG, "likely successfully sent to central with name" + device.getName()+"and status:"+code);
        }else{
            dataCharacteristic.setValue(data);
            boolean isSuccess = server.notifyCharacteristicChanged(device,dataCharacteristic,true);
            Log.d(TAG, "isSuccessful:"+ (isSuccess?"true":"false")+" when sending to central with name" + device.getName());
        }

    }

    @Override
    public void avoid(String address) {
        avoidedAddresses.add(address);
    }

    @Override
    public void allow(String address) {
        avoidedAddresses.remove(address);
    }

}
