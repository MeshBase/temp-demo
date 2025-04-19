package com.example.mesh_base.router;

import android.util.Log;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.ConnectionHandlerListener;
import com.example.mesh_base.global_interfaces.ConnectionHandlersEnum;
import com.example.mesh_base.global_interfaces.DataListener;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;


public class Router {
    String TAG = "my_router";
    UUID id;
    HashMap<ConnectionHandlersEnum, ConnectionHandler> connectionHandlers;
    HashSet<String> routedSet = new HashSet<>();
    DataListener onReceivedData = (data, neighbor) -> Log.d(TAG, "Received data from " + neighbor.name);
    HashMap<Integer, SendListener> listeners = new HashMap<>();

    public Router(HashMap<ConnectionHandlersEnum, ConnectionHandler> connectionHandlers, UUID id) {

        this.connectionHandlers = connectionHandlers;
        this.id = id;

        for (ConnectionHandler handler : connectionHandlers.values()) {
            handler.subscribe(
                    new ConnectionHandlerListener() {
                        @Override
                        public void onDataReceived(Device device, byte[] data) {
                            handleOnData(device, data);
                        }
                    }
            );
        }
    }

    public void sendData(MeshProtocol<?> protocol, SendListener listener) {
        //override since router should be concerned about the remaining hops and keeping track of message Ids
        protocol.messageId = new Random().nextInt();
        protocol.remainingHops = 4;
        setRouted(protocol.messageId, protocol.sender);
        listeners.put(protocol.messageId, listener);
        try {
            floodData(protocol.encode());
        } catch (SendError e) {
            handleOnError(e, protocol.messageId);
        }
    }

    //keep private until it's need is justified
    private void floodData(byte[] data) throws SendError {
        boolean hasAttemptedSending = false;
        for (ConnectionHandler handler : connectionHandlers.values()) {
            try {
                if (handler.isOn() && !handler.getNeighbourDevices().isEmpty()) {
                    handler.send(data);
                    hasAttemptedSending = true;
                }
            } catch (SendError e) {
                //Silent error in case other neighbors have successfully sent
                Log.e(TAG, "Error sending data: " + e.getMessage());
            }
        }

        if (!hasAttemptedSending) {
            //TODO: Create a dedicated error type if necessary
            throw new SendError("Could not send to any neighbor");
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
            //TODO: if the data was a response to a sent message, use handleOnResponse() instead of onReceivedData() - when Header-only decoding is implemented
            //TODO: if the data was an ack, use handleOnAck() instead of onReceivedData() - when Header-only decoding is implemented
            onReceivedData.onEvent(byteArray, neighbor);
        } else if (hasRoutedDataBefore(protocol.messageId, protocol.sender)) {
            Log.d(TAG, "already routed data. skipping. messageId=" + protocol.messageId + " sender=" + protocol.sender);
        } else if (protocol.remainingHops <= 0) {
            Log.d(TAG, "finished remaining hops, cant route anymore. messageId=" + protocol.messageId + " sender=" + protocol.sender);
        } else {
            setRouted(protocol.messageId, protocol.sender);
            protocol.remainingHops -= 1;
            Log.d(TAG, "relaying data " + protocol.messageId + "sender=" + protocol.sender + " remainingHops=" + protocol.remainingHops);
            try {
                floodData(protocol.encode());
            } catch (SendError e) {
                //Silent error because there is no listener for relaying data
                Log.e(TAG, "Error relaying data: " + e.getMessage());
            }
        }
    }

    private void handleOnError(SendError error, int messageId) {
        SendListener listener = getListenerOrError(messageId);
        listener.onError(error);
        listeners.remove(messageId);
    }

    //TODO: throw error for unexpected response type
    private void handleOnResponse(MeshProtocol<?> response, int messageId) {
        SendListener listener = getListenerOrError(messageId);
        listener.onResponse(response);
        listeners.remove(messageId);
    }

    private void handleOnAck(int messageId) {
        //TODO: think on how to remove listener if no other response is expected
        SendListener listener = getListenerOrError(messageId);
        listener.onAck();
    }

    private SendListener getListenerOrError(int messageId) {
        SendListener listener = listeners.get(messageId);
        if (listener == null)
            throw new RuntimeException("Could not find listener for messageId" + messageId);

        return listener;
    }
}
