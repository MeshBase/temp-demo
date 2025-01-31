package com.example.ble_dummy;

import android.annotation.SuppressLint;
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
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final HashSet<String> temporaryAvoidAddresses = new HashSet<>();

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
        scanning = true;
        BLECentral self = this;
        ScanFilter serviceFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BLEConstants.ServiceUUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(serviceFilter);
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        scanner.startScan(
                filters,settings,
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);
                        String address = result.getDevice().getAddress();
                        if (self.isAllowed(address) && !temporaryAvoidAddresses.contains(address)){
                            temporaryAvoidAddresses.add(address);
                            connectPeripheral(result.getDevice());
                        }
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
        BluetoothGatt gatt = bluetoothDevice.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                if (newState != BluetoothGatt.STATE_CONNECTED) {
                    int seconds = 4;
                    Log.d(TAG, " a peripheral could not connect. Black listed for "+seconds+"seconds! name :" + bluetoothDevice.getName() + bluetoothDevice.getAddress()+"status:"+status);
                    gatt.close();
                    new Thread(()->{
                        //delay as suggested in https://medium.com/android-news/lessons-for-first-time-android-bluetooth-le-developers-i-learned-the-hard-way-fee07646624
                        try {
                            Thread.sleep(4000);
                            temporaryAvoidAddresses.remove(bluetoothDevice.getAddress());
                            Log.d(TAG, bluetoothDevice.getName()+"not in black list anymore");
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }else{
                    Log.d(TAG, "peripheral kinda connected :" + bluetoothDevice.getName() + bluetoothDevice.getAddress()+" now discovering services");
                    gatt.discoverServices();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                BluetoothGattService service = gatt.getService(BLEConstants.ServiceUUID);
                BluetoothGattCharacteristic idCharacteristic = service.getCharacteristic(BLEConstants.IDCharacteristicUUID);

                Log.d(TAG, "service kinda discovered for "+bluetoothDevice.getName());
                if (idCharacteristic == null) {
                    boolean success = gatt.readCharacteristic(idCharacteristic);
                    Log.d(TAG, "read characteristic success:" + success);
                }

                BluetoothGattCharacteristic dataCharacteristic = service.getCharacteristic(BLEConstants.DataCharacteristicUUID);
                if (dataCharacteristic == null) {
                    Log.d(TAG, "data characteristic not found");
                    return;
                }
                gatt.setCharacteristicNotification(dataCharacteristic, true);
                BluetoothGattDescriptor desc = dataCharacteristic.getDescriptor(BLEConstants.CCCD);
                if (desc == null) {
                    Log.d(TAG, "descriptor not found");
                    return;
                }
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(desc);
            }

            @Override
            public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                if (characteristic.getUuid() == BLEConstants.IDCharacteristicUUID) {
                    Log.d(TAG, "peripheral id is" + Arrays.toString(characteristic.getValue()));
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                if (characteristic.getUuid() == BLEConstants.DataCharacteristicUUID) {
                    Log.d(TAG, "Wriet status:" + status);
                }
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                Log.d(TAG, "characterstic changed:" + Arrays.toString(characteristic.getValue()));
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "descriptor write success on" + descriptor.getUuid() + " where char =" + descriptor.getCharacteristic());
                } else {
                    Log.d(TAG, "descriptor write fail on" + descriptor.getUuid() + " where char =" + descriptor.getCharacteristic() + "status=" + status);
                }
            }
        }, BluetoothDevice.TRANSPORT_LE);


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

        sendViaCharacteristic(data, con.gatt);
    }

    @SuppressLint("MissingPermission")
    private void sendViaCharacteristic(byte[] data, BluetoothGatt gatt) throws Exception {
        Log.d(TAG, "sending data:" + Arrays.toString(data));
        BluetoothGattService service = gatt.getService(BLEConstants.ServiceUUID);
        if (service == null)
            throw new Exception("couldn't find  gatt service for device with address:" + gatt.getDevice().getAddress() + " to send data");
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(BLEConstants.DataCharacteristicUUID);
        if (characteristic == null)
            throw new Exception("couldn't find  characteristic:" + BLEConstants.DataCharacteristicUUID.toString() + " for device with address:" + gatt.getDevice().getAddress() + " to send data");

        characteristic.setValue(data);
        boolean success = gatt.writeCharacteristic(characteristic);
        Log.d(TAG, "write characteristic success:" + success);
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

    private boolean isAllowed(String address){
        return !avoidedAddresses.contains(address);
    }

    @Override
    public void allow(String address) {
        avoidedAddresses.remove(address);
    }


}
