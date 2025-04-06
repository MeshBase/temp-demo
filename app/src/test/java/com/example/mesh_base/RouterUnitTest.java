package com.example.mesh_base;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;
import com.example.mesh_base.router.ConcreteMeshProtocol;
import com.example.mesh_base.router.MeshProtocol;
import com.example.mesh_base.router.Router;
import com.example.mesh_base.router.SendListener;
import com.example.mesh_base.router.SendMessageBody;

import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

public class RouterUnitTest {

  @Test
  public void testSendCall_isOnAndNeighbors_callsSend() throws SendError {
    ConnectionHandler handler = mock(ConnectionHandler.class);
    ArrayList<Device> devices = createDevices(5);

    when(handler.isOn()).thenReturn(true);
    when(handler.getNeighbourDevices()).thenReturn(devices);

    UUID id = UUID.randomUUID();
    ArrayList<ConnectionHandler> handlers = new ArrayList<>();
    handlers.add(handler);
    Router router = new Router(handlers, id);
    MeshProtocol<SendMessageBody> protocol = new ConcreteMeshProtocol<>(1, -1, -1, id, new SendMessageBody(4, false, devices.get(0).uuid, "hello world"));

    //Perfect path
    router.sendData(protocol, mock(SendListener.class));
    verify(handler).send(any(byte[].class));

    //errors when no neighbor
    when(handler.getNeighbourDevices()).thenReturn(new ArrayList<>());
    SendListener listener = mock(SendListener.class);
    router.sendData(protocol, listener);
    verify(listener).onError(any(SendError.class));


    //errors when not on
    when(handler.getNeighbourDevices()).thenReturn(createDevices(3));
    when(handler.isOn()).thenReturn(false);
    SendListener listener2 = mock(SendListener.class);
    router.sendData(protocol, listener2);
    verify(listener2).onError(any(SendError.class));

  }


  private ArrayList<Device> createDevices(int count) {
    ArrayList<Device> devices = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Device device = new Device(UUID.randomUUID(), "device" + i) {
      };
      devices.add(device);
    }

    return devices;
  }


}
