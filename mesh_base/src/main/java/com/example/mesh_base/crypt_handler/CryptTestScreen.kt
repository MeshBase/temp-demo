package com.example.mesh_base.crypt_handler

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
import java.util.function.Function


@Composable
fun CryptTestScreen() {

    MeshBaseTheme {
        val TAG = "my_crypt_screen"

        val cryptHandler = CryptHandler();
        cryptHandler.dummyMethod();
        Log.d(TAG, "my print statement inside the screen");

        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Hello crypt")
            }
        }
    }
}
