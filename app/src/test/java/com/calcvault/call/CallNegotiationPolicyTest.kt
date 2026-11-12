package com.calcvault.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallNegotiationPolicyTest {
    @Test
    fun onlyOneAnswerMayStartAfterAnIncomingOfferArrives() {
        assertFalse(CallNegotiationPolicy.canAnswer(CallEngine.CallState.INCOMING_RINGING, false, false))
        assertFalse(CallNegotiationPolicy.canAnswer(CallEngine.CallState.INCOMING_RINGING, true, true))
        assertFalse(CallNegotiationPolicy.canAnswer(CallEngine.CallState.CONNECTING, true, false))
        assertTrue(CallNegotiationPolicy.canAnswer(CallEngine.CallState.INCOMING_RINGING, true, false))
    }

    @Test
    fun signalingMustMatchBothCallIdAndExpectedPeer() {
        assertTrue(CallNegotiationPolicy.matchesSignal("call-1", "call-1", "peer", "peer"))
        assertFalse(CallNegotiationPolicy.matchesSignal("call-1", "call-2", "peer", "peer"))
        assertFalse(CallNegotiationPolicy.matchesSignal("call-1", "call-1", "peer", "other"))
        assertFalse(
            CallNegotiationPolicy.matchesSignal(
                "call-1", "call-1", "peer", "peer", expectedLocalUserId = "local", recipientId = "other"
            )
        )
        assertTrue(
            CallNegotiationPolicy.matchesSignal(
                "call-1", "call-1", "peer", "peer", expectedLocalUserId = "local", recipientId = "local"
            )
        )
        assertFalse(CallNegotiationPolicy.matchesSignal("", "", "peer", "peer"))
    }

    @Test
    fun activeStateRequiresARealNegotiationState() {
        assertTrue(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.CONNECTING))
        assertTrue(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.ON_HOLD))
        assertFalse(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.INCOMING_RINGING))
        assertFalse(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.FAILED))
    }

    @Test
    fun cameraCanBePausedAndResumedWithoutChangingCallConnectionState() {
        val paused = CallNegotiationPolicy.nextVideoEnabled(currentState = true)
        assertFalse(paused)
        assertTrue(CallNegotiationPolicy.nextVideoEnabled(currentState = paused))
    }
}
