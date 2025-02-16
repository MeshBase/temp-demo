package com.example.ble_dummy
// MainActivity.kt
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.ble_dummy.ui.theme.BLE_DummyTheme

//class MainActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            AppNavigation()
//        }
//    }
//}
//
//@Composable
//fun AppNavigation() {
//    val navController = rememberNavController()
//    NavHost(navController = navController, startDestination = "main") {
//        composable("main") { MainScreen(navController) }
//        composable("central") { CentralScreen() }
//        composable("peripheral") { PeripheralScreen() }
//    }
//}
//
//@Composable
//fun MainScreen(navController: androidx.navigation.NavController) {
//    Column(
//        modifier = Modifier.fillMaxSize(),
//        verticalArrangement = Arrangement.Center,
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Button(
//            onClick = { navController.navigate("central") },
//            modifier = Modifier.padding(16.dp)
//        ) { Text("Be Central") }
//
//        Button(
//            onClick = { navController.navigate("peripheral") },
//            modifier = Modifier.padding(16.dp)
//        ) { Text("Be Peripheral") }
//    }
//}


//class MainActivity : ComponentActivity() {
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge() // Ensure this function is defined in your project
//
//        setContent {
//            BLE_DummyTheme {
//                // Create a NavController instance
//                val navController = rememberNavController()
//
//                Scaffold(
//                    modifier = Modifier.fillMaxSize()
//                ) { _ ->
//                    // Pass the NavController to MainScreen
//
//                    MainScreen(navController = navController)
//                }
//            }
//        }
//    }
//}
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.UUID

class MainActivity : ComponentActivity() {

    val id: UUID = UUID.randomUUID()
    val TAG = "my_kotlin_screen"

    var perm: BLEPermissions? = null
    var bleCentral: BLECentral? = null;

    var centralsDevices = mutableStateListOf<BluetoothDevice>()
    val context = this;
    val centralCallback = object : BLECentral.ConnectionCallback {
        @SuppressLint("MissingPermission")
        override fun onDeviceFound(device: BluetoothDevice) {
        }

        override fun onMessageReceived(message: String) {
            Log.d(TAG, "Message recieved: $message")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Received: $message", Toast.LENGTH_LONG).show()
            }

            centralsDevices.forEach { device ->
                run {
                    bleCentral?.send(message.toByteArray(), device.address)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.d(TAG, "Device connected: ${device.name}")
            centralsDevices.add(device);
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceDisconnected(device: BluetoothDevice?) {

            Log.d(TAG, "Device disconnected: ${device?.name}")
            centralsDevices.remove(device)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Disconnected to ${device?.name}", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    }


    var peripheralsDevices = mutableStateListOf<BluetoothDevice>();
    private var blePeripheral: BLEPeripheral? = null;
    val peripheralCallback =object : BLEPeripheral.MessageCallback {

        @SuppressLint("MissingPermission")
        override fun onDeviceConnected(device: BluetoothDevice) {
            peripheralsDevices.add(device)
            Log.d(TAG, "Central connected: ${device.name}")
        }

        @SuppressLint("MissingPermission")
        override fun onDeviceDisconnected(device: BluetoothDevice) {
            Log.d(TAG, "disconnected to: ${device.name}")
            peripheralsDevices.remove(device);
        }

        override fun onMessageReceived(message: ByteArray, device: BluetoothDevice) {
            Log.d(TAG, "Message recieved: ${String(message, Charsets.UTF_8)}")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Message recieved: $message", Toast.LENGTH_SHORT).show()
            }
        }
    }


    ///////
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge() // Ensure this function is defined
        val that = this;
        Log.d(TAG, "create called");

        this.bleCentral = BLECentral(context, centralCallback);
        this.blePeripheral = BLEPeripheral(context, peripheralCallback);

        this.perm = BLEPermissions(this, object : BLEPermissionListener {
            override fun onEnabled() {
                Toast.makeText(that, "BLE enabled", Toast.LENGTH_SHORT).show();
                bleCentral?.start();
                blePeripheral?.start();
            }

            override fun onDisabled() {
                Toast.makeText(that, "BLE disabled", Toast.LENGTH_SHORT).show()
                bleCentral?.stop();
                blePeripheral?.start();
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
                                Button(onClick = { bleCentral?.start() }) {
                                    Text("start central")
                                }
                                Button(onClick = { bleCentral?.stop() }) {
                                    Text("stop central")
                                }

                                Button(onClick = { blePeripheral?.start() }) {
                                    Text("Start peripheral")
                                }

                                Button(onClick = { blePeripheral?.stop() }) {
                                    Text("Stop peripheral")
                                }

                                //// central devices
                                LazyColumn {
                                    items(centralsDevices) { device ->
                                        Row(Modifier.fillMaxWidth().padding(8.dp)) {
                                            Text(
                                                text = "centrals device - ${device.name ?: "Unknown"} (${device.address})",
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(onClick = {
                                                bleCentral?.send("abcd".toByteArray(Charsets.UTF_8), device.address);
                                            }) {
                                                Text("Send")
                                            }
                                        }
                                    }
                                }

                                ////
                                LazyColumn {
                                    items(peripheralsDevices) { device ->
                                        Row(Modifier.fillMaxWidth().padding(8.dp)) {
                                            Text(
                                                text = "peripherals device - ${device.name ?: "Unknown"} (${device.address})",
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(onClick = {
                                                blePeripheral?.send("cdef".toByteArray(Charsets.UTF_8), device.address);
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
