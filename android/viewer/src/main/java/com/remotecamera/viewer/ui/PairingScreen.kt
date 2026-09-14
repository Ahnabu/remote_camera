package com.remotecamera.viewer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotecamera.viewer.pairing.DevicePairingRecord
import com.remotecamera.viewer.pairing.PairingRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerPairingScreen(
    pairingRepository: PairingRepository,
    onOpenScannerRequested: () -> Unit,
    onConnectRequested: (String) -> Unit = {}
) {
    val pairedCameras by pairingRepository.observePairedCameras().collectAsState(initial = emptyList())
    var manualPayloadInput by remember { mutableStateOf("") }
    var pairingStatusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Camera Viewer") },
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
                .padding(16.dp)
        ) {
            Button(
                onClick = onOpenScannerRequested,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📷 Scan Camera QR Code")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Or Paste QR Code JSON Payload:", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = manualPayloadInput,
                onValueChange = { manualPayloadInput = it },
                label = { Text("QR JSON Data") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        val result = pairingRepository.processScannedPairingPayload(manualPayloadInput)
                        pairingStatusMessage = if (result.isSuccess) {
                            "✅ Pairing Successful! Cryptographic signature verified."
                        } else {
                            "❌ Pairing Failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                },
                enabled = manualPayloadInput.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Verify & Pair Device")
            }

            pairingStatusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = if (msg.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val debugLogs by com.remotecamera.viewer.debug.DebugLogger.logs.collectAsState()
            var isConsoleExpanded by remember { mutableStateOf(true) }
            val context = androidx.compose.ui.platform.LocalContext.current

            Spacer(modifier = Modifier.height(16.dp))

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
                                    val clip = android.content.ClipData.newPlainText("Viewer Debug Logs", clipText)
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
                                .heightIn(max = 160.dp)
                        ) {
                            if (debugLogs.isEmpty()) {
                                item {
                                    Text(
                                        "Standing by for viewer events...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color.Gray
                                    )
                                }
                            }
                            items(debugLogs.reversed()) { log ->
                                val color = when (log.level) {
                                    com.remotecamera.viewer.debug.LogLevel.ERROR -> androidx.compose.ui.graphics.Color.Red
                                    com.remotecamera.viewer.debug.LogLevel.WARNING -> androidx.compose.ui.graphics.Color.Yellow
                                    com.remotecamera.viewer.debug.LogLevel.SUCCESS -> androidx.compose.ui.graphics.Color.Green
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
                text = "Paired Cameras (${pairedCameras.size})",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(pairedCameras) { camera ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ListItem(
                            headlineContent = { Text(camera.cameraDeviceId) },
                            supportingContent = { Text("Status: ${camera.status} | Signature Verified ✅") },
                            trailingContent = {
                                Button(onClick = { onConnectRequested(camera.cameraDeviceId) }) {
                                    Text("Connect")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
