package com.calcvault.network.privacy

/** Features whose privacy/performance route choices are independently configurable. */
enum class PrivacyFeature(
    val label: String,
    val supportsOnion: Boolean,
    val isRealtime: Boolean,
    val supportsDirect: Boolean
) {
    MESSAGING("Messaging", true, false, false),
    GROUP_MESSAGING("Group messaging", true, false, false),
    AUDIO_CALLS("Audio calls", false, true, true),
    VIDEO_CALLS("Video calls", false, true, true),
    FILE_SHARING("File sharing", true, false, true),
    SCREEN_SHARING("Screen sharing", false, true, true),
    WATCH_TOGETHER("Watch Together", false, false, true),
    LOCATION_PRESENCE("Location and presence", true, false, false)
}

enum class PrivacyMode {
    /** Never try a less-private route automatically. */
    MAX_PRIVACY,

    /** Permit encrypted relaying only after the user accepts its disclosure. */
    ENCRYPTED_RELAY_WITH_CONSENT,

    /** Permit direct peer routing only after the user accepts peer-IP disclosure. */
    DIRECT_WITH_IP_WARNING
}

object PrivacyPreferenceRules {
    fun canSelect(feature: PrivacyFeature, mode: PrivacyMode): Boolean = when (mode) {
        PrivacyMode.MAX_PRIVACY -> true
        PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT -> true
        PrivacyMode.DIRECT_WITH_IP_WARNING -> feature.supportsDirect
    }

    fun routePreferences(feature: PrivacyFeature, mode: PrivacyMode): PrivacyRoutePreferences = when (mode) {
        PrivacyMode.MAX_PRIVACY -> PrivacyRoutePreferences(
            allowTor = feature.supportsOnion,
            allowEncryptedRelay = false,
            allowTurnRelay = false,
            allowDirectWebRtc = false
        )
        PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT -> PrivacyRoutePreferences(
            allowTor = feature.supportsOnion,
            allowEncryptedRelay = !feature.isRealtime,
            encryptedRelayConsentGiven = !feature.isRealtime,
            allowTurnRelay = feature.isRealtime,
            turnRelayConsentGiven = feature.isRealtime,
            allowDirectWebRtc = false
        )
        PrivacyMode.DIRECT_WITH_IP_WARNING -> PrivacyRoutePreferences(
            allowTor = false,
            allowEncryptedRelay = false,
            allowTurnRelay = false,
            allowDirectWebRtc = true,
            directPeerIpWarningAccepted = true
        )
    }

    fun disclosure(feature: PrivacyFeature, mode: PrivacyMode): String = when (mode) {
        PrivacyMode.MAX_PRIVACY -> "Use only the most private route available for ${feature.label}. " +
            "If it is unavailable, the feature may not connect until you choose an alternative."
        PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT -> if (feature.isRealtime) {
            "Use a TURN relay for ${feature.label}. The relay can observe your IP address, connection " +
                "timing and traffic volume. Standard WebRTC encrypts media in transit, but this app " +
                "does not yet verify end-to-end call identity. The peer should not see your public IP."
        } else {
            "Allow an encrypted relay for ${feature.label}. The service can observe your IP address, " +
                "recipient, timing and traffic volume, and store ciphertext temporarily. It should not " +
                "receive plaintext or private keys. This app's end-to-end encryption is not verified."
        }
        PrivacyMode.DIRECT_WITH_IP_WARNING -> "Allow direct peer-to-peer routing for ${feature.label}. " +
            "The other peer may learn your public IP address and approximate network location. " +
            "Use this only with a peer you trust; no fallback will happen without this saved choice."
    }
}
