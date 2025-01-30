package com.example.ble_dummy;

class Status {
    static class Property {
        boolean isSupported;
        boolean isOn;
        boolean isAllowed;
    }

    Property ble;
    Property wifi;
    Status(Property ble, Property wifi){
        this.ble = ble;
        this.wifi = wifi;
    }

}

interface  MeshBaseListener {
    void onData(byte[] data);
    void onStatusChange (Status status);
    //TODO: add medium type in devices
    void onNeighborsChanged();
}


public class MeshBase {
    private MeshBaseListener listener;
    void on(MeshBaseListener listener){
        this.listener = listener;
    }
    void off(){}

    void setAllowedMedium(){}

    void getNeighbors(){}
    void send(byte[] data, String address){}
    void send(String data, String address){}

}
