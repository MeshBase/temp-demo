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

public class MeshManager {

  private final ArrayList<ConnectionHandler> helpers = new ArrayList<>();
  //TODO: store uuid in local storage so that the devices address is consistent
  private final UUID id = UUID.randomUUID();
  private final Router router;
  //TODO: discuss directly using permission classes vs using them behind connection handlers
  private final BLEPermissions blePermissions;
  private final BLEHandler bleHelper;
  String TAG = "my_meshManager";
  private MeshManagerListener listener = MeshManagerListener.createEmpty();

  public MeshManager(ComponentActivity context) {
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
            //TODO: Remove argument from ConnectionHandler if using BLEPermissions to listen to connection/disconnection events
            //TODO: Remove device as argument as it is know that THIS device is the one which disconnected
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
          //TODO: send error to user using a callback
        }
      }

      @Override
      public void onDisabled() {
        listener.onStatusChange(getStatus());
        bleHelper.stop();
      }
    });

    router = new Router(helpers, id);
    //TODO: accept protocol instead of byte array once the router's handleOnData is modified, to not cause conflict
    //TODO: consider exposing the protocol itself to users
    router.setOnReceivedData(listener::onData);
  }

  public UUID getId() {
    return id;
  }

  public void setListener(MeshManagerListener listener) {
    this.listener = listener;
  }

  public void on() {
    //TODO: if having a list of permission classes is possible, loop through them and call .enable()
    //calling .enable() even if already enabled will call the onEnabled listener, which will call start()
    blePermissions.enable();
  }

  public void off() {
    for (ConnectionHandler helper : helpers) {
      helper.stop();
    }
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
    //TODO: consider generating a map instead of changing the Status class whenever a new technology is added
    return new Status(
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
