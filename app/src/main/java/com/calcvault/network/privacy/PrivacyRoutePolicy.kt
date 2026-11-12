package com.calcvault.network.privacy

/**
 * Feature-aware route policy for CalcVault.
 *
 * This is a policy/planning layer only: it does not open sockets or claim that a
 * transport is implemented. Transport adapters must consume only authenticated
 * end-to-end ciphertext and must honor [RouteAttempt.requiresUserConsent].
 */
enum class CommunicationFeature(
    val supportsOnionRouting: Boolean,
    val isRealtimeMedia: Boolean
) {
    TEXT_MESSAGE(supportsOnionRouting = true, isRealtimeMedia = false),
    GROUP_MESSAGE(supportsOnionRouting = true, isRealtimeMedia = false),
    VOICE_MESSAGE(supportsOnionRouting = true, isRealtimeMedia = false),
    IMAGE_VIDEO_SHARE(supportsOnionRouting = true, isRealtimeMedia = false),
    PRIVATE_DOCUMENT(supportsOnionRouting = true, isRealtimeMedia = false),
    LARGE_FILE(supportsOnionRouting = true, isRealtimeMedia = false),
    LOCATION_SHARE(supportsOnionRouting = true, isRealtimeMedia = false),
    PRESENCE(supportsOnionRouting = true, isRealtimeMedia = false),
    AUDIO_CALL(supportsOnionRouting = false, isRealtimeMedia = true),
    VIDEO_CALL(supportsOnionRouting = false, isRealtimeMedia = true),
    SCREEN_SHARE(supportsOnionRouting = false, isRealtimeMedia = true),
    WATCH_SYNC(supportsOnionRouting = false, isRealtimeMedia = false),
    NOTIFICATION(supportsOnionRouting = false, isRealtimeMedia = false)
}

enum class NetworkRoute {
    TOR_ONION,
    ENCRYPTED_RELAY,
    TURN_RELAY,
    DIRECT_WEBRTC,
    WIFI_DIRECT,
    BLUETOOTH
}

enum class RouteBlockReason {
    END_TO_END_ENCRYPTION_NOT_READY,
    NO_ROUTE_ALLOWED_BY_PRIVACY_POLICY
}

data class RouteAvailability(
    val torOnion: Boolean = false,
    val encryptedRelay: Boolean = false,
    val turnRelay: Boolean = false,
    val directWebRtc: Boolean = false,
    val wifiDirect: Boolean = false,
    val bluetooth: Boolean = false
)

/**
 * These values represent explicit user choices, not inferred consent.
 * Defaults are conservative: no direct peer-IP exposure and no unapproved relay.
 */
data class PrivacyRoutePreferences(
    val allowTor: Boolean = true,
    val allowEncryptedRelay: Boolean = true,
    val encryptedRelayConsentGiven: Boolean = false,
    val allowTurnRelay: Boolean = true,
    val turnRelayConsentGiven: Boolean = false,
    val allowDirectWebRtc: Boolean = false,
    val directPeerIpWarningAccepted: Boolean = false,
    val allowNearbyTransports: Boolean = false
)

data class RouteAttempt(
    val route: NetworkRoute,
    val timeoutMs: Long,
    val requiresUserConsent: Boolean,
    val privacyDisclosure: String
)

data class RoutePlan(
    val feature: CommunicationFeature,
    val attempts: List<RouteAttempt>,
    val blockedReason: RouteBlockReason? = null
) {
    val isUsable: Boolean
        get() = attempts.isNotEmpty()
}

object PrivacyRoutePolicy {
    private const val TOR_TIMEOUT_MS = 45_000L
    private const val RELAY_TIMEOUT_MS = 8_000L
    private const val TURN_TIMEOUT_MS = 12_000L
    private const val DIRECT_TIMEOUT_MS = 12_000L
    private const val WIFI_DIRECT_TIMEOUT_MS = 20_000L
    private const val BLUETOOTH_TIMEOUT_MS = 20_000L

