package com.calcvault.sync

import android.content.Context
import android.util.Log
import com.calcvault.call.SocketCallEngine
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*

/**
 * WatchSessionNetworkCoordinator - Bridges sync events with network transport
 *
 * Responsibilities:
 * - Transmit sync events over socket connection
 * - Receive and parse sync events from peer
 * - Handle network failures and retries
 * - Maintain session connection health
 *
 * Data Flow:
 * Local Event → SyncEvent → JSON → Socket → Peer's Socket → JSON → SyncEvent → Sync Engine
 */
class WatchSessionNetworkCoordinator(
    private val context: Context,
    private val socketCallEngine: SocketCallEngine
) {
    companion object {
        private const val TAG = "WatchNetworkCoord"
        const val SYNC_DATA_PORT = 7778  // Separate from voice
        const val KEEP_ALIVE_INTERVAL_MS = 5000L
        const val MAX_MESSAGE_SIZE = 4096
    }

    // Callbacks for received events
    var onSyncEventReceived: ((SyncEvent) -> Unit)? = null
    var onNetworkError: ((String) -> Unit)? = null
    var onConnectionHealthChanged: ((isHealthy: Boolean) -> Unit)? = null

    // Connection state
    private var isNetworkConnected = false
    private var lastHeartbeat = System.currentTimeMillis()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiveJob: Job? = null
    private var keepAliveJob: Job? = null

    /**
     * Start network coordination
     * Sets up receive loop and health monitoring
     */
    fun startNetworkCoordination() {
        Log.d(TAG, "Starting network coordination")
        isNetworkConnected = true
        
        // Start receiving sync events
        startReceiveLoop()
        
        // Start keep-alive heartbeat
        startKeepAlive()
    }

    /**
     * Stop network coordination
     */
    fun stopNetworkCoordination() {
        Log.d(TAG, "Stopping network coordination")
        isNetworkConnected = false
        receiveJob?.cancel()
        keepAliveJob?.cancel()
    }

    /**
     * Send a sync event to peer over network
     * Serializes event to JSON and transmits
     */
    fun sendSyncEvent(event: SyncEvent) {
        if (!isNetworkConnected) {
            onNetworkError?.invoke("Network not connected")
            return
        }

        try {
            val json = serializeSyncEvent(event)
            val message = json.toString()

            if (message.length > MAX_MESSAGE_SIZE) {
                Log.w(TAG, "Message too large: ${message.length} bytes")
                return
            }

            // Send via socket as a length-prefixed message
            socketCallEngine.sendSyncEventData(message.toByteArray())
            Log.d(TAG, "Sync event sent: ${event.eventType}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending sync event", e)
            onNetworkError?.invoke(e.message ?: "Send failed")
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * NETWORK OPERATIONS
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Start receiving loop for sync events
     */
    private fun startReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            while (isActive && isNetworkConnected) {
                try {
                    val message = socketCallEngine.receiveSyncEventData()
                    if (message.isNotEmpty()) {
                        processReceivedMessage(String(message))
                        lastHeartbeat = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    if (isNetworkConnected) {
                        Log.e(TAG, "Error receiving from socket", e)
                        onNetworkError?.invoke(e.message ?: "Receive error")
                    }
                }
                delay(10)  // Non-blocking wait between attempts
            }
        }
    }

    /**
     * Keep-alive heartbeat to detect disconnections
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && isNetworkConnected) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                
                val timeSinceLastMsg = System.currentTimeMillis() - lastHeartbeat
                val isHealthy = timeSinceLastMsg < KEEP_ALIVE_INTERVAL_MS * 3
                
                if (!isHealthy) {
                    Log.w(TAG, "Network health degraded: no message for ${timeSinceLastMsg}ms")
                    onConnectionHealthChanged?.invoke(false)
                } else {
                    onConnectionHealthChanged?.invoke(true)
                }
            }
        }
    }

    /**
     * Process a received message containing sync event
     */
    private fun processReceivedMessage(message: String) {
        try {
            val json = JSONObject(message)
            val event = deserializeSyncEvent(json)
            if (event != null) {
                Log.d(TAG, "Sync event received: ${event.eventType}")
                onSyncEventReceived?.invoke(event)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing message: ${e.message}")
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * SERIALIZATION
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Convert SyncEvent to JSON for transmission
     */
    private fun serializeSyncEvent(event: SyncEvent): JSONObject {
        return JSONObject().apply {
            put("eventId", event.eventId)
            put("sessionId", event.sessionId)
            put("senderId", event.senderId)
            put("eventType", event.eventType.name)
            put("timelinePositionMs", event.timelinePositionMs)
            put("state", event.state.name)
            put("timestamp", event.timestamp)
            put("driftMs", event.driftMs)
        }
    }

    /**
     * Convert JSON to SyncEvent
     */
    private fun deserializeSyncEvent(json: JSONObject): SyncEvent? {
        return try {
            SyncEvent(
                eventId = json.getString("eventId"),
                sessionId = json.getString("sessionId"),
                senderId = json.getString("senderId"),
                eventType = SyncEventType.valueOf(json.getString("eventType")),
                timelinePositionMs = json.getLong("timelinePositionMs"),
                state = PlaybackState.valueOf(json.getString("state")),
                timestamp = json.getLong("timestamp"),
                driftMs = json.getLong("driftMs")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize event: ${e.message}")
            null
        }
    }
}

/**
 * Extension to SocketCallEngine for sync event data
 * This should be added to the actual SocketCallEngine class
 */

// TODO: Add these extension methods to SocketCallEngine:
// fun sendSyncEventData(data: ByteArray)
// fun receiveSyncEventData(): ByteArray

// For now, here's a reference implementation:

class SyncEventDataChannel {
    /**
     * Send sync event data over the existing socket connection
     * Uses a simple length-prefixed message format:
     *
     * [4 bytes: length] [N bytes: JSON data]
     */
    fun sendMessage(outputStream: OutputStream, message: ByteArray) {
        val length = message.length.toLong()
        val lengthBytes = ByteArray(4)
        lengthBytes[0] = ((length shr 24) and 0xFF).toByte()
        lengthBytes[1] = ((length shr 16) and 0xFF).toByte()
        lengthBytes[2] = ((length shr 8) and 0xFF).toByte()
        lengthBytes[3] = (length and 0xFF).toByte()

        val combined = lengthBytes + message
        outputStream.write(combined)
        outputStream.flush()
    }

    /**
     * Receive sync event data from the existing socket connection
     * Reads length-prefixed messages
     */
    fun receiveMessage(inputStream: InputStream): ByteArray? {
        return try {
            val lengthBytes = ByteArray(4)
            if (inputStream.read(lengthBytes) != 4) {
                return null  // Connection closed
            }

            val length = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                        ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                        ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                        (lengthBytes[3].toInt() and 0xFF)

            if (length <= 0 || length > 1_000_000) {
                return null  // Sanity check
            }

            val messageBytes = ByteArray(length)
            var totalRead = 0
            while (totalRead < length) {
                val read = inputStream.read(messageBytes, totalRead, length - totalRead)
                if (read < 0) return null
                totalRead += read
            }

            messageBytes
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Session state synchronizer
 * Periodically syncs session state between host and follower
 */
class SessionStateSynchronizer(
    private val coordinator: WatchSessionNetworkCoordinator,
    private val syncEngine: PlaybackSynchronizationEngine
) {
    private var syncJob: Job? = null

    fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(1000)  // Sync every second
                
                // Follower reports drift periodically
                val status = syncEngine.getSyncStatus()
                if (status.role == PlaybackSynchronizationEngine.Role.FOLLOWER) {
                    val driftEvent = SyncEvent(
                        sessionId = syncEngine.getCurrentSession()?.sessionId ?: "",
                        senderId = "follower",  // TODO: Use actual ID
                        eventType = SyncEventType.DRIFT_REPORT,
                        timelinePositionMs = 0L,
                        state = syncEngine.getCurrentSession()?.state ?: PlaybackState.IDLE,
                        driftMs = status.currentDriftMs
                    )
                    coordinator.sendSyncEvent(driftEvent)
                }
            }
        }
    }

    fun stopPeriodicSync() {
        syncJob?.cancel()
    }
}
