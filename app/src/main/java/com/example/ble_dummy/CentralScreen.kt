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
    var bleCentral:BLECentral? = remember {
        null
    }

    // 1. Create callback FIRST
//    val connectionCallback = remember { }


    Column(modifier = Modifier.padding(16.dp)) {


        Spacer(modifier = Modifier.height(16.dp))

    }

//    LaunchedEffect(Unit) {
//        bleCentral = BLECentral(context, connectionCallback)
//        bleCentral?.start()
//    }
}
