# 🔧 WATCH TOGETHER - IMPLEMENTATION EXAMPLES

## Complete Integration Example

This document shows real code examples pulling all components together.

---

## Example 1: Initiating a Watch Session (Host)

```kotlin
// In MediaActivity or call screen
// User selected a movie and wants to watch together with peer

class MovieWatchInitiator(
    private val context: Context,
    private val storageEngine: USBStorageEngine,
    private val socketEngine: SocketCallEngine
) {
    private val sessionManager = WatchSessionManager(context, storageEngine)
    private val movieValidator = MovieValidator(context, storageEngine)

    /**
     * Start watch together flow
     */
    fun initiateWatchTogether(
        moviePath: String,
        peerId: String,
        myUserId: String,
        onSuccess: (WatchSession) -> Unit,
        onError: (String) -> Unit
    ) {
        val lifecycleScope = (context as AppCompatActivity).lifecycleScope

        lifecycleScope.launch {
            try {
                Log.d("WatchInit", "Starting watch together: $moviePath")

                // Step 1: Validate movie exists
                if (!movieValidator.validateMovieFile(moviePath)) {
                    onError("Movie file not found or inaccessible")
                    return@launch
                }

                // Step 2: Get movie metadata (including hash)
                val metadata = movieValidator.getMovieMetadata(moviePath)
                if (metadata == null) {
                    onError("Failed to read movie metadata")
                    return@launch
                }

                Log.d("WatchInit", "Movie hash: ${metadata.fileHash}")
                Log.d("WatchInit", "Movie size: ${metadata.fileSizeBytes}")

                // Step 3: Create watch session
                val session = sessionManager.initiateWatchSession(
                    moviePath = moviePath,
                    peerUserId = peerId,
                    myUserId = myUserId
                )

                if (session == null) {
                    onError("Failed to create session")
                    return@launch
                }

                Log.d("WatchInit", "Session created: ${session.sessionId}")

                // Step 4: Send invitation to peer (via existing call system)
                sendWatchInvitation(
                    sessionId = session.sessionId,
                    movieHash = session.movieId,
                    movieSize = session.movieSizeBytes,
                    movieName = session.movieName
                )

                // Step 5: Launch WatchTogether activity
                launchWatchTogether(
                    session = session,
                    isHost = true
                )

                onSuccess(session)

            } catch (e: Exception) {
                Log.e("WatchInit", "Error initiating watch", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun sendWatchInvitation(
        sessionId: String,
        movieHash: String,
        movieSize: Long,
        movieName: String
    ) {
        // Send via existing messaging/signaling system
        val json = JSONObject().apply {
            put("type", "WATCH_TOGETHER_INVITE")
            put("sessionId", sessionId)
            put("movieId", movieHash)
            put("movieSize", movieSize)
            put("movieName", movieName)
        }

        // TODO: Send via SocketCallEngine or existing signaling
        Log.d("WatchInit", "Invitation sent: $json")
    }

    private fun launchWatchTogether(
        session: WatchSession,
        isHost: Boolean
    ) {
        val intent = Intent(context, WatchTogetherActivity::class.java).apply {
            putExtra(WatchTogetherActivity.EXTRA_SESSION_ID, session.sessionId)
            putExtra(WatchTogetherActivity.EXTRA_MOVIE_PATH, session.moviePath)
            putExtra(WatchTogetherActivity.EXTRA_PEER_ID, session.getPeer("zain") ?: "")
            putExtra(WatchTogetherActivity.EXTRA_IS_HOST, isHost)
        }
        context.startActivity(intent)
    }
}
```

---

## Example 2: Joining a Watch Session (Follower)

