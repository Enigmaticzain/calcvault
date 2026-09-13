package com.calcvault.sync

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * PlaybackSynchronizationEngine - Keeps both devices in sync
 * 
 * Architecture:
 * - HOST: Controls playback, broadcasts events
 * - FOLLOWER: Receives events, adjusts playback to match host
 * 
 * Sync Strategy:
 * 1. Host sends every play/pause/seek event
 * 2. Follower listens and adjusts
 * 3. Continuous monitoring detects drift
 * 4. Auto-correction if drift > threshold (100-200ms)
 * 5. Manual resync button available anytime
 * 
 * Events flow: User Action → Host → Network → Follower
 */
class PlaybackSynchronizationEngine {
    
    companion object {
        private const val TAG = "PlaybackSync"
        const val MAX_DRIFT_MS = 200L         // 200ms tolerance
        const val DRIFT_CHECK_INTERVAL = 500L // Check every 500ms
        const val SOFT_CORRECTION_MS = 100L   // Gentle catch-up speed
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
        currentSession = session
        myUserId = userId
        myRole = if (isHost) Role.HOST else Role.FOLLOWER
        syncLocked.set(false)
        currentDrift.set(0L)

        Log.d(TAG, "Session initialized: role=${myRole}, session=${session.sessionId}")
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
        val event = SyncEvent(
            sessionId = currentSession?.sessionId ?: "",
            senderId = myUserId,
            eventType = SyncEventType.PLAY,
            timelinePositionMs = currentTimeMs,
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
        val event = SyncEvent(
            sessionId = currentSession?.sessionId ?: "",
            senderId = myUserId,
            eventType = SyncEventType.PAUSE,
            timelinePositionMs = currentTimeMs,
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
        val event = SyncEvent(
            sessionId = currentSession?.sessionId ?: "",
            senderId = myUserId,
            eventType = SyncEventType.SEEK,
            timelinePositionMs = targetTimeMs,
            state = currentSession?.state ?: PlaybackState.PAUSED,
            timestamp = System.currentTimeMillis()
        )
        recordEvent(event)
        return event
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * EVENT RECEPTION (Follower processes)
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Process an incoming sync event from the host
     * This is the critical decision point for follower behavior
     */
    fun processSyncEvent(event: SyncEvent) {
        check(myRole == Role.FOLLOWER) { "Only follower processes events" }

        if (event.isStale()) {
            Log.w(TAG, "Stale event received: ${event.eventType}")
            syncErrors++
            return
        }

        recordEvent(event)
        lastHostTimeMs.set(event.timelinePositionMs)
        lastEventTime = System.currentTimeMillis()

        when (event.eventType) {
            SyncEventType.PLAY -> {
                Log.d(TAG, "PLAY event received at ${event.timelinePositionMs}ms")
                onPlaybackAdjustmentNeeded?.invoke(
                    event.timelinePositionMs,
                    SyncEventType.PLAY
                )
            }
            
            SyncEventType.PAUSE -> {
                Log.d(TAG, "PAUSE event received")
                onPlaybackAdjustmentNeeded?.invoke(
                    event.timelinePositionMs,
                    SyncEventType.PAUSE
                )
            }
            
            SyncEventType.SEEK -> {
                Log.d(TAG, "SEEK event to ${event.timelinePositionMs}ms")
                onPlaybackAdjustmentNeeded?.invoke(
                    event.timelinePositionMs,
                    SyncEventType.SEEK
                )
            }
            
            SyncEventType.BUFFERING -> {
                Log.d(TAG, "BUFFERING event received")
                onPlaybackAdjustmentNeeded?.invoke(
                    event.timelinePositionMs,
                    SyncEventType.BUFFERING
                )
            }

            SyncEventType.BUFFERING_END -> {
                Log.d(TAG, "BUFFERING_END event")
                onPlaybackAdjustmentNeeded?.invoke(
                    event.timelinePositionMs,
                    SyncEventType.BUFFERING_END
                )
            }

            SyncEventType.DRIFT_REPORT -> {
                Log.d(TAG, "Drift report: ${event.driftMs}ms")
            }

            SyncEventType.SYNC_REQUEST -> {
                Log.d(TAG, "Resync requested")
            }

            SyncEventType.SESSION_END -> {
                Log.d(TAG, "Session ended by host")
                endSession()
            }
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
        val hostTime = lastHostTimeMs.get()
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
            drift > 0 -> -kotlin.math.min(drift, SOFT_CORRECTION_MS)  // Behind, speed up
            drift < 0 -> kotlin.math.min(-drift, SOFT_CORRECTION_MS)  // Ahead, slow down
            else -> 0L
        }
    }

    /**
     * Start background monitoring of drift
     */
    private fun startDriftMonitoring() {
        if (myRole == Role.FOLLOWER) {
            driftMonitorJob?.cancel()
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
            val oldestKey = eventHistory.keys.minByOrNull { 
                eventHistory[it]?.timestamp ?: 0L 
            }
            oldestKey?.let { eventHistory.remove(it) }
        }
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
    val driftPercentage: Double 
        get() = if (currentDriftMs == 0L) 0.0 
                else (kotlin.math.abs(currentDriftMs) / PlaybackSynchronizationEngine.MAX_DRIFT_MS.toDouble()) * 100
}
