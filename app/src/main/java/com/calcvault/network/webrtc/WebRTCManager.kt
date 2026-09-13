package com.calcvault.network.webrtc

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * WebRTCManager
 *
 * Full WebRTC PeerConnection lifecycle management.
 * * CURRENT STATUS: Stubs implemented. Returning false to allow fallback to Signaling Relay.
 * To enable real P2P, integrate libwebrtc.aar and implement the commented logic.
 */
class WebRTCManager(
    private val context: Context,
    private val signalingUrl: String,
    private val localUserId: String,
    private val remoteUserId: String
) {
    var onDataChannelMessage: ((ByteArray) -> Unit)? = null
    var onConnectionStateChange: ((String) -> Unit)? = null
    var onRemoteAudioTrack: ((Any) -> Unit)? = null
    var onRemoteVideoTrack: ((Any) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class State { IDLE, OFFERING, ANSWERING, CONNECTED, DISCONNECTED, FAILED }
    var state = State.IDLE
        private set

    /**
     * Initiate a call — create offer, send via signaling, wait for answer.
     */
    suspend fun createOffer(withVideo: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        // Return false for now as this is a stub.
        // This forces NetworkMessageEngine to use SIGNALING_RELAY which is functional.
        false
    }

    /**
     * Answer an incoming call — receive offer, create answer, send back.
     */
    suspend fun answerOffer(offerSdp: String): Boolean = withContext(Dispatchers.IO) {
        false
    }

    /**
     * Send bytes via WebRTC data channel.
     */
    fun sendData(data: ByteArray): Boolean {
        return false
    }

    fun observeDataChannel() { }

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

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) { }

    fun addLocalAudioTrack() { }

    fun addLocalVideoTrack() { }

    fun disconnect() {
        state = State.DISCONNECTED
        scope.launch { sendToSignaling("bye", "") }
        onConnectionStateChange?.invoke("DISCONNECTED")
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

    private suspend fun sendRawToSignaling(json: String): Boolean {
        return withContext(Dispatchers.IO) {
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
}
