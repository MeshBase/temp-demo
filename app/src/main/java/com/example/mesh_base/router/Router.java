package com.example.mesh_base.router;

import android.util.Log;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.DataListener;
import com.example.mesh_base.global_interfaces.Device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;


public class Router {
  String TAG = "my_router";
  UUID id;
  ArrayList<ConnectionHandler> connectionHandlers;
  HashSet<String> routedSet = new HashSet<>();
  DataListener onReceivedData = (data, neighbor) -> Log.d(TAG, "Received data from " + neighbor.name);
  //TODO: make router call onError, onAck, and onResponse when AckProtocol and getting Headers from byte[] is implemented
  HashMap<Integer, SendListener<?>> listeners = new HashMap<>();

  public Router(ArrayList<ConnectionHandler> connectionHandlers, UUID id) {
    this.connectionHandlers = connectionHandlers;
    this.id = id;

    for (ConnectionHandler handler : connectionHandlers) {
      handler.setDataListener((data, neighbor) -> handleOnData(neighbor, data));
    }
  }

  public void sendData(MeshProtocol<?> protocol, SendListener<?> listener) {
    //override since router should be concerned about the remaining hops and keeping track of message Ids
    protocol.messageId = new Random().nextInt();
    protocol.remainingHops = 4;
    setRouted(protocol.messageId, protocol.sender);
    listeners.put(protocol.messageId, listener);
    floodData(protocol.encode());
  }

  //keep private until it's need is justified
  private void floodData(byte[] data) {
    for (ConnectionHandler handler : connectionHandlers) {
      try {
        if (handler.isOn() && !handler.getNeighbourDevices().isEmpty()) {
          handler.send(data);
        }
      } catch (com.example.mesh_base.global_interfaces.SendError e) {
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
      //TODO: prevent user from receiving the message twice, but keep now for testing purposes
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
