package com.calcvault.listen

/**
 * Playback state inside a Listen Together session.
 */
enum class PlaybackState {
    PLAYING,
    PAUSED,
    SEEKING
}

enum class SyncRole {
    HOST,
    FOLLOWER
}

data class MusicSession(
    val sessionId: String,
    val trackId: String,
    val participants: List<String>, // Zain, Sanu
    val host: String,
    val currentTimeMs: Long,
    val state: PlaybackState // PLAYING / PAUSED / SEEKING
)

data class TrackFingerprint(
    val trackId: String,
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val path: String
)

data class LocalMusicTrack(
    val fileName: String,
    val displayName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val durationMs: Long,
    var sha256: String = ""
)

object ListenDriftPolicy {
    const val SOFT_DRIFT_MS = 50L
    const val HARD_DRIFT_MS = 100L
    const val DURATION_MATCH_TOLERANCE_MS = 250L
}
