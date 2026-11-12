package com.calcvault.network.privacy

import java.util.concurrent.ConcurrentHashMap

/** In-process registry used by Privacy Controls to end local active sessions. */
object ActivePrivacySessionRegistry {
    data class ActiveSession(
        val id: String,
        val peerId: String,
        val feature: String,
        val startedAtMs: Long
    )

    private data class Entry(val session: ActiveSession, val endLocally: () -> Unit)
    private val entries = ConcurrentHashMap<String, Entry>()

    fun register(id: String, peerId: String, feature: String, endLocally: () -> Unit) {
        if (id.isBlank()) return
        entries[id] = Entry(
            ActiveSession(id, peerId, feature, System.currentTimeMillis()),
            endLocally
        )
    }

    fun unregister(id: String?) {
        if (!id.isNullOrBlank()) entries.remove(id)
    }

    fun activeSessions(): List<ActiveSession> = entries.values.map { it.session }
        .sortedBy { it.startedAtMs }

    fun endLocally(id: String): Boolean {
        val entry = entries.remove(id) ?: return false
        runCatching { entry.endLocally() }
        return true
    }
}
