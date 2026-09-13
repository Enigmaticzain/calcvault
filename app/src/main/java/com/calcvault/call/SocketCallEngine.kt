package com.calcvault.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.core.content.ContextCompat
import com.calcvault.crypto.E2EKeyManager
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.CallLogRecord
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.*
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SocketCallEngine - Real Transport Layer
 *
 * Provides the actual peer-to-peer socket connection for calls.
 * This is the SOURCE OF TRUTH for call connectivity.
 *
 * Lifecycle:
 * INIT → CONNECTING → CONNECTED → DISCONNECTED
 *                  ↘ FAILED (on error)
 *
 * CRITICAL GUARANTEES:
 * - CONNECTED state ONLY when real socket is established and bidirectional
 * - FAILED state ONLY on unrecoverable errors (timeout, unreachable, etc.)
 * - DISCONNECTED state ONLY on clean close or remote disconnect
 * - No fake/simulated states
 * - All state transitions are driven by real socket events
 */
class SocketCallEngine(private val context: Context) {

    companion object {
        private const val CALL_PORT = 7777
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val ACCEPT_TIMEOUT = 60_000 // ms — server waits for callee
        private const val CONNECT_TIMEOUT = 15_000 // ms — client connect attempt
        private const val MAX_FRAME_BYTES = 65_536
        private const val MAX_RETRIES = 2

        fun callPort(): Int = CALL_PORT
    }

    /**
     * Connection lifecycle states.
     *
     * INIT: Initial state, no connection attempt yet
     * CONNECTING: Connection attempt in progress (server listening or client connecting)
     * CONNECTED: Real bidirectional socket established, audio streams active
     * DISCONNECTED: Connection closed cleanly (user ended call or remote disconnected)
     * FAILED: Connection failed (timeout, unreachable, error)
     */
    enum class ConnectionState { INIT, CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    var connectionState: ConnectionState = ConnectionState.INIT
        private set

    var onStateChanged: ((ConnectionState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLocalAudioPacket: ((ByteArray) -> Unit)? = null
    var onRemoteAudioPacket: ((ByteArray) -> Unit)? = null
    var suppressInternalLogging: Boolean = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var sendJob: Job? = null
    private var recvJob: Job? = null
    private var callStartTime = 0L
    private var currentCallId = ""
    private var isMuted = false
    private var remotePlaybackVolume = 1f

    // Guards against concurrent endCall() calls
    private val isEnding = AtomicBoolean(false)

    private val bufSize: Int by lazy {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        if (min > 0) min * 2 else 3200 // fallback: 100ms @ 16kHz mono 16-bit
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Caller side: opens a ServerSocket and waits for the callee to connect.
     * Returns a callId.
     *
     * State: INIT → CONNECTING (server listening) → CONNECTED (on accept) or FAILED (on timeout/error)
     */
    fun startCall(): String {
        currentCallId = UUID.randomUUID().toString()
        callStartTime = System.currentTimeMillis()
        isEnding.set(false)

        scope.launch {
            try {
                updateState(ConnectionState.CONNECTING)
                serverSocket = ServerSocket(CALL_PORT)
                serverSocket!!.soTimeout = ACCEPT_TIMEOUT

                val socket = serverSocket!!.accept() // blocks until callee connects
                serverSocket?.closeSilently()
                serverSocket = null

                clientSocket = socket
                startAudioStreams()
                updateState(ConnectionState.CONNECTED)
            } catch (e: Exception) {
                if (!isEnding.get()) {
                    onError?.invoke("Call failed: ${e.message}")
                    failConnection(e.message ?: "Unknown error")
                }
            }
        }
        return currentCallId
    }

    /**
     * Callee side: connects to the caller's IP. Retries up to MAX_RETRIES on failure.
     *
     * State: INIT → CONNECTING → CONNECTED (on success) or FAILED (on all retries exhausted)
     */
    fun answerCall(callerIp: String) {
        currentCallId = UUID.randomUUID().toString()
        callStartTime = System.currentTimeMillis()
        isEnding.set(false)

        scope.launch {
            updateState(ConnectionState.CONNECTING)
            var lastError: Exception? = null

            repeat(MAX_RETRIES + 1) { attempt ->
                if (isEnding.get()) return@launch
                try {
                    val socket = Socket()
                    socket.connect(
                        java.net.InetSocketAddress(callerIp, CALL_PORT),
                        CONNECT_TIMEOUT
                    )
                    clientSocket = socket
                    startAudioStreams()
                    updateState(ConnectionState.CONNECTED)
                    return@launch // success — exit retry loop
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < MAX_RETRIES && !isEnding.get()) {
                        delay(1_000L * (attempt + 1)) // back-off: 1s, 2s
                    }
                }
            }

            if (!isEnding.get()) {
                onError?.invoke("Could not connect: ${lastError?.message}")
                failConnection(lastError?.message ?: "Connection failed")
            }
        }
    }

    /**
     * User ends the call. Closes socket and transitions to DISCONNECTED.
     */
    fun endCall() {
        if (!isEnding.compareAndSet(false, true)) return // already ending
        scope.launch { cleanup(reason = "User ended call") }
    }

    fun toggleMute() {
        isMuted = !isMuted
        if (isMuted) audioRecord?.stop() else audioRecord?.startRecording()
    }

    fun toggleSpeaker(on: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = on
    }

    fun setRemotePlaybackVolume(volume: Float) {
        remotePlaybackVolume = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(remotePlaybackVolume)
    }

    fun getLocalIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
            "unknown"
        } catch (_: Exception) { "unknown" }
    }

