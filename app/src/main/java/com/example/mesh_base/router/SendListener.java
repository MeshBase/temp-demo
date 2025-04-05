package com.example.mesh_base.router;

import com.example.mesh_base.global_interfaces.SendError;

public abstract class SendListener<T extends MeshSerializer<T>> {

  abstract void onError(SendError error);

  abstract void onAck();

  abstract void onResponse(MeshProtocol<T> protocol);
}
