package com.calcvault.network.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPreferenceRulesTest {
    @Test
    fun maximumPrivacyNeverAuthorizesRelayOrDirectDowngrade() {
        PrivacyFeature.values().forEach { feature ->
            val preferences = PrivacyPreferenceRules.routePreferences(feature, PrivacyMode.MAX_PRIVACY)
            assertFalse(preferences.allowEncryptedRelay)
            assertFalse(preferences.allowTurnRelay)
            assertFalse(preferences.allowDirectWebRtc)
            assertEquals(feature.supportsOnion, preferences.allowTor)
        }
    }

    @Test
    fun performanceChoicesAreFeatureScopedAndCarryConsentBits() {
        val call = PrivacyPreferenceRules.routePreferences(
            PrivacyFeature.VIDEO_CALLS,
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT
        )
        assertTrue(call.allowTurnRelay)
        assertTrue(call.turnRelayConsentGiven)
        assertFalse(call.allowDirectWebRtc)

        val direct = PrivacyPreferenceRules.routePreferences(
            PrivacyFeature.VIDEO_CALLS,
            PrivacyMode.DIRECT_WITH_IP_WARNING
        )
        assertTrue(direct.allowDirectWebRtc)
        assertTrue(direct.directPeerIpWarningAccepted)
    }

    @Test
    fun directModeIsNotOfferedForFeaturesWithoutDirectRouting() {
        assertFalse(PrivacyPreferenceRules.canSelect(PrivacyFeature.MESSAGING, PrivacyMode.DIRECT_WITH_IP_WARNING))
        assertTrue(PrivacyPreferenceRules.canSelect(PrivacyFeature.FILE_SHARING, PrivacyMode.DIRECT_WITH_IP_WARNING))
    }

    @Test
    fun directDisclosureNamesPublicIpExposure() {
        val warning = PrivacyPreferenceRules.disclosure(
            PrivacyFeature.AUDIO_CALLS,
            PrivacyMode.DIRECT_WITH_IP_WARNING
        )
        assertTrue(warning.contains("public IP address"))
    }
}
