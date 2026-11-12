package com.calcvault.call

/** Pure correlation/state rules for negotiation; signaling transport authentication is separate. */
object CallNegotiationPolicy {
    fun canAnswer(
        state: CallEngine.CallState,
        hasOffer: Boolean,
        answerInProgress: Boolean
    ): Boolean = state == CallEngine.CallState.INCOMING_RINGING && hasOffer && !answerInProgress

    fun matchesSignal(
        expectedCallId: String,
        incomingCallId: String,
        expectedPeerId: String,
        senderId: String,
        expectedLocalUserId: String? = null,
        recipientId: String? = null
    ): Boolean = expectedCallId.isNotBlank() && incomingCallId == expectedCallId &&
        expectedPeerId.isNotBlank() && senderId == expectedPeerId &&
        (expectedLocalUserId == null || recipientId == expectedLocalUserId)

    fun mayEnterActive(currentState: CallEngine.CallState): Boolean = currentState in setOf(
        CallEngine.CallState.CONNECTING,
        CallEngine.CallState.OUTGOING_RINGING,
        CallEngine.CallState.ON_HOLD
    )

    /** Camera state is independent of ICE connection state, so repeated taps can resume video. */
    fun nextVideoEnabled(currentState: Boolean): Boolean = !currentState
}
