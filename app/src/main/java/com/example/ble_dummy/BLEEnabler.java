package com.example.ble_dummy;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;

import static androidx.core.app.ActivityCompat.requestPermissions;
import static androidx.core.app.ActivityCompat.startActivityForResult;

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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class BLEEnabler implements BLEEnablerI {
    private String TAG = "my_BleEnabler";

    final private String[] oldVersionPermissions = new String[]{BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION};
    final private String[] recentVersionPermissions = new String[]{BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE};
    private boolean isRecent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    static private BLEEnabler instance = null;

    static public BLEEnabler getInstance() {
        if (instance == null) {
            instance = new BLEEnabler();
        }
        return instance;
    }

    private ComponentActivity activity;
    ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    public void init(BLEEnabledListener enabledListener, BLEDisabledListener disabledListener, ComponentActivity activity) {
        this.activity = activity;

        this.permissionLauncher =
                activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedMap -> {
                    if (hasPermissions() && bluetoothIsOn()) {
                        Log.d(TAG, "Permission granted");
                        enabledListener.onEvent();
                    } else if (hasPermissions() && !bluetoothIsOn()) {
                        promptBluetooth();
                    } else {
                        Log.d(TAG, "Permission not granted");
                        disabledListener.onEvent();
                    }
                });

        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) return;
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    disabledListener.onEvent();
                } else if (state == BluetoothAdapter.STATE_ON && hasPermissions()) {
                    enabledListener.onEvent();
                }


            }
        };

        //Immediately check if it was enabled
        if (isEnabled()) enabledListener.onEvent();
    }

    private boolean hasPermissions() {
        String[] permissions = isRecent ? recentVersionPermissions : oldVersionPermissions;

        for (String permission : permissions) {
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

    @SuppressLint("MissingPermission")
    private void promptBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivity(enableBtIntent);
    }

    @Override
    public boolean isEnabled() {
        return hasPermissions() && bluetoothIsOn();
    }

    @Override
    public void enable() {

        //TODO: handle permanent denial of permissions
        if (isEnabled()) return;
        Log.d(TAG, "trying to enable bluetooth and its permissions");
        //permissions first, then bluetooth
        if (!hasPermissions()) {
            permissionLauncher.launch(isRecent ? recentVersionPermissions : oldVersionPermissions);
        } else {
            promptBluetooth();
        }

    }
}
