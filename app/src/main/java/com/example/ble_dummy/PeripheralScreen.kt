package com.example.ble_dummy
// PeripheralScreen.kt
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel

class LogViewModel: ViewModel() {
    private val _messages = mutableStateListOf<String>()
    val messages: List<String> = _messages

    fun addMessages(message: String) {
        _messages.add(message)
    }
}

@Composable
fun MessageCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            fontSize = 16.sp
        )
    }
}

@Composable
fun PeripheralScreen() {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Not connected") }
    val TAG = "my_peripheral screen"

    val messages = remember { mutableStateListOf<String>() }

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

            override fun onMessageReceived(message: String) {
                Log.d(TAG, "Message Received: $message")
                messages.add(message)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Message Received: $message", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

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
                    blePeripheral.sendMessage(message)
                    message = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Message")
        }

        LazyColumn(modifier=Modifier.fillMaxSize()) {
            items(messages) {msg ->
                MessageCard(msg)
            }
        }
    }

    LaunchedEffect(Unit) {
        blePeripheral.startAdvertising()
        connectionStatus = "Advertising..."
    }
}
