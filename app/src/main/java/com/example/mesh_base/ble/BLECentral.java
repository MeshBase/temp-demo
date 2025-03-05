package com.example.mesh_base.ble;

import static com.example.mesh_base.ble.BLEHandler.CTRL;
import static com.example.mesh_base.ble.CommonConstants.CCCD_UUID;
import static com.example.mesh_base.ble.CommonConstants.ID_UUID;
import static com.example.mesh_base.ble.CommonConstants.MESSAGE_UUID;
import static com.example.mesh_base.ble.CommonConstants.SERVICE_UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class BLECentral {
  private static final int MAX_PERIPHERAL_RETRIES = 7;
  private static final long SCAN_TIME_GAP = 6_500; //6.5 seconds
  private static final int MAX_MTU_SIZE = 517;
  private static String TAG;
  public final Map<String, Integer> peripheralConnectTryCount = new HashMap<>();
  private final HashMap<String, BluetoothDevice> connectingPeripherals = new HashMap<>();
  private final Map<UUID, BluetoothGatt> connectedPeripherals = new HashMap<>();
  private final HashSet<String> scanResultAddresses = new HashSet<>();
  boolean centralIsOn = false;
  BluetoothLeScanner scanner;
  BLEHandler handler;
  private boolean isScanning = false;
  private long lastScanTime = 0;

  BLECentral(BLEHandler handler) {
    this.handler = handler;
    this.TAG = handler.TAG + CTRL;
  }


  @SuppressLint("MissingPermission")
  void startCentral() {
    if (centralIsOn) {
      Log.d(TAG, "already on");
      return;
    }
    centralIsOn = true;
    if (!isScanning) {
      //1 device to not make users wait too much for the first connection
      handler.addToQueue(new Scan(1));
    }
  }

  @SuppressLint("MissingPermission")
  void startScan(Scan task) {
    boolean isOnlyTask = handler.getQueue().isEmpty();
    if (!centralIsOn || isScanning || !isOnlyTask) {
      Log.d(TAG, "ignoring scan task due to centralIsOff: " + !centralIsOn + " scanning already: " + isScanning + " notOnlyTaskInQueue:" + !isOnlyTask + " (queueSize=" + handler.getQueue().size() + ")");
      handler.taskEnded();
      return;
    }

    if (scanner == null) {
      BluetoothManager btManager = (BluetoothManager) handler.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
      if (btManager == null) {
        Log.e(TAG, "btManager is null, cant scan");
        handler.taskEnded();
        return;
      }
      scanner = btManager.getAdapter().getBluetoothLeScanner();
      if (scanner == null) {
        Log.e(TAG + CTRL, "scanner is null, cant scan");
        handler.taskEnded();
        return;
      }
    }


    long timeGap = System.currentTimeMillis() - lastScanTime;
    if (timeGap < SCAN_TIME_GAP) {
      long addAfter = SCAN_TIME_GAP - timeGap;
      Log.d(TAG, "scanning too early for the 5 scans per 30 seconds rule. re adding task after " + addAfter + " milliseconds");

      handler.taskEnded();
      new Handler(Looper.getMainLooper()).postDelayed(() -> handler.addToQueue(new Scan(task.devicesBeforeConnect)), addAfter);

      return;
    }

    scanResultAddresses.clear();
    isScanning = true;
    ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(CommonConstants.SERVICE_UUID)).build();
    scanner.startScan(Collections.singletonList(filter), new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) //detects far away devices
            .build(), scanCallback);
    lastScanTime = System.currentTimeMillis();
  }


  @SuppressLint("MissingPermission")
  private void expireScan(Scan task) {
    stopScan();
  }

  @SuppressLint("MissingPermission")
  private void stopScan() {
    isScanning = false;
    if (scanner == null) {
      Log.e(TAG + CTRL, "scanner is null, skipping stopping scan");
    } else {
      scanner.stopScan(scanCallback);
    }
  }

  private UUID getPeripheralUUID(String address) {
    for (UUID key : connectedPeripherals.keySet()) {
      BluetoothGatt gatt = connectedPeripherals.get(key);
      assert gatt != null;
      if (gatt.getDevice().getAddress().equals(address)) {
        return key;
      }
    }
    return null;
  }

  @SuppressLint("MissingPermission")
  private boolean avoidConnectingToPeripheral(BluetoothDevice device) {
    String address = device.getAddress();
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    String name = adapter != null ? adapter.getName() : "unknown";
    String otherName = device.getName() == null ? "unknown" : device.getName();
    int retries = peripheralConnectTryCount.getOrDefault(address, 0);
    boolean hasBiggerHash = (name.hashCode() ^ 0x5bf03635) > (otherName.hashCode() ^ 0x5bf03635);

    boolean tooManyRetries = retries > MAX_PERIPHERAL_RETRIES;
    boolean isAlreadyConnecting = connectingPeripherals.containsKey(address);
    boolean isAlreadyConnected = getPeripheralUUID(address) != null;
    //To avoid duplicate central-peripheral connection in both directions. XOR so that hashes are more evenly distributed.
    boolean avoidConViaCentral = hasBiggerHash && centralIsOn && handler.peripheralIsOn();

    boolean avoid = !centralIsOn || isAlreadyConnecting || tooManyRetries || isAlreadyConnected || avoidConViaCentral;
    if (avoid && !isAlreadyConnected) {
      Log.d(TAG, "Avoiding connection - "
              + "centralOff: " + !centralIsOn
              + ", connecting: " + isAlreadyConnecting
              + ", retriesOverMax: " + (retries > MAX_PERIPHERAL_RETRIES)
              + ", avoidConViaCentral: " + avoidConViaCentral);
    }

    return avoid;
  }

  @SuppressLint("MissingPermission")
  private void startConnectToPeripheral(ConnectToPeripheral task) {
    BluetoothDevice device = task.device;
    if (avoidConnectingToPeripheral(task.device)) {
      Log.d(TAG, "dropping connecting to" + device.getName());
      handler.taskEnded();
      return;
    }

    connectingPeripherals.put(device.getAddress(), device);
    device.connectGatt(handler.getContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE);
  }  private final ScanCallback scanCallback = new ScanCallback() {

    @SuppressLint("MissingPermission")
    @Override
    public void onScanResult(int callbackType, ScanResult result) {
      BluetoothDevice device = result.getDevice();

      boolean shouldContinue = handler.getPending() instanceof Scan;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not scan, skipping. stopping scan");
        //if not stopped, will never expire nor find devices to connect to
        stopScan();
        handler.addToQueue(new Scan());
        return;
      }

      if (scanResultAddresses.contains(device.getAddress())) {
        //Commented, because it overwhelms the logs
//                Log.d(TAG , device.getName() + device.getAddress() + " already in scan addresses, skipping adding connect task");
        return;
      }
      scanResultAddresses.add(device.getAddress());


      if (avoidConnectingToPeripheral(device)) {
        Log.d(TAG, "skipping connecting to" + device.getName() + device.getAddress());
        return;
      }

      handler.addToQueue(new ConnectToPeripheral(device));

      ((Scan) handler.getPending()).devicesBeforeConnect -= 1;
      int remaining = ((Scan) handler.getPending()).devicesBeforeConnect;
      if (remaining <= 0) {
        stopScan();
        handler.taskEnded();
      }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onScanFailed(int errorCode) {
      super.onScanFailed(errorCode);
      boolean shouldContinue = handler.getPending() instanceof Scan;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not scan, skipping handling scan fail");
        return;
      }

      Log.d(TAG, "scan failed, code: " + errorCode + " adding scan task again");
      handler.addToQueue(new Scan(((Scan) handler.getPending()).devicesBeforeConnect));

      stopScan();
      handler.taskEnded();
    }
  };


  ///// central methods (follows sequence of operations as much as possible)

  private void expireConnectToPeripheral(ConnectToPeripheral task) {
    int count = peripheralConnectTryCount.getOrDefault(task.device.getAddress(), 0);
    peripheralConnectTryCount.put(task.device.getAddress(), count + 1);
    connectingPeripherals.remove(task.device.getAddress());
  }

  @SuppressLint("MissingPermission")
  private void startDisconnectPeripheral(DisconnectPeripheral task) {
    boolean isConnecting = connectingPeripherals.containsKey(task.gatt.getDevice().getAddress());
    boolean isConnected = connectedPeripherals.containsKey(getPeripheralUUID(task.gatt.getDevice().getAddress()));
    if (!isConnecting && !isConnected) {
      Log.d(TAG, "skipping disconnect because" + task.gatt.getDevice().getName() + task.gatt.getDevice().getAddress() + "is already disconnected");
      handler.taskEnded();
      return;
    }

    task.gatt.disconnect();
  }

  @SuppressLint("MissingPermission")
  private void expireDisconnectPeripheral(DisconnectPeripheral task) {
    String address = task.gatt.getDevice().getAddress();
    task.gatt.close();
    UUID uuid = getPeripheralUUID(address);
    handler.notifyDisconnect(uuid);
    connectingPeripherals.remove(address);
    connectedPeripherals.remove(uuid);
    if (task.forgetRetries) {
      peripheralConnectTryCount.remove(address);
    }
  }

  @SuppressLint("MissingPermission")
  private void startNegotiateMTU(NegotiateMTU task) {
    task.gatt.requestMtu(task.size);
  }

  private void expireNegotiateMTU(NegotiateMTU task) {
    handler.addToQueue(new DisconnectPeripheral(task.gatt));
  }

  @SuppressLint("MissingPermission")
  private void startDiscoverServices(DiscoverServices task) {
    //in ui thread to prevent waiting for an service discovered callback that was actually dropped somehow. according to https://punchthrough.com/android-ble-guide/
    new Handler(Looper.getMainLooper()).post(() -> task.gatt.discoverServices());
  }

  private void expireDiscoverServices(DiscoverServices task) {
    handler.addToQueue(new DisconnectPeripheral(task.gatt));
  }  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
    @SuppressLint("MissingPermission")
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      String address = gatt.getDevice().getAddress();
      String name = gatt.getDevice().getName();

      boolean anticipatedConnect = handler.getPending() instanceof ConnectToPeripheral && ((ConnectToPeripheral) handler.getPending()).device.getAddress().equals(address);
      boolean anticipatedDisconnect = handler.getPending() instanceof DisconnectPeripheral && ((DisconnectPeripheral) handler.getPending()).gatt.getDevice().getAddress().equals(address);

      if (newState == BluetoothGatt.STATE_CONNECTED) {
        if (!anticipatedConnect) {
          Log.w(TAG, "did not anticipate connecting to " + name + address + ", disconnecting");
          handler.addToQueue(new DisconnectPeripheral(gatt));
          handler.taskEnded();
          return;
        }
        Log.d(TAG, "Connected (not fully though) to: " + name + address);
        handler.notifyDiscovered(name, address);
        handler.addToQueue(new DiscoverServices(gatt));
        handler.taskEnded();

      } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {

        Log.w(TAG, "Disconnected from: " + name + address + " anticipated:" + (anticipatedConnect || anticipatedDisconnect) + ". status:" + status);
        gatt.close();
        UUID uuid = getPeripheralUUID(address);
        handler.notifyDisconnect(uuid);
        connectingPeripherals.remove(address);
        connectedPeripherals.remove(uuid);


        if (handler.getPending() instanceof DisconnectPeripheral && ((DisconnectPeripheral) handler.getPending()).forgetRetries) {
          Log.d(TAG, "forgetting retry count of " + name + address);
          peripheralConnectTryCount.remove(address);
        }

        if (handler.getPending() instanceof DisconnectPeripheral && ((DisconnectPeripheral) handler.getPending()).tryReconnect) {

          if (avoidConnectingToPeripheral(gatt.getDevice())) {
            Log.d(TAG, "skip trying to reconnect to " + name + address);
          } else {
            int count = peripheralConnectTryCount.getOrDefault(address, 0);
            peripheralConnectTryCount.put(address, count + 1);

            Log.d(TAG, "Retrying to connect to " + name + address + " " + peripheralConnectTryCount.getOrDefault(address, -1) + "tries done");
            handler.addToQueue(new ConnectToPeripheral(gatt.getDevice()));
          }
        }

        handler.addToQueue(new Scan());
        if (anticipatedConnect || anticipatedDisconnect) handler.taskEnded();
      } else {
        Log.d(TAG, "unknown connection state " + newState + " , disconnecting");
        handler.addToQueue(new DisconnectPeripheral(gatt));
      }

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      boolean shouldContinue = handler.getPending() instanceof DiscoverServices && ((DiscoverServices) handler.getPending()).gatt.getDevice().getAddress().equals(gatt.getDevice().getAddress());
      if (!shouldContinue) {
        Log.w(TAG, "current task is not discover services or not for this device, skipping");
        return;
      }

      if (status != BluetoothGatt.GATT_SUCCESS) {
        Log.d(TAG, "Services discovery failed for " + gatt.getDevice().getName());
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }
      Log.d(TAG, "Services discovered for " + gatt.getDevice().getAddress());

      if (!centralIsOn) {
        Log.d(TAG, "can't continue connection ladder because central is off");
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }

      handler.addToQueue(new NegotiateMTU(gatt, MAX_MTU_SIZE));
      handler.taskEnded();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
      super.onMtuChanged(gatt, mtu, status);

      boolean isMtuTask = handler.getPending() instanceof NegotiateMTU;
      boolean isSameAddress = isMtuTask && ((NegotiateMTU) handler.getPending()).gatt.getDevice().getAddress().equals(gatt.getDevice().getAddress());
      boolean shouldContinue = isMtuTask && isSameAddress;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not mtu changing or not this device, skipping");
        return;
      }

      boolean notSuccess = status != BluetoothGatt.GATT_SUCCESS;
      boolean centralIsOff = !centralIsOn;
      if (notSuccess || centralIsOff) {
        Log.w(TAG, "[on mtu changed] stopping from fully connecting" + gatt.getDevice().getName() + gatt.getDevice().getAddress() + "due to notSuccess:" + notSuccess + " centralIsOff" + centralIsOff);
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }

      Log.d(TAG, "new mtu value is " + mtu);
      try {
        BluetoothGattCharacteristic messageCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(MESSAGE_UUID);
        handler.addToQueue(new EnableIndication(messageCharacteristic, gatt));
        handler.taskEnded();
      } catch (Exception e) {
        //see if any null errors
        Log.e(TAG, "error on mtu changed: " + e);
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
      }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
      super.onDescriptorWrite(gatt, descriptor, status);

      boolean isEnableIndication = handler.getPending() instanceof EnableIndication;
      boolean isSameAddress = isEnableIndication && ((EnableIndication) handler.getPending()).gatt.getDevice().getAddress().equals(gatt.getDevice().getAddress());
      boolean shouldContinue = isEnableIndication && isSameAddress;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not descriptor writing or not this device, skipping");
        return;
      }

      boolean notIndication = !descriptor.getUuid().equals(CCCD_UUID);
      boolean centralIsOff = !centralIsOn;
      if (notIndication || centralIsOff) {
        Log.w(TAG, "[on desc write] stopping from fully connecting" + gatt.getDevice().getName() + gatt.getDevice().getAddress() + "due to notIndication:" + notIndication + " centralIsOff" + centralIsOff);
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }

      try {
        BluetoothGattCharacteristic idCharacteristic = gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(ID_UUID);
        if (idCharacteristic == null) throw new Exception("id characteristic is null");
        handler.addToQueue(new ReadCharacteristic(idCharacteristic, gatt));
        handler.addToQueue(new Scan());
        handler.taskEnded();
      } catch (Exception e) {
        Log.e(TAG, "error on descriptor write" + e);
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
      }

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      super.onCharacteristicRead(gatt, characteristic, status);

      boolean isReadCharInstance = handler.getPending() instanceof ReadCharacteristic;
      boolean isSameChar = isReadCharInstance && ((ReadCharacteristic) handler.getPending()).characteristic.getUuid().equals(characteristic.getUuid());
      boolean isSameAddress = isReadCharInstance && ((ReadCharacteristic) handler.getPending()).gatt.getDevice().getAddress().equals(gatt.getDevice().getAddress());

      boolean shouldContinue = isReadCharInstance && isSameChar && isSameAddress;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not read characteristic or not this characteristic, skipping");
        return;
      }


      boolean notSuccessful = status != BluetoothGatt.GATT_SUCCESS;
      boolean notId = !characteristic.getUuid().equals(ID_UUID);
      boolean centralIsOff = !centralIsOn;
      if (notSuccessful || notId || centralIsOff) {
        Log.w(TAG, "[on char read] stopping from fully connecting" + gatt.getDevice().getName() + gatt.getDevice().getAddress() + "due to notSuccessful:" + notSuccessful + " notId:" + notId + " centralIsOff" + centralIsOff);
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }

      Log.d(TAG, "Read characteristic from " + gatt.getDevice().getName() + " char:" + characteristic.getUuid() + " val:" + Arrays.toString(characteristic.getValue()));
      UUID uuid;
      BluetoothGattCharacteristic idCharacteristic;
      try {
        uuid = ConvertUUID.bytesToUUID(characteristic.getValue());
        idCharacteristic = gatt.getService(CommonConstants.SERVICE_UUID).getCharacteristic(CommonConstants.ID_UUID);
        if (idCharacteristic == null)
          throw new Exception("id characteristic should not be null");
      } catch (Exception e) {
        Log.e(TAG, "error when reading uuid / idCharacteristic" + e);
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }

      Log.d(TAG, "id received" + uuid);
      if (connectedPeripherals.containsKey(uuid)) {
        Log.d(TAG, "peripheral" + gatt.getDevice().getName() + gatt.getDevice().getName() + " is already connected with uuid " + uuid + ",disconnecting ");
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }

      Log.d(TAG, "Device UUID of " + gatt.getDevice().getName() + " is : " + uuid);
      handler.addToQueue(new WriteCharacteristic(gatt, idCharacteristic, ConvertUUID.uuidToBytes(handler.getId()), 3, uuid));
      handler.addToQueue(new Scan());
      handler.taskEnded();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      super.onCharacteristicWrite(gatt, characteristic, status);
      boolean shouldContinue = handler.getPending() instanceof WriteCharacteristic && ((WriteCharacteristic) handler.getPending()).characteristic == characteristic;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not characteristic writing or not this characteristic, skipping");
        return;
      }
      WriteCharacteristic task = (WriteCharacteristic) handler.getPending();
      String name = task.gatt.getDevice().getName();
      String address = task.gatt.getDevice().getAddress();

      if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(ID_UUID) && handler.connectedExists(task.uuid)) {
        Log.d(TAG, "device " + name + address + " is already connected, disconnecting");
        handler.addToQueue(new DisconnectPeripheral(gatt, true, false));
        return;
      }

      if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(ID_UUID)) {
        Log.d(TAG, name + address + " is now connected fully!");
        connectingPeripherals.remove(address);
        connectedPeripherals.put(task.uuid, gatt);
        peripheralConnectTryCount.remove(address);
        handler.notifyConnect(task.uuid);
        handler.taskEnded();
        return;
      }

      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.d(TAG, "sent data " + Arrays.toString(task.data) + " successfully!");
        handler.taskEnded();
        return;
      }

      if (task.remainingRetries <= 0) {
        Log.d(TAG, "could not send to characteristic after retries, stopping");
        handler.addToQueue(new DisconnectPeripheral(gatt));
        handler.taskEnded();
        return;
      }

      Log.d(TAG, "retrying writing characteristic");
      handler.addToQueue(new WriteCharacteristic(gatt, characteristic, task.data, task.remainingRetries - 1, task.uuid));
      handler.taskEnded();

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
      super.onCharacteristicChanged(gatt, characteristic);
      Log.d(TAG, "characteristic changed");
      BluetoothDevice device = gatt.getDevice();

      if (characteristic.getUuid().equals(MESSAGE_UUID)) {
        Log.d(TAG, "Received data from " + device.getName() + device.getAddress());
        UUID uuid = getPeripheralUUID(device.getAddress());

        if (uuid == null) {
          Log.d(TAG, "peripheral sent a message but was not connected" + device.getName() + device.getAddress() + " disconnecting");
          handler.addToQueue(new DisconnectPeripheral(gatt));
          return;
        }

        handler.notifyData(uuid, characteristic.getValue());
      } else {
        Log.e(TAG, "unexpected characteristic was written:" + characteristic.getUuid());
        handler.addToQueue(new DisconnectCentral(device));
      }
    }
  };

  @SuppressLint("MissingPermission")
  private void startReadingCharacteristic(ReadCharacteristic task) {
    task.gatt.readCharacteristic(task.characteristic);
  }

  private void expireReadingCharacteristic(ReadCharacteristic task) {
    handler.addToQueue(new DisconnectPeripheral(task.gatt));
  }

  @SuppressLint("MissingPermission")
  private void startEnablingIndication(EnableIndication task) {
    BluetoothGattDescriptor descriptor = task.characteristic.getDescriptor(CCCD_UUID);
    boolean success = task.gatt.setCharacteristicNotification(task.characteristic, true);
    if (!success || descriptor == null) {
      Log.w(TAG, "could not enable indication due to couldSetCharNotification:" + !success + " descriptorIsNull:" + (descriptor == null));
      handler.addToQueue(new DisconnectPeripheral(task.gatt));
      handler.taskEnded();
      return;
    }

    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
    task.gatt.writeDescriptor(descriptor);
  }

  private void expireEnablingIndication(EnableIndication task) {
    handler.addToQueue(new DisconnectPeripheral(task.gatt));
  }

  @SuppressLint("MissingPermission")
  private void startWritingCharacteristic(WriteCharacteristic task) {

    task.characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
    task.characteristic.setValue(task.data);
    task.gatt.writeCharacteristic(task.characteristic);
  }

  private void expireWritingCharacteristic(WriteCharacteristic task) {
    if (task.remainingRetries > 0) {
      handler.addToQueue(new WriteCharacteristic(task.gatt, task.characteristic, task.data, task.remainingRetries - 1, task.uuid));
    } else {
      handler.addToQueue(new DisconnectPeripheral(task.gatt));
    }
  }

  @SuppressLint("MissingPermission")
  public void stopCentral() {
    if (!centralIsOn) {
      Log.d(TAG, "already off");
      return;
    }

    centralIsOn = false;
    Log.d(TAG, "disconnecting to all connected devices:" + connectedPeripherals.size() + " but has " + connectingPeripherals.size() + " connecting devices");
    for (BluetoothGatt gatt : connectedPeripherals.values()) {
      DisconnectPeripheral task = new DisconnectPeripheral(gatt, true, false);
      //If bluetooth is off, the onConnectionStateChange may not be called by android. so time out faster
      task.expireMilli = 1500L;
      handler.addToQueue(task);
    }
    //so that max retry devices have a chance to try connecting again
    peripheralConnectTryCount.clear();
    if (handler.getPending() instanceof Scan) {
      stopScan();
    }
  }

  private BluetoothGattCharacteristic getMessageCharacteristic(BluetoothGatt gatt) {
    BluetoothGattService service = gatt.getService(SERVICE_UUID);
    if (service == null) {
      Log.e(TAG, "service is null when trying to get message char");
      throw new RuntimeException("service is null when trying to get message char");
    }
    BluetoothGattCharacteristic characteristic = service.getCharacteristic(MESSAGE_UUID);
    if (characteristic == null) {
      Log.e(TAG, "service is null when trying to get message char");
      throw new RuntimeException("service is null when trying to get message char");
    }
    return characteristic;
  }






}
