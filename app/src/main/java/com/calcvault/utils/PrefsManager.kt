package com.calcvault.utils

import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * PrefsManager
 *
 * Stores user preferences as encrypted records on the USB container.
 * Uses a single JSON blob — segment type SEG_KEY_VAULT, record ID = 999.
 *
 * Preferences stored:
 *  - theme type
 *  - adaptive theme
 *  - animation mode
 *  - inactivity timeout
 *  - biometric enabled
 *  - signaling server URL
 *  - partner BT address
 *  - custom triggers (keyword → animation map)
 *  - custom background image path
 */
class PrefsManager(private val engine: USBStorageEngine) {

    companion object {
        private const val PREFS_RECORD_ID = 999L
    }

    // In-memory cache
    private var cache: JSONObject = JSONObject()

    // ─── Load ──────────────────────────────────────────────────────────────

    /**
     * Load preferences from USB on unlock.
     * Call once after USBStorageEngine.unlock() succeeds.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        // In full impl: read from container by PREFS_RECORD_ID
        // For now, start with defaults
        cache = buildDefaultPrefs()
        applyToSession()
    }

    // ─── Get ───────────────────────────────────────────────────────────────

    fun getString(key: String, default: String = "") = cache.optString(key, default)
    fun getBoolean(key: String, default: Boolean = false) = cache.optBoolean(key, default)
    fun getLong(key: String, default: Long = 0L) = cache.optLong(key, default)
    fun getInt(key: String, default: Int = 0) = cache.optInt(key, default)

    fun getCustomTriggers(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val triggers = cache.optJSONObject("custom_triggers") ?: return map
        triggers.keys().forEach { key -> map[key] = triggers.getString(key) }
        return map
    }

    // ─── Set + Save ─────────────────────────────────────────────────────────

    suspend fun set(key: String, value: String) { cache.put(key, value); save() }
    suspend fun set(key: String, value: Boolean) { cache.put(key, value); save() }
    suspend fun set(key: String, value: Long) { cache.put(key, value); save() }
    suspend fun set(key: String, value: Int) { cache.put(key, value); save() }

    suspend fun addCustomTrigger(keyword: String, animation: String) {
        val triggers = cache.optJSONObject("custom_triggers") ?: JSONObject()
        triggers.put(keyword.lowercase(), animation)
        cache.put("custom_triggers", triggers)
        save()
    }

    suspend fun removeCustomTrigger(keyword: String) {
        cache.optJSONObject("custom_triggers")?.remove(keyword.lowercase())
        save()
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        applyToSession()
        val payload = cache.toString().toByteArray()
        engine.appendRecord(
            segmentType = USBStorageEngine.SEG_KEY_VAULT,
            recordId = PREFS_RECORD_ID,
            payload = payload
        )
    }

    // ─── Apply to session ──────────────────────────────────────────────────

    private fun applyToSession() {
        SessionManager.themeType = getString("theme", "DARK")
        SessionManager.isAdaptiveTheme = getBoolean("adaptive_theme", true)
        SessionManager.animationMode = getString("animation_mode", "FULL")
        SessionManager.inactivityTimeoutMs = getLong("inactivity_ms", 5 * 60 * 1000L)
        SessionManager.isBiometricEnabled = getBoolean("biometric", true)
        SessionManager.signalingUrl = getString("signaling_url", "")
        SessionManager.partnerBtAddress = getString("partner_bt", "")
        SessionManager.chatWallpaperPath = getString("custom_bg_path", "")
    }

    // ─── Defaults ─────────────────────────────────────────────────────────

    private fun buildDefaultPrefs() = JSONObject().apply {
        put("theme", "DARK")
        put("adaptive_theme", true)
        put("animation_mode", "FULL")
        put("inactivity_ms", 5 * 60 * 1000L)
        put("biometric", true)
        put("signaling_url", "")
        put("partner_bt", "")
        put("custom_triggers", JSONObject())
        put("custom_bg_path", "")
    }
}
