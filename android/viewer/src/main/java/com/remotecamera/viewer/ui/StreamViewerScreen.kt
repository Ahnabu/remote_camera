package com.remotecamera.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.remotecamera.viewer.model.QualityProfile
import com.remotecamera.viewer.model.StreamMetrics
import com.remotecamera.viewer.webrtc.WebRTCManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamViewerScreen(
    cameraDeviceId: String,
    webRTCManager: WebRTCManager,
    metrics: StreamMetrics = StreamMetrics(),
    onStopStreamRequested: () -> Unit,
    onIceRestartRequested: () -> Unit,
    onTorchToggleRequested: (Boolean) -> Unit,
    onSwitchCameraRequested: () -> Unit,
    onQualitySelected: (QualityProfile) -> Unit
) {
    var isTorchOn by remember { mutableStateOf(false) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(QualityProfile.MEDIUM_720P) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Live WebRTC Video Player View
        WebRTCVideoPlayerView(
            webRTCManager = webRTCManager,
            modifier = Modifier.fillMaxSize()
        )

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cameraDeviceId,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            IconButton(
                onClick = onStopStreamRequested,
                modifier = Modifier
                    .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop Stream",
                    tint = Color.White
                )
            }
        }

        // Stats Card Overlay
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .padding(top = 60.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.small
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("FPS: ${metrics.fps}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text("Bitrate: ${metrics.bitrateKbps} kbps", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text("RTT: ${metrics.rttMs} ms", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text("Quality: ${selectedQuality.label}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Bottom Action Bar (Controls)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Torch Toggle
                IconButton(onClick = {
                    isTorchOn = !isTorchOn
                    onTorchToggleRequested(isTorchOn)
                }) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Torch",
                        tint = if (isTorchOn) Color.Yellow else Color.White
                    )
                }

                // Switch Camera
                IconButton(onClick = onSwitchCameraRequested) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }

                // ICE Restart
                IconButton(onClick = onIceRestartRequested) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "ICE Restart / Reconnect",
                        tint = Color.White
                    )
                }

                // Quality Profile Dropdown Menu
                Box {
                    IconButton(onClick = { qualityMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Quality Settings",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = qualityMenuExpanded,
                        onDismissRequest = { qualityMenuExpanded = false }
                    ) {
                        QualityProfile.values().forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.label) },
                                onClick = {
                                    selectedQuality = profile
                                    onQualitySelected(profile)
                                    qualityMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
