package com.remotecamera.camera

import com.remotecamera.camera.pairing.PairingPayload
import org.junit.Assert.*
import org.junit.Test

class SystemIntegrityTest {

    @Test
    fun testPairingPayloadSerialization() {
        val payload = PairingPayload(
            cameraDeviceId = "Realme_C55_Test",
            cameraPublicKey = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE...",
            challenge = "test_challenge_nonce_12345",
            signature = "MEQCIH...=="
        )

        val json = payload.toJson()
        assertNotNull(json)
        assertTrue(json.contains("Realme_C55_Test"))

        val deserialized = PairingPayload.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(payload.cameraDeviceId, deserialized?.cameraDeviceId)
        assertEquals(payload.challenge, deserialized?.challenge)
    }
}
