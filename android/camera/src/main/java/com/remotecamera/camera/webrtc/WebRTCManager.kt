package com.remotecamera.camera.webrtc

import android.content.Context
import android.util.Log
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
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val STREAM_ID = "ARDAMS"
        private const val TAG = "WebRTCManager_Camera"
    }

    val rootEglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

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

    fun createPeerConnection(
        stunTurnServers: List<PeerConnection.IceServer>,
        onIceCandidateGenerated: (IceCandidate) -> Unit
    ): PeerConnection? {
        ensureFactoryInitialized()
        isRemoteDescriptionSet = false
        queuedRemoteCandidates.clear()

        val rtcConfig = PeerConnection.RTCConfiguration(stunTurnServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnectionAdapter() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    com.remotecamera.camera.debug.DebugLogger.log(
                        TAG,
                        "❄️ Generated Local ICE Candidate: sdpMid=${it.sdpMid}, sdpMLineIndex=${it.sdpMLineIndex}"
                    )
                    onIceCandidateGenerated(it)
                }
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "📊 ICE Gathering State Changed: $newState")
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "🚦 Signaling State Changed: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                com.remotecamera.camera.debug.DebugLogger.log(
                    TAG,
                    "🌐 ICE Connection State Changed: $newState",
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> com.remotecamera.camera.debug.LogLevel.SUCCESS
                        PeerConnection.IceConnectionState.FAILED,
                        PeerConnection.IceConnectionState.DISCONNECTED -> com.remotecamera.camera.debug.LogLevel.ERROR
                        else -> com.remotecamera.camera.debug.LogLevel.INFO
                    }
                )
                when (newState) {
                    PeerConnection.IceConnectionState.CHECKING -> _connectionState.value = WebRTCState.Connecting
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> _connectionState.value = WebRTCState.Connected
                    PeerConnection.IceConnectionState.DISCONNECTED -> _connectionState.value = WebRTCState.Disconnected("ICE Disconnected")
                    PeerConnection.IceConnectionState.FAILED -> _connectionState.value = WebRTCState.Error("ICE Connection Failed")
                    else -> {}
                }
            }
        })

        return peerConnection
    }

    fun attachLocalVideoSource(surfaceTextureHelper: SurfaceTextureHelper, videoCapturer: VideoCapturer): VideoTrack? {
        ensureFactoryInitialized()
        this.surfaceTextureHelper = surfaceTextureHelper
        this.videoCapturer = videoCapturer
        val videoSource = factory?.createVideoSource(videoCapturer.isScreencast) ?: return null

        val proxiedCapturerObserver = object : CapturerObserver {
            private var capturedFrameCount = 0

            override fun onCapturerStarted(success: Boolean) {
                com.remotecamera.camera.debug.DebugLogger.log(
                    TAG,
                    "📷 Camera Capturer Started: success=$success",
                    if (success) com.remotecamera.camera.debug.LogLevel.SUCCESS else com.remotecamera.camera.debug.LogLevel.ERROR
                )
                if (!success) {
                    com.remotecamera.camera.debug.DebugLogger.log(TAG, "⚠️ Retrying capture with fallback resolution 640x480 in 800ms...", com.remotecamera.camera.debug.LogLevel.WARNING)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            videoCapturer.startCapture(640, 480, 30)
                        } catch (e: Exception) {
                            com.remotecamera.camera.debug.DebugLogger.log(TAG, "❌ Fallback capture failed: ${e.message}", com.remotecamera.camera.debug.LogLevel.ERROR)
                        }
                    }, 800)
                }
                videoSource.capturerObserver.onCapturerStarted(success)
            }

            override fun onCapturerStopped() {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "⏹️ Camera Capturer Stopped", com.remotecamera.camera.debug.LogLevel.WARNING)
                videoSource.capturerObserver.onCapturerStopped()
            }

            override fun onFrameCaptured(frame: VideoFrame?) {
                capturedFrameCount++
                if (capturedFrameCount == 1 || capturedFrameCount % 150 == 0) {
                    com.remotecamera.camera.debug.DebugLogger.log(
                        TAG,
                        "📹 Captured $capturedFrameCount frames from camera sensor! Size: ${frame?.buffer?.width}x${frame?.buffer?.height}",
                        com.remotecamera.camera.debug.LogLevel.SUCCESS
                    )
                }
                videoSource.capturerObserver.onFrameCaptured(frame)
            }
        }

        videoCapturer.initialize(surfaceTextureHelper, context, proxiedCapturerObserver)
        try {
            videoCapturer.startCapture(1280, 720, 30)
        } catch (e: Exception) {
            com.remotecamera.camera.debug.DebugLogger.log(TAG, "⚠️ 1280x720 startCapture threw exception: ${e.message}, trying 640x480", com.remotecamera.camera.debug.LogLevel.WARNING)
            try {
                videoCapturer.startCapture(640, 480, 30)
            } catch (e2: Exception) {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "❌ 640x480 startCapture failed: ${e2.message}", com.remotecamera.camera.debug.LogLevel.ERROR)
            }
        }

        localVideoTrack = factory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        peerConnection?.addTrack(localVideoTrack, listOf(STREAM_ID))
        com.remotecamera.camera.debug.DebugLogger.log(TAG, "📹 Local VideoTrack attached to PeerConnection", com.remotecamera.camera.debug.LogLevel.SUCCESS)
        return localVideoTrack
    }

    fun switchCamera(onSwitched: ((Boolean) -> Unit)? = null) {
        val cameraCapturer = videoCapturer as? CameraVideoCapturer
        if (cameraCapturer != null) {
            cameraCapturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    com.remotecamera.camera.debug.DebugLogger.log(
                        TAG,
                        "📷 Switched WebRTC Camera Lens! isFrontCamera=$isFrontCamera",
                        com.remotecamera.camera.debug.LogLevel.SUCCESS
                    )
                    onSwitched?.invoke(isFrontCamera)
                }

                override fun onCameraSwitchError(errorDescription: String?) {
                    com.remotecamera.camera.debug.DebugLogger.log(
                        TAG,
                        "❌ Failed to switch WebRTC camera lens: $errorDescription",
                        com.remotecamera.camera.debug.LogLevel.ERROR
                    )
                }
            })
        } else {
            com.remotecamera.camera.debug.DebugLogger.log(TAG, "⚠️ Cannot switch camera: videoCapturer is not CameraVideoCapturer", com.remotecamera.camera.debug.LogLevel.WARNING)
        }
    }

    fun createOffer(onSdpCreated: (SessionDescription) -> Unit) {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    com.remotecamera.camera.debug.DebugLogger.log(TAG, "📝 SDP Offer created successfully, setting local description")
                    peerConnection?.setLocalDescription(SdpAdapter(), it)
                    onSdpCreated(it)
                }
            }

            override fun onCreateFailure(reason: String?) {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "❌ Failed to create SDP Offer: $reason", com.remotecamera.camera.debug.LogLevel.ERROR)
            }
        }, mediaConstraints)
    }

    fun canSetRemoteAnswer(): Boolean {
        val state = peerConnection?.signalingState()
        return state == PeerConnection.SignalingState.HAVE_LOCAL_OFFER
    }

    fun setRemoteAnswer(sdp: String) {
        if (!canSetRemoteAnswer()) {
            com.remotecamera.camera.debug.DebugLogger.log(
                TAG,
                "⚠️ Ignoring setRemoteAnswer: PeerConnection signaling state is ${peerConnection?.signalingState()} (expected HAVE_LOCAL_OFFER)",
                com.remotecamera.camera.debug.LogLevel.WARNING
            )
            return
        }

        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "✅ Remote SDP Answer applied successfully!", com.remotecamera.camera.debug.LogLevel.SUCCESS)
                drainQueuedCandidates()
            }

            override fun onSetFailure(reason: String?) {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "❌ Failed to set Remote SDP Answer: $reason", com.remotecamera.camera.debug.LogLevel.ERROR)
            }
        }, sessionDescription)
    }

    @Synchronized
    fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (isRemoteDescriptionSet && peerConnection != null) {
            peerConnection?.addIceCandidate(candidate)
            com.remotecamera.camera.debug.DebugLogger.log(TAG, "➕ Applied Remote ICE Candidate: sdpMid=${candidate.sdpMid}")
        } else {
            queuedRemoteCandidates.add(candidate)
            com.remotecamera.camera.debug.DebugLogger.log(TAG, "⏳ Queued Remote ICE Candidate (waiting for Remote Description): total queued=${queuedRemoteCandidates.size}")
        }
    }

    @Synchronized
    private fun drainQueuedCandidates() {
        isRemoteDescriptionSet = true
        com.remotecamera.camera.debug.DebugLogger.log(TAG, "📥 Draining ${queuedRemoteCandidates.size} queued remote ICE candidates")
        for (candidate in queuedRemoteCandidates) {
            peerConnection?.addIceCandidate(candidate)
        }
        queuedRemoteCandidates.clear()
    }

    fun logStatsReport() {
        peerConnection?.getStats { report ->
            for (stats in report.statsMap.values) {
                if (stats.type == "outbound-rtp") {
                    val bytesSent = stats.members["bytesSent"]
                    val framesEncoded = stats.members["framesEncoded"]
                    val packetsSent = stats.members["packetsSent"]
                    com.remotecamera.camera.debug.DebugLogger.log(
                        TAG,
                        "📊 WebRTC Outbound Stats: bytesSent=$bytesSent, packetsSent=$packetsSent, framesEncoded=$framesEncoded",
                        com.remotecamera.camera.debug.LogLevel.INFO
                    )
                }
            }
        }
    }

    fun createCameraCapturer(context: Context): VideoCapturer? {
        val eventsHandler = object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String?) {
                com.remotecamera.camera.debug.DebugLogger.log(
                    TAG,
                    "❌ WebRTC Camera Error: $errorDescription",
                    com.remotecamera.camera.debug.LogLevel.ERROR
                )
                if (errorDescription?.contains("CAMERA_DISABLED") == true || errorDescription?.contains("policy") == true) {
                    com.remotecamera.camera.debug.DebugLogger.log(TAG, "⏳ OS Camera policy lock detected. Scheduling delayed retry in 800ms...", com.remotecamera.camera.debug.LogLevel.WARNING)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            videoCapturer?.startCapture(640, 480, 30)
                        } catch (e: Exception) {
                            com.remotecamera.camera.debug.DebugLogger.log(TAG, "❌ Delayed retry failed: ${e.message}", com.remotecamera.camera.debug.LogLevel.ERROR)
                        }
                    }, 800)
                }
            }

            override fun onCameraDisconnected() {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "⚠️ Camera Disconnected", com.remotecamera.camera.debug.LogLevel.WARNING)
            }

            override fun onCameraFreezed(errorDescription: String?) {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "🧊 Camera Freezed: $errorDescription", com.remotecamera.camera.debug.LogLevel.ERROR)
            }

            override fun onCameraOpening(cameraName: String?) {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "📷 Opening Camera Hardware ($cameraName)...")
            }

            override fun onFirstFrameAvailable() {
                com.remotecamera.camera.debug.DebugLogger.log(
                    TAG,
                    "🎉 FIRST CAMERA SENSOR FRAME AVAILABLE TO WEBRTC!",
                    com.remotecamera.camera.debug.LogLevel.SUCCESS
                )
            }

            override fun onCameraClosed() {
                com.remotecamera.camera.debug.DebugLogger.log(TAG, "🚪 Camera Hardware Closed")
            }
        }

        // Attempt 1: Camera2Enumerator
        if (Camera2Enumerator.isSupported(context)) {
            com.remotecamera.camera.debug.DebugLogger.log(TAG, "🔍 Initializing Camera2Enumerator...")
            val enumerator = Camera2Enumerator(context)
            val capturer = createCapturerFromEnumerator(enumerator, eventsHandler)
            if (capturer != null) return capturer
        }

        // Fallback Attempt 2: Camera1Enumerator
        com.remotecamera.camera.debug.DebugLogger.log(TAG, "⚠️ Camera2Enumerator failed or unsupported, falling back to Camera1Enumerator...")
        val enumerator = Camera1Enumerator(true)
        return createCapturerFromEnumerator(enumerator, eventsHandler)
    }

    private fun createCapturerFromEnumerator(
        enumerator: CameraEnumerator,
        eventsHandler: CameraVideoCapturer.CameraEventsHandler
    ): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, eventsHandler)
                if (capturer != null) {
                    com.remotecamera.camera.debug.DebugLogger.log(TAG, "✅ Created Back Camera Capturer: $deviceName", com.remotecamera.camera.debug.LogLevel.SUCCESS)
                    return capturer
                }
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, eventsHandler)
                if (capturer != null) {
                    com.remotecamera.camera.debug.DebugLogger.log(TAG, "✅ Created Front Camera Capturer: $deviceName", com.remotecamera.camera.debug.LogLevel.SUCCESS)
                    return capturer
                }
            }
        }
        return null
    }

    fun closeSession() {
        try {
            isRemoteDescriptionSet = false
            queuedRemoteCandidates.clear()

            try {
                videoCapturer?.stopCapture()
                videoCapturer?.dispose()
            } catch (e: Exception) { }
            videoCapturer = null

            try {
                surfaceTextureHelper?.dispose()
            } catch (e: Exception) { }
            surfaceTextureHelper = null

            peerConnection?.close()
            peerConnection = null
            localVideoTrack?.dispose()
            localVideoTrack = null
            _connectionState.value = WebRTCState.Idle
        } catch (e: Exception) {
            _connectionState.value = WebRTCState.Error("Error closing WebRTC session: ${e.message}")
        }
    }

    fun close() {
        closeSession()
        try {
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


