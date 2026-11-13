package com.calcvault.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSessionCryptoTest {

    @Test
    fun generateEphemeralKeyPairReturnsValidKeys() {
        val keyPair = CompanionSessionCrypto.generateEphemeralX25519()
        assertNotNull(keyPair.keyPair)
        assertNotNull(keyPair.publicDerBase64)
        assertTrue(keyPair.publicDerBase64.isNotBlank())
    }

    @Test
    fun computePairingProofProducesConsistentHMAC() {
        val proof1 = CompanionSessionCrypto.computePairingProof(
            pairingCode = "123456",
            sessionId = "sess-01",
            pcPub = "pc-pub-key",
            phonePub = "phone-pub-key",
            pcNonce = "nonce-pc",
            phoneNonce = "nonce-phone",
            deviceId = "dev-123"
        )

        val proof2 = CompanionSessionCrypto.computePairingProof(
            pairingCode = "123456",
            sessionId = "sess-01",
            pcPub = "pc-pub-key",
            phonePub = "phone-pub-key",
            pcNonce = "nonce-pc",
            phoneNonce = "nonce-phone",
            deviceId = "dev-123"
        )

        assertEquals(proof1, proof2)
        assertTrue(CompanionSessionCrypto.safeEqualsBase64(proof1, proof2))
    }

    @Test
    fun safeEqualsBase64RejectsTamperedProof() {
        val original = CompanionSessionCrypto.sha256Base64("hello world")
        val tampered = CompanionSessionCrypto.sha256Base64("hello world!")

        assertFalse(CompanionSessionCrypto.safeEqualsBase64(original, tampered))
    }
}