    private const val TOR_DISCLOSURE =
        "Routed through Tor. Tor relays carry encrypted traffic; Tor may be blocked or slower."
    private const val RELAY_DISCLOSURE =
        "Encrypted relay: the relay sees metadata and ciphertext, never keys or plaintext."
    private const val TURN_DISCLOSURE =
        "TURN sees network addresses and traffic metadata, not encrypted media. " +
            "The peer should not learn your public IP."
    private const val DIRECT_DISCLOSURE =
        "Direct peer-to-peer: the other peer may discover your public IP address."
    private const val WIFI_DISCLOSURE =
        "Nearby Wi-Fi Direct connection: the peer can observe local-network connection details."
    private const val BLUETOOTH_DISCLOSURE =
        "Nearby Bluetooth connection: nearby-device presence and Bluetooth metadata may be observable."

    /**
     * Produce a privacy-ordered route plan. A route needing user approval remains
     * visible in the plan but must not be attempted until that approval is obtained.
     *
     * This method fails closed if the caller cannot guarantee that payloads are
     * already end-to-end encrypted. It intentionally does not infer encryption
     * readiness from a method name or from transport-level TLS/DTLS.
     */
    fun plan(
        feature: CommunicationFeature,
        availability: RouteAvailability,
        preferences: PrivacyRoutePreferences = PrivacyRoutePreferences(),
        endToEndEncryptionReady: Boolean
    ): RoutePlan {
        if (!endToEndEncryptionReady) {
            return RoutePlan(
                feature = feature,
                attempts = emptyList(),
                blockedReason = RouteBlockReason.END_TO_END_ENCRYPTION_NOT_READY
            )
        }

        val attempts = mutableListOf<RouteAttempt>()

        if (feature.supportsOnionRouting && preferences.allowTor && availability.torOnion) {
            attempts += RouteAttempt(
                route = NetworkRoute.TOR_ONION,
                timeoutMs = TOR_TIMEOUT_MS,
                requiresUserConsent = false,
                privacyDisclosure = TOR_DISCLOSURE
            )
        }

        if (feature.isRealtimeMedia) {
            // Live media must not be forced through Tor or a generic message relay.
            // TURN is the IP-hiding relay transport for WebRTC media.
            if (preferences.allowTurnRelay && availability.turnRelay) {
                attempts += RouteAttempt(
                    route = NetworkRoute.TURN_RELAY,
                    timeoutMs = TURN_TIMEOUT_MS,
                    requiresUserConsent = !preferences.turnRelayConsentGiven,
                    privacyDisclosure = TURN_DISCLOSURE
                )
            }
        } else if (preferences.allowEncryptedRelay && availability.encryptedRelay) {
            attempts += RouteAttempt(
                route = NetworkRoute.ENCRYPTED_RELAY,
                timeoutMs = RELAY_TIMEOUT_MS,
                requiresUserConsent = !preferences.encryptedRelayConsentGiven,
                privacyDisclosure = RELAY_DISCLOSURE
            )
        }

        if (preferences.allowNearbyTransports && availability.wifiDirect) {
            attempts += RouteAttempt(
                route = NetworkRoute.WIFI_DIRECT,
                timeoutMs = WIFI_DIRECT_TIMEOUT_MS,
                requiresUserConsent = false,
                privacyDisclosure = WIFI_DISCLOSURE
            )
        }

        if (preferences.allowNearbyTransports && availability.bluetooth) {
            attempts += RouteAttempt(
                route = NetworkRoute.BLUETOOTH,
                timeoutMs = BLUETOOTH_TIMEOUT_MS,
                requiresUserConsent = false,
                privacyDisclosure = BLUETOOTH_DISCLOSURE
            )
        }

        // Direct WebRTC is never a privacy fallback unless the user explicitly
        // enabled it. An unaccepted IP warning keeps the attempt consent-gated.
        if (preferences.allowDirectWebRtc && availability.directWebRtc) {
            attempts += RouteAttempt(
                route = NetworkRoute.DIRECT_WEBRTC,
                timeoutMs = DIRECT_TIMEOUT_MS,
                requiresUserConsent = !preferences.directPeerIpWarningAccepted,
                privacyDisclosure = DIRECT_DISCLOSURE
            )
        }

        return RoutePlan(
            feature = feature,
            attempts = attempts.distinctBy { it.route },
            blockedReason = if (attempts.isEmpty()) {
                RouteBlockReason.NO_ROUTE_ALLOWED_BY_PRIVACY_POLICY
            } else {
                null
            }
        )
    }
}
