package com.calcvault.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoUtils
 *
 * Shared cryptographic utilities used across all CalcVault modules.
 *
 * All operations are stateless and use standard Java/Android crypto APIs.
 * No third-party crypto dependencies — pure JCE.
 */
object CryptoUtils {

    private val RNG = SecureRandom()

    // ─── Hashing ───────────────────────────────────────────────────────────

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256(text: String): ByteArray = sha256(text.toByteArray(Charsets.UTF_8))

    fun sha256hex(data: ByteArray): String =
        sha256(data).joinToString("") { "%02x".format(it) }

    fun sha256b64(data: ByteArray): String =
        Base64.getEncoder().encodeToString(sha256(data))

    fun sha512(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").digest(data)

    // ─── HMAC ──────────────────────────────────────────────────────────────

    /**
     * HMAC-SHA256 for message authentication.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hmacVerify(key: ByteArray, data: ByteArray, expected: ByteArray): Boolean =
        hmacSha256(key, data).contentEquals(expected)

    // ─── Random ────────────────────────────────────────────────────────────

    fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also { RNG.nextBytes(it) }

    fun randomLong(): Long = RNG.nextLong()

    fun randomHex(byteCount: Int): String =
        randomBytes(byteCount).joinToString("") { "%02x".format(it) }

    /**
     * Generate a human-readable recovery key.
     * Format: XXXXXX-XXXXXX-XXXXXX-XXXXXX (base62 chunks)
     */
    fun generateRecoveryKey(): String {
        val bytes = randomBytes(24)
        return Base64.getEncoder().encodeToString(bytes)
            .replace("+", "A").replace("/", "B").replace("=", "")
            .uppercase()
            .chunked(6)
            .take(4)
            .joinToString("-")
    }

    /**
     * Generate a short device ID (16 hex chars).
     */
    fun generateDeviceId(): String = randomHex(8)

    // ─── Encoding ──────────────────────────────────────────────────────────

    fun toBase64(data: ByteArray): String =
        Base64.getEncoder().encodeToString(data)

    fun fromBase64(encoded: String): ByteArray =
        Base64.getDecoder().decode(encoded)

    fun toHex(data: ByteArray): String =
        data.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        check(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // ─── Secure Wipe ───────────────────────────────────────────────────────

    /**
     * Overwrite a byte array with zeros before GC.
     * Call on any sensitive key material after use.
     */
    fun wipe(data: ByteArray) { data.fill(0) }

    /**
     * Overwrite a char array (e.g. passphrase) before GC.
     */
    fun wipe(data: CharArray) { data.fill('\u0000') }

    // ─── Constant-time comparison ──────────────────────────────────────────

    /**
     * Compare two byte arrays in constant time (prevents timing attacks).
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
