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
fun CameraPairingScreen(pairingRepository: PairingRepository) {
    val payload = remember { pairingRepository.createPairingPayload() }
    val qrBitmap = remember(payload) { QRCodeGenerator.generateQRCodeBitmap(payload.toJson(), 600, 600) }
    val pairedDevices by pairingRepository.observeActivePairings().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Realme C55 — Camera Agent") },
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
                text = "Scan QR Code on Samsung S20 Viewer to Pair",
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
                    Text("Device ID: ${payload.cameraDeviceId}", style = MaterialTheme.typography.bodySmall)
                    Text("Public Key (EC secp256r1): Hardware Keystore Backed", style = MaterialTheme.typography.bodySmall)
                    Text("Status: READY / STANDBY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                    Divider()
                }
            }
        }
    }
}
