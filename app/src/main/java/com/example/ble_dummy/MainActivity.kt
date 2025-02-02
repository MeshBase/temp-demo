package com.example.ble_dummy
// MainActivity.kt
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current

    // Launcher to request multiple permissions
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all permissions are granted
        if (permissions.all { it.value }) {
            Toast.makeText(context, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Some permissions denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to request Bluetooth permissions based on Android version
    fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ uses these permissions
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            // For Android 11 and below, use the legacy permissions
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        permissionsLauncher.launch(permissions)
    }

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
            onClick = { requestBluetoothPermissions() },
            modifier = Modifier.padding(8.dp)
        ) {
            Text("Request Bluetooth Permissions")
        }
    }
}