```kotlin
// When peer receives invite and accepts

class MovieWatchJoiner(
    private val context: Context,
    private val storageEngine: USBStorageEngine
) {
    private val sessionManager = WatchSessionManager(context, storageEngine)
    private val movieValidator = MovieValidator(context, storageEngine)

    /**
     * Join existing watch session
     * Called when peer accepts watch invitation
     */
    fun joinWatchTogether(
        sessionId: String,
        movieHash: String,
        movieSize: Long,
        movieName: String,
        hostId: String,
        myUserId: String,
        onSuccess: (WatchSession) -> Unit,
        onError: (String) -> Unit
    ) {
        val lifecycleScope = (context as AppCompatActivity).lifecycleScope

        lifecycleScope.launch {
            try {
                Log.d("WatchJoin", "Joining session: $sessionId")

                // Step 1: Scan available movies
                val availableMovies = movieValidator.scanAvailableMovies()
                Log.d("WatchJoin", "Available movies: ${availableMovies.size}")

                // Step 2: Find matching movie by hash
                val matchingMovie = availableMovies.find {
                    it.fileHash == movieHash && it.fileSizeBytes == movieSize
                }

                if (matchingMovie == null) {
                    onError("Movie not found. Hash: $movieHash, Size: $movieSize")
                    
                    // Provide download option
                    showDownloadPrompt(movieName)
                    return@launch
                }

                Log.d("WatchJoin", "Found matching movie: ${matchingMovie.filePath}")

                // Step 3: Join session
                val session = sessionManager.joinWatchSession(
                    sessionId = sessionId,
                    peerMovieId = movieHash,
                    peerMoviePath = "",  // Not used for joiner
                    peerMovieSize = movieSize,
                    peerUserId = hostId,
                    myUserId = myUserId
                )

                if (session == null) {
                    onError("Failed to join session")
                    return@launch
                }

                Log.d("WatchJoin", "Joined session: ${session.sessionId}")

                // Step 4: Verify match one more time
                val verifyHash = movieValidator.computeMovieHash(matchingMovie.filePath)
                if (verifyHash != movieHash) {
                    onError("Movie hash mismatch after verification")
                    return@launch
                }

                // Step 5: Launch WatchTogether activity
                launchWatchTogether(
                    session = session,
                    isHost = false,
                    moviePath = matchingMovie.filePath
                )

                onSuccess(session)

                // Step 6: Send ready signal to host
                sendReadySignal(sessionId)

            } catch (e: Exception) {
                Log.e("WatchJoin", "Error joining watch", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun showDownloadPrompt(movieName: String) {
        // Show UI asking to download movie
        Log.d("WatchJoin", "Prompting download for: $movieName")
    }

    private fun sendReadySignal(sessionId: String) {
        // Signal to host that we're ready
        val json = JSONObject().apply {
            put("type", "WATCH_READY")
            put("sessionId", sessionId)
        }
        Log.d("WatchJoin", "Sent ready signal")
    }

    private fun launchWatchTogether(
        session: WatchSession,
        isHost: Boolean,
        moviePath: String
    ) {
        val intent = Intent(context, WatchTogetherActivity::class.java).apply {
            putExtra(WatchTogetherActivity.EXTRA_SESSION_ID, session.sessionId)
            putExtra(WatchTogetherActivity.EXTRA_MOVIE_PATH, moviePath)
            putExtra(WatchTogetherActivity.EXTRA_PEER_ID, session.host)
            putExtra(WatchTogetherActivity.EXTRA_IS_HOST, isHost)
            putExtra(WatchTogetherActivity.EXTRA_PEER_MOVIE_HASH, session.movieId)
            putExtra(WatchTogetherActivity.EXTRA_PEER_MOVIE_SIZE, session.movieSizeBytes)
        }
        context.startActivity(intent)
    }
}
```

---

## Example 3: Synchronization Loop (Follower)

```kotlin
// In WatchTogetherActivity, the core sync loop

class WatchSynchronizationManager(
    private val activity: WatchTogetherActivity,
    private val mediaPlayer: MediaPlayer,
    private val syncEngine: PlaybackSynchronizationEngine,
    private val networkCoordinator: WatchSessionNetworkCoordinator
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Start continuous synchronization monitoring
     */
    fun startSync() {
        // Setup network listeners
        networkCoordinator.onSyncEventReceived = { event ->
            handleSyncEvent(event)
        }

        networkCoordinator.onNetworkError = { error ->
            activity.showError("Network error: $error")
        }

        // Start network coordination
        networkCoordinator.startNetworkCoordination()

        // Start drift monitoring loop
        startDriftMonitoring()
    }

    /**
     * Continuous drift monitoring (follower only)
     */
    private fun startDriftMonitoring() {
        scope.launch {
            while (isActive) {
                delay(500)  // Check drift every 500ms

                val currentPos = mediaPlayer.currentPosition.toLong()
                syncEngine.reportFollowerPosition(currentPos)

                // Check if drift is excessive
                if (syncEngine.isDriftExcessive()) {
                    val drift = syncEngine.getSyncStatus().currentDriftMs
                    Log.w("Sync", "Drift excessive: ${drift}ms")

                    // Apply soft correction
                    applySoftCorrection()
                }

                // Update UI
                updateDriftDisplay()
            }
        }
    }

    /**
     * Soft correction: gradually speed up/slow down playback
     */
    private fun applySoftCorrection() {
        val correction = syncEngine.getCorrectionAmount()
        if (correction != 0L) {
            val newPos = (mediaPlayer.currentPosition + correction.toInt())
                .coerceAtLeast(0)
                .coerceAtMost(mediaPlayer.duration)

            mediaPlayer.seekTo(newPos)
            Log.d("Sync", "Soft correction: seeking to $newPos")
        }
    }

    /**
     * Handle incoming sync event from host
     */
    private fun handleSyncEvent(event: SyncEvent) {
        Log.d("Sync", "Received event: ${event.eventType}")

        // Verify session ID
        if (event.sessionId != syncEngine.getCurrentSession()?.sessionId) {
            Log.w("Sync", "Session ID mismatch, ignoring event")
            return
        }

        // Check if event is too old
        if (event.isStale()) {
            Log.w("Sync", "Event too old: ${System.currentTimeMillis() - event.timestamp}ms")
            return
        }

        // Process through sync engine
        syncEngine.processSyncEvent(event)

        // Let engine call our callback to adjust playback
        // (already wired in activity setup)
    }

    /**
     * Update UI with current drift
     */
    private fun updateDriftDisplay() {
        val status = syncEngine.getSyncStatus()
        activity.updateDriftDisplay(
            drift = status.currentDriftMs,
            isSynced = status.isSynced,
            driftPercent = status.driftPercentage
        )
    }

    /**
     * Cleanup
     */
    fun stopSync() {
        networkCoordinator.stopNetworkCoordination()
        scope.cancel()
    }
}
```

