package com.calcvault.network.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * WebRTCManager
 *
 * Full WebRTC PeerConnection lifecycle management.
 */
class WebRTCManager(
    private val context: Context,
    private val signalingUrl: String,
    private val localUserId: String,
    private val remoteUserId: String
) {
    private val TAG = "WebRTCManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rootEglBase: EglBase by lazy { EglBase.create() }
    
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null

    var onLocalVideoTrack: ((VideoTrack) -> Unit)? = null
    var onRemoteVideoTrack: ((VideoTrack) -> Unit)? = null
    var onConnectionStateChange: ((String) -> Unit)? = null
    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null

    enum class State { IDLE, OFFERING, ANSWERING, CONNECTED, DISCONNECTED, FAILED }
    var state = State.IDLE
        private set

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val videoEncoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val videoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(isCaller: Boolean) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    onIceCandidateGenerated?.invoke(it)
                    sendIceCandidate(it.sdpMid, it.sdpMLineIndex, it.sdp)
                }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() ?: return
                if (track is VideoTrack) {
                    onRemoteVideoTrack?.invoke(track)
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                state = when(newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> State.CONNECTED
                    PeerConnection.PeerConnectionState.DISCONNECTED -> State.DISCONNECTED
                    PeerConnection.PeerConnectionState.FAILED -> State.FAILED
                    else -> state
                }
                onConnectionStateChange?.invoke(newState?.name ?: "UNKNOWN")
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })
    }

    suspend fun createOffer(withVideo: Boolean = false): Boolean = suspendCancellableCoroutine { cont ->
        if (peerConnection == null) createPeerConnection(true)
        addLocalAudioTrack()
        if (withVideo) addLocalVideoTrack()

        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        scope.launch {
                            val ok = sendToSignaling("offer", sdp.description)
                            cont.resume(ok)
                        }
                    }
                    override fun onCreateFailure(p0: String?) { cont.resume(false) }
                    override fun onSetFailure(p0: String?) { cont.resume(false) }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { cont.resume(false) }
            override fun onSetFailure(p0: String?) { cont.resume(false) }
        }, constraints)
    }

    suspend fun answerOffer(offerSdp: String): Boolean = suspendCancellableCoroutine { cont ->
        if (peerConnection == null) createPeerConnection(false)
        addLocalAudioTrack()

        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                scope.launch {
                                    val ok = sendToSignaling("answer", sdp.description)
                                    cont.resume(ok)
                                }
                            }
                            override fun onCreateFailure(p0: String?) { cont.resume(false) }
                            override fun onSetFailure(p0: String?) { cont.resume(false) }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                        }, sdp)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) { cont.resume(false) }
                    override fun onSetFailure(p0: String?) { cont.resume(false) }
                }, MediaConstraints())
            }
            override fun onSetFailure(p0: String?) { cont.resume(false) }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) { cont.resume(false) }
        }, remoteSdp)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidateSdp))
    }

    fun addLocalAudioTrack() {
        if (localAudioTrack != null) return
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio0", audioSource)
        peerConnection?.addTrack(localAudioTrack)
    }

    fun addLocalVideoTrack() {
        if (localVideoTrack != null) return
        val videoSource = peerConnectionFactory.createVideoSource(false)
        localVideoTrack = peerConnectionFactory.createVideoTrack("video0", videoSource)
        onLocalVideoTrack?.invoke(localVideoTrack!!)
        peerConnection?.addTrack(localVideoTrack)
    }

    fun disconnect() {
        state = State.DISCONNECTED
        scope.launch { sendToSignaling("bye", "") }
        peerConnection?.close()
        peerConnection = null
        onConnectionStateChange?.invoke("DISCONNECTED")
    }

    private fun sendIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        scope.launch {
            val payload = JSONObject().apply {
                put("type", "ice")
                put("from", localUserId)
                put("to", remoteUserId)
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
                put("candidate", candidateSdp)
            }.toString()
            sendRawToSignaling(payload)
        }
    }

    private suspend fun sendToSignaling(type: String, sdp: String): Boolean {
        return try {
            val payload = JSONObject().apply {
                put("type", type)
                put("from", localUserId)
                put("to", remoteUserId)
                put("sdp", sdp)
                put("ts", System.currentTimeMillis())
            }.toString()
            sendRawToSignaling(payload)
        } catch (e: Exception) { false }
    }

    private suspend fun sendRawToSignaling(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$signalingUrl/signal")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Content-Type", "application/json")
                outputStream.write(json.toByteArray())
            }
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) { false }
    }
}
