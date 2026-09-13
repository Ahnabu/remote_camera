package com.remotecamera.viewer.pairing

import com.google.firebase.firestore.FirebaseFirestore
import com.remotecamera.viewer.crypto.CryptoManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PairingRepository(
    private val customFirestore: FirebaseFirestore? = null,
    private val customCryptoManager: CryptoManager? = null
) {
    private val firestore: FirebaseFirestore
        get() = customFirestore ?: FirebaseFirestore.getInstance()

    private val cryptoManager: CryptoManager
        get() = customCryptoManager ?: CryptoManager()

    val viewerDeviceId: String = "Galaxy_S20_${UUID.randomUUID().toString().take(8)}"

    /**
     * Verifies scanned camera QR code payload signature and creates a Firestore pairing entry.
     */
    suspend fun processScannedPairingPayload(payloadJson: String): Result<DevicePairingRecord> {
        return try {
            val payload = PairingPayload.fromJson(payloadJson)
                ?: return Result.failure(IllegalArgumentException("Invalid QR Code payload format"))

            // Verify Camera Signature locally using camera's public key
            val isSignatureValid = cryptoManager.verifySignature(
                data = payload.challenge.toByteArray(Charsets.UTF_8),
                signatureBase64 = payload.signature,
                publicKeyBase64 = payload.cameraPublicKey
            )

            if (!isSignatureValid) {
                return Result.failure(SecurityException("Cryptographic verification failed: Camera signature invalid!"))
            }

            val viewerPublicKeyBase64 = cryptoManager.getPublicKeyBase64()
            val pairingDocId = "${payload.cameraDeviceId}_$viewerDeviceId"

            val record = DevicePairingRecord(
                cameraDeviceId = payload.cameraDeviceId,
                viewerDeviceId = viewerDeviceId,
                cameraPublicKey = payload.cameraPublicKey,
                viewerPublicKey = viewerPublicKeyBase64,
                status = "ACTIVE",
                pairedAt = System.currentTimeMillis()
            )

            firestore.collection("pairings").document(pairingDocId).set(record).await()
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observes Firestore for active camera pairings registered to this Viewer device.
     */
    fun observePairedCameras(): Flow<List<DevicePairingRecord>> = callbackFlow {
        val listener = firestore.collection("pairings")
            .whereEqualTo("viewerDeviceId", viewerDeviceId)
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
