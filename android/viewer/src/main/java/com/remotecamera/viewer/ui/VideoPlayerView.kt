package com.remotecamera.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.remotecamera.viewer.webrtc.WebRTCManager
import com.remotecamera.viewer.webrtc.WebRTCState
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun WebRTCVideoPlayerView(
    webRTCManager: WebRTCManager,
    modifier: Modifier = Modifier
) {
    val connectionState by webRTCManager.connectionState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(webRTCManager.rootEglBase.eglBaseContext, null)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    setEnableHardwareScaler(true)
                    setMirror(false)
                    webRTCManager.attachRemoteVideoTrack(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Real-Time Connection Status Overlay
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart),
            color = Color.Black.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (statusColor, statusText) = when (connectionState) {
                    is WebRTCState.Connected -> Color.Green to "LIVE (WebRTC Connected)"
                    is WebRTCState.Connecting -> Color.Yellow to "Connecting WebRTC Media..."
                    is WebRTCState.Disconnected -> Color.Red to "Disconnected"
                    is WebRTCState.Error -> Color.Red to "Connection Error"
                    else -> Color.Gray to "Standby"
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, shape = MaterialTheme.shapes.extraSmall)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
