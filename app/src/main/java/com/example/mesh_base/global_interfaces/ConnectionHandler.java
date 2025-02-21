package com.example.mesh_base.global_interfaces;


import java.util.ArrayList;


public abstract class ConnectionHandler {

    protected NeighborDisconnectedListener neighborDisconnectedListener;
    protected NeighborConnectedListener neighborConnectedListener;
    protected NeighborDiscoveredListener neighborDiscoveredListener;
    protected DisconnectedListener disconnectedListener;
    protected DataListener dataListener;
    protected NearbyDevicesListener nearbyDevicesListener;

    public ConnectionHandler(NeighborConnectedListener neighborConnectedListener, NeighborDisconnectedListener neighborDisconnectedListener, NeighborDiscoveredListener neighborDiscoveredListener, DisconnectedListener disconnectedListener, DataListener dataListener, NearbyDevicesListener nearbyDevicesListener) {
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
