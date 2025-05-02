package com.example.mesh_base
// MainActivity.kt
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.mesh_base.crypt_handler.CryptTestScreen
import com.example.mesh_base.mesh_manager.MeshManager


//Remove because no longer used
 class MainActivity : ComponentActivity() {

    lateinit var meshManager: MeshManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        meshManager = MeshManager(this)

        setContent {
//            BleTestScreen(meshManager = meshManager)
            //TODO: comment CryptTestScreen() and uncomment BleTestScreen() back
            CryptTestScreen();
        }
    }
}
