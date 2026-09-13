package com.calcvault.call.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.calcvault.network.NetworkMessageEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class ScreenShareEngine(
    private val context: Context,
    private val networkEngine: NetworkMessageEngine,
    private val eglBaseContext: EglBase.Context
) {
    companion object {
        private const val VIDEO_TRACK_ID = "screenTrack"
        private const val AUDIO_TRACK_ID = "screenAudioTrack"
        private const val STREAM_ID = "liveAssistStream"
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peerConnectionFactory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var sessionId: String = ""
    private var partnerId: String = ""
    private var isSharer = false
    private var didCleanup = false

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
        peerConnection = createPeerConnection()

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
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf(STREAM_ID)) }

        capturer?.startCapture(1280, 720, 30)
        peerConnection?.createOffer(sdpObserver, MediaConstraints())
    }

    fun initViewer(partnerId: String, sessionId: String) {
        this.partnerId = partnerId
        this.sessionId = sessionId
        this.isSharer = false
        this.didCleanup = false
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
        if (notifyPeer) {
            sendSignal("screenshare_stop")
        }
        cleanUp()
    }

    private fun createPeerConnection(): PeerConnection? {
        val config = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        )
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
            if (state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.DISCONNECTED ||
                state == PeerConnection.IceConnectionState.CLOSED
            ) {
                endSession(notifyPeer = false)
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

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp == null) return
            peerConnection?.setLocalDescription(this, sdp)
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
        if (sessionId.isNotBlank() && incomingSessionId.isNotBlank() && incomingSessionId != sessionId) {
            return
        }
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
        peerConnection?.setRemoteDescription(
            sdpObserver,
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
        peerConnection?.createAnswer(sdpObserver, MediaConstraints())
    }

    private fun onRemoteAnswerReceived(sdp: String) {
        if (sdp.isBlank()) return
        peerConnection?.setRemoteDescription(
            sdpObserver,
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private fun onRemoteIceCandidateReceived(sdp: String, sdpMLineIndex: Int, sdpMid: String) {
        if (sdp.isBlank() || sdpMid.isBlank()) return
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
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
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
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
        engineScope.cancel()
        runCatching { peerConnectionFactory.dispose() }
    }
}
