package com.calcvault.crypto.ratchet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

class X3DHTest {

    private fun generateIdentityPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
    }.generateKeyPair()

    @Test
    fun x3dhAgreementDerivesMatchingSharedSecret() {
        val x3dh = X3DH()

        val aliceIdentity = generateIdentityPair()
        val bobIdentity = generateIdentityPair()

        // Bob generates published key bundle
        val (bobBundle, bobPrivateKeys) = x3dh.generateKeyBundle(bobIdentity.private, bobIdentity.public)

        // Alice performs agreement using Bob's bundle
        val aliceInitial = x3dh.senderAgreement(aliceIdentity, bobBundle)
        assertNotNull(aliceInitial)

        // Bob performs agreement using Alice's initial message and his private keys
        val bobSharedSecret = x3dh.receiverAgreement(
            myIdentityKey = bobIdentity,
            mySignedPreKey = bobPrivateKeys["spk"]!!,
            myOneTimePreKey = bobPrivateKeys["opk"],
            msg = aliceInitial!!
        )

        assertNotNull(bobSharedSecret)
        assertArrayEquals(aliceInitial.sharedSecret, bobSharedSecret)
    }
}
