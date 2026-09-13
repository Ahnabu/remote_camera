package com.remotecamera.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.remotecamera.viewer.auth.AuthManager
import com.remotecamera.viewer.pairing.PairingRepository
import com.remotecamera.viewer.ui.ViewerPairingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var startupError by mutableStateOf<String?>(null)

    private val authManager by lazy { AuthManager() }
    private val pairingRepository by lazy { PairingRepository() }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    pairingRepository.processScannedPairingPayload(result.contents)
                } catch (e: Throwable) {
                    Log.e("ViewerMainActivity", "Pairing error: ${e.message}", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    authManager.signInAnonymouslyIfNeeded()
                } catch (e: Throwable) {
                    Log.e("ViewerMainActivity", "Auth error: ${e.message}", e)
                }
            }
        } catch (t: Throwable) {
            Log.e("ViewerMainActivity", "Startup error: ${t.message}", t)
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
                                "⚠️ Viewer Initialization Error",
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
                        ViewerPairingScreen(
                            pairingRepository = pairingRepository,
                            onOpenScannerRequested = {
                                try {
                                    val options = ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        setPrompt("Scan Realme Camera Agent QR Code")
                                        setCameraId(0)
                                        setBeepEnabled(true)
                                        setBarcodeImageEnabled(true)
                                    }
                                    barcodeLauncher.launch(options)
                                } catch (t: Throwable) {
                                    Log.e("ViewerMainActivity", "Scanner launch error", t)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
