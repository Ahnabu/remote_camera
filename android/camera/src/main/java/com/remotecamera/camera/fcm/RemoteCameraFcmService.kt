package com.remotecamera.camera.fcm

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RemoteCameraFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("[FCM] New token generated for Camera device: $token")
        // Store FCM token in Firestore for recovery notifications
        FirebaseFirestore.getInstance().collection("devices").document("Realme_C55_Agent")
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        println("[FCM] Notification received: ${message.data}")
    }
}
