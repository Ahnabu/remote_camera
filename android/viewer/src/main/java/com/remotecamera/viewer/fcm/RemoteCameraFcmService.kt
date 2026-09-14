package com.remotecamera.viewer.fcm

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RemoteCameraFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val deviceId = com.remotecamera.viewer.pairing.PairingRepository.getPersistentDeviceId(this)
        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        println("[FCM] Notification received: ${message.data}")
    }
}
