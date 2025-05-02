package com.example.meshdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.mesh_base.crypt_handler.CryptTestScreen
import com.example.mesh_base.mesh_manager.MeshManager
import com.example.meshdemo.ui.theme.BLE_DummyTheme
import com.example.mesh_base.ble.BleTestScreen


class MainActivity : ComponentActivity() {

    lateinit var meshManager: MeshManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        meshManager = MeshManager(this)

        setContent {
            BleTestScreen(meshManager = meshManager)
            //TODO: comment CryptTestScreen() and uncomment BleTestScreen() back
//            CryptTestScreen();
        }
    }
}
