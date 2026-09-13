package com.remotecamera.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.remotecamera.viewer.auth.AuthManager
import com.remotecamera.viewer.pairing.PairingRepository
import com.remotecamera.viewer.ui.ViewerPairingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authManager = AuthManager()
    private val pairingRepository = PairingRepository()

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            CoroutineScope(Dispatchers.Main).launch {
                pairingRepository.processScannedPairingPayload(result.contents)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.Main).launch {
            authManager.signInAnonymouslyIfNeeded()
        }

        setContent {
            MaterialTheme {
                Surface {
                    ViewerPairingScreen(
                        pairingRepository = pairingRepository,
                        onOpenScannerRequested = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan Realme Camera Agent QR Code")
                                setCameraId(0)
                                setBeepEnabled(true)
                                setBarcodeImageEnabled(true)
                            }
                            barcodeLauncher.launch(options)
                        }
                    )
                }
            }
        }
    }
}
