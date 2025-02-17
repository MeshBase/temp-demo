package com.example.ble_dummy;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import java.util.Arrays;

public abstract class BLETask {
    public long expireMilli = 1000L;
    public abstract String asString();
}

class Scan extends BLETask {
    int devicesBeforeConnect = 1;

    @Override
    public String asString() {
        return "Scan: devicesBeforeConnect = " + devicesBeforeConnect
                + ", expireMilli = " + expireMilli;
    }
}

class ConnectToPeripheral extends BLETask {
    BluetoothDevice device;

    ConnectToPeripheral(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public String asString() {
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "ConnectToPeripheral: device = " + deviceName + " (" + device.getAddress() + "), retriesLeft = " + ", expireMilli = " + expireMilli; }
}

class DiscoverServices extends BLETask {
    BluetoothGatt gatt;

    DiscoverServices(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    @Override
    public String asString() {
        BluetoothDevice device = gatt.getDevice();
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "DiscoverServices: device = " + deviceName
                + " (" + device.getAddress() + "), expireMilli = " + expireMilli;
    }
}

class WriteCharacteristic extends BLETask {
    BluetoothGatt gatt;
    byte[] data;
    int remainingRetries;

    WriteCharacteristic(BluetoothGatt gatt, byte[] data, int remainingRetries) {
        this.gatt = gatt;
        this.data = data;
        this.remainingRetries = remainingRetries;
    }

    @Override
    public String asString() {
        BluetoothDevice device = gatt.getDevice();
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "WriteCharacteristic: device = " + deviceName
                + " (" + device.getAddress() + "), data = " + Arrays.toString(data)
                + ", remainingRetries = " + remainingRetries
                + ", expireMilli = " + expireMilli;
    }
}

class DisconnectPeripheral extends BLETask {
    BluetoothGatt gatt;

    DisconnectPeripheral(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    @Override
    public String asString() {
        BluetoothDevice device = gatt.getDevice();
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "DisconnectPeripheral: device = " + deviceName
                + " (" + device.getAddress() + "), expireMilli = " + expireMilli;
    }
}

class StartGattServer extends BLETask {

    @Override
    public String asString() {
        return "StartGattServer, expireMilli = " + expireMilli;
    }
}

class Advertise extends BLETask {

    @Override
    public String asString() {
        return "Advertise, expireMilli = " + expireMilli;
    }
}

class ConnectCentral extends BLETask {
    BluetoothDevice device;

    ConnectCentral(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public String asString() {
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "ConnectCentral: device = " + deviceName
                + " (" + device.getAddress() + "), expireMilli = " + expireMilli;
    }
}

class SendWriteResponse extends BLETask {
    BluetoothDevice device;
    int requestId;
    int newState;
    int offset;
    byte[] data;

    SendWriteResponse(BluetoothDevice device, int requestId, int newState, int offset, byte[] data) {
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
                + ", data = " + Arrays.toString(data)
                + ", expireMilli = " + expireMilli;
    }
}

class DisconnectCentral extends BLETask {
    BluetoothDevice device;

    DisconnectCentral(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public String asString() {
        @SuppressLint("MissingPermission") String deviceName = (device.getName() != null) ? device.getName() : "Unknown";
        return "DisconnectCentral: device = " + deviceName
                + " (" + device.getAddress() + "), expireMilli = " + expireMilli;
    }
}

class CloseGatt extends BLETask {

    @Override
    public String asString() {
        return "CloseGatt, expireMilli = " + expireMilli;
    }
}
