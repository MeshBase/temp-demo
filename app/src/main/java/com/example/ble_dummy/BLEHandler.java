package com.example.ble_dummy;


import static com.example.ble_dummy.CommonConstants.ID_UUID;
import static com.example.ble_dummy.CommonConstants.MESSAGE_UUID;
import static com.example.ble_dummy.CommonConstants.SERVICE_UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


class CommonConstants {
    public static final UUID SERVICE_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb");
    public static final UUID MESSAGE_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb");
    public static final UUID ID_UUID = UUID.fromString("b000000f-0000-1000-8000-00805f9b34fb");
    public  static  final  UUID NOTIF_DESCRIPTOR_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb" );
}

public class BLEHandler extends ConnectionHandler {

    //TODO: use BLE Permissions to notify connected and disconnected
    BLEHandler(NeighborConnectedListener neighborConnectedListener, NeighborDisconnectedListener neighborDisconnectedListener, NeighborDiscoveredListener neighborDiscoveredListener, DisconnectedListener disconnectedListener, DataListener dataListener, NearbyDevicesListener nearbyDevicesListener, Context context, UUID id) {
        super(neighborConnectedListener, neighborDisconnectedListener, neighborDiscoveredListener, disconnectedListener, dataListener, nearbyDevicesListener);
        this.context = context;
        this.id = id;

        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        scanner = btManager.getAdapter().getBluetoothLeScanner();
    }

