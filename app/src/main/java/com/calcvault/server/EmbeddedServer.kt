package com.calcvault.server

import android.util.Log
import com.calcvault.data.db.MessageDao
import com.calcvault.data.model.Message
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class EmbeddedServer(
    private val messageDao: MessageDao,
    private val port: Int = 8443
) {
    private var server: ApplicationEngine? = null
    private val gson = Gson()
    private val TAG = "EmbeddedServer"

    // Active WebSocket connections
    val activeConnections = ConcurrentHashMap<String, DefaultWebSocketSession>()

    // Callback when message is received
    var onMessageReceived: ((Message) -> Unit)? = null

    fun start() {
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }

            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(30)
                maxFrameSize = Long.MAX_VALUE
            }

            routing {
                get("/ping") {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "alive"))
                }

                post("/api/message") {
                    try {
                        val envelope = call.receive<EncryptedEnvelope>()
                        Log.d(TAG, "Received message from: ${envelope.senderId}")

                        val message = Message(
                            id = envelope.messageId,
                            conversationId = envelope.senderId,
                            senderId = envelope.senderId,
                            recipientId = "me",
                            encryptedContent = envelope.ciphertext,
                            timestamp = envelope.timestamp,
                            status = Message.MessageStatus.DELIVERED,
                            isIncoming = true
                        )

                        messageDao.insert(message)
                        onMessageReceived?.invoke(message)

                        call.respond(HttpStatusCode.OK, mapOf("status" to "delivered"))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error receiving message", e)
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
                    }
                }

                webSocket("/ws/{userId}") {
                    val userId = call.parameters["userId"] ?: return@webSocket
                    activeConnections[userId] = this
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleWebSocketMessage(userId, text)
                            }
                        }
                    } finally {
                        activeConnections.remove(userId)
                    }
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleWebSocketMessage(senderId: String, text: String) {
        try {
            val envelope = gson.fromJson(text, EncryptedEnvelope::class.java)
            val message = Message(
                id = envelope.messageId,
                conversationId = senderId,
                senderId = senderId,
                recipientId = "me",
                encryptedContent = envelope.ciphertext,
                timestamp = envelope.timestamp,
                status = Message.MessageStatus.DELIVERED,
                isIncoming = true
            )
            messageDao.insert(message)
            onMessageReceived?.invoke(message)
        } catch (e: Exception) {
            Log.e(TAG, "WS Handle error", e)
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        activeConnections.clear()
    }

    fun isRunning(): Boolean = server != null
}

data class EncryptedEnvelope(
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val ciphertext: ByteArray,
    val timestamp: Long,
    val type: String = "text"
)
