package com.calcvault.sync

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * PlaybackSynchronizationEngine - Keeps both devices in sync
 * * Architecture:
 * - HOST: Controls playback, broadcasts events
 * - FOLLOWER: Receives events, adjusts playback to match host
 * * Sync Strategy:
 * 1. Host sends every play/pause/seek event
 * 2. Follower listens and adjusts
 * 3. Continuous monitoring detects drift
 * 4. Auto-correction if drift > threshold (100-200ms)
 * 5. Manual resync button available anytime
 * * Events flow: User Action → Host → Network → Follower
 */
class PlaybackSynchronizationEngine {

    companion object {
        private const val TAG = "PlaybackSync"
        const val MAX_DRIFT_MS = 200L // 200ms tolerance
        const val DRIFT_CHECK_INTERVAL = 500L // Check every 500ms
        const val SOFT_CORRECTION_MS = 100L // Gentle catch-up speed
    }

    // Role in the session
    enum class Role { HOST, FOLLOWER, NONE }

    // Current session
    private var currentSession: WatchSession? = null
    private var myRole: Role = Role.NONE
    private var myUserId: String = ""

    // Playback state tracking
    private val lastHostTimeMs = AtomicLong(0L)
    private val lastFollowerTimeMs = AtomicLong(0L)
    private val lastHostPositionObservedAtMs = AtomicLong(0L)
    private val estimatedOneWayDelayMs = AtomicLong(0L)
    private val remoteClockOffsetMs = AtomicLong(0L)
    private val lastProcessedHostEventTimestampMs = AtomicLong(0L)
    private val hostPlaybackState = AtomicReference(PlaybackState.IDLE)
    private val currentDrift = AtomicLong(0L)
    private val syncLocked = AtomicBoolean(false)

    // Counters and stats
    private var driftCorrections = 0
    private var syncErrors = 0
    private var lastEventTime = 0L

    // Callbacks
    var onPlaybackAdjustmentNeeded: ((targetTimeMs: Long, syncEventType: SyncEventType) -> Unit)? = null
    var onDriftDetected: ((driftMs: Long) -> Unit)? = null
    var onSyncStatusChanged: ((isSynced: Boolean) -> Unit)? = null
    var onSyncEventReceived: ((SyncEvent) -> Unit)? = null
    var onStateSnapshotRequested: ((SyncEvent) -> Unit)? = null
    var onSyncLocked: (() -> Unit)? = null
    var onSyncLost: (() -> Unit)? = null

    // Background drift monitoring
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var driftMonitorJob: Job? = null

    // Event history for debugging
    private val eventHistory = ConcurrentHashMap<String, SyncEvent>()

