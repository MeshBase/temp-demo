package com.example.mesh_base.ble;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.mesh_base.global_interfaces.ConnectionHandler;
import com.example.mesh_base.global_interfaces.DataListener;
import com.example.mesh_base.global_interfaces.Device;
import com.example.mesh_base.global_interfaces.DisconnectedListener;
import com.example.mesh_base.global_interfaces.NearbyDevicesListener;
import com.example.mesh_base.global_interfaces.NeighborConnectedListener;
import com.example.mesh_base.global_interfaces.NeighborDisconnectedListener;
import com.example.mesh_base.global_interfaces.NeighborDiscoveredListener;
import com.example.mesh_base.global_interfaces.SendError;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


public class BLEHandler extends ConnectionHandler {


  static final String CTRL = " central: ";
  static final String PRFL = "peripheral:";
  /////common fields and methods
  final String TAG = "my_bleHandler";
  private final UUID id;
  private final Context context;
  private final ConcurrentLinkedQueue<BLETask> queue = new ConcurrentLinkedQueue<>();
  private final HashMap<UUID, BLEDevice> connectedDevices = new HashMap<>();
  BLECentral central;
  BLEPeripheral peripheral;
  private BLETask pendingTask = null;
  //TODO: use BLE Permissions to notify connected and disconnected
  BLEHandler(NeighborConnectedListener neighborConnectedListener, NeighborDisconnectedListener neighborDisconnectedListener, NeighborDiscoveredListener neighborDiscoveredListener, DisconnectedListener disconnectedListener, DataListener dataListener, NearbyDevicesListener nearbyDevicesListener, Context context, UUID id) {
    super(neighborConnectedListener, neighborDisconnectedListener, neighborDiscoveredListener, disconnectedListener, dataListener, nearbyDevicesListener);
    this.context = context;
    this.id = id;
    this.central = new BLECentral(this);
    this.peripheral = new BLEPeripheral(this);

  }

  void addToQueue(BLETask task) {
    synchronized (queue) {
      String taskTag = (pendingTask instanceof PeripheralTask) ? PRFL : CTRL;
      Log.d(TAG + taskTag, "added task " + task.asString() + " .To a queue of length:" + queue.size());

      queue.add(task);
      if (pendingTask == null) {
        startNextTask();
      }
    }
  }

  BLETask getPending() {
    return pendingTask;
  }

  boolean peripheralIsOn() {
    return peripheral.peripheralIsOn;
  }

  Context getContext() {
    return context;
  }

  UUID getId() {
    return this.id;
  }

  AbstractQueue<BLETask> getQueue() {
    return queue;
  }

  boolean centralIsOn() {
    return central.centralIsOn;
  }

  private void startNextTask() {
    synchronized (queue) {

      if (pendingTask != null) {
        String oldTaskTag = (pendingTask instanceof PeripheralTask) ? PRFL : CTRL;
        Log.w(TAG + oldTaskTag, "Can't do task:" + queue.peek() + " until pending task: " + pendingTask.asString() + " is finished");
        return;
      }

      pendingTask = queue.poll();
      if (pendingTask == null) {
        Log.d(TAG, "Queue is empty. no task to do");
        return;
      }

      BLETask task = pendingTask;
      String taskTag = (task instanceof PeripheralTask) ? PRFL : CTRL;

      Log.d(TAG + taskTag, "executing " + task.asString());
      try {
        if (task instanceof Scan) {
          central.startScan((Scan) task);
          expireTask(task, () -> expireScan((Scan) task));
        } else if (task instanceof ConnectToPeripheral) {
          startConnectToPeripheral((ConnectToPeripheral) pendingTask);
          expireTask(task, () -> expireConnectToPeripheral((ConnectToPeripheral) pendingTask));
        } else if (task instanceof DiscoverServices) {
          startDiscoverServices((DiscoverServices) task);
          expireTask(task, () -> expireDiscoverServices((DiscoverServices) task));
        } else if (task instanceof NegotiateMTU) {
          startNegotiateMTU((NegotiateMTU) task);
          expireTask(task, () -> expireNegotiateMTU((NegotiateMTU) task));
        } else if (task instanceof EnableIndication) {
          startEnablingIndication((EnableIndication) task);
          expireTask(task, () -> expireEnablingIndication((EnableIndication) task));
        } else if (task instanceof ReadCharacteristic) {
          startReadingCharacteristic((ReadCharacteristic) task);
          expireTask(task, () -> expireReadingCharacteristic((ReadCharacteristic) task));
        } else if (task instanceof WriteCharacteristic) {
          startWritingCharacteristic((WriteCharacteristic) task);
          expireTask(task, () -> expireWritingCharacteristic((WriteCharacteristic) task));
        } else if (task instanceof DisconnectPeripheral) {
          startDisconnectPeripheral((DisconnectPeripheral) task);
          expireTask(task, () -> expireDisconnectPeripheral((DisconnectPeripheral) task));
        } else if (task instanceof StartGattServer) {
          startGattServer((StartGattServer) task);
          expireTask(task, () -> expireStartGattServer((StartGattServer) task));
        } else if (task instanceof Advertise) {
          startAdvertising((Advertise) task);
          expireTask(task, () -> expireStartAdvertising((Advertise) task));
        } else if (task instanceof ConnectCentral) {
          startConnectCentral((ConnectCentral) task);
          expireTask(task, null);
        } else if (task instanceof IndicateCharacteristic) {
          startIndicateCharacteristic((IndicateCharacteristic) task);
          expireTask(task, () -> expireIndicateCharacteristic((IndicateCharacteristic) task));
        } else if (task instanceof DisconnectCentral) {
          startDisconnectCentral((DisconnectCentral) task);
          expireTask(task, () -> expireDisconnectCentral((DisconnectCentral) task));
        } else if (task instanceof CloseGatt) {
          startClosingGatt((CloseGatt) task);
          expireTask(task, null);
        } else {
          Log.e(TAG + taskTag, "unknown task type" + task.asString());
          expireTask(task, null);
        }
      } catch (Exception e) {
        Log.w(TAG + taskTag, "error when executing task " + task.asString() + ". Force moving on to next task. Error:" + e);
        pendingTask = null;
        startNextTask();
      }

    }
  }

