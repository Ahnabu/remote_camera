package com.remotecamera.viewer.signaling

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class FirestoreSignalingSession(
    val sessionId: String = "",
    val cameraDeviceId: String = "",
    val viewerDeviceId: String = "",
    val status: String = "INITIATED", // INITIATED, OFFERED, ANSWERED, CONNECTED, STOPPED
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val torchEnabled: Boolean? = null,
    val switchCameraRequested: Long? = null,
    val iceRestartRequested: Long? = null,
    val qualityProfile: String? = null,
    val photoBurstActive: Boolean? = null,
    val lastCommand: String? = null,
    val lastCommandTimestamp: Long? = null
)

data class IceCandidateRecord(
    val sender: String = "", // CAMERA or VIEWER
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = ""
)

class FirestoreSignalingClient(
    private val customFirestore: FirebaseFirestore? = null
) {
    private val firestore: FirebaseFirestore
        get() = customFirestore ?: FirebaseFirestore.getInstance()

    /**
     * Viewer initiates a new stream session in Firestore.
     */
    suspend fun initiateSession(cameraDeviceId: String, viewerDeviceId: String): String {
        val sessionId = "${cameraDeviceId}_${viewerDeviceId}_${System.currentTimeMillis()}"
        val session = FirestoreSignalingSession(
            sessionId = sessionId,
            cameraDeviceId = cameraDeviceId,
            viewerDeviceId = viewerDeviceId,
            status = "INITIATED"
        )
        firestore.collection("signaling").document(sessionId).set(session).await()
        return sessionId
    }

    /**
     * Marks session as STOPPED in Firestore when viewer disconnects.
     */
    suspend fun stopSession(sessionId: String) {
        try {
            firestore.collection("signaling").document(sessionId).update(
                mapOf("status" to "STOPPED")
            ).await()
        } catch (e: Exception) {
            // Ignore if doc already cleaned up
        }
    }

    /**
     * Listens for Camera responses (SDP offer) for a session.
     */
    fun observeSession(sessionId: String): Flow<FirestoreSignalingSession> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection("signaling").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                snapshot?.toObject(FirestoreSignalingSession::class.java)?.let { session ->
                    trySend(session)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Viewer posts WebRTC SDP Answer to Firestore.
     */
    suspend fun sendAnswer(sessionId: String, answerSdp: String) {
        firestore.collection("signaling").document(sessionId).update(
            mapOf(
                "answerSdp" to answerSdp,
                "status" to "ANSWERED"
            )
        ).await()
    }

    /**
     * Sends torch light ON/OFF command to Camera Agent via Firestore.
     */
    suspend fun sendTorchCommand(sessionId: String, enabled: Boolean) {
        firestore.collection("signaling").document(sessionId).update(
            mapOf(
                "torchEnabled" to enabled,
                "lastCommand" to if (enabled) "TORCH_ON" else "TORCH_OFF",
                "lastCommandTimestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Sends switch camera (front/back lens) command to Camera Agent via Firestore.
     */
    suspend fun sendSwitchCameraCommand(sessionId: String) {
        val timestamp = System.currentTimeMillis()
        firestore.collection("signaling").document(sessionId).update(
            mapOf(
                "switchCameraRequested" to timestamp,
                "lastCommand" to "SWITCH_CAMERA",
                "lastCommandTimestamp" to timestamp
            )
        ).await()
    }

    /**
     * Sends ICE restart / reconnect command to Camera Agent via Firestore.
     */
    suspend fun sendIceRestartCommand(sessionId: String) {
        val timestamp = System.currentTimeMillis()
        firestore.collection("signaling").document(sessionId).update(
            mapOf(
                "iceRestartRequested" to timestamp,
                "lastCommand" to "ICE_RESTART",
                "lastCommandTimestamp" to timestamp
            )
        ).await()
    }

    /**
     * Sends quality profile setting command to Camera Agent via Firestore.
     */
    suspend fun sendQualityProfileCommand(sessionId: String, profileLabel: String) {
        firestore.collection("signaling").document(sessionId).update(
            mapOf(
                "qualityProfile" to profileLabel,
                "lastCommand" to "QUALITY_$profileLabel",
                "lastCommandTimestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Sends Photo Burst mode (10 FPS capture) status to Camera Agent via Firestore.
     */
    suspend fun sendPhotoBurstCommand(sessionId: String, active: Boolean) {
        firestore.collection("signaling").document(sessionId).update(
            mapOf(
                "photoBurstActive" to active,
                "lastCommand" to if (active) "PHOTO_BURST_START" else "PHOTO_BURST_STOP",
                "lastCommandTimestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Viewer sends ICE candidate to sub-collection.
     */
    suspend fun sendIceCandidate(sessionId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidateRecord(
            sender = "VIEWER",
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
            sdp = sdp
        )
        firestore.collection("signaling").document(sessionId).collection("candidates").add(candidate).await()
    }

    /**
     * Listens for Camera ICE candidates.
     */
    fun observeCameraIceCandidates(sessionId: String): Flow<IceCandidateRecord> = callbackFlow {
        val listener = firestore.collection("signaling").document(sessionId).collection("candidates")
            .whereEqualTo("sender", "CAMERA")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    val candidate = change.document.toObject(IceCandidateRecord::class.java)
                    trySend(candidate)
                }
            }
        awaitClose { listener.remove() }
    }
}
