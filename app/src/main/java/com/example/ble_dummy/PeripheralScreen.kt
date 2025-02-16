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


    Column(modifier = Modifier.padding(16.dp)) {
        // Status Display
        Text("Status: $connectionStatus", style = MaterialTheme.typography.bodyLarge)


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
//                    blePeripheral.send("cde".toByteArray(), )
                    message = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Message")
        }
    }

    LaunchedEffect(Unit) {
//        blePeripheral.start()
        connectionStatus = "Advertising..."
    }
}
