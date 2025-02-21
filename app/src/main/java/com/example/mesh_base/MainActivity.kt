package com.example.mesh_base
// MainActivity.kt
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.example.mesh_base.ble.BLEHandler
import com.example.mesh_base.ble.BLEPermissions
import com.example.mesh_base.ble.BleTestScreen
import com.example.mesh_base.ui.theme.MeshBaseTheme
import java.util.UUID


class MainActivity : ComponentActivity() {

    lateinit var blePerm: BLEPermissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLEHandler and BLEPermissions BEFORE setContent.
        blePerm = BLEPermissions(this);

        setContent {
            BleTestScreen(blePerm = blePerm)
        }
    }
}
