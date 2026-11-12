package com.calcvault.network.privacy

/** Features whose privacy/performance route choices are independently configurable. */
enum class PrivacyFeature(
    val label: String,
    val supportsOnion: Boolean,
    val isRealtime: Boolean,
    val supportsDirect: Boolean
) {
    MESSAGING("Messaging", true, false, true),
    GROUP_MESSAGING("Group messaging", true, false, true),
    AUDIO_CALLS("Audio calls", false, true, true),
    VIDEO_CALLS("Video calls", false, true, true),
    FILE_SHARING("File sharing", true, false, true),
    SCREEN_SHARING("Screen sharing", false, true, true),
    WATCH_TOGETHER("Watch Together", false, false, true),
    LISTEN_TOGETHER("Listen Together", false, false, true),
    LOCATION_PRESENCE("Location and presence", true, false, true)
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
        // This is a saved route preference, not proof that Tor/relay adapters exist.
        // Every feature can request its supported relay route and will fail closed if absent.
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
            // Direct consent is the final fallback, never a reason to skip a safer route.
            // A TURN/content relay still needs its own consent if selected first.
            allowTor = feature.supportsOnion,
            allowEncryptedRelay = !feature.isRealtime,
            encryptedRelayConsentGiven = false,
            allowTurnRelay = feature.isRealtime,
            turnRelayConsentGiven = false,
            allowDirectWebRtc = feature.supportsDirect,
            directPeerIpWarningAccepted = feature.supportsDirect
        )
    }

    fun disclosure(feature: PrivacyFeature, mode: PrivacyMode): String = when (mode) {
        PrivacyMode.MAX_PRIVACY -> {
            val route = if (feature.supportsOnion) "Tor/onion only" else "no network route"
            val securityGap = if (feature.isRealtime || feature == PrivacyFeature.WATCH_TOGETHER ||
                feature == PrivacyFeature.LISTEN_TOGETHER
            ) "authenticated ciphertext-only signaling route" else "verified E2EE content route"
            "Keep ${feature.label} at maximum privacy ($route). No less-private route or fallback will be " +
                "attempted. ${feature.label} remains local/unavailable if its private route is not implemented " +
                "or reachable. This build does not have a $securityGap."
        }
        PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT -> if (feature.isRealtime) {
            "Use a TURN relay first for ${feature.label}. TURN can see your IP address, timing and traffic volume; " +
                "it should not see the encrypted media. Tor is not used for live media. Direct P2P is not a fallback " +
                "unless you separately choose the direct option and accept public-IP disclosure. This build lacks " +
                "authenticated ciphertext-only signaling, so the call/screen-share will remain unavailable and no " +
                "signal or media will be sent."
        } else {
            val onionOrder = if (feature.supportsOnion) "Try Tor/onion first, then use the encrypted relay as fallback. " else "Use the encrypted relay. "
            onionOrder + "With this consent, the relay may see your IP, recipient, timing and traffic volume and " +
                "temporarily store ciphertext, but must receive no keys or plaintext. No verified E2EE or matching " +
                "Tor/ciphertext-relay adapter is integrated in this build, so sends remain blocked and no payload " +
                "will be transmitted."
        }
        PrivacyMode.DIRECT_WITH_IP_WARNING -> {
            val saferRoutes = when {
                feature.isRealtime -> "Try configured TURN first, after its separate consent. "
                feature.supportsOnion -> "Try Tor/onion first, then an encrypted relay after its separate consent. "
                else -> "Try an encrypted relay first, after its separate consent. "
            }
            saferRoutes + "Direct peer-to-peer is the last-resort option for ${feature.label}. The other peer " +
                "may learn your public IP address, local IP addresses, and approximate network location. This choice explicitly " +
                "confirms that disclosure; it does not authorize skipping the safer routes. This build has no " +
                "authenticated ciphertext-only transport, so the feature remains unavailable and no content or " +
                "control data will be sent."
        }
    }
}
