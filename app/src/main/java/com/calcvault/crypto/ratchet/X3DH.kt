package com.calcvault.crypto.ratchet

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * X3DH — Extended Triple Diffie-Hellman
 *
 * Initial key agreement protocol used to bootstrap the Double Ratchet.
 * Used once when two users connect for the first time (during USB pairing).
 *
 * Provides:
 *   ✅ Mutual authentication (both parties' identity keys involved)
 *   ✅ Forward secrecy (ephemeral keys discarded after use)
 *   ✅ Deniability (no signatures on message content)
 *
 * Key bundle (published by each user):
 *   IK  — Identity Key (long-term EC P-256)
 *   SPK — Signed PreKey (medium-term, rotated periodically)
 *   OPK — One-time PreKey (used once then discarded)
 *
 * Sender (Alice) computes shared secret using:
 *   DH1 = ECDH(IK_A_priv, SPK_B_pub)
 *   DH2 = ECDH(EK_A_priv,  IK_B_pub)
 *   DH3 = ECDH(EK_A_priv,  SPK_B_pub)
 *   DH4 = ECDH(EK_A_priv,  OPK_B_pub)  (if OPK available)
 *   SK  = KDF(DH1 || DH2 || DH3 || DH4)
 *
 * Receiver (Bob) computes the same SK from his side.
 *
 * After X3DH, SK is used to initialise DoubleRatchet.initSender/initReceiver.
 */
class X3DH {

    companion object {
        private const val CURVE = "secp256r1"
        private const val KDF_INFO = "CalcVault-X3DH-v1"
        private val F_BYTES = ByteArray(32) { 0xFF.toByte() } // padding constant
    }

    private val rng = SecureRandom()

    // ── Key Bundle ────────────────────────────────────────────────────────

    data class KeyBundle(
        val identityKeyPub: ByteArray, // IK public
        val signedPreKeyPub: ByteArray, // SPK public
        val signedPreKeySig: ByteArray, // SPK signed by IK (Ed25519 or ECDSA)
        val oneTimePreKeyPub: ByteArray? // OPK public (optional)
    )

    data class InitialMessage(
        val senderIdentityPub: ByteArray, // IK_A public
        val senderEphemeralPub: ByteArray, // EK_A public
        val usedOneTimePreKey: Boolean,
        val sharedSecret: ByteArray // SK (not transmitted — derived independently)
    )

    // ── Generate Key Bundle (done once per user, stored on USB) ───────────

    fun generateKeyBundle(identityPrivKey: PrivateKey, identityPubKey: PublicKey): Pair<KeyBundle, Map<String, KeyPair>> {
        // Generate Signed PreKey
        val spkPair = generateECPair()
        val spkSig = sign(spkPair.public.encoded, identityPrivKey)

        // Generate One-Time PreKey
        val opkPair = generateECPair()

        val bundle = KeyBundle(
            identityKeyPub = identityPubKey.encoded,
            signedPreKeyPub = spkPair.public.encoded,
            signedPreKeySig = spkSig,
            oneTimePreKeyPub = opkPair.public.encoded
        )

        // Return private keys for storage (encrypted on USB)
        val privateKeys = mapOf(
            "spk" to spkPair,
            "opk" to opkPair
        )

        return Pair(bundle, privateKeys)
    }

    // ── Sender: Compute Shared Secret ─────────────────────────────────────

    /**
     * Alice runs this to initiate a session with Bob.
     *
     * @param myIdentityKey  Alice's long-term identity keypair
     * @param bobBundle      Bob's published key bundle
     * @return InitialMessage containing ephemeral public key + computed SK
     */
    fun senderAgreement(
        myIdentityKey: KeyPair,
        bobBundle: KeyBundle
    ): InitialMessage? {
        // Verify Bob's SPK signature
        val bobIdentityPub = decodePublicKey(bobBundle.identityKeyPub) ?: return null
        if (!verify(bobBundle.signedPreKeyPub, bobBundle.signedPreKeySig, bobIdentityPub)) {
            return null // signature verification failed — bundle tampered
        }

        val bobSPK = decodePublicKey(bobBundle.signedPreKeyPub) ?: return null
        val bobOPK = bobBundle.oneTimePreKeyPub?.let { decodePublicKey(it) }

        // Generate ephemeral key
        val ephemeralKey = generateECPair()

        // Four ECDH computations
        val dh1 = ecdh(myIdentityKey.private, bobSPK)
        val dh2 = ecdh(ephemeralKey.private, bobIdentityPub)
        val dh3 = ecdh(ephemeralKey.private, bobSPK)
        val dh4 = if (bobOPK != null) ecdh(ephemeralKey.private, bobOPK) else null

        val sharedSecret = kdf(dh1, dh2, dh3, dh4)

        // Wipe DH outputs
        dh1.fill(0); dh2.fill(0); dh3.fill(0); dh4?.fill(0)

        return InitialMessage(
            senderIdentityPub = myIdentityKey.public.encoded,
            senderEphemeralPub = ephemeralKey.public.encoded,
            usedOneTimePreKey = bobOPK != null,
            sharedSecret = sharedSecret
        )
    }

    // ── Receiver: Compute Same Shared Secret ──────────────────────────────

    /**
     * Bob runs this when he receives Alice's InitialMessage.
     *
     * @param myIdentityKey   Bob's long-term identity keypair
     * @param mySignedPreKey  Bob's SPK keypair
     * @param myOneTimePreKey Bob's OPK keypair (if used)
     * @param msg             Alice's InitialMessage
     * @return 32-byte shared secret (same as Alice computed) or null if invalid
     */
    fun receiverAgreement(
        myIdentityKey: KeyPair,
        mySignedPreKey: KeyPair,
        myOneTimePreKey: KeyPair?,
        msg: InitialMessage
    ): ByteArray? {
        val aliceIdentityPub = decodePublicKey(msg.senderIdentityPub) ?: return null
        val aliceEphemeralPub = decodePublicKey(msg.senderEphemeralPub) ?: return null

        val dh1 = ecdh(mySignedPreKey.private, aliceIdentityPub)
        val dh2 = ecdh(myIdentityKey.private, aliceEphemeralPub)
        val dh3 = ecdh(mySignedPreKey.private, aliceEphemeralPub)
        val dh4 = if (msg.usedOneTimePreKey && myOneTimePreKey != null) {
            ecdh(myOneTimePreKey.private, aliceEphemeralPub)
        } else {
            null
        }

        val sharedSecret = kdf(dh1, dh2, dh3, dh4)
        dh1.fill(0); dh2.fill(0); dh3.fill(0); dh4?.fill(0)
        return sharedSecret
    }

    // ── Private ────────────────────────────────────────────────────────────

    private fun ecdh(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv); ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    /**
     * KDF per Signal spec:
     *   SK = HKDF(F || DH1 || DH2 || DH3 [|| DH4], salt=0, info)
     *   where F is 32 0xFF bytes (domain separator)
     */
    private fun kdf(dh1: ByteArray, dh2: ByteArray, dh3: ByteArray, dh4: ByteArray?): ByteArray {
        val ikm = F_BYTES + dh1 + dh2 + dh3 + (dh4 ?: ByteArray(0))
        val salt = ByteArray(32) // zero salt
        val prk = hmac(salt, ikm)
        val okm = hkdfExpand(prk, KDF_INFO.toByteArray(), 32)
        prk.fill(0); ikm.fill(0)
        return okm
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
        val out = mutableListOf<Byte>(); var t = ByteArray(0); var n = 1
        while (out.size < len) { t = hmac(prk, t + info + byteArrayOf(n.toByte())); out.addAll(t.toList()); n++ }
        return out.take(len).toByteArray()
    }

    private fun sign(data: ByteArray, privKey: PrivateKey): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privKey); sig.update(data); return sig.sign()
    }

    private fun verify(data: ByteArray, signature: ByteArray, pubKey: PublicKey): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pubKey); sig.update(data); sig.verify(signature)
        } catch (e: Exception) { false }
    }

    private fun generateECPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(CURVE), rng)
        return kpg.generateKeyPair()
    }

    private fun decodePublicKey(encoded: ByteArray): PublicKey? {
        return try { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)) } catch (e: Exception) { null }
    }
}
