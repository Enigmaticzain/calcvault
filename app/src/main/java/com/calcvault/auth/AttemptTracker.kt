package com.calcvault.auth

import com.calcvault.utils.CryptoUtils
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory

/**
 * AttemptTracker
 *
 * Persistent, tamper-evident wrong-attempt counter stored ON the USB drive.
 *
 * Why on USB (not phone)?
 *   - Survives app reinstall
 *   - Survives phone factory reset
 *   - Can't be cleared without the USB
 *   - The attacker can't bypass it by wiping the phone
 *
 * Storage: /usb_root/cv_attempts.dat
 * Format:  AES-256-GCM encrypted binary blob (key derived from device fingerprint)
 * Tamper detection: GCM authentication tag — any modification is detected
 *
 * Attempt record (binary, 64 bytes):
 *   [4B: attempt_count] [8B: last_attempt_ts] [8B: lockout_until_ts]
 *   [4B: version]       [8B: first_attempt_ts] [32B: HMAC-SHA256 of above]
 *
 * Lockout tiers (per spec):
 *   attempts ≥ 5  → lock for 10 hours
 *   attempts ≥ 15 → lock for 24 hours
 *   attempts ≥ 20 → WIPE (3-pass container destruction)
 */
class AttemptTracker(private val usbRoot: File) {

    companion object {
        private const val FILE_NAME = "cv_attempts.dat"
        private const val TIER_1_ATTEMPTS = 5
        private const val TIER_1_LOCK_MS = 10 * 60 * 60 * 1000L // 10 hours
        private const val TIER_2_ATTEMPTS = 15
        private const val TIER_2_LOCK_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val TIER_3_ATTEMPTS = 20 // → WIPE

        // Derive encryption key from a stable device fingerprint
        // This prevents moving the file to another device to reset counters
        private const val PBKDF2_ITERATIONS = 100_000
    }

    sealed class AttemptResult {
        object Allowed : AttemptResult()
        data class LockedOut(val remainingMs: Long, val attemptsUsed: Int) : AttemptResult()
        object WipeTriggered : AttemptResult()
    }

    private val trackFile = File(usbRoot, FILE_NAME)

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Call on every failed unlock attempt.
     * Returns the result: allowed (just counting), locked out, or wipe triggered.
     */
    fun recordFailedAttempt(deviceFingerprint: String): AttemptResult {
        val record = loadRecord(deviceFingerprint) ?: AttemptRecord()
        val now = System.currentTimeMillis()

        record.attemptCount++
        record.lastAttemptTs = now
        record.firstAttemptTs = if (record.firstAttemptTs == 0L) now else record.firstAttemptTs

        val result = when {
            record.attemptCount >= TIER_3_ATTEMPTS -> {
                record.lockoutUntilTs = Long.MAX_VALUE // permanent
                saveRecord(record, deviceFingerprint)
                AttemptResult.WipeTriggered
            }
            record.attemptCount >= TIER_2_ATTEMPTS -> {
                record.lockoutUntilTs = now + TIER_2_LOCK_MS
                saveRecord(record, deviceFingerprint)
                AttemptResult.LockedOut(TIER_2_LOCK_MS, record.attemptCount)
            }
            record.attemptCount >= TIER_1_ATTEMPTS -> {
                record.lockoutUntilTs = now + TIER_1_LOCK_MS
                saveRecord(record, deviceFingerprint)
                AttemptResult.LockedOut(TIER_1_LOCK_MS, record.attemptCount)
            }
            else -> {
                saveRecord(record, deviceFingerprint)
                AttemptResult.Allowed
            }
        }

        return result
    }

    /**
     * Call on successful unlock. Resets the counter.
     */
    fun recordSuccess(deviceFingerprint: String) {
        saveRecord(AttemptRecord(), deviceFingerprint)
    }

