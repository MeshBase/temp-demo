package com.example.ble_dummy;

// BLEPeripheral.java
import android.annotation.SuppressLint;
import android.bluetooth.*;
        import android.bluetooth.le.*;
        import android.content.Context;
import android.util.Log;
import java.util.UUID;

public class BLEPeripheral {
    public static final String TAG = "my_peripheral";
    private static final UUID SERVICE_UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb");

    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic messageCharacteristic;
    private Context context;
    private MessageCallback callback;

    public interface MessageCallback {
        void onMessageSent(String message);
        void onDeviceConnected(String deviceName);
    }

    public BLEPeripheral(Context context, MessageCallback callback) {
        this.context = context;
        this.callback = callback;
        setupGattServer();
    }

    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        gattServer = btManager.openGattServer(context, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    callback.onDeviceConnected(device.getName());
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                     BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                                                     boolean responseNeeded, int offset, byte[] value) {
                String message = new String(value);
                callback.onMessageSent(message);
            }
        });

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        messageCharacteristic = new BluetoothGattCharacteristic(CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(messageCharacteristic);
        gattServer.addService(service);
    }

    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        Log.d(TAG, "Starting advertising");
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID))
                .build();

        BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser()
                .startAdvertising(settings, data, new AdvertiseCallback() {});
    }

    @SuppressLint("MissingPermission")
    public void sendMessage(String message) {
        Log.d(TAG, "Sending message to " + gattServer.getConnectedDevices().size() + " devices");
        messageCharacteristic.setValue(message);
        for (BluetoothDevice device : gattServer.getConnectedDevices()) {
            gattServer.notifyCharacteristicChanged(device, messageCharacteristic, false);
        }
    }
}