    /**
     * Initialize a new watch session
     */
    fun initializeSession(
        session: WatchSession,
        userId: String,
        isHost: Boolean
    ) {
        require(session.sessionId.isNotBlank()) { "Session ID must not be blank" }
        require(session.host in session.participants) { "Host must be a session participant" }
        require(userId in session.participants) { "User must be a session participant" }
        require((isHost && session.host == userId) || (!isHost && session.host != userId)) {
            "Session role does not match host identity"
        }
        currentSession = session
        myUserId = userId
        myRole = if (isHost) Role.HOST else Role.FOLLOWER
        lastHostTimeMs.set(session.currentTimeMs)
        lastFollowerTimeMs.set(session.currentTimeMs)
        lastHostPositionObservedAtMs.set(System.currentTimeMillis())
        hostPlaybackState.set(session.state)
        estimatedOneWayDelayMs.set(0L)
        remoteClockOffsetMs.set(0L)
        lastProcessedHostEventTimestampMs.set(0L)
        lastEventTime = 0L
        driftCorrections = 0
        syncErrors = 0
        eventHistory.clear()
        syncLocked.set(false)
        currentDrift.set(0L)

        Log.d(TAG, "Session initialized: role=$myRole, session=${session.sessionId}")
        startDriftMonitoring()
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * EVENT GENERATION (Host only)
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Create a PLAY event from host
     * Called when user presses play button
     */
    fun createPlayEvent(currentTimeMs: Long): SyncEvent {
        check(myRole == Role.HOST) { "Only host can create events" }
        updateHostPlaybackState(currentTimeMs, PlaybackState.PLAYING)
        val event = SyncEvent(
            sessionId = currentSession?.sessionId ?: "",
            senderId = myUserId,
            eventType = SyncEventType.PLAY,
            timelinePositionMs = currentTimeMs.coerceAtLeast(0L),
            state = PlaybackState.PLAYING,
            timestamp = System.currentTimeMillis()
        )
        recordEvent(event)
        return event
    }

    /**
     * Create a PAUSE event from host
     */
    fun createPauseEvent(currentTimeMs: Long): SyncEvent {
        check(myRole == Role.HOST) { "Only host can create events" }
        updateHostPlaybackState(currentTimeMs, PlaybackState.PAUSED)
        val event = SyncEvent(
            sessionId = currentSession?.sessionId ?: "",
            senderId = myUserId,
            eventType = SyncEventType.PAUSE,
            timelinePositionMs = currentTimeMs.coerceAtLeast(0L),
            state = PlaybackState.PAUSED,
            timestamp = System.currentTimeMillis()
        )
        recordEvent(event)
        return event
    }

    /**
     * Create a SEEK event from host
     * Send when user drags progress bar
     */
    fun createSeekEvent(targetTimeMs: Long): SyncEvent {
        check(myRole == Role.HOST) { "Only host can create events" }
        val state = hostPlaybackState.get()
        updateHostPlaybackState(targetTimeMs, state)
        val event = SyncEvent(
            sessionId = currentSession?.sessionId ?: "",
            senderId = myUserId,
            eventType = SyncEventType.SEEK,
            timelinePositionMs = targetTimeMs.coerceAtLeast(0L),
            state = state,
            timestamp = System.currentTimeMillis()
        )
        recordEvent(event)
        return event
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * EVENT PROCESSING (Host requests and follower playback events)
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /** Update timing from a request-correlated ping/pong exchange; this is not peer authentication. */
    fun updateNetworkTiming(sample: RoundTripTimingSample): NetworkTimingEstimate? {
        val estimate = WatchSyncMath.estimateTiming(sample) ?: return null
        estimatedOneWayDelayMs.set(estimate.estimatedOneWayDelayMs)
        remoteClockOffsetMs.set(estimate.remoteClockOffsetMs)
        return estimate
    }

    /**
     * Hosts can send this after a follower reconnects or requests a resync.
     * The snapshot contains no movie filename or local path.
     */
    fun createStateSnapshot(): SyncEvent {
        check(myRole == Role.HOST) { "Only host can create a state snapshot" }
        val session = checkNotNull(currentSession) { "No active watch session" }
        val now = System.currentTimeMillis()
        val state = hostPlaybackState.get()
        val event = SyncEvent(
            sessionId = session.sessionId,
            senderId = myUserId,
            eventType = SyncEventType.STATE_SNAPSHOT,
            timelinePositionMs = WatchSyncMath.projectPositionNow(
                lastHostTimeMs.get(),
                lastHostPositionObservedAtMs.get(),
                now,
                state
            ),
            state = state,
            timestamp = now
        )
        recordEvent(event)
        return event
    }

    /**
     * Process a transport-independent playback event. The transport adapter is
     * responsible for authenticating the event before calling this method.
     */
    fun processSyncEvent(event: SyncEvent) {
        val session = currentSession ?: run {
            syncErrors++
            return
        }
        val localNow = System.currentTimeMillis()
        if (event.sessionId != session.sessionId || event.senderId !in session.participants) {
            syncErrors++
            return
        }
        if (event.eventId in eventHistory || WatchSyncMath.isEventStale(
                remoteEventTimestampMs = event.timestamp,
                localNowMs = localNow,
                remoteMinusLocalClockOffsetMs = remoteClockOffsetMs.get()
            )
        ) {
            syncErrors++
            return
        }

        if (myRole == Role.HOST) {
            if (event.senderId == myUserId ||
                event.eventType !in setOf(SyncEventType.SYNC_REQUEST, SyncEventType.DRIFT_REPORT)
            ) {
                syncErrors++
                return
            }
            if (event.eventType == SyncEventType.SYNC_REQUEST) {
                onStateSnapshotRequested?.invoke(createStateSnapshot())
            }
            recordEvent(event)
            onSyncEventReceived?.invoke(event)
            return
        }

        if (myRole != Role.FOLLOWER || event.senderId != session.host) {
            syncErrors++
            return
        }

        if (event.eventType == SyncEventType.SYNC_REQUEST ||
            event.eventType == SyncEventType.DRIFT_REPORT ||
            event.timestamp < lastProcessedHostEventTimestampMs.get()
        ) {
            syncErrors++
            return
        }

        val targetPosition = WatchSyncMath.projectPositionAtArrival(
            positionMs = event.timelinePositionMs,
            state = event.state,
            estimatedOneWayDelayMs = estimatedOneWayDelayMs.get()
        )
        recordEvent(event)
        lastProcessedHostEventTimestampMs.set(event.timestamp)
        lastHostTimeMs.set(targetPosition)
        lastHostPositionObservedAtMs.set(localNow)
        hostPlaybackState.set(event.state)
        lastEventTime = localNow
        session.currentTimeMs = targetPosition
        session.state = event.state
        session.lastUpdatedAt = localNow

        val adjustment = when (event.eventType) {
            SyncEventType.PLAY -> SyncEventType.PLAY
            SyncEventType.PAUSE -> SyncEventType.PAUSE
            SyncEventType.SEEK -> SyncEventType.SEEK
            SyncEventType.BUFFERING -> SyncEventType.BUFFERING
            SyncEventType.BUFFERING_END -> SyncEventType.BUFFERING_END
            SyncEventType.STATE_SNAPSHOT -> when (event.state) {
                PlaybackState.PLAYING -> SyncEventType.PLAY
                PlaybackState.PAUSED -> SyncEventType.PAUSE
                PlaybackState.BUFFERING -> SyncEventType.BUFFERING
                PlaybackState.ENDED -> SyncEventType.SESSION_END
                PlaybackState.IDLE, PlaybackState.SEEKING -> SyncEventType.SEEK
            }
            SyncEventType.SESSION_END -> SyncEventType.SESSION_END
            else -> return
        }

        if (adjustment == SyncEventType.SESSION_END) {
            endSession()
        } else {
            onPlaybackAdjustmentNeeded?.invoke(targetPosition, adjustment)
        }
        onSyncEventReceived?.invoke(event)
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * DRIFT MONITORING & CORRECTION
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Report current playback position from follower
     * Used to calculate drift
     */
    fun reportFollowerPosition(currentTimeMs: Long) {
        lastFollowerTimeMs.set(currentTimeMs)
        updateDrift()
    }

    /**
     * Calculate current drift between host and follower
     * Drift = follower_time - host_time
     */
    private fun updateDrift() {
        val hostTime = WatchSyncMath.projectPositionNow(
            positionAtObservationMs = lastHostTimeMs.get(),
            observedAtLocalMs = lastHostPositionObservedAtMs.get(),
            nowLocalMs = System.currentTimeMillis(),
            state = hostPlaybackState.get()
        )
        val followerTime = lastFollowerTimeMs.get()
        val drift = followerTime - hostTime
        currentDrift.set(drift)
        onDriftDetected?.invoke(drift)
    }

    /**
     * Check if drift exceeds tolerance
     */
    fun isDriftExcessive(): Boolean {
        val drift = currentDrift.get()
        return kotlin.math.abs(drift) > MAX_DRIFT_MS
    }

    /**
     * Get recommended correction in milliseconds
     * Follower should seek to hostTime, not jump instantly
     */
    fun getCorrectionAmount(): Long {
        val drift = currentDrift.get()
        // Soft correction: move at rate of SOFT_CORRECTION_MS per check interval
        return when {
            drift > 0 -> -kotlin.math.min(drift, SOFT_CORRECTION_MS) // Behind, speed up
            drift < 0 -> kotlin.math.min(-drift, SOFT_CORRECTION_MS) // Ahead, slow down
            else -> 0L
        }
    }

    /**
     * Start background monitoring of drift
     */
    private fun startDriftMonitoring() {
        driftMonitorJob?.cancel()
        if (myRole == Role.FOLLOWER) {
            driftMonitorJob = scope.launch {
                while (isActive) {
                    delay(DRIFT_CHECK_INTERVAL)

                    if (isDriftExcessive()) {
                        Log.w(TAG, "Drift excessive: ${currentDrift.get()}ms")
                        if (syncLocked.get()) {
                            syncLocked.set(false)
                            onSyncLost?.invoke()
                        }
                        driftCorrections++
                    } else {
                        if (!syncLocked.get()) {
                            syncLocked.set(true)
                            onSyncLocked?.invoke()
                        }
                    }
                }
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * MANUAL RESYNC
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Manual resync button pressed by follower
     * Request realignment to host time
     */
    fun requestResync(currentFollowerTime: Long): SyncEvent {
        check(myRole == Role.FOLLOWER) { "Only follower can request resync" }

        val event = SyncEvent(
            sessionId = currentSession?.sessionId ?: "",
            senderId = myUserId,
            eventType = SyncEventType.SYNC_REQUEST,
            timelinePositionMs = currentFollowerTime,
            state = currentSession?.state ?: PlaybackState.IDLE,
            driftMs = currentDrift.get()
        )

        Log.d(TAG, "Resync requested, drift=${currentDrift.get()}ms")
        recordEvent(event)
        return event
    }

    /**
     * Follower applies immediate correction to match host time
     * This is instant alignment, used after resync
     */
    fun applyInstantCorrection(hostTimeMs: Long) {
        Log.d(TAG, "Applying instant correction to $hostTimeMs")
        lastHostTimeMs.set(hostTimeMs)
        lastFollowerTimeMs.set(hostTimeMs)
        currentDrift.set(0L)

        onPlaybackAdjustmentNeeded?.invoke(hostTimeMs, SyncEventType.SEEK)
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * SESSION MANAGEMENT
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * End the sync session
     */
    fun endSession() {
        Log.d(TAG, "Sync session ended")
        driftMonitorJob?.cancel()
        currentSession = null
        myRole = Role.NONE
        syncLocked.set(false)
    }

    /**
     * Get current sync status
     */
    fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            isSynced = !isDriftExcessive(),
            currentDriftMs = currentDrift.get(),
            role = myRole,
            driftCorrectionsApplied = driftCorrections,
            syncErrors = syncErrors,
            lastEventReceivedAt = lastEventTime
        )
    }

    /**
     * Get session info
     */
    fun getCurrentSession(): WatchSession? = currentSession

    /**
     * Get event history (for debugging)
     */
    fun getEventHistory(): List<SyncEvent> = eventHistory.values.toList()

    // ── PRIVATE HELPERS ──────────────────────────────────────────────────────

    private fun recordEvent(event: SyncEvent) {
        eventHistory[event.eventId] = event
        // Keep only last 100 events
        if (eventHistory.size > 100) {
            val oldestKey = eventHistory.keys.minByOrNull { eventHistory[it]?.timestamp ?: 0L }
            oldestKey?.let { eventHistory.remove(it) }
        }
    }

    private fun updateHostPlaybackState(positionMs: Long, state: PlaybackState) {
        val session = checkNotNull(currentSession) { "No active watch session" }
        val now = System.currentTimeMillis()
        val safePosition = positionMs.coerceAtLeast(0L)
        lastHostTimeMs.set(safePosition)
        lastHostPositionObservedAtMs.set(now)
        hostPlaybackState.set(state)
        session.currentTimeMs = safePosition
        session.state = state
        session.lastUpdatedAt = now
    }

    private fun shouldCorrectDrift(): Boolean {
        return isDriftExcessive() && !syncLocked.get()
    }
}

/**
 * Snapshot of current sync status
 */
data class SyncStatus(
    val isSynced: Boolean,
    val currentDriftMs: Long,
    val role: PlaybackSynchronizationEngine.Role,
    val driftCorrectionsApplied: Int,
    val syncErrors: Int,
    val lastEventReceivedAt: Long
) {
    val driftPercentage: Double get() = if (currentDriftMs == 0L) {
        0.0
    } else {
        (kotlin.math.abs(currentDriftMs) / PlaybackSynchronizationEngine.MAX_DRIFT_MS.toDouble()) * 100
    }
}
