package com.example.ble_dummy
// MainActivity.kt
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.material3.Scaffold
import com.example.ble_dummy.ui.theme.BLE_DummyTheme
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import java.util.UUID

class MainActivity : ComponentActivity() {

    val id: UUID = UUID.randomUUID()
    val TAG = "my_kotlin_screen"

    var perm: BLEPermissions? = null
    private var connectedDevices = mutableStateListOf<Device>();
    var bleHandler:BLEHandler? = null;

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val that = this;

        this.bleHandler = BLEHandler(
            {connectedDevices.add(it)},
            {connectedDevices.remove(it)},
            {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText( that, "Discovered: ${it.name}", Toast.LENGTH_SHORT ).show()
                }
            },
            {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText( that, "disconnected", Toast.LENGTH_SHORT ).show()
                }
            },
            {
                data:ByteArray, device: Device ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText( that, "Received: ${String(data, Charsets.UTF_8)} from ${device.name}", Toast.LENGTH_SHORT ).show()
                }
            },

            {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText( that, "nearby changed", Toast.LENGTH_SHORT ).show()
                }
            },
            that,
            id
        );

        this.perm = BLEPermissions(this, object : BLEPermissionListener {
            override fun onEnabled() {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(that, "BLE enabled", Toast.LENGTH_SHORT).show();
                }
                //
            }

            override fun onDisabled() {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(that, "BLE disabled", Toast.LENGTH_SHORT).show()
                }
                bleHandler?.stopCentral();
                bleHandler?.stopPeripheral();
            }
        });

        this.perm?.enable();
        setContent {
            BLE_DummyTheme {
                // Create a NavController instance for navigation
                val navController = rememberNavController()
                var message by remember { mutableStateOf("") }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    // Set up the NavHost with a startDestination and define your routes
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(padding)
                    ) {
                        // The main screen route that contains your buttons
                        composable("main") {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                Button(onClick = { perm?.enable() }) {
                                    Text("enable ble")
                                }
                                Button(onClick = { bleHandler?.startCentral() }) {
                                    Text("start central")
                                }
                                Button(onClick = { bleHandler?.stopCentral() }) {
                                    Text("stop central")
                                }

                                Button(onClick = { bleHandler?.startPeripheral() }) {
                                    Text("Start peripheral")
                                }

                                Button(onClick = { bleHandler?.stopPeripheral() }) {
                                    Text("Stop peripheral")
                                }

                                LazyColumn {
                                    items(connectedDevices) { device ->
                                        Row(Modifier.fillMaxWidth().padding(8.dp)) {
                                            Text(
                                                text = "${device.name ?: "Unknown"} (${device.uuid})",
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(onClick = {
                                                bleHandler?.send(message.toByteArray(Charsets.UTF_8), device);
                                            }) {
                                                Text("Send")
                                            }
                                        }
                                    }
                                }

                                ////
                                TextField(
                                    value = message,
                                    onValueChange = { message = it },
                                    label = { Text("Enter message") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                    }
                }
            }
        }
    }
}
