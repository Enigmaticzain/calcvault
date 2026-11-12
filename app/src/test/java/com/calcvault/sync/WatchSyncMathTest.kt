package com.calcvault.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchSyncMathTest {

    @Test
    fun estimatesClockOffsetAndRoundTripDelay() {
        val estimate = WatchSyncMath.estimateTiming(
            RoundTripTimingSample(
                localSentAtMs = 1_000L,
                remoteReceivedAtMs = 1_030L,
                remoteSentAtMs = 1_035L,
                localReceivedAtMs = 1_075L
            )
        )

        assertEquals(-5L, estimate?.remoteClockOffsetMs)
        assertEquals(70L, estimate?.roundTripTimeMs)
        assertEquals(35L, estimate?.estimatedOneWayDelayMs)
    }

    @Test
    fun rejectsImpossibleTimingSample() {
        val estimate = WatchSyncMath.estimateTiming(
            RoundTripTimingSample(
                localSentAtMs = 2_000L,
                remoteReceivedAtMs = 1_000L,
                remoteSentAtMs = 1_100L,
                localReceivedAtMs = 1_900L
            )
        )

        assertNull(estimate)
    }

    @Test
    fun arrivalCompensationAdvancesPlayingButNotPausedPosition() {
        assertEquals(
            10_350L,
            WatchSyncMath.projectPositionAtArrival(10_000L, PlaybackState.PLAYING, 350L)
        )
        assertEquals(
            10_000L,
            WatchSyncMath.projectPositionAtArrival(10_000L, PlaybackState.PAUSED, 350L)
        )
    }

    @Test
    fun compensationAndProjectionAreBounded() {
        assertEquals(
            12_000L,
            WatchSyncMath.projectPositionAtArrival(10_000L, PlaybackState.PLAYING, 90_000L)
        )
        assertEquals(
            70_000L,
            WatchSyncMath.projectPositionNow(
                positionAtObservationMs = 10_000L,
                observedAtLocalMs = 1_000L,
                nowLocalMs = 100_000L,
                state = PlaybackState.PLAYING
            )
        )
    }

    @Test
    fun staleCheckAccountsForRemoteClockOffsetAndFutureSkew() {
        assertEquals(
            false,
            WatchSyncMath.isEventStale(
                remoteEventTimestampMs = 10_500L,
                localNowMs = 10_000L,
                remoteMinusLocalClockOffsetMs = 500L
            )
        )
        assertEquals(
            true,
            WatchSyncMath.isEventStale(
                remoteEventTimestampMs = 1_000L,
                localNowMs = 20_000L,
                remoteMinusLocalClockOffsetMs = 0L
            )
        )
        assertEquals(
            true,
            WatchSyncMath.isEventStale(
                remoteEventTimestampMs = 20_000L,
                localNowMs = 10_000L,
                remoteMinusLocalClockOffsetMs = 0L
            )
        )
    }

    @Test
    fun projectionDoesNotAdvancePausedPlaybackAndClampsNegativePositions() {
        assertEquals(
            0L,
            WatchSyncMath.projectPositionAtArrival(-100L, PlaybackState.PLAYING, 500L)
        )
        assertEquals(
            5_000L,
            WatchSyncMath.projectPositionNow(
                positionAtObservationMs = 5_000L,
                observedAtLocalMs = 1_000L,
                nowLocalMs = 9_000L,
                state = PlaybackState.PAUSED
            )
        )
    }
}
