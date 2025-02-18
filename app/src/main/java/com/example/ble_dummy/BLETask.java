package com.example.ble_dummy;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import java.util.Arrays;

public abstract class BLETask {
    public long expireMilli = 3_000L;
    public boolean expires = true;
    public abstract String asString();
}

class Scan extends BLETask {
    int devicesBeforeConnect;
    boolean expires = false;

    Scan(int devicesBeforeConnect){
        this.devicesBeforeConnect =devicesBeforeConnect;
    }

    Scan(){
        this.devicesBeforeConnect = 5;
    }

    @Override
    public String asString() {
        return "Scan: devicesBeforeConnect = " + devicesBeforeConnect;
    }
}

abstract class CentralTask extends BLETask {

}

class ConnectToPeripheral extends CentralTask {
    BluetoothDevice device;

    ConnectToPeripheral(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public String asString() {
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "ConnectToPeripheral: device = " + deviceName + " (" + device.getAddress() + "), retriesLeft = ";}
}

class DiscoverServices extends CentralTask {
    BluetoothGatt gatt;

    DiscoverServices(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    @Override
    public String asString() {
        BluetoothDevice device = gatt.getDevice();
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "DiscoverServices: device = " + deviceName
                + " (" + device.getAddress() + ")";
    }
}

class ReadCharacteristic extends CentralTask{
    BluetoothGattCharacteristic characteristic;
    BluetoothGatt gatt;
    ReadCharacteristic(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt){
        this.characteristic = characteristic;
        this.gatt = gatt;
    }

    @Override
    public String asString() {
        return "read characteristic - "+characteristic.getUuid();
    }
}
class WriteCharacteristic extends CentralTask {
    BluetoothGatt gatt;
    byte[] data;
    int remainingRetries;
    BluetoothGattCharacteristic characteristic;

    WriteCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] data, int remainingRetries) {
        this.gatt = gatt;
        this.data = data;
        this.characteristic = characteristic;
        this.remainingRetries = remainingRetries;
    }

    @Override
    public String asString() {
        BluetoothDevice device = gatt.getDevice();
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "WriteCharacteristic: device = " + deviceName
                + " (" + device.getAddress() + "), data = " + Arrays.toString(data)
                + ", remainingRetries = " + remainingRetries;
    }
}

class DisconnectPeripheral extends CentralTask {
    BluetoothGatt gatt;

    DisconnectPeripheral(BluetoothGatt gatt) {
        this.gatt = gatt;
    }
    DisconnectPeripheral(BluetoothGatt gatt,  boolean forgetRetries) {
        this.gatt = gatt;
    }

    @Override
    public String asString() {
        BluetoothDevice device = gatt.getDevice();
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "DisconnectPeripheral: device = " + deviceName
                + " (" + device.getAddress() + ")" ;
    }
}



abstract class PeripheralTask extends BLETask {

}
class StartGattServer extends PeripheralTask {

    @Override
    public String asString() {
        return "StartGattServer";
    }
}

class Advertise extends PeripheralTask {

    @Override
    public String asString() {
        return "Advertise";
    }
}

class ConnectCentral extends PeripheralTask {
    BluetoothDevice device;

    ConnectCentral(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public String asString() {
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "ConnectCentral: device = " + deviceName
                + " (" + device.getAddress() + ")";
    }
}

class SendResponse extends PeripheralTask {
    BluetoothDevice device;
    int requestId;
    int newState;
    int offset;
    byte[] data;

    SendResponse(BluetoothDevice device, int requestId, int newState, int offset, byte[] data) {
        this.device = device;
        this.requestId = requestId;
        this.newState = newState;
        this.offset = offset;
        this.data = data;
    }

    @Override
    public String asString() {
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "SendWriteResponse: device = " + deviceName
                + " (" + device.getAddress() + "), requestId = " + requestId
                + ", newState = " + newState + ", offset = " + offset
                + ", data = " + Arrays.toString(data);
    }
}

class DisconnectCentral extends PeripheralTask {
    BluetoothDevice device;

    DisconnectCentral(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public String asString() {
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "DisconnectCentral: device = " + deviceName
                + " (" + device.getAddress() + ")";
    }
}

class CloseGatt extends PeripheralTask {

    @Override
    public String asString() {
        return "CloseGatt";
    }
}
