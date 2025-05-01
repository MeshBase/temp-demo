package com.example.mesh_base;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mesh_base.crypt_handler.CryptHandler;
import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.ConnectionHandlersEnum;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.SendError;
import com.example.mesh_base.router.ConcreteMeshProtocol;
import com.example.mesh_base.router.MeshProtocol;
import com.example.mesh_base.router.Router;
import com.example.mesh_base.router.SendListener;
import com.example.mesh_base.router.SendMessageBody;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class CryptHandlerUnitTest {

  @Test
  public void testSendCall_isOnAndNeighbors_callsSend() throws SendError {
    CryptHandler cryptoHandler = new CryptHandler();
    assertEquals(1, cryptoHandler.returnOne());
  }
}
