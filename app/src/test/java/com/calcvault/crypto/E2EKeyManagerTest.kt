package com.calcvault.crypto

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class E2EKeyManagerTest {

    private lateinit var keyManager: E2EKeyManager

    @Before
    fun setUp() {
        keyManager = E2EKeyManager.getInstance()
        keyManager.clearSession()
    }

    @After
    fun tearDown() {
        keyManager.clearSession()
    }

    @Test
    fun startsWithoutActiveSession() {
        assertTrue(keyManager.needsRotation())
        assertFalse(keyManager.hasActiveSession())
    }

    @Test
    fun ephemeralExchangeCreatesActiveSession() {
        establishSelfSession()

        assertFalse(keyManager.needsRotation())
        assertTrue(keyManager.hasActiveSession())
    }

    @Test
    fun encryptDecryptRoundTripUsesActiveSession() {
        establishSelfSession()
        val plaintext = "Secret message for partner".toByteArray()

        val encrypted = keyManager.encrypt(plaintext)
        val decrypted = keyManager.decrypt(encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun repeatedEncryptionUsesRandomizedCiphertext() {
        establishSelfSession()
        val plaintext = "Same message".toByteArray()

        val encryptedOne = keyManager.encrypt(plaintext)
        val encryptedTwo = keyManager.encrypt(plaintext)

        assertFalse(encryptedOne.contentEquals(encryptedTwo))
        assertArrayEquals(plaintext, keyManager.decrypt(encryptedOne))
        assertArrayEquals(plaintext, keyManager.decrypt(encryptedTwo))
    }

    @Test
    fun largePayloadRoundTrips() {
        establishSelfSession()
        val plaintext = ByteArray(10_000) { index -> (index % 251).toByte() }

        val encrypted = keyManager.encrypt(plaintext)

        assertNotNull(encrypted)
        assertArrayEquals(plaintext, keyManager.decrypt(encrypted))
    }

    @Test
    fun decryptWithoutSessionThrows() {
        assertThrows(IllegalStateException::class.java) {
            keyManager.decrypt(ByteArray(32))
        }
    }

    @Test
    fun triggerRotationInvalidatesActiveSession() {
        establishSelfSession()

        keyManager.triggerRotation()

        assertTrue(keyManager.needsRotation())
        assertFalse(keyManager.hasActiveSession())
    }

    private fun establishSelfSession() {
        val publicKey = keyManager.startEphemeralExchange()
        keyManager.completeEphemeralExchange(publicKey)
    }
}
