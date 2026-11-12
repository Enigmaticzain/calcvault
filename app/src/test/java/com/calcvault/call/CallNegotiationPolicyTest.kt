package com.calcvault.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallNegotiationPolicyTest {
    @Test
    fun signalingMustMatchBothCallIdAndExpectedPeer() {
        assertTrue(CallNegotiationPolicy.matchesSignal("call-1", "call-1", "peer", "peer"))
        assertFalse(CallNegotiationPolicy.matchesSignal("call-1", "call-2", "peer", "peer"))
        assertFalse(CallNegotiationPolicy.matchesSignal("call-1", "call-1", "peer", "other"))
        assertFalse(CallNegotiationPolicy.matchesSignal("", "", "peer", "peer"))
    }

    @Test
    fun activeStateRequiresARealNegotiationState() {
        assertTrue(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.CONNECTING))
        assertTrue(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.ON_HOLD))
        assertFalse(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.INCOMING_RINGING))
        assertFalse(CallNegotiationPolicy.mayEnterActive(CallEngine.CallState.FAILED))
    }
}
