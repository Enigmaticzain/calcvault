package com.calcvault.call

import android.content.Context

/**
 * Compatibility stub for legacy direct-IP TCP calling.
 *
 * This class intentionally opens no sockets and captures no media. Calls must use the
 * privacy-gated WebRTC path in [CallEngine]. Keeping a fail-closed adapter prevents old
 * in-app callers from silently falling back to public/local-IP TCP.
 */
@Deprecated("Raw-socket calling is disabled; use the privacy-gated CallEngine")
class SocketCallEngine(@Suppress("UNUSED_PARAMETER") context: Context) {
    enum class ConnectionState { INIT, CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    companion object {
        /** No listening port is opened for the retired transport. */
        fun callPort(): Int = 0
    }

    var suppressInternalLogging: Boolean = true
    var onStateChanged: ((ConnectionState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onLocalAudioPacket: ((ByteArray) -> Unit)? = null
    var onRemoteAudioPacket: ((ByteArray) -> Unit)? = null

    var connectionState: ConnectionState = ConnectionState.INIT
        private set

    fun startCall(): String {
        failClosed()
        return ""
    }

    fun answerCall(callerIp: String) {
        failClosed()
    }

    fun endCall() {
        connectionState = ConnectionState.DISCONNECTED
        onStateChanged?.invoke(connectionState)
    }

    fun toggleMute() = Unit

    fun toggleSpeaker(on: Boolean) = Unit

    fun setRemotePlaybackVolume(volume: Float) = Unit

    /** Never resolves or returns a peer address. */
    fun getLocalIp(): String = "unavailable"

    private fun failClosed() {
        connectionState = ConnectionState.FAILED
        onError?.invoke("Direct-IP TCP calling is disabled. No alternate route was attempted.")
        onStateChanged?.invoke(connectionState)
    }
}
