package com.example.ble_dummy;
import static android.Manifest.permission.*;

import static androidx.activity.result.contract.ActivityResultContracts.*;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;



public class BLEPermissions {

    private String TAG = "my_BleEnabler";

    private ComponentActivity activity;
    private BLEPermissionListener listener;

    ActivityResultLauncher<String[]> permissionLauncher;


    public BLEPermissions(ComponentActivity activity, BLEPermissionListener listener) {
        this.activity = activity;
        this.listener = listener;

        this.permissionLauncher = activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedMap -> {
            if (hasPermissions() && bluetoothIsOn()) {
                Log.d(TAG, "Permission granted");
                listener.onEnabled();
            } else if (hasPermissions() && !bluetoothIsOn()) {
                promptBluetooth();
            } else {
                Log.d(TAG, "Permission not granted");
                listener.onDisabled();
            }
        });

        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) return;
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    listener.onDisabled();
                } else if (state == BluetoothAdapter.STATE_ON && hasPermissions()) {
                    listener.onEnabled();
                }
            }
        };
    }


    public void enable() {
        //TODO: handle permanent denial of permissions
        if (isEnabled()) return;
        Log.d(TAG, "trying to enable bluetooth and its permissions");
        //permissions first, then bluetooth
        if (!hasPermissions()) {
            permissionLauncher.launch(getPermissions());
        } else {
            promptBluetooth();
        }

    }
     private String[]  getPermissions(){
        ArrayList<String> permissions = new ArrayList<>();
        //following startScan permissions -  https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner#startScan(android.bluetooth.le.ScanCallback)
        //line by line
        permissions.add(ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            permissions.add(ACCESS_FINE_LOCATION);
        }
        if ( Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
            permissions.add(BLUETOOTH_ADMIN);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            permissions.add(BLUETOOTH_SCAN);
            permissions.add(ACCESS_FINE_LOCATION);
        }

        //stop scan uses the same permissions

        // following connect gatt permissions - https://developer.android.com/reference/android/bluetooth/BluetoothDevice#connectGatt(android.content.Context,%20boolean,%20android.bluetooth.BluetoothGattCallback)
        //line by line
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            permissions.add(BLUETOOTH_CONNECT);
        }

        //following advertise permissions - https://developer.android.com/reference/android/bluetooth/le/BluetoothLeAdvertiser
        //line by line
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
            permissions.add(BLUETOOTH_ADMIN);
        }

        //following device.getname permissions - https://developer.android.com/reference/android/bluetooth/BluetoothDevice#public-methods
        //line by line
       if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
           permissions.add(BLUETOOTH);
       }

       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
           permissions.add(BLUETOOTH_CONNECT);
       }

       //following gatt.writeCharacteristic permissions -https://developer.android.com/reference/android/bluetooth/BluetoothGatt#writeCharacteristic(android.bluetooth.BluetoothGattCharacteristic)
        //line by line

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
            permissions.add(BLUETOOTH);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
           permissions.add(BLUETOOTH_CONNECT);
        }

        //gatt.readCharacteristic has same permissions
        //gattServer.sendResponse has same permissions

         // new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE) also uses the above permissions


        return permissions.toArray(new String[0]);
    }

    private boolean hasPermissions() {

        for (String permission : this.getPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }

        return true;
    }

    private boolean bluetoothIsOn() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return false;
        return adapter.isEnabled();
    }

    public boolean isEnabled() {
        return hasPermissions() && bluetoothIsOn();
    }

    private void promptBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivity(enableBtIntent);
    }



}
