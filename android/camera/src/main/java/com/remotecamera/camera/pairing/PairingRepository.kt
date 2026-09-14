package com.remotecamera.camera.pairing

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.remotecamera.camera.crypto.CryptoManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class PairingRepository(
    private val context: Context? = null,
    private val customFirestore: FirebaseFirestore? = null,
    private val customCryptoManager: CryptoManager? = null
) {
    private val firestore: FirebaseFirestore
        get() = customFirestore ?: FirebaseFirestore.getInstance()

    private val cryptoManager: CryptoManager
        get() = customCryptoManager ?: CryptoManager()

    val cameraDeviceId: String
        get() = getPersistentDeviceId(context)

    companion object {
        private var memoryDeviceId: String? = null

        @Synchronized
        fun getPersistentDeviceId(context: Context? = null): String {
            if (context != null) {
                val prefs = context.getSharedPreferences("remote_camera_prefs", Context.MODE_PRIVATE)
                var id = prefs.getString("camera_device_id", null)
                if (id.isNullOrBlank()) {
                    val androidId = try {
                        android.provider.Settings.Secure.getString(
                            context.contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        )
                    } catch (e: Exception) { null }

                    val hash = if (!androidId.isNullOrBlank()) {
                        kotlin.math.abs(androidId.hashCode()).toString(16).padStart(8, '0').take(8)
                    } else {
                        UUID.randomUUID().toString().take(8)
                    }
                    id = "Camera_Agent_$hash"
                    prefs.edit().putString("camera_device_id", id).apply()
                }
                memoryDeviceId = id
                return id
            }
            if (memoryDeviceId == null) {
                memoryDeviceId = "Camera_Agent_${android.os.Build.MODEL.replace(" ", "_")}"
            }
            return memoryDeviceId!!
        }
    }

    /**
     * Generates a signed QR pairing payload for this device.
     */
    fun createPairingPayload(): PairingPayload {
        val publicKeyBase64 = cryptoManager.getPublicKeyBase64()
        val challengeNonce = UUID.randomUUID().toString()
        val signatureBase64 = cryptoManager.signData(challengeNonce.toByteArray(Charsets.UTF_8))

        return PairingPayload(
            cameraDeviceId = cameraDeviceId,
            cameraPublicKey = publicKeyBase64,
            challenge = challengeNonce,
            signature = signatureBase64
        )
    }

    /**
     * Observes Firestore for active pairing requests targeting this Camera device.
     */
    fun observeActivePairings(): Flow<List<DevicePairingRecord>> = callbackFlow {
        val listener = firestore.collection("pairings")
            .whereEqualTo("cameraDeviceId", cameraDeviceId)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val pairings = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(DevicePairingRecord::class.java)
                } ?: emptyList()
                trySend(pairings)
            }
        awaitClose { listener.remove() }
    }
}
