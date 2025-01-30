package com.example.ble_dummy;

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

public class BLEHandler extends ConnectionHandler {
    private final BLECentralI bleCentral;
    private final BLEPeripheralI blePeripheral;
    private final HashMap<String, BLEDevice> peripherals = new HashMap<>();
    private final HashMap<String, BLEDevice> centrals = new HashMap<>();
    String TAG = "My_BLEHandler";


    BLEHandler(BLECentralI bleCentral, BLEPeripheralI blePeripheral, NeighborConnectedListener neighborConnectedListener, NeighborDisconnectedListener neighborDisconnectedListener, NeighborDiscoveredListener neighborDiscoveredListener, DisconnectedListener disconnectedListener, DataListener dataListener, NearbyDevicesListener nearbyDevicesListener) {
        super(neighborConnectedListener, neighborDisconnectedListener, neighborDiscoveredListener, disconnectedListener, dataListener, nearbyDevicesListener);
        this.bleCentral = bleCentral;
        this.blePeripheral = blePeripheral;

    }

    @Override
    public ArrayList<Device> getNeighbourDevices() {
        ArrayList<Device> neighbors = new ArrayList<>(peripherals.values());
        neighbors.addAll(centrals.values());
        return neighbors;
    }

    private boolean exists(String address){
        return centrals.containsKey(address) || peripherals.containsKey(address);
    }

    //Please make sure bleEnabler.isEnabled() == true before starting
    @Override
    public void start() throws Exception {
        Log.d(TAG, "starting bluetooth handler");
        stop();

        bleCentral.setListeners(
                // on connect
                device -> {
                    if (exists(device.address)){
                        Log.e(TAG,"connecting device:"+device.name+" already exists");
                        return;
                    }
                    peripherals.put(device.address, device);
                    this.neighborConnectedListener.onEvent(device);
                    this.nearbyDevicesListener.onEvent(getNearbyDevices());
                    blePeripheral.avoid(device.address);
                },
                // on disconnect
                address -> {
                    if (!peripherals.containsKey(address)) {
                        Log.e(TAG,"peripheral with address:" + address + " not found while trying to disconnect");
                        return;
                    }
                    Device device = peripherals.remove(address);
                    this.neighborDisconnectedListener.onEvent(device);
                    this.nearbyDevicesListener.onEvent(getNearbyDevices());
                    blePeripheral.allow(address);
                },
                //on data
                (data, address) -> {
                    if (!exists(address)) {
                        //TODO: Make a universal error handling way
                        Log.e(TAG,"received data from unknown peripheral with address:"+address+" data:"+ Arrays.toString(data));
                        return;
                    }
                    dataListener.onEvent(data, peripherals.get(address));
                }
        );

        bleCentral.startScan();
        blePeripheral.setListeners(
                //on connect
                device -> {
                    if(exists(device.address)){
                        Log.e(TAG,"connecting central:"+device.name+" already exists");
                        return;
                    }
                    centrals.put(device.address, device);
                    this.neighborConnectedListener.onEvent(device);
                    this.nearbyDevicesListener.onEvent(getNearbyDevices());
                    bleCentral.avoid(device.address);
                },
                //on disconnect
                address -> {
                    if (!centrals.containsKey(address)){
                        Log.e(TAG,"peripheral with address:" + address + " not found while trying to disconnect");
                        return;
                    }
                    Device central = centrals.remove(address);
                    this.neighborDisconnectedListener.onEvent(central);
                    this.nearbyDevicesListener.onEvent(getNearbyDevices());
                    bleCentral.allow(address);
                },
                //on data
                (data, address) -> {
                    if (!centrals.containsKey(address)){
                        Log.e(TAG,"recieved data from unknown central with address:"+address+" data:"+Arrays.toString(data));
                        return;
                    }
                    dataListener.onEvent(data, centrals.get(address));
                }
        );
        blePeripheral.startServer();
    }

    @Override
    public void stop() {
        try {
            bleCentral.stop();
            blePeripheral.stopServer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        peripherals.clear();
        centrals.clear();
    }

    @Override
    public ArrayList<Device> getNearbyDevices() {
        //TODO: return surrounding 2nd neighbors too
        return getNeighbourDevices();
    }

    @Override
    public void send(byte[] data) throws SendError {

        for (String address: peripherals.keySet()) {
            send(data, peripherals.get(address));
        }
        for (String address: centrals.keySet()) {
            send(data, centrals.get(address));
        }
    }

    @Override
    public void send(byte[] data, Device device) throws SendError {
        //TODO: make it take only O(1) time to find the device
        for (BLEDevice bleDevice : peripherals.values()) {
            if (bleDevice.uuid != device.uuid) continue;
            try {
                bleCentral.send(data, bleDevice.address);
                return;
            } catch (Exception e) {
                throw new SendError("Couldn't send to " + bleDevice.name);
            }
        }

        for (BLEDevice bleDevice : centrals.values()) {
            if (bleDevice.uuid != device.uuid) continue;
            try {
                blePeripheral.send(data, bleDevice.address);
                return;
            } catch (Exception e) {
                throw new SendError("Couldn't send to " + bleDevice.name);
            }
        }

        throw new SendError("Neighbor:" + device.name + " not found");
    }

}
