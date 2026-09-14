package com.remotecamera.camera.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.remotecamera.camera.MainActivity
import com.remotecamera.camera.camerax.CameraManager
import com.remotecamera.camera.signaling.FirestoreSignalingClient
import kotlinx.coroutines.launch

import android.util.Log
import com.remotecamera.camera.webrtc.WebRTCManager
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SurfaceTextureHelper

class CameraAgentService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "camera_agent_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_CAMERA_DEVICE_ID = "EXTRA_CAMERA_DEVICE_ID"

        fun startService(context: Context, cameraDeviceId: String) {
            try {
                val intent = Intent(context, CameraAgentService::class.java).apply {
                    action = ACTION_START_SERVICE
                    putExtra(EXTRA_CAMERA_DEVICE_ID, cameraDeviceId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, CameraAgentService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private lateinit var cameraManager: CameraManager
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val signalingClient by lazy { FirestoreSignalingClient() }
    private val webrtcManager by lazy { WebRTCManager(this) }
    private var activeSessionId: String? = null
    private var cameraDeviceId: String = "Camera_Agent_${android.os.Build.MODEL.replace(" ", "_")}"

    override fun onCreate() {
        super.onCreate()
        cameraManager = CameraManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                cameraDeviceId = intent.getStringExtra(EXTRA_CAMERA_DEVICE_ID) ?: com.remotecamera.camera.pairing.PairingRepository.getPersistentDeviceId(this)
                startForegroundWithNotification()
                updateDeviceStatus("READY")
                observeSignalingEvents()
            }
            ACTION_STOP_SERVICE -> {
                updateDeviceStatus("OFFLINE")
                cameraManager.stopCamera()
                try {
                    webrtcManager.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, CameraAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Camera Agent")
            .setContentText("Remote Camera Service active.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Remote Mode", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    0
                }
                try {
                    startForeground(NOTIFICATION_ID, notification, serviceType)
                } catch (se: SecurityException) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Camera Service Channel",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent foreground notification for Remote Camera Agent."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateDeviceStatus(status: String) {
        lifecycleScope.launch {
            try {
                firestore.collection("devices").document(cameraDeviceId).set(
                    mapOf(
                        "deviceId" to cameraDeviceId,
                        "deviceName" to android.os.Build.MODEL,
                        "status" to status,
                        "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var lastHandledTorchState: Boolean? = null
    private var lastHandledSwitchCameraTimestamp: Long? = null
    private var lastHandledIceRestartTimestamp: Long? = null
    private var lastHandledPhotoBurstState: Boolean? = null

    private fun observeSignalingEvents() {
        com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Observing signaling events for device: $cameraDeviceId")
        lifecycleScope.launch {
            signalingClient.observeSessionEvents(cameraDeviceId).collect { session ->
                if (session.sessionId.isBlank()) return@collect

                com.remotecamera.camera.debug.DebugLogger.log(
                    "CameraAgent",
                    "Signaling update [${session.sessionId.takeLast(12)}]: status=${session.status}, lastCmd=${session.lastCommand}, torch=${session.torchEnabled}"
                )

                if (session.status == "INITIATED" && activeSessionId != session.sessionId) {
                    if (activeSessionId != null) {
                        try {
                            webrtcManager.closeSession()
                            kotlinx.coroutines.delay(500)
                        } catch (e: Exception) { }
                    }
                    activeSessionId = session.sessionId
                    lastHandledTorchState = null
                    lastHandledSwitchCameraTimestamp = null
                    lastHandledIceRestartTimestamp = null
                    lastHandledPhotoBurstState = null
                    com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "New session initiated: ${session.sessionId}", com.remotecamera.camera.debug.LogLevel.SUCCESS)
                    updateDeviceStatus("BUSY")
                    startWebRtcStreamingSession(session.sessionId)
                } else if (session.status == "STOPPED" && session.sessionId == activeSessionId) {
                    com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Active session stopped by viewer", com.remotecamera.camera.debug.LogLevel.WARNING)
                    updateDeviceStatus("READY")
                    activeSessionId = null
                    lastHandledTorchState = null
                    lastHandledSwitchCameraTimestamp = null
                    lastHandledIceRestartTimestamp = null
                    lastHandledPhotoBurstState = null
                    try {
                        webrtcManager.closeSession()
                    } catch (e: Exception) { }
                    return@collect
                } else if (session.sessionId != activeSessionId) {
                    // Ignore updates for old inactive sessions
                    return@collect
                } else if (session.status == "ANSWERED" && !session.answerSdp.isNullOrBlank()) {
                    if (webrtcManager.canSetRemoteAnswer()) {
                        com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Received SDP Answer from Viewer", com.remotecamera.camera.debug.LogLevel.SUCCESS)
                        webrtcManager.setRemoteAnswer(session.answerSdp)
                    } else {
                        com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Ignoring duplicate or redundant SDP Answer event (signaling state not HAVE_LOCAL_OFFER)", com.remotecamera.camera.debug.LogLevel.WARNING)
                    }
                }

                // Process Remote Command: Torch Light Toggle
                if (session.torchEnabled != null && session.torchEnabled != lastHandledTorchState) {
                    lastHandledTorchState = session.torchEnabled
                    cameraManager.setTorchEnabled(session.torchEnabled)
                    com.remotecamera.camera.debug.DebugLogger.log(
                        "CameraAgent",
                        "🔦 Remote Command Executed: Torch set to ${session.torchEnabled}",
                        com.remotecamera.camera.debug.LogLevel.SUCCESS
                    )
                }

                // Process Remote Command: Switch Camera Lens
                if (session.switchCameraRequested != null && session.switchCameraRequested != lastHandledSwitchCameraTimestamp) {
                    lastHandledSwitchCameraTimestamp = session.switchCameraRequested
                    webrtcManager.switchCamera()
                    com.remotecamera.camera.debug.DebugLogger.log(
                        "CameraAgent",
                        "📷 Remote Command Executed: Switch Camera Lens",
                        com.remotecamera.camera.debug.LogLevel.SUCCESS
                    )
                }

                // Process Remote Command: ICE Restart
                if (session.iceRestartRequested != null && session.iceRestartRequested != lastHandledIceRestartTimestamp) {
                    lastHandledIceRestartTimestamp = session.iceRestartRequested
                    com.remotecamera.camera.debug.DebugLogger.log(
                        "CameraAgent",
                        "🔄 Remote Command Executed: ICE Restart Requested",
                        com.remotecamera.camera.debug.LogLevel.WARNING
                    )
                    startWebRtcStreamingSession(session.sessionId)
                }

                // Process Remote Command: Photo Burst 10 FPS
                if (session.photoBurstActive != null && session.photoBurstActive != lastHandledPhotoBurstState) {
                    lastHandledPhotoBurstState = session.photoBurstActive
                    val statusText = if (session.photoBurstActive) "STARTED" else "STOPPED"
                    com.remotecamera.camera.debug.DebugLogger.log(
                        "CameraAgent",
                        "📸 Viewer $statusText 10 FPS Photo Burst Capture",
                        if (session.photoBurstActive) com.remotecamera.camera.debug.LogLevel.SUCCESS else com.remotecamera.camera.debug.LogLevel.WARNING
                    )
                }
            }
        }
    }

    private fun startWebRtcStreamingSession(sessionId: String) {
        try {
            com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Starting WebRTC streaming session: $sessionId")
            val iceServers = com.remotecamera.camera.webrtc.TurnServerManager().getIceServers()

            webrtcManager.createPeerConnection(iceServers) { candidate ->
                lifecycleScope.launch {
                    try {
                        signalingClient.sendIceCandidate(sessionId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                        com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Sent ICE candidate to viewer")
                    } catch (e: Exception) {
                        com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Error sending ICE candidate: ${e.message}", com.remotecamera.camera.debug.LogLevel.ERROR)
                    }
                }
            }

            // Create video capturer and attach to WebRTC stream
            val capturer = webrtcManager.createCameraCapturer(this)
            if (capturer != null) {
                val surfaceTextureHelper = SurfaceTextureHelper.create("CameraCapturerThread", webrtcManager.rootEglBase.eglBaseContext)
                webrtcManager.attachLocalVideoSource(surfaceTextureHelper, capturer)
                com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Camera capturer attached successfully")
            }

            // Generate SDP Offer and post to Firestore
            webrtcManager.createOffer { offerSdp ->
                lifecycleScope.launch {
                    try {
                        signalingClient.sendOffer(sessionId, offerSdp.description)
                        com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Posted SDP Offer to Firestore", com.remotecamera.camera.debug.LogLevel.SUCCESS)
                    } catch (e: Exception) {
                        com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Error sending SDP offer: ${e.message}", com.remotecamera.camera.debug.LogLevel.ERROR)
                    }
                }
            }

            // Observe Viewer ICE candidates
            lifecycleScope.launch {
                signalingClient.observeViewerIceCandidates(sessionId).collect { candidateRecord ->
                    val iceCandidate = IceCandidate(candidateRecord.sdpMid, candidateRecord.sdpMLineIndex, candidateRecord.sdp)
                    webrtcManager.addRemoteIceCandidate(iceCandidate)
                    com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Received Viewer ICE Candidate")
                }
            }
        } catch (e: Exception) {
            com.remotecamera.camera.debug.DebugLogger.log("CameraAgent", "Error starting WebRTC session: ${e.message}", com.remotecamera.camera.debug.LogLevel.ERROR)
        }
    }

    override fun onDestroy() {
        cameraManager.shutdown()
        try {
            webrtcManager.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateDeviceStatus("OFFLINE")
        super.onDestroy()
    }
}
