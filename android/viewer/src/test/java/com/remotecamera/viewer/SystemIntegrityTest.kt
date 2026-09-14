package com.remotecamera.viewer

import com.remotecamera.viewer.pairing.PairingPayload
import org.junit.Assert.*
import org.junit.Test

class SystemIntegrityTest {

    @Test
    fun testViewerPairingPayloadDeserialization() {
        val payload = PairingPayload(
            cameraDeviceId = "Camera_Agent_Test",
            cameraPublicKey = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE...",
            challenge = "nonce_98765",
            signature = "MEQC...=="
        )

        val json = payload.toJson()
        val parsed = PairingPayload.fromJson(json)

        assertNotNull(parsed)
        assertEquals("Camera_Agent_Test", parsed?.cameraDeviceId)
        assertEquals("nonce_98765", parsed?.challenge)
    }
}