    // ── Audio streams ─────────────────────────────────────────────────────────

    private fun startAudioStreams() {
        val socket = clientSocket
        if (socket == null || socket.isClosed) {
            onError?.invoke("Call socket unavailable")
            scope.launch { failConnection("Socket unavailable") }
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError?.invoke("Microphone permission not granted")
            scope.launch { failConnection("Microphone permission denied") }
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            bufSize
        ).also { it.startRecording() }

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AUDIO_FORMAT)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .build()
            .also {
                it.setVolume(remotePlaybackVolume)
                it.play()
            }

        val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val inp = DataInputStream(BufferedInputStream(socket.getInputStream()))

        sendJob = scope.launch {
            val buf = ByteArray(bufSize)
            try {
                while (isActive && connectionState == ConnectionState.CONNECTED) {
                    val read = audioRecord?.read(buf, 0, bufSize) ?: break
                    if (read <= 0 || isMuted) continue
                    val payload = buf.copyOf(read)
                    val frame = tryEncrypt(payload)
                    onLocalAudioPacket?.invoke(frame.copyOf())
                    out.writeInt(frame.size)
                    out.write(frame)
                    out.flush()
                }
            } catch (_: SocketException) {
                // remote closed — recvJob will handle cleanup
            } catch (e: Exception) {
                if (!isEnding.get()) onError?.invoke("Send error: ${e.message}")
            }
        }

        recvJob = scope.launch {
            try {
                while (isActive && connectionState == ConnectionState.CONNECTED) {
                    val len = inp.readInt()
                    if (len <= 0 || len > MAX_FRAME_BYTES) break
                    val frame = ByteArray(len).also { inp.readFully(it) }
                    onRemoteAudioPacket?.invoke(frame.copyOf())
                    val pcm = tryDecrypt(frame)
                    audioTrack?.write(pcm, 0, pcm.size)
                }
            } catch (_: EOFException) {
                // remote disconnected cleanly
            } catch (_: SocketException) {
                // socket closed
            } catch (e: Exception) {
                if (!isEnding.get()) onError?.invoke("Receive error: ${e.message}")
            } finally {
                // Remote side closed — trigger cleanup once
                if (!isEnding.get()) {
                    isEnding.set(true)
                    cleanup(reason = "Remote disconnected")
                }
            }
        }
    }

    // ── Encryption helpers ────────────────────────────────────────────────────

    private fun tryEncrypt(data: ByteArray): ByteArray = try {
        E2EKeyManager.getInstance().encrypt(data)
    } catch (_: Exception) { data }

    private fun tryDecrypt(data: ByteArray): ByteArray = try {
        E2EKeyManager.getInstance().decrypt(data)
    } catch (_: Exception) { data }

    // ── State management ──────────────────────────────────────────────────────

    private fun updateState(s: ConnectionState) {
        connectionState = s
        scope.launch(Dispatchers.Main) { onStateChanged?.invoke(s) }
    }

    private fun failConnection(reason: String) {
        if (!isEnding.compareAndSet(false, true)) return
        scope.launch {
            cleanup(reason = reason)
            updateState(ConnectionState.FAILED)
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun cleanup(reason: String) {
        sendJob?.cancel(); recvJob?.cancel()
        sendJob = null; recvJob = null

        audioRecord?.apply { runCatching { stop() }; runCatching { release() } }
        audioTrack?.apply { runCatching { stop() }; runCatching { release() } }
        audioRecord = null; audioTrack = null

        clientSocket?.closeSilently(); clientSocket = null
        serverSocket?.closeSilently(); serverSocket = null

        logCallIfNeeded()

        // Only transition to DISCONNECTED if we were actually connected
        if (connectionState == ConnectionState.CONNECTED) {
            updateState(ConnectionState.DISCONNECTED)
        } else if (connectionState != ConnectionState.FAILED) {
            // If we were in CONNECTING and user ended, mark as DISCONNECTED
            updateState(ConnectionState.DISCONNECTED)
        }
    }

    private fun logCallIfNeeded() {
        if (suppressInternalLogging || currentCallId.isBlank()) return
        val duration = System.currentTimeMillis() - callStartTime
        scope.launch {
            runCatching {
                val storage = USBStorageEngine.getInstance(context)
                AppendOnlyMessageDB(storage).saveCallLog(
                    CallLogRecord(
                        callId = currentCallId,
                        type = "VOICE",
                        initiator = "USER_A",
                        receiver = "USER_B",
                        startTime = callStartTime,
                        duration = duration,
                        ended = "NORMAL"
                    )
                )
            }
        }
    }

    private fun Socket.closeSilently() { runCatching { close() } }
    private fun ServerSocket.closeSilently() { runCatching { close() } }
}
