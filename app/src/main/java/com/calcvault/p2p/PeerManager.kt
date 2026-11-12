package com.calcvault.p2p

/**
 * Compatibility stub for the retired unauthenticated direct HTTP/WebSocket transport.
 * Messaging has no integrated Tor/ciphertext-relay adapter, and direct peer transport is
 * not an allowed Messaging privacy choice, so this class performs no network I/O.
 */
@Deprecated("Direct P2P messaging is disabled until a feature-approved encrypted route exists")
class PeerManager(
    private val onPrivacyRouteUnavailable: ((String) -> Unit)? = null
) {
    private val unavailableReason =
        "Message not sent: no authenticated Tor/ciphertext-relay route is implemented, and direct peer routing is not consented."

    suspend fun pingPeer(@Suppress("UNUSED_PARAMETER") peerAddress: String, @Suppress("UNUSED_PARAMETER") port: Int = 8443): Boolean {
        reportUnavailable()
        return false
    }

    suspend fun sendMessage(
        @Suppress("UNUSED_PARAMETER") peerAddress: String,
        @Suppress("UNUSED_PARAMETER") port: Int = 8443,
        @Suppress("UNUSED_PARAMETER") messageText: String,
        @Suppress("UNUSED_PARAMETER") senderId: String,
        @Suppress("UNUSED_PARAMETER") recipientId: String
    ): Boolean {
        reportUnavailable()
        return false
    }

    suspend fun connectWebSocket(
        @Suppress("UNUSED_PARAMETER") peerAddress: String,
        @Suppress("UNUSED_PARAMETER") port: Int = 8443,
        @Suppress("UNUSED_PARAMETER") myUserId: String,
        @Suppress("UNUSED_PARAMETER") onMessageReceived: (String) -> Unit
    ) {
        reportUnavailable()
    }

    suspend fun sendViaWebSocket(
        @Suppress("UNUSED_PARAMETER") peerAddress: String,
        @Suppress("UNUSED_PARAMETER") message: String
    ): Boolean {
        reportUnavailable()
        return false
    }

    fun disconnectAll() = Unit

    private fun reportUnavailable() {
        onPrivacyRouteUnavailable?.invoke(unavailableReason)
    }
}
