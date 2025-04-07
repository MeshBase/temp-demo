package com.example.mesh_base.mesh_manager;

import com.example.mesh_base.global_interfaces.Device;

import java.util.ArrayList;

public interface MeshManagerListener {
  void onData(byte[] data, Device device);

  void onStatusChange(Status status);

  //TODO: add medium type in devices
  void onNeighborsChanged(ArrayList<Device> devices);

  void onConnected(Device device);

  void onDisconnected(Device device);

  void onDiscovered(Device device);
}
