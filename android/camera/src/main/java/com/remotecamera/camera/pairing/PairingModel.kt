package com.remotecamera.camera.pairing

import com.google.gson.Gson

data class PairingPayload(
    val cameraDeviceId: String,
    val cameraPublicKey: String,
    val challenge: String,
    val signature: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): PairingPayload? {
            return try {
                Gson().fromJson(json, PairingPayload::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class DevicePairingRecord(
    val cameraDeviceId: String = "",
    val viewerDeviceId: String = "",
    val cameraPublicKey: String = "",
    val viewerPublicKey: String = "",
    val status: String = "ACTIVE", // ACTIVE, REVOKED
    val pairedAt: Long = System.currentTimeMillis()
)
