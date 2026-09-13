package com.calcvault.call

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket

/**
 * SyncEventDataChannel Extension for SocketCallEngine
 *
 * Adds a parallel data channel for sync events alongside audio streams.
 * This allows structured data (JSON) to be transmitted separately from audio.
 *
 * Architecture:
 * - Audio streams: PCM frames (unstructured binary)
 * - Sync channel: JSON messages (structured, length-prefixed)
 *
 * Message Format:
 * [4 bytes: length] [N bytes: JSON data]
 *
 * This should be injected into SocketCallEngine or used as a companion channel.
 */
class SyncEventDataChannel(private val socket: Socket?) {

    companion object {
        private const val TAG = "SyncDataChannel"
        const val MAX_MESSAGE_SIZE = 8192
    }

    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var receiveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callbacks
    var onMessageReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Initialize data streams from socket
     */
    fun initialize() {
        try {
            socket?.let {
                outputStream = it.getOutputStream()
                inputStream = it.getInputStream()
                startReceiveLoop()
                Log.d(TAG, "Data channel initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing data channel", e)
            onError?.invoke(e.message ?: "Init failed")
        }
    }

    /**
     * Send a message (JSON string) to peer
     * Wraps in length-prefixed format
     */
    fun sendMessage(message: String) {
        try {
            val bytes = message.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_MESSAGE_SIZE) {
                Log.w(TAG, "Message too large: ${bytes.size} bytes")
                return
            }

            val lengthBytes = encodeLength(bytes.size)
            outputStream?.write(lengthBytes)
            outputStream?.write(bytes)
            outputStream?.flush()

            Log.d(TAG, "Message sent: ${bytes.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            onError?.invoke(e.message ?: "Send failed")
        }
    }

    /**
     * Start receiving loop
     */
    private fun startReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            while (isActive) {
                try {
                    val message = receiveMessage()
                    if (message != null) {
                        onMessageReceived?.invoke(message)
                    } else {
                        break // Connection closed
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Error in receive loop", e)
                        onError?.invoke(e.message ?: "Receive failed")
                    }
                }
                delay(10)
            }
        }
    }

    /**
     * Receive a single message
     */
    private suspend fun receiveMessage(): String? = withContext(Dispatchers.IO) {
        try {
            val lengthBytes = ByteArray(4)
            if (inputStream?.read(lengthBytes) != 4) {
                return@withContext null // Connection closed
            }

            val length = decodeLength(lengthBytes)
            if (length <= 0 || length > MAX_MESSAGE_SIZE) {
                Log.w(TAG, "Invalid message length: $length")
                return@withContext null
            }

            val messageBytes = ByteArray(length)
            var totalRead = 0
            while (totalRead < length) {
                val read = inputStream?.read(messageBytes, totalRead, length - totalRead)
                    ?: return@withContext null
                if (read < 0) return@withContext null
                totalRead += read
            }

            String(messageBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Stop the data channel
     */
    fun stop() {
        receiveJob?.cancel()
        try {
            outputStream?.close()
            inputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping channel", e)
        }
    }

    // ── HELPERS ──────────────────────────────────────────────────────────

    private fun encodeLength(length: Int): ByteArray {
        return byteArrayOf(
            ((length shr 24) and 0xFF).toByte(),
            ((length shr 16) and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte()
        )
    }

    private fun decodeLength(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }
}

/**
 * Extension functions for SocketCallEngine to support sync events
 *
 * Usage:
 * ```
 * socketEngine.initializeSyncChannel()
 * socketEngine.onSyncMessageReceived = { message ->
 *     // Handle incoming sync event
 * }
 * socketEngine.sendSyncMessage(jsonString)
 * ```
 */
private var syncDataChannel: SyncEventDataChannel? = null

/**
 * Initialize sync data channel alongside audio
 * Call this after connection is established in SocketCallEngine
 */
fun SocketCallEngine.initializeSyncChannel() {
    // This requires modification to SocketCallEngine
    // to expose the client socket after connection
    Log.d("SocketCallEngine", "Sync channel initialization requested")
}

/**
 * Send a sync message to peer
 */
fun SocketCallEngine.sendSyncMessage(message: String) {
    syncDataChannel?.sendMessage(message)
}

/**
 * Receive handler for sync messages
 * Set this to receive callbacks when sync events arrive
 */
var SocketCallEngine.onSyncMessageReceived: ((String) -> Unit)?
    get() = null
    set(value) {
        syncDataChannel?.onMessageReceived = value
    }

/**
 * Alternative: Dual-channel architecture wrapper
 *
 * This creates TWO separate socket connections:
 * 1. Audio channel (SocketCallEngine) - unstructured PCM frames
 * 2. Sync channel (SyncEventDataChannel) - structured JSON messages
 *
 * This approach is more robust but uses more resources.
 */
class DualChannelCallCoordinator(
    private val primarySocket: Socket,
    private val audioChannel: SocketCallEngine,
    private val syncChannel: SyncEventDataChannel
) {
    /**
     * Initialize both channels
     */
    fun initialize() {
        audioChannel.initializeSyncChannel()
        syncChannel.initialize()
    }

    /**
     * Send audio frame through audio channel
     */
    fun sendAudioFrame(frame: ByteArray) {
        // Handled by SocketCallEngine
    }

    /**
     * Send sync event through data channel
     */
    fun sendSyncEvent(syncJson: String) {
        syncChannel.sendMessage(syncJson)
    }

    /**
     * Cleanup both channels
     */
    fun cleanup() {
        audioChannel.endCall()
        syncChannel.stop()
        primarySocket.close()
    }
}
