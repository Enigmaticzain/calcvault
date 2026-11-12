package com.calcvault.network.webrtc

import com.calcvault.network.privacy.PrivacyMode

/** Credentials are kept out of toString/logs because TURN passwords are secrets. */
class IceServerCredentials(
    val stunUrls: List<String> = emptyList(),
    val turnUrls: List<String> = emptyList(),
    val turnUsername: String = "",
    val turnPassword: String = ""
)

data class IceRoutePlan(
    val allowed: Boolean,
    val relayOnly: Boolean,
    val stunUrls: List<String>,
    val turnUrls: List<String>,
    val explanation: String
)

object WebRtcIcePolicy {
    fun plan(mode: PrivacyMode, credentials: IceServerCredentials): IceRoutePlan = when (mode) {
        PrivacyMode.MAX_PRIVACY -> IceRoutePlan(
            allowed = false,
            relayOnly = true,
            stunUrls = emptyList(),
            turnUrls = emptyList(),
            explanation = "No private real-time ICE route is configured. Choose a disclosed TURN or direct route."
        )
        PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT -> {
            val usableTurns = credentials.turnUrls.filter(::isTurnUrl)
            val hasCredentials = credentials.turnUsername.isNotBlank() && credentials.turnPassword.isNotBlank()
            IceRoutePlan(
                allowed = usableTurns.isNotEmpty() && hasCredentials,
                relayOnly = true,
                stunUrls = emptyList(),
                turnUrls = usableTurns,
                explanation = if (usableTurns.isNotEmpty() && hasCredentials) {
                    "TURN-only ICE. The TURN provider sees your IP and connection metadata; peer host candidates are not used."
                } else {
                    "TURN-only route selected, but no valid TURN URI and credentials are configured. No direct fallback will occur."
                }
            )
        }
        PrivacyMode.DIRECT_WITH_IP_WARNING -> {
            val usableTurns = credentials.turnUrls.filter(::isTurnUrl)
            val usableStun = credentials.stunUrls.filter(::isStunUrl)
            IceRoutePlan(
                allowed = usableTurns.isNotEmpty() || usableStun.isNotEmpty(),
                relayOnly = false,
                stunUrls = usableStun,
                turnUrls = usableTurns,
                explanation = if (usableTurns.isNotEmpty() || usableStun.isNotEmpty()) {
                    "Direct ICE is allowed by your saved IP-exposure consent. The peer may learn your public and local IP addresses."
                } else {
                    "Direct route selected, but configure a STUN or TURN server. No route is attempted without ICE infrastructure."
                }
            )
        }
    }

    fun isTurnUrl(url: String): Boolean =
        url.trim().startsWith("turn:", ignoreCase = true) || url.trim().startsWith("turns:", ignoreCase = true)

    fun isStunUrl(url: String): Boolean =
        url.trim().startsWith("stun:", ignoreCase = true) || url.trim().startsWith("stuns:", ignoreCase = true)
}
