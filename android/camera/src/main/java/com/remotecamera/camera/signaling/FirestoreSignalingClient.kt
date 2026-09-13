package com.remotecamera.camera.signaling

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
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Listens for incoming session signaling events targeting this Camera device.
     */
    fun observeSessionEvents(cameraDeviceId: String): Flow<FirestoreSignalingSession> = callbackFlow {
        val listener: ListenerRegistration = firestore.collection("signaling")
            .whereEqualTo("cameraDeviceId", cameraDeviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(FirestoreSignalingSession::class.java)?.let { session ->
                        trySend(session)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Camera posts WebRTC SDP Offer to Firestore.
     */
    suspend fun sendOffer(sessionId: String, offerSdp: String) {
        firestore.collection("signaling").document(sessionId).update(
            mapOf(
                "offerSdp" to offerSdp,
                "status" to "OFFERED"
            )
        ).await()
    }

    /**
     * Camera sends ICE candidate to sub-collection.
     */
    suspend fun sendIceCandidate(sessionId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidateRecord(
            sender = "CAMERA",
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
            sdp = sdp
        )
        firestore.collection("signaling").document(sessionId).collection("candidates").add(candidate).await()
    }

    /**
     * Listens for Viewer ICE candidates.
     */
    fun observeViewerIceCandidates(sessionId: String): Flow<IceCandidateRecord> = callbackFlow {
        val listener = firestore.collection("signaling").document(sessionId).collection("candidates")
            .whereEqualTo("sender", "VIEWER")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
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
