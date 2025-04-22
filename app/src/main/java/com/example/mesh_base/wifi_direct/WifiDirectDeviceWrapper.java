package com.example.mesh_base.wifi_direct;

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.example.mesh_base.global_interfaces.Device;

import java.net.InetAddress;
import java.util.UUID;

public class WifiDirectDeviceWrapper extends Device {
    private final WifiP2pDevice internalDevice;

    public WifiDirectDeviceWrapper(UUID uuid, WifiP2pDevice device) {
        super(uuid, device.deviceName);
        this.internalDevice = device;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            this.internalDevice.writeToParcel(new Parcelable() {
                @Override
                public int describeContents() {
                    return 0;
                }

                @Override
                public void writeToParcel(@NonNull Parcel parcel, int i) {

                }
            });
            InetAddress ip = this.internalDevice.getIpAddress();
        }

    }

    public WifiP2pDevice getInternalDevice() {
        return internalDevice;
    }

    // Optionally, add helper methods to expose WifiP2pDevice attributes.
    public String getDeviceAddress() {
        return internalDevice.deviceAddress;
    }
}
