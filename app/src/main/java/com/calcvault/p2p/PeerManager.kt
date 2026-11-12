package com.calcvault.p2p

import android.util.Log
import com.calcvault.crypto.E2EKeyManager
import com.calcvault.server.EncryptedEnvelope
import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.UUID

class PeerManager {

    private val TAG = "PeerManager"
    private val gson = Gson()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            gson()
        }
        install(WebSockets)
        engine {
            requestTimeout = 10_000
        }
    }

    private val peerConnections = mutableMapOf<String, DefaultClientWebSocketSession>()

    suspend fun pingPeer(peerAddress: String, port: Int = 8443): Boolean {
        return try {
            val response = httpClient.get("http://$peerAddress:$port/ping")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            Log.w(TAG, "Peer $peerAddress is unreachable: ${e.message}")
            false
        }
    }

    suspend fun sendMessage(
        peerAddress: String,
        port: Int = 8443,
        messageText: String,
        senderId: String,
        recipientId: String
    ): Boolean {
        return try {
            val keyManager = E2EKeyManager.getInstance()
            val ciphertext = keyManager.encrypt(messageText.toByteArray())

            val envelope = EncryptedEnvelope(
                messageId = UUID.randomUUID().toString(),
                senderId = senderId,
                recipientId = recipientId,
                ciphertext = ciphertext,
                timestamp = System.currentTimeMillis()
            )

            val response = httpClient.post("http://$peerAddress:$port/api/message") {
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(envelope))
            }

            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to $peerAddress", e)
            false
        }
    }

    suspend fun connectWebSocket(
        peerAddress: String,
        port: Int = 8443,
        myUserId: String,
        onMessageReceived: (String) -> Unit
    ) {
        try {
            httpClient.webSocket(
                host = peerAddress,
                port = port,
                path = "/ws/$myUserId"
            ) {
                Log.d(TAG, "WebSocket connected to $peerAddress")
                peerConnections[peerAddress] = this

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        onMessageReceived(text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket connection failed to $peerAddress", e)
            peerConnections.remove(peerAddress)
        }
    }

    suspend fun sendViaWebSocket(peerAddress: String, message: String): Boolean {
        val session = peerConnections[peerAddress] ?: return false
        return try {
            session.send(Frame.Text(message))
            true
        } catch (e: Exception) {
            peerConnections.remove(peerAddress)
            false
        }
    }

    fun disconnectAll() {
        peerConnections.clear()
        httpClient.close()
    }
}
