package com.example.mesh_base
// MainActivity.kt
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.mesh_base.ble.BLEPermissions
import com.example.mesh_base.ble.BleTestScreen


class MainActivity : ComponentActivity() {

    lateinit var blePerm: BLEPermissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLEHandler and BLEPermissions BEFORE setContent.
        blePerm = BLEPermissions(this)

        setContent {
            BleTestScreen(activity = this)
        }
    }
}