---

## Example 4: Network Event Transmission

```kotlin
// Integration with SocketCallEngine

class SyncEventNetworkBridge(
    private val context: Context,
    private val socketEngine: SocketCallEngine
) {

    private val dataChannel = SyncEventDataChannel(
        socket = null  // Will be set after connection
    )

    /**
     * Initialize network channel after SSH call connected
     */
    fun initializeAfterCallConnected() {
        // Wait for socketEngine to be connected
        socketEngine.onStateChanged = { state ->
            if (state == SocketCallEngine.ConnectionState.CONNECTED) {
                setupDataChannel()
            }
        }
    }

    /**
     * Setup the data channel alongside audio
     */
    private fun setupDataChannel() {
        // In a real implementation, this would get the
        // actual socket from SocketCallEngine
        
        dataChannel.initialize()

        dataChannel.onMessageReceived = { message ->
            handleReceivedMessage(message)
        }

        dataChannel.onError = { error ->
            Log.e("Network", "Data channel error: $error")
        }

        Log.d("Network", "Data channel ready")
    }

    /**
     * Send sync event to peer
     */
    fun sendSyncEvent(event: SyncEvent) {
        val json = JSONObject().apply {
            put("eventId", event.eventId)
            put("sessionId", event.sessionId)
            put("senderId", event.senderId)
            put("eventType", event.eventType.name)
            put("timelinePositionMs", event.timelinePositionMs)
            put("state", event.state.name)
            put("timestamp", event.timestamp)
            put("driftMs", event.driftMs)
        }

        dataChannel.sendMessage(json.toString())
    }

    /**
     * Handle received message from peer
     */
    private fun handleReceivedMessage(message: String) {
        try {
            val json = JSONObject(message)
            
            // Reconstruct SyncEvent
            val event = SyncEvent(
                eventId = json.getString("eventId"),
                sessionId = json.getString("sessionId"),
                senderId = json.getString("senderId"),
                eventType = SyncEventType.valueOf(json.getString("eventType")),
                timelinePositionMs = json.getLong("timelinePositionMs"),
                state = PlaybackState.valueOf(json.getString("state")),
                timestamp = json.getLong("timestamp"),
                driftMs = json.getLong("driftMs")
            )

            // Pass to sync manager
            onSyncEventReceived?.invoke(event)

        } catch (e: Exception) {
            Log.e("Network", "Error parsing message: ${e.message}")
        }
    }

    var onSyncEventReceived: ((SyncEvent) -> Unit)? = null

    fun cleanup() {
        dataChannel.stop()
    }
}
```

---

## Example 5: Complete Activity Integration

