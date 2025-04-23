package com.example.mesh_base.wifi_direct;

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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class WifiDirectPermissions {
    private final ComponentActivity activity;
    private final String TAG = "my_WifiP2pPermissions";
    private final ActivityResultLauncher<String[]> permissionsLauncher;
    private final ActivityResultLauncher<Intent> wifiSettingsLauncher;
    private final ActivityResultLauncher<Intent> locationSettingsLauncher;
    private WifiDirectPermissionListener listener;

    public WifiDirectPermissions(ComponentActivity activity, WifiDirectPermissionListener listener) {
        this.activity = activity;
        this.listener = listener;
        permissionsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionsResult
        );
        wifiSettingsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (wifiP2pIsOn()) {
                        checkLocation();
                    } else {
                        Log.d(TAG, "Wi-Fi still disabled after prompt.");
                        listener.onWifiDisabled();
                    }
                }
        );
        locationSettingsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (locationIsOn()) {
                        listener.onEnabled();
                    } else {
                        Log.d(TAG, "Location still disabled after prompt.");
                        listener.onLocationDisabled();
                    }
                }
        );
    }

    public void setListener(WifiDirectPermissionListener listener) {
        this.listener = listener;
    }

    public void enable() {
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            checkWifiAndLocation();
        }
    }

    private void requestPermissions() {
        permissionsLauncher.launch(getPermissions());
    }

    private void checkWifiAndLocation() {
        if (!wifiP2pIsOn()) {
            promptWifi();
        } else if (!locationIsOn()) {
            promptLocation();
        } else {
            listener.onEnabled();
        }
    }

    private void checkLocation() {
        if (!locationIsOn()) {
            promptLocation();
        } else {
            listener.onEnabled();
        }
    }

    private void handlePermissionsResult(Map<String, Boolean> result) {
        boolean allGranted = true;
        for (Map.Entry<String, Boolean> entry : result.entrySet()) {
            if (!entry.getValue()) {
                Log.w(TAG, "Permission " + entry.getKey() + " not granted.");
                allGranted = false;
            }
        }
        if (allGranted) {
            checkWifiAndLocation();
        } else {
            listener.onPermissionsDenied();
        }
    }

    private boolean hasPermissions() {
        boolean allGranted = true;
        for (String permission : getPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission " + permission + " not granted.");
                allGranted = false;
            }
        }
        return allGranted;
    }

    private String[] getPermissions() {
        ArrayList<String> permissions = new ArrayList<>(Arrays.asList(
                permission.ACCESS_WIFI_STATE,
                permission.CHANGE_WIFI_STATE,
                permission.ACCESS_FINE_LOCATION,
                permission.ACCESS_COARSE_LOCATION,
                permission.INTERNET
        ));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(permission.NEARBY_WIFI_DEVICES);
        }

        return permissions.toArray(new String[0]);
    }

    private void promptWifi() {
        Log.d(TAG, "Prompting to enable WiFi");
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        wifiSettingsLauncher.launch(intent);
    }

    private boolean wifiP2pIsOn() {
        WifiManager wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "WifiManager is null, cannot check Wi-Fi status.");
            return false;
        }
        return wifiManager.isWifiEnabled();
    }

    private void promptLocation() {
        Log.d(TAG, "Prompting to enable Location");
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        locationSettingsLauncher.launch(intent);
    }

    private boolean locationIsOn() {
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Log.e(TAG, "LocationManager is null, cannot check location status.");
            return false;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }
    }

    public interface WifiDirectPermissionListener {
        void onEnabled();

        void onPermissionsDenied();

        void onWifiDisabled();

        void onLocationDisabled();
    }
}