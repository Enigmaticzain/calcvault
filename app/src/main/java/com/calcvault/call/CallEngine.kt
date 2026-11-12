package com.calcvault.call

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
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
import com.calcvault.utils.PairedUserIdentity
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
        val startElapsedRealtimeMs: Long,
        @Volatile var state: CallState,
        @Volatile var answerInProgress: Boolean = false,
        @Volatile var isEnding: Boolean = false,
        var signalingAddress: String = "",
        var isRecording: Boolean = false,
        var recordTarget: RecordingTarget = RecordingTarget.NONE,
        var isMuted: Boolean = false,
        var isSpeakerOn: Boolean = false,
        var isVideoEnabled: Boolean = type == CallType.VIDEO,
        var isInitiator: Boolean = true,
        var pendingOfferSdp: String = "",
        val pendingIceMessages: MutableList<JSONObject> = mutableListOf()
    ) {
        val privacySessionId: String get() = "call:$callId"
    }

    @Volatile
    var currentCall: ActiveCall? = null
        private set

    /** Called after remote acceptance and before creating/capturing any local media. */
    var onPrepareCallMedia: (() -> Boolean)? = null

    var onCallStateChanged: ((CallState) -> Unit)? = null
    var onCallError: ((String) -> Unit)? = null
    var onInboundCall: ((String, String, CallType, String) -> Unit)? = null
    var onLocalVideoTrack: ((org.webrtc.VideoTrack) -> Unit)? = null
    var onRemoteVideoTrack: ((org.webrtc.VideoTrack) -> Unit)? = null
    var onWebRtcManagerCreated: ((WebRTCManager) -> Unit)? = null
    var onCallRouteDisclosure: ((String) -> Unit)? = null

    private val callScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleLock = Any()
    private val safetyContacts = SafetyContactStore(context)
    private var signalingEngine: NetworkMessageEngine? = null
    private var expectedPeerUserId: String? = null
    private var expectedLocalUserId: String? = null
    private var webRtcManager: WebRTCManager? = null
    private var timeoutJob: Job? = null
    private var originalAudioMode: Int? = null
    private var originalSpeakerState: Boolean? = null

    private companion object {
        private const val OFFER_TIMEOUT_MS = 45_000L
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val MAX_PENDING_ICE_CANDIDATES = 64
    }

    fun attachSignaling(
        engine: NetworkMessageEngine,
        expectedRemoteUserId: String? = null,
        expectedLocalUserId: String? = null
    ) {
        signalingEngine = engine
        this.expectedPeerUserId = PairedUserIdentity.stableId(expectedRemoteUserId)
        this.expectedLocalUserId = PairedUserIdentity.stableId(expectedLocalUserId)
    }

    fun attachSignalHandler() {
        signalingEngine?.onControlSignalReceived = ::handleIncomingSignal
    }

    @Synchronized
    fun startCall(localUser: String, remoteUser: String, type: CallType) {
        val localPairId = PairedUserIdentity.stableId(localUser)
        val remotePairId = PairedUserIdentity.stableId(remoteUser)
        if (localPairId == null || remotePairId == null || localPairId == remotePairId) {
            onCallError?.invoke("Call was not started: valid, distinct paired-user identities are required")
            notifyStateChange(CallState.FAILED)
            return
        }
        if (safetyContacts.isBlocked(remotePairId)) {
            onCallError?.invoke("Call was not started: this contact is blocked")
            notifyStateChange(CallState.FAILED)
            return
        }
        if (currentCall != null) {
            onCallError?.invoke("Already in a call")
            return
        }
        val signal = signalingEngine
        if (signal == null) {
            onCallError?.invoke("Call signaling unavailable")
            return
        }
        signal.privacyRouteUnavailableReason?.let { explanation ->
            onCallError?.invoke("Call was not started. $explanation")
            notifyStateChange(CallState.FAILED)
            return
        }

        val mode = selectedPrivacyMode(type)
        val route = WebRtcIcePolicy.plan(mode, WebRtcIceSettingsStore.loadOrEmpty(context))
        if (!route.allowed) {
            onCallError?.invoke(route.explanation)
            notifyStateChange(CallState.FAILED)
            return
        }

        val call = ActiveCall(
            callId = UUID.randomUUID().toString(),
            type = type,
            localUser = localPairId,
            remoteUser = remotePairId,
            startTime = System.currentTimeMillis(),
            startElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            state = CallState.IDLE,
            isInitiator = true
        )
        synchronized(lifecycleLock) { currentCall = call }
        registerPrivacySession(call)
        callScope.launch {
            try {
                val sent = signal.sendControlSignal(
                    "call_offer",
                    JSONObject().put("callId", call.callId).put("callType", type.name)
                )
                if (!sent) {
                    onCallError?.invoke("Call invitation could not be delivered on the selected route. No alternate route was attempted.")
                    endCallInternal(call, "FAILED", notifyRemote = false)
                    return@launch
                }

                // Do not create a peer connection or capture microphone/camera before
                // the remote user accepts the invitation.
                updateCallState(call, CallState.OUTGOING_RINGING)
                startTimeout(OFFER_TIMEOUT_MS, "NO_ANSWER")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onCallError?.invoke("Call invitation failed: ${error.message ?: "unknown error"}")
                endCallInternal(call, "FAILED", notifyRemote = false)
            }
        }
    }

    @Synchronized
    fun answerCall() {
        val call = currentCall ?: return
        if (!CallNegotiationPolicy.canAnswer(
                state = call.state,
                hasOffer = call.pendingOfferSdp.isNotBlank(),
                answerInProgress = call.answerInProgress
            )
        ) {
            val explanation = when {
                call.answerInProgress -> "Call acceptance is already being processed."
                call.state != CallState.INCOMING_RINGING -> "Call is not in ringing state."
                else -> "Call offer is still arriving. Please try Answer again in a moment."
            }
            onCallError?.invoke(explanation)
            return
        }
        if (safetyContacts.isBlocked(call.remoteUser)) {
            onCallError?.invoke("Call not answered: this contact is blocked")
            callScope.launch { endCallInternal(call, "BLOCKED", notifyRemote = true) }
            return
        }
        call.answerInProgress = true
        val signal = signalingEngine ?: run {
            onCallError?.invoke("Call signaling unavailable")
            callScope.launch { endCallInternal(call, "FAILED", notifyRemote = false) }
            return
        }
        signal.privacyRouteUnavailableReason?.let { explanation ->
            onCallError?.invoke("Call was not answered. $explanation")
            callScope.launch { endCallInternal(call, "FAILED", notifyRemote = false) }
            return
        }
        val mode = selectedPrivacyMode(call.type)
        val route = WebRtcIcePolicy.plan(mode, WebRtcIceSettingsStore.loadOrEmpty(context))
        if (!route.allowed) {
            onCallError?.invoke(route.explanation)
            callScope.launch { endCallInternal(call, "FAILED", notifyRemote = true) }
            return
        }

        callScope.launch {
            try {
                val accepted = signal.sendControlSignal(
                    "call_accept",
                    JSONObject().put("callId", call.callId).put("callType", call.type.name)
                )
                if (!accepted) {
                    onCallError?.invoke("Unable to accept call. No alternate route was attempted.")
                    endCallInternal(call, "FAILED", notifyRemote = false)
                    return@launch
                }
                if (onPrepareCallMedia?.invoke() != true) {
                    onCallError?.invoke("Call not answered: the foreground microphone/camera service could not be started. No media was captured.")
                    endCallInternal(call, "FAILED", notifyRemote = false)
                    return@launch
                }
                if (!isCurrentCall(call)) return@launch
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onCallError?.invoke("Could not prepare the call: ${error.message ?: "unknown error"}")
                endCallInternal(call, "FAILED", notifyRemote = false)
            }
        }
    }

    @Synchronized
    fun onIncomingCall(
        callId: String,
        fromUser: String,
        type: CallType,
        @Suppress("UNUSED_PARAMETER") callerIp: String = "",
        localUser: String = ""
    ) {
        if (currentCall != null) return
        val senderPairId = PairedUserIdentity.stableId(fromUser)
        val localPairId = PairedUserIdentity.stableId(localUser) ?: PairedUserIdentity.USER_A
        if (callId.isBlank() || senderPairId == null) {
            onCallError?.invoke("Call invitation ignored: missing call data or an unknown paired-user identity")
            return
        }
        if (safetyContacts.isBlocked(fromUser) || safetyContacts.isBlocked(senderPairId)) {
            onCallError?.invoke("Call ignored: this contact is blocked")
            return
        }
        val call = ActiveCall(
            callId = callId.ifBlank { UUID.randomUUID().toString() },
            type = type,
            localUser = localPairId,
            remoteUser = senderPairId,
            startTime = System.currentTimeMillis(),
            startElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            state = CallState.INCOMING_RINGING,
            signalingAddress = "", // Never accept a caller-supplied IP address.
            isInitiator = false
        )
        synchronized(lifecycleLock) { currentCall = call }
        registerPrivacySession(call)
        updateCallState(call, CallState.INCOMING_RINGING)
        startTimeout(OFFER_TIMEOUT_MS, "NO_ANSWER")
        onInboundCall?.invoke(call.callId, fromUser, type, "")
    }

    @Synchronized
    fun handleIncomingSignal(json: JSONObject) {
        val call = currentCall
        when (json.optString("type")) {
            "call_offer" -> {
                val sender = PairedUserIdentity.stableId(json.optString("from")) ?: return
                val recipient = PairedUserIdentity.stableId(json.optString("to")) ?: return
                val incomingCallId = json.optString("callId")
                if (incomingCallId.isBlank() || safetyContacts.isBlocked(sender) ||
                    (expectedPeerUserId != null && sender != expectedPeerUserId) ||
                    (expectedLocalUserId != null && recipient != expectedLocalUserId)
                ) return
                if (call == null) {
                    val incomingType = when (json.optString("callType").uppercase(java.util.Locale.US)) {
                        "VOICE" -> CallType.VOICE
                        "VIDEO" -> CallType.VIDEO
                        else -> return
                    }
                    onIncomingCall(
                        callId = incomingCallId,
                        fromUser = sender,
                        type = incomingType,
                        localUser = recipient
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

    @Synchronized
    fun onRemoteCallAccepted(callId: String?) {
        val call = currentCall ?: return
        if (!matchesCallId(callId, call) || call.state != CallState.OUTGOING_RINGING) return
        if (safetyContacts.isBlocked(call.remoteUser)) {
            onCallError?.invoke("Call ended: this contact was blocked before media setup")
            callScope.launch { endCallInternal(call, "PRIVACY_REVOKED", notifyRemote = false) }
            return
        }
        timeoutJob?.cancel()
        updateCallState(call, CallState.CONNECTING)
        val mode = selectedPrivacyMode(call.type)
        callScope.launch {
            try {
                if (currentCall?.callId != call.callId) return@launch
                if (onPrepareCallMedia?.invoke() != true) {
                    onCallError?.invoke("Call not started: the foreground microphone/camera service could not be started. No media was captured.")
                    endCallInternal(call, "FAILED", notifyRemote = false)
                    return@launch
                }
                if (!isCurrentCall(call)) return@launch
                applyCallAudioRouting(call)
                startTimeout(CONNECT_TIMEOUT_MS, "CONNECTION_TIMEOUT")
                val manager = createWebRtcManager(call, mode)
                if (!manager.createOffer(withVideo = call.type == CallType.VIDEO)) {
                    endCallInternal(call, "FAILED", notifyRemote = true)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onCallError?.invoke("Could not prepare the call: ${error.message ?: "unknown error"}")
                endCallInternal(call, "FAILED", notifyRemote = false)
            }
        }
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

    @Synchronized
    fun toggleVideo(): Boolean? {
        val call = currentCall ?: return null
        if (call.type != CallType.VIDEO) return null
        val manager = webRtcManager ?: return null
        val nextEnabled = CallNegotiationPolicy.nextVideoEnabled(call.isVideoEnabled)
        if (!manager.setVideoEnabled(nextEnabled)) return null
        call.isVideoEnabled = nextEnabled
        return nextEnabled
    }

    /** Recording is disabled until an explicit, consented WebRTC recording pipeline exists. */
    fun startRecording(target: RecordingTarget) {
        onCallError?.invoke("Call recording is disabled until its consent and encrypted-storage flow is implemented")
    }

    fun stopRecording() {
        currentCall?.isRecording = false
    }

    private fun createWebRtcManager(call: ActiveCall, mode: PrivacyMode): WebRTCManager {
        val previous = synchronized(lifecycleLock) {
            webRtcManager.also { webRtcManager = null }
        }
        previous?.disconnect()
        if (!isCurrentCall(call)) throw IllegalStateException("Call ended before media setup")
        val manager = WebRTCManager(
            context = context,
            signaling = signalingEngine ?: throw IllegalStateException("Call signaling unavailable"),
            localUserId = call.localUser,
            remoteUserId = call.remoteUser,
            callId = call.callId,
            privacyMode = mode,
            iceCredentials = WebRtcIceSettingsStore.loadOrEmpty(context)
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
                    if (currentCall?.callId == call.callId && !call.isEnding) {
                        callScope.launch { endCallInternal(call, "FAILED", notifyRemote = false) }
                    }
                }
                else -> Unit
            }
        }
        val installed = synchronized(lifecycleLock) {
            if (currentCall?.callId == call.callId && !call.isEnding) {
                webRtcManager = manager
                true
            } else {
                false
            }
        }
        if (!installed) {
            manager.disconnect()
            throw IllegalStateException("Call ended before media setup completed")
        }
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
        var managerToClose: WebRTCManager? = null
        val claimed = synchronized(lifecycleLock) {
            if (currentCall?.callId != call.callId || call.isEnding) false
            else {
                call.isEnding = true
                managerToClose = webRtcManager
                webRtcManager = null
                true
            }
        }
        if (!claimed) return
        timeoutJob?.cancel()
        val signal = signalingEngine
        if (notifyRemote && signal != null) {
            runCatching {
                signal.sendControlSignal(
                    if (reason == "DECLINED" || reason == "BLOCKED") "call_reject" else "call_end",
                    JSONObject()
                        .put("callId", call.callId)
                        .put("callType", call.type.name)
                        .put("reason", reason)
                )
            }
        }
        managerToClose?.disconnect()
        restoreAudioRouting()
        val isFailure = reason in listOf(
            "FAILED", "NO_ANSWER", "CONNECTION_TIMEOUT", "DECLINED", "PRIVACY_REVOKED", "BLOCKED"
        )
        call.state = if (isFailure) CallState.FAILED else CallState.ENDED
        val duration = (SystemClock.elapsedRealtime() - call.startElapsedRealtimeMs).coerceAtLeast(0L)
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
        synchronized(lifecycleLock) {
            if (currentCall?.callId == call.callId) currentCall = null
        }
        ActivePrivacySessionRegistry.unregister(call.privacySessionId)
        notifyStateChange(call.state)
    }

    private fun registerPrivacySession(call: ActiveCall) {
        val id = call.privacySessionId
        synchronized(lifecycleLock) {
            if (currentCall?.callId != call.callId || call.isEnding) return
            ActivePrivacySessionRegistry.register(
                id = id,
                peerId = call.remoteUser,
                feature = if (call.type == CallType.VIDEO) {
                    PrivacyFeature.VIDEO_CALLS.label
                } else {
                    PrivacyFeature.AUDIO_CALLS.label
                }
            ) {
                callScope.launch {
                    currentCall?.takeIf { it.callId == call.callId }?.let {
                        endCallInternal(it, "PRIVACY_REVOKED", notifyRemote = true)
                    }
                }
            }
        }
    }

    private fun updateCallState(call: ActiveCall, newState: CallState) {
        val updated = synchronized(lifecycleLock) {
            if (currentCall?.callId != call.callId || call.isEnding) false
            else {
                call.state = newState
                true
            }
        }
        if (updated) notifyStateChange(newState)
    }

    private fun isCurrentCall(call: ActiveCall): Boolean = synchronized(lifecycleLock) {
        currentCall?.callId == call.callId && !call.isEnding
    }

    private fun isMatchingCall(json: JSONObject, call: ActiveCall): Boolean =
        CallNegotiationPolicy.matchesSignal(
            expectedCallId = call.callId,
            incomingCallId = json.optString("callId"),
            expectedPeerId = call.remoteUser,
            senderId = PairedUserIdentity.stableId(json.optString("from")).orEmpty(),
            expectedLocalUserId = call.localUser,
            recipientId = PairedUserIdentity.stableId(json.optString("to"))
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

    @Synchronized
    fun destroy() {
        timeoutJob?.cancel()
        val resources = synchronized(lifecycleLock) {
            val call = currentCall
            call?.isEnding = true
            currentCall = null
            call to webRtcManager.also { webRtcManager = null }
        }
        ActivePrivacySessionRegistry.unregister(resources.first?.privacySessionId)
        val manager = resources.second
        manager?.onConnectionStateChange = null
        manager?.onRemoteAudioTrack = null
        manager?.onRemoteVideoTrack = null
        manager?.onLocalVideoTrack = null
        manager?.disconnect()
        restoreAudioRouting()
        onPrepareCallMedia = null
        onCallStateChanged = null
        onCallError = null
        onInboundCall = null
        onLocalVideoTrack = null
        onRemoteVideoTrack = null
        onWebRtcManagerCreated = null
        onCallRouteDisclosure = null
        callScope.cancel()
    }
}