  void taskEnded() {
    synchronized (queue) {
      String taskTag = (pendingTask instanceof PeripheralTask) ? PRFL : CTRL;
      Log.d(TAG + taskTag, "ended task of " + pendingTask.asString());
      pendingTask = null;
      startNextTask();
    }
  }

  private void expireTask(BLETask task, Runnable expireHandler) {
    new Handler(Looper.getMainLooper()).postDelayed(() -> {
      if (pendingTask != task) return;

      if (expireHandler != null) {
        expireHandler.run();
      }

      String taskTag = (pendingTask instanceof PeripheralTask) ? PRFL : CTRL;
      Log.w(TAG + taskTag, pendingTask.asString() + " Timed out after" + pendingTask.expireMilli + "ms. Moving on to next task");
      //In case this is the only task so that a scan task may never be added
      addToQueue(new Scan());

      pendingTask = null;
      startNextTask();
    }, task.expireMilli);
  }


  //// shared methods
  boolean connectedExists(UUID uuid) {
    return connectedDevices.containsKey(uuid);
  }

  @SuppressLint("MissingPermission")
  void notifyConnect(UUID uuid) {
    BluetoothDevice peripheral = connectedCentrals.get(uuid);
    BluetoothGatt centralGatt = connectedPeripherals.get(uuid);
    BluetoothDevice central = centralGatt == null ? null : centralGatt.getDevice();

    if (central == null && peripheral == null) {
      Log.w(TAG, "connected device " + uuid + " not found in centrals or peripherals!");
      return;
    }


    String name = "unk";
    String address = "unk";
    if (peripheral != null) {
      address = peripheral.getAddress();
      if (peripheral.getName() != null) {
        name = peripheral.getName();
      } else {
        name = "Unknown-Peripheral-" + uuid.toString().substring(0, 5);
      }
    } else {
      address = central.getAddress();
      if (central.getName() != null) {
        name = central.getName();
      } else {
        name = "Unknown-Central-" + uuid.toString().substring(0, 5);
      }
    }

    BLEDevice device = new BLEDevice(uuid, name, address);
    connectedDevices.put(uuid, device);
    neighborConnectedListener.onEvent(device);
    nearbyDevicesListener.onEvent(getNearbyDevices());
  }

  void notifyDisconnect(UUID uuid) {
    if (uuid == null) return;
    if (!connectedDevices.containsKey(uuid)) return;

    neighborDisconnectedListener.onEvent(connectedDevices.get(uuid));
    connectedDevices.remove(uuid);
    nearbyDevicesListener.onEvent(getNearbyDevices());
  }

  void notifyData(UUID uuid, byte[] data) {
    if (connectedDevices.containsKey(uuid)) {
      BLEDevice device = connectedDevices.get(uuid);
      dataListener.onEvent(data, device);
    } else {
      Log.w(TAG, "wanted to notify onData() but " + uuid + " is not connected");
    }
  }

  void notifyDiscovered(String address, String name) {
    //TODO: perhaps create another Device class that doesn't have uuid
    neighborDiscoveredListener.onEvent(new BLEDevice(null, name, address));
    Log.d(TAG, "notified neighbors");
  }


  /////public methods
  @Override
  public ArrayList<Device> getNeighbourDevices() {
    return new ArrayList<>(connectedDevices.values());
  }

  @Override
  public void start() throws Exception {
    central.startCentral();
    peripheral.startPeripheral();
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
    for (Device neighbor : getNeighbourDevices()) {
      send(data, neighbor);
    }
  }

  @SuppressLint("MissingPermission")
  @Override
  public void send(byte[] data, Device neighbor) throws SendError {
    //TODO: handle mtu negotiation
//        if (data.length > 20) {
//            //from https://punchthrough.com/android-ble-guide/
//            throw new SendError("not guaranteed to send more than 20 bytes at a time");
//        }

    if (!connectedDevices.containsKey(neighbor.uuid)) {
      Log.w(TAG, "wanted to send to " + neighbor.uuid + " but neighbor not connected");
      return;
    }

    BluetoothGatt gatt = connectedPeripherals.get(neighbor.uuid);
    BluetoothDevice centralDevice = connectedCentrals.get(neighbor.uuid);
    if (gatt == null && centralDevice == null) {
      Log.w(TAG, "device exists, but not found in connectedCentrals nor connectedPeripherals!" + neighbor.name + neighbor.uuid);
      return;
    }

    if (gatt != null) {
      BluetoothGattCharacteristic characteristic = getMessageCharacteristic(gatt);
      addToQueue(new WriteCharacteristic(gatt, characteristic, data, 3, neighbor.uuid));
    } else {
      addToQueue(new IndicateCharacteristic(3, peripheralMessageCharacteristic, data, centralDevice));
    }

    if (pendingTask instanceof Scan) {
      Log.d(TAG + CTRL, "ending scan to write quickly");
      stopScan();
      addToQueue(new Scan(((Scan) pendingTask).devicesBeforeConnect));
      taskEnded();
    }
  }
}
