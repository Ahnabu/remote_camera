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
    onOpenScannerRequested: () -> Unit
) {
    val pairedCameras by pairingRepository.observePairedCameras().collectAsState(initial = emptyList())
    var manualPayloadInput by remember { mutableStateOf("") }
    var pairingStatusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Samsung S20 — Camera Viewer") },
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

            Spacer(modifier = Modifier.height(24.dp))

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
                                Button(onClick = { /* Connect to camera */ }) {
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
