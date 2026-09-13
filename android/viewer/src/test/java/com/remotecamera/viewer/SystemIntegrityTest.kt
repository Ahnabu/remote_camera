package com.remotecamera.viewer

import com.remotecamera.viewer.pairing.PairingPayload
import org.junit.Assert.*
import org.junit.Test

class SystemIntegrityTest {

    @Test
    fun testViewerPairingPayloadDeserialization() {
        val payload = PairingPayload(
            cameraDeviceId = "Realme_C55_Agent",
            cameraPublicKey = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE...",
            challenge = "nonce_98765",
            signature = "MEQC...=="
        )

        val json = payload.toJson()
        val parsed = PairingPayload.fromJson(json)

        assertNotNull(parsed)
        assertEquals("Realme_C55_Agent", parsed?.cameraDeviceId)
        assertEquals("nonce_98765", parsed?.challenge)
    }
}
