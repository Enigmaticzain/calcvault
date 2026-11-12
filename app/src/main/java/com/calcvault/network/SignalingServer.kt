package com.calcvault.network

import android.util.Log

/**
 * Compatibility stub for the retired unauthenticated raw HTTP signaling server.
 * Messaging and call signaling must go through feature-gated NetworkMessageEngine;
 * that engine rejects non-HTTPS endpoints and content sends remain fail-closed.
 */
@Deprecated("Unauthenticated raw signaling is disabled; use a feature-gated HTTPS route")
class SignalingServer(@Suppress("UNUSED_PARAMETER") port: Int) {
    fun start() {
        Log.w(TAG, "Raw HTTP signaling is disabled. No socket was opened and no route was attempted.")
    }

    fun stop() = Unit

    private companion object {
        const val TAG = "SignalingServer"
    }
}
