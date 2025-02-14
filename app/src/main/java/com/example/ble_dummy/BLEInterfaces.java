package com.example.ble_dummy;


import android.app.Activity;

import androidx.activity.ComponentActivity;

import java.util.UUID;

 interface BLEPermissionListener{
    void onEnabled();
    void onDisabled();
}

class BLEDevice extends Device {

    String address;

    BLEDevice(UUID uuid, String name, String address) {
        super(uuid, name);
        this.address = address;
    }
}

interface BLEConnectListener {
    void onEvent(BLEDevice device);
}

interface BLEDisconnectListener {
    void onEvent(String address);
}

interface BLEDataListener {
    void onEvent(byte[] data, String address);
}


interface BLECentralI {


    void setListeners(BLEConnectListener connectListener, BLEDisconnectListener disconnectListener, BLEDataListener dataListener);

    void startScan() throws Exception;

    void stopScan();

    void send(byte[] data, String address) throws Exception;

    void stop() throws Exception;

    void avoid(String address);
    void allow(String address);
}

interface BLEPeripheralI {

    void setListeners(BLEConnectListener connectListener, BLEDisconnectListener disconnectListener, BLEDataListener dataListener);

    void startServer() throws Exception;

    void stopServer();

    void send(byte[] data, String address) throws Exception;

    void avoid(String address);
    void allow(String address);
}

class BLEConstants {
    static final UUID ServiceUUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    static final UUID IDCharacteristicUUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fc");
    static final  UUID DataCharacteristicUUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fd");
    static  final  UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
}

interface BLEEnabledListener{
    void onEvent();
}
interface BLEDisabledListener{
    void onEvent();
}

interface BLEEnablerI {

    void init(BLEEnabledListener enabledListener, BLEDisabledListener disabledListener, ComponentActivity activity);

    boolean isEnabled();
    void enable();
}
