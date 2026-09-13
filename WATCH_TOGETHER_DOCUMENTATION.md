# 🎬 Watch Together System - Calcvault
## Synchronized Movie Playback + Real-Time Communication

**Version**: 1.0  
**Created**: April 16, 2026  
**Status**: Complete Implementation  

---

## 📋 TABLE OF CONTENTS

1. [Architecture Overview](#architecture-overview)
2. [Core Components](#core-components)
3. [Integration Guide](#integration-guide)
4. [Usage Examples](#usage-examples)
5. [Synchronization Algorithm](#synchronization-algorithm)
6. [Network Protocol](#network-protocol)
7. [Error Handling](#error-handling)
8. [Testing](#testing)
9. [Performance Tuning](#performance-tuning)

---

## 🏗️ ARCHITECTURE OVERVIEW

```
┌─────────────────────────────────────────────────────────┐
│              WATCH TOGETHER SYSTEM                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │   WatchTogetherActivity (Main UI)                 │ │
│  │   - Full-screen video player                      │ │
│  │   - Playback controls (play, pause, seek)         │ │
│  │   - Sync status indicators                        │ │
│  │   - Voice/Video toggle                            │ │
│  │   - PiP overlay management                        │ │
│  └───────────────────────────────────────────────────┘ │
│                       │                                 │
│       ┌───────────────┼───────────────┐                │
│       │               │               │                │
│       ▼               ▼               ▼                │
│  ┌─────────┐ ┌──────────────┐ ┌─────────────┐        │
│  │ Session │ │ Sync Engine  │ │  Network    │        │
│  │ Manager │ │              │ │ Coordinator │        │
│  └─────────┘ └──────────────┘ └─────────────┘        │
│       │               │               │                │
│       └───────────────┼───────────────┘                │
│                       ▼                                │
│       ┌──────────────────────────────┐                │
│       │   SocketCallEngine           │                │
│       │   (Audio + Sync Data)        │                │
│       │   ┌──────────────────────┐   │                │
│       │   │ Audio Channel (PCM)  │   │                │
│       │   └──────────────────────┘   │                │
│       │   ┌──────────────────────┐   │                │
│       │   │ Data Channel (JSON)  │   │                │
│       │   └──────────────────────┘   │                │
│       └──────────────────────────────┘                │
│       ┌──────────────────────────────┐                │
│       │   WebRTC (optional for vid)  │                │
│       └──────────────────────────────┘                │
│                                                      │
│  ┌─────────────────────────────────────────────────┐ │
│  │   Local MediaPlayer                             │ │
│  │   - Video file (local storage only)             │ │
│  │   - Audio playback                              │ │
│  │   - Seek positioning                            │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
└─────────────────────────────────────────────────────┘

PEER DEVICE (SAME ARCHITECTURE)
```

---

## 🔧 CORE COMPONENTS

### 1. **WatchSession.kt** (Data Models)
```kotlin
// Main session container
data class WatchSession(
    val sessionId: String,              // Unique session ID
    val movieId: String,                // SHA-256 hash of movie file
    val moviePath: String,              // Local file path
    val movieSizeBytes: Long,           // For validation
    val participants: List<String>,     // [Zain, Sanu]
    val host: String,                   // Controller
    var currentTimeMs: Long,            // Current playback position
    var state: PlaybackState,           // PLAYING|PAUSED|SEEKING|etc
    val createdAt: Long,
    var lastUpdatedAt: Long
)

// Sync events structure
data class SyncEvent(
    val eventId: String,
    val sessionId: String,
    val senderId: String,               // Host ID
    val eventType: SyncEventType,       // PLAY|PAUSE|SEEK|DRIFT_REPORT|etc
    val timelinePositionMs: Long,       // Playback position
    val state: PlaybackState,
    val timestamp: Long,
    val driftMs: Long                   // Current drift (for DRIFT_REPORT)
)

// Event types
enum class SyncEventType {
    PLAY,               // ▶️ Host pressed play
    PAUSE,              // ⏸️ Host paused
    SEEK,               // ⏭️ Host seeked
    BUFFERING,          // ⏳ Buffering started
    BUFFERING_END,      // ✓ Buffering done
    DRIFT_REPORT,       // 📊 Sync drift report
    SYNC_REQUEST,       // 🔄 Request resync
    SESSION_END         // 🛑 End session
}
```

### 2. **MovieValidator.kt** (Validation & Hashing)
```kotlin
// Key methods:
suspend fun computeMovieHash(filePath: String): String?
    → Computes SHA-256 hash of movie file
    → Used to verify both devices have the same movie
    
suspend fun validateMovieFile(filePath: String): Boolean
    → Checks if file exists, is readable, and is valid

suspend fun getMovieMetadata(filePath: String): MovieMetadata?
    → Extracts duration, file size, MIME type

suspend fun areMoviesIdentical(...): Boolean
    → Compares local movie with peer's movie
    → Returns true only if hash + size match
```

### 3. **PlaybackSynchronizationEngine.kt** (Core Sync Logic)
```kotlin
// Roles
enum class Role { HOST, FOLLOWER, NONE }

// Methods:
fun initializeSession(session, userId, isHost)
    → Setup session for this device

// HOST METHODS:
fun createPlayEvent(currentTimeMs): SyncEvent
fun createPauseEvent(currentTimeMs): SyncEvent  
fun createSeekEvent(targetTimeMs): SyncEvent

// FOLLOWER METHODS:
fun processSyncEvent(event: SyncEvent)
    → Handle incoming event from host
    
fun reportFollowerPosition(currentTimeMs)
    → Report current playback position
    → Used to calculate drift

fun requestResync(currentFollowerTime): SyncEvent
    → Follower requests realignment

fun applyInstantCorrection(hostTimeMs)
    → Instant jump to host time

// MONITORING:
fun isDriftExcessive(): Boolean
    → True if |drift| > 200ms

fun getSyncStatus(): SyncStatus
    → Get detailed sync statistics
```

### 4. **WatchSessionManager.kt** (Session Lifecycle)
```kotlin
// HOST:
suspend fun initiateWatchSession(
    moviePath: String,
    peerUserId: String,
    myUserId: String
): WatchSession?
    → Create new session
    → Validate movie exists
    → Compute hash for peer verification

// FOLLOWER:
suspend fun joinWatchSession(
    sessionId: String,
    peerMovieId: String,
    peerMoviePath: String,
    peerMovieSize: Long,
    peerUserId: String,
    myUserId: String
): WatchSession?
    → Find matching movie locally
    → Verify hash matches peer's
    → Join session if validation OK

// COMMON:
fun updateSessionState(newState: PlaybackState, currentTimeMs)
fun getCurrentSession(): WatchSession?
fun endSession()
fun getSessionHistory(): List<WatchSession>
```

### 5. **WatchTogetherActivity.kt** (Main UI)
```kotlin
// Features:
- Full-screen VideoView
- Play/pause/seek controls
- Real-time status display
- Sync indicators (drift, connection, sync status)
- Voice/Video toggle buttons
- Resync button (follower only)
- End call button

// Layout structure:
VideoView (fullscreen)
  └─ PiP overlay (draggable, optional)
  └─ Control panel (bottom overlay)
      ├─ Status bar
      ├─ Progress/time bar
      └─ Control buttons
```

### 6. **WatchSessionNetworkCoordinator.kt** (Network Handler)
```kotlin
// Methods:
fun startNetworkCoordination()
    → Initialize socket connection
    → Start receive loop

fun sendSyncEvent(event: SyncEvent)
    → Serialize to JSON
    → Send over network

fun stopNetworkCoordination()
    → Cleanup connections

// Callbacks:
onSyncEventReceived: (SyncEvent) → Unit
onNetworkError: (String) → Unit
onConnectionHealthChanged: (Boolean) → Unit
```

### 7. **PictureInPictureOverlay.kt** (Video Overlay)
```kotlin
// Features:
- Draggable overlay with touch handling
- Resizable within constraints
- Border and styling
- Snap-to-corner presets
- Smooth animations
- Real-time position/size callbacks

// Methods:
fun updateConfig(newConfig: PipConfig)
fun moveTo(x: Int, y: Int)
fun resize(newWidth: Int, newHeight: Int)
fun snapToCorner(corner: Corner)
fun centerOnScreen()
```

### 8. **SyncEventDataChannel.kt** (Network Transport)
```kotlin
// Parallel data channel for sync events
// Runs alongside audio channel
// Length-prefixed message format: [4 bytes length][N bytes JSON]

// Methods:
fun initialize()
    → Setup on established socket

fun sendMessage(message: String)
    → Send JSON sync event

fun startReceiveLoop()
    → Listen for incoming events

fun stop()
    → Cleanup channel
```

---

## 🔌 INTEGRATION GUIDE

### Step 1: Add to AndroidManifest.xml
```xml
<!-- Watch Together Activity -->
<activity
    android:name="com.calcvault.ui.watchtogether.WatchTogetherActivity"
    android:screenOrientation="landscape"
    android:exported="false" />

<!-- Permissions needed -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### Step 2: Initiate from MediaActivity
```kotlin
// In MediaActivity or media selection screen
val selectedMovie = "/calcvault/storage/media/movies/movie.mp4"
val peerId = "sanu"  // Who to watch with

val intent = Intent(this, WatchTogetherActivity::class.java).apply {
    putExtra(WatchTogetherActivity.EXTRA_SESSION_ID, UUID.randomUUID().toString())
    putExtra(WatchTogetherActivity.EXTRA_MOVIE_PATH, selectedMovie)
    putExtra(WatchTogetherActivity.EXTRA_PEER_ID, peerId)
    putExtra(WatchTogetherActivity.EXTRA_IS_HOST, true)  // This device is host
}
startActivity(intent)
```

### Step 3: Peer Joins Session
```kotlin
// Peer receives session info (via existing call setup)
// Then joins:
val intent = Intent(this, WatchTogetherActivity::class.java).apply {
    putExtra(WatchTogetherActivity.EXTRA_SESSION_ID, receivedSessionId)
    putExtra(WatchTogetherActivity.EXTRA_MOVIE_PATH, myLocalMoviePath)
    putExtra(WatchTogetherActivity.EXTRA_PEER_ID, "zain")
    putExtra(WatchTogetherActivity.EXTRA_IS_HOST, false)  // Follower role
    putExtra(WatchTogetherActivity.EXTRA_PEER_MOVIE_HASH, hostMovieHash)
    putExtra(WatchTogetherActivity.EXTRA_PEER_MOVIE_SIZE, hostMovieSize)
}
startActivity(intent)
```

### Step 4: Integrate Network Communication

In WatchTogetherActivity:
```kotlin
private fun setupCommunication() {
    // Create network coordinator
    val networkCoordinator = WatchSessionNetworkCoordinator(
        this,
        socketEngine
    )
    
    // Handle incoming sync events
    networkCoordinator.onSyncEventReceived = { event ->
        syncEngine.processSyncEvent(event)
    }
    
    // Handle network errors
    networkCoordinator.onNetworkError = { error ->
        tvStatus.text = "Network error: $error"
    }
    
    // Start network coordination
    networkCoordinator.startNetworkCoordination()
}

private fun sendSyncEventToPeer(event: SyncEvent) {
    networkCoordinator.sendSyncEvent(event)
}
```

---

## 📖 USAGE EXAMPLES

### Example 1: Basic Watch Session (Host)
```kotlin
// Host initiates
val sessionManager = WatchSessionManager(context, storageEngine)

lifecycleScope.launch {
    val session = sessionManager.initiateWatchSession(
        moviePath = "/calcvault/storage/media/movies/movie.mp4",
        peerUserId = "sanu",
        myUserId = "zain"
    )
    
    if (session != null) {
        // Share session ID with peer (via existing call/messaging)
        // Session ready for peer to join
    }
}
```

### Example 2: Follower Joins
```kotlin
val sessionManager = WatchSessionManager(context, storageEngine)

lifecycleScope.launch {
    val session = sessionManager.joinWatchSession(
        sessionId = receivedSessionId,
        peerMovieId = hostMovieHash,     // SHA-256 from host
        peerMoviePath = "",               // Not used for joiner
        peerMovieSize = 2_147_483_648,   // ~2GB
        peerUserId = "zain",
        myUserId = "sanu"
    )
    
    if (session != null) {
        // Successfully joined
        // Local movie validated: same hash + size as host's
    }
}
```

### Example 3: Playback Control (Host Only)
```kotlin
// User presses PLAY button (host only)
private fun onPlayClicked() {
    if (!isHost) {
        toast("Only host can control playback")
        return
    }
    
    // 1. Play locally
    mediaPlayer?.start()
    isPlaying = true
    
    // 2. Create sync event
    val event = syncEngine.createPlayEvent(
        currentTimeMs = mediaPlayer?.currentPosition?.toLong() ?: 0L
    )
    
    // 3. Send to peer
    networkCoordinator.sendSyncEvent(event)
    
    // 4. Update session
    sessionManager.updateSessionState(PlaybackState.PLAYING, mediaPlayer?.currentPosition?.toLong() ?: 0L)
}
```

### Example 4: Sync Drift Correction
```kotlin
// Background loop continuously monitors drift
lifecycleScope.launch {
    while (isActive) {
        delay(500)  // Check every 500ms
        
        // Follower reports position
        if (!isHost) {
            val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
            syncEngine.reportFollowerPosition(currentPos)
        }
        
        // Check if drift is excessive
        if (syncEngine.isDriftExcessive()) {
            Log.w("Sync", "Drift excessive: applying correction")
            
            // Soft correction (gradual catch-up)
            val correction = syncEngine.getCorrectionAmount()
            val newPos = (mediaPlayer?.currentPosition ?: 0) + correction.toInt()
            mediaPlayer?.seekTo(newPos.toInt())
        }
    }
}
```

### Example 5: Manual Resync
```kotlin
// User presses RESYNC button (follower only)
private fun onResyncClicked() {
    if (isHost) {
        toast("Host cannot resync")
        return
    }
    
    // Request instant realignment
    val currentTime = mediaPlayer?.currentPosition?.toLong() ?: 0L
    val syncRequest = syncEngine.requestResync(currentTime)
    
    // Send to host
    networkCoordinator.sendSyncEvent(syncRequest)
    
    // Host will respond with SEEK event to force alignment
}
```

### Example 6: Picture-in-Picture
```kotlin
// User toggles video overlay
private fun onToggleVideo() {
    isVideoEnabled = !isVideoEnabled
    pipContainer.visibility = if (isVideoEnabled) View.VISIBLE else View.GONE
    
    if (isVideoEnabled) {
        // Create PiP manager
        val pipManager = PipManager(pipOverlay)
        
        // Show with animation
        pipManager.setVisible(true, animate = true)
        
        // Apply preset size
        pipManager.applyPreset(PipManager.SizePreset.MEDIUM)
        
        // Setup position change callback
        pipOverlay.onPositionChanged = { x, y ->
            savePipPosition(x, y)
        }
    } else {
        pipManager.setVisible(false, animate = true)
    }
}
```

---

## 🔄 SYNCHRONIZATION ALGORITHM

### HOST Perspective:
```
User Action (Play/Pause/Seek)
    ↓
Update Local Playback
    ↓
Create SyncEvent
    ↓
Broadcast to Follower
    ↓
Mark as Sent
    ↓
Wait for Ack (5s timeout)
```

### FOLLOWER Perspective:
```
Receive SyncEvent
    ↓
Verify Session ID Match
    ↓
Check Event Timestamp (max 10s old)
    ↓
Extract Target Position
    ↓
Adjust Local Playback
    ↓
Report Result
    ↓
Monitor Drift
    ↓
If Drift > 200ms → Request Resync
```

### Drift Detection & Correction:
```
Continuous Loop (every 500ms):
    ┌─────────────────────────────────┐
    │ Measure Current Position (MP)   │
    │ Measure Host Position (HP)      │
    │ Calculate Drift = MP - HP       │
    └────────┬──────────────────────┘
             │
        Is |Drift| < 200ms?
         ┌───┴───┐
        YES     NO
         │       │
      Synced    ┌────────────────────┐
               │ How Big is Drift?   │
               └─────┬────────┬──────┘
             Small  │        │  Large
             (-50ms)│        │(+300ms)
                    │        │
          ┌─────────┘        └───────┐
          │                          │
      Soft Correct              Instant Jump
   (Speed up 100ms)          (Seek to Host Pos)
          │                          │
          └──────────┬───────────────┘
                     │
              ✓ Synced Again
```

### Soft vs Instant Correction:
```
Soft Correction (Gentle):
    - Used for small drifts (<150ms)
    - Gradually speeds up/slows down playback
    - Corrects at rate of ~100ms per check
    - Feels smooth, less jarring
    - Recommended for most cases

Instant Correction (Aggressive):
    - Used for large drifts (>200ms) or manual resync
    - Seeks directly to target position
    - Instant alignment
    - May cause slight stutter
    - Use when user explicitly requests resync
```

---

## 🌐 NETWORK PROTOCOL

### Session Initiation Handshake:
```
HOST                                    PEER
  │                                      │
  ├─────── SESSION INIT Message ────────>│
  │  {                                   │
  │    sessionId: "abc123"              │
  │    movieId: "sha256hash"            │
  │    movieSize: 2147483648            │
  │    movieName: "movie.mp4"           │
  │  }                                   │
  │                                      │
  │                          Verify Movie Locally
  │                          Compute Hash
  │                          Compare with Host's
  │                                      │
  │<────── SESSION ACCEPT Message ───────┤
  │  {                                   │
  │    sessionId: "abc123"              │
  │    status: "ACCEPTED"               │
  │    verified: true                   │
  │  }                                   │
  │                                      │
  ├─────── SYNC START ────────────────>│
      Audio Channel + Data Channel Active
```

### Sync Event Message Format (JSON):
```json
{
  "eventId": "event-uuid-123",
  "sessionId": "session-uuid-456",
  "senderId": "zain",
  "eventType": "PLAY|PAUSE|SEEK|DRIFT_REPORT|SYNC_REQUEST|SESSION_END",
  "timelinePositionMs": 12345,
  "state": "PLAYING|PAUSED|SEEKING|BUFFERING|IDLE|ENDED",
  "timestamp": 1713292800000,
  "driftMs": 0
}
```

### Network Layer:
```
┌──────────────────────────────────────────────┐
│       SocketCallEngine (Bidirectional)       │
├──────────────────────────────────────────────┤
│ Audio Channel           Data Channel         │
│ ─────────────           ────────────         │
│ • PCM frames            • JSON messages      │
│ • Real-time audio       • Structured data    │
│ • No ordering req       • Order preserved    │
│ • Low latency ideal     • Reliability req    │
│                                              │
│ Protocol: TCP/IP with Encryption (E2E)      │
│ Port: 7777 (audio), 7778 (data, if separate)│
└──────────────────────────────────────────────┘
```

### Encryption:
```
All sync event data is encrypted using:
E2EKeyManager.encrypt(data: ByteArray): ByteArray

Decryption:
E2EKeyManager.decrypt(data: ByteArray): ByteArray

This ensures sync events are only readable by 
the intended peer, protecting against MITM attacks.
```

---

## ⚠️ ERROR HANDLING

### Movie Validation Errors:
```kotlin
when {
    // Movie not found
    !movieValidator.validateMovieFile(path) → {
        toast("Movie file not found")
        endSession()
    }
    
    // Hash mismatch
    localHash != peerHash → {
        toast("Movie files don't match")
        showDownloadPrompt()
    }
    
    // Size mismatch
    localSize != peerSize → {
        toast("Movie corrupted - sizes don't match")
        endSession()
    }
}
```

### Network Errors:
```kotlin
networkCoordinator.onNetworkError = { error →
    when {
        error.contains("Connection refused") → {
            tvStatus.text = "Cannot reach peer"
            showReconnectButton()
        }
        
        error.contains("Timeout") → {
            tvStatus.text = "Network timeout"
            // Auto-retry with exponential backoff
        }
        
        error.contains("Disconnected") → {
            tvStatus.text = "Connection lost"
            // Attempt reconnection
        }
    }
}
```

### Sync Errors:
```kotlin
syncEngine.isDriftExcessive() → {
    // Drift >200ms
    if (Manual Resync Requested) {
        applyInstantCorrection()
    } else {
        applySoftCorrection()
    }
}

if (eventStale) {
    // Event >10s old
    discardEvent()
    syncErrors++
}
```

### Recovery Strategies:
```
1. Soft Correction First
   - Try gentle speed adjustment
   - Wait 2 seconds
   - If still bad → escalate

2. Instant Correction
   - Seek to host position
   - Usually works for network jitter

3. Full Resync
   - Pause both devices
   - Align to host time
   - Resume together
   - Request manual resync if still failing

4. Session Restart
   - If repeated failures
   - End current session
   - Allow user to restart watch session
```

---

## 🧪 TESTING

### Unit Tests:

```kotlin
// Test sync engine logic
@Test
fun testDriftDetection() {
    val engine = PlaybackSynchronizationEngine()
    engine.initializeSession(mockSession, "zain", false)
    
    // Host at 10s, follower at 10.5s
    engine.lastHostTimeMs.set(10_000L)
    engine.lastFollowerTimeMs.set(10_500L)
    
    assertTrue(engine.isDriftExcessive())  // 500ms > 200ms threshold
}

@Test
fun testMovieMatching() {
    val validator = MovieValidator(context, storage)
    
    val hash1 = validator.computeMovieHash(path1)
    val hash2 = validator.computeMovieHash(path2)
    
    assertTrue(validator.areMoviesIdentical(
        path1, 2GB, hash1,
        path2, 2GB, hash2
    ))
}

@Test
fun testSessionInitiation() {
    val manager = WatchSessionManager(context, storage)
    
    val session = manager.initiateWatchSession(
        moviePath,
        "sanu",
        "zain"
    )
    
    assertNotNull(session)
    assertEquals(session.host, "zain")
    assertEquals(session.participants.size, 2)
}
```

### Integration Tests:

```kotlin
@Test
fun testFullWatchSessionFlow() {
    // 1. Host initiates
    val hostSession = hostManager.initiateWatchSession(...)
    
    // 2. Peer joins
    val peerSession = peerManager.joinWatchSession(
        hostSession.sessionId,
        hostSession.movieId,
        hostSession.movieSizeBytes,
        ...
    )
    
    assertNotNull(peerSession)
    assertEquals(hostSession.sessionId, peerSession.sessionId)
    
    // 3. Host plays
    val playEvent = hostSyncEngine.createPlayEvent(0L)
    
    // 4. Peer receives and processes
    peerSyncEngine.processSyncEvent(playEvent)
    
    assertEquals(PlaybackState.PLAYING, peerSyncEngine.getCurrentSession()?.state)
}
```

### Manual Testing Checklist:

```
□ Movie Selection
  □ Display available movies with thumbnails
  □ Show file size and duration
  □ Filter by type (already implemented)

□ Session Initiation
  □ Host can start "Watch Together"
  □ Session ID generated and displayed
  □ Peer can join from call screen

□ Movie Matching
  □ Both devices have same movie → Accept
  □ Different movies → Block with error
  □ Movie missing on peer → Download prompt

□ Playback Sync
  □ Host presses play → Both play
  □ Host pauses → Both pause
  □ Host seeks → Both seek to same position
  □ <200ms drift detected
  □ >200ms drift triggers auto-correction
  □ Manual resync works instantly

□ Voice/Video
  □ Voice enabled by default
  □ Can toggle voice on/off
  □ Video overlay appears when enabled
  □ PiP draggable across screen
  □ PiP resizable

□ UI/UX
  □ Status shows "Connected to Sanu"
  □ Drift indicator shows current drift
  □ Play/pause buttons reflect state
  □ Only host can control playback
  □ Follower sees read-only mode

□ Error Cases
  □ Network disconnects → "Sync Lost" message
  □ Press resync → Instant alignment
  □ Movie goes missing → Error + option to download
  □ High latency → UI becomes responsive
  □ Session timeout after 1 hour → Auto-cleanup

□ Performance
  □ Video plays smoothly (60fps ideal)
  □ No lag in UI responses
  □ Voice clear and audible
  □ Low battery impact
```

---

## ⚙️ PERFORMANCE TUNING

### Drift Check Frequency:
```kotlin
// Default: 500ms (balance between responsiveness and overhead)
// Adjustable:
DRIFT_CHECK_INTERVAL = 500L  // Too frequent = battery drain
                             // Too sparse = sync lag
```

### Soft Correction Speed:
```kotlin
// Default: 100ms per check (catches up 100ms every 500ms)
SOFT_CORRECTION_MS = 100L  // Faster = jarring
                           // Slower = prolonged drift
```

### Network Buffer Size:
```kotlin
// For large messages
MAX_MESSAGE_SIZE = 8192  // Increase for debug info
                         // Decrease for bandwidth constraints
```

### Priority Settings:
```kotlin
// Playback priority
MediaPlayer runs on MAIN thread (UI thread)
  → Ensures smooth video rendering

// Sync monitoring
Runs on IO using coroutines (background)
  → Doesn't block UI

// Network I/O
AsyncTask or Coroutine with Dispatcher.IO
  → Non-blocking socket operations
```

---

## 📝 NEXT STEPS

1. **Integrate Startup Flow**
   - Add "Watch Together" button to call screen
   - Implement movie selection UI
   - Handle session handshake

2. **WebRTC Video Feed**
   - Setup video track for PiP
   - Render peer's video in overlay
   - Handle video enable/disable

3. **Advanced Features**
   - Reactions (😂 ❤️) → Send as annotations
   - Chat overlay (optional, minimal)
   - Playback speed adjustment
   - Subtitle sync

4. **Testing**
   - Device-to-device testing on real hardware
   - Network latency simulation (throttle tool)
   - Long session stability (4+ hour movie)
   - Battery/thermal monitoring

5. **Analytics**
   - Track sync accuracy metrics
   - Monitor network performance
   - Measure user engagement
   - Identify drift pattern issues

---

## 📚 REFERENCES

- **Architecture**: Event-driven sync with leader-follower model
- **Drift Tolerance**: 200ms max (industry standard for video sync)
- **Movie Validation**: SHA-256 hashing for integrity
- **Encryption**: E2E encryption via existing E2EKeyManager
- **Testing**: Android Espresso + JUnit4
- **Monitoring**: Log + analytics

---

**Implementation Complete**  
Ready for integration and testing!
