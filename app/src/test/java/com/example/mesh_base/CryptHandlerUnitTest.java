package com.example.mesh_base;

import static org.junit.Assert.assertEquals;

import com.example.mesh_base.crypt_handler.CryptHandler;
import org.junit.Test;

public class CryptHandlerUnitTest {

  @Test
  //Click the button to the left of this method to run
  public void testDummyMethod() {
    CryptHandler cryptoHandler = new CryptHandler();
    assertEquals(1, cryptoHandler.returnOne());
  }
}
