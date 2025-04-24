package com.example.mesh_base.wifi_direct;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.NEARBY_WIFI_DEVICES;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private final Listener listener;

    // Broadcast receiver for Wi-Fi state changes
    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_DISABLED) {
                    Log.d(TAG, "Wi-Fi state changed to: " + state);
                    if (isEnabled()) {
                        listener.onEnabled();
                    } else {
                        listener.onPermissionsDenied();
                    }
                }
            }
        }
    };

    // Broadcast receiver for location state changes
    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                Log.d(TAG, "Location providers changed");
                if (isEnabled()) {
                    listener.onEnabled();
                } else {
                    listener.onPermissionsDenied();
                }
            }
        }
    };

    public WifiDirectPermissions(ComponentActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;

        // Initialize launchers
        permissionsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionsResult
        );
        wifiSettingsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> Log.d(TAG, "Wi-Fi settings prompt ended")
        );
        locationSettingsLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> Log.d(TAG, "Location settings prompt ended")
        );

        // Register broadcast receivers
        IntentFilter wifiFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        activity.registerReceiver(wifiReceiver, wifiFilter);

        IntentFilter locationFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        activity.registerReceiver(locationReceiver, locationFilter);
    }

    public void enable() {
        if (isEnabled()) {
            listener.onEnabled();
            return;
        }
        if (!hasPermissions()) {
            Log.d(TAG, "Requesting permissions");
            requestPermissions();
        } else if (!wifiP2pIsOn()) {
            promptWifi();
        } else if (!locationIsOn()) {
            promptLocation();
        }
    }

    private void requestPermissions() {
        permissionsLauncher.launch(getPermissions());
    }

    private void handlePermissionsResult(Map<String, Boolean> result) {
        if (!hasPermissions()) {
            Log.d(TAG, "Permissions not granted");
            listener.onPermissionsDenied();
        } else if (isEnabled()) {
            Log.d(TAG, "Permissions granted and all conditions met");
            listener.onEnabled();
        } else {
            Log.d(TAG, "Permissions granted, checking next condition");
            enable(); // Proceed to check Wi-Fi or location
        }
    }

    private boolean hasPermissions() {
        for (String permission : getPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] getPermissions() {
        ArrayList<String> permissions = new ArrayList<>(Arrays.asList(
                ACCESS_WIFI_STATE,
                CHANGE_WIFI_STATE,
                INTERNET,
                ACCESS_FINE_LOCATION,
                ACCESS_COARSE_LOCATION
        ));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(NEARBY_WIFI_DEVICES);
        }

        return permissions.toArray(new String[0]);
    }

    private void promptWifi() {
        Log.d(TAG, "Prompting to enable Wi-Fi");
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
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }
    }

    public boolean isEnabled() {
        return hasPermissions() && wifiP2pIsOn() && locationIsOn();
    }

    public interface Listener {
        void onEnabled();

        void onPermissionsDenied();
    }
}