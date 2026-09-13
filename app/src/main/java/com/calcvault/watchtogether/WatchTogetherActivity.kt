package com.calcvault.watchtogether

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.call.SocketCallEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.network.webrtc.WebRTCManager
import com.calcvault.storage.USBStorageEngine
import com.calcvault.sync.*
import com.calcvault.ui.common.GlassUi
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.*

/**
 * WatchTogetherActivity - Main UI for synchronized movie playback
 *
 * Features:
 * - Full-screen video playback with sync controls
 * - Real-time synchronization with peer
 * - Voice/video communication during playback
 * - Picture-in-Picture for video overlay
 * - Drift detection and auto-correction
 * - Session management and lifecycle
 *
 * Layout:
 * ┌──────────────────────┐
 * │   VIDEO PLAYER (FS)  │
 * │                      │
 * │     [PiP Overlay]    │  ← Small video from peer (draggable)
 * │                      │
 * └──────────────────────┘
 * ┌──────────────────────┐
 * │  [Play] [Pause] [Seek bar]
 * │  Status: Connected to Sanu (100ms drift)
 * │  [Toggle Voice] [Resync] [End Call]
 * └──────────────────────┘
 */
class WatchTogetherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WatchTogether"

        // Intent extras
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_MOVIE_PATH = "moviePath"
        const val EXTRA_PEER_ID = "peerId"
        const val EXTRA_IS_HOST = "isHost"
        const val EXTRA_PEER_MOVIE_HASH = "peerMovieHash"
        const val EXTRA_PEER_MOVIE_SIZE = "peerMovieSize"
        private const val REQUEST_PICK_LOCAL_VIDEO = 4101
    }

    // Core components
    private lateinit var storageEngine: USBStorageEngine
    private lateinit var themeEngine: ThemeEngine
    private lateinit var sessionManager: WatchSessionManager
    private lateinit var syncEngine: PlaybackSynchronizationEngine
    private lateinit var socketEngine: SocketCallEngine
    private var webRtcManager: WebRTCManager? = null

    // UI Components
    private lateinit var videoView: VideoView
    private lateinit var pipContainer: FrameLayout
    private lateinit var tvVideoPlaceholder: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDrift: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnPickVideo: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var btnToggleVoice: ImageButton
    private lateinit var btnToggleVideo: ImageButton
    private lateinit var btnResync: Button
    private lateinit var btnEndCall: Button
    private lateinit var tvConnectedTo: TextView

    // Playback state
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var sessionId = ""
    private var moviePath = ""
    private var peerId = ""
    private var isHost = false
    private var currentSession: WatchSession? = null

    // PiP state
    private var pipConfig = PipConfig()
    private var lastPipX = 0f
    private var lastPipY = 0f

    // Voice/Video state
    private var isVoiceEnabled = true
    private var isVideoEnabled = false

    // UI update
    private val uiHandler = Handler(Looper.getMainLooper())
    private var progressUpdateJob: Job? = null

    // ── LIFECYCLE ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_SECURE
        )

        // Extract intent data
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        moviePath = intent.getStringExtra(EXTRA_MOVIE_PATH) ?: ""
        peerId = intent.getStringExtra(EXTRA_PEER_ID) ?: ""
        isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false)

        // Initialize core components
        storageEngine = USBStorageEngine.getInstance(this)
        themeEngine = ThemeEngine(this)
        sessionManager = WatchSessionManager(this, storageEngine)
        syncEngine = PlaybackSynchronizationEngine()
        socketEngine = SocketCallEngine(this)

        buildUI()
        GlassUi.applyThemeChrome(this)
        setupPlayback()
        setupSync()
        setupCommunication()

        Log.d(TAG, "Activity created: host=$isHost, session=$sessionId")
    }

    override fun onStart() {
        super.onStart()
        GlassUi.applyThemeChrome(this)
        startProgressUpdates()
    }

    override fun onStop() {
        super.onStop()
        stopProgressUpdates()
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        isPlaying = false
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        syncEngine.endSession()
        socketEngine.endCall()
        webRtcManager?.disconnect()
        progressUpdateJob?.cancel()
        ThemeApplicator.detach(this)
    }

    // ── UI SETUP ────────────────────────────────────────────────────────────

    private fun buildUI() {
        val theme = themeEngine.getCurrentTheme()
        val root = FrameLayout(this)
        setContentView(root)

        // Main container
        val mainContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(mainContainer)

        // Video player (fullscreen)
        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        mainContainer.addView(videoView)

        tvVideoPlaceholder = TextView(this).apply {
            text = "Watch Together\nPick a local video file to start a synced session."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(theme.primaryText)
            setPadding(36, 36, 36, 36)
            visibility = if (moviePath.isBlank()) View.VISIBLE else View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        mainContainer.addView(tvVideoPlaceholder)

        // PiP container that overlays video
        pipContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                pipConfig.width,
                pipConfig.height,
                Gravity.TOP or Gravity.END
            ).apply {
                rightMargin = 20
                topMargin = 20
            }
            setBackgroundColor(0x88000000.toInt())
            visibility = View.GONE // Disabled by default
        }
        GlassUi.markPanel(pipContainer, alpha = 146, radiusDp = 20)
        mainContainer.addView(pipContainer)

        // Controls overlay (bottom)
        val controlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setBackgroundColor(0xBB000000.toInt())
        }
        GlassUi.markPanel(controlsContainer, alpha = 158, radiusDp = 0)
        mainContainer.addView(controlsContainer)

        // Status bar
        tvConnectedTo = TextView(this).apply {
            text = "Connected to $peerId"
            textSize = 12f
            setTextColor(0xFF00FF00.toInt())
            setPadding(16, 8, 16, 4)
        }
        controlsContainer.addView(tvConnectedTo)

        tvStatus = TextView(this).apply {
            text = "Initializing..."
            textSize = 12f
            setTextColor(theme.primaryText)
            setPadding(16, 4, 16, 4)
        }
        controlsContainer.addView(tvStatus)

        tvDrift = TextView(this).apply {
            text = "Drift: 0ms"
            textSize = 11f
            setTextColor(0xFFFFFF00.toInt())
            setPadding(16, 0, 16, 4)
        }
        controlsContainer.addView(tvDrift)

        // Time info
        val timeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        controlsContainer.addView(timeContainer)

        tvCurrentTime = TextView(this).apply {
            text = "00:00"
            textSize = 12f
            setTextColor(theme.primaryText)
            setPadding(16, 4, 8, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        timeContainer.addView(tvCurrentTime)

        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }
        timeContainer.addView(seekBar)

        tvDuration = TextView(this).apply {
            text = "00:00"
            textSize = 12f
            setTextColor(theme.primaryText)
            setPadding(8, 4, 16, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        timeContainer.addView(tvDuration)

        // Control buttons row
        val buttonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 16)
        }
        controlsContainer.addView(buttonsContainer)

        btnPickVideo = Button(this).apply {
            text = "Pick Video"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            setOnClickListener { openLocalVideoPicker() }
        }
        GlassUi.styleButton(btnPickVideo)
        buttonsContainer.addView(btnPickVideo)

        btnPlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            setOnClickListener { onPlayClicked() }
        }
        buttonsContainer.addView(btnPlay)

        btnPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            setOnClickListener { onPauseClicked() }
        }
        buttonsContainer.addView(btnPause)

        btnToggleVoice = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            setOnClickListener { onToggleVoice() }
        }
        buttonsContainer.addView(btnToggleVoice)

        btnToggleVideo = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            setOnClickListener { onToggleVideo() }
        }
        buttonsContainer.addView(btnToggleVideo)

        btnResync = Button(this).apply {
            text = "Resync"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
            setOnClickListener { onResyncClicked() }
        }
        buttonsContainer.addView(btnResync)

        btnEndCall = Button(this).apply {
            text = "End"
            setBackgroundColor(0xFFCC0000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onEndCallClicked() }
        }
        buttonsContainer.addView(btnEndCall)

        // Setup seek bar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && isHost) {
                    val targetTimeMs = progress.toLong()
                    mediaPlayer?.seekTo(targetTimeMs.toInt())

                    // Send sync event to peer
                    val syncEvent = syncEngine.createSeekEvent(targetTimeMs)
                    sendSyncEventToPeer(syncEvent)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    // ── PLAYBACK SETUP ──────────────────────────────────────────────────────

    private fun setupPlayback() {
        lifecycleScope.launch {
            try {
                if (moviePath.isBlank()) {
                    tvStatus.text = "Pick a video from Media to start Watch Together"
                    seekBar.max = 1
                    return@launch
                }

                videoView.stopPlayback()
                if (moviePath.startsWith("content://", ignoreCase = true)) {
                    videoView.setVideoURI(Uri.parse(moviePath))
                } else {
                    videoView.setVideoPath(moviePath)
                }
                tvVideoPlaceholder.visibility = View.GONE

                // Set up MediaController with custom implementation
                val mediaController = MediaController(this@WatchTogetherActivity)
                mediaController.setAnchorView(videoView)
                videoView.setMediaController(mediaController)

                // Set up completion listener
                videoView.setOnCompletionListener {
                    Log.d(TAG, "Video completed")
                    isPlaying = false
                    onVideoCompleted()
                }

                // Get duration once video is prepared
                videoView.setOnPreparedListener { mp ->
                    mediaPlayer = mp
                    val duration = mp.duration
                    tvDuration.text = formatTime(duration.toLong())
                    seekBar.max = duration
                    Log.d(TAG, "Video prepared: duration=$duration ms")
                }

                tvStatus.text = "Ready: ${displayNameFor(Uri.parse(moviePath)).ifBlank { "local video" }}"
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up playback", e)
                tvStatus.text = "Error: ${e.message}"
            }
        }
    }

    // ── SYNCHRONIZATION SETUP ───────────────────────────────────────────────

    private fun setupSync() {
        lifecycleScope.launch {
            try {
                if (moviePath.isBlank() && isHost) {
                    tvStatus.text = "Watch ready. Select a video before hosting."
                    return@launch
                }

                // Create or join session based on host status
                val session = if (isHost) {
                    sessionManager.initiateWatchSession(
                        moviePath,
                        peerId,
                        "zain" // TODO: Get actual user ID
                    )
                } else {
                    val peerMovieHash = intent.getStringExtra(EXTRA_PEER_MOVIE_HASH) ?: ""
                    val peerMovieSize = intent.getLongExtra(EXTRA_PEER_MOVIE_SIZE, 0L)

                    sessionManager.joinWatchSession(
                        sessionId,
                        peerMovieHash,
                        "", // Not used for joiner
                        peerMovieSize,
                        peerId,
                        "sanu" // TODO: Get actual user ID
                    )
                }

                if (session == null) {
                    tvStatus.text = "Failed to create session"
                    Log.e(TAG, "Session creation failed")
                    return@launch
                }

                currentSession = session

                // Initialize sync engine
                syncEngine.initializeSession(
                    session,
                    if (isHost) "zain" else "sanu", // TODO: Get from system
                    isHost
                )

                // Setup sync callbacks
                setupSyncCallbacks()

                tvStatus.text = if (isHost) "Host - Ready" else "Connected as Follower"
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up sync", e)
                tvStatus.text = "Sync error: ${e.message}"
            }
        }
    }

    private fun setupSyncCallbacks() {
        syncEngine.onPlaybackAdjustmentNeeded = { targetTimeMs, eventType ->
            uiHandler.post {
                when (eventType) {
                    SyncEventType.PLAY -> {
                        mediaPlayer?.start()
                        isPlaying = true
                        updatePlayPauseButtons()
                    }
                    SyncEventType.PAUSE -> {
                        mediaPlayer?.pause()
                        isPlaying = false
                        updatePlayPauseButtons()
                    }
                    SyncEventType.SEEK -> {
                        mediaPlayer?.seekTo(targetTimeMs.toInt())
                        seekBar.progress = targetTimeMs.toInt()
                    }
                    SyncEventType.BUFFERING -> {
                        tvStatus.text = "Buffering..."
                    }
                    SyncEventType.BUFFERING_END -> {
                        tvStatus.text = "Playing"
                    }
                    else -> {}
                }
            }
        }

        syncEngine.onDriftDetected = { driftMs ->
            uiHandler.post {
                tvDrift.text = "Drift: ${driftMs}ms"
                if (syncEngine.isDriftExcessive()) {
                    tvDrift.setTextColor(0xFFFF6666.toInt())
                } else {
                    tvDrift.setTextColor(0xFFFFFF00.toInt())
                }
            }
        }

        syncEngine.onSyncLocked = {
            uiHandler.post {
                tvStatus.setTextColor(0xFF00FF00.toInt())
            }
        }

        syncEngine.onSyncLost = {
            uiHandler.post {
                tvStatus.setTextColor(0xFFFF6666.toInt())
                tvStatus.text = "SYNC LOST - Press Resync"
            }
        }
    }

    // ── COMMUNICATION SETUP ──────────────────────────────────────────────────

    private fun setupCommunication() {
        lifecycleScope.launch {
            try {
                if (isHost) {
                    // Host waits for peer
                    socketEngine.startCall()
                } else {
                    // Peer connects to host
                    val hostIp = "192.168.1.100" // TODO: Get from session setup
                    socketEngine.answerCall(hostIp)
                }

                // Set up audio handlers
                socketEngine.onStateChanged = { state ->
                    uiHandler.post {
                        when (state) {
                            SocketCallEngine.ConnectionState.CONNECTED -> {
                                tvStatus.text = "Voice connected"
                                isVoiceEnabled = true
                            }
                            SocketCallEngine.ConnectionState.DISCONNECTED -> {
                                tvStatus.text = "Voice disconnected"
                                isVoiceEnabled = false
                            }
                            else -> {}
                        }
                    }
                }

                Log.d(TAG, "Communication setup complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up communication", e)
                tvStatus.text = "Comm error: ${e.message}"
            }
        }
    }

    // ── BUTTON HANDLERS ──────────────────────────────────────────────────────

    private fun onPlayClicked() {
        if (!isHost) {
            tvStatus.text = "Only host can control playback"
            return
        }

        mediaPlayer?.start()
        isPlaying = true
        updatePlayPauseButtons()

        currentSession?.let {
            val event = syncEngine.createPlayEvent(mediaPlayer?.currentPosition?.toLong() ?: 0L)
            sendSyncEventToPeer(event)
            sessionManager.updateSessionState(PlaybackState.PLAYING, mediaPlayer?.currentPosition?.toLong() ?: 0L)
        }
    }

    private fun onPauseClicked() {
        if (!isHost) {
            tvStatus.text = "Only host can control playback"
            return
        }

        mediaPlayer?.pause()
        isPlaying = false
        updatePlayPauseButtons()

        currentSession?.let {
            val event = syncEngine.createPauseEvent(mediaPlayer?.currentPosition?.toLong() ?: 0L)
            sendSyncEventToPeer(event)
            sessionManager.updateSessionState(PlaybackState.PAUSED, mediaPlayer?.currentPosition?.toLong() ?: 0L)
        }
    }

    private fun onToggleVoice() {
        isVoiceEnabled = !isVoiceEnabled
        tvStatus.text = if (isVoiceEnabled) "Voice ON" else "Voice OFF"
        btnToggleVoice.alpha = if (isVoiceEnabled) 1.0f else 0.5f
    }

    private fun onToggleVideo() {
        isVideoEnabled = !isVideoEnabled
        pipContainer.visibility = if (isVideoEnabled) View.VISIBLE else View.GONE
        btnToggleVideo.alpha = if (isVideoEnabled) 1.0f else 0.5f

        pipConfig = pipConfig.copy(isEnabled = isVideoEnabled)
    }

    private fun onResyncClicked() {
        if (isHost) {
            tvStatus.text = "Host cannot resync"
            return
        }

        val currentTime = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val syncRequest = syncEngine.requestResync(currentTime)
        sendSyncEventToPeer(syncRequest)
        tvStatus.text = "Resync requested..."
    }

    private fun onEndCallClicked() {
        Log.d(TAG, "Ending watch session")

        currentSession?.let {
            if (isHost) {
                val endEvent = SyncEvent(
                    sessionId = it.sessionId,
                    senderId = "zain", // TODO
                    eventType = SyncEventType.SESSION_END,
                    timelinePositionMs = mediaPlayer?.currentPosition?.toLong() ?: 0L,
                    state = PlaybackState.ENDED
                )
                sendSyncEventToPeer(endEvent)
            }
            sessionManager.endSession()
        }

        socketEngine.endCall()
        finish()
    }

    private fun openLocalVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_LOCAL_VIDEO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_LOCAL_VIDEO || resultCode != Activity.RESULT_OK) return

        val uri = data?.data ?: return
        persistVideoPermission(uri)
        moviePath = uri.toString()
        tvVideoPlaceholder.visibility = View.GONE
        tvStatus.text = "Selected: ${displayNameFor(uri)}"
        setupPlayback()
        setupSync()
    }

    private fun persistVideoPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { error ->
            Log.d(TAG, "Video URI is not persistable: $uri", error)
        }
    }

    private fun displayNameFor(uri: Uri): String {
        if (uri.scheme != "content") {
            return uri.lastPathSegment?.substringAfterLast('/') ?: "local video"
        }
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "local video"
    }

    private fun onVideoCompleted() {
        currentSession?.let {
            sessionManager.updateSessionState(PlaybackState.ENDED, mediaPlayer?.duration?.toLong() ?: 0L)
        }
    }

    // ── SYNC COMMUNICATION ──────────────────────────────────────────────────

    private fun sendSyncEventToPeer(event: SyncEvent) {
        try {
            val json = JSONObject().apply {
                put("type", "SYNC_EVENT")
                put("eventId", event.eventId)
                put("sessionId", event.sessionId)
                put("senderId", event.senderId)
                put("eventType", event.eventType.name)
                put("timelinePositionMs", event.timelinePositionMs)
                put("state", event.state.name)
                put("timestamp", event.timestamp)
                put("driftMs", event.driftMs)
            }

            // Send via socket
            // TODO: Integrate with actual socket send
            Log.d(TAG, "Sync event sent: ${event.eventType}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending sync event", e)
        }
    }

    // ── UI UPDATES ──────────────────────────────────────────────────────────

    private fun startProgressUpdates() {
        progressUpdateJob = lifecycleScope.launch {
            while (isActive) {
                uiHandler.post {
                    mediaPlayer?.let { mp ->
                        val current = mp.currentPosition.toLong()
                        val duration = mp.duration.toLong()

                        // Update seekbar
                        if (!seekBar.isPressed) {
                            seekBar.progress = mp.currentPosition
                        }

                        // Update time display
                        tvCurrentTime.text = formatTime(current)
                        tvDuration.text = formatTime(duration)

                        // Report position to sync engine (for drift calculation)
                        if (!isHost) {
                            syncEngine.reportFollowerPosition(current)
                        }
                    }
                }
                delay(100) // Update every 100ms
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
    }

    private fun updatePlayPauseButtons() {
        btnPlay.alpha = if (isPlaying) 0.5f else 1.0f
        btnPause.alpha = if (isPlaying) 1.0f else 0.5f
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
