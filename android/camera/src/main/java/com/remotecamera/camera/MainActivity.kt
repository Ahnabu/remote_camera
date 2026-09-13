package com.remotecamera.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.remotecamera.camera.auth.AuthManager
import com.remotecamera.camera.pairing.PairingRepository
import com.remotecamera.camera.service.CameraAgentService
import com.remotecamera.camera.ui.CameraPairingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authManager by lazy { AuthManager() }
    private val pairingRepository by lazy { PairingRepository() }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startCameraServiceSafely()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                authManager.signInAnonymouslyIfNeeded()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        checkAndRequestPermissions()

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

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startCameraServiceSafely()
        }
    }

    private fun startCameraServiceSafely() {
        try {
            val deviceId = pairingRepository.cameraDeviceId
            CameraAgentService.startService(this, deviceId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

