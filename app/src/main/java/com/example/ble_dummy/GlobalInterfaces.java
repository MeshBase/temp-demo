package com.example.ble_dummy;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

interface NeighborDisconnectedListener {
    void onEvent(Device device);
}

interface NeighborConnectedListener {
    void onEvent(Device device);
}

interface NeighborDiscoveredListener {
    void onEvent(Device device);
}

interface DisconnectedListener {
    void onEvent(Device device);
}

interface DataListener {
    void onEvent(byte[] data, Device neighbor);
}

interface NearbyDevicesListener {
    void onEvent(ArrayList<Device> devices);
}

abstract class Device {
    public UUID uuid;
    public String name;

    Device(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}

class SendError extends Exception {
    String message;

    SendError(String message) {
        this.message = message;
    }
}


abstract class ConnectionHandler {

    protected NeighborDisconnectedListener neighborDisconnectedListener;
    protected NeighborConnectedListener neighborConnectedListener;
    protected NeighborDiscoveredListener neighborDiscoveredListener;
    protected DisconnectedListener disconnectedListener;
    protected DataListener dataListener;
    protected NearbyDevicesListener nearbyDevicesListener;

    ConnectionHandler(NeighborConnectedListener neighborConnectedListener, NeighborDisconnectedListener neighborDisconnectedListener, NeighborDiscoveredListener neighborDiscoveredListener, DisconnectedListener disconnectedListener, DataListener dataListener, NearbyDevicesListener nearbyDevicesListener) {
        this.neighborConnectedListener = neighborConnectedListener;
        this.neighborDisconnectedListener = neighborDisconnectedListener;
        this.neighborDiscoveredListener = neighborDiscoveredListener;
        this.disconnectedListener = disconnectedListener;
        this.dataListener = dataListener;
        this.nearbyDevicesListener = nearbyDevicesListener;
    }

    public abstract ArrayList<Device> getNeighbourDevices();

    public abstract void start() throws Exception;

    public abstract void stop();

    public abstract ArrayList<Device> getNearbyDevices();

    public abstract void send(byte[] data) throws SendError; //Send to all neighbors

    public abstract void send(byte[] data, Device neighbor) throws SendError; //Send through a specific neighbor
}
