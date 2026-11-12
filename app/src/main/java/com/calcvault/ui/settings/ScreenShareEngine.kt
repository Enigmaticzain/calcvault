package com.calcvault.call.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.network.privacy.PrivacyMode
import com.calcvault.network.webrtc.AdaptiveBitrateController
import com.calcvault.network.webrtc.IceServerCredentials
import com.calcvault.network.webrtc.WebRtcIcePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.StatsObserver
import org.webrtc.StatsReport
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class ScreenShareEngine(
    private val context: Context,
    private val networkEngine: NetworkMessageEngine,
    private val eglBaseContext: EglBase.Context,
    private val privacyMode: PrivacyMode,
    private val iceCredentials: IceServerCredentials
) {
    companion object {
        private const val VIDEO_TRACK_ID = "screenTrack"
        private const val AUDIO_TRACK_ID = "screenAudioTrack"
        private const val STREAM_ID = "liveAssistStream"
    }

    private companion object {
        const val MAX_ICE_RECOVERY_ATTEMPTS = 4
        const val STATS_SAMPLE_INTERVAL_MS = 3_000L
        val RECOVERY_BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L)
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peerConnectionFactory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSender: RtpSender? = null
    private var statsJob: kotlinx.coroutines.Job? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var sessionId: String = ""
    private var partnerId: String = ""
    private var isSharer = false
    private var didCleanup = false
    private var recoveryAttempts = 0
    private var recoveryJob: kotlinx.coroutines.Job? = null
    private var remoteDescriptionReady = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    var onRemoteStream: ((VideoTrack, AudioTrack?) -> Unit)? = null
    var onLocalTrackReady: ((VideoTrack) -> Unit)? = null
    var onPauseStateChanged: ((Boolean) -> Unit)? = null
    var onSessionEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        networkEngine.onControlSignalReceived = ::handleSignalingMessage
    }

    fun initSharer(partnerId: String, mediaProjectionIntent: Intent, sessionId: String) {
        this.partnerId = partnerId
        this.sessionId = sessionId
        this.isSharer = true
        this.didCleanup = false
        recoveryAttempts = 0
        remoteDescriptionReady = false
        pendingRemoteCandidates.clear()
        peerConnection = createPeerConnection()
        if (peerConnection == null) return

        audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(true)

        capturer = ScreenCapturerAndroid(
            mediaProjectionIntent,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    endSession(notifyPeer = true)
                }
            }
        )
        videoSource = peerConnectionFactory.createVideoSource(capturer!!.isScreencast)
        surfaceTextureHelper = SurfaceTextureHelper.create("LiveAssistCapture", eglBaseContext)
        capturer?.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
        localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource!!)
        onLocalTrackReady?.invoke(localVideoTrack!!)

        localAudioTrack?.let { peerConnection?.addTrack(it, listOf(STREAM_ID)) }
        localVideoSender = localVideoTrack?.let { peerConnection?.addTrack(it, listOf(STREAM_ID)) }

        capturer?.startCapture(1280, 720, 30)
        startAdaptiveBitrateSampling()
        peerConnection?.createOffer(sdpObserver, MediaConstraints())
    }

    fun initViewer(partnerId: String, sessionId: String) {
        this.partnerId = partnerId
        this.sessionId = sessionId
        this.isSharer = false
        this.didCleanup = false
        recoveryAttempts = 0
        remoteDescriptionReady = false
        pendingRemoteCandidates.clear()
        peerConnection = createPeerConnection()
    }

    fun pauseSharing() {
        if (!isSharer) return
        localVideoTrack?.setEnabled(false)
        onPauseStateChanged?.invoke(true)
        sendSignal("screenshare_pause")
    }

    fun resumeSharing() {
        if (!isSharer) return
        localVideoTrack?.setEnabled(true)
        onPauseStateChanged?.invoke(false)
        sendSignal("screenshare_resume")
    }

    fun endSession(notifyPeer: Boolean = true) {
        if (didCleanup) return
        didCleanup = true
        recoveryJob?.cancel()
        recoveryJob = null
        if (notifyPeer) {
            sendSignal("screenshare_stop")
        }
        cleanUp()
    }

    private fun createPeerConnection(): PeerConnection? {
        val route = WebRtcIcePolicy.plan(privacyMode, iceCredentials)
        if (!route.allowed) {
            onError?.invoke(route.explanation)
            return null
        }
        val iceServers = buildList {
            route.stunUrls.forEach { add(PeerConnection.IceServer.builder(it).createIceServer()) }
            route.turnUrls.forEach {
                add(
                    PeerConnection.IceServer.builder(it)
                        .setUsername(iceCredentials.turnUsername)
                        .setPassword(iceCredentials.turnPassword)
                        .createIceServer()
                )
            }
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportPolicy = if (route.relayOnly) {
                PeerConnection.IceTransportPolicy.RELAY
            } else {
                PeerConnection.IceTransportPolicy.ALL
            }
        }
        return peerConnectionFactory.createPeerConnection(config, peerConnectionObserver)
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null) return
            val json = JSONObject().apply {
                put("sessionId", sessionId)
                put("sdp", candidate.sdp)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("sdpMid", candidate.sdpMid)
            }
            sendSignal("screenshare_ice", json)
        }

        override fun onAddStream(stream: MediaStream?) {
            val videoTrack = stream?.videoTracks?.firstOrNull() ?: return
            val audioTrack = stream.audioTracks.firstOrNull()
            onRemoteStream?.invoke(videoTrack, audioTrack)
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    recoveryAttempts = 0
                    recoveryJob?.cancel()
                }
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED -> scheduleIceRecovery()
                PeerConnection.IceConnectionState.CLOSED -> endSession(notifyPeer = false)
                else -> Unit
            }
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
    }

    private fun startAdaptiveBitrateSampling() {
        statsJob?.cancel()
        statsJob = engineScope.launch {
            while (isActive && !didCleanup && isSharer) {
                sampleAndApplyBitrate()
                kotlinx.coroutines.delay(STATS_SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun sampleAndApplyBitrate() {
        val connection = peerConnection ?: return
        connection.getStats(object : StatsObserver {
            override fun onComplete(reports: Array<StatsReport>) {
                val reportValues = reports.flatMap { it.values.asList() }
                fun metric(name: String): Double? = reportValues
                    .firstOrNull { it.name == name }
                    ?.value
                    ?.toString()
                    ?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() }

                val availableBps = metric("availableOutgoingBitrate")?.toLong()
                val rttMs = metric("currentRoundTripTime")?.let { (it * 1_000.0).toLong() }
                val sent = metric("packetsSent")?.toLong()
                val lost = metric("packetsLost")?.toLong()
                val lossFraction = if (sent != null && lost != null && sent + lost > 0L) {
                    lost.toDouble() / (sent + lost).toDouble()
                } else {
                    null
                }
                val target = AdaptiveBitrateController.targetVideoBitrateBps(
                    availableOutgoingBitrateBps = availableBps,
                    roundTripTimeMs = rttMs,
                    packetLossFraction = lossFraction
                )
                applyVideoBitrate(target)
            }
        })
    }

    private fun applyVideoBitrate(targetBps: Int) {
        val sender = localVideoSender ?: return
        runCatching {
            val parameters = sender.parameters
            parameters.encodings.forEach { it.maxBitrateBps = targetBps }
            if (!sender.setParameters(parameters)) {
                onError?.invoke("WebRTC rejected adaptive bitrate update")
            }
        }.onFailure { error -> onError?.invoke("Could not adapt video bitrate: ${error.message}") }
    }

    private fun scheduleIceRecovery() {
        if (didCleanup || recoveryJob?.isActive == true) return
        if (recoveryAttempts >= MAX_ICE_RECOVERY_ATTEMPTS) {
            onError?.invoke("Screen-share reconnection failed on the selected privacy route; no direct fallback was attempted.")
            endSession(notifyPeer = false)
            return
        }
        val attempt = recoveryAttempts++
        recoveryJob = engineScope.launch {
            kotlinx.coroutines.delay(RECOVERY_BACKOFF_MS[attempt])
            if (didCleanup) return@launch
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            peerConnection?.createOffer(sdpObserver, constraints)
        }
    }

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp == null) return
            peerConnection?.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    val signalType = if (sdp.type == SessionDescription.Type.OFFER) {
                        "screenshare_offer"
                    } else {
                        "screenshare_answer"
                    }
                    sendSignal(
                        signalType,
                        JSONObject().apply {
                            put("sessionId", sessionId)
                            put("sdp", sdp.description)
                        }
                    )
                }
                override fun onCreateFailure(message: String?) {
                    onError?.invoke(message ?: "Failed to set local SDP")
                }
                override fun onSetFailure(message: String?) {
                    onError?.invoke(message ?: "Failed to set local SDP")
                }
            }, sdp)
        }

        override fun onSetSuccess() {}
        override fun onCreateFailure(message: String?) {
            onError?.invoke(message ?: "Failed to create SDP")
        }

        override fun onSetFailure(message: String?) {
            onError?.invoke(message ?: "Failed to set SDP")
        }
    }

    private fun handleSignalingMessage(json: JSONObject) {
        val type = json.optString("type")
        val incomingSessionId = json.optString("sessionId")
        val senderId = json.optString("from")
        if (sessionId.isBlank() || incomingSessionId != sessionId || senderId != partnerId) return
        when (type) {
            "screenshare_offer" -> onRemoteOfferReceived(json.optString("sdp"))
            "screenshare_answer" -> onRemoteAnswerReceived(json.optString("sdp"))
            "screenshare_ice" -> onRemoteIceCandidateReceived(
                sdp = json.optString("sdp"),
                sdpMLineIndex = json.optInt("sdpMLineIndex", 0),
                sdpMid = json.optString("sdpMid")
            )
            "screenshare_pause" -> onPauseStateChanged?.invoke(true)
            "screenshare_resume" -> onPauseStateChanged?.invoke(false)
            "screenshare_stop", "screenshare_end" -> endSession(notifyPeer = false)
        }
    }

    private fun onRemoteOfferReceived(sdp: String) {
        if (sdp.isBlank()) return
        setRemoteDescription(SessionDescription.Type.OFFER, sdp) {
            peerConnection?.createAnswer(sdpObserver, MediaConstraints())
        }
    }

    private fun onRemoteAnswerReceived(sdp: String) {
        if (sdp.isBlank()) return
        setRemoteDescription(SessionDescription.Type.ANSWER, sdp) {}
    }

    private fun setRemoteDescription(type: SessionDescription.Type, sdp: String, onReady: () -> Unit) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                remoteDescriptionReady = true
                pendingRemoteCandidates.toList().forEach { peerConnection?.addIceCandidate(it) }
                pendingRemoteCandidates.clear()
                onReady()
            }
            override fun onCreateFailure(message: String?) {
                onError?.invoke(message ?: "Failed to apply remote SDP")
            }
            override fun onSetFailure(message: String?) {
                onError?.invoke(message ?: "Failed to apply remote SDP")
            }
        }, SessionDescription(type, sdp))
    }

    private fun onRemoteIceCandidateReceived(sdp: String, sdpMLineIndex: Int, sdpMid: String) {
        if (sdp.isBlank() || sdpMid.isBlank()) return
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        if (remoteDescriptionReady) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingRemoteCandidates.add(candidate)
        }
    }

    private fun sendSignal(type: String, extras: JSONObject = JSONObject()) {
        val payload = JSONObject(extras.toString()).apply {
            if (sessionId.isNotBlank()) put("sessionId", sessionId)
        }
        engineScope.launch {
            runCatching { networkEngine.sendControlSignal(type, payload) }
        }
    }

    private fun cleanUp() {
        statsJob?.cancel()
        statsJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        remoteDescriptionReady = false
        pendingRemoteCandidates.clear()
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        localVideoSender = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        onSessionEnded?.invoke()
    }

    fun release() {
        endSession(notifyPeer = false)
        networkEngine.onControlSignalReceived = null
        engineScope.cancel()
        runCatching { peerConnectionFactory.dispose() }
    }
}
