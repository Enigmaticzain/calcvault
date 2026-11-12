package com.calcvault.sync

import kotlin.math.max
import kotlin.math.roundToLong

/** Pure playback timing calculations, separated from the socket/WebRTC transport. */
data class RoundTripTimingSample(
    val localSentAtMs: Long,
    val remoteReceivedAtMs: Long,
    val remoteSentAtMs: Long,
    val localReceivedAtMs: Long
)

data class NetworkTimingEstimate(
    /** Remote clock minus local clock, in milliseconds. */
    val remoteClockOffsetMs: Long,
    val roundTripTimeMs: Long,
    val estimatedOneWayDelayMs: Long
)

object WatchSyncMath {
    /**
     * NTP-style clock/RTT estimate. All four values must use wall clocks from
     * their respective devices and be captured for one request/response exchange.
     */
    fun estimateTiming(sample: RoundTripTimingSample): NetworkTimingEstimate? {
        if (sample.localSentAtMs < 0L || sample.remoteReceivedAtMs < 0L ||
            sample.remoteSentAtMs < 0L || sample.localReceivedAtMs < 0L ||
            sample.localReceivedAtMs < sample.localSentAtMs ||
            sample.remoteSentAtMs < sample.remoteReceivedAtMs
        ) {
            return null
        }

        val localElapsed = sample.localReceivedAtMs - sample.localSentAtMs
        val remoteProcessing = sample.remoteSentAtMs - sample.remoteReceivedAtMs
        if (remoteProcessing > localElapsed) return null
        val roundTrip = localElapsed - remoteProcessing
        val offset = (
            (sample.remoteReceivedAtMs.toDouble() - sample.localSentAtMs.toDouble()) +
                (sample.remoteSentAtMs.toDouble() - sample.localReceivedAtMs.toDouble())
            ) / 2.0

        return NetworkTimingEstimate(
            remoteClockOffsetMs = offset.roundToLong(),
            roundTripTimeMs = roundTrip,
            estimatedOneWayDelayMs = roundTrip / 2L
        )
    }

    /**
     * Project a host position to the time it arrives at the follower. Paused
     * playback is not advanced. The caller supplies an RTT/2 estimate, avoiding
     * any assumption that the devices' wall clocks are perfectly synchronized.
     */
    fun projectPositionAtArrival(
        positionMs: Long,
        state: PlaybackState,
        estimatedOneWayDelayMs: Long
    ): Long {
        val safePosition = max(0L, positionMs)
        val safeDelay = estimatedOneWayDelayMs.coerceIn(0L, MAX_COMPENSATION_MS)
        return if (state == PlaybackState.PLAYING) saturatingAdd(safePosition, safeDelay) else safePosition
    }

    /**
     * Convert a remote wall-clock event timestamp to age using the measured
     * remote-minus-local offset. Reject both delayed replays and implausibly
     * future-dated events. Transport authentication remains the caller's job.
     */
    fun isEventStale(
        remoteEventTimestampMs: Long,
        localNowMs: Long,
        remoteMinusLocalClockOffsetMs: Long,
        maxAgeMs: Long = 10_000L,
        maxFutureSkewMs: Long = 5_000L
    ): Boolean {
        if (remoteEventTimestampMs < 0L || localNowMs < 0L || maxAgeMs < 0L || maxFutureSkewMs < 0L) {
            return true
        }
        val ageMs = localNowMs.toDouble() + remoteMinusLocalClockOffsetMs.toDouble() -
            remoteEventTimestampMs.toDouble()
        return ageMs > maxAgeMs.toDouble() || ageMs < -maxFutureSkewMs.toDouble()
    }

    /** Project the host's current position from its last observed position. */
    fun projectPositionNow(
        positionAtObservationMs: Long,
        observedAtLocalMs: Long,
        nowLocalMs: Long,
        state: PlaybackState
    ): Long {
        val safePosition = max(0L, positionAtObservationMs)
        if (state != PlaybackState.PLAYING || observedAtLocalMs < 0L || nowLocalMs < 0L ||
            nowLocalMs <= observedAtLocalMs
        ) {
            return safePosition
        }
        val elapsed = (nowLocalMs - observedAtLocalMs).coerceAtMost(MAX_PROJECTION_MS)
        return saturatingAdd(safePosition, elapsed)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private const val MAX_COMPENSATION_MS = 2_000L
    private const val MAX_PROJECTION_MS = 60_000L
}
