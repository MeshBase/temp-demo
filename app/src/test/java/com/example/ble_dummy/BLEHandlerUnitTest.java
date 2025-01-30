package com.example.ble_dummy;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import java.util.UUID;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import android.util.Log;


public class BLEHandlerUnitTest {
    private BLECentralI bleCentral;
    private BLEPeripheralI blePeripheral;
    private BLEHandler bleHandler;

    private NeighborConnectedListener neighborConnectedListener;
    private NeighborDisconnectedListener neighborDisconnectedListener;
    private NeighborDiscoveredListener neighborDiscoveredListener;
    private DisconnectedListener disconnectedListener;
    private DataListener dataListener;
    private NearbyDevicesListener nearbyDevicesListener;

    @Before
    public void testLogging() {
        Log logMock = mock(Log.class);
        when(logMock.d("tag", "message")).thenReturn(0); // Mock the behavior if needed

        // Your test code that uses Log.d
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        bleCentral = mock(BLECentralI.class);
        blePeripheral = mock(BLEPeripheralI.class);
        neighborConnectedListener = mock(NeighborConnectedListener.class);
        neighborDisconnectedListener = mock(NeighborDisconnectedListener.class);
        neighborDiscoveredListener = mock(NeighborDiscoveredListener.class);
        disconnectedListener = mock(DisconnectedListener.class);
        dataListener = mock(DataListener.class);
        nearbyDevicesListener = mock(NearbyDevicesListener.class);

        bleHandler = new BLEHandler(
                bleCentral,
                blePeripheral,
                neighborConnectedListener,
                neighborDisconnectedListener,
                neighborDiscoveredListener,
                disconnectedListener,
                dataListener,
                nearbyDevicesListener
        );
    }

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testNoErrorAtStart() {
        try {
            bleHandler.start();
            verify(bleCentral).startScan();
            verify(blePeripheral).startServer();
        } catch(Exception e) {
            fail("Should not have thrown any exception"+e);
        }
    }
//
//    @Test
//    public void testNearbyDevicesFromCentral() {
//        BLEDevice device = new BLEDevice(UUID.randomUUID(), "Device1", "address1");
//        ArgumentCaptor<BLEConnectListener> connectCaptor = ArgumentCaptor.forClass(BLEConnectListener.class);
//
//        connectCaptor.getValue().onEvent(device);
//        verify(neighborConnectedListener).onEvent(device);
//        verify(nearbyDevicesListener).onEvent(any());
//    }
//
//    @Test
//    public void testNearbyDevicesFromPeripheral() {
//        BLEDevice device = new BLEDevice(UUID.randomUUID(), "Device2", "address2");
//        ArgumentCaptor<BLEConnectListener> connectCaptor = ArgumentCaptor.forClass(BLEConnectListener.class);
//
//        bleHandler.start();
//        verify(blePeripheral).setListeners(connectCaptor.capture(), any(), any());
//
//        connectCaptor.getValue().onEvent(device);
//        verify(neighborConnectedListener).onEvent(device);
//        verify(nearbyDevicesListener).onEvent(any());
//    }
//
//    @Test
//    public void testSameDeviceConnectingFromCentralAndPeripheral() {
//        BLEDevice device = new BLEDevice(UUID.randomUUID(), "Device3", "address3");
//        ArgumentCaptor<BLEConnectListener> centralCaptor = ArgumentCaptor.forClass(BLEConnectListener.class);
//        ArgumentCaptor<BLEConnectListener> peripheralCaptor = ArgumentCaptor.forClass(BLEConnectListener.class);
//
//        bleHandler.start();
//        verify(bleCentral).setListeners(centralCaptor.capture(), any(), any());
//        verify(blePeripheral).setListeners(peripheralCaptor.capture(), any(), any());
//
//        centralCaptor.getValue().onEvent(device);
//        peripheralCaptor.getValue().onEvent(device);
//
//        verify(neighborConnectedListener, times(1)).onEvent(device);
//    }
//
//    @Test
//    public void testSendingAsCentral() {
//        BLEDevice device = new BLEDevice(UUID.randomUUID(), "Device4", "address4");
//        byte[] data = {1, 2, 3};
//
//        bleHandler.start();
//        bleHandler.send(data, device);
//        verify(bleCentral).send(data, device.getAddress());
//    }
//
//    @Test
//    public void testSendingAsPeripheral() {
//        BLEDevice device = new BLEDevice(UUID.randomUUID(), "Device5", "address5");
//        byte[] data = {4, 5, 6};
//
//        bleHandler.start();
//        bleHandler.send(data, device);
//        verify(blePeripheral).send(data, device.getAddress());
//    }
//
//    @Test
//    public void testPeripheralDisconnecting() {
//        BLEDevice device = new BLEDevice(UUID.randomUUID(), "Device6", "address6");
//        ArgumentCaptor<BLEDisconnectListener> disconnectCaptor = ArgumentCaptor.forClass(BLEDisconnectListener.class);
//
//        bleHandler.start();
//        verify(bleCentral).setListeners(any(), disconnectCaptor.capture(), any());
//
//        disconnectCaptor.getValue().onEvent(device.getAddress());
//        verify(neighborDisconnectedListener).onEvent(device);
//    }
//
//    @Test
//    public void testCentralDisconnecting() {
//        BLEDevice device = new BLEDevice(UUID.randomUUID(), "Device7", "address7");
//        ArgumentCaptor<BLEDisconnectListener> disconnectCaptor = ArgumentCaptor.forClass(BLEDisconnectListener.class);
//
//        bleHandler.start();
//        verify(blePeripheral).setListeners(any(), disconnectCaptor.capture(), any());
//
//        disconnectCaptor.getValue().onEvent(device.getAddress());
//        verify(neighborDisconnectedListener).onEvent(device);
//    }
//
//    @Test
//    public void testStop() {
//        bleHandler.start();
//        bleHandler.stop();
//
//        verify(bleCentral).stop();
//        verify(blePeripheral).stopServer();
//        assertEquals(0, bleHandler.getNeighbourDevices().size());
//    }
//
//    @Test
//    public void testStartThenTestListeningToNeighbor() {
//        bleHandler.start();
//        verify(bleCentral).setListeners(any(), any(), any());
//        verify(blePeripheral).setListeners(any(), any(), any());
//    }
//

}
