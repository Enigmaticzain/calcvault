package com.calcvault.crypto.ratchet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

class DoubleRatchetTest {

    private fun generateEcKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
    }.generateKeyPair()

    @Test
    fun doubleRatchetEncryptDecryptRoundTrip() {
        val sharedSecret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val bobRatchetPair = generateEcKeyPair()

        val alice = DoubleRatchet()
        val bob = DoubleRatchet()

        // Alice is sender, Bob is receiver
        alice.initSender(sharedSecret.copyOf(), bobRatchetPair.public)
        bob.initReceiver(sharedSecret.copyOf(), bobRatchetPair)

        val plaintext = "Hello Sanu! This is end-to-end encrypted.".toByteArray()
        val encryptedMsg = alice.encrypt(plaintext)

        assertNotNull(encryptedMsg)
        val decrypted = bob.decrypt(encryptedMsg)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun doubleRatchetMultipleMessagesSequential() {
        val sharedSecret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val bobRatchetPair = generateEcKeyPair()

        val alice = DoubleRatchet()
        val bob = DoubleRatchet()

        alice.initSender(sharedSecret.copyOf(), bobRatchetPair.public)
        bob.initReceiver(sharedSecret.copyOf(), bobRatchetPair)

        val msg1 = "Message 1".toByteArray()
        val msg2 = "Message 2".toByteArray()
        val msg3 = "Message 3".toByteArray()

        val enc1 = alice.encrypt(msg1)
        val enc2 = alice.encrypt(msg2)
        val enc3 = alice.encrypt(msg3)

        assertArrayEquals(msg1, bob.decrypt(enc1))
        assertArrayEquals(msg2, bob.decrypt(enc2))
        assertArrayEquals(msg3, bob.decrypt(enc3))
    }

    @Test
    fun doubleRatchetBidirectionalExchange() {
        val sharedSecret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val bobRatchetPair = generateEcKeyPair()

        val alice = DoubleRatchet()
        val bob = DoubleRatchet()

        alice.initSender(sharedSecret.copyOf(), bobRatchetPair.public)
        bob.initReceiver(sharedSecret.copyOf(), bobRatchetPair)

        // Alice sends to Bob
        val msgA1 = "Alice msg 1".toByteArray()
        val encA1 = alice.encrypt(msgA1)
        assertArrayEquals(msgA1, bob.decrypt(encA1))

        // Bob replies to Alice
        val msgB1 = "Bob msg 1".toByteArray()
        val encB1 = bob.encrypt(msgB1)
        assertArrayEquals(msgB1, alice.decrypt(encB1))

        // Alice replies again
        val msgA2 = "Alice msg 2".toByteArray()
        val encA2 = alice.encrypt(msgA2)
        assertArrayEquals(msgA2, bob.decrypt(encA2))
    }
}
