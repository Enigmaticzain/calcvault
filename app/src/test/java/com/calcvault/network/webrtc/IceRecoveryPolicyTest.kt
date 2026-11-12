package com.calcvault.network.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class IceRecoveryPolicyTest {
    @Test
    fun networkInterruptionUsesBoundedIncreasingIceRestartBackoff() {
        val policy = IceRecoveryPolicy()

        assertEquals(1_000L, policy.nextRetryDelayMs() ?: -1L)
        assertEquals(2_000L, policy.nextRetryDelayMs() ?: -1L)
        assertEquals(4_000L, policy.nextRetryDelayMs() ?: -1L)
        assertEquals(8_000L, policy.nextRetryDelayMs() ?: -1L)
        assertNull(policy.nextRetryDelayMs())
        assertEquals(4, policy.attemptsUsed())
    }

    @Test
    fun repeatedRestartFailuresExhaustBudgetInsteadOfRetryingForever() {
        val policy = IceRecoveryPolicy(listOf(10L, 20L))
        var failedRestarts = 0
        while (policy.nextRetryDelayMs() != null) {
            failedRestarts += 1
        }

        assertEquals(2, failedRestarts)
        assertNull(policy.nextRetryDelayMs())
    }

    @Test
    fun stableConnectionResetsRecoveryBudgetForNextInterruption() {
        val policy = IceRecoveryPolicy(listOf(10L, 20L))
        assertEquals(10L, policy.nextRetryDelayMs() ?: -1L)
        assertEquals(20L, policy.nextRetryDelayMs() ?: -1L)
        assertNull(policy.nextRetryDelayMs())

        policy.reset()

        assertEquals(0, policy.attemptsUsed())
        assertEquals(10L, policy.nextRetryDelayMs())
    }

    @Test
    fun invalidRetryScheduleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { IceRecoveryPolicy(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { IceRecoveryPolicy(listOf(0L)) }
        assertThrows(IllegalArgumentException::class.java) { IceRecoveryPolicy(listOf(-1L)) }
    }
}
