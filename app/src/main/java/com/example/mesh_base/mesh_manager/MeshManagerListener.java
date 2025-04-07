package com.example.mesh_base.mesh_manager;

public interface MeshManagerListener {
  void onData(byte[] data);

  void onStatusChange(Status status);

  //TODO: add medium type in devices
  void onNeighborsChanged();
}
