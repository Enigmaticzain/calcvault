package com.calcvault.sync

import java.util.*

/**
 * WatchSession - Represents a shared watch session between two users
 * * Attributes:
 * - sessionId: Unique session identifier
 * - movieId: Hash-based movie identifier (SHA-256)
 * - movieName: Human-readable movie name
 * - participants: List of user IDs (Zain, Sanu, etc.)
 * - host: User ID of the host/controller
 * - currentTimeMs: Current playback position in milliseconds
 * - state: Current playback state (PLAYING, PAUSED, SEEKING, BUFFERING)
 * - createdAt: Session creation timestamp
 * - lastUpdatedAt: Last sync event timestamp
 */

enum class PlaybackState {
    PLAYING, // Both users playing
    PAUSED, // Both users paused
    SEEKING, // Host is seeking to new position
    BUFFERING, // Waiting for data
    IDLE, // Not yet started
    ENDED // Movie finished
}

data class WatchSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val movieId: String, // SHA-256 hash of video file
    val movieName: String,
    val moviePath: String, // Local path to movie file
    val movieSizeBytes: Long, // For validation
    val participants: List<String>, // [Zain, Sanu]
    val host: String, // Who controls playback
    var currentTimeMs: Long = 0L,
    var state: PlaybackState = PlaybackState.IDLE,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUpdatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if both users have the same movie (by hash)
     */
    fun isMovieMatched(otherMovieId: String, otherMoviePath: String): Boolean {
        return movieId == otherMovieId && moviePath == otherMoviePath
    }

    /**
     * Checks if session is still valid for both participants
     */
    fun isValid(): Boolean {
        return participants.size == 2 && host in participants &&
            movieId.isNotEmpty() &&
            moviePath.isNotEmpty()
    }

    /**
     * Gets the peer (non-host) participant
     */
    fun getPeer(myId: String): String? {
        return participants.firstOrNull { it != myId }
    }
}

/**
 * Sync event that travels across the network
 * Includes: play, pause, seek, buffering events
 */
data class SyncEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val senderId: String, // Who triggered this event (host)
    val eventType: SyncEventType,
    val timelinePositionMs: Long, // Position at time of event
    val state: PlaybackState,
    val timestamp: Long = System.currentTimeMillis(),
    val driftMs: Long = 0L // Current drift from follower
) {
    fun isStale(maxAgeMs: Long = 10_000): Boolean {
        return System.currentTimeMillis() - timestamp > maxAgeMs
    }
}

enum class SyncEventType {
    PLAY, // Host pressed play
    PAUSE, // Host pressed pause
    SEEK, // Host seeked to position
    BUFFERING, // Network/disk buffering started
    BUFFERING_END, // Buffering complete
    DRIFT_REPORT, // Follower reports time drift
    SYNC_REQUEST, // Follower requests resync
    SESSION_END // Host ended session
}

/**
 * Movie metadata for validation
 */
data class MovieMetadata(
    val filePath: String,
    val fileHash: String, // SHA-256
    val fileSizeBytes: Long,
    val durationMs: Long = 0L,
    val fileName: String = "",
    val mediaType: String = "video/mp4"
)

/**
 * Drift correction parameters
 */
data class SyncDrift(
    val followerTimeMs: Long, // What follower thinks current time is
    val hostTimeMs: Long, // What host says current time is
    val driftMs: Long = followerTimeMs - hostTimeMs,
    val threshold: Long = 200L // Max acceptable drift (ms)
) {
    /**
     * True if drift exceeds tolerance
     */
    fun exceedsThreshold(): Boolean = kotlin.math.abs(driftMs) > threshold

    /**
     * Correction amount needed to realign
     */
    fun getCorrectionMs(): Long = driftMs
}

/**
 * Picture-in-Picture overlay configuration
 */
data class PipConfig(
    val isEnabled: Boolean = false,
    val positionX: Int = 1000, // Right side
    val positionY: Int = 200, // Top area
    val width: Int = 300,
    val height: Int = 300,
    val isDragging: Boolean = false,
    val scale: Float = 1.0f
)
