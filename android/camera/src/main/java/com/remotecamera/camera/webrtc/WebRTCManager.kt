package com.remotecamera.camera.webrtc

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
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val STREAM_ID = "ARDAMS"
    }

    val rootEglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null

    private val _connectionState = MutableStateFlow<WebRTCState>(WebRTCState.Idle)
    val connectionState: StateFlow<WebRTCState> = _connectionState

    init {
        initPeerConnectionFactory()
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
        val rtcConfig = PeerConnection.RTCConfiguration(stunTurnServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnectionAdapter() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidateGenerated(it) }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
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
        val videoSource = factory?.createVideoSource(videoCapturer.isScreencast) ?: return null
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        videoCapturer.startCapture(1280, 720, 30)

        localVideoTrack = factory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        peerConnection?.addTrack(localVideoTrack, listOf(STREAM_ID))
        return localVideoTrack
    }

    fun createOffer(onSdpCreated: (SessionDescription) -> Unit) {
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SdpAdapter(), it)
                    onSdpCreated(it)
                }
            }
        }, mediaConstraints)
    }

    fun setRemoteAnswer(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(SdpAdapter(), sessionDescription)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun close() {
        try {
            peerConnection?.close()
            localVideoTrack?.dispose()
            factory?.dispose()
            rootEglBase.release()
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
