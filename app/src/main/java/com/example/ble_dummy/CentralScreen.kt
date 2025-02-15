package com.example.ble_dummy

// CentralScreen.kt
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@Composable
fun CentralScreen() {
    val context = LocalContext.current
    var devices = remember { mutableStateListOf<BluetoothDevice>() }
    val TAG = "my_central screen"

    // 1. Create callback FIRST
    val connectionCallback = remember {
        object : BLECentral.ConnectionCallback {
            @SuppressLint("MissingPermission")
            override fun onDeviceFound(device: BluetoothDevice) {
                if (!devices.contains(device)) {
                    devices.add(device)
//                    Log.d(TAG, "Device found: ${device.name}")
                }

                Toast.makeText(context, "Found: ${device.name}", Toast.LENGTH_SHORT).show()
            }

            override fun onMessageReceived(message: String) {
                Log.d(TAG, "Message recieved: $message")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Received: $message", Toast.LENGTH_LONG).show()
                }
            }

            override fun onDeviceConnected(device: BluetoothDevice?) {
                Log.d(TAG, "Device connected: ${device?.name}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Connected to ${device?.name}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onDeviceDisconnected(device: BluetoothDevice?) {

                Log.d(TAG, "Device disconnected: ${device?.name}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Disconnected to ${device?.name}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onMessageForwarded(message: String) {
                Log.d(TAG, "Forwarded: ${message}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Forwarded: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val bleCentral = remember {
        BLECentral(context, connectionCallback)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { bleCentral.start() }) {
            Text("Scan Devices")
        }
        Button(onClick = { bleCentral.stop() }) {
            Text("Stop")
        }

        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(devices) { device ->
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(
                        text = "${device.name ?: "Unknown"} (${device.address})",
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        bleCentral.retryCount.clear() // Reset on manual connect
                        bleCentral.connectToDevice(device)
                        Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
