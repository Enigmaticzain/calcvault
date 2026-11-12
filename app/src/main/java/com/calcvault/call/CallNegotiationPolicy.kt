package com.calcvault.call

/** Pure correlation/state rules for negotiation; signaling transport authentication is separate. */
object CallNegotiationPolicy {
    fun matchesSignal(expectedCallId: String, incomingCallId: String, expectedPeerId: String, senderId: String): Boolean =
        expectedCallId.isNotBlank() && incomingCallId == expectedCallId &&
            expectedPeerId.isNotBlank() && senderId == expectedPeerId

    fun mayEnterActive(currentState: CallEngine.CallState): Boolean = currentState in setOf(
        CallEngine.CallState.CONNECTING,
        CallEngine.CallState.OUTGOING_RINGING,
        CallEngine.CallState.ON_HOLD
    )
}
