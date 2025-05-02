package com.example.mesh_base.crypt_handler

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mesh_base.ui.theme.MeshBaseTheme
import android.util.Base64 // Import for Base64 encoding

@Composable
fun CryptTestScreen() {
    MeshBaseTheme {
        val tag = "my_crypt_screen"
        val cryptHandler = CryptHandler()

        // 1.  Call CryptHandler methods and Log the output
        cryptHandler.dummyMethod()
        Log.d(tag, "My print statement inside the screen")

        // Generate Key Pair
        cryptHandler.generateKeyPair()
        val publicKey = cryptHandler.getPublicKey()
        val privateKey = cryptHandler.getPrivateKey()

        if (publicKey != null && privateKey != null) {
            Log.d(tag, "Public Key: ${Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)}")
            Log.d(tag, "Private Key: ${Base64.encodeToString(privateKey.encoded, Base64.DEFAULT)}")

            // Fingerprint
            val fingerprint = cryptHandler.fingerprintPublicKey(publicKey)
            Log.d(tag, "Fingerprint: $fingerprint")

            // Test byte conversion
            val publicKeyBytes = publicKey.encoded
            val recoveredPublicKey = cryptHandler.bytesToPublicKey(publicKeyBytes)
            if (recoveredPublicKey != null) {
                val fingerprint2 = cryptHandler.fingerprintPublicKey(recoveredPublicKey)
                Log.d(tag, "Fingerprint from bytes: $fingerprint2")

                val isValid = cryptHandler.validateFingerprint(publicKey, fingerprint)
                Log.d(tag, "Is fingerprint valid: $isValid")

                val isValid2 = cryptHandler.validateFingerprint(publicKeyBytes, fingerprint)
                Log.d(tag, "Is fingerprint from bytes valid: $isValid2")

                // Encryption/Decryption
                val plaintext = "This is a secret message"
                val ciphertext = cryptHandler.encrypt(plaintext, publicKey)
                if (ciphertext != null) {
                    Log.d(tag, "Ciphertext: $ciphertext")
                    val decryptedText = cryptHandler.decrypt(ciphertext, privateKey)
                    Log.d(tag, "Decrypted Text: $decryptedText")

                    val ciphertext2 = cryptHandler.encrypt(plaintext, publicKeyBytes)
                    if (ciphertext2 != null) {
                        val decryptedText2 = cryptHandler.decrypt(ciphertext2, privateKey?.encoded ?: byteArrayOf())
                        Log.d(tag, "Decrypted Text from bytes: $decryptedText2")
                    }
                }


                // UUID Conversion
                val uuid = cryptHandler.fingerprintPublicKey(publicKey)
                Log.d(tag, "UUID: $uuid")
            }


        } else {
            Log.e(tag, "Key pair generation failed")
        }

        // 2. UI Layout
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

