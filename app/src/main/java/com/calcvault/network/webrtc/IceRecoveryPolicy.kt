package com.calcvault.network.webrtc

/**
 * Bounded retry budget for ICE restarts after an interruption. A retry only restarts
 * ICE on the existing peer connection; this policy never selects another route.
 */
internal class IceRecoveryPolicy(
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS
) {
    init {
        require(retryDelaysMs.isNotEmpty()) { "At least one ICE recovery delay is required" }
        require(retryDelaysMs.all { it > 0L }) { "ICE recovery delays must be positive" }
    }

    private var attemptsUsed = 0

    /** Returns the next bounded retry delay, or null once recovery is exhausted. */
    @Synchronized
    fun nextRetryDelayMs(): Long? {
        if (attemptsUsed >= retryDelaysMs.size) return null
        return retryDelaysMs[attemptsUsed++]
    }

    /** A stable ICE connection starts a fresh recovery budget for future interruptions. */
    @Synchronized
    fun reset() {
        attemptsUsed = 0
    }

    @Synchronized
    fun attemptsUsed(): Int = attemptsUsed

    private companion object {
        val DEFAULT_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L)
    }
}
