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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.UUID

class MainActivity : ComponentActivity() {

    var bluetoothHandler: BLEHandler? = null
    val id: UUID = UUID.randomUUID()
    val TAG = "my_kotlin_screen"
    var bleEnabler: BLEEnabler? = null

    var perm: BLEPermissions? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge() // Ensure this function is defined
        val that = this;
        this.perm = BLEPermissions( this,object :BLEPermissionListener{
            override fun onEnabled() {
                Toast.makeText(that, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
        }
            override fun onDisabled() {
                Toast.makeText(that, "Bluetooth disabled", Toast.LENGTH_SHORT).show()
        }
        } );

        setContent {
            BLE_DummyTheme {
                // Create a NavController instance for navigation
                val navController = rememberNavController()

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
                                Button(
                                    onClick = { navController.navigate("central") },
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text("Be Central")
                                }

                                Button(
                                    onClick = { navController.navigate("peripheral") },
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text("Be Peripheral")
                                }

                                // New button for requesting Bluetooth permissions
                                Button(
                                    onClick = {
                                        that.perm?.enable()
                                        Log.d(TAG, "requesting permissions from button$perm");

                                              },
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text("Request Bluetooth Permissions")
                                }
                            }
                        }
                        // Define the "central" route destination
                        composable("central") {
                            CentralScreen() // Create this composable for your central functionality
                        }
                        // Define the "peripheral" route destination
                        composable("peripheral") {
                            PeripheralScreen() // Create this composable for your peripheral functionality
                        }
                    }
                }
            }
        }
    }
}


//class MainActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            BLE_DummyTheme {
//                val navController = rememberNavController()
//                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
//                    MainScreen(navController = navController, modifier = Modifier.padding(paddingValues), )
//
//                }
//            }
//        }
//    }
//}
