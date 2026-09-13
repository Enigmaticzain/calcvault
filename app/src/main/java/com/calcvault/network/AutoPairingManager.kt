package com.calcvault.network

import android.content.Context
import android.util.Log
import com.calcvault.sync.PermanentPairManager
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.*

/**
 * AutoPairingManager - Automatically establishes connection on app launch
 *
 * When both users have selected their identities in PermanentPairManager,
 * this manager automatically creates a WebRTC peer connection without
 * requiring a manual pairing code exchange.
 */
class AutoPairingManager(
    private val context: Context,
    private val signalingUrl: String = "wss://signal.calcvault.app"
) {

    companion object {
        private const val TAG = "AutoPairingManager"
        private const val AUTO_PAIR_TIMEOUT_MS = 30000L
    }

    private val prefs = context.getSharedPreferences("auto_pair", Context.MODE_PRIVATE)
    private var pairingManager: SecurePairingManager? = null
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var onPeerConnectedCallback: (() -> Unit)? = null
    private var onFailureCallback: ((String) -> Unit)? = null

    /**
     * Check if auto-pairing should be attempted.
     * Returns true if:
     * - This device is already paired with an identity
     * - Session isn't already connected
     */
    fun shouldAutoConnect(): Boolean {
        // Check if we're already paired with an identity
        if (!PermanentPairManager.isPaired) {
            Log.d(TAG, "Not paired - skipping auto-connect")
            return false
        }

        // Check if we're already connected (session is active)
        if (SessionManager.localUserId.isNotBlank() && SessionManager.partnerUserId.isNotBlank()) {
            Log.d(TAG, "Already connected - skipping auto-connect")
            return false
        }

        return true
    }

    /**
     * Attempt automatic peer connection using permanent identity.
     * This does NOT require a pairing code exchange.
     */
    fun connectToPeer(
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (!shouldAutoConnect()) {
            Log.d(TAG, "Auto-connect conditions not met")
            onFailure("Not eligible for auto-connect")
            return
        }

        onPeerConnectedCallback = onSuccess
        onFailureCallback = onFailure

        Log.d(TAG, "Starting auto-peer connection for ${PermanentPairManager.myRole}")
        scope.launch {
            try {
                // Initialize manager with permanent identity
                val myRole = PermanentPairManager.myRole
                pairingManager = SecurePairingManager(
                    signalingUrl = signalingUrl,
                    onPeerConnected = {
                        Log.d(TAG, "✓ Auto-peer connection successful!")
                        // Restore session from permanent pair manager
                        PermanentPairManager.restoreSession()
                        prefs.edit().putBoolean("auto_pair_connected", true).apply()
                        runOnMainThread { onPeerConnectedCallback?.invoke() }
                    },
                    onSignalReceived = { signal ->
                        Log.d(TAG, "Signal received from peer during auto-connect")
                    },
                    onError = { err ->
                        Log.e(TAG, "Auto-pair error: $err")
                        runOnMainThread { onFailureCallback?.invoke(err) }
                    }
                )

                // Start pairing with persistent device identifier (not temporary code)
                pairingManager!!.startAutoPairing(myRole)

                // Wait for connection or timeout
                withTimeoutOrNull(AUTO_PAIR_TIMEOUT_MS) {
                    delay(AUTO_PAIR_TIMEOUT_MS)
                }

                if (pairingManager != null) {
                    Log.w(TAG, "Auto-peer connection timed out")
                    onFailureCallback?.invoke("Connection timeout - falling back to code entry")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-pair failed: ${e.message}", e)
                runOnMainThread { onFailureCallback?.invoke(e.message ?: "Unknown error") }
            }
        }
    }

    /**
     * Disconnect from peer
     */
    fun disconnect() {
        pairingManager?.disconnect()
        pairingManager = null
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
    }

    private fun runOnMainThread(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}
