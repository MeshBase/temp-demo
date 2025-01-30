package com.example.ble_dummy;

import static androidx.core.app.ActivityCompat.startActivityForResult;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BLECentral implements BLECentralI {

    private BLEConnectListener connectListener;
    private BLEDisconnectListener disconnectListener;

    private BLEDataListener dataListener;
    private boolean scanning = false;
    private BluetoothLeScanner scanner;
    private final Context context;

    private static class Connection {
        BluetoothDevice device;
        BluetoothGatt gatt;

        Connection(BluetoothDevice d, BluetoothGatt g) {
            this.device = d;
            this.gatt = g;
        }
    }

    //TODO: use gatt instead of the Connection class
    private final HashMap<String, Connection> connections = new HashMap<>();


    private String TAG = "my_BLE_Central";
    private UUID id;

    private final HashSet<String> avoidedAddresses = new HashSet<>();

    BLECentral(Context context, UUID id) {
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
    public void startScan() throws Exception {
        Log.d(TAG, "scanning for ble peripherals");
        if (scanning) return;
        if (this.scanner == null) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            this.scanner = adapter.getBluetoothLeScanner();
        }
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BLEConstants.ServiceUUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanning = true;
        BLECentral self = this;
        scanner.startScan(
                filters, settings,
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);
                        BluetoothDevice bluetoothDevice = result.getDevice();
                        Log.d(TAG, "Potential device"+ bluetoothDevice.getName() + bluetoothDevice.getAddress());
                        if (avoidedAddresses.contains(bluetoothDevice.getAddress())) return;
                        self.connectPeripheral(bluetoothDevice);
                    }
                    @Override
                    public void onScanFailed(int errorCode) {
                        super.onScanFailed(errorCode);
                        Log.e(TAG, "Scan failed with error code: " + errorCode);
                    }
                }
        );
    }

    @SuppressLint("MissingPermission")
    private void connectPeripheral(BluetoothDevice bluetoothDevice) {
        BLECentral self = this;
        bluetoothDevice.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "peripheral failed to either connect or disconnect. name :" + gatt.getDevice().getName() + gatt.getDevice().getAddress());
                    return;
                }

                BluetoothGattService service = gatt.getService(BLEConstants.ServiceUUID);
                if (service == null){
                    Log.d(TAG, "no service discovery on device"+bluetoothDevice.getName());
                    return;
                }

                BluetoothGattCharacteristic idChar = service.getCharacteristic(BLEConstants.IDCharacteristicUUID);
                BluetoothGattCharacteristic dataChar = service.getCharacteristic(BLEConstants.DataCharacteristicUUID);
                if (idChar == null || dataChar == null) {
                    Log.d(TAG, "device doesn't have the "+(idChar==null?"id":"data")+" characteristic: " + bluetoothDevice.getName());
                    gatt.close();
                    return;
                }

                gatt.readCharacteristic(idChar);
                try {
                    sendViaCharacteristic(id.toString().getBytes(), gatt, BLEConstants.IDCharacteristicUUID);
                } catch (Exception e) {
                    Log.e(TAG, "couldn't exchange id with peripheral due to:" + e);
                }
                //TODO: clarify flow
              if (newState == BluetoothGatt.STATE_CONNECTED) {
                    gatt.discoverServices();
                    Log.d(TAG, "discovered peripheral, seeing if match" + gatt.getDevice().getName() + gatt.getDevice().getAddress());
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED && self.connections.containsKey(bluetoothDevice.getAddress())) {
                    try {
                        disconnectListener.onEvent(bluetoothDevice.getAddress());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    disconnect(bluetoothDevice.getAddress());
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "service could not be discovered for device: " + bluetoothDevice.getName() + ", address:" + bluetoothDevice.getAddress());
                    gatt.close();
                    return;
                }
                if (avoidedAddresses.contains(bluetoothDevice.getAddress())) return;

                BluetoothGattService service = gatt.getService(BLEConstants.ServiceUUID);
                if (service == null) {
                    Log.d(TAG, "device doesn't have the service: " + bluetoothDevice.getName());
                    gatt.close();
                    return;
                }

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(BLEConstants.IDCharacteristicUUID);
                if (characteristic == null) {
                    Log.d(TAG, "device doesn't have the characteristic: " + bluetoothDevice.getName());
                    gatt.close();
                    return;
                }

                enableNotifications(gatt, characteristic);
                gatt.readCharacteristic(characteristic);
                try {
                    sendViaCharacteristic(id.toString().getBytes(), gatt, BLEConstants.IDCharacteristicUUID);
                } catch (Exception e) {
                    Log.e(TAG, "couldn't exchange id with peripheral due to:" + e);
                }
            }

            private void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"));
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                    Log.d(TAG, "Notifications enabled");
                } else {
                    Log.e(TAG, "CCCD descriptor not found");
                }
            }
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                //Todo: use Futures or Threads per send request
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "something went wrong on the write request");
                    return;
                }

                if (characteristic.getUuid() == BLEConstants.IDCharacteristicUUID) {
                    if (avoidedAddresses.contains(bluetoothDevice.getAddress())) return;

                    //hooray
                    UUID id = UUID.fromString(Arrays.toString(characteristic.getValue()));
                    BLEDevice device = new BLEDevice(id, bluetoothDevice.getName(), bluetoothDevice.getAddress());
                    self.connections.put(bluetoothDevice.getAddress(), new Connection(bluetoothDevice, gatt));
                    connectListener.onEvent(device);
                    gatt.setCharacteristicNotification(characteristic, true);
                    Log.d(TAG, "peripheral device: " + bluetoothDevice.getName() + "has connected");
                } else if (characteristic.getUuid() == BLEConstants.DataCharacteristicUUID) {
                    Log.d(TAG, "message successfully recieved by peripheral");
                } else {
                    Log.e(TAG, "message from unknown characteristic:" + characteristic.getUuid() + " received");
                }
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
                super.onCharacteristicChanged(gatt, characteristic, value);
                if (characteristic.getUuid() == BLEConstants.DataCharacteristicUUID) {
                    Log.d(TAG, "received data from device:" + bluetoothDevice.getName() + " data:" + Arrays.toString(value));
                    dataListener.onEvent(value, bluetoothDevice.getAddress());
                } else {
                    Log.d(TAG, "received data from unknown characteristic with uuid:" + characteristic.getUuid() + " data:" + Arrays.toString(value));
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void stopScan() {
        if (scanner == null) return;
        scanner.stopScan(
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);
                        Log.d(TAG, "Stopped scanning");
                    }
                }
        );
        scanning = false;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void send(byte[] data, String address) throws Exception {
        Connection con = connections.get(address);
        if (con == null)
            throw new Exception("couldn't find device with address:" + address + " to send to");

        sendViaCharacteristic(data, con.gatt, BLEConstants.DataCharacteristicUUID);
    }

    @SuppressLint("MissingPermission")
    private void sendViaCharacteristic(byte[] data, BluetoothGatt gatt, UUID charactersticUUID) throws Exception {
        BluetoothGattService service = gatt.getService(BLEConstants.ServiceUUID);
        if (service == null)
            throw new Exception("couldn't find  gatt service for device with address:" + gatt.getDevice().getAddress() + " to send data");
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(charactersticUUID);
        if (characteristic == null)
            throw new Exception("couldn't find  characteristic:" + charactersticUUID.toString() + " for device with address:" + gatt.getDevice().getAddress() + " to send data");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(data);
        }
    }

    //returns true if it found the device to disconnect
    @SuppressLint("MissingPermission")
    private boolean disconnect(String address) {
        Connection con = connections.get(address);
        if (con == null) {
            return false;
        }
        con.gatt.close();
        connections.remove(address);
        Log.d(TAG, "device: " + con.device.getName() + "has disconnected");
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void stop() throws Exception {
        stopScan();
        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            String address = entry.getValue().device.getAddress();
            boolean successful = disconnect(address);
            if (!successful) {
                throw new Exception("Could'nt stop BLECentral as device with address:" + address + " couldn't be disconnected");
            }
        }
        avoidedAddresses.clear();
    }

    @Override
    public void avoid(String address) {
        //Prevents new connections with this address, but old ones are not harmed
        avoidedAddresses.add(address);
    }

    @Override
    public void allow(String address) {
        avoidedAddresses.remove(address);
    }


}
