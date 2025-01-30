package com.example.ble_dummy;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import android.util.Log;


public class BLEHandlerUnitTest {
    private static MockedStatic<Log> logMock;

    @BeforeClass
    public static void setup() {
        logMock = Mockito.mockStatic(Log.class);
        logMock.when(() -> Log.d(anyString(), anyString())).thenReturn(0);
        logMock.when(() -> Log.e(anyString(), anyString())).thenReturn(0);
        logMock.when(() -> Log.i(anyString(), anyString())).thenReturn(0);
    }

    private BLECentralI bleCentral;
    private BLEPeripheralI blePeripheral;
    private BLEHandler bleHandler;

    private NeighborConnectedListener neighborConnectedListener;
    private NeighborDisconnectedListener neighborDisconnectedListener;
    private NeighborDiscoveredListener neighborDiscoveredListener;
    private DisconnectedListener disconnectedListener;
    private DataListener dataListener;
    private NearbyDevicesListener nearbyDevicesListener;
    private ArgumentCaptor<BLEConnectListener> centralConnectCaptor;
    private ArgumentCaptor<BLEConnectListener> peripheralConnectCaptor;
    private ArgumentCaptor<BLEDisconnectListener> centralDisconnectCaptor;
    private ArgumentCaptor<BLEDisconnectListener> peripheralDisconnectCaptor;
    private ArgumentCaptor<BLEDataListener> centralDataCaptor;
    private ArgumentCaptor<BLEDataListener> peripheralDataCaptor;


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

        centralConnectCaptor = ArgumentCaptor.forClass(BLEConnectListener.class);
        peripheralConnectCaptor = ArgumentCaptor.forClass(BLEConnectListener.class);
        centralDisconnectCaptor = ArgumentCaptor.forClass(BLEDisconnectListener.class);
        peripheralDisconnectCaptor = ArgumentCaptor.forClass(BLEDisconnectListener.class);
        centralDataCaptor = ArgumentCaptor.forClass(BLEDataListener.class);
        peripheralDataCaptor = ArgumentCaptor.forClass(BLEDataListener.class);

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
    public void testBLEHandlerCycle() throws Exception {
        testStartsWithoutError();
        testGainNeighbors();
        testGainingSameNeighbor();
        testSendingData();
        testDataRecieved();
        testDisconnecting();
        testStopping();

        //cycle 2
        reset(bleCentral, blePeripheral, neighborConnectedListener, neighborDisconnectedListener, neighborDiscoveredListener, disconnectedListener, dataListener, nearbyDevicesListener);
        testStartsWithoutError();
        testGainNeighbors();
        testGainingSameNeighbor();
        testSendingData();
        testDataRecieved();
        testDisconnecting();
        testStopping();
    }

    private void testStartsWithoutError() {
        try {

            bleHandler.start();

            verify(bleCentral).setListeners(centralConnectCaptor.capture(), centralDisconnectCaptor.capture(), centralDataCaptor.capture());
            verify(blePeripheral).setListeners(peripheralConnectCaptor.capture(), peripheralDisconnectCaptor.capture(), peripheralDataCaptor.capture());

            verify(bleCentral).startScan();
            verify(blePeripheral).startServer();


        } catch (Exception e) {
            fail("Should not have thrown any exception" + e);
        }
    }

    private void testGainNeighbors() {
        BLEDevice device1 = new BLEDevice(UUID.randomUUID(), "Device1", "address1");
        BLEDevice device2 = new BLEDevice(UUID.randomUUID(), "Device2", "address2");
        centralConnectCaptor.getValue().onEvent(device1);
        peripheralConnectCaptor.getValue().onEvent(device2);

        //check that the devices are in the list
        assertTrue(bleHandler.getNeighbourDevices().contains(device1));
        assertTrue(bleHandler.getNeighbourDevices().contains(device2));
    }

    private void testGainingSameNeighbor() {
        BLEDevice device1 = new BLEDevice(UUID.randomUUID(), "Device1", "address1");
        BLEDevice device2 = new BLEDevice(UUID.randomUUID(), "Device2", "address2");
        centralConnectCaptor.getValue().onEvent(device2);
        peripheralConnectCaptor.getValue().onEvent(device1);

        //Num neighbors shouldn't change
        assertEquals(bleHandler.getNeighbourDevices().size(), 2);
    }

    private void testSendingData() throws Exception {
        ArrayList<Device> devices = bleHandler.getNeighbourDevices();
        byte[] data = {1, 2, 3};
        for (Device device : devices) {
            try {
                bleHandler.send(data, device);
            } catch (Exception e) {
                fail("Should not have thrown any exception" + e);
            }
        }
        verify(bleCentral, times(1)).send(eq(data), any());
        verify(blePeripheral, times(1)).send(eq(data), any());
    }

    private void testDataRecieved() {
        byte[] data1 = new byte[]{1};
        byte[] data2 = new byte[]{2};
        centralDataCaptor.getValue().onEvent(data1, "address1");
        verify(dataListener).onEvent(eq(data1), any());
        peripheralDataCaptor.getValue().onEvent(data2, "address2");
        verify(dataListener).onEvent(eq(data2), any());
    }

    private void testDisconnecting() {
        centralDisconnectCaptor.getValue().onEvent("address1");
        assertEquals(bleHandler.getNeighbourDevices().size(), 1);
        peripheralDisconnectCaptor.getValue().onEvent("address2");
        assertEquals(bleHandler.getNeighbourDevices().size(), 0);
    }

    private void testStopping() {
        //no neighbors
        centralConnectCaptor.getValue().onEvent(new BLEDevice(UUID.randomUUID(), "Device1", "address1"));
        bleHandler.stop();
        assertEquals(bleHandler.getNeighbourDevices().size(), 0);

        //send throws exception
        try {
            bleHandler.send(new byte[]{1});
            fail("Should have thrown an exception");
        } catch (SendError ignored) {
        }
    }

}
