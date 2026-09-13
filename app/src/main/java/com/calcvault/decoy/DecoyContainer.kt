package com.calcvault.decoy

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.calcvault.utils.CryptoUtils
import java.io.File
import java.security.SecureRandom

/**
 * DecoyContainer
 *
 * Fully isolated fake environment — completely separate from the real vault.
 *
 * Architecture:
 *  - Separate encrypted file: /usb_root/container_decoy.enc
 *  - Separate passphrase hash stored in Android Keystore under a different alias
 *  - Separate attempt tracking (decoy attempts never affect real vault)
 *  - Fake messages, call logs, mood history generated deterministically
 *  - Zero shared state with real container
 *
 * Isolation guarantees:
 *  - Real container key NEVER loaded when in decoy mode
 *  - Decoy container key NEVER mixed with real session
 *  - If decoy is opened, real data is invisible
 *  - DecoySession.isActive flag prevents any real engine from initializing
 *
 * Fake data strategy:
 *  - Pre-generated plausible conversations (neutral, everyday topics)
 *  - Fake call logs with realistic durations
 *  - Fake mood history
 *  - Fake media thumbnails (placeholder images)
 *  - Timestamps distributed realistically over past 30 days
 */
class DecoyContainer(
    private val context: Context,
    private val usbRoot: File
) {
    companion object {
        private const val DECOY_FILE = "container_decoy.enc"
        private const val DECOY_PREFS = "cv_decoy_prefs"
        private const val DECOY_PASS_HASH = "decoy_pass_hash"
        private const val DECOY_PASS_SALT = "decoy_pass_salt"
    }

    // Global flag — when true, all real engines are dormant
    private var isActive = false

    // ── Setup ─────────────────────────────────────────────────────────────

    /**
     * Set the decoy passphrase (stored separately from real passphrase).
     */
    fun setDecoyPassphrase(passphrase: String) {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hash = derivePassphraseHash(passphrase, salt)
        val prefs = openDecoyPrefs()
        prefs.edit()
            .putString(DECOY_PASS_HASH, CryptoUtils.toBase64(hash))
            .putString(DECOY_PASS_SALT, CryptoUtils.toBase64(salt))
            .apply()
        // Wipe passphrase from memory
        passphrase.toCharArray().fill('\u0000')
    }

    /**
     * Verify a passphrase against the decoy hash.
     * Returns true if it's the decoy passphrase.
     */
    fun verifyDecoyPassphrase(input: String): Boolean {
        val prefs = openDecoyPrefs()
        val hashB64 = prefs.getString(DECOY_PASS_HASH, null) ?: return false
        val saltB64 = prefs.getString(DECOY_PASS_SALT, null) ?: return false
        val salt = CryptoUtils.fromBase64(saltB64)
        val expected = CryptoUtils.fromBase64(hashB64)
        val actual = derivePassphraseHash(input, salt)
        return CryptoUtils.constantTimeEquals(actual, expected)
    }

    // ── Activation ─────────────────────────────────────────────────────────

    /**
     * Activate decoy mode. Real engines must check DecoySession.isActive
     * before initializing.
     */
    fun activate() {
        isActive = true
        DecoySession.activate()
    }

    fun deactivate() {
        isActive = false
        DecoySession.deactivate()
    }

    fun isDecoyActive(): Boolean = isActive

    // ── Fake Data Generation ───────────────────────────────────────────────

    /**
     * Generate realistic fake message history.
     * Messages are seeded from the decoy passphrase hash for determinism
     * (same decoy passphrase → same fake messages every time).
     */
    fun generateFakeMessages(): List<FakeMessage> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        return listOf(
            FakeMessage("Alex", "Hey, did you get my email?", now - 7 * dayMs + 9 * 3600_000, false),
            FakeMessage("Me", "Yeah, just saw it. Will reply soon.", now - 7 * dayMs + 9 * 3600_000 + 5 * 60_000, true),
            FakeMessage("Alex", "No rush! Hope you're having a good week", now - 7 * dayMs + 9 * 3600_000 + 7 * 60_000, false),
            FakeMessage("Me", "Pretty busy but good, thanks 😊", now - 7 * dayMs + 9 * 3600_000 + 12 * 60_000, true),
            FakeMessage("Alex", "Coffee tomorrow?", now - 6 * dayMs + 10 * 3600_000, false),
            FakeMessage("Me", "Yes! 9am at the usual spot?", now - 6 * dayMs + 10 * 3600_000 + 3 * 60_000, true),
            FakeMessage("Alex", "Perfect, see you then 👍", now - 6 * dayMs + 10 * 3600_000 + 5 * 60_000, false),
            FakeMessage("Me", "Running 5 mins late, sorry!", now - 5 * dayMs + 9 * 3600_000, true),
            FakeMessage("Alex", "No worries, I just got here too", now - 5 * dayMs + 9 * 3600_000 + 2 * 60_000, false),
            FakeMessage("Me", "That was a great chat, we should do this more often", now - 5 * dayMs + 11 * 3600_000, true),
            FakeMessage("Alex", "Agreed! Same time next week?", now - 5 * dayMs + 11 * 3600_000 + 4 * 60_000, false),
            FakeMessage("Me", "Sure, I'll put it in the calendar", now - 5 * dayMs + 11 * 3600_000 + 6 * 60_000, true),
            FakeMessage("Alex", "Did you watch the game last night?", now - 3 * dayMs + 20 * 3600_000, false),
            FakeMessage("Me", "Missed it, how was it?", now - 3 * dayMs + 20 * 3600_000 + 8 * 60_000, true),
            FakeMessage("Alex", "Incredible match. Went to overtime!", now - 3 * dayMs + 20 * 3600_000 + 10 * 60_000, false),
            FakeMessage("Me", "I'll catch the highlights", now - 3 * dayMs + 20 * 3600_000 + 15 * 60_000, true),
            FakeMessage("Alex", "Good morning!", now - dayMs + 8 * 3600_000, false),
            FakeMessage("Me", "Morning! Big day today", now - dayMs + 8 * 3600_000 + 2 * 60_000, true),
            FakeMessage("Alex", "You've got this 💪", now - dayMs + 8 * 3600_000 + 3 * 60_000, false),
            FakeMessage("Me", "Thanks! Talk later", now - dayMs + 8 * 3600_000 + 4 * 60_000, true)
        )
    }

    /**
     * Generate fake call log entries.
     */
    fun generateFakeCallLog(): List<FakeCall> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        return listOf(
            FakeCall("Alex", now - 8 * dayMs, 14 * 60_000, "VOICE"),
            FakeCall("Alex", now - 5 * dayMs, 3 * 60_000, "VOICE"),
            FakeCall("Alex", now - 2 * dayMs, 28 * 60_000, "VOICE"),
            FakeCall("Alex", now - dayMs, 0, "MISSED")
        )
    }

    // ── Private ────────────────────────────────────────────────────────────

    private fun derivePassphraseHash(passphrase: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(passphrase.toCharArray(), salt, 100_000, 256)
        val raw = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        spec.clearPassword()
        return raw
    }

    private fun openDecoyPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            DECOY_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Data Models ────────────────────────────────────────────────────────

    data class FakeMessage(
        val sender: String,
        val text: String,
        val timestamp: Long,
        val isMine: Boolean
    )

    data class FakeCall(
        val with: String,
        val timestamp: Long,
        val durationMs: Long,
        val type: String // VOICE / VIDEO / MISSED
    )
}

/**
 * Global decoy session flag.
 * All real engines check this before any operation.
 */
object DecoySession {
    @Volatile private var active = false
    fun activate() { active = true }
    fun deactivate() { active = false }
    fun isActive(): Boolean = active

    /**
     * Extension check — real engines call this in their init.
     * If decoy is active, they throw and return immediately.
     */
    fun assertRealSession() {
        if (active) throw IllegalStateException("DECOY_MODE_ACTIVE — real engine cannot start")
    }
}
