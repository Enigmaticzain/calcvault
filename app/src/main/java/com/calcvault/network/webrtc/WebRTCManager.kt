package com.calcvault.network.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.network.privacy.PrivacyMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
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
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.StatsObserver
import org.webrtc.StatsReport
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WebRTC media transport. Signaling is carried by NetworkMessageEngine; its
 * current server is not an authenticated identity system, so transport encryption
 * must not be described as verified end-to-end encryption.
 */
class WebRTCManager(
    private val context: Context,
    private val signaling: NetworkMessageEngine,
    private val localUserId: String,
    private val remoteUserId: String,
    private val callId: String,
    private val privacyMode: PrivacyMode,
    private val iceCredentials: IceServerCredentials
) {
    enum class State { IDLE, OFFERING, ANSWERING, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, FAILED }

    companion object {
        private const val STREAM_ID = "calcvault-call"
        private const val AUDIO_TRACK_ID = "calcvault-call-audio"
        private const val VIDEO_TRACK_ID = "calcvault-call-video"
        private const val STATS_INTERVAL_MS = 3_000L
        private const val MAX_DATA_MESSAGE_BYTES = 16_384
        private const val MAX_PENDING_ICE_CANDIDATES = 64
        private const val OUTGOING_SIGNAL_CAPACITY = 128
        private const val MAX_SDP_CHARS = 1_000_000
        private const val MAX_ICE_CANDIDATE_CHARS = 4_096
    }

    private data class OutgoingSignal(val type: String, val payload: JSONObject)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outgoingSignals = Channel<OutgoingSignal>(OUTGOING_SIGNAL_CAPACITY)
    private val signalEnqueueLock = Any()
    private val localCandidateBuffer = DescriptionOrderedCandidateBuffer<JSONObject>(MAX_PENDING_ICE_CANDIDATES)
    private val remoteCandidateBuffer = DescriptionOrderedCandidateBuffer<IceCandidate>(MAX_PENDING_ICE_CANDIDATES)
    private val eglBaseDelegate = lazy { EglBase.create() }
    private val eglBase: EglBase by eglBaseDelegate
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext
    private val peerConnectionFactoryDelegate = lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }
    private val peerConnectionFactory: PeerConnectionFactory by peerConnectionFactoryDelegate
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private val disposed = AtomicBoolean(false)
    private val recoveryPolicy = IceRecoveryPolicy()
    private var recoveryJob: Job? = null
    private var statsJob: Job? = null
    private var withVideo = false

    @Volatile
    var state: State = State.IDLE
        private set

    var onConnectionStateChange: ((State, String) -> Unit)? = null
    var onRemoteAudioTrack: ((AudioTrack) -> Unit)? = null
    var onRemoteVideoTrack: ((VideoTrack) -> Unit)? = null
    var onLocalVideoTrack: ((VideoTrack) -> Unit)? = null
    var onDataChannelMessage: ((ByteArray) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onRouteDisclosure: ((String) -> Unit)? = null

    init {
        scope.launch {
            for (signal in outgoingSignals) {
                if (disposed.get()) break
                val sent = runCatching {
                    signaling.sendControlSignal(signal.type, signal.payload)
                }.getOrDefault(false)
                if (!sent) {
                    fail("Signaling failed for ${signal.type}; the call was not rerouted")
                    break
                }
            }
        }
    }

    suspend fun createOffer(withVideo: Boolean = false): Boolean {
        if (disposed.get()) return false
        this.withVideo = withVideo
        val route = WebRtcIcePolicy.plan(privacyMode, iceCredentials)
        onRouteDisclosure?.invoke(route.explanation)
        if (!route.allowed) return fail(route.explanation)
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return fail("Microphone permission is required for a call")
        }
        if (withVideo && !hasPermission(Manifest.permission.CAMERA)) {
            return fail("Camera permission is required for a video call")
        }

        return try {
            createPeerConnection(route)
            addLocalMedia(withVideo)
            transition(State.OFFERING, "Creating WebRTC offer")
            val offer = createDescription(isOffer = true, iceRestart = false)
            beginLocalNegotiation()
            setLocalDescription(offer)
            sendDescription("webrtc_offer", offer)
            startStatsSampling()
            transition(State.CONNECTING, route.explanation)
            true
        } catch (error: Exception) {
            fail(error.message ?: "Could not create WebRTC offer")
        }
    }

    suspend fun answerOffer(offerSdp: String, withVideo: Boolean = false): Boolean {
        if (disposed.get()) return false
        this.withVideo = withVideo
        val route = WebRtcIcePolicy.plan(privacyMode, iceCredentials)
        onRouteDisclosure?.invoke(route.explanation)
        if (!route.allowed) return fail(route.explanation)
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return fail("Microphone permission is required for a call")
        }
        if (withVideo && !hasPermission(Manifest.permission.CAMERA)) {
            return fail("Camera permission is required for a video call")
        }
        if (offerSdp.isBlank()) return fail("The incoming call offer is missing")
        if (offerSdp.length > MAX_SDP_CHARS) return fail("The incoming call offer exceeds the size limit")

        return try {
            createPeerConnection(route)
            addLocalMedia(withVideo)
            transition(State.ANSWERING, "Applying WebRTC offer")
            setRemoteDescription(SessionDescription.Type.OFFER, offerSdp)
            val answer = createDescription(isOffer = false, iceRestart = false)
            beginLocalNegotiation()
            setLocalDescription(answer)
            sendDescription("webrtc_answer", answer)
            startStatsSampling()
            transition(State.CONNECTING, route.explanation)
            true
        } catch (error: Exception) {
            fail(error.message ?: "Could not answer WebRTC offer")
        }
    }

    /** Dispatch a previously received, sender-filtered signaling message. */
    fun handleSignal(message: JSONObject) {
        if (disposed.get() || message.optString("callId") != callId) return
        if (message.optString("from") != remoteUserId) return
        val sdp = message.optString("sdp")
        if (sdp.length > MAX_SDP_CHARS) {
            fail("Received WebRTC description exceeds the size limit")
            return
        }
        when (message.optString("type")) {
            "webrtc_answer" -> {
                // A remote answer can be observed after its ICE candidates have already
                // arrived; preserve that bounded queue until setRemoteDescription's
                // success callback flushes it. Local ICE restarts begin a generation
                // before sending their offer.
                scope.launch {
                    runCatching {
                        setRemoteDescription(SessionDescription.Type.ANSWER, message.optString("sdp"))
                        transition(State.CONNECTING, "Applying remote answer")
                    }.onFailure { fail(it.message ?: "Could not apply WebRTC answer") }
                }
            }
            "webrtc_offer" -> {
                remoteCandidateBuffer.beginDescription()
                scope.launch {
                    runCatching {
                        if (peerConnection == null) {
                            val route = WebRtcIcePolicy.plan(privacyMode, iceCredentials)
                            onRouteDisclosure?.invoke(route.explanation)
                            if (!route.allowed) throw IllegalStateException(route.explanation)
                            createPeerConnection(route)
                            addLocalMedia(withVideo)
                        }
                        setRemoteDescription(SessionDescription.Type.OFFER, message.optString("sdp"))
                        val answer = createDescription(isOffer = false, iceRestart = false)
                        beginLocalNegotiation()
                        setLocalDescription(answer)
                        sendDescription("webrtc_answer", answer)
                    }.onFailure { fail(it.message ?: "Could not renegotiate WebRTC call") }
                }
            }
            "webrtc_ice" -> addRemoteCandidate(
                message.optString("candidate"),
                message.optString("sdpMid"),
                message.optInt("sdpMLineIndex", 0)
            )
            "webrtc_bye" -> disconnect()
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setVideoEnabled(enabled: Boolean): Boolean {
        val track = localVideoTrack ?: return false
        track.setEnabled(enabled)
        return true
    }

    fun sendData(data: ByteArray): Boolean {
        if (disposed.get() || data.isEmpty() || data.size > MAX_DATA_MESSAGE_BYTES) return false
        val channel = dataChannel ?: return false
        return runCatching {
            channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(data), true))
        }.getOrDefault(false)
    }

    private var dataChannel: DataChannel? = null

    private fun createPeerConnection(route: IceRoutePlan) {
        val servers = buildList {
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
        val config = PeerConnection.RTCConfiguration(servers).apply {
            iceTransportPolicy = if (route.relayOnly) {
                PeerConnection.IceTransportPolicy.RELAY
            } else {
                PeerConnection.IceTransportPolicy.ALL
            }
        }
        peerConnection = peerConnectionFactory.createPeerConnection(config, peerObserver)
            ?: throw IllegalStateException("WebRTC could not create a peer connection")
    }

    private fun addLocalMedia(video: Boolean) {
        val pc = peerConnection ?: throw IllegalStateException("Peer connection is unavailable")
        audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        val audioTrack = localAudioTrack ?: throw IllegalStateException("Could not create the local audio track")
        audioTrack.setEnabled(true)
        if (pc.addTrack(audioTrack, listOf(STREAM_ID)) == null) {
            throw IllegalStateException("Could not attach the local audio track to WebRTC")
        }

        if (video) {
            val enumerator = Camera2Enumerator(context)
            val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: throw IllegalStateException("No camera is available")
            cameraCapturer = enumerator.createCapturer(cameraName, null)
                ?: throw IllegalStateException("Could not open the camera")
            videoSource = peerConnectionFactory.createVideoSource(false)
            textureHelper = SurfaceTextureHelper.create("CallVideoCapture", eglBaseContext)
            cameraCapturer?.initialize(textureHelper, context, videoSource!!.capturerObserver)
            val videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource!!)
                ?: throw IllegalStateException("Could not create the local video track")
            localVideoTrack = videoTrack
            videoTrack.setEnabled(true)
            onLocalVideoTrack?.invoke(videoTrack)
            localVideoSender = pc.addTrack(videoTrack, listOf(STREAM_ID))
                ?: throw IllegalStateException("Could not attach the local video track to WebRTC")
            cameraCapturer?.startCapture(640, 480, 24)
        }
    }

    private suspend fun createDescription(isOffer: Boolean, iceRestart: Boolean): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (withVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            val observer = object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description != null && continuation.isActive) continuation.resume(description)
                    else if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Empty SDP"))
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "SDP creation failed"))
                }
                override fun onSetFailure(error: String?) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "SDP update failed"))
                }
            }
            val pc = peerConnection
            if (pc == null) {
                continuation.resumeWithException(IllegalStateException("Peer connection closed"))
            } else if (isOffer) {
                pc.createOffer(observer, constraints)
            } else {
                pc.createAnswer(observer, constraints)
            }
        }

    private suspend fun setLocalDescription(description: SessionDescription) {
        setDescription(description, remote = false)
    }

    private suspend fun setRemoteDescription(type: SessionDescription.Type, sdp: String) {
        if (sdp.isBlank()) throw IllegalArgumentException("Remote SDP is empty")
        setDescription(SessionDescription(type, sdp), remote = true)
    }

    private suspend fun setDescription(description: SessionDescription, remote: Boolean) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    if (remote) {
                        val candidates = remoteCandidateBuffer.markDescriptionReady()
                        candidates.forEach { candidate ->
                            if (peerConnection?.addIceCandidate(candidate) == false) {
                                onError?.invoke("WebRTC rejected a queued ICE candidate")
                            }
                        }
                    }
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onCreateFailure(error: String?) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "SDP update failed"))
                }
                override fun onSetFailure(error: String?) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "SDP update failed"))
                }
            }
            val pc = peerConnection ?: run {
                continuation.resumeWithException(IllegalStateException("Peer connection closed"))
                return@suspendCancellableCoroutine
            }
            if (remote) pc.setRemoteDescription(observer, description) else pc.setLocalDescription(observer, description)
        }

    private fun addRemoteCandidate(candidate: String, mid: String, lineIndex: Int) {
        if (candidate.isBlank() || candidate.length > MAX_ICE_CANDIDATE_CHARS ||
            mid.isBlank() || mid.length > 64 || lineIndex < 0
        ) return
        val ice = IceCandidate(mid, lineIndex, candidate)
        val result = remoteCandidateBuffer.offer(ice)
        when (result.disposition) {
            CandidateBufferDisposition.SEND_NOW -> {
                if (result.immediate?.let { peerConnection?.addIceCandidate(it) } == false) {
                    onError?.invoke("WebRTC rejected an ICE candidate")
                }
            }
            CandidateBufferDisposition.BUFFERED -> Unit
            CandidateBufferDisposition.OVERFLOW ->
                fail("Too many ICE candidates arrived before the remote description")
        }
    }

    private fun beginLocalNegotiation() {
        synchronized(signalEnqueueLock) {
            localCandidateBuffer.beginDescription()
        }
    }

    /** Queue SDP before ICE so the receiver can correlate candidates with a description. */
    private suspend fun sendDescription(type: String, description: SessionDescription) {
        if (disposed.get()) throw IllegalStateException("Call ended before signaling")
        val failed = synchronized(signalEnqueueLock) {
            if (!enqueueSignalLocked(type, signalPayload(JSONObject().put("sdp", description.description)))) {
                true
            } else {
                localCandidateBuffer.markDescriptionReady().any { candidate ->
                    !enqueueSignalLocked("webrtc_ice", candidate)
                }
            }
        }
        if (failed) {
            fail("The bounded signaling queue is full; no alternate route was attempted")
            throw IllegalStateException("The signaling queue is full")
        }
    }

    private fun queueLocalCandidate(candidate: IceCandidate) {
        if (disposed.get() || candidate.sdp.isBlank() || candidate.sdp.length > MAX_ICE_CANDIDATE_CHARS) return
        val payload = signalPayload(
            JSONObject()
                .put("candidate", candidate.sdp)
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
        )
        var failure: String? = null
        synchronized(signalEnqueueLock) {
            when (localCandidateBuffer.offer(payload).disposition) {
                CandidateBufferDisposition.SEND_NOW -> {
                    if (!enqueueSignalLocked("webrtc_ice", payload)) {
                        failure = "The bounded signaling queue is full; no alternate route was attempted"
                    }
                }
                CandidateBufferDisposition.BUFFERED -> Unit
                CandidateBufferDisposition.OVERFLOW ->
                    failure = "Too many local ICE candidates were gathered before SDP signaling"
            }
        }
        failure?.let(::fail)
    }

    /** Must be called under signalEnqueueLock to preserve SDP-before-ICE ordering. */
    private fun enqueueSignalLocked(type: String, payload: JSONObject): Boolean =
        !disposed.get() && outgoingSignals.trySend(OutgoingSignal(type, payload)).isSuccess

    private fun signalPayload(fields: JSONObject): JSONObject = JSONObject(fields.toString()).apply {
        put("callId", callId)
        put("from", localUserId)
        put("to", remoteUserId)
        put("callType", if (withVideo) "VIDEO" else "VOICE")
    }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) queueLocalCandidate(candidate)
        }

        override fun onIceConnectionChange(iceState: PeerConnection.IceConnectionState?) {
            when (iceState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    resetRecoveryAfterConnection()
                    transition(State.CONNECTED, "WebRTC ICE connected")
                }
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED -> scheduleRecovery()
                PeerConnection.IceConnectionState.CLOSED -> transition(State.DISCONNECTED, "Call ended")
                else -> Unit
            }
        }

        override fun onAddStream(stream: MediaStream?) {
            stream?.audioTracks?.firstOrNull()?.let { onRemoteAudioTrack?.invoke(it) }
            stream?.videoTracks?.firstOrNull()?.let { onRemoteVideoTrack?.invoke(it) }
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            when (val track = receiver?.track()) {
                is AudioTrack -> onRemoteAudioTrack?.invoke(track)
                is VideoTrack -> onRemoteVideoTrack?.invoke(track)
            }
        }

        override fun onDataChannel(channel: DataChannel?) {
            dataChannel = channel
            channel?.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}
                override fun onStateChange() {}
                override fun onMessage(buffer: DataChannel.Buffer?) {
                    if (buffer == null || buffer.data.remaining() > MAX_DATA_MESSAGE_BYTES) return
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    onDataChannelMessage?.invoke(bytes)
                }
            })
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
    }

    @Synchronized
    private fun scheduleRecovery(lastFailure: String? = null) {
        if (disposed.get() || state == State.FAILED || recoveryJob?.isActive == true) return
        val delayMs = recoveryPolicy.nextRetryDelayMs()
        if (delayMs == null) {
            val detail = lastFailure?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            fail("Call reconnection failed$detail on the selected privacy route; no less-private fallback was attempted")
            return
        }
        transition(State.RECONNECTING, "Network interruption; retrying the same ICE privacy route")
        recoveryJob = scope.launch {
            delay(delayMs)
            if (disposed.get() || state == State.FAILED) return@launch
            runCatching {
                remoteCandidateBuffer.beginDescription()
                val offer = createDescription(isOffer = true, iceRestart = true)
                beginLocalNegotiation()
                setLocalDescription(offer)
                sendDescription("webrtc_offer", offer)
            }.onFailure { error ->
                retryAfterRecoveryFailure(error.message ?: "WebRTC ICE restart failed")
            }
        }
    }

    @Synchronized
    private fun retryAfterRecoveryFailure(message: String) {
        recoveryJob = null
        scheduleRecovery(message)
    }

    @Synchronized
    private fun resetRecoveryAfterConnection() {
        recoveryPolicy.reset()
        recoveryJob?.cancel()
        recoveryJob = null
    }

    private fun startStatsSampling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive && !disposed.get() && withVideo) {
                sampleStats()
                delay(STATS_INTERVAL_MS)
            }
        }
    }

    private fun sampleStats() {
        peerConnection?.getStats(object : StatsObserver {
            override fun onComplete(reports: Array<StatsReport>) {
                val values = reports.flatMap { it.values.asList() }
                fun metric(name: String): Double? = values.firstOrNull { it.name == name }
                    ?.value?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
                val lost = metric("packetsLost")?.toLong()?.coerceAtLeast(0L)
                val received = metric("packetsReceived")?.toLong()?.coerceAtLeast(0L)
                val totalPackets = if (lost != null && received != null) {
                    lost.toDouble() + received.toDouble()
                } else {
                    0.0
                }
                val observedLoss = if (totalPackets > 0.0) {
                    (lost ?: 0L).toDouble() / totalPackets
                } else {
                    metric("packetLossRate")?.coerceIn(0.0, 1.0)
                }
                val target = AdaptiveBitrateController.targetVideoBitrateBps(
                    availableOutgoingBitrateBps = metric("availableOutgoingBitrate")?.toLong(),
                    roundTripTimeMs = metric("currentRoundTripTime")?.let { (it * 1_000.0).toLong() },
                    packetLossFraction = observedLoss
                )
                val sender = localVideoSender ?: return
                runCatching {
                    val params = sender.parameters
                    params.encodings.forEach { it.maxBitrateBps = target }
                    sender.setParameters(params)
                }
            }
        })
    }

    fun disconnect() {
        if (!disposed.compareAndSet(false, true)) return
        statsJob?.cancel()
        recoveryJob?.cancel()
        runCatching { cameraCapturer?.stopCapture() }
        runCatching { cameraCapturer?.dispose() }
        cameraCapturer = null
        runCatching { textureHelper?.dispose() }
        textureHelper = null
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        localVideoSender = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { dataChannel?.dispose() }
        dataChannel = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        if (peerConnectionFactoryDelegate.isInitialized()) {
            runCatching { peerConnectionFactory.dispose() }
        }
        if (eglBaseDelegate.isInitialized()) runCatching { eglBase.release() }
        outgoingSignals.close()
        state = State.DISCONNECTED
        onConnectionStateChange?.invoke(state, "Call ended")
        scope.cancel()
    }

    private fun transition(next: State, reason: String) {
        if (disposed.get()) return
        state = next
        onConnectionStateChange?.invoke(next, reason)
    }

    @Synchronized
    private fun fail(message: String): Boolean {
        if (disposed.get()) return false
        recoveryJob?.cancel()
        recoveryJob = null
        onError?.invoke(message)
        transition(State.FAILED, message)
        return false
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

}
