package com.remotecamera.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.remotecamera.camera.auth.AuthManager
import com.remotecamera.camera.pairing.PairingRepository
import com.remotecamera.camera.service.CameraAgentService
import com.remotecamera.camera.ui.CameraPairingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var startupError by mutableStateOf<String?>(null)

    private val authManager by lazy { AuthManager() }
    private val pairingRepository by lazy { PairingRepository() }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startCameraServiceSafely()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    authManager.signInAnonymouslyIfNeeded()
                } catch (e: Throwable) {
                    Log.e("MainActivity", "Auth error: ${e.message}", e)
                }
            }

            checkAndRequestPermissions()
        } catch (t: Throwable) {
            Log.e("MainActivity", "Startup error: ${t.message}", t)
            startupError = t.stackTraceToString()
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (startupError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "⚠️ Camera Agent Initialization Error",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                                Text(
                                    text = startupError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Green,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
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

