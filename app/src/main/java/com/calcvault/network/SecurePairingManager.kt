package com.calcvault.network

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * SecurePairingManager
 *
 * Implements code-based ephemeral pairing for WebRTC.
 * Stateless, no technical data exposed to user.
 */
class SecurePairingManager(
    private val signalingUrl: String,
    private val onPeerConnected: () -> Unit,
    private val onSignalReceived: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val endpoints = SignalingEndpointResolver.resolve(signalingUrl)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var currentCode: String? = null

    @Volatile
    private var activeAttempt = 0
    private val candidateUrls: List<String>
        get() = buildCandidateUrls()

    /**
     * Generate a random 6-character alphanumeric code.
     */
    fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Exclude confusing chars like O, 0, I, 1
        val random = Random()
        val code = (1..6)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
        currentCode = code
        return code
    }

    /**
     * Connect to signaling server and join a room with the code.
     */
    fun startPairing(code: String) {
        disconnect()
        currentCode = code.trim().uppercase(Locale.US)
        activeAttempt += 1
        connectToNextCandidate(currentCode.orEmpty(), candidateUrls.toMutableList(), activeAttempt)
    }

    /**
     * Send WebRTC signal (offer/answer/ice) to the peer.
     */
    fun sendSignal(signal: String) {
        val signalMsg = JSONObject().apply { put("signal", signal) }
        if (webSocket?.send(signalMsg.toString()) != true) {
            onError("Pairing socket is not connected")
        }
    }

    /**
     * Terminate the temporary signaling connection.
     */
    fun disconnect() {
        activeAttempt += 1
        currentCode = null
        webSocket?.cancel()
        webSocket = null
    }

    /**
     * Start auto-pairing using permanent device role instead of temporary code.
     * Devices with same role prefix will auto-connect on app launch.
     */
    fun startAutoPairing(deviceRole: String) {
        // Use device role as permanent room identifier (e.g., "zain_to_sanu", "sanu_to_zain")
        val autoPairCode = "auto_${deviceRole}_${System.currentTimeMillis() / 1000 % 3600}"
        startPairing(autoPairCode)
    }



    private fun connectToNextCandidate(code: String, remainingUrls: MutableList<String>, attemptId: Int) {
        val nextUrl = remainingUrls.removeFirstOrNull()
        if (nextUrl.isNullOrBlank()) {
            if (attemptId == activeAttempt) {
                onError("Connection failed: no compatible signaling endpoint")
            }
            return
        }

        val request = Request.Builder().url(nextUrl).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (attemptId != activeAttempt || currentCode != code) {
                        webSocket.close(1000, "Superseded")
                        return
                    }
                    val joinMsg = JSONObject().apply { put("join", code) }
                    webSocket.send(joinMsg.toString())
                    Log.d("PairingManager", "Joined room: $code via $nextUrl")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (attemptId != activeAttempt) return
                    try {
                        val data = JSONObject(text)
                        if (data.has("status") && data.getString("status") == "PEER_CONNECTED") {
                            onPeerConnected()
                        } else if (data.has("signal")) {
                            onSignalReceived(data.getString("signal"))
                        } else if (data.has("error")) {
                            onError(data.getString("error"))
                        }
                    } catch (e: Exception) {
                        Log.e("PairingManager", "Error parsing message: ${e.message}")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (attemptId != activeAttempt) return
                    this@SecurePairingManager.webSocket = null
                    if (remainingUrls.isNotEmpty()) {
                        connectToNextCandidate(code, remainingUrls, attemptId)
                        return
                    }
                    onError("Connection failed: ${t.message ?: "unknown error"}")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (attemptId == activeAttempt && this@SecurePairingManager.webSocket === webSocket) {
                        this@SecurePairingManager.webSocket = null
                    }
                }
            }
        )
    }

    private fun buildCandidateUrls(): List<String> {
        val urls = linkedSetOf<String>()
        val raw = signalingUrl.trim().trimEnd('/').takeIf { it.isNotBlank() }
        if (!raw.isNullOrBlank()) {
            val normalizedRaw = SignalingEndpointResolver.resolve(raw)
            if (raw.startsWith("ws://", ignoreCase = true) || raw.startsWith("wss://", ignoreCase = true)) {
                urls.add(raw)
            }
            urls.add(normalizedRaw.wsUrl("/ws"))
            urls.add(normalizedRaw.wsBaseUrl)
            
            // Add port + 1 for built-in Android Signaling Server
            if (raw.contains(":37112")) {
                val directWs = raw.replace(":37112", ":37113")
                urls.add(directWs.replace("http", "ws") + "/ws")
                urls.add(directWs.replace("http", "ws"))
            }
        }
        urls.addAll(endpoints.wsCandidates("/ws", "/"))
        return urls.toList()
    }
}
