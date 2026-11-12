package com.calcvault.network.webrtc

import com.calcvault.network.privacy.PrivacyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcIcePolicyTest {
    @Test
    fun maximumPrivacyDoesNotSilentlyUseStunOrDirectCandidates() {
        val plan = WebRtcIcePolicy.plan(
            PrivacyMode.MAX_PRIVACY,
            IceServerCredentials(stunUrls = listOf("stun:stun.example:3478"))
        )

        assertFalse(plan.allowed)
        assertTrue(plan.relayOnly)
        assertTrue(plan.stunUrls.isEmpty())
        assertTrue(plan.turnUrls.isEmpty())
    }

    @Test
    fun turnConsentUsesRelayOnlyAndRequiresCredentials() {
        val missingCredentials = WebRtcIcePolicy.plan(
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT,
            IceServerCredentials(turnUrls = listOf("turn:turn.example:3478"))
        )
        assertFalse(missingCredentials.allowed)
        assertTrue(missingCredentials.relayOnly)

        val ready = WebRtcIcePolicy.plan(
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT,
            IceServerCredentials(
                stunUrls = listOf("stun:stun.example:3478"),
                turnUrls = listOf("turns:turn.example:5349"),
                turnUsername = "temporary-user",
                turnPassword = "temporary-secret"
            )
        )
        assertTrue(ready.allowed)
        assertTrue(ready.relayOnly)
        assertTrue(ready.stunUrls.isEmpty())
        assertEquals(listOf("turns:turn.example:5349"), ready.turnUrls)
    }

    @Test
    fun directRouteRequiresConfiguredIceServerAndExplicitlySelectedMode() {
        assertFalse(
            WebRtcIcePolicy.plan(PrivacyMode.DIRECT_WITH_IP_WARNING, IceServerCredentials()).allowed
        )
        val direct = WebRtcIcePolicy.plan(
            PrivacyMode.DIRECT_WITH_IP_WARNING,
            IceServerCredentials(stunUrls = listOf("stun:stun.example:3478"))
        )
        assertTrue(direct.allowed)
        assertFalse(direct.relayOnly)
        assertTrue(direct.explanation.contains("public and local IP"))
    }
}
