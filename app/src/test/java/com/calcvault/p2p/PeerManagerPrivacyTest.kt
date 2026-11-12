package com.calcvault.p2p

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class PeerManagerPrivacyTest {
    @Test
    fun legacyDirectMessagingAndWebSocketTransportFailClosed() = runBlocking {
        val explanations = mutableListOf<String>()
        val manager = PeerManager { explanations += it }

        assertFalse(manager.pingPeer("192.0.2.10"))
        assertFalse(
            manager.sendMessage(
                peerAddress = "192.0.2.10",
                messageText = "private text",
                senderId = "local",
                recipientId = "peer"
            )
        )
        manager.connectWebSocket("192.0.2.10", myUserId = "local") { }
        assertFalse(manager.sendViaWebSocket("192.0.2.10", "plaintext"))

        assertEquals(4, explanations.size)
        assertTrue(explanations.all { it.contains("Message not sent") })
        assertTrue(explanations.all { it.contains("direct peer routing is not consented") })
    }
}
