package com.example.mesh_base.ble;

import static com.example.mesh_base.ble.CommonConstants.CCCD_UUID;
import static com.example.mesh_base.ble.CommonConstants.ID_UUID;
import static com.example.mesh_base.ble.CommonConstants.MESSAGE_UUID;
import static com.example.mesh_base.ble.CommonConstants.SERVICE_UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class BLEPeripheral {
  private final HashMap<String, BluetoothDevice> connectingDevices = new HashMap<>();
  private final HashMap<UUID, BluetoothDevice> connectedDevices = new HashMap<>();
  private final String TAG;
  BLEHandler handler;
  boolean isOn = false;
  /////peripheral fields
  private BluetoothGattServer server;
  private BluetoothLeAdvertiser advertiser;
  private BluetoothGattCharacteristic messageCharacteristic;
  private BluetoothGattDescriptor messageDescriptor;

  BLEPeripheral(BLEHandler handler) {
    this.handler = handler;
    this.TAG = handler.TAG + BLEHandler.PRFL;
  }

  BluetoothGattCharacteristic getMessageCharacteristic() {
    return messageCharacteristic;
  }

  BluetoothDevice getCentral(UUID id) {
    return connectedDevices.get(id);
  }

  public void startPeripheral() {
    if (isOn) {
      Log.d(TAG, "is already on");
      return;
    }

    isOn = true;
    handler.addToQueue(new StartGattServer());
  }

  @SuppressLint("MissingPermission")
  void startGattServer(StartGattServer task) {
    if (server != null || !isOn) {
      Log.d(TAG, "skipping starting gatt server due to isNotNull:" + (server != null) + " peripheralIsOff:" + !isOn);
      handler.taskEnded();
      return;
    }

    BluetoothManager btManager = (BluetoothManager) handler.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
    server = btManager.openGattServer(handler.getContext(), serverCallback);

    BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

    messageCharacteristic = new BluetoothGattCharacteristic(
            MESSAGE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ
    );

    messageDescriptor = new BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ
    );

    messageCharacteristic.addDescriptor(messageDescriptor);

    BluetoothGattCharacteristic idCharacteristic = new BluetoothGattCharacteristic(
            ID_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
    );

    service.addCharacteristic(messageCharacteristic);
    service.addCharacteristic(idCharacteristic);
    server.addService(service);
  }

  void expireStartGattServer(StartGattServer task) {
    handler.addToQueue(new CloseGatt());
    handler.taskEnded();
  }  private final BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {

    @Override
    public void onServiceAdded(int status, BluetoothGattService service) {
      super.onServiceAdded(status, service);
      boolean shouldContinue = handler.getPending() instanceof StartGattServer;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not start gatt server, skipping");
        return;
      }

      boolean isKnownService = service.getUuid().equals(SERVICE_UUID);
      boolean isSuccessful = status == BluetoothGatt.GATT_SUCCESS;

      if (isOn && isKnownService && isSuccessful) {
        Log.d(TAG, "service added successfully" + service.getUuid());
        handler.addToQueue(new Advertise());
        handler.taskEnded();
      } else {
        Log.w(TAG, "cant confirm gatt server started because peripheralOff:" + !isOn + " notIsKnownService" + !isKnownService + " notSuccessful:" + (!isSuccessful) + " gattCode:" + status);
        handler.addToQueue(new CloseGatt());
        handler.taskEnded();
      }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
      String address = device.getAddress();
      String name = device.getName();

      boolean anticipatingDisconnect = handler.getPending() instanceof DisconnectCentral && ((DisconnectCentral) handler.getPending()).device.getAddress().equals(device.getAddress());
      boolean exists = connectingDevices.containsKey(address) || (getCentralUUID(address) != null);

      if (newState == BluetoothGatt.STATE_CONNECTED) {
        if (exists) {
          Log.w(TAG, name + " (" + address + ") attempted to connect twice. Ignoring");
          handler.addToQueue(new DisconnectCentral(device));
          return;
        }
        if (!isOn) {
          Log.d(TAG, "disconnecting because peripheral is off: " + name + address);
          handler.addToQueue(new DisconnectCentral(device));
          return;
        }

        connectingDevices.put(device.getAddress(), device);
        Log.d(TAG, "Central connected (not fully though): " + name + address + ". Now have " + connectingDevices.size() + "connecting centrals. status:" + status);

        //so that server.cancelConnection() causes disconnect events. According to https://stackoverflow.com/questions/38762758/bluetoothgattserver-cancelconnection-does-not-cancel-the-connection
        handler.addToQueue(new ConnectCentral(device));
        handler.notifyDiscovered(device.getName(), device.getAddress());

      } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
        if (anticipatingDisconnect) {
          Log.d(TAG, "anticipated disconnect of " + name + address + " is successful");
        }

        if (!exists) {
          //Can happen when both central and peripheral decide to disconnect at the same time
          Log.w(TAG, name + address + " was already not connected. Ignoring disconnect.");
          if (anticipatingDisconnect) handler.taskEnded();
          return;
        }

        Log.d(TAG, "Central disconnected: " + name + address + " status:" + status);
        UUID uuid = getCentralUUID(address);
        connectingDevices.remove(address);
        connectedDevices.remove(uuid);
        handler.notifyDisconnect(uuid);
        handler.addToQueue(new Scan());
        if (anticipatingDisconnect) handler.taskEnded();

      } else {
        Log.w(TAG, "Unknown state: " + newState + " status: " + status);
      }
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
      super.onDescriptorReadRequest(device, requestId, offset, descriptor);
      boolean isRequestingIndication = descriptor.getUuid().equals(CCCD_UUID);
      byte[] messageDescValue = messageDescriptor.getValue();

      if (isOn && isRequestingIndication && messageDescValue != null) {
        Log.d(TAG, "indications read request received from " + device.getName() + ":" + descriptor.getUuid());
        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, messageDescriptor.getValue());
      } else {
        Log.w(TAG, "rejecting indication read request from" + device.getName() + ":" + descriptor.getUuid() + "because didn'tRequestNotification" + !isRequestingIndication + " peripheralIsOff:" + !isOn + " messageDescValueIsNull:" + (messageDescValue == null));
        sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
        handler.addToQueue(new DisconnectCentral(device));
      }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
      super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
      boolean isRequestingIndication = descriptor.getUuid().equals(CCCD_UUID);
      boolean isEnable = Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);

      if (isOn && isRequestingIndication && isEnable) {
        Log.d(TAG, "indications write request received from " + device.getName() + ":" + descriptor.getUuid());
        descriptor.setValue(value);
        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
      } else {
        Log.w(TAG, "rejecting indication write request from" + device.getName() + ":" + descriptor.getUuid() + "because didn'tRequestNotification" + !isRequestingIndication + " peripheralIsOff:" + !isOn + "notIsEnable" + !isEnable);
        sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
        handler.addToQueue(new DisconnectCentral(device));
      }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
      super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
      boolean isRequestingId = characteristic.getUuid().equals(ID_UUID);

      if (isOn && isRequestingId) {
        Log.d(TAG, "Id read request received from " + device.getName() + ":" + characteristic.getUuid());
        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ConvertUUID.uuidToBytes(handler.getId()));
      } else {
        Log.w(TAG, "rejecting read request from" + device.getName() + ":" + characteristic.getUuid() + "because didn'tRequestID" + isRequestingId + " peripheralIsOff:" + !isOn);
        sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
        handler.addToQueue(new DisconnectCentral(device));
      }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

      if (characteristic.getUuid().equals(MESSAGE_UUID)) {
        String message = new String(value, StandardCharsets.UTF_8);
        Log.d(TAG, "Received (string?): " + message + " from " + device.getName() + device.getAddress());
        UUID uuid = getCentralUUID(device.getAddress());

        if (uuid == null) {
          Log.d(TAG, "central send a message but was not connected" + device.getName() + device.getAddress() + " disconnecting");
          handler.addToQueue(new DisconnectCentral(device));
          if (responseNeeded)
            sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
          return;
        }

        handler.notifyData(uuid, value);
        if (responseNeeded)
          sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);

      } else if (characteristic.getUuid().equals(ID_UUID)) {
        UUID otherId;
        try {
          otherId = ConvertUUID.bytesToUUID(value);
        } catch (Exception e) {
          Log.d(TAG, "couldn't parse uuid from central" + device.getName() + device.getAddress() + " where value is" + Arrays.toString(value));
          handler.addToQueue(new DisconnectCentral(device));
          if (responseNeeded)
            sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
          return;
        }

        if (handler.connectedExists(otherId)) {
          Log.d(TAG, "central is already connected " + device.getName() + device.getAddress() + " with uuid" + otherId + ", disconnecting");
          handler.addToQueue(new DisconnectCentral(device));
          if (responseNeeded)
            sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
          return;
        }

        connectedDevices.put(otherId, device);
        connectingDevices.remove(device.getAddress());
        handler.notifyConnect(otherId);
        handler.addToQueue(new Scan());
        if (responseNeeded)
          sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);

      } else {
        Log.e(TAG, "unexpected characteristic was written:" + characteristic.getUuid());
        handler.addToQueue(new DisconnectCentral(device));
        if (responseNeeded)
          sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
      }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onNotificationSent(BluetoothDevice device, int status) {
      super.onNotificationSent(device, status);

      boolean shouldContinue = handler.getPending() instanceof IndicateCharacteristic && ((IndicateCharacteristic) handler.getPending()).device.getAddress().equals(device.getAddress());
      if (!shouldContinue) {
        Log.w(TAG, "current task is not indication or the address of device does not match, skipping");
        return;
      }
      IndicateCharacteristic task = (IndicateCharacteristic) handler.getPending();

      if (status == BluetoothGatt.GATT_SUCCESS) {
        Log.d(TAG, "sent data to central" + device.getName() + device.getAddress() + " successfully!");
        handler.taskEnded();
        return;
      }

      if (task.remainingRetries <= 0) {
        Log.d(TAG, "could not indicate after retries, stopping");
        handler.addToQueue(new DisconnectCentral(device));
        handler.taskEnded();
        return;
      }

      Log.d(TAG, "retrying indication");
      handler.addToQueue(new IndicateCharacteristic(task.remainingRetries - 1, task.characteristic, task.value, task.device));
      handler.taskEnded();
    }
  };

  @SuppressLint("MissingPermission")
  void startAdvertising(Advertise task) {
    boolean alreadyAdvertising = advertiser != null;
    if (server == null || !isOn || alreadyAdvertising) {
      Log.d(TAG, "skipping starting advertising due to gatIsNull:" + (server == null) + " peripheralIsOff:" + !isOn + "alreadyAdvertising:" + alreadyAdvertising);
      handler.taskEnded();
      return;
    }
    AdvertiseSettings settings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build();

    AdvertiseData data = new AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID))
            .build();

    advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
    try {
      advertiser.startAdvertising(settings, data, advertisementCallback);
    } catch (Exception e) {
      Log.w(TAG, "couldn't advertise due to error:" + e + ", closing gatt");
      advertiser.stopAdvertising(advertisementCallback);
      advertiser = null;
      handler.addToQueue(new CloseGatt());
      handler.taskEnded();
    }
  }


  ///// peripheral methods (follows sequence of operations as much as possible)

  @SuppressLint("MissingPermission")
  void expireStartAdvertising(Advertise task) {
    Log.d(TAG, "advertising expired, closing gatt server");
    advertiser.stopAdvertising(advertisementCallback);
    advertiser = null;
    handler.addToQueue(new CloseGatt());
    handler.taskEnded();
  }

  private UUID getCentralUUID(String address) {
    for (UUID key : connectedDevices.keySet()) {
      BluetoothDevice device = connectedDevices.get(key);
      if (device != null && device.getAddress().equals(address)) {
        return key;
      }
    }
    return null;
  }

  @SuppressLint("MissingPermission")
  void startConnectCentral(ConnectCentral task) {
    //so that server.cancelConnection() causes disconnect events. According to https://stackoverflow.com/questions/38762758/bluetoothgattserver-cancelconnection-does-not-cancel-the-connection
    if (server == null) {
      Log.d(TAG, "gatt server has been already closed, skipping connecting");
      handler.taskEnded();
    } else {
      server.connect(task.device, false);
      handler.taskEnded();
    }
  }

  @SuppressLint("MissingPermission")
  void startIndicateCharacteristic(IndicateCharacteristic task) {
    if (task.characteristic == null || server == null) {
      Log.w(TAG, "can not indicate message to central" + task.device.getName() + task.device.getAddress() + " because characteristicIsNull:" + (task.characteristic == null) + " gattServerIsNull" + (server == null));
      handler.taskEnded();
      return;
    }
    task.characteristic.setValue(task.value);
    server.notifyCharacteristicChanged(task.device, task.characteristic, true);
  }

  void expireIndicateCharacteristic(IndicateCharacteristic task) {
    if (task.remainingRetries > 0) {
      handler.addToQueue(new IndicateCharacteristic(task.remainingRetries - 1, task.characteristic, task.value, task.device));
    } else {
      handler.addToQueue(new DisconnectCentral(task.device));
    }
  }

  @SuppressLint("MissingPermission")
  void startDisconnectCentral(DisconnectCentral task) {
    if (server == null) {
      Log.d(TAG, "gatt server already closed, skipping disconnecting");
      handler.taskEnded();
      return;
    }
    boolean isConnecting = (connectingDevices.containsKey(task.device.getAddress()));
    boolean isConnected = getCentralUUID(task.device.getAddress()) != null;
    if (!isConnecting && !isConnected) {
      Log.d(TAG, "skipping disconnecting to " + task.device.getName() + task.device.getAddress() + " as it is already not connected");
      handler.taskEnded();
      return;
    }
    server.cancelConnection(task.device);
  }

  void expireDisconnectCentral(DisconnectCentral task) {
    UUID uuid = getCentralUUID(task.device.getAddress());
    handler.notifyDisconnect(uuid);
    connectingDevices.remove(task.device.getAddress());
    connectedDevices.remove(uuid);
  }

  @SuppressLint("MissingPermission")
  private void sendResponse(BluetoothDevice device, int requestId, int newState, int offset, byte[] data) {
    if (server == null) {
      Log.d(TAG, "gatt server already closed, skipping sending response");
    } else {
      server.sendResponse(device, requestId, newState, offset, data);
    }
  }

  @SuppressLint("MissingPermission")
  public void stopPeripheral() {
    if (!isOn) {
      Log.d(TAG, "is already off");
      return;
    }
    isOn = false;
    if (advertiser != null) {
      advertiser.stopAdvertising(advertisementCallback);
      advertiser = null;
    } else {
      Log.w(TAG, "advertiser is null, skipping stopping advertising");
    }

    for (BluetoothDevice device : connectingDevices.values()) {
      handler.addToQueue(new DisconnectCentral(device));
    }

    for (BluetoothDevice device : connectedDevices.values()) {
      handler.addToQueue(new DisconnectCentral(device));
    }

    handler.addToQueue(new CloseGatt());
  }

  @SuppressLint("MissingPermission")
  void startClosingGatt(CloseGatt task) {
    if (server == null) {
      Log.d(TAG, "gatt has already been stopped, skipping");
      handler.addToQueue(new Scan());
      handler.taskEnded();
      return;
    }
    if (advertiser != null) {
      Log.d(TAG, "closing advertiser along with closing gatt");
      advertiser.stopAdvertising(advertisementCallback);
      advertiser = null;
    }

    server.close();
    server = null;
    handler.addToQueue(new Scan());
    handler.taskEnded();
  }



  private final AdvertiseCallback advertisementCallback = new AdvertiseCallback() {
    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
      super.onStartSuccess(settingsInEffect);
      boolean shouldContinue = handler.getPending() instanceof Advertise;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not advertisement, skipping");
        return;
      }
      Log.d(TAG, "Advertisement started successfully");
      handler.addToQueue(new Scan());
      handler.taskEnded();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onStartFailure(int errorCode) {
      super.onStartFailure(errorCode);

      boolean shouldContinue = handler.getPending() instanceof Advertise;
      if (!shouldContinue) {
        Log.w(TAG, "current task is not advertisement, skipping canceling it");
        return;
      }
      Log.d(TAG, "Advertisement failed:" + errorCode);
      if (advertiser == null) {
        Log.w(TAG, "advertiser was null onStartFailure!, skipping .stopAdvertising");
      } else {
        advertiser.stopAdvertising(advertisementCallback);
        advertiser = null;
      }
      handler.addToQueue(new CloseGatt());
      handler.taskEnded();
    }


  };


}
