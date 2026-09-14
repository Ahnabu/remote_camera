package com.remotecamera.camera.webrtc

import org.webrtc.PeerConnection
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

data class TurnConfig(
    val turnUrl: String = "turn:openrelay.metered.ca:80",
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
            "stun:stun.l.google.com:443",
            "stun:stun.services.mozilla.com:3478",
            "stun:global.stun.twilio.com:3478"
        )

        for (url in stunUrls) {
            iceServers.add(PeerConnection.IceServer.builder(url).createIceServer())
        }

        // TURN Fallback Servers for Cross-Network Traversal over Symmetric NAT / Cellular CGNAT
        if (config.secretKey.isNotBlank()) {
            val (username, credential) = generateEphemeralCredentials(config.secretKey, config.ttlSeconds)
            val turnServer = PeerConnection.IceServer.builder(config.turnUrl)
                .setUsername(username)
                .setPassword(credential)
                .createIceServer()
            iceServers.add(turnServer)
        } else {
            // Free OpenRelay TURN servers by Metered.ca for fallback relay
            val openRelayServers = listOf(
                "turn:openrelay.metered.ca:80",
                "turn:openrelay.metered.ca:443",
                "turns:openrelay.metered.ca:443?transport=tcp"
            )
            for (turnUrl in openRelayServers) {
                val turnServer = PeerConnection.IceServer.builder(turnUrl)
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer()
                iceServers.add(turnServer)
            }
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

