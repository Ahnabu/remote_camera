package com.remotecamera.camera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.remotecamera.camera.pairing.DevicePairingRecord
import com.remotecamera.camera.pairing.PairingRepository
import com.remotecamera.camera.pairing.QRCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPairingScreen(
    pairingRepository: PairingRepository,
    onToggleServiceRequested: (Boolean, String) -> Unit = { _, _ -> }
) {
    val payload = remember { pairingRepository.createPairingPayload() }
    val qrBitmap = remember(payload) { QRCodeGenerator.generateQRCodeBitmap(payload.toJson(), 600, 600) }
    val pairedDevices by pairingRepository.observeActivePairings().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Camera Agent") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scan QR Code on Viewer App to Pair",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Device Pairing QR Code",
                    modifier = Modifier
                        .size(260.dp)
                        .padding(8.dp)
                )
            } else {
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var serviceEnabled by remember { mutableStateOf(true) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Remote Camera Agent",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Switch(
                            checked = serviceEnabled,
                            onCheckedChange = { enabled ->
                                serviceEnabled = enabled
                                onToggleServiceRequested(enabled, payload.cameraDeviceId)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Device ID: ${payload.cameraDeviceId}", style = MaterialTheme.typography.bodySmall)
                    Text("Public Key (EC secp256r1): Hardware Keystore Backed", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (serviceEnabled) "Status: READY / STANDBY" else "Status: DISABLED",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            val debugLogs by com.remotecamera.camera.debug.DebugLogger.logs.collectAsState()
            var isConsoleExpanded by remember { mutableStateOf(true) }
            val context = androidx.compose.ui.platform.LocalContext.current

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Black)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛠️ Live Debug Console (${debugLogs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = androidx.compose.ui.graphics.Color.Green
                        )
                        Row {
                            TextButton(onClick = {
                                if (debugLogs.isNotEmpty()) {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clipText = debugLogs.joinToString("\n") { "[${it.timestamp}] [${it.tag}] ${it.message}" }
                                    val clip = android.content.ClipData.newPlainText("Camera Agent Debug Logs", clipText)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Copied ${debugLogs.size} logs to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("📋 Copy", color = androidx.compose.ui.graphics.Color.Yellow, style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { isConsoleExpanded = !isConsoleExpanded }) {
                                Text(
                                    if (isConsoleExpanded) "Hide" else "Expand",
                                    color = androidx.compose.ui.graphics.Color.Cyan,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    if (isConsoleExpanded) {
                        HorizontalDivider(color = androidx.compose.ui.graphics.Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                        ) {
                            if (debugLogs.isEmpty()) {
                                item {
                                    Text(
                                        "Standing by for signaling events...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color.Gray
                                    )
                                }
                            }
                            items(debugLogs.reversed()) { log ->
                                val color = when (log.level) {
                                    com.remotecamera.camera.debug.LogLevel.ERROR -> androidx.compose.ui.graphics.Color.Red
                                    com.remotecamera.camera.debug.LogLevel.WARNING -> androidx.compose.ui.graphics.Color.Yellow
                                    com.remotecamera.camera.debug.LogLevel.SUCCESS -> androidx.compose.ui.graphics.Color.Green
                                    else -> androidx.compose.ui.graphics.Color.White
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Paired Viewers (${pairedDevices.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start)
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(pairedDevices) { device ->
                    ListItem(
                        headlineContent = { Text("Viewer: ${device.viewerDeviceId}") },
                        supportingContent = { Text("Paired at: ${device.pairedAt}") },
                        leadingContent = { Text("✅", style = MaterialTheme.typography.titleMedium) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
