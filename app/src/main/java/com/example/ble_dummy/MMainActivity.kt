package com.example.ble_dummy

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ble_dummy.ui.theme.BLE_DummyTheme
import java.util.ArrayList
import java.util.UUID

class MMainActivity : ComponentActivity() {
    var bluetoothHandler: BLEHandler? = null;
    val id = UUID.randomUUID();
    val TAG = "my_kotlin_screen";
    var bleEnabler: BLEEnabler? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupBlue();
        setContent {
            BLE_DummyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding),
                        onClick = { this.sendToEveryone() },
                        onClick2 = { this.bluetoothHandler?.stop() },
                        onClick3 = { this.bleEnabler?.enable() }

                    )

                }
            }
        }
    }


    fun sendToEveryone() {
        Log.d(TAG, "trying to send to every one");
        this.bluetoothHandler?.send("Hello world".toByteArray());
    }

    fun setupBlue() {
        Log.d(TAG, "my uuid is" + id.toString());
        val bbleCentral = BBLECentral(this, id);
        val BBlEPeripheral = BBlEPeripheral(this, id);
        this.bluetoothHandler = BLEHandler(
            bbleCentral,
            BBlEPeripheral,
            {
                Log.d(TAG, "connected to " + it.name);
            },
            {
                Log.d(TAG, "disconnected from " + it.name);
            },
            {
                Log.d(TAG, "neighbor discovered listener");
            },
            {
                Log.d(TAG, "neigbor disconnected");
            },
            { data, neighbor ->
                Log.d(
                    TAG,
                    "data received from " + neighbor.name + " data:" + data.toString()
                )
            },
            { devices: ArrayList<Device> -> Log.d(TAG, "nearby devices listener") }
        );

        this.bleEnabler = BLEEnabler.getInstance();
        bleEnabler?.init(
            {
                bluetoothHandler?.start();
                Log.d(TAG, "bluetooth enabled") },
            {
                bluetoothHandler?.stop();
                Log.d(TAG, "bluetooth disabled"); },
            this
        );
        bleEnabler?.enable();

    }

}

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClick2: () -> Unit,
    onClick3: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp), // Optional padding around the column
        verticalArrangement = Arrangement.Center, // Center vertically
        horizontalAlignment = Alignment.CenterHorizontally // Center horizontally
    ) {
        Text(
            text = "Hello $name 2!",
            modifier = Modifier.padding(bottom = 16.dp) // Space below the greeting text
        )
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("Send to Everyone")
        }
        Spacer(modifier = Modifier.height(16.dp)) // Add space between buttons
        Button(onClick = onClick2, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("Stop Server")
        }
        Spacer(modifier = Modifier.height(16.dp)) // Add space between buttons
        Button(onClick = onClick3, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("Start Server")
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    BLE_DummyTheme {
//        Greeting(
//            "Android",
//            modifier = TODO() ,
//            onClick = TODO(),
//            onClick2 = TODO(),
//            onClick3 = TODO(),
//        )
//    }
//}
