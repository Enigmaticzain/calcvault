package com.calcvault.network.webrtc

import java.util.ArrayDeque

/** Result of offering an ICE candidate before/after its matching session description. */
enum class CandidateBufferDisposition { SEND_NOW, BUFFERED, OVERFLOW }

data class CandidateBufferResult<T>(
    val disposition: CandidateBufferDisposition,
    val immediate: T? = null
)

/**
 * Bounded ordering buffer shared by local signaling and remote ICE application.
 * A new description generation discards stale queued candidates; candidates are
 * released in insertion order only after that generation's description is signaled/applied.
 */
class DescriptionOrderedCandidateBuffer<T>(private val capacity: Int = 64) {
    private val lock = Any()
    private val pending = ArrayDeque<T>()
    private var descriptionReady = false

    init {
        require(capacity > 0)
    }

    fun beginDescription() = synchronized(lock) {
        descriptionReady = false
        pending.clear()
    }

    fun offer(candidate: T): CandidateBufferResult<T> = synchronized(lock) {
        if (descriptionReady) {
            CandidateBufferResult(CandidateBufferDisposition.SEND_NOW, candidate)
        } else if (pending.size < capacity) {
            pending.addLast(candidate)
            CandidateBufferResult(CandidateBufferDisposition.BUFFERED)
        } else {
            CandidateBufferResult(CandidateBufferDisposition.OVERFLOW)
        }
    }

    fun markDescriptionReady(): List<T> = synchronized(lock) {
        descriptionReady = true
        pending.toList().also { pending.clear() }
    }
}
