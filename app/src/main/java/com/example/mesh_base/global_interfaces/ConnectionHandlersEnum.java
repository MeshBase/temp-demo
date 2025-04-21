package com.example.mesh_base.global_interfaces;

public enum ConnectionHandlersEnum {
    BLE,
    WifiDirect;

    //TODO: use connectionHandlersEnum.values instead of implementedHandlers once WifiDirect is implemented
    public static final ConnectionHandlersEnum[] implementedHandlers = {
            BLE
    };
}
