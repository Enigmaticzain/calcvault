package com.calcvault.ui.watch

import org.json.JSONObject

/** In-process handoff for Watch Together signaling consumed by another polling engine. */
object WatchSignalInbox {
    private const val MAX_QUEUED = 128
    private val lock = Any()
    private val queued = ArrayDeque<JSONObject>()
    private var activeSessionId = ""
    private var listener: ((JSONObject) -> Unit)? = null

    fun offer(signal: JSONObject) {
        if (!signal.optString("type").startsWith("watch_")) return
        val copy = JSONObject(signal.toString())
        val activeListener = synchronized(lock) {
            if (copy.optString("sessionId") == activeSessionId && listener != null) {
                listener
            } else {
                if (queued.size >= MAX_QUEUED) queued.removeFirst()
                queued.addLast(copy)
                null
            }
        }
        activeListener?.invoke(copy)
    }

    fun register(sessionId: String, onSignal: (JSONObject) -> Unit) {
        synchronized(lock) {
            activeSessionId = sessionId
            listener = onSignal
        }
    }

    fun drain(sessionId: String): List<JSONObject> = synchronized(lock) {
        val matching = queued.filter { it.optString("sessionId") == sessionId }
        queued.removeAll { it.optString("sessionId") == sessionId }
        matching
    }

    fun unregister(sessionId: String) {
        synchronized(lock) {
            if (activeSessionId == sessionId) {
                activeSessionId = ""
                listener = null
            }
            queued.removeAll { it.optString("sessionId") == sessionId }
        }
    }
}
