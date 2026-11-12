package com.calcvault.network.webrtc

import com.calcvault.network.privacy.PrivacyFeature
import com.calcvault.network.privacy.PrivacyMode
import com.calcvault.network.privacy.PrivacyTransportGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcIcePolicyTest {
    @Test
    fun iceServerUrisAreValidatedRatherThanAcceptedBySchemePrefix() {
        assertTrue(WebRtcIcePolicy.isStunUrl("stun:stun.example:3478"))
        assertTrue(WebRtcIcePolicy.isTurnUrl("turns:[2001:db8::2]:5349?transport=tcp"))
        assertFalse(WebRtcIcePolicy.isTurnUrl("turn:"))
        assertFalse(WebRtcIcePolicy.isTurnUrl("turn:host:70000"))
        assertFalse(WebRtcIcePolicy.isStunUrl("stun:host/path"))
        assertFalse(WebRtcIcePolicy.isTurnUrl("turns:host:5349?transport=udp"))
    }

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
    fun selectedCallRelayWithoutConfiguredTurnFailsClosedWithExplanation() {
        val route = WebRtcIcePolicy.plan(
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT,
            IceServerCredentials()
        )
        val gate = PrivacyTransportGate.control(
            feature = PrivacyFeature.AUDIO_CALLS,
            mode = PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT,
            selectedRouteAvailable = route.allowed
        )

        assertFalse(route.allowed)
        assertFalse(gate.allowed)
        assertTrue(route.explanation.contains("No direct fallback"))
        assertTrue(gate.explanation.contains("No authenticated, ciphertext-only signaling adapter exists"))
    }

    @Test
    fun directFallbackDoesNotGatherCandidatesBeforeTurnFirstPolicyExists() {
        val direct = WebRtcIcePolicy.plan(
            PrivacyMode.DIRECT_WITH_IP_WARNING,
            IceServerCredentials(
                stunUrls = listOf("stun:stun.example:3478"),
                turnUrls = listOf("turns:turn.example:5349"),
                turnUsername = "temporary-user",
                turnPassword = "temporary-secret"
            )
        )

        assertFalse(direct.allowed)
        assertTrue(direct.relayOnly)
        assertTrue(direct.stunUrls.isEmpty())
        assertTrue(direct.turnUrls.isEmpty())
        assertTrue(direct.explanation.contains("TURN-first"))
        assertTrue(direct.explanation.contains("No ICE candidates were gathered"))
    }
}
