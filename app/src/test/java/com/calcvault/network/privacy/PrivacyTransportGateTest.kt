package com.calcvault.network.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyTransportGateTest {
    @Test
    fun storedConsentDoesNotEnableContentWithoutVerifiedEndToEndEncryption() {
        val result = PrivacyTransportGate.content(
            feature = PrivacyFeature.MESSAGING,
            endToEndEncryptionReady = false,
            selectedRouteAvailable = true
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("not encryption"))
    }

    @Test
    fun contentFailsClosedWhenTheSelectedRouteIsNotAvailable() {
        val result = PrivacyTransportGate.content(
            feature = PrivacyFeature.FILE_SHARING,
            endToEndEncryptionReady = true,
            selectedRouteAvailable = false,
            mode = PrivacyMode.MAX_PRIVACY
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("selected Tor/onion-only route is unavailable"))
        assertTrue(result.explanation.contains("No less-private fallback"))
    }

    @Test
    fun contentExplainsBothUnavailableSelectedRouteAndMissingEncryption() {
        val result = PrivacyTransportGate.content(
            feature = PrivacyFeature.FILE_SHARING,
            endToEndEncryptionReady = false,
            selectedRouteAvailable = false,
            mode = PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("selected Tor-first with consented encrypted-relay fallback route is unavailable"))
        assertTrue(result.explanation.contains("verified end-to-end encryption is not available"))
        assertTrue(result.explanation.contains("No less-private fallback"))
    }

    @Test
    fun messagingAndFileSharingBothFailClosedWithoutAnImplementedRoute() {
        listOf(PrivacyFeature.MESSAGING, PrivacyFeature.FILE_SHARING).forEach { feature ->
            val result = PrivacyTransportGate.content(
                feature = feature,
                endToEndEncryptionReady = false,
                selectedRouteAvailable = false,
                mode = PrivacyMode.MAX_PRIVACY
            )
            assertFalse(result.allowed)
            assertTrue(result.explanation.contains(feature.label))
            assertTrue(result.explanation.contains("selected Tor/onion-only route is unavailable"))
            assertTrue(result.explanation.contains("verified end-to-end encryption is not available"))
        }
    }

    @Test
    fun unavailableDirectChoiceReportsSaferRoutesFirstAndIpDisclosure() {
        val result = PrivacyTransportGate.content(
            feature = PrivacyFeature.FILE_SHARING,
            endToEndEncryptionReady = false,
            selectedRouteAvailable = false,
            mode = PrivacyMode.DIRECT_WITH_IP_WARNING
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("Tor-first, then separately consented relay, then direct"))
        assertTrue(result.explanation.contains("public-IP disclosure route is unavailable"))
        assertTrue(result.explanation.contains("No less-private fallback was attempted"))
    }

    @Test
    fun maximumPrivacyBlocksControlSignalingWithoutAPrivateRoute() {
        val result = PrivacyTransportGate.control(
            feature = PrivacyFeature.AUDIO_CALLS,
            mode = PrivacyMode.MAX_PRIVACY,
            selectedRouteAvailable = true,
            authenticatedCiphertextOnlyRouteReady = true
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("maximum privacy"))
    }

    @Test
    fun directCallSignalFailureNamesSelectedRouteAndPublicIpDisclosure() {
        val result = PrivacyTransportGate.control(
            feature = PrivacyFeature.AUDIO_CALLS,
            mode = PrivacyMode.DIRECT_WITH_IP_WARNING,
            selectedRouteAvailable = false
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("TURN-first, then direct with public-IP disclosure route is unavailable"))
        assertTrue(result.explanation.contains("No authenticated, ciphertext-only signaling adapter exists"))
        assertTrue(result.explanation.contains("No less-private fallback was attempted"))
    }

    @Test
    fun oneTimeConsentCannotTurnAnUnauthenticatedRouteIntoAnApprovedSignalPath() {
        val result = PrivacyTransportGate.control(
            feature = PrivacyFeature.WATCH_TOGETHER,
            mode = PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT,
            selectedRouteAvailable = true,
            oneTimeWatchConsent = true
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("not verified as authenticated, ciphertext-only peer signaling"))
        assertTrue(result.explanation.contains("one-time consent cannot replace"))
    }

    @Test
    fun oneTimeListenConsentIsScopedAndStillRequiresAnAvailableRoute() {
        val allowed = PrivacyTransportGate.control(
            feature = PrivacyFeature.LISTEN_TOGETHER,
            mode = PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT,
            selectedRouteAvailable = true,
            authenticatedCiphertextOnlyRouteReady = true,
            oneTimeListenConsent = true
        )
        val blocked = PrivacyTransportGate.control(
            feature = PrivacyFeature.LISTEN_TOGETHER,
            mode = PrivacyMode.MAX_PRIVACY,
            selectedRouteAvailable = false,
            oneTimeListenConsent = true
        )

        assertTrue(allowed.allowed)
        assertFalse(blocked.allowed)
        assertTrue(blocked.explanation.contains("No authenticated, ciphertext-only signaling adapter exists"))
        assertTrue(blocked.explanation.contains("No less-private fallback"))
    }

    @Test
    fun oneTimeSessionConsentCannotOverrideMaximumPrivacyRouteChoice() {
        val result = PrivacyTransportGate.control(
            feature = PrivacyFeature.WATCH_TOGETHER,
            mode = PrivacyMode.MAX_PRIVACY,
            selectedRouteAvailable = true,
            authenticatedCiphertextOnlyRouteReady = true,
            oneTimeWatchConsent = true
        )

        assertFalse(result.allowed)
        assertTrue(result.explanation.contains("maximum privacy is selected"))
        assertTrue(result.explanation.contains("cannot override the saved route choice"))
    }

    @Test
    fun oneTimeWatchConsentIsFeatureScopedAndStillRequiresAnAvailableRoute() {
        val allowed = PrivacyTransportGate.control(
            feature = PrivacyFeature.WATCH_TOGETHER,
            mode = PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT,
            selectedRouteAvailable = true,
            authenticatedCiphertextOnlyRouteReady = true,
            oneTimeWatchConsent = true
        )
        val blockedUnavailable = PrivacyTransportGate.control(
            feature = PrivacyFeature.WATCH_TOGETHER,
            mode = PrivacyMode.MAX_PRIVACY,
            selectedRouteAvailable = false,
            oneTimeWatchConsent = true
        )
        val blockedWrongFeature = PrivacyTransportGate.control(
            feature = PrivacyFeature.AUDIO_CALLS,
            mode = PrivacyMode.MAX_PRIVACY,
            selectedRouteAvailable = true,
            authenticatedCiphertextOnlyRouteReady = true,
            oneTimeWatchConsent = true
        )

        assertTrue(allowed.allowed)
        assertFalse(blockedUnavailable.allowed)
        assertFalse(blockedWrongFeature.allowed)
    }
}
