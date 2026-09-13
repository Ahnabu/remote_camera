package com.remotecamera.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.remotecamera.camera.auth.AuthManager
import com.remotecamera.camera.pairing.PairingRepository
import com.remotecamera.camera.service.CameraAgentService
import com.remotecamera.camera.ui.CameraPairingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authManager = AuthManager()
    private val pairingRepository = PairingRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.Main).launch {
            authManager.signInAnonymouslyIfNeeded()
        }

        val deviceId = pairingRepository.cameraDeviceId
        CameraAgentService.startService(this, deviceId)

        setContent {
            MaterialTheme {
                Surface {
                    CameraPairingScreen(
                        pairingRepository = pairingRepository,
                        onToggleServiceRequested = { enabled, id ->
                            if (enabled) {
                                CameraAgentService.startService(this, id)
                            } else {
                                CameraAgentService.stopService(this)
                            }
                        }
                    )
                }
            }
        }
    }
}
