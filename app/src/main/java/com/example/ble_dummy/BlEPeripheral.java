package com.example.ble_dummy;

import static android.bluetooth.BluetoothGattCharacteristic.*;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
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

    private final BluetoothGattCharacteristic idCharacteristic;

    private final BluetoothGattCharacteristic dataCharacteristic;

    private BluetoothGattServer server;
    private HashMap<String, BluetoothDevice> devices = new HashMap<>();
    private final UUID id;

    private final HashSet<String> avoidedAddresses = new HashSet<>();

    BlEPeripheral(Context context, UUID id) {
        this.context = context;
        this.id = id;
        idCharacteristic = new BluetoothGattCharacteristic(
                BLEConstants.IDCharacteristicUUID,
                PROPERTY_READ | PROPERTY_WRITE,
                PERMISSION_READ | PERMISSION_WRITE
        );
        dataCharacteristic = new BluetoothGattCharacteristic(
                BLEConstants.IDCharacteristicUUID,
                PROPERTY_READ | PROPERTY_WRITE | PROPERTY_NOTIFY,
                PERMISSION_READ | PERMISSION_WRITE
        );
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

        BluetoothGattService service = new BluetoothGattService(BLEConstants.ServiceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(idCharacteristic);
        service.addCharacteristic(dataCharacteristic);

        this.server = manager.openGattServer(context,createRequestHandler());
        boolean serviceAdded = server.addService(service);
        if (!serviceAdded){
            throw  new Exception("Service couldn't be created");
        }
        Log.d(TAG, "started peripheral server");
    }

    private BluetoothGattServerCallback createRequestHandler() {

        BlEPeripheral self = this;
        return new BluetoothGattServerCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                if (status != BluetoothGatt.GATT_SUCCESS){
                    Log.d(TAG, "central failed to either connect or disconnect. name :"+device.getName());
                    return;
                }

                if (newState == BluetoothGatt.STATE_CONNECTED){
                    Log.d(TAG, "central named:"+device.getName()+" has connected ...");
                }else if (newState == BluetoothGatt.STATE_DISCONNECTED){
                    if (devices.containsKey(device.getAddress())){
                        Log.d(TAG, "central named:"+device.getName()+" has disconnected ...");
                        devices.remove(device.getAddress());
                        try {
                            disconnectListener.onEvent(device.getAddress());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }


            @SuppressLint("MissingPermission")
            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                Log.d(TAG, "unexpected read request from device:"+device.getName());
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

                if (characteristic.getUuid() == BLEConstants.IDCharacteristicUUID){
                    if (avoidedAddresses.contains(device.getAddress())) return;

                    //hooray
                    UUID id = UUID.fromString(Arrays.toString(value));
                    BLEDevice bleDevice = new BLEDevice(id, device.getName(), device.getAddress());
                    //TODO: handle if send response fails
                    server.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,self.id.toString().getBytes());
                    devices.put(device.getAddress(), device);
                    connectListener.onEvent(bleDevice);
                    Log.d(TAG, "central device: " + device.getName() + "has connected");
                }else if (characteristic.getUuid() == BLEConstants.DataCharacteristicUUID){
                    Log.d(TAG, "data received from central:" + device.getName());
                    dataListener.onEvent(value, device.getAddress());
                }

            }
        };
    }

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
            server.notifyCharacteristicChanged(device,dataCharacteristic,true, data);
        }else{
            dataCharacteristic.setValue(data);
            server.notifyCharacteristicChanged(device,dataCharacteristic,true);
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
