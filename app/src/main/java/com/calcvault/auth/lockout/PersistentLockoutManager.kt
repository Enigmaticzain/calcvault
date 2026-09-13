package com.calcvault.auth.lockout

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
 * PersistentLockoutManager
 *
 * USB-based attempt tracking that survives:
 *  ✅ App restarts
 *  ✅ Phone reboots
 *  ✅ App uninstall/reinstall
 *  ✅ Phone factory reset (data is on USB, not phone)
 *
 * Stored at: /usb_root/cv_attempts.dat
 * Format: AES-256-GCM encrypted binary, key derived from device fingerprint
 * Tamper detection: GCM auth tag + HMAC of entire record
 *
 * Lockout tiers (exact per spec):
 *  ≥ 5  attempts → lock 10 hours
 *  ≥ 15 attempts → lock 24 hours
 *  ≥ 20 attempts → WIPE (call containerWipeCallback)
 *
 * Cross-restart enforcement:
 *  lockout_until timestamp is stored in the encrypted file.
 *  On every unlock attempt: read file → check current time vs lockout_until.
 *  Even after phone reboot, lock is still enforced.
 *
 * Wipe trigger:
 *  When attempt count reaches 20, wipeCallback is invoked.
 *  The attempt file itself is overwritten then deleted.
 *  The real wipe (3-pass container destruction) happens in USBStorageEngine.
 */
class PersistentLockoutManager(
    private val usbRoot: File,
    private val wipeCallback: () -> Unit // called when ≥20 attempts
) {
    companion object {
        private const val FILE = "cv_attempts.dat"
        private const val TIER1 = 5
        private const val TIER2 = 15
        private const val TIER3 = 20
        private const val TIER1_MS = 10L * 3600_000 // 10 hours
        private const val TIER2_MS = 24L * 3600_000 // 24 hours
        private const val PBKDF2_ITER = 150_000

        // Binary record: 4 + 8 + 8 + 4 + 8 = 32 bytes
        private const val RECORD_LEN = 32
    }

    private val trackFile = File(usbRoot, FILE)

    sealed class LockState {
        object Open : LockState()
        data class Locked(val remainingMs: Long, val attempts: Int) : LockState()
        object WipeTriggered : LockState()
    }

    // ── Check (before showing passphrase screen) ───────────────────────────

    fun checkState(fingerprint: String): LockState {
        val rec = read(fingerprint) ?: return LockState.Open
        if (rec.attempts >= TIER3) return LockState.WipeTriggered
        val remaining = rec.lockUntil - System.currentTimeMillis()
        return if (remaining > 0) {
            LockState.Locked(remaining, rec.attempts)
        } else {
            LockState.Open
        }
    }

    // ── Record failed attempt ──────────────────────────────────────────────

    fun recordFailure(fingerprint: String): LockState {
        val rec = read(fingerprint) ?: AttemptRecord()
        val now = System.currentTimeMillis()
        rec.attempts++
        rec.lastAttempt = now
        if (rec.firstAttempt == 0L) rec.firstAttempt = now

        return when {
            rec.attempts >= TIER3 -> {
                write(rec, fingerprint)
                selfDestruct()
                LockState.WipeTriggered
            }
            rec.attempts >= TIER2 -> {
                rec.lockUntil = now + TIER2_MS
                write(rec, fingerprint)
                LockState.Locked(TIER2_MS, rec.attempts)
            }
            rec.attempts >= TIER1 -> {
                rec.lockUntil = now + TIER1_MS
                write(rec, fingerprint)
                LockState.Locked(TIER1_MS, rec.attempts)
            }
            else -> {
                write(rec, fingerprint)
                LockState.Open
            }
        }
    }

    // ── Record success (reset counter) ────────────────────────────────────

    fun recordSuccess(fingerprint: String) {
        write(AttemptRecord(), fingerprint)
    }

    // ── Remaining info ────────────────────────────────────────────────────

    fun getAttemptCount(fingerprint: String): Int =
        read(fingerprint)?.attempts ?: 0

    fun getRemainingLockMs(fingerprint: String): Long {
        val rec = read(fingerprint) ?: return 0L
        return maxOf(0L, rec.lockUntil - System.currentTimeMillis())
    }

    // ── Private ────────────────────────────────────────────────────────────

    private class AttemptRecord(
        var attempts: Int = 0,
        var lastAttempt: Long = 0L,
        var lockUntil: Long = 0L,
        var version: Int = 1,
        var firstAttempt: Long = 0L
    )

    private fun write(rec: AttemptRecord, fingerprint: String) {
        try {
            val plain = ByteBuffer.allocate(RECORD_LEN).apply {
                putInt(rec.attempts); putLong(rec.lastAttempt)
                putLong(rec.lockUntil); putInt(rec.version); putLong(rec.firstAttempt)
            }.array()

            val key = deriveKey(fingerprint)
            val hmac = CryptoUtils.hmacSha256(key, plain)
            val blob = plain + hmac // 32 + 32 = 64 bytes

            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            trackFile.writeBytes(iv + c.doFinal(blob))

            plain.fill(0); hmac.fill(0); blob.fill(0)
        } catch (e: Exception) {}
    }

    private fun read(fingerprint: String): AttemptRecord? {
        if (!trackFile.exists()) return null
        return try {
            val raw = trackFile.readBytes()
            if (raw.size < 12) return null
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val key = deriveKey(fingerprint)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val blob = c.doFinal(ct) // throws if tampered

            val plain = blob.copyOfRange(0, RECORD_LEN)
            val storedHmac = blob.copyOfRange(RECORD_LEN, blob.size)
            val expectedHmac = CryptoUtils.hmacSha256(key, plain)
            if (!CryptoUtils.constantTimeEquals(storedHmac, expectedHmac)) return null

            val b = ByteBuffer.wrap(plain)
            AttemptRecord(b.int, b.long, b.long, b.int, b.long)
        } catch (e: Exception) { null }
    }

    private fun selfDestruct() {
        // Overwrite track file then delete
        try {
            if (trackFile.exists()) {
                val rng = SecureRandom()
                val rand = ByteArray(trackFile.length().toInt()).also { rng.nextBytes(it) }
                trackFile.writeBytes(rand); rand.fill(0); trackFile.delete()
            }
        } catch (e: Exception) {}
        // Trigger container wipe
        wipeCallback()
    }

    private fun deriveKey(fingerprint: String): ByteArray {
        val salt = "cv_lockout_v1".toByteArray()
        val spec = PBEKeySpec(fingerprint.toCharArray(), salt, PBKDF2_ITER, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return raw
    }

    fun deviceFingerprint(): String {
        val raw = android.os.Build.BOARD + android.os.Build.BRAND + android.os.Build.HARDWARE + android.os.Build.MANUFACTURER + android.os.Build.MODEL + android.os.Build.PRODUCT
        return CryptoUtils.sha256hex(raw.toByteArray())
    }
}
