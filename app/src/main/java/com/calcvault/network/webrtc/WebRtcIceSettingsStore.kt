package com.calcvault.network.webrtc

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted-at-rest user-provided STUN/TURN settings; there is no hard-coded public STUN fallback. */
class WebRtcIceSettingsStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFERENCES_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): IceServerCredentials = IceServerCredentials(
        stunUrls = parseUrls(preferences.getString(KEY_STUN_URLS, "").orEmpty()),
        turnUrls = parseUrls(preferences.getString(KEY_TURN_URLS, "").orEmpty()),
        turnUsername = preferences.getString(KEY_TURN_USERNAME, "").orEmpty(),
        turnPassword = preferences.getString(KEY_TURN_PASSWORD, "").orEmpty()
    )

    fun save(
        stunUrls: String,
        turnUrls: String,
        turnUsername: String,
        turnPassword: String
    ): Boolean {
        val parsedStun = parseUrls(stunUrls)
        val parsedTurn = parseUrls(turnUrls)
        if (parsedStun.any { !WebRtcIcePolicy.isStunUrl(it) } ||
            parsedTurn.any { !WebRtcIcePolicy.isTurnUrl(it) }
        ) return false
        if (parsedTurn.isNotEmpty() && (turnUsername.isBlank() || turnPassword.isBlank())) return false
        return preferences.edit()
            .putString(KEY_STUN_URLS, parsedStun.joinToString("\n"))
            .putString(KEY_TURN_URLS, parsedTurn.joinToString("\n"))
            .putString(KEY_TURN_USERNAME, if (parsedTurn.isEmpty()) "" else turnUsername.trim())
            .putString(KEY_TURN_PASSWORD, if (parsedTurn.isEmpty()) "" else turnPassword)
            .commit()
    }

    fun clear(): Boolean = preferences.edit().clear().commit()

    private fun parseUrls(value: String): List<String> = value
        .split(',', '\n', '\r', ';')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    companion object {
        /** Credential-store failures are treated as no configured route by transport callers. */
        fun loadOrEmpty(context: Context): IceServerCredentials =
            runCatching { WebRtcIceSettingsStore(context).load() }.getOrDefault(IceServerCredentials())

        private const val PREFERENCES_NAME = "webrtc_ice_credentials"
        private const val KEY_STUN_URLS = "stun_urls"
        private const val KEY_TURN_URLS = "turn_urls"
        private const val KEY_TURN_USERNAME = "turn_username"
        private const val KEY_TURN_PASSWORD = "turn_password"
    }
}
