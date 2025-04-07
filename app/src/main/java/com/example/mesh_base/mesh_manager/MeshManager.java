package com.example.mesh_base.mesh_manager;

import android.util.Log;

import androidx.activity.ComponentActivity;

import com.example.mesh_base.ble.BLEHandler;
import com.example.mesh_base.ble.BLEPermissions;
import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.router.Router;

import java.util.ArrayList;
import java.util.UUID;

interface MeshBaseListener {
  void onData(byte[] data);

  void onStatusChange(Status status);

  //TODO: add medium type in devices
  void onNeighborsChanged();
}

class Status {
  Property ble;
  Property wifi;

  Status(Property ble, Property wifi) {
    this.ble = ble;
    this.wifi = wifi;
  }

  static class Property {
    boolean isSupported;
    boolean isOn;
    boolean isAllowed;
  }

}

public class MeshManager {


  private final ArrayList<ConnectionHandler> helpers = new ArrayList<>();
  //TODO: consider accepting uuid from application vs generating one and storing it
  private final UUID id = UUID.randomUUID();
  private final Router router;
  //TODO: discuss directly using permission classes vs using them behind connection handlers
  private final BLEPermissions blePermissions;
  private final BLEHandler bleHelper;
  String TAG = "my_meshManager";
  private MeshBaseListener listener;

  public MeshManager(ComponentActivity context) {
    bleHelper = new BLEHandler(
            (device) -> {
              Log.d(TAG, "neighbor connected");
            },
            (device) -> {
              Log.d(TAG, "neighbor disconnected");
            },
            (device) -> {
              Log.d(TAG, "neighbor discovered");
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
        } catch (Exception e) {
          //TODO: send error to user using a callback
        }
      }

      @Override
      public void onDisabled() {
        bleHelper.stop();
      }
    });

    router = new Router(helpers, id);
  }

  void on() {
    //TODO: if having a list of permission classes is possible, loop through them and call .enable()
    //calling .enable() even if already enabled will call the onEnabled listener, which will call start()
    blePermissions.enable();
  }

  void off() {
    for (ConnectionHandler helper : helpers) {
      helper.stop();
    }
  }

  void setAllowedMedium() {
  }

  void getNeighbors() {
  }

  void send(byte[] data, String address) {
  }

  void send(String data, String address) {
  }

}