    /////central fields
    static final String CTRL = " central: ";
    private final HashMap<String, BluetoothDevice> connectingPeripherals = new HashMap<>();
    private final Map<UUID, BluetoothGatt> connectedPeripherals = new HashMap<>();
    private boolean centralIsOn = false;
    BluetoothLeScanner scanner;
    private  boolean isScanning = false;
    public final Map<String, Integer> peripheralRetryCount = new HashMap<>();
    private static final int MAX_PERIPHERAL_RETRIES = 5;
    private static final long SCAN_TIME_GAP = 6_500; //6.5 seconds
    private static final long MAX_SCAN_DURATION = 3_000;//3 seoncs
    private long lastScanTime = 0;

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
    private final UUID id;
    private final Context context;
    private final ConcurrentLinkedQueue<BLETask> queue = new ConcurrentLinkedQueue<>();
    private BLETask pendingTask = null;
    private final HashMap<UUID, BLEDevice> twoWayConnectedDevices = new HashMap<>();
    private void addToQueue (BLETask task){
        synchronized (queue){
            queue.add(task);

            String extraTag = "--";
            if (task instanceof PeripheralTask) extraTag = PRFL;
            else if (task instanceof CentralTask) extraTag = CTRL;

            Log.d(TAG+extraTag,"added task "+task.asString()+" .To a queue of length:"+queue.size());
            if (pendingTask == null){
                startNextTask();
            }
        }
    }
    private void startNextTask(){
        synchronized (queue){
            if (pendingTask != null){
                String oldTaskTag = (pendingTask instanceof PeripheralTask)? PRFL: CTRL;
                Log.w(TAG+oldTaskTag, "Can't do task until pending task: "+pendingTask.asString()+" is finished");
                return;
            }

            pendingTask = queue.poll();

            String taskTag = (pendingTask instanceof PeripheralTask)? PRFL: CTRL;
            if (pendingTask == null){
                startScanWhenEmptyQueue();
            }else if (pendingTask instanceof Scan){
                startScan((Scan) pendingTask);
            }else if (pendingTask instanceof ConnectToPeripheral){
                startConnectToPeripheral((ConnectToPeripheral) pendingTask);
            }else if (pendingTask instanceof DiscoverServices){
                startDiscoverServices((DiscoverServices) pendingTask);
            }else if (pendingTask instanceof ReadCharacteristic){
                startReadingCharacteristic((ReadCharacteristic) pendingTask);
            } else if (pendingTask instanceof WriteCharacteristic){
                startWritingCharacteristic((WriteCharacteristic) pendingTask);
            }else if (pendingTask instanceof DisconnectPeripheral){
                startDisconnectPeripheral((DisconnectPeripheral) pendingTask);
            }else if (pendingTask instanceof StartGattServer){
                startGattServer((StartGattServer) pendingTask);
            }else if (pendingTask instanceof  Advertise){
                startAdvertising((Advertise) pendingTask);
            }else if (pendingTask instanceof SendResponse){
                startSendResponse((SendResponse) pendingTask);
            } else if (pendingTask instanceof ConnectCentral){
                startConnectCentral((ConnectCentral) pendingTask);
            }else if (pendingTask instanceof DisconnectCentral){
                startDisconnectCentral((DisconnectCentral) pendingTask);
            }else if (pendingTask instanceof CloseGatt){
                startClosingGatt((CloseGatt) pendingTask);
            } else{
                Log.e(TAG+taskTag ,"unknown task type"+pendingTask.asString());
                return;
            }
            Log.d(TAG+taskTag,"executing "+pendingTask.asString());

            if (pendingTask.expires){
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.w(TAG+taskTag, pendingTask.asString()+" Timed out after"+pendingTask.expireMilli+"ms. Moving on to next task");
                    pendingTask = null;
                    startNextTask();
                }, pendingTask.expireMilli);
            }
        }
    }

    private void taskEnded(){
        synchronized (queue){
            String taskTag = (pendingTask instanceof PeripheralTask)? PRFL: CTRL;
            Log.d(TAG+taskTag, "task of "+pendingTask.asString()+" has ended. (successful or not)");
            pendingTask = null;
            startNextTask();
        }
    }

    ///// central methods (follows sequence of operations as much as possible)

    @SuppressLint("MissingPermission")
    public void startCentral() {
        if (centralIsOn) {
            Log.d(TAG+CTRL, "already on");
            return;
        }
        centralIsOn = true;
        if (!isScanning){
            //1 device to not make users wait too much for the first connection
            addToQueue(new Scan(1));
        }
    }

    private void startScanWhenEmptyQueue(){
        long timeGap = System.currentTimeMillis() - lastScanTime;

        if (timeGap < SCAN_TIME_GAP){
            Log.d(TAG+CTRL, "emptyQueue: will add scan task after"+ timeGap +"milliseconds");
            long remainingTime = SCAN_TIME_GAP - timeGap;
            taskEnded();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (queue.isEmpty()) return;
                Log.d(TAG+CTRL, "emptyQueue: added scan task ( after "+remainingTime+" milliseconds)");
                addToQueue(new Scan(3));
            }, remainingTime);

            return;
        }

        Log.d(TAG+CTRL, "emptyQueue: adding scan task ");
        addToQueue(new Scan(3));
    }
    @SuppressLint("MissingPermission")
    private void startScan(Scan task){
        if (!centralIsOn || isScanning){
            Log.d(TAG+CTRL, "ignoring scan task due to centralIsOff: "+!centralIsOn + " scanning already: "+isScanning);
            taskEnded();
            return;
        }

        long timeGap = System.currentTimeMillis() - lastScanTime;
        if (timeGap < SCAN_TIME_GAP){
            Log.d(TAG+CTRL, "scanning too early for the 5 scans per 30 seconds rule. ignoring task");
            taskEnded();
            return;
        }

        isScanning = true;
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(CommonConstants.SERVICE_UUID)).build();
        scanner.startScan(Collections.singletonList(filter), new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), scanCallback);
        lastScanTime = System.currentTimeMillis();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (pendingTask == task){
                Log.w(TAG+CTRL, "timing out scan after "+MAX_SCAN_DURATION+" milliseconds");
                taskEnded();
            }
        }, MAX_SCAN_DURATION);
    }
    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            boolean shouldContinue = pendingTask instanceof Scan;
            if (!shouldContinue){
                Log.w(TAG+CTRL, "current task is not scan, skipping");
                return;
            }

            if (avoidConnectingToPeripheral(device)){
                Log.d(TAG+CTRL, "skipping connecting to"+device.getName()+device.getAddress() );
                return;
            }

            ((Scan) pendingTask).devicesBeforeConnect -= 1;

            int remaining = ((Scan) pendingTask).devicesBeforeConnect;
            if (remaining <= 0) {
                scanner.stopScan(scanCallback);
                isScanning = false;
                taskEnded();
            }
            addToQueue(new ConnectToPeripheral(device));
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            boolean shouldContinue = pendingTask instanceof Scan;
            if (!shouldContinue){
                Log.w(TAG+CTRL, "current task is not scan, skipping handling scan fail");
                return;
            }

            scanner.stopScan(scanCallback);
            isScanning = false;
            taskEnded();
        }
    };

    private UUID getPeripheralUUID(String address){
       for (UUID key: connectedPeripherals.keySet()){
           BluetoothGatt gatt = connectedPeripherals.get(key);
           assert gatt != null;
           if (gatt.getDevice().getAddress().equals(address)){
               return key;
           }
       }
       return null;
    }
    private boolean avoidConnectingToPeripheral(BluetoothDevice device){
        String address = device.getAddress();
        int retries = peripheralRetryCount.getOrDefault(address, 0);
        boolean isAlreadyConnecting = connectingPeripherals.containsKey(address);
        boolean isAlreadyConnected = getPeripheralUUID(address) != null;
        boolean avoid = !centralIsOn || isAlreadyConnecting || retries > MAX_PERIPHERAL_RETRIES || isAlreadyConnected;
        if (avoid && !isAlreadyConnected) {
            Log.d(TAG+CTRL, "Avoiding connection - "
                    + "centralOff: " + !centralIsOn
                    + ", connecting: " + isAlreadyConnecting
                    + ", retriesOverMax: " + (retries > MAX_PERIPHERAL_RETRIES));
        }

        return avoid;
    }

    @SuppressLint("MissingPermission")
    private void startConnectToPeripheral(ConnectToPeripheral task){
        BluetoothDevice device = task.device;
        if (avoidConnectingToPeripheral(task.device)){
            Log.d(TAG+CTRL, "dropping connecting to" + device.getName());
            taskEnded();
            return;
        }

        int count  = peripheralRetryCount.getOrDefault(device.getAddress(), 0);
        peripheralRetryCount.put(device.getAddress(), count + 1);
        connectingPeripherals.put(device.getAddress(), device);
        device.connectGatt(context, false, gattCallback);
    }


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String address = gatt.getDevice().getAddress();
            String name = gatt.getDevice().getName();

            boolean anticipatedConnect = pendingTask instanceof ConnectToPeripheral && ((ConnectToPeripheral) pendingTask).device.getAddress().equals(address);
            boolean anticipatedDisconnect = pendingTask instanceof DisconnectPeripheral && ((DisconnectPeripheral) pendingTask).gatt.getDevice().getAddress().equals(address);

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (!anticipatedConnect){
                    Log.w(TAG+CTRL, "current task is not connect to peripheral or not this device, skipping");
                    return;
                }
                taskEnded();
                Log.d(TAG+CTRL, "Connected (not fully though) to: " + name+address);
                addToQueue(new DiscoverServices(gatt));

            }else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (anticipatedConnect || anticipatedDisconnect){
                    taskEnded();
                }

                Log.w(TAG+CTRL, "Disconnected from: " + name + address+" anticipated:"+(anticipatedConnect||anticipatedDisconnect));
                UUID uuid = getPeripheralUUID(address);
                removeIfTwoWayConnected(uuid);
                connectingPeripherals.remove(address);
                connectedPeripherals.remove(uuid);

                if (avoidConnectingToPeripheral(gatt.getDevice())){
                    Log.d(TAG+CTRL, "skip trying to reconnect to "+name+address);
                    gatt.close();
                    return;
                }

                addToQueue(new ConnectToPeripheral(gatt.getDevice()));

            }


        }
        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            boolean shouldContinue = pendingTask instanceof DiscoverServices && ((DiscoverServices) pendingTask).gatt.getDevice().getAddress().equals(gatt.getDevice().getAddress());
            if (!shouldContinue){
                Log.w(TAG+CTRL, "current task is not discover services or not for this device, skipping");
                return;
            }
            taskEnded();

            if (status != BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG+CTRL, "Services discovery failed for "+gatt.getDevice().getName());
                addToQueue(new DisconnectPeripheral(gatt));
                return;
            }
            Log.d(TAG+CTRL, "Services discovered for " + gatt.getDevice().getAddress());

            if (!centralIsOn){
                Log.d(TAG+CTRL, "can't continue connection ladder because central is off");
                addToQueue(new DisconnectPeripheral(gatt));
                return;
            }

            try{
                //to make sure message characteristic is not null
                gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(CommonConstants.MESSAGE_UUID);
                BluetoothGattCharacteristic idCharacteristic = gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(ID_UUID);
                addToQueue(new ReadCharacteristic(idCharacteristic, gatt));
            }catch (Exception e){
                //see if any null errors
                Log.e(TAG+CTRL, "error on discover services"+e);
                addToQueue(new DisconnectPeripheral(gatt));
                throw  e;
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);

            boolean isReadCharInstance = pendingTask instanceof ReadCharacteristic;
            boolean isSameChar = isReadCharInstance && ((ReadCharacteristic) pendingTask).characteristic.getUuid().equals(characteristic.getUuid());
            boolean isSameAddress = isReadCharInstance && ((ReadCharacteristic) pendingTask).gatt.getDevice().getAddress().equals(gatt.getDevice().getAddress());

            boolean shouldContinue = isReadCharInstance && isSameChar && isSameAddress;
            if (!shouldContinue){
                Log.w(TAG+CTRL, "current task is not read characteristic or not this characteristic, skipping");
                return;
            }

            taskEnded();

            boolean noSuccessful = status != BluetoothGatt.GATT_SUCCESS;
            boolean notId  = !characteristic.getUuid().equals(ID_UUID);
            boolean centralIsOff = !centralIsOn;
            if (noSuccessful || notId || centralIsOff) {
                Log.w(TAG+CTRL, "stopping from fully connecting"+gatt.getDevice().getName()+gatt.getDevice().getAddress()+ "due to notSuccessful:"+noSuccessful+" notId:"+notId+" centralIsOff"+centralIsOff);
                addToQueue(new DisconnectPeripheral(gatt));
                return;
            }

            Log.d(TAG+CTRL, "Read characteristic from "+gatt.getDevice().getName()+" char:"+characteristic.getUuid()+" val:"+ Arrays.toString(characteristic.getValue()));

            try {
                Log.d(TAG+CTRL, "id received");
                UUID uuid = ConvertUUID.bytesToUUID(characteristic.getValue());
                Log.d(TAG+CTRL, "Device UUID of " + gatt.getDevice().getName() + " is : " + uuid + "now fully connected!");

                //Fully connected
                connectingPeripherals.remove(gatt.getDevice().getAddress());
                connectedPeripherals.put(uuid, gatt);
                addIfTwoWayConnected(uuid);

                //Write my uuid
                BluetoothGattCharacteristic idCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(ID_UUID);
                addToQueue(new WriteCharacteristic(gatt, idCharacteristic, ConvertUUID.uuidToBytes(id), 3));

            }catch (Exception e){
                //if can't parse or null errors
                Log.e(TAG+CTRL, "error when reading id"+e);
                addToQueue(new DisconnectPeripheral(gatt));
                throw e;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            boolean shouldContinue = pendingTask instanceof WriteCharacteristic && ((WriteCharacteristic) pendingTask).characteristic == characteristic;
            if (!shouldContinue){
                Log.w(TAG+CTRL, "current task is not characteristic writing or not this characteristic, skipping");
                return;
            }
            taskEnded();
            WriteCharacteristic task = (WriteCharacteristic) pendingTask;

            if (status == BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG+CTRL, "sent data "+ Arrays.toString(task.data) +" successfully!");
                return;
            }

            if (task.remainingRetries <= 0){
                Log.d(TAG+CTRL, "could not send to characteristic after retries, stopping");
                addToQueue(new DisconnectPeripheral(gatt));
                return;
            }

            addToQueue(new WriteCharacteristic(gatt, characteristic, task.data, task.remainingRetries-1  ));

        }
    };

    @SuppressLint("MissingPermission")
    private void startDisconnectPeripheral(DisconnectPeripheral task){
        boolean isConnecting = connectingPeripherals.containsKey(task.gatt.getDevice().getAddress());
        boolean isConnected = connectedPeripherals.containsKey(getPeripheralUUID(task.gatt.getDevice().getAddress()));
        if (!isConnecting && !isConnected){
            Log.d(TAG+CTRL, "skipping disconnect because"+task.gatt.getDevice().getName()+task.gatt.getDevice().getAddress()+"is already disconnected");
            taskEnded();
            return;
        }

       task.gatt.disconnect();
    }

    @SuppressLint("MissingPermission")
    private void startDiscoverServices(DiscoverServices task){
        task.gatt.discoverServices();
    }


    @SuppressLint("MissingPermission")
    private void startReadingCharacteristic(ReadCharacteristic task){
       task.gatt.readCharacteristic(task.characteristic);
    }

    @SuppressLint("MissingPermission")
    private void startWritingCharacteristic(WriteCharacteristic task){
        task.characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        task.characteristic.setValue(task.data);
        task.gatt.writeCharacteristic(messageCharacteristic);
    }

    @SuppressLint("MissingPermission")
    public void stopCentral(){
        if (!centralIsOn){
            Log.d(TAG+CTRL, "already off");
            return;
        }

        centralIsOn = false;
        Log.d(TAG+CTRL, "disconnecting to all connected devices:"+ connectedPeripherals.size()+" but has "+connectingPeripherals.size()+" connecting devices");
        for (BluetoothGatt gatt : connectedPeripherals.values()) {
            addToQueue(new DisconnectPeripheral(gatt));
        }
        if (pendingTask instanceof Scan){
            isScanning = false;
            scanner.stopScan(scanCallback);
        }
    }

    private BluetoothGattCharacteristic getMessageCharacteristic(BluetoothGatt gatt){
        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null){
            Log.e(TAG+CTRL, "service is null when trying to get message char");
            throw new RuntimeException("service is null when trying to get message char");
        }
        BluetoothGattCharacteristic characteristic =service.getCharacteristic(MESSAGE_UUID);
        if (characteristic == null){
            Log.e(TAG+CTRL, "service is null when trying to get message char");
            throw new RuntimeException("service is null when trying to get message char");
        }
        return characteristic;
    }

    ///// peripheral methods (follows sequence of operations as much as possible)

    public void startPeripheral(){
        if (peripheralIsOn){
            Log.d(TAG+PRFL, "is already on");
            return;
        }

        peripheralIsOn = true;
        addToQueue(new StartGattServer());
    }

    @SuppressLint("MissingPermission")
    private void startGattServer(StartGattServer task){
        if (gattServer != null || !peripheralIsOn){
            Log.d(TAG+PRFL, "skipping starting gatt server due to isNotNull:"+(gattServer!=null) + " peripheralIsOff:"+!peripheralIsOn);
            taskEnded();
            return;
        }

        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        gattServer = btManager.openGattServer(context, gattServerCallback);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        messageCharacteristic = new BluetoothGattCharacteristic(
                MESSAGE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ
        );

        BluetoothGattCharacteristic idCharacteristic = new BluetoothGattCharacteristic(
                ID_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        service.addCharacteristic(messageCharacteristic);
        service.addCharacteristic(idCharacteristic);
        gattServer.addService(service);
        taskEnded();

        addToQueue(new Advertise());
    }
    @SuppressLint("MissingPermission")
    private void startAdvertising(Advertise task) {
        if (gattServer == null || !peripheralIsOn){
            Log.d(TAG+PRFL, "skipping starting gatt server due to gatIsNull:"+(gattServer==null) + " peripheralIsOff:"+!peripheralIsOn);
            taskEnded();
            return;
        }
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0) // No timeout for Samsung
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID))
                .build();

        advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        advertiser.startAdvertising(settings, data, advertisementCallback);
    }

    private final AdvertiseCallback advertisementCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            boolean shouldContinue = pendingTask instanceof Advertise;
            if (!shouldContinue){
                Log.w(TAG+PRFL, "current task is not advertisement, skipping");
                return;
            }
            taskEnded();
            Log.d(TAG+PRFL, "Advertisement started");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);

            boolean shouldContinue = pendingTask instanceof Advertise;
            if (!shouldContinue){
                Log.w(TAG+PRFL, "current task is not advertisement, skipping canceling it");
                return;
            }
            taskEnded();
            Log.d(TAG+PRFL, "Advertisement failed:" + errorCode);
        }
    };

    private UUID getCentralUUID(String address){
        for (UUID key: connectedCentrals.keySet()){
            BluetoothDevice device = connectedCentrals.get(key);
            if (device!= null && device.getAddress().equals(address)){
                return  key;
            }
        }
        return null;
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            String address = device.getAddress();
            String name = device.getName();

            boolean anticipatingDisconnect = pendingTask instanceof DisconnectCentral && ((DisconnectCentral) pendingTask).device.getAddress().equals(device.getAddress());
            boolean exists = connectingCentrals.containsKey(address) || (getCentralUUID(address)!=null);

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (exists) {
                    Log.w(TAG+PRFL, name + " (" + address + ") attempted to connect twice. Ignoring");
                    addToQueue(new DisconnectCentral(device));
                    return;
                }
                if (!peripheralIsOn){
                    Log.d(TAG+PRFL, "disconnecting because peripheral is off: "+name+address);
                    addToQueue(new DisconnectCentral(device));
                    return;
                }

                connectingCentrals.put(device.getAddress(), device);
                Log.d(TAG+PRFL, "Central connected (not fully though): " + name + address+". Now have " + connectingCentrals.size() + "connecting centrals. status:"+ status );

                //so that server.cancelConnection() causes disconnect events. According to https://stackoverflow.com/questions/38762758/bluetoothgattserver-cancelconnection-does-not-cancel-the-connection
                addToQueue(new ConnectCentral(device));

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (anticipatingDisconnect){
                    Log.d(TAG+PRFL, "anticipated disconnect of "+name+address+" is successful");
                    taskEnded();
                }

                if (!exists) {
                    //Can happen when both central and peripheral decide to disconnect at the same time
                    Log.w(TAG+PRFL, name + address + " was already not connected. Ignoring disconnect.");
                    return;
                }

                Log.d(TAG+PRFL, "Central disconnected: " + name + address+" status:"+status );
                UUID uuid = getCentralUUID(address);
                connectingCentrals.remove(address);
                connectedCentrals.remove(uuid);
                removeIfTwoWayConnected(uuid);

            } else {
                Log.w(TAG+PRFL, "Unknown state: " + newState + " status: "+status);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            boolean isRequestingId = characteristic.getUuid().equals(ID_UUID);

            if (peripheralIsOn && isRequestingId) {
                Log.d(TAG+PRFL, "Id read request received from " + device.getName() + ":" + characteristic.getUuid());
                addToQueue(new SendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ConvertUUID.uuidToBytes(id)));
            }else{
                Log.w(TAG+PRFL, "rejecting read request from" + device.getName() + ":" + characteristic.getUuid()+ "because didn'tRequestID"+isRequestingId+" peripheralIsOff:"+!peripheralIsOn);
                addToQueue(new SendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null));
                addToQueue(new DisconnectCentral(device));
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            SendResponse successResponse = new SendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
            SendResponse failResponse = new SendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);

            if (characteristic.getUuid().equals(MESSAGE_UUID)) {
                String message = new String(value, StandardCharsets.UTF_8);
                Log.d(TAG+PRFL, "Received (string?): " + message+" from "+device.getName()+device.getAddress());
                UUID uuid = getCentralUUID(device.getAddress());

                if (uuid == null){
                    Log.d(TAG+PRFL, "central send a message but was not connected"+device.getName()+device.getAddress()+" disconnecting");
                    addToQueue(new DisconnectCentral(device));
                    if (responseNeeded) addToQueue(failResponse);
                    return;
                }

                notifyIfTwoWayConnected(uuid, value);
                if (responseNeeded) addToQueue(successResponse);

            } else if (characteristic.getUuid().equals(ID_UUID)){
                UUID otherId;
                try{
                    otherId = ConvertUUID.bytesToUUID(value);
                }catch (Exception e){
                    Log.d(TAG+PRFL, "couldn't parse uuid from central"+device.getName()+device.getAddress()+" where value is"+ Arrays.toString(value));
                    addToQueue(new DisconnectCentral(device));
                    if (responseNeeded) addToQueue(failResponse);
                    return;
                }

                if (connectedCentrals.containsKey(otherId)){
                    Log.d(TAG+PRFL, "central attempted connecting twice "+device.getName()+device.getAddress()+" with uuid"+otherId);
                    addToQueue(new DisconnectCentral(device));
                    if (responseNeeded) addToQueue(failResponse);
                    return;
                }

                connectedCentrals.put(otherId, device);
                connectingCentrals.remove(device.getAddress());
                addIfTwoWayConnected(otherId);
                if (responseNeeded) addToQueue(successResponse);

            } else{
                Log.e(TAG+PRFL, "unexpected characteristic was written:"+characteristic.getUuid());
                addToQueue(new DisconnectCentral(device));
                if (responseNeeded) addToQueue(failResponse);
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void startConnectCentral(ConnectCentral task){
        //so that server.cancelConnection() causes disconnect events. According to https://stackoverflow.com/questions/38762758/bluetoothgattserver-cancelconnection-does-not-cancel-the-connection
        if (gattServer == null){
            Log.d(TAG+PRFL, "gatt server has been already closed, skipping connecting");
        }else{
            gattServer.connect(task.device, false);
        }
        taskEnded();
    }

    @SuppressLint("MissingPermission")
    private void startDisconnectCentral(DisconnectCentral task){
        if (gattServer == null){
            Log.d(TAG+PRFL, "gatt server already closed, skipping disconnecting");
            taskEnded();
            return;
        }
        boolean isConnecting = (connectingCentrals.containsKey(task.device.getAddress()));
        boolean isConnected = getCentralUUID(task.device.getAddress()) != null;
        if (!isConnecting && !isConnected){
            Log.d(TAG+PRFL, "skipping disconnecting to "+task.device.getName()+task.device.getAddress()+" as it is already not connected");
            taskEnded();
            return;
        }
        gattServer.cancelConnection(task.device);
    }

    @SuppressLint("MissingPermission")
    private void startSendResponse(SendResponse task){
        if (gattServer == null){
            Log.d(TAG+PRFL, "gatt server already closed, skipping disconnecting");
        }else{
            gattServer.sendResponse(task.device, task.requestId, task.newState, task.offset, task.data);
        }
        taskEnded();
    }

@SuppressLint("MissingPermission")
private void stopPeripheral(){
    if (!peripheralIsOn) {
        Log.d(TAG+PRFL, "is already off");
        return;
    }
    peripheralIsOn = false;
    advertiser.stopAdvertising(advertisementCallback);

    for (BluetoothDevice device: connectingCentrals.values()) {
        addToQueue(new DisconnectCentral(device));
    }

    for (BluetoothDevice device: connectedCentrals.values()){
        addToQueue(new DisconnectCentral(device));
    }

    addToQueue(new CloseGatt());
}

@SuppressLint("MissingPermission")
private void startClosingGatt(CloseGatt task){
    if (gattServer == null){
        Log.d(TAG+PRFL, "gatt has already been stopped, skipping");
        taskEnded();
        return;
    }
    gattServer.close();
    gattServer = null;
    taskEnded();
}

    //// shared methods
    @SuppressLint("MissingPermission")
    private void addIfTwoWayConnected(UUID uuid){
        BluetoothDevice peripheral = connectedCentrals.get(uuid);
        BluetoothGatt centralGatt = connectedPeripherals.get(uuid);
        BluetoothDevice central = centralGatt == null? null:centralGatt.getDevice();

        if (peripheral!=null && central!=null && !twoWayConnectedDevices.containsKey(uuid)){
            String name = "unknown";
            if (peripheral.getName()!=null)name = peripheral.getName();
            if (central.getName()!=null)name = central.getName();

            BLEDevice device = new BLEDevice(uuid, name, peripheral.getAddress());
            twoWayConnectedDevices.put(uuid, device);
            neighborConnectedListener.onEvent(device);
            nearbyDevicesListener.onEvent(getNearbyDevices());
        }
    }

    private void removeIfTwoWayConnected(UUID uuid){
        if (uuid == null) return;
        if (twoWayConnectedDevices.containsKey(uuid)){
            neighborDisconnectedListener.onEvent(twoWayConnectedDevices.get(uuid));
            twoWayConnectedDevices.remove(uuid);
        }
    }

    private void notifyIfTwoWayConnected(UUID uuid, byte[] data){
        if (twoWayConnectedDevices.containsKey(uuid)){
            BLEDevice device = twoWayConnectedDevices.get(uuid);
            dataListener.onEvent(data, device);
        }else{
            Log.w(TAG, "wanted to notify onData() but "+uuid+" is not two way connected");
        }
    }

    private void notifyDiscovered(UUID uuid, String address, String name){
        //TODO: store in a map so that discovery and connection give the same instance
        neighborDiscoveredListener.onEvent( new BLEDevice(uuid,name, address ));
    }


    /////public methods
    @Override
    public ArrayList<Device> getNeighbourDevices() {
        return new ArrayList<>(twoWayConnectedDevices.values());
    }

    @Override
    public void start() throws Exception {
        startCentral();
        startPeripheral();
    }

    @Override
    public void stop() {
        stopCentral();
        stopPeripheral();
    }

    @Override
    public ArrayList<Device> getNearbyDevices() {
        return getNeighbourDevices();
    }

    @Override
    public void send(byte[] data) throws SendError {
        for (BluetoothGatt gatt: connectedPeripherals.values()){
            BluetoothGattCharacteristic characteristic = getMessageCharacteristic(gatt);
            addToQueue(new WriteCharacteristic(gatt, characteristic, data, 3));
        }
    }

    @Override
    public void send(byte[] data, Device neighbor) throws SendError {
        BluetoothGatt gatt = connectedPeripherals.get(neighbor.uuid);
        if (gatt == null){
            Log.e(TAG, "no peripheral found to send to");
            return;
        }
        BluetoothGattCharacteristic characteristic = getMessageCharacteristic(gatt);
        addToQueue(new WriteCharacteristic(gatt, characteristic, data, 3));
    }
}
