package com.calcvault.network.webrtc

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveBitrateControllerTest {
    @Test
    fun targetTracksAvailableBandwidthAndRespectsBounds() {
        assertEquals(
            750_000,
            AdaptiveBitrateController.targetVideoBitrateBps(1_000_000L, 100L, 0.0)
        )
        assertEquals(
            AdaptiveBitrateController.MIN_VIDEO_BITRATE_BPS,
            AdaptiveBitrateController.targetVideoBitrateBps(20_000L, 800L, 0.25)
        )
    }

    @Test
    fun highRttAndPacketLossReduceBitrate() {
        val clean = AdaptiveBitrateController.targetVideoBitrateBps(1_000_000L, 100L, 0.0)
        val poor = AdaptiveBitrateController.targetVideoBitrateBps(1_000_000L, 600L, 0.12)
        assertEquals(243_750, poor)
        assertEquals(750_000, clean)
    }
}
