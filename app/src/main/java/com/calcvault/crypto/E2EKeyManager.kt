package com.calcvault.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.*
import javax.crypto.spec.*
import java.util.Base64

/**
 * E2EKeyManager (V6 - Enhanced Privacy & Randomized Padding)
 */
class E2EKeyManager private constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val IDENTITY_KEY_ALIAS = "cv_identity_ec"
        private const val ALGORITHM_EC = "EC"
        private const val ALGORITHM_ECDH = "ECDH"
        private const val CURVE = "secp256r1"
        private const val HKDF_INFO = "calcvault-session-v6"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_LEN = 128
        private const val SESSION_ROTATION_MS = 3600000 // 1 hour

        @Volatile private var instance: E2EKeyManager? = null
        fun getInstance(): E2EKeyManager =
            instance ?: synchronized(this) { instance ?: E2EKeyManager().also { instance = it } }
    }

    private var sessionKey: SecretKey? = null
    private var sessionEstablishedAt: Long = 0
    private var ephemeralKeyPair: KeyPair? = null
    private var partnerPublicKey: PublicKey? = null
    private var forceRotation: Boolean = false

    fun generateIdentityKeypair() {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (ks.containsAlias(IDENTITY_KEY_ALIAS)) return

        // PURPOSE_AGREE_KEY (64) is only supported on API 31+ (Android 12)
        // Using it on older versions causes "unknown purpose: 64" exception.
        var purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            purposes = purposes or KeyProperties.PURPOSE_AGREE_KEY
        }

        try {
            val spec = KeyGenParameterSpec.Builder(IDENTITY_KEY_ALIAS, purposes)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setUserAuthenticationRequired(false)
                .build()

            KeyPairGenerator.getInstance(ALGORITHM_EC, KEYSTORE_PROVIDER)
                .apply { initialize(spec) }
                .generateKeyPair()
        } catch (e: Exception) {
            // Fallback for devices where even standard EC generation might fail in KeyStore
            // or if the specific combination of parameters is rejected.
            if (purposes and KeyProperties.PURPOSE_AGREE_KEY != 0) {
                // Try again without PURPOSE_AGREE_KEY if it failed
                val fallbackPurposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                val fallbackSpec = KeyGenParameterSpec.Builder(IDENTITY_KEY_ALIAS, fallbackPurposes)
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setUserAuthenticationRequired(false)
                    .build()
                KeyPairGenerator.getInstance(ALGORITHM_EC, KEYSTORE_PROVIDER)
                    .apply { initialize(fallbackSpec) }
                    .generateKeyPair()
            } else {
                throw e
            }
        }
    }

    fun getDeviceId(): String {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val pub = ks.getCertificate(IDENTITY_KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("Identity keypair not generated")
        val hash = MessageDigest.getInstance("SHA-256").digest(pub.encoded)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun getKeyFingerprint(): String {
        val deviceId = getDeviceId()
        return deviceId.chunked(4).joinToString(":")
    }

    fun startEphemeralExchange(): String {
        val kpg = KeyPairGenerator.getInstance(ALGORITHM_EC)
        kpg.initialize(ECGenParameterSpec(CURVE), SecureRandom())
        ephemeralKeyPair = kpg.generateKeyPair()
        return Base64.getEncoder().encodeToString(ephemeralKeyPair!!.public.encoded)
    }

    fun completeEphemeralExchange(partnerEphemeralPubKeyBase64: String) {
        val keyBytes = Base64.getDecoder().decode(partnerEphemeralPubKeyBase64)
        val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
        val partnerKey = KeyFactory.getInstance(ALGORITHM_EC).generatePublic(keySpec)

        val localPriv = ephemeralKeyPair?.private
            ?: throw IllegalStateException("Call startEphemeralExchange first")

        val ka = KeyAgreement.getInstance(ALGORITHM_ECDH)
        ka.init(localPriv)
        ka.doPhase(partnerKey, true)
        val sharedSecret = ka.generateSecret()

        sessionKey = deriveFreshSessionKey(sharedSecret)
        sessionEstablishedAt = System.currentTimeMillis()
        forceRotation = false

        sharedSecret.fill(0)
        ephemeralKeyPair = null
    }

    private fun deriveFreshSessionKey(ikm: ByteArray): SecretKey {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val info = HKDF_INFO.toByteArray(Charsets.UTF_8)
        val okm = mac.doFinal(info + byteArrayOf(0x01))

        return SecretKeySpec(okm.copyOf(32), "AES")
    }

    fun needsRotation(): Boolean {
        if (sessionKey == null) return true
        if (forceRotation) return true
        return (System.currentTimeMillis() - sessionEstablishedAt) > SESSION_ROTATION_MS
    }

    fun triggerRotation() {
        forceRotation = true
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Handshake required")
        val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(GCM_TAG_LEN, iv)

        // Requirement 1: Randomized Adaptive Padding
        val paddedPlaintext = applyAdaptivePadding(plaintext)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        return iv + cipher.doFinal(paddedPlaintext)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("No active session key")
        val iv = blob.copyOfRange(0, GCM_IV_LEN)
        val ct = blob.copyOfRange(GCM_IV_LEN, blob.size)
        val spec = GCMParameterSpec(GCM_TAG_LEN, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val padded = cipher.doFinal(ct)
        return removeAdaptivePadding(padded)
    }

    /**
     * Requirement 1: Randomized Adaptive Padding.
     * Blends message size to obscure content type and length.
     * Uses a range-based randomized padding where the last 2 bytes store the padding length.
     */
    private fun applyAdaptivePadding(data: ByteArray): ByteArray {
        val random = SecureRandom()
        // Random padding between 32 and 512 bytes, plus alignment to 256-byte blocks
        val minPadding = 32
        val basePadding = minPadding + random.nextInt(256)
        val totalSize = data.size + basePadding + 2
        val alignedSize = ((totalSize + 255) / 256) * 256
        val paddingLen = alignedSize - data.size - 2

        val result = ByteArray(alignedSize)
        System.arraycopy(data, 0, result, 0, data.size)

        // Fill padding with random bytes instead of zeros or fixed patterns
        val padBytes = ByteArray(paddingLen)
        random.nextBytes(padBytes)
        System.arraycopy(padBytes, 0, result, data.size, paddingLen)

        // Store padding length in last 2 bytes (Big Endian)
        result[alignedSize - 2] = (paddingLen shr 8).toByte()
        result[alignedSize - 1] = (paddingLen and 0xFF).toByte()

        return result
    }

    private fun removeAdaptivePadding(padded: ByteArray): ByteArray {
        if (padded.size < 2) return padded
        val paddingLen = ((padded[padded.size - 2].toInt() and 0xFF) shl 8) or (padded[padded.size - 1].toInt() and 0xFF)

        if (paddingLen < 0 || paddingLen > padded.size - 2) return padded
        return padded.copyOfRange(0, padded.size - paddingLen - 2)
    }

    fun hasActiveSession(): Boolean = sessionKey != null && !needsRotation()

    fun clearSession() {
        sessionKey = null
        ephemeralKeyPair = null
        forceRotation = true
    }
}
