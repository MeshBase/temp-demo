package com.example.mesh_base.ble

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mesh_base.global_interfaces.Device
import com.example.mesh_base.router.MeshProtocol
import com.example.mesh_base.router.Router
import com.example.mesh_base.router.SendMessageBody
import com.example.mesh_base.ui.theme.MeshBaseTheme
import java.util.UUID
import java.util.function.Function


@Composable
fun BleTestScreen(blePerm: BLEPermissions) {

    // Use your app’s theme if desired
    MeshBaseTheme {
        val context = LocalContext.current
        val id = remember { UUID.randomUUID() }
        val connectedDevices = remember { mutableStateListOf<Device>() }

        var message by remember { mutableStateOf("") }

        val bleHandler = BLEHandler(
            { connectedDevices.add(it) },
            { connectedDevices.remove(it) },
            { device ->
                Handler(Looper.getMainLooper()).post({
                    Toast.makeText(context, "Discovered: ${device.name}", Toast.LENGTH_SHORT).show()
                })
            },
            {
                Handler(Looper.getMainLooper()).post({
                    Toast.makeText(context, "disconnected", Toast.LENGTH_SHORT).show()
                })
            },
            { data, device ->
                Handler(Looper.getMainLooper()).post({
                    Toast.makeText(
                        context,
                        "Received from BLE(should not happen): ${
                            String(
                                data,
                                Charsets.UTF_8
                            )
                        } from ${device.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                })
            },
            {
                Handler(Looper.getMainLooper()).post({
                    Toast.makeText(
                        context,
                        "now have ${it.size} nearby devices",
                        Toast.LENGTH_SHORT
                    ).show()
                })
            },
            context,
            id
        )
        val router = Router(arrayListOf(bleHandler), id)

        LaunchedEffect(Unit) {
            blePerm.setListener(
                object : BLEPermissions.Listener {
                    override fun onEnabled() {
                        Toast.makeText(context, "BLE enabled", Toast.LENGTH_SHORT).show()
                        bleHandler.startCentral()
                        bleHandler.startPeripheral()
                    }

                    override fun onDisabled() {
                        Toast.makeText(context, "BLE disabled", Toast.LENGTH_SHORT).show()
                        bleHandler.stopCentral()
                        bleHandler.stopPeripheral()
                    }
                }
            )
            blePerm.enable()

            router.setOnReceivedData(
                { data, device ->

                    val bodyDecoder =
                        Function { d: ByteArray? -> SendMessageBody.decode(d) }
                    val protocol = MeshProtocol.decode(data, bodyDecoder)

                    Handler(Looper.getMainLooper()).post({
                        Toast.makeText(
                            context,
                            "Received from router: ${
                                protocol.body.msg
                            } from device with uuid=${protocol.sender} through neighbor=${device.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    })
                }
            )
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { blePerm.enable() }) {
                    Text("Enable BLE")
                }
                Button(onClick = { bleHandler.startCentral() }) {
                    Text("Start Central")
                }
                Button(onClick = { bleHandler.stopCentral() }) {
                    Text("Stop Central")
                }
                Button(onClick = { bleHandler.startPeripheral() }) {
                    Text("Start Peripheral")
                }
                Button(onClick = { bleHandler.stopPeripheral() }) {
                    Text("Stop Peripheral")
                }

                LazyColumn {
                    items(connectedDevices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${device.name ?: "Unknown"} (${device.uuid})",
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                router.sendData(message.toByteArray(Charsets.UTF_8), device.uuid)
                            }) {
                                Text("Send")
                            }
                        }
                    }
                }

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
