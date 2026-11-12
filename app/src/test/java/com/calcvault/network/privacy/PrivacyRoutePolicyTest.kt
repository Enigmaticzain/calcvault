package com.calcvault.network.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRoutePolicyTest {

    @Test
    fun blocksAllNetworkRoutesUntilE2eEncryptionIsReady() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(
                torOnion = true,
                encryptedRelay = true,
                directWebRtc = true
            ),
            preferences = PrivacyRoutePreferences(
                allowDirectWebRtc = true,
                directPeerIpWarningAccepted = true,
                encryptedRelayConsentGiven = true
            ),
            endToEndEncryptionReady = false
        )

        assertTrue(plan.attempts.isEmpty())
        assertEquals(RouteBlockReason.END_TO_END_ENCRYPTION_NOT_READY, plan.blockedReason)
    }

    @Test
    fun onionIsPreferredForSupportedAsynchronousFeatures() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(torOnion = true, encryptedRelay = true),
            preferences = PrivacyRoutePreferences(encryptedRelayConsentGiven = true),
            endToEndEncryptionReady = true
        )

        assertEquals(
            listOf(NetworkRoute.TOR_ONION, NetworkRoute.ENCRYPTED_RELAY),
            plan.attempts.map { it.route }
        )
        assertFalse(plan.attempts.first().requiresUserConsent)
        assertFalse(plan.attempts.last().requiresUserConsent)
    }

    @Test
    fun lowerPrivacyRelayFallbackRequiresConsentByDefault() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(torOnion = true, encryptedRelay = true),
            endToEndEncryptionReady = true
        )

        val relay = plan.attempts.single { it.route == NetworkRoute.ENCRYPTED_RELAY }
        assertTrue(relay.requiresUserConsent)
        assertTrue(relay.privacyDisclosure.contains("metadata"))
    }

    @Test
    fun torIsNeverSelectedForRealtimeMedia() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.VIDEO_CALL,
            availability = RouteAvailability(torOnion = true, turnRelay = true, directWebRtc = true),
            preferences = PrivacyRoutePreferences(
                allowDirectWebRtc = true,
                directPeerIpWarningAccepted = true,
                turnRelayConsentGiven = true
            ),
            endToEndEncryptionReady = true
        )

        assertEquals(
            listOf(NetworkRoute.TURN_RELAY, NetworkRoute.DIRECT_WEBRTC),
            plan.attempts.map { it.route }
        )
        assertFalse(plan.attempts.any { it.route == NetworkRoute.TOR_ONION })
    }

    @Test
    fun directP2pIsNotIncludedUnlessUserEnablesIt() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(directWebRtc = true),
            endToEndEncryptionReady = true
        )

        assertTrue(plan.attempts.isEmpty())
        assertEquals(RouteBlockReason.NO_ROUTE_ALLOWED_BY_PRIVACY_POLICY, plan.blockedReason)
    }

    @Test
    fun directP2pRequiresAnExplicitPublicIpWarningAcknowledgement() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(directWebRtc = true),
            preferences = PrivacyRoutePreferences(allowDirectWebRtc = true),
            endToEndEncryptionReady = true
        )

        val direct = plan.attempts.single()
        assertEquals(NetworkRoute.DIRECT_WEBRTC, direct.route)
        assertTrue(direct.requiresUserConsent)
        assertTrue(direct.privacyDisclosure.contains("public IP"))
    }

    @Test
    fun directFallbackAfterOnionRequiresConsentAndCannotBeSilent() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(torOnion = true, directWebRtc = true),
            preferences = PrivacyRoutePreferences(allowDirectWebRtc = true),
            endToEndEncryptionReady = true
        )

        assertEquals(
            listOf(NetworkRoute.TOR_ONION, NetworkRoute.DIRECT_WEBRTC),
            plan.attempts.map { it.route }
        )
        assertTrue(plan.attempts.last().requiresUserConsent)
    }

    @Test
    fun acceptedDirectWarningIsRetainedInRouteDisclosure() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(directWebRtc = true),
            preferences = PrivacyRoutePreferences(
                allowDirectWebRtc = true,
                directPeerIpWarningAccepted = true
            ),
            endToEndEncryptionReady = true
        )

        val direct = plan.attempts.single()
        assertFalse(direct.requiresUserConsent)
        assertTrue(direct.privacyDisclosure.contains("public IP"))
    }

    @Test
    fun callPrivacyUsesTurnBeforeExplicitlyAllowedDirectWebrtc() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.AUDIO_CALL,
            availability = RouteAvailability(turnRelay = true, directWebRtc = true),
            preferences = PrivacyRoutePreferences(
                allowDirectWebRtc = true,
                directPeerIpWarningAccepted = true,
                turnRelayConsentGiven = true
            ),
            endToEndEncryptionReady = true
        )

        assertEquals(
            listOf(NetworkRoute.TURN_RELAY, NetworkRoute.DIRECT_WEBRTC),
            plan.attempts.map { it.route }
        )
    }

    @Test
    fun savedMessagingRelayPreferencePlansOnionBeforeConsentGrantedCiphertextRelay() {
        val feature = PrivacyFeature.MESSAGING
        val preferences = PrivacyPreferenceRules.routePreferences(
            feature,
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT
        )
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(torOnion = true, encryptedRelay = true),
            preferences = preferences,
            endToEndEncryptionReady = true
        )

        assertEquals(
            listOf(NetworkRoute.TOR_ONION, NetworkRoute.ENCRYPTED_RELAY),
            plan.attempts.map { it.route }
        )
        assertFalse(plan.attempts[0].requiresUserConsent)
        assertFalse(plan.attempts[1].requiresUserConsent)
    }

    @Test
    fun savedDirectMessagingConsentDoesNotSkipTorOrSeparatelyConsentedRelay() {
        val preferences = PrivacyPreferenceRules.routePreferences(
            PrivacyFeature.MESSAGING,
            PrivacyMode.DIRECT_WITH_IP_WARNING
        )
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.TEXT_MESSAGE,
            availability = RouteAvailability(
                torOnion = true,
                encryptedRelay = true,
                directWebRtc = true
            ),
            preferences = preferences,
            endToEndEncryptionReady = true
        )

        assertEquals(
            listOf(NetworkRoute.TOR_ONION, NetworkRoute.ENCRYPTED_RELAY, NetworkRoute.DIRECT_WEBRTC),
            plan.attempts.map { it.route }
        )
        assertFalse(plan.attempts.first().requiresUserConsent)
        assertTrue(plan.attempts[1].requiresUserConsent)
        assertFalse(plan.attempts.last().requiresUserConsent)
        assertTrue(plan.attempts.last().privacyDisclosure.contains("public IP"))
    }

    @Test
    fun savedDirectCallPreferenceKeepsTurnFirstAndRequiresItsSeparateConsent() {
        val preferences = PrivacyPreferenceRules.routePreferences(
            PrivacyFeature.VIDEO_CALLS,
            PrivacyMode.DIRECT_WITH_IP_WARNING
        )
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.VIDEO_CALL,
            availability = RouteAvailability(turnRelay = true, directWebRtc = true),
            preferences = preferences,
            endToEndEncryptionReady = true
        )

        assertEquals(
            listOf(NetworkRoute.TURN_RELAY, NetworkRoute.DIRECT_WEBRTC),
            plan.attempts.map { it.route }
        )
        assertTrue(plan.attempts.first().requiresUserConsent)
        assertFalse(plan.attempts.last().requiresUserConsent)
        assertTrue(plan.attempts.last().privacyDisclosure.contains("public IP"))
    }

    @Test
    fun watchSyncDoesNotUseTorOrGenericRealtimeMediaPolicy() {
        val plan = PrivacyRoutePolicy.plan(
            feature = CommunicationFeature.WATCH_SYNC,
            availability = RouteAvailability(torOnion = true, encryptedRelay = true),
            endToEndEncryptionReady = true
        )

        assertEquals(listOf(NetworkRoute.ENCRYPTED_RELAY), plan.attempts.map { it.route })
    }
}
