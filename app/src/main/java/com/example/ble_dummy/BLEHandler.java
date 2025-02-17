package com.example.ble_dummy;

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

public class BLEHandler extends ConnectionHandler {

    BLEHandler(NeighborConnectedListener neighborConnectedListener, NeighborDisconnectedListener neighborDisconnectedListener, NeighborDiscoveredListener neighborDiscoveredListener, DisconnectedListener disconnectedListener, DataListener dataListener, NearbyDevicesListener nearbyDevicesListener) {
        super(neighborConnectedListener, neighborDisconnectedListener, neighborDiscoveredListener, disconnectedListener, dataListener, nearbyDevicesListener);
    }

    @Override
    public ArrayList<Device> getNeighbourDevices() {
        return null;
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() {

    }

    @Override
    public ArrayList<Device> getNearbyDevices() {
        return null;
    }

    @Override
    public void send(byte[] data) throws SendError {

    }

    @Override
    public void send(byte[] data, Device neighbor) throws SendError {

    }
}
