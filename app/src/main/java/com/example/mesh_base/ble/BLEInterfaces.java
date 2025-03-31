package com.example.mesh_base.ble;

import android.content.Context;

import com.example.mesh_base.global_interfaces.Device;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

class BLEDevice extends Device {

    String address;

    BLEDevice(UUID uuid, String name, String address) {
        super(uuid, name);
        this.address = address;
    }
}
