package com.calcvault.network.webrtc

/** Conservative video bitrate adaptation from WebRTC's available-uplink and RTT/loss stats. */
object AdaptiveBitrateController {
    const val MIN_VIDEO_BITRATE_BPS = 150_000
    const val MAX_VIDEO_BITRATE_BPS = 2_500_000
    private const val DEFAULT_VIDEO_BITRATE_BPS = 600_000

    fun targetVideoBitrateBps(
        availableOutgoingBitrateBps: Long?,
        roundTripTimeMs: Long?,
        packetLossFraction: Double?
    ): Int {
        val estimated = availableOutgoingBitrateBps
            ?.takeIf { it > 0L }
            ?.let { (it * 0.75).toLong() }
            ?: DEFAULT_VIDEO_BITRATE_BPS.toLong()
        var target = estimated
        if ((packetLossFraction ?: 0.0) >= 0.10) target = (target * 0.5).toLong()
        else if ((packetLossFraction ?: 0.0) >= 0.04) target = (target * 0.75).toLong()
        if ((roundTripTimeMs ?: 0L) >= 500L) target = (target * 0.65).toLong()
        else if ((roundTripTimeMs ?: 0L) >= 250L) target = (target * 0.85).toLong()
        return target.coerceIn(MIN_VIDEO_BITRATE_BPS.toLong(), MAX_VIDEO_BITRATE_BPS.toLong()).toInt()
    }
}
