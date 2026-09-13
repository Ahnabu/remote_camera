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
    val answerSdp: String? = null
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
        val sessionId = "${cameraDeviceId}_${viewerDeviceId}"
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
