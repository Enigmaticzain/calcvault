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
        PrivacyMode.DIRECT_WITH_IP_WARNING -> IceRoutePlan(
            // RTCConfiguration.ALL gathers host/srflx candidates immediately, before a
            // TURN-first attempt can fail. This manager has no staged policy transition,
            // so allowing this mode would silently expose peer IPs before the fallback.
            // Keep direct routing unavailable until a TURN-first, consent-gated restart
            // is implemented; a saved disclosure choice cannot authorize reordering.
            allowed = false,
            relayOnly = true,
            stunUrls = emptyList(),
            turnUrls = emptyList(),
            explanation = "Direct call fallback is unavailable: this WebRTC adapter cannot guarantee TURN-first negotiation followed by a separate IP-disclosure fallback. No ICE candidates were gathered."
        )
    }

    fun isTurnUrl(url: String): Boolean = isIceUri(url, setOf("turn", "turns"))

    fun isStunUrl(url: String): Boolean = isIceUri(url, setOf("stun", "stuns"))

    private fun isIceUri(rawUrl: String, allowedSchemes: Set<String>): Boolean {
        val match = ICE_URI.matchEntire(rawUrl.trim()) ?: return false
        val scheme = match.groupValues[1].lowercase(java.util.Locale.ROOT)
        if (scheme !in allowedSchemes) return false
        val port = match.groupValues[3].takeIf(String::isNotBlank)?.toIntOrNull()
        if (port != null && port !in 1..65_535) return false
        val transport = match.groupValues[4].lowercase(java.util.Locale.ROOT)
        return !(scheme.endsWith("s") && transport == "udp")
    }

    private val ICE_URI = Regex(
        "^(stun|stuns|turn|turns):((?:\[[0-9a-fA-F:.]+\])|(?:[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?))(?::([0-9]{1,5}))?(?:\?transport=(udp|tcp))?$",
        RegexOption.IGNORE_CASE
    )
}
