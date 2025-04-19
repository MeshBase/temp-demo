package com.example.mesh_base.mesh_manager;

import androidx.activity.ComponentActivity;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.ConnectionHandlerFactory;
import com.example.mesh_base.global_interfaces.ConnectionHandlerListener;
import com.example.mesh_base.global_interfaces.ConnectionHandlersEnum;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.router.MeshProtocol;
import com.example.mesh_base.router.Router;
import com.example.mesh_base.router.SendListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

//TODO: unit test once BLE and WifiDirect have the same interfaces
public class MeshManager {
    private final HashMap<ConnectionHandlersEnum, ConnectionHandler> connectionHandlers = new HashMap<>();
    //TODO: store uuid in local storage so that the devices address is consistent
    private final UUID id;
    private final Router router;
    String TAG = "my_meshManager";
    private boolean isOn = false;
    private MeshManagerListener listener = MeshManagerListener.createEmpty();

    public MeshManager(ComponentActivity context) {
        Store store = Store.getInstance(context);
        if (store.getId() == null) store.storeId(UUID.randomUUID());
        id = store.getId();

        ConnectionHandlerFactory factory = new ConnectionHandlerFactory();

        // TODO: Check for user connection handler preference
        for (ConnectionHandlersEnum _enum : ConnectionHandlersEnum.values()) {
            ConnectionHandler connectionHandler = factory.createConnectionHandler(_enum, context, id);
            connectionHandlers.put(_enum, connectionHandler);
            connectionHandler.subscribe(
                    new ConnectionHandlerListener() {
                        @Override
                        public void onNeighborConnected(Device device) {
                            listener.onNeighborConnected(device);
                        }

                        @Override
                        public void onNeighborDisconnected(Device device) {
                            listener.onNeighborDisconnected(device);
                        }

                        @Override
                        public void onDisconnected() {
                            listener.onStatusChange(getStatus());
                        }

                        @Override
                        public void onConnected() {
                            // TODO: change status
                            listener.onStatusChange(getStatus());
                        }

                        @Override
                        public void onDataReceived(Device device, byte[] data) {
                            if (device.uuid == id) {
                                listener.onDataReceivedForSelf(data);
                            }
                        }
                    }
            );
        }

        router = new Router(connectionHandlers, id);
        //TODO: accept protocol instead of byte array once the router's handleOnData is modified, to not cause conflict
        //TODO: consider exposing the protocol itself to users
        router.setOnReceivedData(((data, neighbor) -> {
            listener.onDataReceivedForSelf(data);
        }));
    }

    public UUID getId() {
        return id;
    }

    public void setListener(MeshManagerListener listener) {
        this.listener = listener;
    }

    public void on() {
        for (ConnectionHandler connectionHandler : connectionHandlers.values()) {
            connectionHandler.enable();
        }
        isOn = true;
    }

    public void off() {
        for (ConnectionHandler helper : connectionHandlers.values()) {
            helper.stop();
        }
        isOn = false;
    }

    public ArrayList<Device> getNeighbors() {
        ArrayList<Device> neighbors = new ArrayList<Device>();
        for (ConnectionHandler helper : connectionHandlers.values()) {
            neighbors.addAll(helper.getNeighbourDevices());
        }
        return neighbors;
    }

    public Status getStatus() {
        HashMap<ConnectionHandlersEnum, Status.Property> _status = new HashMap<>() {
            {
                for (Entry<ConnectionHandlersEnum, ConnectionHandler> helper : connectionHandlers.entrySet()) {
                    ConnectionHandlersEnum key = helper.getKey();
                    ConnectionHandler value = helper.getValue();
                    // TODO: check status and is on and is allowed in the connection handlers
                    put(key, new Status.Property(true, true, true));
                }
            }
        };

        return new Status(isOn, _status);
    }

    public void send(MeshProtocol<?> protocol, SendListener listener) {
        router.sendData(protocol, listener);
    }
}
