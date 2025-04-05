package com.example.mesh_base.router;

import com.example.mesh_base.global_interfaces.SendError;

//TODO: implement timer so that onError is called when timeout is reached
public abstract class SendListener<T extends MeshSerializer<T>> {

  abstract public void onError(SendError error);

  abstract public void onAck();

  abstract public void onResponse(MeshProtocol<T> protocol);
}
