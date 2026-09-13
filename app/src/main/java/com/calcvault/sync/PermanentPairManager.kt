package com.calcvault.sync

import android.content.Context
import android.content.SharedPreferences
import com.calcvault.utils.SessionManager

/**
 * PermanentPairManager
 *
 * Manages the permanent two-person bond between Zain and Sanu.
 * Once the second user selects their identity, the pair is locked forever.
 * No manual pairing codes needed — the app auto-connects on every launch.
 */
object PermanentPairManager {

    private const val PREFS_NAME = "permanent_pair"
    private const val KEY_IS_PAIRED = "is_paired"
    private const val KEY_MY_ROLE = "my_role"   // "zain" or "sanu"
    private const val KEY_PAIR_TIMESTAMP = "pair_timestamp"
    private const val KEY_PARTNER_DEVICE_ID = "partner_device_id"
    private const val KEY_LOCAL_VERSION_CODE = "local_version_code"
    private const val KEY_PARTNER_VERSION_CODE = "partner_version_code"
    private const val KEY_APK_DOWNLOAD_URL = "apk_download_url"
    private const val KEY_UPDATE_AVAILABLE = "update_available"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ─── Pairing State ──────────────────────────────────────────────

    val isPaired: Boolean get() = prefs.getBoolean(KEY_IS_PAIRED, false)

    val myRole: String get() = prefs.getString(KEY_MY_ROLE, "") ?: ""

    val isZain: Boolean get() = myRole == "zain"
    val isSanu: Boolean get() = myRole == "sanu"

    val pairTimestamp: Long get() = prefs.getLong(KEY_PAIR_TIMESTAMP, 0L)

    // ─── Select Identity & Auto-Pair ────────────────────────────────

    /**
     * Called once when the user selects their identity (Zain or Sanu).
     * This permanently locks the pairing — no undo, no re-pairing.
     */
    fun selectIdentity(role: String) {
        val normalizedRole = role.lowercase().trim()
        require(normalizedRole == "zain" || normalizedRole == "sanu") {
            "Role must be 'zain' or 'sanu'"
        }

        prefs.edit()
            .putBoolean(KEY_IS_PAIRED, true)
            .putString(KEY_MY_ROLE, normalizedRole)
            .putLong(KEY_PAIR_TIMESTAMP, System.currentTimeMillis())
            .apply()

        // Wire into SessionManager
        if (normalizedRole == "zain") {
            SessionManager.localUserId = "zain"
            SessionManager.partnerUserId = "sanu"
            SessionManager.localNickname = "Zain"
            SessionManager.partnerNickname = "Sanu"
        } else {
            SessionManager.localUserId = "sanu"
            SessionManager.partnerUserId = "zain"
            SessionManager.localNickname = "Sanu"
            SessionManager.partnerNickname = "Zain"
        }
    }

    /**
     * Called on every app launch after pairing to restore identity into SessionManager.
     */
    fun restoreSession() {
        if (!isPaired) return
        val role = myRole
        if (role == "zain") {
            SessionManager.localUserId = "zain"
            SessionManager.partnerUserId = "sanu"
            SessionManager.localNickname = "Zain"
            SessionManager.partnerNickname = "Sanu"
        } else if (role == "sanu") {
            SessionManager.localUserId = "sanu"
            SessionManager.partnerUserId = "zain"
            SessionManager.localNickname = "Sanu"
            SessionManager.partnerNickname = "Zain"
        }
    }

    // ─── Version / Update Management ────────────────────────────────

    fun setLocalVersionCode(code: Int) {
        prefs.edit().putInt(KEY_LOCAL_VERSION_CODE, code).apply()
    }

    fun getLocalVersionCode(): Int = prefs.getInt(KEY_LOCAL_VERSION_CODE, 1)

    fun setPartnerVersionCode(code: Int) {
        prefs.edit().putInt(KEY_PARTNER_VERSION_CODE, code).apply()
    }

    fun getPartnerVersionCode(): Int = prefs.getInt(KEY_PARTNER_VERSION_CODE, 1)

    fun setUpdateAvailable(available: Boolean, apkUrl: String = "") {
        prefs.edit()
            .putBoolean(KEY_UPDATE_AVAILABLE, available)
            .putString(KEY_APK_DOWNLOAD_URL, apkUrl)
            .apply()
    }

    fun isUpdateAvailable(): Boolean = prefs.getBoolean(KEY_UPDATE_AVAILABLE, false)

    fun getApkDownloadUrl(): String = prefs.getString(KEY_APK_DOWNLOAD_URL, "") ?: ""

    fun clearUpdateFlag() {
        prefs.edit()
            .putBoolean(KEY_UPDATE_AVAILABLE, false)
            .putString(KEY_APK_DOWNLOAD_URL, "")
            .apply()
    }

    fun setPartnerDeviceId(id: String) {
        prefs.edit().putString(KEY_PARTNER_DEVICE_ID, id).apply()
    }

    fun getPartnerDeviceId(): String = prefs.getString(KEY_PARTNER_DEVICE_ID, "") ?: ""
}
