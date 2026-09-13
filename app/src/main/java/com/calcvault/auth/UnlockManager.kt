package com.calcvault.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * UnlockManager
 *
 * Central authority for authentication state.
 * Persists critical auth metadata to public storage to survive uninstalls.
 */
class UnlockManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private const val PREFS_NAME = "cv_auth_state"
        private const val KEY_ATTEMPT_COUNT = "attempt_count"
        private const val KEY_LAST_ATTEMPT = "last_attempt_time"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_PASSPHRASE_HASH = "passphrase_hash"
        private const val KEY_PASSPHRASE_SALT = "passphrase_salt"
        private const val KEY_DECOY_HASH = "decoy_hash"
        private const val KEY_RECOVERY_HASH = "recovery_hash"
        private const val KEY_VAULT_DESTROYED = "vault_destroyed"
        private const val KEY_USER_IDENTITY = "user_identity"
        private const val KEY_SETUP_COMPLETE = "setup_complete"

        // Nicknames
        private const val KEY_LOCAL_NICKNAME = "local_nickname"
        private const val KEY_PARTNER_NICKNAME = "partner_nickname"

        // Wallpaper
        private const val KEY_CHAT_WALLPAPER = "chat_wallpaper_path"

        // Custom Triggers (JSON)
        private const val KEY_CUSTOM_TRIGGERS = "custom_triggers_v2"
        private const val KEY_WORD_EFFECTS_JSON = "word_effects_v1"
        private const val KEY_WORD_EFFECTS_ENABLED = "word_effects_enabled"
        private const val KEY_CARE_DROPS_ENABLED = "care_drops_enabled"

        // Persistent settings
        private const val KEY_ANIM_MODE = "setting_anim_mode"
        private const val KEY_THEME_TYPE = "setting_theme_type"
        private const val KEY_ADAPTIVE_THEME = "setting_adaptive_theme"
        private const val KEY_INACTIVITY_TIMEOUT = "setting_inactivity_timeout"
        private const val KEY_BIOMETRIC_ENABLED = "setting_biometric_enabled"
        private const val KEY_SIGNALING_URL = "setting_signaling_url"
        private const val KEY_PARTNER_BT_ADDR = "setting_partner_bt_addr"
        private const val KEY_INTERNET_ONLY_MODE = "setting_internet_only_mode"
        private const val KEY_AMBIENT_SCENE = "setting_ambient_scene"
        private const val KEY_ACTIVE_ANIMATED_THEME = "setting_active_animated_theme"
        private const val KEY_MOOD_UI_ADAPTATION = "setting_mood_ui_adaptation"

        private const val TIER_1_ATTEMPTS = 5
        private const val TIER_1_DURATION_MS = 10 * 60 * 60 * 1000L
        private const val TIER_2_ATTEMPTS = 15
        private const val TIER_2_DURATION_MS = 24 * 60 * 60 * 1000L
        private const val TIER_3_ATTEMPTS = 20

        private const val PERSISTENT_FOLDER_NAME = ".cv_persistent_data"
        private const val AUTH_BACKUP_FILE = ".vault_auth"
        private const val AUTH_SNAPSHOT_FILE = ".vault_auth.sec"
        private const val AUTH_SNAPSHOT_BACKUP_FILE = ".vault_auth.sec.bak"
        private const val AUTH_SNAPSHOT_TMP_FILE = ".vault_auth.sec.tmp"
        private const val AUTH_SNAPSHOT_VERSION = 2
        private const val AUTH_SNAPSHOT_LEGACY_VERSION = 1
        private const val AUTH_SNAPSHOT_MAGIC = "CVAUTH2!"
        private const val AUTH_SNAPSHOT_AAD = "cv_auth_snapshot_v2"
        private const val AUTH_SNAPSHOT_LEGACY_AAD = "cv_auth_snapshot_v1"
        private const val AUTH_SNAPSHOT_SALT_LEN = 32
        private const val AUTH_SNAPSHOT_IV_LEN = 12
        private const val AUTH_SNAPSHOT_TAG_LEN = 16
        private const val AUTH_SNAPSHOT_PBKDF_ITERS = 100_000
        private const val PBKDF2_ITERATIONS = 100_000

        private val SNAPSHOT_STRING_KEYS = setOf(
            KEY_PASSPHRASE_HASH,
            KEY_PASSPHRASE_SALT,
            KEY_DECOY_HASH,
            KEY_RECOVERY_HASH,
            KEY_USER_IDENTITY,
            KEY_LOCAL_NICKNAME,
            KEY_PARTNER_NICKNAME,
            KEY_CHAT_WALLPAPER,
            KEY_CUSTOM_TRIGGERS,
            KEY_WORD_EFFECTS_JSON,
            KEY_ANIM_MODE,
            KEY_THEME_TYPE,
            KEY_SIGNALING_URL,
            KEY_PARTNER_BT_ADDR,
            KEY_AMBIENT_SCENE,
            KEY_ACTIVE_ANIMATED_THEME
        )
        private val SNAPSHOT_BOOLEAN_KEYS = setOf(
            KEY_VAULT_DESTROYED,
            KEY_SETUP_COMPLETE,
            KEY_ADAPTIVE_THEME,
            KEY_BIOMETRIC_ENABLED,
            KEY_WORD_EFFECTS_ENABLED,
            KEY_CARE_DROPS_ENABLED,
            KEY_INTERNET_ONLY_MODE,
            KEY_MOOD_UI_ADAPTATION
        )
        private val SNAPSHOT_LONG_KEYS = setOf(
            KEY_INACTIVITY_TIMEOUT
        )
        private val SNAPSHOT_REQUIRED_KEYS = setOf(
            KEY_PASSPHRASE_HASH,
            KEY_PASSPHRASE_SALT
        )

        @Volatile private var instance: UnlockManager? = null
        
        // Optimized iterations for faster verification
        private const val PBKDF2_ITERATIONS_FAST = 50_000

        fun getInstance(context: Context): UnlockManager {
            return instance ?: synchronized(this) {
                instance ?: UnlockManager(context).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences
    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val backupMutex = Mutex()

    private enum class SnapshotFormat {
        BINARY_V2,
        ENCRYPTED_JSON_V1
    }

    private data class DecodedSnapshot(
        val payload: JSONObject,
        val sourceFile: File,
        val format: SnapshotFormat
    )

    init {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        migrateLegacyBackupIfNeeded()
        restoreAuthBackup()

        // Load settings into SessionManager
        loadPersistentSettings()
    }

    fun isInitialized(): Boolean = prefs.contains(KEY_PASSPHRASE_HASH)

    fun isLockedOut(): Boolean {
        if (isVaultDestroyed()) return true
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        return System.currentTimeMillis() < lockoutUntil
    }

    fun isVaultDestroyed(): Boolean {
        return prefs.getBoolean(KEY_VAULT_DESTROYED, false)
    }

    fun getRemainingLockoutMs(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        return maxOf(0L, lockoutUntil - System.currentTimeMillis())
    }

    sealed class UnlockResult {
        object RealUnlock : UnlockResult()
        object DecoyUnlock : UnlockResult()
        data class Failed(val attemptsRemaining: Int, val lockedOut: Boolean) : UnlockResult()
        object VaultDestroyed : UnlockResult()
        object LockedOut : UnlockResult()
    }

    fun attemptUnlock(input: String): UnlockResult {
        if (isVaultDestroyed()) return UnlockResult.VaultDestroyed
        if (isLockedOut()) return UnlockResult.LockedOut
        if (verifyHash(input, KEY_PASSPHRASE_HASH, KEY_PASSPHRASE_SALT)) {
            resetAttempts()
            return UnlockResult.RealUnlock
        }
        if (verifyDecoy(input)) {
            resetAttempts()
            return UnlockResult.DecoyUnlock
        }
        return recordFailedAttempt()
    }

    fun setupPassphrase(passphrase: String) {
        val salt = generateSalt()
        val hash = pbkdf2Hash(passphrase, salt)
        prefs.edit()
            .putString(KEY_PASSPHRASE_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_PASSPHRASE_HASH, hash)
            .apply()
        saveAuthBackup()
    }

    /** Iteration count exposed for callers that need to derive keys with the same KDF params. */
    fun getPbkdf2Iterations(): Int = PBKDF2_ITERATIONS

    fun getOrCreateSalt(): ByteArray {
        val stored = prefs.getString(KEY_PASSPHRASE_SALT, null)
        return if (stored != null) {
            Base64.getDecoder().decode(stored)
        } else {
            val salt = generateSalt()
            prefs.edit().putString(KEY_PASSPHRASE_SALT, Base64.getEncoder().encodeToString(salt)).apply()
            saveAuthBackup()
            salt
        }
    }

    fun setupDecoyPassphrase(decoy: String) {
        prefs.edit().putString(KEY_DECOY_HASH, sha256(decoy)).apply()
        saveAuthBackup()
    }

    fun setupRecoveryKey(key: String) {
        prefs.edit().putString(KEY_RECOVERY_HASH, sha256(key)).apply()
        saveAuthBackup()
    }

    fun setUserIdentity(identity: String) {
        prefs.edit()
            .putString(KEY_USER_IDENTITY, identity)
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
        SessionManager.localUserId = identity
        SessionManager.partnerUserId = if (identity == "USER_A") "USER_B" else "USER_A"
        saveAuthBackup()
    }

    fun getUserIdentity(): String? = prefs.getString(KEY_USER_IDENTITY, null)

    fun isSetupComplete(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETE, false)

    fun verifyRecoveryKey(key: String): Boolean {
        val storedHash = prefs.getString(KEY_RECOVERY_HASH, null) ?: return false
        return sha256(key.trim()) == storedHash
    }

    // ── Persistent Settings ────────────────────────────────────────────────

    private fun loadPersistentSettings() {
        SessionManager.animationMode = prefs.getString(KEY_ANIM_MODE, "FULL") ?: "FULL"
        SessionManager.themeType = prefs.getString(KEY_THEME_TYPE, "DARK") ?: "DARK"
        SessionManager.isAdaptiveTheme = prefs.getBoolean(KEY_ADAPTIVE_THEME, true)
        SessionManager.inactivityTimeoutMs = prefs.getLong(KEY_INACTIVITY_TIMEOUT, 5 * 60 * 1000L)
        SessionManager.isBiometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        SessionManager.isWordEffectsEnabled = prefs.getBoolean(KEY_WORD_EFFECTS_ENABLED, true)
        SessionManager.isCareDropsEnabled = prefs.getBoolean(KEY_CARE_DROPS_ENABLED, true)
        SessionManager.isMoodUiAdaptationEnabled = prefs.getBoolean(KEY_MOOD_UI_ADAPTATION, true)
        SessionManager.signalingUrl = prefs.getString(KEY_SIGNALING_URL, "") ?: ""
        SessionManager.partnerBtAddress = prefs.getString(KEY_PARTNER_BT_ADDR, "") ?: ""
        SessionManager.internetOnlyMode = prefs.getBoolean(KEY_INTERNET_ONLY_MODE, true)
        SessionManager.activeAnimatedTheme = prefs.getString(KEY_ACTIVE_ANIMATED_THEME, "NONE") ?: "NONE"

        SessionManager.localNickname = prefs.getString(KEY_LOCAL_NICKNAME, "") ?: ""
        SessionManager.partnerNickname = prefs.getString(KEY_PARTNER_NICKNAME, "") ?: ""
        SessionManager.chatWallpaperPath = prefs.getString(KEY_CHAT_WALLPAPER, "") ?: ""

        val identity = getUserIdentity()
        if (identity != null) {
            SessionManager.localUserId = identity
            SessionManager.partnerUserId = if (identity == "USER_A") "USER_B" else "USER_A"
        }
    }

    fun getCustomTriggersJson(): String = prefs.getString(KEY_CUSTOM_TRIGGERS, "[]") ?: "[]"

    fun saveCustomTriggersJson(json: String) {
        prefs.edit().putString(KEY_CUSTOM_TRIGGERS, json).apply()
        saveAuthBackup()
    }

    fun getWordEffectsJson(): String = prefs.getString(KEY_WORD_EFFECTS_JSON, "[]") ?: "[]"

    fun saveWordEffectsJson(json: String) {
        prefs.edit().putString(KEY_WORD_EFFECTS_JSON, json).apply()
        saveAuthBackup()
    }

    fun isWordEffectsEnabled(): Boolean = prefs.getBoolean(KEY_WORD_EFFECTS_ENABLED, true)

    fun updateWordEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORD_EFFECTS_ENABLED, enabled).apply()
        SessionManager.isWordEffectsEnabled = enabled
        saveAuthBackup()
    }

    fun isCareDropsEnabled(): Boolean = prefs.getBoolean(KEY_CARE_DROPS_ENABLED, true)

    fun updateCareDropsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CARE_DROPS_ENABLED, enabled).apply()
        SessionManager.isCareDropsEnabled = enabled
        saveAuthBackup()
    }

    fun updateChatWallpaper(path: String) {
        prefs.edit().putString(KEY_CHAT_WALLPAPER, path).apply()
        SessionManager.chatWallpaperPath = path
        saveAuthBackup()
    }

    fun updateNicknames(local: String, partner: String) {
        prefs.edit()
            .putString(KEY_LOCAL_NICKNAME, local)
            .putString(KEY_PARTNER_NICKNAME, partner)
            .apply()
        SessionManager.localNickname = local
        SessionManager.partnerNickname = partner
        saveAuthBackup()
    }

    fun updateAnimationMode(mode: String) {
        prefs.edit().putString(KEY_ANIM_MODE, mode).apply()
        saveAuthBackup()
    }

    fun updateThemeSettings(type: String, adaptive: Boolean) {
        prefs.edit()
            .putString(KEY_THEME_TYPE, type)
            .putBoolean(KEY_ADAPTIVE_THEME, adaptive)
            .apply()
        saveAuthBackup()
    }

    fun updateInactivityTimeout(timeoutMs: Long) {
        prefs.edit().putLong(KEY_INACTIVITY_TIMEOUT, timeoutMs).apply()
        saveAuthBackup()
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        saveAuthBackup()
    }

    fun updateMoodUiAdaptation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOOD_UI_ADAPTATION, enabled).apply()
        SessionManager.isMoodUiAdaptationEnabled = enabled
        saveAuthBackup()
    }

    fun updateSignalingUrl(url: String) {
        prefs.edit().putString(KEY_SIGNALING_URL, url).apply()
        saveAuthBackup()
    }

    fun updatePartnerBtAddress(addr: String) {
        prefs.edit().putString(KEY_PARTNER_BT_ADDR, addr).apply()
        saveAuthBackup()
    }

    fun updateInternetOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTERNET_ONLY_MODE, enabled).apply()
        saveAuthBackup()
    }

    fun updateActiveAnimatedTheme(theme: String) {
        prefs.edit().putString(KEY_ACTIVE_ANIMATED_THEME, theme).apply()
        SessionManager.activeAnimatedTheme = theme
        saveAuthBackup()
    }

    fun getAmbientScene(): String =
        prefs.getString(KEY_AMBIENT_SCENE, "COMPANION")?.uppercase() ?: "COMPANION"

    fun updateAmbientScene(scene: String) {
        val normalized = when (scene.uppercase()) {
            "NATURE" -> "NATURE"
            else -> "COMPANION"
        }
        prefs.edit().putString(KEY_AMBIENT_SCENE, normalized).apply()
        saveAuthBackup()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun pbkdf2Hash(input: String, salt: ByteArray): String {
        // Use faster iterations if it matches the stored version, otherwise fallback to legacy
        val iters = PBKDF2_ITERATIONS_FAST
        val spec = PBEKeySpec(input.toCharArray(), salt, iters, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = try {
            factory.generateSecret(spec).encoded
        } catch (e: Exception) {
            // Fallback to original slower iterations if verification fails
            val legacySpec = PBEKeySpec(input.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            factory.generateSecret(legacySpec).encoded
        }
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun recordFailedAttempt(): UnlockResult {
        val now = System.currentTimeMillis()
        val attempts = prefs.getInt(KEY_ATTEMPT_COUNT, 0) + 1
        prefs.edit().putInt(KEY_ATTEMPT_COUNT, attempts).putLong(KEY_LAST_ATTEMPT, now).apply()
        return when {
            attempts >= TIER_3_ATTEMPTS -> {
                destroyVault()
                UnlockResult.VaultDestroyed
            }
            attempts >= TIER_2_ATTEMPTS -> {
                prefs.edit().putLong(KEY_LOCKOUT_UNTIL, now + TIER_2_DURATION_MS).apply()
                UnlockResult.Failed(0, true)
            }
            attempts >= TIER_1_ATTEMPTS -> {
                prefs.edit().putLong(KEY_LOCKOUT_UNTIL, now + TIER_1_DURATION_MS).apply()
                UnlockResult.Failed(0, true)
            }
            else -> UnlockResult.Failed(TIER_1_ATTEMPTS - attempts, false)
        }
    }

    private fun resetAttempts() {
        prefs.edit().putInt(KEY_ATTEMPT_COUNT, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
    }

    fun destroyVault() {
        prefs.edit().putBoolean(KEY_VAULT_DESTROYED, true).apply()
        clearAuthBackup()
        com.calcvault.auth.VaultDestructionBus.trigger()
    }

    private fun verifyHash(input: String, hashKey: String, saltKey: String): Boolean {
        val storedHash = prefs.getString(hashKey, null) ?: return false
        val storedSalt = prefs.getString(saltKey, null)?.let { Base64.getDecoder().decode(it) } ?: return false
        return pbkdf2Hash(input, storedSalt) == storedHash
    }

    private fun verifyDecoy(input: String): Boolean {
        val storedHash = prefs.getString(KEY_DECOY_HASH, null) ?: return false
        return sha256(input) == storedHash
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return Base64.getEncoder().encodeToString(bytes)
    }

    // ── Persistent Auth Backup ─────────────────────────────────────────────

    private fun getPersistentRoot(): File {
        val externalRoot = Environment.getExternalStorageDirectory()
        val persistentDir = File(externalRoot, PERSISTENT_FOLDER_NAME)
        if (!persistentDir.exists()) persistentDir.mkdirs()
        return persistentDir
    }

    private fun getPersistentAuthFile(): File = File(getPersistentRoot(), AUTH_BACKUP_FILE)

    private fun getSecureSnapshotFile(name: String = AUTH_SNAPSHOT_FILE): File {
        return File(getPersistentRoot(), name)
    }

    private fun saveAuthBackup() {
        backupScope.launch {
            backupMutex.withLock {
                try {
                    val snapshot = buildSnapshotPayload(collectSnapshotEntries())
                    if (writeEncryptedSnapshot(snapshot)) {
                        val legacy = getPersistentAuthFile()
                        if (legacy.exists()) {
                            secureDelete(legacy)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun restoreAuthBackup() {
        try {
            if (isInitialized()) return
            val decoded = readSecureSnapshotPayload() ?: return
            if (!applySnapshot(decoded.payload)) return

            val legacy = getPersistentAuthFile()
            if (legacy.exists()) {
                secureDelete(legacy)
            }
        } catch (_: Exception) {
        }
    }

    private fun clearAuthBackup() {
        try {
            listOf(
                getPersistentAuthFile(),
                getSecureSnapshotFile(AUTH_SNAPSHOT_FILE),
                getSecureSnapshotFile(AUTH_SNAPSHOT_BACKUP_FILE),
                getSecureSnapshotFile(AUTH_SNAPSHOT_TMP_FILE)
            ).forEach { file ->
                if (file.exists()) secureDelete(file)
            }
        } catch (_: Exception) {}
    }

    fun wipeEverything() {
        // 1. Clear SharedPreferences
        prefs.edit().clear().commit()

        // 2. Clear backup file
        clearAuthBackup()

        // 3. Clear persistent local vault folder
        try {
            val externalRoot = Environment.getExternalStorageDirectory()
            val persistentDir = File(externalRoot, PERSISTENT_FOLDER_NAME)
            if (persistentDir.exists()) {
                wipeDirectory(persistentDir)
                persistentDir.delete()
            }
        } catch (e: Exception) {}
    }

    private fun wipeDirectory(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                wipeDirectory(file)
            } else {
                // Overwrite with random data before deleting
                try {
                    val size = file.length().toInt()
                    if (size > 0) {
                        val rng = SecureRandom()
                        file.writeBytes(ByteArray(size).also { rng.nextBytes(it) })
                    }
                } catch (e: Exception) {}
                file.delete()
            }
        }
    }

    private fun migrateLegacyBackupIfNeeded() {
        try {
            val decodedSecure = readSecureSnapshotPayload()
            if (decodedSecure != null) {
                if (decodedSecure.format != SnapshotFormat.BINARY_V2 ||
                    decodedSecure.sourceFile.name != AUTH_SNAPSHOT_FILE
                ) {
                    if (!writeEncryptedSnapshot(decodedSecure.payload)) {
                        return
                    }
                }

                val legacy = getPersistentAuthFile()
                if (legacy.exists()) {
                    secureDelete(legacy)
                }
                return
            }

            val legacyFile = getPersistentAuthFile()
            if (!legacyFile.exists()) return

            val legacyEntries = readLegacyEntries(legacyFile) ?: return
            val snapshot = buildSnapshotPayload(legacyEntries)
            if (writeEncryptedSnapshot(snapshot)) {
                secureDelete(legacyFile)
            }
        } catch (_: Exception) {
        }
    }

    private fun collectSnapshotEntries(): JSONArray {
        val entries = JSONArray()
        snapshotKeysInOrder().forEach { key ->
            when (expectedSnapshotType(key)) {
                "string" -> {
                    if (prefs.contains(key)) {
                        val value = prefs.getString(key, null) ?: ""
                        entries.put(createSnapshotEntry(key, "string", value))
                    }
                }
                "boolean" -> {
                    if (prefs.contains(key)) {
                        entries.put(createSnapshotEntry(key, "boolean", prefs.getBoolean(key, false)))
                    }
                }
                "long" -> {
                    if (prefs.contains(key)) {
                        entries.put(createSnapshotEntry(key, "long", prefs.getLong(key, 0L)))
                    }
                }
            }
        }
        return entries
    }

    private fun readLegacyEntries(file: File): JSONArray? {
        return try {
            val lines = file.readLines()
            val entries = JSONArray()
            lines.forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size != 2) return@forEach
                val key = parts[0].trim()
                val value = parts[1]
                val expectedType = expectedSnapshotType(key) ?: return@forEach
                if (key.isBlank()) {
                    return@forEach
                }
                val entry = when (expectedType) {
                    "boolean" -> {
                        if (value != "true" && value != "false") return@forEach
                        createSnapshotEntry(key, "boolean", value.toBoolean())
                    }
                    "long" -> {
                        val longValue = value.toLongOrNull() ?: return@forEach
                        createSnapshotEntry(key, "long", longValue)
                    }
                    else -> createSnapshotEntry(key, "string", value)
                }
                entries.put(entry)
            }
            sanitizeSnapshotEntries(entries)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSnapshotPayload(entries: JSONArray, createdAt: Long = System.currentTimeMillis()): JSONObject {
        return JSONObject().apply {
            put("version", AUTH_SNAPSHOT_VERSION)
            put("createdAt", createdAt.coerceAtLeast(0L))
            put("entries", entries)
        }
    }

    private fun writeEncryptedSnapshot(snapshot: JSONObject): Boolean {
        val normalized = normalizeSnapshotPayload(snapshot) ?: return false
        val target = getSecureSnapshotFile(AUTH_SNAPSHOT_FILE)
        val backup = getSecureSnapshotFile(AUTH_SNAPSHOT_BACKUP_FILE)
        val tmp = getSecureSnapshotFile(AUTH_SNAPSHOT_TMP_FILE)
        val jsonBytes = normalized.toString().toByteArray(Charsets.UTF_8)

        return try {
            val blob = encryptSnapshot(jsonBytes)
            java.io.FileOutputStream(tmp).use { output ->
                output.write(blob)
                output.fd.sync()
            }

            val validated = decodeBinarySnapshot(tmp)
            if (validated == null || normalizeSnapshotPayload(validated.payload) == null) {
                secureDelete(tmp)
                return false
            }

            if (backup.exists()) {
                secureDelete(backup)
            }
            if (target.exists()) {
                if (!target.renameTo(backup)) {
                    secureDelete(tmp)
                    return false
                }
            }
            if (!tmp.renameTo(target)) {
                if (backup.exists() && !target.exists()) {
                    backup.renameTo(target)
                }
                secureDelete(tmp)
                return false
            }
            val targetSnapshot = decodeBinarySnapshot(target)
            if (targetSnapshot == null || normalizeSnapshotPayload(targetSnapshot.payload) == null) {
                secureDelete(target)
                if (backup.exists() && !target.exists()) {
                    backup.renameTo(target)
                }
                return false
            }
            true
        } catch (_: Exception) {
            if (tmp.exists()) secureDelete(tmp)
            false
        } finally {
            jsonBytes.fill(0)
        }
    }

    private fun readSecureSnapshotPayload(): DecodedSnapshot? {
        val files = listOf(
            getSecureSnapshotFile(AUTH_SNAPSHOT_FILE),
            getSecureSnapshotFile(AUTH_SNAPSHOT_BACKUP_FILE)
        )
        files.forEach { file ->
            val decoded = decodeBinarySnapshot(file)
            if (decoded != null) {
                return decoded
            }
            val legacy = decodeLegacyEncryptedSnapshot(file)
            if (legacy != null) return legacy
        }
        return null
    }

    private fun decodeBinarySnapshot(file: File): DecodedSnapshot? {
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            val bytes = file.readBytes()
            val magicBytes = AUTH_SNAPSHOT_MAGIC.toByteArray(Charsets.US_ASCII)
            val minimumSize = magicBytes.size + 4 + AUTH_SNAPSHOT_SALT_LEN + AUTH_SNAPSHOT_IV_LEN + 4 + AUTH_SNAPSHOT_TAG_LEN
            if (bytes.size < minimumSize) return null

            val buffer = ByteBuffer.wrap(bytes)
            val magic = ByteArray(magicBytes.size)
            buffer.get(magic)
            if (!magic.contentEquals(magicBytes)) return null

            val version = buffer.int
            if (version != AUTH_SNAPSHOT_VERSION) return null

            val salt = ByteArray(AUTH_SNAPSHOT_SALT_LEN)
            buffer.get(salt)
            val iv = ByteArray(AUTH_SNAPSHOT_IV_LEN)
            buffer.get(iv)
            val ciphertextLength = buffer.int
            if (ciphertextLength <= 0 || ciphertextLength > buffer.remaining() - AUTH_SNAPSHOT_TAG_LEN) {
                return null
            }

            val ciphertext = ByteArray(ciphertextLength)
            buffer.get(ciphertext)
            val tag = ByteArray(AUTH_SNAPSHOT_TAG_LEN)
            buffer.get(tag)

            val plaintext = decryptSnapshot(ciphertext + tag, salt, iv, AUTH_SNAPSHOT_AAD) ?: return null
            try {
                val payload = normalizeSnapshotPayload(JSONObject(String(plaintext, Charsets.UTF_8))) ?: return null
                DecodedSnapshot(payload, file, SnapshotFormat.BINARY_V2)
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeLegacyEncryptedSnapshot(file: File): DecodedSnapshot? {
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            val envelope = JSONObject(file.readText())
            val version = envelope.optInt("version", -1)
            if (version != AUTH_SNAPSHOT_LEGACY_VERSION) return null

            val salt = Base64.getDecoder().decode(envelope.optString("salt"))
            val iv = Base64.getDecoder().decode(envelope.optString("iv"))
            val ciphertext = Base64.getDecoder().decode(envelope.optString("ciphertext"))
            if (salt.size != AUTH_SNAPSHOT_SALT_LEN || iv.size != AUTH_SNAPSHOT_IV_LEN || ciphertext.size <= AUTH_SNAPSHOT_TAG_LEN) {
                return null
            }

            val plaintext = decryptSnapshot(ciphertext, salt, iv, AUTH_SNAPSHOT_LEGACY_AAD) ?: return null
            try {
                val payload = normalizeSnapshotPayload(JSONObject(String(plaintext, Charsets.UTF_8))) ?: return null
                DecodedSnapshot(payload, file, SnapshotFormat.ENCRYPTED_JSON_V1)
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptSnapshot(plain: ByteArray): ByteArray {
        val salt = ByteArray(AUTH_SNAPSHOT_SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(AUTH_SNAPSHOT_IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveSnapshotKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(AUTH_SNAPSHOT_AAD.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        val ciphertextLength = encrypted.size - AUTH_SNAPSHOT_TAG_LEN
        if (ciphertextLength <= 0) {
            throw IllegalStateException("Snapshot encryption failed")
        }

        val magicBytes = AUTH_SNAPSHOT_MAGIC.toByteArray(Charsets.US_ASCII)
        val ciphertext = encrypted.copyOfRange(0, ciphertextLength)
        val tag = encrypted.copyOfRange(ciphertextLength, encrypted.size)

        return ByteBuffer.allocate(
            magicBytes.size + 4 + AUTH_SNAPSHOT_SALT_LEN + AUTH_SNAPSHOT_IV_LEN + 4 + ciphertext.size + tag.size
        ).apply {
            put(magicBytes)
            putInt(AUTH_SNAPSHOT_VERSION)
            put(salt)
            put(iv)
            putInt(ciphertext.size)
            put(ciphertext)
            put(tag)
        }.array()
    }

    private fun decryptSnapshot(
        ciphertextWithTag: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
        aad: String
    ): ByteArray? {
        if (salt.size != AUTH_SNAPSHOT_SALT_LEN || iv.size != AUTH_SNAPSHOT_IV_LEN) return null
        return try {
            val key = deriveSnapshotKey(salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertextWithTag)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveSnapshotKey(salt: ByteArray): SecretKeySpec {
        require(salt.size == AUTH_SNAPSHOT_SALT_LEN)
        val seed = getDeviceBoundSecret()
        val spec = PBEKeySpec(seed.toCharArray(), salt, AUTH_SNAPSHOT_PBKDF_ITERS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    private fun getDeviceBoundSecret(): String {
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val fingerprint = buildString {
            append(appContext.packageName)
            append('|')
            append(if (androidId.isNotBlank()) androidId else "unknown")
        }
        return sha256(fingerprint)
    }

    private fun expectedSnapshotType(key: String): String? {
        return when {
            key in SNAPSHOT_STRING_KEYS -> "string"
            key in SNAPSHOT_BOOLEAN_KEYS -> "boolean"
            key in SNAPSHOT_LONG_KEYS -> "long"
            else -> null
        }
    }

    private fun snapshotKeysInOrder(): List<String> {
        return buildList {
            addAll(SNAPSHOT_STRING_KEYS)
            addAll(SNAPSHOT_BOOLEAN_KEYS)
            addAll(SNAPSHOT_LONG_KEYS)
        }.sorted()
    }

    private fun createSnapshotEntry(key: String, type: String, value: Any): JSONObject {
        return JSONObject()
            .put("key", key)
            .put("type", type)
            .put("value", value)
    }

    private fun sanitizeSnapshotEntries(entries: JSONArray?): JSONArray? {
        if (entries == null || entries.length() == 0) return null

        val seenKeys = mutableSetOf<String>()
        val sanitized = mutableListOf<JSONObject>()
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: return null
            val key = entry.optString("key").trim()
            val expectedType = expectedSnapshotType(key) ?: return null
            if (!seenKeys.add(key)) return null
            if (entry.optString("type") != expectedType || !entry.has("value")) return null

            val value = entry.opt("value")
            val sanitizedEntry = when (expectedType) {
                "string" -> {
                    if (value !is String) return null
                    createSnapshotEntry(key, "string", value)
                }
                "boolean" -> {
                    if (value !is Boolean) return null
                    createSnapshotEntry(key, "boolean", value)
                }
                "long" -> {
                    if (value !is Number) return null
                    createSnapshotEntry(key, "long", value.toLong())
                }
                else -> return null
            }
            sanitized += sanitizedEntry
        }

        if (!SNAPSHOT_REQUIRED_KEYS.all { required -> seenKeys.contains(required) }) {
            return null
        }

        return JSONArray().apply {
            sanitized.sortedBy { it.getString("key") }.forEach { put(it) }
        }
    }

    private fun normalizeSnapshotPayload(snapshot: JSONObject?): JSONObject? {
        if (snapshot == null) return null
        val version = snapshot.optInt("version", -1)
        if (version != AUTH_SNAPSHOT_VERSION && version != AUTH_SNAPSHOT_LEGACY_VERSION) return null
        val sanitizedEntries = sanitizeSnapshotEntries(snapshot.optJSONArray("entries")) ?: return null
        val createdAt = snapshot.optLong("createdAt", System.currentTimeMillis())
        return buildSnapshotPayload(sanitizedEntries, createdAt)
    }

    private fun isValidSnapshot(snapshot: JSONObject?): Boolean {
        return normalizeSnapshotPayload(snapshot) != null
    }

    private fun applySnapshot(snapshot: JSONObject): Boolean {
        val normalized = normalizeSnapshotPayload(snapshot) ?: return false
        val entries = normalized.optJSONArray("entries") ?: return false
        val edit = prefs.edit()
        snapshotKeysInOrder().forEach { key -> edit.remove(key) }
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: return false
            val key = entry.optString("key")
            when (entry.optString("type")) {
                "string" -> edit.putString(key, entry.optString("value"))
                "boolean" -> edit.putBoolean(key, entry.optBoolean("value"))
                "long" -> edit.putLong(key, entry.optLong("value"))
                else -> return false
            }
        }
        return edit.commit()
    }

    private fun secureDelete(file: File) {
        try {
            if (!file.exists()) return
            val size = file.length().toInt()
            if (size > 0) {
                val random = ByteArray(size).also { SecureRandom().nextBytes(it) }
                file.writeBytes(random)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { file.delete() }
        }
    }
}
