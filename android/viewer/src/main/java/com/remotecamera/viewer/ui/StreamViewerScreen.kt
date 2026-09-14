package com.remotecamera.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    isPhotoBurstActive: Boolean = false,
    savedPhotoCount: Int = 0,
    onStopStreamRequested: () -> Unit,
    onIceRestartRequested: () -> Unit,
    onTorchToggleRequested: (Boolean) -> Unit,
    onSwitchCameraRequested: () -> Unit,
    onQualitySelected: (QualityProfile) -> Unit,
    onPhotoBurstToggleRequested: (Boolean) -> Unit
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

        // Photo Burst Banner Overlay
        if (isPhotoBurstActive) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                color = Color.Red.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔴 REC (10 FPS) — Saved: $savedPhotoCount photos",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Stats Card Overlay
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .padding(top = if (isPhotoBurstActive) 110.dp else 60.dp),
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

        val debugLogs by com.remotecamera.viewer.debug.DebugLogger.logs.collectAsState()
        var showDebugOverlay by remember { mutableStateOf(false) }

        // Live Debug Console Overlay
        if (showDebugOverlay) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                color = Color.Black.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛠️ Viewer Live Log (${debugLogs.size})", color = Color.Green, style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                if (debugLogs.isNotEmpty()) {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clipText = debugLogs.joinToString("\n") { "[${it.timestamp}] [${it.tag}] ${it.message}" }
                                    val clip = android.content.ClipData.newPlainText("Viewer Debug Logs", clipText)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Copied ${debugLogs.size} logs to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("📋 Copy", color = Color.Yellow, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { showDebugOverlay = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close Debug Log", tint = Color.White)
                            }
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(debugLogs.reversed()) { log ->
                            val color = when (log.level) {
                                com.remotecamera.viewer.debug.LogLevel.ERROR -> Color.Red
                                com.remotecamera.viewer.debug.LogLevel.WARNING -> Color.Yellow
                                com.remotecamera.viewer.debug.LogLevel.SUCCESS -> Color.Green
                                else -> Color.White
                            }
                            Text(
                                text = "[${log.timestamp}] [${log.tag}] ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
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
                // 10 FPS Photo Burst Toggle
                IconButton(onClick = {
                    onPhotoBurstToggleRequested(!isPhotoBurstActive)
                }) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Toggle 10 FPS Photo Burst",
                        tint = if (isPhotoBurstActive) Color.Red else Color.White
                    )
                }

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

                // Debug Console Toggle Button
                IconButton(onClick = { showDebugOverlay = !showDebugOverlay }) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Toggle Debug Console",
                        tint = if (showDebugOverlay) Color.Green else Color.White
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
