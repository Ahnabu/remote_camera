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
import com.remotecamera.viewer.signaling.FirestoreSignalingClient
import com.remotecamera.viewer.ui.StreamViewerScreen
import com.remotecamera.viewer.ui.ViewerPairingScreen
import com.remotecamera.viewer.webrtc.WebRTCManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

class MainActivity : ComponentActivity() {

    private var startupError by mutableStateOf<String?>(null)

    private val authManager by lazy { AuthManager() }
    private val pairingRepository by lazy { PairingRepository() }
    private val signalingClient by lazy { FirestoreSignalingClient() }

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
                    var activeConnectedCameraId by remember { mutableStateOf<String?>(null) }

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
                    } else if (activeConnectedCameraId != null) {
                        val cameraDeviceId = activeConnectedCameraId!!
                        val webRTCManager = remember { WebRTCManager(this@MainActivity) }
                        val scope = rememberCoroutineScope()

                        DisposableEffect(cameraDeviceId) {
                            var sessionId: String? = null

                            scope.launch {
                                try {
                                    sessionId = signalingClient.initiateSession(cameraDeviceId, pairingRepository.viewerDeviceId)
                                    val currentSessionId = sessionId ?: return@launch

                                    val iceServers = listOf(
                                        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                                        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
                                    )

                                    webRTCManager.createPeerConnection(
                                        stunTurnServers = iceServers,
                                        onIceCandidateGenerated = { candidate ->
                                            scope.launch {
                                                signalingClient.sendIceCandidate(currentSessionId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                                            }
                                        },
                                        onRemoteVideoTrackReceived = { track ->
                                            Log.d("ViewerMainActivity", "Remote video track received!")
                                        }
                                    )

                                    // Listen for SDP Offer from Camera Agent
                                    scope.launch {
                                        signalingClient.observeSession(currentSessionId).collect { session ->
                                            if (!session.offerSdp.isNullOrBlank() && session.status == "OFFERED") {
                                                webRTCManager.setRemoteOfferAndCreateAnswer(session.offerSdp) { answerDesc ->
                                                    scope.launch {
                                                        signalingClient.sendAnswer(currentSessionId, answerDesc.description)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Listen for Camera ICE Candidates
                                    scope.launch {
                                        signalingClient.observeCameraIceCandidates(currentSessionId).collect { candidateRecord ->
                                            val iceCandidate = IceCandidate(candidateRecord.sdpMid, candidateRecord.sdpMLineIndex, candidateRecord.sdp)
                                            webRTCManager.addRemoteIceCandidate(iceCandidate)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ViewerMainActivity", "Signaling session error", e)
                                }
                            }

                            onDispose {
                                webRTCManager.close()
                            }
                        }

                        StreamViewerScreen(
                            cameraDeviceId = cameraDeviceId,
                            webRTCManager = webRTCManager,
                            onStopStreamRequested = {
                                activeConnectedCameraId = null
                            },
                            onIceRestartRequested = {
                                // Ice restart if needed
                            },
                            onTorchToggleRequested = { _ -> },
                            onSwitchCameraRequested = { },
                            onQualitySelected = { }
                        )
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
                            },
                            onConnectRequested = { targetCameraDeviceId ->
                                activeConnectedCameraId = targetCameraDeviceId
                            }
                        )
                    }
                }
            }
        }
    }
}
