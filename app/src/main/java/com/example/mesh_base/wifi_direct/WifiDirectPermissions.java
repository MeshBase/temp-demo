package com.example.mesh_base.wifi_direct;

//public class WifiDirectPermissions { }

import static android.Manifest.permission;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

public class WifiDirectPermissions {
    private final ComponentActivity activity;
    private final String TAG = "my_WifiP2pPermissions";
    private final WifiDirectPermissionListener defaultListener = new WifiDirectPermissionListener() {
        @Override
        public void onEnabled() {
            Log.d(TAG, "WifiDirect enabled (listener not set yet)");
        }

        public void onDisabled() {
            Log.d(TAG, "WifiDirect disabled (listener not set yet)");
        }
    };
    private final ActivityResultLauncher<String[]> permissionsLauncher;
    public WifiDirectPermissions(ComponentActivity activity) {
        this.activity = activity;
        permissionsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (!wifiP2pIsOn()) {
                        promptWifi();
                        Log.d(TAG, "Wifi permission Prompted");
                    } else if (!locationIsOn()) {
                        promptLocation();
                        Log.d(TAG, "Location permission Prompted");
                    } else {
                        listener.onEnabled();
                        Log.d(TAG, "Every permission given!");
                    }
                }
        );
    }

    public void setListener(StatusListener listener) {
        this.listener = listener;
    }

    public void enable() {
        // Request permissions if needed
        if (!hasPermissions()) {
            permissionsLauncher.launch(getPermissions());
        } else if (!wifiP2pIsOn()) {
            promptWifi();
        } else if (!locationIsOn()) {
            promptLocation();
        } else {
            listener.onEnabled();
        }
    }

    private boolean hasPermissions() {
        boolean allGranted = true;
        for (String permission : getPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission denied: " + permission);
                allGranted = false;
            }
        }
        return allGranted;
    }

    private String[] getPermissions() {
        ArrayList<String> permissions = new ArrayList<>(Arrays.asList(
                permission.ACCESS_WIFI_STATE,
                permission.CHANGE_WIFI_STATE,
//                permission.ACCESS_FINE_LOCATION,
//                permission.ACCESS_COARSE_LOCATION,
                permission.INTERNET
        ));

        // Nearby WiFi devices permission if target Android 13 (API level 33) or later
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(permission.NEARBY_WIFI_DEVICES);
        }

        return permissions.toArray(new String[0]);
    }

    private boolean wifiP2pIsOn() {
        WifiManager wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    private void promptWifi() {
        Log.d(TAG, "Prompting to enable WiFi");
        activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }


    private boolean locationIsOn() {
        boolean result;
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            return (
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            );
        }
    }

    private void promptLocation() {
        Log.d(TAG, "Prompting to enable Location");
        activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }
}
}
