package com.calcvault.network

import java.security.SecureRandom

/**
 * Fail-closed compatibility facade for pairing APIs.
 *
 * The former WebSocket implementation could exchange WebRTC signaling over a
 * server that was not proven ciphertext-only or onion-routed. Keeping the API
 * shape lets callers surface a clear error without opening a network route.
 * This class must not be treated as a secure pairing transport.
 */
@Deprecated("No authenticated Tor/ciphertext-only pairing transport is available")
class SecurePairingManager(
    @Suppress("UNUSED_PARAMETER") signalingUrl: String,
    @Suppress("UNUSED_PARAMETER") private val onPeerConnected: () -> Unit,
    @Suppress("UNUSED_PARAMETER") private val onSignalReceived: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    fun startPairing(@Suppress("UNUSED_PARAMETER") code: String) {
        reportUnavailable()
    }

    fun sendSignal(@Suppress("UNUSED_PARAMETER") signal: String) {
        reportUnavailable()
    }

    fun disconnect() {
        currentCode = null
    }

    fun startAutoPairing(@Suppress("UNUSED_PARAMETER") deviceRole: String) {
        reportUnavailable()
    }

    private fun reportUnavailable() {
        currentCode = null
        onError(UNAVAILABLE_REASON)
    }

    companion object {
        const val UNAVAILABLE_REASON =
            "Secure pairing is unavailable. Tor/onion routing and an authenticated ciphertext-only relay are not configured; no connection was attempted."
    }
}
