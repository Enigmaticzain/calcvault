package com.calcvault.network.privacy

/** Result of checking whether a concrete transport may be used for this operation. */
data class TransportGateResult(
    val allowed: Boolean,
    val explanation: String
)

/**
 * Fail-closed checks shared by transport adapters. A saved preference is consent only;
 * it neither proves a route exists nor establishes end-to-end encryption.
 */
object PrivacyTransportGate {
    fun content(
        feature: PrivacyFeature,
        endToEndEncryptionReady: Boolean,
        selectedRouteAvailable: Boolean,
        mode: PrivacyMode = PrivacyMode.MAX_PRIVACY
    ): TransportGateResult {
        val routeLabel = selectedRouteLabel(feature, mode)
        return when {
            !selectedRouteAvailable && !endToEndEncryptionReady -> TransportGateResult(
                allowed = false,
                explanation = "${feature.label} was not sent: the selected $routeLabel route is unavailable, " +
                    "and verified end-to-end encryption is not available. No less-private fallback was attempted."
            )
            !selectedRouteAvailable -> TransportGateResult(
                allowed = false,
                explanation = "${feature.label} was not sent: the selected $routeLabel route is unavailable. " +
                    "No less-private fallback was attempted."
            )
            !endToEndEncryptionReady -> TransportGateResult(
                allowed = false,
                explanation = "${feature.label} was not sent: verified end-to-end encryption is not available. " +
                    "A saved route choice is not encryption or proof of an alternate route."
            )
            else -> TransportGateResult(true, "The selected content route is available.")
        }
    }

    fun control(
        feature: PrivacyFeature,
        mode: PrivacyMode,
        selectedRouteAvailable: Boolean,
        authenticatedCiphertextOnlyRouteReady: Boolean = false,
        privateOnionRouteReady: Boolean = false,
        oneTimeWatchConsent: Boolean = false,
        oneTimeListenConsent: Boolean = false
    ): TransportGateResult = when {
        !selectedRouteAvailable -> TransportGateResult(
            allowed = false,
            explanation = "${feature.label} signaling was not sent: the selected ${selectedRouteLabel(feature, mode)} route is unavailable. " +
                "No authenticated, ciphertext-only signaling adapter exists; HTTPS signaling and TURN settings alone " +
                "do not provide one. No less-private fallback was attempted."
        )
        !authenticatedCiphertextOnlyRouteReady -> TransportGateResult(
            allowed = false,
            explanation = "${feature.label} signaling was not sent: the route is not verified as authenticated, " +
                "ciphertext-only peer signaling. A saved route choice or one-time consent cannot replace that guarantee."
        )
        mode == PrivacyMode.MAX_PRIVACY && !(feature.supportsOnion && privateOnionRouteReady) -> TransportGateResult(
            allowed = false,
            explanation = "${feature.label} signaling was not sent: maximum privacy is selected, but this route is not verified as Tor/onion-only. A session invitation cannot override the saved route choice."
        )
        feature == PrivacyFeature.WATCH_TOGETHER && oneTimeWatchConsent -> TransportGateResult(
            allowed = true,
            explanation = "One-time Watch Together signaling consent was supplied for this room."
        )
        feature == PrivacyFeature.LISTEN_TOGETHER && oneTimeListenConsent -> TransportGateResult(
            allowed = true,
            explanation = "One-time Listen Together signaling consent was supplied for this session."
        )
        mode == PrivacyMode.MAX_PRIVACY -> TransportGateResult(
            allowed = false,
            explanation = "${feature.label} signaling was not sent: maximum privacy is selected and " +
                "no private signaling route is available. Choose an alternative only after reviewing its disclosure."
        )
        else -> TransportGateResult(true, "The selected signaling route is available.")
    }

    private fun selectedRouteLabel(feature: PrivacyFeature, mode: PrivacyMode): String = when (mode) {
        PrivacyMode.MAX_PRIVACY -> if (feature.supportsOnion) "Tor/onion-only" else "maximum-privacy"
        PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT -> when {
            feature.isRealtime -> "consented TURN-relay"
            feature.supportsOnion -> "Tor-first with consented encrypted-relay fallback"
            else -> "consented encrypted-relay"
        }
        PrivacyMode.DIRECT_WITH_IP_WARNING -> when {
            feature.isRealtime -> "TURN-first, then direct with public-IP disclosure"
            feature.supportsOnion -> "Tor-first, then separately consented relay, then direct with public-IP disclosure"
            else -> "separately consented relay, then direct with public-IP disclosure"
        }
    }
}
