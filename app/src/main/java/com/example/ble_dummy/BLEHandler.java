package com.example.ble_dummy;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import kotlin.jvm.Synchronized;

public class BLEHandler extends ConnectionHandler {

    BLEHandler(NeighborConnectedListener neighborConnectedListener, NeighborDisconnectedListener neighborDisconnectedListener, NeighborDiscoveredListener neighborDiscoveredListener, DisconnectedListener disconnectedListener, DataListener dataListener, NearbyDevicesListener nearbyDevicesListener, Context context) {
        super(neighborConnectedListener, neighborDisconnectedListener, neighborDiscoveredListener, disconnectedListener, dataListener, nearbyDevicesListener);
        this.context = context;

        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        scanner = btManager.getAdapter().getBluetoothLeScanner();
    }

    /////central fields
    static final String CTRL = " central: ";
    private final Set<String> connectingPeripherals = new HashSet<>();
    private final Map<UUID, BluetoothGatt> connectedPeripherals = new HashMap<>();
    private boolean centralIsOn = false;
    BluetoothLeScanner scanner;
    private  boolean isScanning = false;
    public final Map<String, Integer> peripheralRetryCount = new HashMap<>();
    private static final int MAX_PERIPHERAL_RETRIES = 5;

    /////peripheral fields
    static final String PRFL = "peripheral:";
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic messageCharacteristic;
    private boolean peripheralIsOn = false;
    private final HashMap<String, BluetoothDevice> connectingCentrals = new HashMap<>();
    private final HashMap<UUID, BluetoothDevice> connectedCentrals = new HashMap<>();
    private BluetoothLeAdvertiser advertiser;

    /////common fields and methods
    private final String TAG = "my_bleHandler";
    private final Context context;
    private final ConcurrentLinkedQueue<BLETask> queue = new ConcurrentLinkedQueue<>();
    private BLETask pendingTask = null;
    private void addToQueue (BLETask task){
        synchronized (queue){
            queue.add(task);
            if (pendingTask == null){
                doNextTask();
            }
        }
    }
    private void doNextTask(){
        synchronized (queue){
            if (pendingTask != null){
                Log.w(TAG, "can't do task until pending task:"+pendingTask.asString()+"is finished");
                return;
            }

            BLETask task = queue.poll();
            if (task == null){
                Log.w(TAG, "queue is empty, nothing to do");
                return;
            }

            if (task instanceof Scan) doScan();

        }
    }

    /////private central methods (follows sequence of operations as much as possible)
    private void doScan(){
        if (!centralIsOn || isScanning){
            Log.d(TAG,CTRL+"ignoring scan task due to centralIsOff: "+!centralIsOn + " scanning already: "+isScanning);
            return;
        }
        isScanning = true;
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(CommonConstants.SERVICE_UUID)).build();

    }

    /////public methods
    @Override
    public ArrayList<Device> getNeighbourDevices() {
        return null;
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() {

    }

    @Override
    public ArrayList<Device> getNearbyDevices() {
        return null;
    }

    @Override
    public void send(byte[] data) throws SendError {

    }

    @Override
    public void send(byte[] data, Device neighbor) throws SendError {

    }


}
