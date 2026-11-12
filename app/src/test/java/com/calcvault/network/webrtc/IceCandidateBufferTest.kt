package com.calcvault.network.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IceCandidateBufferTest {
    @Test
    fun candidatesArrivingBeforeDescriptionAreReleasedInArrivalOrder() {
        val buffer = DescriptionOrderedCandidateBuffer<String>(capacity = 3)

        assertEquals(CandidateBufferDisposition.BUFFERED, buffer.offer("candidate-1").disposition)
        assertEquals(CandidateBufferDisposition.BUFFERED, buffer.offer("candidate-2").disposition)
        assertEquals(listOf("candidate-1", "candidate-2"), buffer.markDescriptionReady())
        val next = buffer.offer("candidate-3")
        assertEquals(CandidateBufferDisposition.SEND_NOW, next.disposition)
        assertEquals("candidate-3", next.immediate)
    }

    @Test
    fun aNewNegotiationDiscardsCandidatesFromThePriorDescriptionGeneration() {
        val buffer = DescriptionOrderedCandidateBuffer<String>(capacity = 2)
        buffer.offer("stale")
        buffer.beginDescription()
        buffer.offer("current")

        assertEquals(listOf("current"), buffer.markDescriptionReady())
    }

    @Test
    fun candidateQueueOverflowIsExplicitAndBounded() {
        val buffer = DescriptionOrderedCandidateBuffer<String>(capacity = 1)
        buffer.offer("first")
        val overflow = buffer.offer("second")

        assertEquals(CandidateBufferDisposition.OVERFLOW, overflow.disposition)
        assertNull(overflow.immediate)
        assertEquals(listOf("first"), buffer.markDescriptionReady())
    }
}
