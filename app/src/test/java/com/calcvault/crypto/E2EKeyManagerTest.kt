package com.calcvault.crypto

import javax.crypto.AEADBadTagException
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
    fun independentPeersDeriveInteroperableSessionKeys() {
        val alice = E2EKeyManager()
        val bob = E2EKeyManager()
        establishPeerSession(alice, bob)
        val aliceMessage = "Alice to Bob".toByteArray()
        val bobMessage = "Bob to Alice".toByteArray()

        assertArrayEquals(aliceMessage, bob.decrypt(alice.encrypt(aliceMessage)))
        assertArrayEquals(bobMessage, alice.decrypt(bob.encrypt(bobMessage)))
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
    fun ciphertextDoesNotContainPlaintextAndUsesRandomizedIvAndPadding() {
        establishSelfSession()
        val plaintext = "Same message used for randomized encryption".toByteArray()

        val encryptedOne = keyManager.encrypt(plaintext)
        val encryptedTwo = keyManager.encrypt(plaintext)

        assertFalse(containsBytes(encryptedOne, plaintext))
        assertFalse(encryptedOne.contentEquals(encryptedTwo))
        assertArrayEquals(plaintext, keyManager.decrypt(encryptedOne))
        assertArrayEquals(plaintext, keyManager.decrypt(encryptedTwo))
    }

    @Test
    fun emptyPayloadRoundTrips() {
        establishSelfSession()

        assertArrayEquals(byteArrayOf(), keyManager.decrypt(keyManager.encrypt(byteArrayOf())))
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
    fun authenticatedEncryptionRejectsCiphertextAndIvTampering() {
        val alice = E2EKeyManager()
        val bob = E2EKeyManager()
        establishPeerSession(alice, bob)
        val ciphertext = alice.encrypt("authenticated payload".toByteArray())
        val changedCiphertext = ciphertext.copyOf().also {
            it[it.lastIndex - 1] = (it[it.lastIndex - 1].toInt() xor 0x01).toByte()
        }
        val changedIv = ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        assertThrows(AEADBadTagException::class.java) { bob.decrypt(changedCiphertext) }
        assertThrows(AEADBadTagException::class.java) { bob.decrypt(changedIv) }
    }

    @Test
    fun decryptRejectsTruncatedAndMalformedEnvelopes() {
        establishSelfSession()

        assertThrows(IllegalArgumentException::class.java) { keyManager.decrypt(ByteArray(11)) }
        assertThrows(IllegalArgumentException::class.java) { keyManager.decrypt(ByteArray(285)) }
    }

    @Test
    fun differentPeerSessionCannotDecryptCiphertext() {
        val alice = E2EKeyManager()
        val bob = E2EKeyManager()
        val charlie = E2EKeyManager()
        val mallory = E2EKeyManager()
        establishPeerSession(alice, bob)
        establishPeerSession(charlie, mallory)
        val ciphertext = alice.encrypt("private to Bob".toByteArray())

        assertThrows(AEADBadTagException::class.java) { mallory.decrypt(ciphertext) }
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

    private fun establishPeerSession(alice: E2EKeyManager, bob: E2EKeyManager) {
        val alicePublic = alice.startEphemeralExchange()
        val bobPublic = bob.startEphemeralExchange()
        alice.completeEphemeralExchange(bobPublic)
        bob.completeEphemeralExchange(alicePublic)
    }

    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean =
        needle.isNotEmpty() && haystack.indices.any { start ->
            start + needle.size <= haystack.size &&
                needle.indices.all { offset -> haystack[start + offset] == needle[offset] }
        }
}