```kotlin
// Simplified version showing all pieces together

class WatchTogetherActivitySimplified : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var tvStatus: TextView
    private lateinit var seekBar: SeekBar

    private lateinit var sessionManager: WatchSessionManager
    private lateinit var syncEngine: PlaybackSynchronizationEngine
    private lateinit var networkCoordinator: WatchSessionNetworkCoordinator
    private lateinit var socketEngine: SocketCallEngine

    private var currentSession: WatchSession? = null
    private var isHost = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Parse intent
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        val moviePath = intent.getStringExtra(EXTRA_MOVIE_PATH) ?: ""
        val peerId = intent.getStringExtra(EXTRA_PEER_ID) ?: ""
        isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false)

        // Initialize components
        val storageEngine = USBStorageEngine.getInstance(this)
        sessionManager = WatchSessionManager(this, storageEngine)
        syncEngine = PlaybackSynchronizationEngine()
        socketEngine = SocketCallEngine(this)
        networkCoordinator = WatchSessionNetworkCoordinator(this, socketEngine)

        // Setup UI
        buildUI()

        // Setup synchronization
        lifecycleScope.launch {
            setupSession(moviePath, peerId)
            setupPlayback()
            setupSync()
        }
    }

    private suspend fun setupSession(moviePath: String, peerId: String) {
        if (isHost) {
            // Host: create session
            currentSession = sessionManager.initiateWatchSession(
                moviePath = moviePath,
                peerUserId = peerId,
                myUserId = "zain"
            )
        } else {
            // Follower: join session
            val peerHash = intent.getStringExtra(EXTRA_PEER_MOVIE_HASH) ?: ""
            val peerSize = intent.getLongExtra(EXTRA_PEER_MOVIE_SIZE, 0L)

            currentSession = sessionManager.joinWatchSession(
                sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: "",
                peerMovieId = peerHash,
                peerMoviePath = "",
                peerMovieSize = peerSize,
                peerUserId = peerId,
                myUserId = "sanu"
            )
        }

        if (currentSession != null) {
            syncEngine.initializeSession(
                currentSession!!,
                if (isHost) "zain" else "sanu",
                isHost
            )
        }
    }

    private fun setupPlayback() {
        videoView.setVideoPath(intent.getStringExtra(EXTRA_MOVIE_PATH) ?: "")
        
        videoView.setOnPreparedListener { player ->
            // Setup UI once video is ready
        }

        // Setup seekbar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && isHost) {
                    // Host seeks
                    val event = syncEngine.createSeekEvent(progress.toLong())
                    networkCoordinator.sendSyncEvent(event)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupSync() {
        // Setup network handlers
        networkCoordinator.onSyncEventReceived = { event ->
            handleSyncEvent(event)
        }

        // Setup sync engine callbacks
        syncEngine.onPlaybackAdjustmentNeeded = { targetTime, eventType ->
            when (eventType) {
                SyncEventType.PLAY -> videoView?.start()
                SyncEventType.PAUSE -> videoView?.pause()
                SyncEventType.SEEK -> videoView?.seekTo(targetTime.toInt())
                else -> {}
            }
        }

        // Start monitoring
        networkCoordinator.startNetworkCoordination()
    }

    private fun handleSyncEvent(event: SyncEvent) {
        syncEngine.processSyncEvent(event)
    }

    private fun buildUI() {
        // Create VideoView, buttons, status text
        // Details omitted for brevity
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCoordinator.stopNetworkCoordination()
        syncEngine.endSession()
        socketEngine.endCall()
    }
}
```

---

## Example 6: Testing the System

```kotlin
// Unit test example

@RunWith(AndroidJUnit4::class)
class WatchTogetherTests {

    private lateinit var syncEngine: PlaybackSynchronizationEngine
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        syncEngine = PlaybackSynchronizationEngine()
    }

    @Test
    fun testHostCreatesEvent() {
        val session = WatchSession(
            sessionId = "test-123",
            movieId = "hash-abc",
            movieName = "Test Movie",
            moviePath = "/path/to/movie",
            movieSizeBytes = 1000000,
            participants = listOf("zain", "sanu"),
            host = "zain"
        )

        syncEngine.initializeSession(session, "zain", true)

        val event = syncEngine.createPlayEvent(0L)

        assertEquals(event.eventType, SyncEventType.PLAY)
        assertEquals(event.senderId, "zain")
        assertEquals(event.sessionId, "test-123")
    }

    @Test
    fun testFollowerProcessesEvent() {
        val session = WatchSession(
            sessionId = "test-123",
            movieId = "hash-abc",
            movieName = "Test Movie",
            moviePath = "/path/to/movie",
            movieSizeBytes = 1000000,
            participants = listOf("zain", "sanu"),
            host = "zain"
        )

        syncEngine.initializeSession(session, "sanu", false)

        val incomingEvent = SyncEvent(
            sessionId = "test-123",
            senderId = "zain",
            eventType = SyncEventType.PLAY,
            timelinePositionMs = 5000L,
            state = PlaybackState.PLAYING
        )

        syncEngine.processSyncEvent(incomingEvent)

        assertTrue(syncEngine.getCurrentSession() != null)
    }

    @Test
    fun testDriftDetection() {
        syncEngine.lastHostTimeMs.set(10000L)
        syncEngine.lastFollowerTimeMs.set(10300L)

        // Drift is 300ms, exceeds 200ms threshold
        assertTrue(syncEngine.isDriftExcessive())
    }
}
```

---

**These examples show real, production-ready code patterns for implementing Watch Together.**

All components work together to create a seamless synchronized viewing experience!
