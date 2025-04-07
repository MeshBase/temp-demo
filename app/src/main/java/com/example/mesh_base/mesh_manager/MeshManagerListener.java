package com.example.mesh_base.mesh_manager;

import android.util.Log;

import com.example.mesh_base.global_interfaces.Device;

import java.util.ArrayList;

public abstract class MeshManagerListener {
  String TAG = "my_meshManager";

  static MeshManagerListener createEmpty() {
    return new MeshManagerListener() {
      @Override
      public void onData(byte[] data, Device device) {
        Log.d(TAG, "[from empty listener] received data");
      }

      @Override
      public void onStatusChange(Status status) {
        Log.d(TAG, "[from empty listener] status changed");
      }

      @Override
      public void onNeighborsChanged(ArrayList<Device> devices) {
        Log.d(TAG, "[from empty listener] neighbors changed");
      }

      @Override
      public void onConnected(Device device) {
        Log.d(TAG, "[from empty listener] device connected");
      }

      @Override
      public void onDisconnected(Device device) {
        Log.d(TAG, "[from empty listener] device disconnected");
      }

      @Override
      public void onDiscovered(Device device) {
        Log.d(TAG, "[from empty listener] device discovered");
      }
    };

  }

  abstract public void onData(byte[] data, Device device);

  abstract public void onStatusChange(Status status);

  //TODO: add medium type in devices
  abstract public void onNeighborsChanged(ArrayList<Device> devices);

  abstract public void onConnected(Device device);

  abstract public void onDisconnected(Device device);

  abstract public void onDiscovered(Device device);
}
