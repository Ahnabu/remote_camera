package com.remotecamera.camera.pairing

import com.google.firebase.firestore.FirebaseFirestore
import com.remotecamera.camera.crypto.CryptoManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class PairingRepository(
    private val customFirestore: FirebaseFirestore? = null,
    private val customCryptoManager: CryptoManager? = null
) {
    private val firestore: FirebaseFirestore
        get() = customFirestore ?: FirebaseFirestore.getInstance()

    private val cryptoManager: CryptoManager
        get() = customCryptoManager ?: CryptoManager()

    val cameraDeviceId: String = "Realme_C55_${UUID.randomUUID().toString().take(8)}"

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
                    close(error)
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
