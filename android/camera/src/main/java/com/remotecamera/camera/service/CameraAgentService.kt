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
    private val firestore = FirebaseFirestore.getInstance()
    private val signalingClient = FirestoreSignalingClient()
    private var cameraDeviceId: String = "Realme_C55_Agent"

    override fun onCreate() {
        super.onCreate()
        cameraManager = CameraManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                cameraDeviceId = intent.getStringExtra(EXTRA_CAMERA_DEVICE_ID) ?: cameraDeviceId
                startForegroundWithNotification()
                updateDeviceStatus("READY")
                observeSignalingEvents()
            }
            ACTION_STOP_SERVICE -> {
                updateDeviceStatus("OFFLINE")
                cameraManager.stopCamera()
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
            .setContentTitle("Realme Remote Camera — READY")
            .setContentText("Remote Camera Agent is active and standing by for Galaxy S20.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Turn Off Remote Mode", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows ongoing notification when Remote Camera Agent is active."
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
                        "deviceName" to "Realme C55",
                        "status" to status,
                        "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeSignalingEvents() {
        lifecycleScope.launch {
            signalingClient.observeSessionEvents(cameraDeviceId).collect { session ->
                if (session.status == "INITIATED") {
                    updateDeviceStatus("BUSY")
                    // Start CameraX preview for WebRTC capture
                    cameraManager.startCamera(this@CameraAgentService)
                } else if (session.status == "STOPPED") {
                    updateDeviceStatus("READY")
                    cameraManager.stopCamera()
                }
            }
        }
    }

    override fun onDestroy() {
        cameraManager.shutdown()
        updateDeviceStatus("OFFLINE")
        super.onDestroy()
    }
}
