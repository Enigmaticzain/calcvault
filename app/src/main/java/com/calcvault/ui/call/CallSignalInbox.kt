package com.calcvault.ui.call

import org.json.JSONObject

/**
 * Short-lived in-process handoff for call signaling received by MainVaultActivity
 * while it launches CallActivity. This is buffering, not peer authentication.
 */
object CallSignalInbox {
    private const val MAX_QUEUED_SIGNALS = 128
    private val lock = Any()
    private val queued = ArrayDeque<JSONObject>()

    fun offer(signal: JSONObject) {
        val type = signal.optString("type")
        if (type !in CALL_SIGNAL_TYPES) return
        synchronized(lock) {
            if (queued.size >= MAX_QUEUED_SIGNALS) queued.removeFirst()
            queued.addLast(JSONObject(signal.toString()))
        }
    }

    fun drain(callId: String): List<JSONObject> = synchronized(lock) {
        val matching = queued.filter { it.optString("callId") == callId }
        queued.removeAll { it.optString("callId") == callId }
        matching
    }

    fun discard(callId: String?) {
        if (callId.isNullOrBlank()) return
        synchronized(lock) { queued.removeAll { it.optString("callId") == callId } }
    }

    private val CALL_SIGNAL_TYPES = setOf(
        "call_offer", "call_accept", "call_reject", "call_end",
        "webrtc_offer", "webrtc_answer", "webrtc_ice", "webrtc_bye"
    )
}
