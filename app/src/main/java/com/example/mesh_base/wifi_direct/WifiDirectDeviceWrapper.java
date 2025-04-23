package com.example.mesh_base.wifi_direct;

import android.net.wifi.p2p.WifiP2pDevice;

import com.example.mesh_base.global_interfaces.Device;

import java.util.UUID;

public class WifiDirectDeviceWrapper extends Device {
    private final WifiP2pDevice internalDevice;

    public WifiDirectDeviceWrapper(UUID uuid, WifiP2pDevice device) {
        super(uuid, device.deviceName);
        this.internalDevice = device;
    }

    public WifiP2pDevice getInternalDevice() {
        return internalDevice;
    }

    // Optionally, add helper methods to expose WifiP2pDevice attributes.
    public String getDeviceAddress() {
        return internalDevice.deviceAddress;
    }
}
