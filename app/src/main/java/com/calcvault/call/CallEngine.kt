package com.calcvault.call

import android.content.Context
import android.media.AudioManager
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.CallLogRecord
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.network.privacy.ActivePrivacySessionRegistry
import com.calcvault.network.privacy.PrivacyControlStore
import com.calcvault.network.privacy.PrivacyFeature
import com.calcvault.network.privacy.PrivacyMode
import com.calcvault.network.privacy.SafetyContactStore
import com.calcvault.network.webrtc.WebRTCManager
import com.calcvault.network.webrtc.WebRtcIcePolicy
import com.calcvault.network.webrtc.WebRtcIceSettingsStore
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Call lifecycle and WebRTC signaling; raw public-IP TCP calling is intentionally not used. */
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
        var isInitiator: Boolean = true,
        var pendingOfferSdp: String = "",
        val pendingIceMessages: MutableList<JSONObject> = mutableListOf()
    )

    var currentCall: ActiveCall? = null
        private set

    var onCallStateChanged: ((CallState) -> Unit)? = null
    var onCallError: ((String) -> Unit)? = null
    var onInboundCall: ((String, String, CallType, String) -> Unit)? = null
    var onLocalVideoTrack: ((org.webrtc.VideoTrack) -> Unit)? = null
    var onRemoteVideoTrack: ((org.webrtc.VideoTrack) -> Unit)? = null
    var onWebRtcManagerCreated: ((WebRTCManager) -> Unit)? = null
    var onCallRouteDisclosure: ((String) -> Unit)? = null

    private val callScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val safetyContacts = SafetyContactStore(context)
    private var signalingEngine: NetworkMessageEngine? = null
    private var expectedPeerUserId: String? = null
    private var webRtcManager: WebRTCManager? = null
    private var timeoutJob: Job? = null
    private var originalAudioMode: Int? = null
    private var originalSpeakerState: Boolean? = null
    private var activePrivacySessionId: String? = null
    private var isEnding = false

    private companion object {
        private const val OFFER_TIMEOUT_MS = 45_000L
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val MAX_PENDING_ICE_CANDIDATES = 64
    }

    fun attachSignaling(engine: NetworkMessageEngine, expectedRemoteUserId: String? = null) {
        signalingEngine = engine
        expectedPeerUserId = expectedRemoteUserId?.takeIf { it.isNotBlank() }
    }

    fun attachSignalHandler() {
        signalingEngine?.onControlSignalReceived = ::handleIncomingSignal
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

        val mode = selectedPrivacyMode(type)
        val route = WebRtcIcePolicy.plan(mode, WebRtcIceSettingsStore(context).load())
        if (!route.allowed) {
            onCallError?.invoke(route.explanation)
            notifyStateChange(CallState.FAILED)
            return
        }

        val call = ActiveCall(
            callId = UUID.randomUUID().toString(),
            type = type,
            localUser = localUser,
            remoteUser = remoteUser,
            startTime = System.currentTimeMillis(),
            state = CallState.IDLE,
            isInitiator = true
        )
        currentCall = call
        isEnding = false
        applyCallAudioRouting(call)
        callScope.launch {
            val sent = signal.sendControlSignal(
                "call_offer",
                JSONObject().put("callId", call.callId).put("callType", type.name)
            )
            if (!sent) {
                onCallError?.invoke("Call invitation could not be delivered. No alternate route was attempted.")
                endCallInternal(call, "FAILED", notifyRemote = false)
                return@launch
            }

            updateCallState(call, CallState.OUTGOING_RINGING)
            startTimeout(OFFER_TIMEOUT_MS, "NO_ANSWER")
            val manager = createWebRtcManager(call, mode)
            if (!manager.createOffer(withVideo = type == CallType.VIDEO)) {
                endCallInternal(call, "FAILED", notifyRemote = true)
            }
        }
    }

    fun answerCall() {
        val call = currentCall ?: return
        if (call.state != CallState.INCOMING_RINGING) {
            onCallError?.invoke("Call is not in ringing state")
            return
        }
        if (call.pendingOfferSdp.isBlank()) {
            onCallError?.invoke("Call offer is still arriving. Please try Answer again in a moment.")
            return
        }
        val signal = signalingEngine ?: run {
            onCallError?.invoke("Call signaling unavailable")
            return
        }
        val mode = selectedPrivacyMode(call.type)
        val route = WebRtcIcePolicy.plan(mode, WebRtcIceSettingsStore(context).load())
        if (!route.allowed) {
            onCallError?.invoke(route.explanation)
            callScope.launch { endCallInternal(call, "FAILED", notifyRemote = true) }
            return
        }

        callScope.launch {
            val accepted = signal.sendControlSignal(
                "call_accept",
                JSONObject().put("callId", call.callId).put("callType", call.type.name)
            )
            if (!accepted) {
                onCallError?.invoke("Unable to accept call. No alternate route was attempted.")
                endCallInternal(call, "FAILED", notifyRemote = false)
                return@launch
            }
            updateCallState(call, CallState.CONNECTING)
            applyCallAudioRouting(call)
            startTimeout(CONNECT_TIMEOUT_MS, "CONNECTION_TIMEOUT")
            val manager = createWebRtcManager(call, mode)
            if (!manager.answerOffer(call.pendingOfferSdp, withVideo = call.type == CallType.VIDEO)) {
                endCallInternal(call, "FAILED", notifyRemote = true)
            } else {
                call.pendingIceMessages.toList().forEach(manager::handleSignal)
                call.pendingIceMessages.clear()
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
        if (safetyContacts.isBlocked(fromUser)) {
            onCallError?.invoke("Call ignored: this contact is blocked")
            return
        }
        val call = ActiveCall(
            callId = callId.ifBlank { UUID.randomUUID().toString() },
            type = type,
            localUser = localUser,
            remoteUser = fromUser,
            startTime = System.currentTimeMillis(),
            state = CallState.INCOMING_RINGING,
            signalingAddress = "", // Never accept a caller-supplied IP address.
            isInitiator = false
        )
        currentCall = call
        isEnding = false
        updateCallState(call, CallState.INCOMING_RINGING)
        startTimeout(OFFER_TIMEOUT_MS, "NO_ANSWER")
        onInboundCall?.invoke(call.callId, fromUser, type, "")
    }

    fun handleIncomingSignal(json: JSONObject) {
        val call = currentCall
        when (json.optString("type")) {
            "call_offer" -> {
                val sender = json.optString("from")
                if (sender.isBlank() || safetyContacts.isBlocked(sender) ||
                    (expectedPeerUserId != null && sender != expectedPeerUserId)
                ) return
                if (call == null) {
                    onIncomingCall(
                        callId = json.optString("callId"),
                        fromUser = sender,
                        type = if (json.optString("callType").equals("VIDEO", true)) CallType.VIDEO else CallType.VOICE,
                        localUser = json.optString("to")
                    )
                }
            }
            "call_accept" -> if (call != null && isMatchingCall(json, call)) onRemoteCallAccepted(json.optString("callId"))
            "call_reject" -> if (call != null && isMatchingCall(json, call)) {
                onRemoteCallRejected(json.optString("callId"), json.optString("reason", "DECLINED"))
            }
            "call_end" -> if (call != null && isMatchingCall(json, call)) {
                onRemoteCallEnded(json.optString("callId"), json.optString("reason", "REMOTE_ENDED"))
            }
            "webrtc_offer" -> {
                if (call != null && isMatchingCall(json, call)) {
                    if (call.state == CallState.INCOMING_RINGING) {
                        call.pendingOfferSdp = json.optString("sdp")
                    } else {
                        webRtcManager?.handleSignal(json)
                    }
                }
            }
            "webrtc_answer", "webrtc_ice", "webrtc_bye" -> {
                if (call != null && isMatchingCall(json, call)) {
                    val manager = webRtcManager
                    if (manager != null) {
                        manager.handleSignal(json)
                    } else if (json.optString("type") == "webrtc_ice" &&
                        call.pendingIceMessages.size < MAX_PENDING_ICE_CANDIDATES
                    ) {
                        call.pendingIceMessages += JSONObject(json.toString())
                    }
                }
            }
        }
    }

    fun onRemoteCallAccepted(callId: String?) {
        val call = currentCall ?: return
        if (!matchesCallId(callId, call) || call.state != CallState.OUTGOING_RINGING) return
        timeoutJob?.cancel()
        updateCallState(call, CallState.CONNECTING)
        startTimeout(CONNECT_TIMEOUT_MS, "CONNECTION_TIMEOUT")
    }

    fun onRemoteCallRejected(callId: String?, reason: String = "DECLINED") {
        val call = currentCall ?: return
        if (!matchesCallId(callId, call)) return
        onCallError?.invoke("Call declined by partner")
        callScope.launch { endCallInternal(call, reason, notifyRemote = false) }
    }

    fun onRemoteCallEnded(callId: String?, reason: String = "REMOTE_ENDED") {
        val call = currentCall ?: return
        if (!matchesCallId(callId, call)) return
        callScope.launch { endCallInternal(call, reason, notifyRemote = false) }
    }

    fun endCall() {
        val call = currentCall ?: return
        callScope.launch { endCallInternal(call, "NORMAL", notifyRemote = true) }
    }

    fun declineCall() {
        val call = currentCall ?: return
        callScope.launch { endCallInternal(call, "DECLINED", notifyRemote = true) }
    }

    fun toggleMute() {
        val call = currentCall ?: return
        call.isMuted = !call.isMuted
        webRtcManager?.setAudioEnabled(!call.isMuted)
    }

    fun toggleSpeaker() {
        val call = currentCall ?: return
        call.isSpeakerOn = !call.isSpeakerOn
        applyCallAudioRouting(call)
    }

    @Suppress("DEPRECATION")
    private fun applyCallAudioRouting(call: ActiveCall) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (originalAudioMode == null) originalAudioMode = audioManager.mode
        if (originalSpeakerState == null) originalSpeakerState = audioManager.isSpeakerphoneOn
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = call.isSpeakerOn
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreAudioRouting() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            originalSpeakerState?.let { speaker -> audioManager.isSpeakerphoneOn = speaker }
            originalAudioMode?.let { audioManager.mode = it }
        }
        originalAudioMode = null
        originalSpeakerState = null
    }

    fun toggleVideo() {
        val call = currentCall ?: return
        if (call.type != CallType.VIDEO) return
        val isEnabled = webRtcManager?.state == WebRTCManager.State.CONNECTED
        webRtcManager?.setVideoEnabled(!isEnabled)
    }

    /** Recording is disabled until an explicit, consented WebRTC recording pipeline exists. */
    fun startRecording(target: RecordingTarget) {
        onCallError?.invoke("Call recording is disabled until its consent and encrypted-storage flow is implemented")
    }

    fun stopRecording() {
        currentCall?.isRecording = false
    }

    private fun createWebRtcManager(call: ActiveCall, mode: PrivacyMode): WebRTCManager {
        webRtcManager?.disconnect()
        val manager = WebRTCManager(
            context = context,
            signaling = signalingEngine ?: throw IllegalStateException("Call signaling unavailable"),
            localUserId = call.localUser,
            remoteUserId = call.remoteUser,
            callId = call.callId,
            privacyMode = mode,
            iceCredentials = WebRtcIceSettingsStore(context).load()
        )
        manager.onLocalVideoTrack = { onLocalVideoTrack?.invoke(it) }
        manager.onRemoteVideoTrack = { onRemoteVideoTrack?.invoke(it) }
        manager.onRouteDisclosure = { disclosure -> onCallRouteDisclosure?.invoke(disclosure) }
        manager.onError = { error ->
            onCallError?.invoke(error)
            if (manager.state == WebRTCManager.State.FAILED) {
                callScope.launch { endCallInternal(call, "FAILED", notifyRemote = false) }
            }
        }
        manager.onConnectionStateChange = { state, detail ->
            when (state) {
                WebRTCManager.State.CONNECTED -> {
                    timeoutJob?.cancel()
                    if (currentCall?.callId == call.callId && CallNegotiationPolicy.mayEnterActive(call.state)) {
                        updateCallState(call, CallState.ACTIVE)
                    }
                }
                WebRTCManager.State.RECONNECTING -> {
                    if (currentCall?.callId == call.callId) updateCallState(call, CallState.ON_HOLD)
                    onCallError?.invoke(detail)
                }
                WebRTCManager.State.FAILED,
                WebRTCManager.State.DISCONNECTED -> {
                    if (currentCall?.callId == call.callId && !isEnding) {
                        callScope.launch { endCallInternal(call, "FAILED", notifyRemote = false) }
                    }
                }
                else -> Unit
            }
        }
        webRtcManager = manager
        onWebRtcManagerCreated?.invoke(manager)
        return manager
    }

    private fun selectedPrivacyMode(type: CallType): PrivacyMode {
        val feature = if (type == CallType.VIDEO) PrivacyFeature.VIDEO_CALLS else PrivacyFeature.AUDIO_CALLS
        return PrivacyControlStore(context).mode(feature)
    }

    private fun startTimeout(durationMs: Long, reason: String) {
        timeoutJob?.cancel()
        timeoutJob = callScope.launch {
            delay(durationMs)
            val call = currentCall ?: return@launch
            if (reason == "NO_ANSWER" && call.state != CallState.INCOMING_RINGING &&
                call.state != CallState.OUTGOING_RINGING
            ) return@launch
            onCallError?.invoke(
                if (reason == "NO_ANSWER") "No answer from partner." else "WebRTC connection timed out on the selected route."
            )
            endCallInternal(call, reason, notifyRemote = true)
        }
    }

    private suspend fun endCallInternal(call: ActiveCall, reason: String, notifyRemote: Boolean) {
        if (currentCall?.callId != call.callId || isEnding) return
        isEnding = true
        timeoutJob?.cancel()
        val signal = signalingEngine
        if (notifyRemote && signal != null) {
            runCatching {
                signal.sendControlSignal(
                    if (reason == "DECLINED") "call_reject" else "call_end",
                    JSONObject().put("callId", call.callId).put("reason", reason)
                )
            }
        }
        webRtcManager?.disconnect()
        webRtcManager = null
        restoreAudioRouting()
        val isFailure = reason in listOf("FAILED", "NO_ANSWER", "CONNECTION_TIMEOUT", "DECLINED")
        call.state = if (isFailure) CallState.FAILED else CallState.ENDED
        val duration = System.currentTimeMillis() - call.startTime
        runCatching {
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
        }
        currentCall = null
        ActivePrivacySessionRegistry.unregister(activePrivacySessionId)
        activePrivacySessionId = null
        notifyStateChange(call.state)
        isEnding = false
    }

    private fun updateCallState(call: ActiveCall, newState: CallState) {
        call.state = newState
        if (newState == CallState.ACTIVE && activePrivacySessionId == null) {
            activePrivacySessionId = "call:${call.callId}"
            ActivePrivacySessionRegistry.register(
                id = activePrivacySessionId!!,
                peerId = call.remoteUser,
                feature = if (call.type == CallType.VIDEO) "Video call" else "Audio call"
            ) {
                callScope.launch {
                    currentCall?.takeIf { it.callId == call.callId }?.let {
                        endCallInternal(it, "PRIVACY_REVOKED", notifyRemote = true)
                    }
                }
            }
        }
        notifyStateChange(newState)
    }

    private fun isMatchingCall(json: JSONObject, call: ActiveCall): Boolean =
        CallNegotiationPolicy.matchesSignal(
            expectedCallId = call.callId,
            incomingCallId = json.optString("callId"),
            expectedPeerId = call.remoteUser,
            senderId = json.optString("from")
        )

    private fun matchesCallId(callId: String?, call: ActiveCall): Boolean =
        callId != null && CallNegotiationPolicy.matchesSignal(
            expectedCallId = call.callId,
            incomingCallId = callId,
            expectedPeerId = call.remoteUser,
            senderId = call.remoteUser
        )

    private fun notifyStateChange(state: CallState) {
        callScope.launch(Dispatchers.Main) { onCallStateChanged?.invoke(state) }
    }

    fun destroy() {
        timeoutJob?.cancel()
        ActivePrivacySessionRegistry.unregister(activePrivacySessionId)
        activePrivacySessionId = null
        isEnding = true
        currentCall = null
        val manager = webRtcManager
        webRtcManager = null
        manager?.onConnectionStateChange = null
        manager?.onRemoteAudioTrack = null
        manager?.onRemoteVideoTrack = null
        manager?.onLocalVideoTrack = null
        manager?.disconnect()
        restoreAudioRouting()
        callScope.cancel()
    }
}
