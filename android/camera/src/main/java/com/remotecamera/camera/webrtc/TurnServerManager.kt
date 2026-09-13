package com.remotecamera.camera.webrtc

import org.webrtc.PeerConnection
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

data class TurnConfig(
    val turnUrl: String = "turn:turn.remotecamera.com:3478",
    val secretKey: String = "",
    val ttlSeconds: Long = 3600,
    val forceRelayMode: Boolean = false
)

class TurnServerManager(private val config: TurnConfig = TurnConfig()) {

    fun getIceServers(): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()

        // Public STUN Servers for WAN NAT Traversal (Cellular 4G/5G <-> Wi-Fi)
        val stunUrls = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
            "stun:stun.services.mozilla.com:3478",
            "stun:global.stun.twilio.com:3478"
        )

        for (url in stunUrls) {
            iceServers.add(PeerConnection.IceServer.builder(url).createIceServer())
        }

        // TURN Server (Coturn) with Ephemeral Credentials or Static Auth
        if (config.secretKey.isNotBlank()) {
            val (username, credential) = generateEphemeralCredentials(config.secretKey, config.ttlSeconds)
            val turnServer = PeerConnection.IceServer.builder(config.turnUrl)
                .setUsername(username)
                .setPassword(credential)
                .createIceServer()
            iceServers.add(turnServer)
        } else if (config.turnUrl.isNotBlank() && !config.turnUrl.contains("remotecamera.com")) {
            val turnServer = PeerConnection.IceServer.builder(config.turnUrl)
                .setUsername("guest")
                .setPassword("guest_password")
                .createIceServer()
            iceServers.add(turnServer)
        }

        return iceServers
    }

    /**
     * Generates standard Coturn HMAC-SHA1 REST API ephemeral username and password.
     */
    private fun generateEphemeralCredentials(secret: String, ttlSeconds: Long): Pair<String, String> {
        val expiryTimestamp = (System.currentTimeMillis() / 1000) + ttlSeconds
        val username = "$expiryTimestamp:remotecamera_user"

        return try {
            val mac = Mac.getInstance("HmacSHA1")
            val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1")
            mac.init(secretKeySpec)
            val rawHmac = mac.doFinal(username.toByteArray(Charsets.UTF_8))
            val credential = Base64.encodeToString(rawHmac, Base64.NO_WRAP)
            username to credential
        } catch (e: Exception) {
            username to secret
        }
    }
}
