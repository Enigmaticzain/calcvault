package com.calcvault.companion

import com.calcvault.utils.CryptoUtils
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class CompanionEphemeralKeyPair(
    val keyPair: KeyPair,
    val publicDerBase64: String
)

object CompanionSessionCrypto {

    private const val HKDF_INFO = "calcvault-session-v1"
    private const val NONCE_MAGIC = 0x43564231

    fun generateEphemeralX25519(): CompanionEphemeralKeyPair {
        val generator = runCatching { KeyPairGenerator.getInstance("X25519") }
            .getOrElse {
                val fallback = KeyPairGenerator.getInstance("XDH")
                buildNamedParameterSpec("X25519")?.let { paramSpec ->
                    runCatching { fallback.initialize(paramSpec) }
                }
                fallback
            }

        val pair = generator.generateKeyPair()
        return CompanionEphemeralKeyPair(
            keyPair = pair,
            publicDerBase64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        )
    }

    fun deriveSessionKey(
        privateKey: PrivateKey,
        peerPublicDerBase64: String,
        pairingCode: String,
        saltParts: List<String>
    ): ByteArray {
        val peerPublic = decodePeerPublicKey(peerPublicDerBase64)
        val keyAgreement = runCatching { KeyAgreement.getInstance("X25519") }
            .getOrElse { KeyAgreement.getInstance("XDH") }
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublic, true)
        val sharedSecret = keyAgreement.generateSecret()

        val saltDigest = MessageDigest.getInstance("SHA-256")
        saltDigest.update(pairingCode.toByteArray(Charsets.UTF_8))
        saltParts.forEach { saltDigest.update(it.toByteArray(Charsets.UTF_8)) }
        val salt = saltDigest.digest()

        val sessionKey = hkdfSha256(
            ikm = sharedSecret,
            salt = salt,
            info = HKDF_INFO.toByteArray(Charsets.UTF_8),
            length = 32
        )

        sharedSecret.fill(0)
        salt.fill(0)
        return sessionKey
    }

    fun computePairingProof(
        pairingCode: String,
        sessionId: String,
        pcPub: String,
        phonePub: String,
        pcNonce: String,
        phoneNonce: String,
        deviceId: String
    ): String {
        val transcript = listOf(sessionId, pcPub, phonePub, pcNonce, phoneNonce, deviceId).joinToString("|")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pairingCode.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(transcript.toByteArray(Charsets.UTF_8)))
    }

    fun safeEqualsBase64(expected: String, actual: String): Boolean {
        val a = runCatching { Base64.getDecoder().decode(expected) }.getOrNull() ?: return false
        val b = runCatching { Base64.getDecoder().decode(actual) }.getOrNull() ?: return false
        if (a.size != b.size) return false
        return MessageDigest.isEqual(a, b)
    }

    fun sha256Base64(text: String): String {
        return Base64.getEncoder().encodeToString(CryptoUtils.sha256(text.toByteArray(Charsets.UTF_8)))
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = hmac.doFinal(ikm)

        val output = ByteArray(length)
        var previous = ByteArray(0)
        var generated = 0
        var counter = 1

        while (generated < length) {
            hmac.init(SecretKeySpec(prk, "HmacSHA256"))
            hmac.update(previous)
            hmac.update(info)
            hmac.update(counter.toByte())
            val block = hmac.doFinal()
            val copyLen = minOf(block.size, length - generated)
            System.arraycopy(block, 0, output, generated, copyLen)
            generated += copyLen
            previous = block
            counter += 1
        }

        prk.fill(0)
        return output
    }

    private fun buildNamedParameterSpec(name: String): AlgorithmParameterSpec? {
        return runCatching {
            val clazz = Class.forName("java.security.spec.NamedParameterSpec")
            val ctor = clazz.getConstructor(String::class.java)
            ctor.newInstance(name) as AlgorithmParameterSpec
        }.getOrNull()
    }

    private fun decodePeerPublicKey(peerPublicDerBase64: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(peerPublicDerBase64)
        val spec = X509EncodedKeySpec(keyBytes)

        return runCatching {
            KeyFactory.getInstance("X25519").generatePublic(spec)
        }.getOrElse {
            KeyFactory.getInstance("XDH").generatePublic(spec)
        }
    }

    class SessionCipher(
        private val sessionId: String,
        sessionKey: ByteArray,
        private val sendDirection: Int,
        private val recvDirection: Int
    ) {
        private val key = SecretKeySpec(sessionKey.copyOf(), "AES")
        private var sendSeq = 1
        private var recvSeq = 0

        fun encrypt(payload: JSONObject): JSONObject {
            val seq = sendSeq
            sendSeq += 1

            val nonce = buildDirectionalNonce(seq, sendDirection)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val ciphertextWithTag = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
            val ctLen = ciphertextWithTag.size - 16
            val ct = ciphertextWithTag.copyOfRange(0, ctLen)
            val tag = ciphertextWithTag.copyOfRange(ctLen, ciphertextWithTag.size)

            return JSONObject()
                .put("type", "enc")
                .put("sid", sessionId)
                .put("seq", seq)
                .put("iv", Base64.getEncoder().encodeToString(nonce))
                .put("ct", Base64.getEncoder().encodeToString(ct))
                .put("tag", Base64.getEncoder().encodeToString(tag))
        }

        fun decrypt(envelope: JSONObject): JSONObject {
            if (envelope.optString("type") != "enc") {
                throw IllegalArgumentException("invalid_envelope")
            }

            val seq = envelope.optInt("seq", 0)
            if (seq <= recvSeq) {
                throw IllegalStateException("replay_detected")
            }

            val nonce = Base64.getDecoder().decode(envelope.optString("iv", ""))
            val expected = buildDirectionalNonce(seq, recvDirection)
            if (!nonce.contentEquals(expected)) {
                throw IllegalStateException("nonce_mismatch")
            }

            val ct = Base64.getDecoder().decode(envelope.optString("ct", ""))
            val tag = Base64.getDecoder().decode(envelope.optString("tag", ""))
            val blob = ByteArray(ct.size + tag.size).also {
                System.arraycopy(ct, 0, it, 0, ct.size)
                System.arraycopy(tag, 0, it, ct.size, tag.size)
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            val plain = cipher.doFinal(blob)
            recvSeq = seq
            return JSONObject(String(plain, Charsets.UTF_8))
        }

        private fun buildDirectionalNonce(seq: Int, direction: Int): ByteArray {
            return ByteBuffer.allocate(12).apply {
                putInt(NONCE_MAGIC)
                put(direction.toByte())
                put(ByteArray(3))
                putInt(seq)
            }.array()
        }
    }
}
