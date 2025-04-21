package com.example.mesh_base.ble

import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.example.mesh_base.global_interfaces.ConnectionHandlersEnum
import com.example.mesh_base.global_interfaces.Device
import com.example.mesh_base.global_interfaces.SendError
import com.example.mesh_base.mesh_manager.MeshManager
import com.example.mesh_base.mesh_manager.MeshManagerListener
import com.example.mesh_base.mesh_manager.Status
import com.example.mesh_base.router.ConcreteMeshProtocol
import com.example.mesh_base.router.MeshProtocol
import com.example.mesh_base.router.SendListener
import com.example.mesh_base.router.SendMessageBody
import com.example.mesh_base.ui.theme.MeshBaseTheme
import java.util.UUID
import java.util.function.Function


@Composable
fun BleTestScreen(meshManager: MeshManager) {

    MeshBaseTheme {
        val TAG = "my_ble_screen"
        val context = LocalContext.current
        val connectedDevices = remember { mutableStateListOf<Device>() }
        var isOn by remember { mutableStateOf(false) }
        var bleIsOn by remember { mutableStateOf(false) }
        var wifiDirectIsOn by remember { mutableStateOf(false) }
        val id = meshManager.id.toString()


        var message by remember { mutableStateOf("") }


        LaunchedEffect(Unit) {

            val listener = object : MeshManagerListener() {
                override fun onDataReceivedForSelf(data: ByteArray) {
                    Log.d(TAG, "received data")
                    val bodyDecoder =
                        Function { d: ByteArray? -> SendMessageBody.decode(d) }
                    val protocol = MeshProtocol.decode(data, bodyDecoder)

                    Handler(Looper.getMainLooper()).post({
                        Toast.makeText(
                            context,
                            "Received: ${
                                protocol.body.msg
                            } \nfrom device with uuid=${protocol.sender}",
                            Toast.LENGTH_SHORT
                        ).show()
                    })
                }

                override fun onStatusChange(status: Status) {
                    isOn = status.isOn
                    bleIsOn =
                        status.connectionStatuses[ConnectionHandlersEnum.BLE]?.isOn == true
                    wifiDirectIsOn =
                        status.connectionStatuses[ConnectionHandlersEnum.WifiDirect]?.isOn == true
                }

                override fun onNeighborConnected(device: Device) {
                    connectedDevices.add(device)
                }

                override fun onNeighborDisconnected(device: Device?) {
                    connectedDevices.remove(device)
                }

                override fun onError(e: Exception) {
                    Handler(Looper.getMainLooper()).post({
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
                    })
                }

            }

            meshManager.setListener(listener)
            meshManager.on()
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("BleIsOn=$bleIsOn WifiDirectIsOn=$wifiDirectIsOn UUID=$id")

                Button(onClick = {
                    if (isOn) meshManager.off()
                    else meshManager.on()
                }) {
                    Text(if (isOn) "Turn Off" else "Turn On")
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

                                val protocol: MeshProtocol<SendMessageBody> =
                                    ConcreteMeshProtocol<SendMessageBody>(
                                        1,
                                        4,
                                        113,
                                        UUID.fromString("dd91a1c8-5f6a-4430-815f-f3e1c8780fc8"),
                                        SendMessageBody(
                                            4,
                                            false,
                                            device.uuid,
                                            message,
                                        )
                                    )

                                val listener = object : SendListener {
                                    override fun onError(error: SendError) {
                                        Handler(Looper.getMainLooper()).post({
                                            Toast.makeText(
                                                context,
                                                "Send error: ${protocol.body.msg} ${error.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        })
                                    }

                                    override fun onAck() {

                                        Handler(Looper.getMainLooper()).post({
                                            Toast.makeText(
                                                context,
                                                "Ack received for message=${protocol.body.msg}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        })
                                    }

                                    override fun onResponse(protocol: MeshProtocol<*>) {
                                        //No response expected for SendMessageBody
                                        Handler(Looper.getMainLooper()).post({
                                            Toast.makeText(
                                                context,
                                                "unexpected response received",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        })
                                    }
                                }

                                meshManager.send(protocol, listener)
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
