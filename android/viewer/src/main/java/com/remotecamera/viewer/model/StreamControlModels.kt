package com.remotecamera.viewer.model

enum class QualityProfile(val label: String, val width: Int, val height: Int, val targetFps: Int, val maxBitrateKbps: Int) {
    LOW_360P("360p (Low Data)", 640, 360, 20, 500),
    MEDIUM_720P("720p (HD Standard)", 1280, 720, 30, 1500),
    HIGH_1080P("1080p (Full HD)", 1920, 1080, 30, 3500)
}

data class StreamMetrics(
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val rttMs: Int = 0,
    val packetLossPercentage: Float = 0.0f,
    val currentResolution: String = "1280x720"
)

data class RemoteCommand(
    val type: String, // TORCH_TOGGLE, SWITCH_CAMERA, ICE_RESTART, CHANGE_QUALITY
    val payload: Any? = null
)
