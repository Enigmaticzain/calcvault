package com.calcvault.network.privacy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Persists explicit, feature-specific privacy choices in app-private preferences. */
class PrivacyControlStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun mode(feature: PrivacyFeature): PrivacyMode = runCatching {
        PrivacyMode.valueOf(preferences.getString(featureKey(feature), null) ?: "MAX_PRIVACY")
    }.getOrDefault(PrivacyMode.MAX_PRIVACY).takeIf {
        PrivacyPreferenceRules.canSelect(feature, it)
    } ?: PrivacyMode.MAX_PRIVACY

    /** Lower-privacy choices require this invocation to follow a visible disclosure/confirmation. */
    fun setMode(feature: PrivacyFeature, mode: PrivacyMode, disclosureConfirmed: Boolean): Boolean {
        if (!PrivacyPreferenceRules.canSelect(feature, mode)) return false
        if (mode != PrivacyMode.MAX_PRIVACY && !disclosureConfirmed) return false
        return preferences.edit().putString(featureKey(feature), mode.name).commit()
    }

    fun resetToMaximumPrivacy(): Boolean {
        val editor = preferences.edit().clear()
        PrivacyFeature.values().forEach { editor.putString(featureKey(it), PrivacyMode.MAX_PRIVACY.name) }
        return editor.commit()
    }

    /** Consent is feature-scoped; it is not an app-wide blanket permission. */
    fun hasExplicitConsent(feature: PrivacyFeature): Boolean = mode(feature) != PrivacyMode.MAX_PRIVACY

    /** Convert the saved choice to policy input. Callers still must pass real E2EE readiness. */
    fun routePreferences(feature: PrivacyFeature): PrivacyRoutePreferences =
        PrivacyPreferenceRules.routePreferences(feature, mode(feature))

    fun routePlan(
        feature: PrivacyFeature,
        availability: RouteAvailability,
        endToEndEncryptionReady: Boolean
    ): RoutePlan = PrivacyRoutePolicy.plan(
        feature = feature.toCommunicationFeature(),
        availability = availability,
        preferences = routePreferences(feature),
        endToEndEncryptionReady = endToEndEncryptionReady
    )

    private fun PrivacyFeature.toCommunicationFeature(): CommunicationFeature = when (this) {
        PrivacyFeature.MESSAGING -> CommunicationFeature.TEXT_MESSAGE
        PrivacyFeature.GROUP_MESSAGING -> CommunicationFeature.GROUP_MESSAGE
        PrivacyFeature.AUDIO_CALLS -> CommunicationFeature.AUDIO_CALL
        PrivacyFeature.VIDEO_CALLS -> CommunicationFeature.VIDEO_CALL
        PrivacyFeature.FILE_SHARING -> CommunicationFeature.LARGE_FILE
        PrivacyFeature.SCREEN_SHARING -> CommunicationFeature.SCREEN_SHARE
        PrivacyFeature.WATCH_TOGETHER, PrivacyFeature.LISTEN_TOGETHER -> CommunicationFeature.WATCH_SYNC
        PrivacyFeature.LOCATION_PRESENCE -> CommunicationFeature.LOCATION_SHARE
    }

    private fun featureKey(feature: PrivacyFeature) = "route_${feature.name.lowercase(Locale.US)}"

    companion object {
        private const val PREFERENCES_NAME = "privacy_controls"
    }
}

/** Local denylist and report ledger. Reports are stored locally and are not silently uploaded. */
class SafetyContactStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun block(peerId: String): Boolean {
        val normalized = normalize(peerId) ?: return false
        val updated = preferences.getStringSet(BLOCKED_KEY, emptySet()).orEmpty().toMutableSet()
        updated.add(normalized)
        return preferences.edit().putStringSet(BLOCKED_KEY, updated).commit()
    }

    @Synchronized
    fun unblock(peerId: String): Boolean {
        val normalized = normalize(peerId) ?: return false
        val updated = preferences.getStringSet(BLOCKED_KEY, emptySet()).orEmpty().toMutableSet()
        updated.remove(normalized)
        return preferences.edit().putStringSet(BLOCKED_KEY, updated).commit()
    }

    fun isBlocked(peerId: String): Boolean = normalize(peerId)?.let {
        it in preferences.getStringSet(BLOCKED_KEY, emptySet()).orEmpty()
    } ?: false

    fun blockedPeers(): Set<String> = preferences.getStringSet(BLOCKED_KEY, emptySet()).orEmpty().toSet()

    @Synchronized
    fun report(peerId: String, reason: String): Boolean {
        val normalizedPeer = normalize(peerId) ?: return false
        val safeReason = reason.trim().take(MAX_REASON_LENGTH)
        if (safeReason.isBlank()) return false
        val reports = runCatching {
            JSONArray(preferences.getString(REPORTS_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        reports.put(
            JSONObject().apply {
                put("peerId", normalizedPeer)
                put("reason", safeReason)
                put("createdAt", System.currentTimeMillis())
                put("delivery", "LOCAL_ONLY")
            }
        )
        val trimmed = JSONArray()
        for (index in (reports.length() - MAX_REPORTS).coerceAtLeast(0) until reports.length()) {
            trimmed.put(reports.getJSONObject(index))
        }
        return preferences.edit().putString(REPORTS_KEY, trimmed.toString()).commit()
    }

    fun reportCount(): Int = runCatching {
        JSONArray(preferences.getString(REPORTS_KEY, "[]") ?: "[]").length()
    }.getOrDefault(0)

    private fun normalize(peerId: String): String? = peerId.trim().take(MAX_PEER_ID_LENGTH).takeIf { it.isNotBlank() }

    companion object {
        private const val PREFERENCES_NAME = "privacy_safety_contacts"
        private const val BLOCKED_KEY = "blocked_peer_ids"
        private const val REPORTS_KEY = "local_reports"
        private const val MAX_REASON_LENGTH = 500
        private const val MAX_REPORTS = 100
        private const val MAX_PEER_ID_LENGTH = 128
    }
}
