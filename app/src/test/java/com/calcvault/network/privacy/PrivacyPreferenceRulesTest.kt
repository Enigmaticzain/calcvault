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
        assertTrue(direct.allowTurnRelay)
        assertFalse(direct.turnRelayConsentGiven)
        assertTrue(direct.allowDirectWebRtc)
        assertTrue(direct.directPeerIpWarningAccepted)
    }

    @Test
    fun directModeIsAnExplicitPerFeaturePreferenceNotAnAutomaticFallback() {
        PrivacyFeature.values().forEach { feature ->
            assertTrue(PrivacyPreferenceRules.canSelect(feature, PrivacyMode.DIRECT_WITH_IP_WARNING))
            val preferences = PrivacyPreferenceRules.routePreferences(
                feature,
                PrivacyMode.DIRECT_WITH_IP_WARNING
            )
            assertTrue(preferences.allowDirectWebRtc)
            assertTrue(preferences.directPeerIpWarningAccepted)
        }
    }

    @Test
    fun everyFeatureCanSaveAnExplicitEncryptedRelayPreferenceButItDoesNotProveAvailability() {
        PrivacyFeature.values().forEach { feature ->
            assertTrue(PrivacyPreferenceRules.canSelect(feature, PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT))
        }
        listOf(PrivacyFeature.MESSAGING, PrivacyFeature.GROUP_MESSAGING, PrivacyFeature.FILE_SHARING).forEach { feature ->
            val preferences = PrivacyPreferenceRules.routePreferences(
                feature,
                PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT
            )
            assertEquals(feature.supportsOnion, preferences.allowTor)
            assertTrue(preferences.allowEncryptedRelay)
            assertTrue(preferences.encryptedRelayConsentGiven)
            assertFalse(preferences.allowDirectWebRtc)
        }
    }

    @Test
    fun contentFeaturesCanSelectRelayAndDirectPreferencesWithTorRelayDirectOrdering() {
        listOf(PrivacyFeature.MESSAGING, PrivacyFeature.GROUP_MESSAGING, PrivacyFeature.FILE_SHARING).forEach { feature ->
            assertTrue(PrivacyPreferenceRules.canSelect(feature, PrivacyMode.MAX_PRIVACY))
            assertTrue(PrivacyPreferenceRules.canSelect(feature, PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT))
            assertTrue(PrivacyPreferenceRules.canSelect(feature, PrivacyMode.DIRECT_WITH_IP_WARNING))
            assertTrue(
                PrivacyPreferenceRules.disclosure(feature, PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT)
                    .contains("Tor/onion first")
            )
            val directChoice = PrivacyPreferenceRules.routePreferences(feature, PrivacyMode.DIRECT_WITH_IP_WARNING)
            assertTrue(directChoice.allowTor)
            assertTrue(directChoice.allowEncryptedRelay)
            assertFalse(directChoice.encryptedRelayConsentGiven)
            assertTrue(directChoice.allowDirectWebRtc)
            assertTrue(directChoice.directPeerIpWarningAccepted)
        }
    }

    @Test
    fun directModeRequiresExplicitIpDisclosureForWatchAndListenAsWell() {
        listOf(PrivacyFeature.WATCH_TOGETHER, PrivacyFeature.LISTEN_TOGETHER).forEach { feature ->
            assertTrue(PrivacyPreferenceRules.canSelect(feature, PrivacyMode.DIRECT_WITH_IP_WARNING))
            assertTrue(
                PrivacyPreferenceRules.disclosure(feature, PrivacyMode.DIRECT_WITH_IP_WARNING)
                    .contains("public IP address")
            )
        }
    }

    @Test
    fun watchAndListenDisclosuresSayUnavailableRoutesDoNotSendControlData() {
        val watch = PrivacyPreferenceRules.disclosure(
            PrivacyFeature.WATCH_TOGETHER,
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT
        )
        val listen = PrivacyPreferenceRules.disclosure(
            PrivacyFeature.LISTEN_TOGETHER,
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT
        )

        assertTrue(watch.contains("No verified E2EE"))
        assertTrue(watch.contains("sends remain blocked"))
        assertTrue(listen.contains("No verified E2EE"))
        assertTrue(listen.contains("sends remain blocked"))
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
