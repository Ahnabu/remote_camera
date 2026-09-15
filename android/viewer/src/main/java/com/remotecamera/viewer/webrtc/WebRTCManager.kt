package com.remotecamera.viewer.webrtc

import android.content.Context
import org.webrtc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class WebRTCState {
    object Idle : WebRTCState()
    object Initializing : WebRTCState()
    object Connecting : WebRTCState()
    object Connected : WebRTCState()
    data class Disconnected(val reason: String) : WebRTCState()
    data class Error(val message: String) : WebRTCState()
}

class WebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCManager_Viewer"
    }

    val rootEglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var remoteVideoTrack: VideoTrack? = null
    private val _remoteVideoTrackState = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackState: StateFlow<VideoTrack?> = _remoteVideoTrackState

    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false

    private val _connectionState = MutableStateFlow<WebRTCState>(WebRTCState.Idle)
    val connectionState: StateFlow<WebRTCState> = _connectionState

    init {
        initPeerConnectionFactory()
    }

    @Synchronized
    fun ensureFactoryInitialized() {
        if (factory == null) {
            initPeerConnectionFactory()
        }
    }

    private fun initPeerConnectionFactory() {
        _connectionState.value = WebRTCState.Initializing
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun parseCandidateType(sdp: String): String {
        val lower = sdp.lowercase()
        return when {
            lower.contains("typ host") -> "host"
            lower.contains("typ srflx") -> "srflx"
            lower.contains("typ relay") -> "relay"
            lower.contains("typ prflx") -> "prflx"
            else -> "unknown"
        }
    }

    fun createPeerConnection(
        stunTurnServers: List<PeerConnection.IceServer>,
        forceRelayMode: Boolean = false,
        onIceCandidateGenerated: (IceCandidate) -> Unit,
        onRemoteVideoTrackReceived: (VideoTrack) -> Unit
    ): PeerConnection? {
        ensureFactoryInitialized()
        isRemoteDescriptionSet = false
        queuedRemoteCandidates.clear()

        val rtcConfig = PeerConnection.RTCConfiguration(stunTurnServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            if (forceRelayMode) {
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
            }
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnectionAdapter() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val candidateType = parseCandidateType(it.sdp)
                    com.remotecamera.viewer.debug.DebugLogger.log(
                        TAG,
                        "❄️ Generated Viewer Local ICE Candidate: type=$candidateType, sdpMid=${it.sdpMid}, sdpMLineIndex=${it.sdpMLineIndex}"
                    )
                    onIceCandidateGenerated(it)
                }
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                com.remotecamera.viewer.debug.DebugLogger.log(TAG, "📊 Viewer ICE Gathering State Changed: $newState")
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                com.remotecamera.viewer.debug.DebugLogger.log(TAG, "🚦 Viewer Signaling State Changed: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                com.remotecamera.viewer.debug.DebugLogger.log(
                    TAG,
                    "🌐 Viewer ICE Connection State Changed: $newState",
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> com.remotecamera.viewer.debug.LogLevel.SUCCESS
                        PeerConnection.IceConnectionState.FAILED,
                        PeerConnection.IceConnectionState.DISCONNECTED -> com.remotecamera.viewer.debug.LogLevel.ERROR
                        else -> com.remotecamera.viewer.debug.LogLevel.INFO
                    }
                )
                if (newState == PeerConnection.IceConnectionState.CONNECTED || newState == PeerConnection.IceConnectionState.COMPLETED) {
                    logStatsReport()
                }
                when (newState) {
                    PeerConnection.IceConnectionState.CHECKING -> _connectionState.value = WebRTCState.Connecting
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> _connectionState.value = WebRTCState.Connected
                    PeerConnection.IceConnectionState.DISCONNECTED -> _connectionState.value = WebRTCState.Disconnected("ICE Disconnected")
                    PeerConnection.IceConnectionState.FAILED -> _connectionState.value = WebRTCState.Error("ICE Connection Failed")
                    else -> {}
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                com.remotecamera.viewer.debug.DebugLogger.log(
                    TAG,
                    "🎥 onAddTrack triggered! Track kind=${track?.kind()}, id=${track?.id()}, enabled=${track?.enabled()}",
                    com.remotecamera.viewer.debug.LogLevel.SUCCESS
                )
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    _remoteVideoTrackState.value = track
                    photoCaptureSink?.let { sink ->
                        track.addSink(sink)
                    }
                    onRemoteVideoTrackReceived(track)
                }
            }
        })

        return peerConnection
    }

    fun setRemoteOfferAndCreateAnswer(sdpOffer: String, onAnswerCreated: (SessionDescription) -> Unit) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdpOffer)
        peerConnection?.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                com.remotecamera.viewer.debug.DebugLogger.log(TAG, "✅ Remote SDP Offer set successfully on Viewer!", com.remotecamera.viewer.debug.LogLevel.SUCCESS)
                drainQueuedCandidates()

                val mediaConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                peerConnection?.createAnswer(object : SdpAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let {
                            com.remotecamera.viewer.debug.DebugLogger.log(TAG, "📝 Created SDP Answer on Viewer, setting local description")
                            peerConnection?.setLocalDescription(SdpAdapter(), it)
                            onAnswerCreated(it)
                        }
                    }

                    override fun onCreateFailure(reason: String?) {
                        com.remotecamera.viewer.debug.DebugLogger.log(TAG, "❌ Failed to create SDP Answer: $reason", com.remotecamera.viewer.debug.LogLevel.ERROR)
                    }
                }, mediaConstraints)
            }

            override fun onSetFailure(reason: String?) {
                com.remotecamera.viewer.debug.DebugLogger.log(TAG, "❌ Failed to set Remote SDP Offer: $reason", com.remotecamera.viewer.debug.LogLevel.ERROR)
            }
        }, sessionDescription)
    }

    @Synchronized
    fun addRemoteIceCandidate(candidate: IceCandidate) {
        val candType = parseCandidateType(candidate.sdp)
        if (isRemoteDescriptionSet && peerConnection != null) {
            peerConnection?.addIceCandidate(candidate)
            com.remotecamera.viewer.debug.DebugLogger.log(TAG, "➕ Applied Camera ICE Candidate: type=$candType, sdpMid=${candidate.sdpMid}")
        } else {
            queuedRemoteCandidates.add(candidate)
            com.remotecamera.viewer.debug.DebugLogger.log(TAG, "⏳ Queued Camera ICE Candidate (type=$candType, waiting for Remote Offer): total queued=${queuedRemoteCandidates.size}")
        }
    }

    @Synchronized
    private fun drainQueuedCandidates() {
        isRemoteDescriptionSet = true
        com.remotecamera.viewer.debug.DebugLogger.log(TAG, "📥 Draining ${queuedRemoteCandidates.size} queued Camera ICE candidates")
        for (candidate in queuedRemoteCandidates) {
            peerConnection?.addIceCandidate(candidate)
        }
        queuedRemoteCandidates.clear()
    }

    fun attachRemoteVideoTrack(surfaceViewRenderer: SurfaceViewRenderer) {
        try {
            surfaceViewRenderer.init(rootEglBase.eglBaseContext, null)
        } catch (e: IllegalStateException) {
            // Already initialized, ignore
        }
        surfaceViewRenderer.setEnableHardwareScaler(true)
        remoteVideoTrack?.addSink(surfaceViewRenderer)
        com.remotecamera.viewer.debug.DebugLogger.log(TAG, "📺 Attached SurfaceViewRenderer to Remote VideoTrack!")
    }

    fun logStatsReport() {
        peerConnection?.getStats { report ->
            var activePairStats: RTCStats? = null
            for (stats in report.statsMap.values) {
                if (stats.type == "candidate-pair") {
                    val state = stats.members["state"]
                    val nominated = stats.members["nominated"]
                    val bytesReceived = stats.members["bytesReceived"]
                    if (state == "succeeded" || nominated == true || (bytesReceived as? Number)?.toLong() ?: 0L > 0L) {
                        activePairStats = stats
                    }
                }
                if (stats.type == "inbound-rtp") {
                    val bytesReceived = stats.members["bytesReceived"]
                    val packetsReceived = stats.members["packetsReceived"]
                    val framesDecoded = stats.members["framesDecoded"]
                    val packetsLost = stats.members["packetsLost"]
                    com.remotecamera.viewer.debug.DebugLogger.log(
                        TAG,
                        "📊 WebRTC Inbound Stats: bytesReceived=$bytesReceived, packetsReceived=$packetsReceived, framesDecoded=$framesDecoded, packetsLost=$packetsLost",
                        com.remotecamera.viewer.debug.LogLevel.INFO
                    )
                }
            }
            if (activePairStats != null) {
                val localCandId = activePairStats.members["localCandidateId"] as? String
                val remoteCandId = activePairStats.members["remoteCandidateId"] as? String
                val localCandStats = report.statsMap[localCandId]
                val remoteCandStats = report.statsMap[remoteCandId]
                val localType = localCandStats?.members?.get("candidateType") ?: "unknown"
                val remoteType = remoteCandStats?.members?.get("candidateType") ?: "unknown"
                val localProtocol = localCandStats?.members?.get("protocol") ?: "udp"
                val remoteProtocol = remoteCandStats?.members?.get("protocol") ?: "udp"

                com.remotecamera.viewer.debug.DebugLogger.log(
                    TAG,
                    "🎯 SELECTED ICE CANDIDATE PAIR: localType=$localType, remoteType=$remoteType, localProtocol=$localProtocol, remoteProtocol=$remoteProtocol",
                    com.remotecamera.viewer.debug.LogLevel.SUCCESS
                )
            }
        }
    }

    private var photoCaptureSink: VideoSink? = null
    private var isPhotoCaptureActive = false
    private var lastCapturedTimestamp = 0L

    fun start10FpsPhotoCapture(cameraName: String, onPhotoSaved: (Int) -> Unit) {
        if (isPhotoCaptureActive) return
        isPhotoCaptureActive = true
        var photoCount = 0

        photoCaptureSink = VideoSink { frame ->
            if (!isPhotoCaptureActive) return@VideoSink
            val now = System.currentTimeMillis()
            if (now - lastCapturedTimestamp >= 100) { // 100ms = 10 FPS
                lastCapturedTimestamp = now
                val buffer = frame.buffer
                val i420 = buffer.toI420()
                if (i420 != null) {
                    val bitmap = com.remotecamera.viewer.storage.FrameSaverHelper.i420ToBitmap(i420)
                    i420.release()
                    if (bitmap != null) {
                        val uri = com.remotecamera.viewer.storage.FrameSaverHelper.savePhotoToStorage(context, cameraName, bitmap)
                        if (uri != null) {
                            photoCount++
                            onPhotoSaved(photoCount)
                        }
                    }
                }
            }
        }

        remoteVideoTrack?.addSink(photoCaptureSink)
        com.remotecamera.viewer.debug.DebugLogger.log("WebRTCManager", "📸 Started 10 FPS Photo Capture for $cameraName", com.remotecamera.viewer.debug.LogLevel.SUCCESS)
    }

    fun stop10FpsPhotoCapture() {
        if (!isPhotoCaptureActive) return
        isPhotoCaptureActive = false
        photoCaptureSink?.let {
            remoteVideoTrack?.removeSink(it)
        }
        photoCaptureSink = null
        com.remotecamera.viewer.debug.DebugLogger.log("WebRTCManager", "⏹️ Stopped 10 FPS Photo Capture", com.remotecamera.viewer.debug.LogLevel.WARNING)
    }

    fun close() {
        try {
            isRemoteDescriptionSet = false
            queuedRemoteCandidates.clear()
            stop10FpsPhotoCapture()
            peerConnection?.close()
            peerConnection = null
            remoteVideoTrack?.dispose()
            remoteVideoTrack = null
            factory?.dispose()
            factory = null
            _connectionState.value = WebRTCState.Idle
        } catch (e: Exception) {
            _connectionState.value = WebRTCState.Error("Error closing WebRTC: ${e.message}")
        }
    }
}

open class PeerConnectionAdapter : PeerConnection.Observer {
    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(dataChannel: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
}

open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(reason: String?) {}
    override fun onSetFailure(reason: String?) {}
}

