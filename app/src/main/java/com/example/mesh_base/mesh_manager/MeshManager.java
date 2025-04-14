package com.example.mesh_base.mesh_manager;

import android.util.Log;

import androidx.activity.ComponentActivity;

import com.example.mesh_base.ble.BLEHandler;
import com.example.mesh_base.ble.BLEPermissions;
import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.router.MeshProtocol;
import com.example.mesh_base.router.Router;
import com.example.mesh_base.router.SendListener;

import java.util.ArrayList;
import java.util.UUID;

//TODO: unit test once BLE and WifiDirect have the same interfaces
public class MeshManager {

  private final ArrayList<ConnectionHandler> helpers = new ArrayList<>();
  //TODO: store uuid in local storage so that the devices address is consistent
  private final UUID id;
  private final Router router;
  //TODO: discuss directly using permission classes vs using them behind connection handlers
  private final BLEPermissions blePermissions;
  private final BLEHandler bleHelper;
  String TAG = "my_meshManager";
  private boolean isOn = false;
  private MeshManagerListener listener = MeshManagerListener.createEmpty();

  public MeshManager(ComponentActivity context) {

    Store store = Store.getInstance(context);
    if (store.getId() == null) store.storeId(UUID.randomUUID());
    id = store.getId();

    bleHelper = new BLEHandler(
            (device) -> {
              Log.d(TAG, "neighbor connected");
              listener.onNeighborsChanged(getNeighbors());
              listener.onConnected(device);
            },
            (device) -> {
              Log.d(TAG, "neighbor disconnected");
              listener.onNeighborsChanged(getNeighbors());
              listener.onDisconnected(device);
            },
            (device) -> {
              Log.d(TAG, "neighbor discovered");
              listener.onDiscovered(device);
            },
            //TODO: Consider removing argument from ConnectionHandler if WIFI Direct is also not using it
            (device) -> {
              Log.d(TAG, "disconnected");
            },
            //Callback is overridden by router until connection handlers support multiple listeners
            (data, device) -> {
              Log.d(TAG, "received Data");
            },

            //TODO: Consider removing this argument as router is better for discovering neighbors of neighbors
            (devices) -> {
              Log.d(TAG, "nearby devices changed");
            },
            context,
            id
    );
    helpers.add(bleHelper);

    blePermissions = new BLEPermissions(context);
    blePermissions.setListener(new BLEPermissions.Listener() {
      @Override
      public void onEnabled() {
        try {
          bleHelper.start();
          listener.onStatusChange(getStatus());
        } catch (Exception e) {
          //TODO: categorize types of exceptions shown to users, then create classes
          listener.onError(e);
        }
      }

      @Override
      public void onDisabled() {
        listener.onStatusChange(getStatus());
        bleHelper.stop();
        listener.onStatusChange(getStatus());
      }
    });

    router = new Router(helpers, id);
    //TODO: accept protocol instead of byte array once the router's handleOnData is modified, to not cause conflict
    //TODO: consider exposing the protocol itself to users
    router.setOnReceivedData(((data, neighbor) -> {
      listener.onData(data, neighbor);
    }));
  }

  public UUID getId() {
    return id;
  }

  public void setListener(MeshManagerListener listener) {
    this.listener = listener;
  }

  public void on() {
    //TODO: if having a list of permission classes is possible, loop through them and call .enable()
    //calling .enable() even if already enabled should call the onEnabled listener, which will then call start()
    blePermissions.enable();
    isOn = true;
    listener.onStatusChange(getStatus());
  }

  public void off() {
    for (ConnectionHandler helper : helpers) {
      helper.stop();
    }
    isOn = false;
    listener.onStatusChange(getStatus());
  }

  public ArrayList<Device> getNeighbors() {
    ArrayList<Device> neighbors = new ArrayList<Device>();
    for (ConnectionHandler helper : helpers) {
      neighbors.addAll(helper.getNeighbourDevices());
    }
    return neighbors;
  }

  public Status getStatus() {
    //TODO: discuss if maps are better to handle this rather than a Status class
    return new Status(
            isOn,
            //TODO: add a method to BLEPermission to know if it is supported
            new Status.Property(true, bleHelper.isOn(), blePermissions.isEnabled()),
            //TODO: modify when WIFI Direct is added
            new Status.Property(false, false, false)
    );
  }


  public void send(MeshProtocol<?> protocol, SendListener listener) {
    router.sendData(protocol, listener);
  }
}
