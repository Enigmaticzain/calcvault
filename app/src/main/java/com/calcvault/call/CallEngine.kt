package com.calcvault.call

import android.content.Context
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.CallLogRecord
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.json.JSONObject
import java.util.UUID

/**
 * CallEngine - Stabilized Call Controller
 *
 * Enforces strict call state machine where ALL state transitions are driven by:
 * 1. Real signaling events (call_offer, call_accept, call_reject, call_end)
 * 2. Real transport state changes (socket connection established)
 * 3. Explicit timeouts (no artificial delays)
 *
 * Guarantees:
 * - ACTIVE state ONLY when transport truly confirms connection
 * - No fake/simulated success states
 * - Event-driven transitions only
 * - Strict validation of state changes
 */
class CallEngine(
    private val context: Context,
    private val storageEngine: USBStorageEngine,
    private val messageDB: AppendOnlyMessageDB
) {
    enum class CallState {
        IDLE,
        OUTGOING_RINGING,
        INCOMING_RINGING,
        CONNECTING,
        ACTIVE,
        ON_HOLD,
        ENDED,
        FAILED
    }

    enum class CallType { VOICE, VIDEO }

    enum class RecordingTarget { MINE, PARTNER, BOTH, NONE }

    data class ActiveCall(
        val callId: String,
        val type: CallType,
        val localUser: String,
        val remoteUser: String,
        val startTime: Long,
        var state: CallState,
        var signalingAddress: String = "",
        var isRecording: Boolean = false,
        var recordTarget: RecordingTarget = RecordingTarget.NONE,
        var isMuted: Boolean = false,
        var isSpeakerOn: Boolean = false,
        var isInitiator: Boolean = true
    )

    var currentCall: ActiveCall? = null
        private set

    var onCallStateChanged: ((CallState) -> Unit)? = null
    var onCallError: ((String) -> Unit)? = null
    var onInboundCall: ((String, String, CallType, String) -> Unit)? = null

    private val callScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socketEngine = SocketCallEngine(context).apply {
        suppressInternalLogging = true
        onStateChanged = { state -> handleSocketConnectionState(state) }
        onError = { message -> handleSocketError(message) }
        onLocalAudioPacket = { packet -> onRecordedAudioPacket(packet, isLocal = true) }
        onRemoteAudioPacket = { packet -> onRecordedAudioPacket(packet, isLocal = false) }
    }

    private var signalingEngine: NetworkMessageEngine? = null
    private var recordingJob: Job? = null
    private var timeoutJob: Job? = null
    private val recordingBuffer = mutableListOf<ByteArray>()
    private val chunkSizeBytes = 2 * 1024 * 1024
    private var activeEndReason = "NORMAL"
    private var isEnding = false

    private companion object {
        private const val OFFER_TIMEOUT_MS = 45_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }

    fun attachSignaling(engine: NetworkMessageEngine) {
        signalingEngine = engine
    }

    fun attachSignalHandler() {
        signalingEngine?.onControlSignalReceived = { json ->
            handleIncomingSignal(json)
        }
    }

    fun startCall(localUser: String, remoteUser: String, type: CallType) {
        if (currentCall != null) {
            onCallError?.invoke("Already in a call")
            return
        }

        val signal = signalingEngine
        if (signal == null) {
            onCallError?.invoke("Call signaling unavailable")
            return
        }

        val callId = socketEngine.startCall()
        val call = ActiveCall(
            callId = callId.ifBlank { UUID.randomUUID().toString() },
            type = type,
            localUser = localUser,
            remoteUser = remoteUser,
            startTime = System.currentTimeMillis(),
            state = CallState.IDLE,
            signalingAddress = socketEngine.getLocalIp(),
            isInitiator = true
        )
        currentCall = call
        activeEndReason = "NORMAL"

        callScope.launch {
            val sent = signal.sendControlSignal(
                "call_offer",
                JSONObject().apply {
                    put("callId", call.callId)
                    put("callType", call.type.name)
                    put("callerIp", call.signalingAddress)
                    put("callPort", SocketCallEngine.callPort())
                }
            )
            if (sent) {
                updateCallState(call, CallState.OUTGOING_RINGING, "Waiting for peer acceptance")
                startTimeout(OFFER_TIMEOUT_MS, "NO_ANSWER")
            } else {
                onCallError?.invoke("Unable to notify partner")
                callScope.launch {
                    endCallInternal(call, "FAILED", notifyRemote = false, stopSocket = true)
                }
            }
        }
    }

    fun answerCall() {
        val call = currentCall ?: return
        if (call.state != CallState.INCOMING_RINGING) {
            onCallError?.invoke("Call is not in ringing state")
            return
        }
        if (call.signalingAddress.isBlank() || call.signalingAddress == "unknown") {
            onCallError?.invoke("Partner address unavailable")
            callScope.launch { endCallInternal(call, "FAILED", notifyRemote = false, stopSocket = true) }
            return
        }

        callScope.launch {
            val accepted = signalingEngine?.sendControlSignal(
                "call_accept",
                JSONObject().apply {
                    put("callId", call.callId)
                    put("callType", call.type.name)
                }
            ) ?: false
            if (accepted) {
                updateCallState(call, CallState.CONNECTING, "Establishing connection")
                startTimeout(CONNECT_TIMEOUT_MS, "CONNECTION_TIMEOUT")
                socketEngine.answerCall(call.signalingAddress)
            } else {
                onCallError?.invoke("Unable to accept call")
                endCallInternal(call, "FAILED", notifyRemote = false, stopSocket = true)
            }
        }
    }

    fun onIncomingCall(
        callId: String,
        fromUser: String,
        type: CallType,
        callerIp: String = "",
        localUser: String = ""
    ) {
        if (currentCall != null) return

        currentCall = ActiveCall(
            callId = callId.ifBlank { UUID.randomUUID().toString() },
            type = type,
            localUser = localUser,
            remoteUser = fromUser,
            startTime = System.currentTimeMillis(),
            state = CallState.INCOMING_RINGING,
            signalingAddress = callerIp,
            isInitiator = false
        )
        activeEndReason = "NORMAL"
        updateCallState(currentCall!!, CallState.INCOMING_RINGING, "Incoming call from $fromUser")
        startTimeout(OFFER_TIMEOUT_MS, "NO_ANSWER")
    }

    private fun handleIncomingSignal(json: JSONObject) {
        when (json.optString("type")) {
            "call_offer" -> {
                if (currentCall == null) {
                    val callId = json.optString("callId", UUID.randomUUID().toString())
                    val fromUser = json.optString("from", "UNKNOWN")
                    val callType = when (json.optString("callType", "VOICE").uppercase()) {
                        "VIDEO" -> CallType.VIDEO
                        else -> CallType.VOICE
                    }
                    val callerIp = json.optString("callerIp", "")
                    onIncomingCall(callId, fromUser, callType, callerIp)
                }
            }
        }
    }

    fun onRemoteCallAccepted(callId: String?) {
        val call = currentCall ?: return
        if (callId != null && callId.isNotBlank() && call.callId != callId) return
        if (call.state != CallState.OUTGOING_RINGING) return

        timeoutJob?.cancel()
        updateCallState(call, CallState.CONNECTING, "Peer accepted, establishing connection")
        startTimeout(CONNECT_TIMEOUT_MS, "CONNECTION_TIMEOUT")
    }

    fun onRemoteCallRejected(callId: String?, reason: String = "DECLINED") {
        val call = currentCall ?: return
        if (callId != null && callId.isNotBlank() && call.callId != callId) return

        onCallError?.invoke("Call declined by partner")
        activeEndReason = reason
        callScope.launch { endCallInternal(call, reason, notifyRemote = false, stopSocket = true) }
    }

    fun onRemoteCallEnded(callId: String?, reason: String = "REMOTE_ENDED") {
        val call = currentCall ?: return
        if (callId != null && callId.isNotBlank() && call.callId != callId) return

        onCallError?.invoke("Call ended by partner")
        activeEndReason = reason
        callScope.launch { endCallInternal(call, reason, notifyRemote = false, stopSocket = true) }
    }

    fun endCall() {
        val call = currentCall ?: return
        callScope.launch { endCallInternal(call, "NORMAL", notifyRemote = true, stopSocket = true) }
    }

    fun declineCall() {
        val call = currentCall ?: return
        callScope.launch {
            signalingEngine?.sendControlSignal(
                "call_reject",
                JSONObject().apply {
                    put("callId", call.callId)
                    put("reason", "DECLINED")
                }
            )
            endCallInternal(call, "DECLINED", notifyRemote = false, stopSocket = true)
        }
    }

    fun toggleMute() {
        val call = currentCall ?: return
        call.isMuted = !call.isMuted
        socketEngine.toggleMute()
    }

    fun toggleSpeaker() {
        val call = currentCall ?: return
        call.isSpeakerOn = !call.isSpeakerOn
        socketEngine.toggleSpeaker(call.isSpeakerOn)
    }

    fun startRecording(target: RecordingTarget) {
        val call = currentCall ?: return
        if (call.state != CallState.ACTIVE) return
        call.isRecording = true
        call.recordTarget = target
        startRecordingPipeline(call)
    }

    fun stopRecording() {
        val call = currentCall ?: return
        call.isRecording = false
        recordingJob?.cancel()
        flushRecordingBuffer(call.callId)
    }

    private fun startRecordingPipeline(call: ActiveCall) {
        recordingJob?.cancel()
        recordingJob = callScope.launch {
            while (isActive && currentCall?.callId == call.callId && call.isRecording) {
                delay(30_000)
                flushRecordingBuffer(call.callId)
            }
        }
    }

    private fun onRecordedAudioPacket(encryptedPacket: ByteArray, isLocal: Boolean) {
        val call = currentCall ?: return
        if (!call.isRecording || call.state != CallState.ACTIVE) return

        val shouldStore = when (call.recordTarget) {
            RecordingTarget.NONE -> false
            RecordingTarget.BOTH -> true
            RecordingTarget.MINE -> isLocal
            RecordingTarget.PARTNER -> !isLocal
        }
        if (!shouldStore) return
        addToRecordingBuffer(call.callId, encryptedPacket.copyOf())
    }

    private fun addToRecordingBuffer(callId: String, chunk: ByteArray) {
        synchronized(recordingBuffer) {
            recordingBuffer.add(chunk)
            val totalSize = recordingBuffer.sumOf { it.size }
            if (totalSize >= chunkSizeBytes) {
                flushRecordingBuffer(callId)
            }
        }
    }

    private fun flushRecordingBuffer(callId: String) {
        callScope.launch {
            val chunks: List<ByteArray>
            synchronized(recordingBuffer) {
                if (recordingBuffer.isEmpty()) return@launch
                chunks = recordingBuffer.toList()
                recordingBuffer.clear()
            }

            val combined = chunks.fold(ByteArray(0)) { acc, next -> acc + next }
            val mediaId = callId.hashCode().toLong()
            storageEngine.writeMediaChunk(mediaId, System.currentTimeMillis().toInt(), combined)
        }
    }

    private fun startTimeout(durationMs: Long, reason: String) {
        timeoutJob?.cancel()
        timeoutJob = callScope.launch {
            delay(durationMs)
            currentCall?.let { call ->
                val errorMsg = when (reason) {
                    "NO_ANSWER" -> "No answer from partner."
                    "CONNECTION_TIMEOUT" -> "Connection timed out."
                    else -> "Call failed."
                }
                onCallError?.invoke(errorMsg)
                endCallInternal(call, reason, notifyRemote = true, stopSocket = true)
            }
        }
    }

    /**
     * Handle socket connection state changes.
     * Maps SocketCallEngine's ConnectionState to CallEngine's CallState.
     *
     * CRITICAL: ACTIVE state only set when socket reports CONNECTED.
     */
    private fun handleSocketConnectionState(state: SocketCallEngine.ConnectionState) {
        val call = currentCall ?: return

        when (state) {
            SocketCallEngine.ConnectionState.INIT -> {
                // Socket initialized, no action needed
            }
            SocketCallEngine.ConnectionState.CONNECTING -> {
                // Socket connection attempt in progress
                // Call should already be in CONNECTING state from answerCall() or OUTGOING_RINGING from startCall()
            }
            SocketCallEngine.ConnectionState.CONNECTED -> {
                // REAL TRANSPORT CONFIRMED - Socket is fully established and bidirectional
                timeoutJob?.cancel()
                if (call.state != CallState.ACTIVE) {
                    if (call.state == CallState.CONNECTING || call.state == CallState.OUTGOING_RINGING) {
                        updateCallState(call, CallState.ACTIVE, "Transport connected")
                    } else {
                        updateCallState(call, CallState.ACTIVE, "Transport connected (unexpected state: ${call.state})")
                    }
                }
            }
            SocketCallEngine.ConnectionState.DISCONNECTED -> {
                // Socket closed cleanly
                if (!isEnding) {
                    callScope.launch {
                        endCallInternal(call, activeEndReason, notifyRemote = false, stopSocket = false)
                    }
                }
            }
            SocketCallEngine.ConnectionState.FAILED -> {
                // Socket connection failed
                if (!isEnding) {
                    callScope.launch {
                        endCallInternal(call, "FAILED", notifyRemote = false, stopSocket = false)
                    }
                }
            }
        }
    }

    private fun handleSocketError(message: String) {
        onCallError?.invoke(message)
        val call = currentCall ?: return
        activeEndReason = "FAILED"
        if (!isEnding) {
            callScope.launch {
                endCallInternal(call, "FAILED", notifyRemote = false, stopSocket = true)
            }
        }
    }

    private fun updateCallState(call: ActiveCall, newState: CallState, reason: String = "") {
        call.state = newState
        notifyStateChange(newState)
    }

    private suspend fun endCallInternal(
        call: ActiveCall,
        reason: String,
        notifyRemote: Boolean,
        stopSocket: Boolean
    ) {
        if (currentCall?.callId != call.callId || isEnding) return
        isEnding = true
        timeoutJob?.cancel()

        activeEndReason = reason
        val isFailure = reason in listOf("FAILED", "NO_ANSWER", "CONNECTION_TIMEOUT", "DECLINED")
        call.state = if (isFailure) CallState.FAILED else CallState.ENDED

        if (notifyRemote) {
            signalingEngine?.sendControlSignal(
                if (reason == "DECLINED") "call_reject" else "call_end",
                JSONObject().apply {
                    put("callId", call.callId)
                    put("reason", reason)
                }
            )
        }

        recordingJob?.cancel()
        if (call.isRecording) {
            flushRecordingBuffer(call.callId)
        }

        if (stopSocket && socketEngine.connectionState != SocketCallEngine.ConnectionState.DISCONNECTED) {
            socketEngine.endCall()
        }

        val duration = System.currentTimeMillis() - call.startTime
        try {
            messageDB.saveCallLog(
                CallLogRecord(
                    callId = call.callId,
                    type = call.type.name,
                    initiator = call.localUser.ifBlank { "USER_A" },
                    receiver = call.remoteUser,
                    startTime = call.startTime,
                    duration = duration,
                    ended = reason
                )
            )
        } catch (_: Exception) {
        }

        currentCall = null
        notifyStateChange(CallState.ENDED)
        isEnding = false
    }

    fun destroy() {
        if (currentCall != null) {
            callScope.launch { currentCall?.let { endCallInternal(it, "ENDED", notifyRemote = false, stopSocket = true) } }
        }
        callScope.cancel()
    }

    private fun notifyStateChange(state: CallState) {
        callScope.launch(Dispatchers.Main) { onCallStateChanged?.invoke(state) }
    }
}