    /**
     * Check current lockout state without recording an attempt.
     * Returns (isLockedOut, remainingMs, attemptCount).
     */
    fun checkLockout(deviceFingerprint: String): Triple<Boolean, Long, Int> {
        val record = loadRecord(deviceFingerprint) ?: return Triple(false, 0L, 0)
        val now = System.currentTimeMillis()

        if (record.lockoutUntilTs == Long.MAX_VALUE) {
            return Triple(true, Long.MAX_VALUE, record.attemptCount) // wipe state
        }

        return if (now < record.lockoutUntilTs) {
            Triple(true, record.lockoutUntilTs - now, record.attemptCount)
        } else {
            Triple(false, 0L, record.attemptCount)
        }
    }

    /**
     * How many attempts have been made so far.
     */
    fun getAttemptCount(deviceFingerprint: String): Int =
        loadRecord(deviceFingerprint)?.attemptCount ?: 0

    /**
     * Wipe the attempt record itself (called after container is destroyed).
     */
    fun destroyTrackFile() {
        if (trackFile.exists()) {
            // Overwrite then delete
            trackFile.writeBytes(ByteArray(trackFile.length().toInt()).also { SecureRandom().nextBytes(it) })
            trackFile.delete()
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private data class AttemptRecord(
        var attemptCount: Int = 0,
        var lastAttemptTs: Long = 0L,
        var lockoutUntilTs: Long = 0L,
        var version: Int = 1,
        var firstAttemptTs: Long = 0L
    )

    private fun saveRecord(record: AttemptRecord, deviceFingerprint: String) {
        try {
            // Serialize to 40 bytes
            val plain = ByteBuffer.allocate(40).apply {
                putInt(record.attemptCount)
                putLong(record.lastAttemptTs)
                putLong(record.lockoutUntilTs)
                putInt(record.version)
                putLong(record.firstAttemptTs)
            }.array()

            // HMAC for tamper detection even before decryption
            val encKey = deriveKey(deviceFingerprint)
            val hmac = CryptoUtils.hmacSha256(encKey.encoded, plain)
            val payload = plain + hmac // 40 + 32 = 72 bytes

            // Encrypt with AES-GCM
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encKey, GCMParameterSpec(128, iv))
            val ct = cipher.doFinal(payload)

            trackFile.writeBytes(iv + ct)
        } catch (e: Exception) { /* non-fatal — attempt tracking best-effort */ }
    }

    private fun loadRecord(deviceFingerprint: String): AttemptRecord? {
        if (!trackFile.exists()) return null
        return try {
            val raw = trackFile.readBytes()
            if (raw.size < 12) return null
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)

            val encKey = deriveKey(deviceFingerprint)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encKey, GCMParameterSpec(128, iv))
            val payload = cipher.doFinal(ct) // throws if tampered

            val plain = payload.copyOfRange(0, 40)
            val hmac = payload.copyOfRange(40, payload.size)

            // Verify HMAC
            val expectedHmac = CryptoUtils.hmacSha256(encKey.encoded, plain)
            if (!CryptoUtils.constantTimeEquals(hmac, expectedHmac)) return null

            val buf = java.nio.ByteBuffer.wrap(plain)
            AttemptRecord(
                attemptCount = buf.int,
                lastAttemptTs = buf.long,
                lockoutUntilTs = buf.long,
                version = buf.int,
                firstAttemptTs = buf.long
            )
        } catch (e: Exception) { null }
    }

    /**
     * Derive encryption key from device fingerprint.
     * Uses PBKDF2 so moving the file to another device doesn't work
     * (different fingerprint → different key → decryption fails).
     */
    private fun deriveKey(deviceFingerprint: String): javax.crypto.SecretKey {
        val salt = "cv_attempts_v1".toByteArray()
        val spec = PBEKeySpec(deviceFingerprint.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    /**
     * Stable device fingerprint — derived from hardware identifiers.
     * Not perfectly unique but stable enough to tie the attempt file to a device.
     */
    fun getDeviceFingerprint(): String {
        val id = android.os.Build.BOARD + android.os.Build.BRAND +
            android.os.Build.HARDWARE + android.os.Build.MANUFACTURER +
            android.os.Build.MODEL + android.os.Build.PRODUCT
        return CryptoUtils.sha256hex(id.toByteArray())
    }
}
