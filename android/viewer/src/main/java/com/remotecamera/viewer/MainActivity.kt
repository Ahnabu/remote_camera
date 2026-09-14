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
    private val pairingRepository by lazy { PairingRepository(this) }
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

                        var activeSessionId by remember { mutableStateOf<String?>(null) }
                        var isPhotoBurstActive by remember { mutableStateOf(false) }
                        var savedPhotoCount by remember { mutableStateOf(0) }

                        DisposableEffect(cameraDeviceId) {
                            scope.launch {
                                try {
                                    com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Initiating signaling session with camera: $cameraDeviceId")
                                    val sid = signalingClient.initiateSession(cameraDeviceId, pairingRepository.viewerDeviceId)
                                    activeSessionId = sid
                                    val currentSessionId = sid

                                    val iceServers = com.remotecamera.viewer.webrtc.TurnServerManager().getIceServers()

                                    webRTCManager.createPeerConnection(
                                        stunTurnServers = iceServers,
                                        onIceCandidateGenerated = { candidate ->
                                            scope.launch {
                                                signalingClient.sendIceCandidate(currentSessionId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                                                com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Sent ICE candidate to camera")
                                            }
                                        },
                                        onRemoteVideoTrackReceived = { track ->
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "🎥 Remote video track received!", com.remotecamera.viewer.debug.LogLevel.SUCCESS)
                                        }
                                    )

                                    // Listen for SDP Offer from Camera Agent
                                    scope.launch {
                                        signalingClient.observeSession(currentSessionId).collect { session ->
                                            if (!session.offerSdp.isNullOrBlank() && session.status == "OFFERED") {
                                                com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Received SDP Offer from camera agent", com.remotecamera.viewer.debug.LogLevel.SUCCESS)
                                                webRTCManager.setRemoteOfferAndCreateAnswer(session.offerSdp) { answerDesc ->
                                                    scope.launch {
                                                        signalingClient.sendAnswer(currentSessionId, answerDesc.description)
                                                        com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Sent SDP Answer to camera agent", com.remotecamera.viewer.debug.LogLevel.SUCCESS)
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
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Received Camera ICE Candidate")
                                        }
                                    }
                                } catch (e: Exception) {
                                    com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Signaling session error: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
                                }
                            }

                            onDispose {
                                webRTCManager.stop10FpsPhotoCapture()
                                activeSessionId?.let { sid ->
                                    CoroutineScope(Dispatchers.IO).launch {
                                        signalingClient.stopSession(sid)
                                    }
                                }
                                webRTCManager.close()
                            }
                        }

                        StreamViewerScreen(
                            cameraDeviceId = cameraDeviceId,
                            webRTCManager = webRTCManager,
                            isPhotoBurstActive = isPhotoBurstActive,
                            savedPhotoCount = savedPhotoCount,
                            onStopStreamRequested = {
                                com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Stream stop requested")
                                webRTCManager.stop10FpsPhotoCapture()
                                activeSessionId?.let { sid ->
                                    scope.launch {
                                        signalingClient.stopSession(sid)
                                    }
                                }
                                activeConnectedCameraId = null
                            },
                            onIceRestartRequested = {
                                activeSessionId?.let { sid ->
                                    scope.launch {
                                        try {
                                            signalingClient.sendIceRestartCommand(sid)
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Sent ICE Restart command to Camera", com.remotecamera.viewer.debug.LogLevel.WARNING)
                                        } catch (e: Exception) {
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Failed to send ICE restart: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
                                        }
                                    }
                                }
                            },
                            onTorchToggleRequested = { enabled ->
                                activeSessionId?.let { sid ->
                                    scope.launch {
                                        try {
                                            signalingClient.sendTorchCommand(sid, enabled)
                                            com.remotecamera.viewer.debug.DebugLogger.log(
                                                "Viewer",
                                                "🔦 Sent Torch command ($enabled) to Camera",
                                                com.remotecamera.viewer.debug.LogLevel.SUCCESS
                                            )
                                        } catch (e: Exception) {
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Failed to send torch command: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
                                        }
                                    }
                                }
                            },
                            onSwitchCameraRequested = {
                                activeSessionId?.let { sid ->
                                    scope.launch {
                                        try {
                                            signalingClient.sendSwitchCameraCommand(sid)
                                            com.remotecamera.viewer.debug.DebugLogger.log(
                                                "Viewer",
                                                "📷 Sent Switch Camera command to Camera",
                                                com.remotecamera.viewer.debug.LogLevel.SUCCESS
                                            )
                                        } catch (e: Exception) {
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Failed to send switch camera command: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
                                        }
                                    }
                                }
                            },
                            onQualitySelected = { profile ->
                                activeSessionId?.let { sid ->
                                    scope.launch {
                                        try {
                                            signalingClient.sendQualityProfileCommand(sid, profile.label)
                                            com.remotecamera.viewer.debug.DebugLogger.log(
                                                "Viewer",
                                                "⚙️ Sent Quality command (${profile.label}) to Camera",
                                                com.remotecamera.viewer.debug.LogLevel.SUCCESS
                                            )
                                        } catch (e: Exception) {
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Failed to send quality command: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
                                        }
                                    }
                                }
                            },
                            onPhotoBurstToggleRequested = { active ->
                                isPhotoBurstActive = active
                                if (active) {
                                    savedPhotoCount = 0
                                    webRTCManager.start10FpsPhotoCapture(cameraDeviceId) { count ->
                                        savedPhotoCount = count
                                    }
                                    com.remotecamera.viewer.debug.DebugLogger.log(
                                        "Viewer",
                                        "📸 Started 10 FPS photo capture into Pictures/RemoteCamera_$cameraDeviceId",
                                        com.remotecamera.viewer.debug.LogLevel.SUCCESS
                                    )
                                } else {
                                    webRTCManager.stop10FpsPhotoCapture()
                                    com.remotecamera.viewer.debug.DebugLogger.log(
                                        "Viewer",
                                        "🛑 Stopped 10 FPS photo capture (Total saved: $savedPhotoCount photos)",
                                        com.remotecamera.viewer.debug.LogLevel.WARNING
                                    )
                                }
                                activeSessionId?.let { sid ->
                                    scope.launch {
                                        try {
                                            signalingClient.sendPhotoBurstCommand(sid, active)
                                        } catch (e: Exception) {
                                            com.remotecamera.viewer.debug.DebugLogger.log("Viewer", "Failed to send photo burst command: ${e.message}", com.remotecamera.viewer.debug.LogLevel.ERROR)
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        ViewerPairingScreen(
                            pairingRepository = pairingRepository,
                            onOpenScannerRequested = {
                                try {
                                    val options = ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        setPrompt("Scan Camera Agent QR Code")
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
