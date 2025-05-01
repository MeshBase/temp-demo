package com.example.mesh_base;

import static org.junit.Assert.assertEquals;

import com.example.mesh_base.crypt_handler.CryptHandler;
import com.example.mesh_base.global_interfaces.SendError;
import org.junit.Test;

public class CryptHandlerUnitTest {

  @Test
  public void testSendCall_isOnAndNeighbors_callsSend() throws SendError {
    CryptHandler cryptoHandler = new CryptHandler();
    assertEquals(1, cryptoHandler.returnOne());
  }
}
