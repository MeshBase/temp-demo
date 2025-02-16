package com.example.ble_dummy
// PeripheralScreen.kt
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp


@Composable
fun PeripheralScreen() {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Not connected") }
    val TAG = "my_peripheral screen"

    val blePeripheral = remember {
        BLEPeripheral(context, object : BLEPeripheral.MessageCallback {
            override fun onDeviceConnected(deviceName: String) {
                connectionStatus = "Connected to: $deviceName"
                Log.d(TAG, "Central connected: $deviceName")

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Central Connected: $deviceName", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onDeviceDisconnected(deviceName: String?) {
                connectionStatus = "advertising again"
                Log.d(TAG, "disconnected to: $deviceName")

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Central disconnected: $deviceName", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onMessageSent(message: String) {
                Log.d(TAG, "Message recieved: $message")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Message recieved: $message", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // Status Display
        Text("Status: $connectionStatus", style = MaterialTheme.typography.bodyLarge)

        Button(onClick = { blePeripheral.start() }) {
            Text("Start")
        }

        Button(onClick = { blePeripheral.stop() }) {
            Text("Stope")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Message Input

        TextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Enter message") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Send Button
        Button(
            onClick = {
                if (message.isNotEmpty()) {
                    blePeripheral.sendMessage(message)
                    message = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Message")
        }
    }

    LaunchedEffect(Unit) {
        blePeripheral.start()
        connectionStatus = "Advertising..."
    }
}
