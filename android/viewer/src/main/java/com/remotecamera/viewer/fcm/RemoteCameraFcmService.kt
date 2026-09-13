package com.remotecamera.viewer.fcm

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RemoteCameraFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("[FCM] New token generated for Viewer device: $token")
        FirebaseFirestore.getInstance().collection("devices").document("Galaxy_S20_Viewer")
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        println("[FCM] Notification received: ${message.data}")
    }
}
