package com.example.mesh_base.router;

import android.util.Log;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.DataListener;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;

public class Router {
  String TAG = "my_router";
  UUID id;
  ArrayList<ConnectionHandler> connectionHandlers;
  HashSet<String> routedSet = new HashSet<>();
  DataListener onReceivedData = (data, neighbor) -> Log.d(TAG, "Received data from " + neighbor.name);

  public Router(ArrayList<ConnectionHandler> connectionHandlers, UUID id) {
    this.connectionHandlers = connectionHandlers;
    this.id = id;

    for (ConnectionHandler handler : connectionHandlers) {
      handler.setDataListener((data, neighbor) -> handleOnData(neighbor, data));
    }
  }

  //Todo: rethink argument needed for send data byte[] vs a Protocol vs only a Body
  public void sendData(byte[] data, UUID address) {
    //Todo: do intelligent routing
    MeshProtocol<SendMessageBody> protocol = new ConcreteMeshProtocol<>(
            1,
            4,
            new Random().nextInt(),
            id,
            new SendMessageBody(4,
                    false,
                    address,
                    new String(data, StandardCharsets.UTF_8)
            )
    );
    setRouted(protocol.messageId, protocol.sender);
    floodData(protocol.encode());
  }

  public void floodData(byte[] data) {
    for (ConnectionHandler handler : connectionHandlers) {
      try {
        if (handler.isOn()) {
          handler.send(data);
        }
      } catch (SendError e) {
        //TODO: reconsider the need for errors vs raising them only on timeout
        throw new RuntimeException(e);
      }
    }
  }

  public void setOnReceivedData(DataListener onReceivedData) {
    this.onReceivedData = onReceivedData;
  }

  private boolean hasRoutedDataBefore(int messageId, UUID address) {
    String key = messageId + "." + address.toString();
    return routedSet.contains(key);
  }

  private void setRouted(int messageId, UUID address) {
    String key = messageId + "." + address.toString();
    routedSet.add(key);
  }

  private void handleOnData(Device neighbor, byte[] byteArray) {
    //TODO: clarify the way to know the body type before decoding the body. Assuming send message for now
    MeshProtocol<SendMessageBody> protocol = MeshProtocol.decode(byteArray, SendMessageBody::decode);
    if (protocol.body.getDestination().equals(id)) {
      //TODO: figure out giving byte[] vs Protocol to the onReceivedData listener
      //TODO: prevent user from receiving the message twice
      onReceivedData.onEvent(byteArray, neighbor);
    } else if (hasRoutedDataBefore(protocol.messageId, protocol.sender)) {
      Log.d(TAG, "already routed data. skipping. messageId=" + protocol.messageId + " sender=" + protocol.sender);
    } else if (protocol.remainingHops <= 0) {
      Log.d(TAG, "finished remaining hops, cant route anymore. messageId=" + protocol.messageId + " sender=" + protocol.sender);
    } else {
      setRouted(protocol.messageId, protocol.sender);
      protocol.remainingHops -= 1;
      Log.d(TAG, "relaying data " + protocol.messageId + "sender=" + protocol.sender + " remainingHops=" + protocol.remainingHops);
      floodData(protocol.encode());
    }
  }
}
