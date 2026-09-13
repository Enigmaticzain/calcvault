package com.calcvault.storage.provider

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64

/**
 * LocalKeyStore
 *
 * Manages the PBKDF2 salt used to derive the master key for Local mode.
 *
 * Problem solved:
 *  LocalStorageProvider receives a masterKey parameter, but where does
 *  the key come from on restart? The passphrase is typed by the user,
 *  but the PBKDF2 salt must be the SAME salt used when the files were
 *  originally encrypted — otherwise decryption fails.
 *
 * Solution:
 *  Salt is generated once on first run, then stored in EncryptedSharedPreferences
 *  (backed by Android Keystore). This is hardware-protected on modern devices.
 *
 *  On every open: read salt → PBKDF2(passphrase, salt) → masterKey → decrypt files
 *
 * Security:
 *  - Salt is in Android Keystore, not accessible to other apps
 *  - Requires user authentication to access (same auth as vault unlock)
 *  - Wiped when localProvider.destroy() is called
 *
 * Note: In USB mode, the salt is stored in the container header on USB.
 * This class is only for Local mode.
 */
class LocalKeyStore(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "cv_local_ks"
        private const val SALT_KEY = "local_pbkdf2_salt"
        private const val SALT_LEN = 32
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get or create the PBKDF2 salt for local mode.
     * If no salt exists, generates one and stores it.
     * Returns the 32-byte salt.
     */
    fun getOrCreateSalt(): ByteArray {
        val existing = prefs.getString(SALT_KEY, null)
        if (existing != null) {
            return Base64.getDecoder().decode(existing)
        }
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(SALT_KEY, Base64.getEncoder().encodeToString(salt)).apply()
        return salt
    }

    /**
     * Check if a local vault has been initialized (salt exists).
     */
    fun isInitialized(): Boolean = prefs.getString(SALT_KEY, null) != null

    /**
     * Destroy the salt — makes local vault permanently unreadable.
     * Called after successful migration to USB, or on vault destroy.
     */
    fun destroy() {
        prefs.edit().remove(SALT_KEY).apply()
    }
}
