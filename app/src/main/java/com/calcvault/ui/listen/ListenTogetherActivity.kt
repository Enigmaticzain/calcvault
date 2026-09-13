package com.calcvault.ui.listen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.calcvault.call.SocketCallEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.listen.ListenDriftPolicy
import com.calcvault.listen.ListenTogetherCrypto
import com.calcvault.listen.LocalMusicTrack
import com.calcvault.listen.MusicLibraryScanner
import com.calcvault.listen.MusicSession
import com.calcvault.listen.PlaybackState
import com.calcvault.listen.SyncRole
import com.calcvault.listen.TrackFingerprint
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.USBStorageEngine
import com.calcvault.ui.common.GlassUi
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/**
 * ListenTogetherActivity
 *
 * Synchronized local music playback between two devices with shared controls.
 * Music data is never transferred, only encrypted control metadata is exchanged.
 */
class ListenTogetherActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
        private const val SIGNAL_INTERVAL_MS = 850L
        private const val UI_REFRESH_INTERVAL_MS = 250L
        private const val PING_INTERVAL_MS = 6_000L
        private const val REQ_RECORD_AUDIO = 1201
        private const val REQUEST_CODE_FILE_PICKER = 1202

        private const val SIG_INVITE = "listen_invite"
        private const val SIG_ACCEPT = "listen_accept"
        private const val SIG_REJECT = "listen_reject"
        private const val SIG_MISMATCH = "listen_mismatch"
        private const val SIG_EVENT = "listen_event"
        private const val SIG_CONTROL_REQUEST = "listen_control_request"
        private const val SIG_RESYNC_REQUEST = "listen_resync_request"
        private const val SIG_PING = "listen_ping"
        private const val SIG_PONG = "listen_pong"
        private const val SIG_VOICE_REQUEST = "listen_voice_request"
        private const val SIG_VOICE_OFFER = "listen_voice_offer"
        private const val SIG_VOICE_ACCEPT = "listen_voice_accept"
        private const val SIG_VOICE_END = "listen_voice_end"
    }

    private data class HostSnapshot(
        val trackId: String,
        val state: PlaybackState,
        val positionMs: Long,
        val hostSentAtMs: Long
    )

    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var networkEngine: NetworkMessageEngine

    private lateinit var tvPartner: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTrackMeta: TextView
    private lateinit var tvSyncState: TextView
    private lateinit var tvTime: TextView
    private lateinit var ivArtwork: ImageView
    private lateinit var spinnerRole: Spinner
    private lateinit var spinnerTrack: Spinner
    private lateinit var btnStartOrChange: Button
    private lateinit var btnPlayPause: Button
    private lateinit var btnResync: Button
    private lateinit var btnTalk: Button
    private lateinit var switchMute: Switch
    private lateinit var seekTimeline: SeekBar
    private lateinit var seekMusicVolume: SeekBar
    private lateinit var seekVoiceVolume: SeekBar

    private val trackItems = mutableListOf<LocalMusicTrack>()
    private val trackLabels = mutableListOf<String>()
    private lateinit var trackAdapter: ArrayAdapter<String>

    private var localUserId = "zain"
    private var partnerUserId = "sanu"
    private var role = SyncRole.HOST

    private var mediaPlayer: MediaPlayer? = null
    private var loadedTrack: LocalMusicTrack? = null
    private var loadedTrackFingerprint: TrackFingerprint? = null
    private var remoteTrackFingerprint: TrackFingerprint? = null
    private var session: MusicSession? = null
    private var partnerAccepted = false

    private var pendingInvitePayload: JSONObject? = null
    private var pendingStartOnPrepared = false
    private var pendingSeekOnPreparedMs = 0L
    private var userSeeking = false

    private var inSyncedMode = true
    private var lastHostSnapshot: HostSnapshot? = null
    private var clockOffsetMs: Long = 0L

    private var syncJob: Job? = null
    private var uiJob: Job? = null
    private var pingJob: Job? = null

    private var voiceEngine: SocketCallEngine? = null
    private var voiceActive = false
    private var voiceMuted = false
    private var pendingVoiceStart = false
    private var musicVolume = 0.85f
    private var voiceVolume = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        localUserId = SessionManager.localUserId.ifBlank { "zain" }
        partnerUserId = SessionManager.partnerUserId.ifBlank {
            if (localUserId == "zain") "sanu" else "zain"
        }

        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        networkEngine = NetworkMessageEngine(this, messageDB, storageEngine)
        networkEngine.initialize(
            localUserId,
            partnerUserId,
            SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING }
        )

        buildUi()
        GlassUi.applyThemeChrome(this)
        observeNetwork()
        startUiLoop()
        startSyncLoop()
        loadLocalMusicLibrary()

        val incoming = intent.getBooleanExtra("is_incoming", false)
        if (incoming) {
            intent.getStringExtra("from_user")?.takeIf { it.isNotBlank() }?.let {
                partnerUserId = it
            }
            role = SyncRole.FOLLOWER
            val payloadRaw = intent.getStringExtra("invite_payload")
            if (!payloadRaw.isNullOrBlank()) {
                pendingInvitePayload = runCatching { JSONObject(payloadRaw) }.getOrNull()
            }
            val sessionId = intent.getStringExtra("session_id").orEmpty()
            if (pendingInvitePayload == null && sessionId.isNotBlank()) {
                pendingInvitePayload = JSONObject().apply { put("sessionId", sessionId) }
            }
            tvStatus.text = "Incoming listen invite from ${partnerDisplayName()}"
        }

        tvPartner.text = "Listening with ${partnerDisplayName()}"
        refreshRoleUi(lockRole = incoming)
    }

    private fun buildUi() {
        val rootScroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 48)
        }
        rootScroll.addView(root)
        setContentView(rootScroll)
        GlassUi.markPanel(root, alpha = 98, radiusDp = 28)

        val tvTitle = TextView(this).apply {
            text = "Listen Together"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(tvTitle)

        tvPartner = TextView(this).apply {
            text = "Listening with ${partnerDisplayName()}"
            textSize = 14f
            setTextColor(0xFFB0B0B0.toInt())
            setPadding(0, 10, 0, 22)
        }
        root.addView(tvPartner)

        root.addView(sectionLabel("Role"))
        spinnerRole = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ListenTogetherActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Host", "Follower")
            )
            setSelection(0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (session != null) return
                    role = if (position == 0) SyncRole.HOST else SyncRole.FOLLOWER
                    refreshRoleUi(lockRole = false)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        root.addView(spinnerRole)

        root.addView(sectionLabel("Track"))
        trackAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, trackLabels)
        spinnerTrack = Spinner(this).apply {
            adapter = trackAdapter
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = trackItems.getOrNull(position) ?: return
                    updateTrackMeta(selected)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        root.addView(spinnerTrack)

        val btnBrowseFiles = Button(this).apply {
            text = "Browse Files"
            setOnClickListener { openFilePicker() }
        }
        root.addView(btnBrowseFiles)

        ivArtwork = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(280, 280).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 14
            }
            setBackgroundColor(0x33222222)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(ivArtwork)

        btnStartOrChange = Button(this).apply {
            text = "Start Listen Session"
            setOnClickListener { onStartOrChangeClicked() }
        }
        root.addView(btnStartOrChange)

        tvTrackMeta = TextView(this).apply {
            text = "No track selected"
            textSize = 13f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 14, 0, 8)
        }
        root.addView(tvTrackMeta)

        seekTimeline = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        tvTime.text = "${formatMs(progress.toLong())} / ${formatMs(mediaPlayer?.duration?.toLong() ?: 0L)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = false
                    val targetMs = seekBar?.progress?.toLong() ?: 0L
                    handleSeekRequest(targetMs)
                }
            })
        }
        root.addView(seekTimeline)

        tvTime = TextView(this).apply {
            text = "0:00 / 0:00"
            gravity = Gravity.END
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 2, 0, 18)
        }
        root.addView(tvTime)

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(controlsRow)

        btnPlayPause = Button(this).apply {
            text = "Play"
            isEnabled = false
            setOnClickListener { handlePlayPauseRequest() }
        }
        controlsRow.addView(btnPlayPause)

        btnResync = Button(this).apply {
            text = "Resync"
            setOnClickListener { requestResync() }
        }
        controlsRow.addView(btnResync)

        tvSyncState = TextView(this).apply {
            text = "Sync: waiting"
            textSize = 12f
            setTextColor(0xFF7CC8FF.toInt())
            setPadding(0, 16, 0, 6)
        }
        root.addView(tvSyncState)

        tvStatus = TextView(this).apply {
            text = "Load a local track to start"
            textSize = 13f
            setTextColor(0xFFE0E0E0.toInt())
            setPadding(0, 0, 0, 18)
        }
        root.addView(tvStatus)

        root.addView(sectionLabel("Voice (Optional)"))

        btnTalk = Button(this).apply {
            text = "Start Talk"
            setOnClickListener { onTalkClicked() }
        }
        root.addView(btnTalk)

        switchMute = Switch(this).apply {
            text = "Mute Mic"
            setOnCheckedChangeListener { _, checked ->
                if (voiceMuted != checked) {
                    voiceMuted = checked
                    voiceEngine?.toggleMute()
                }
            }
        }
        root.addView(switchMute)

        root.addView(sliderLabel("Music Volume"))
        seekMusicVolume = SeekBar(this).apply {
            max = 100
            progress = (musicVolume * 100).toInt()
            setOnSeekBarChangeListener(
                simpleSeekListener { progress ->
                    musicVolume = progress / 100f
                    mediaPlayer?.setVolume(musicVolume, musicVolume)
                }
            )
        }
        root.addView(seekMusicVolume)

        root.addView(sliderLabel("Voice Volume"))
        seekVoiceVolume = SeekBar(this).apply {
            max = 100
            progress = (voiceVolume * 100).toInt()
            setOnSeekBarChangeListener(
                simpleSeekListener { progress ->
                    voiceVolume = progress / 100f
                    voiceEngine?.setRemotePlaybackVolume(voiceVolume)
                }
            )
        }
        root.addView(seekVoiceVolume)
    }

    private fun observeNetwork() {
        networkEngine.onControlSignalReceived = { signal ->
            runOnUiThread {
                handleControlSignal(signal)
            }
        }

        networkEngine.onConnectionStateChanged = { state, _ ->
            runOnUiThread {
                inSyncedMode = state == NetworkMessageEngine.ConnectionState.CONNECTED
                if (!inSyncedMode) {
                    tvSyncState.text = "Sync: offline (local unsynced mode)"
                    tvSyncState.setTextColor(0xFFFFD166.toInt())
                    tvStatus.text = "Connection lost. You can keep listening locally."
                } else {
                    tvSyncState.text = "Sync: online"
                    tvSyncState.setTextColor(0xFF7CC8FF.toInt())
                    if (role == SyncRole.FOLLOWER && session != null) {
                        requestResync()
                    }
                }
            }
        }
    }

    private fun loadLocalMusicLibrary() {
        tvStatus.text = "Scanning local library..."
        lifecycleScope.launch(Dispatchers.IO) {
            val tracks = MusicLibraryScanner.scan(this@ListenTogetherActivity)
            launch(Dispatchers.Main) {
                trackItems.clear()
                trackItems.addAll(tracks)
                trackLabels.clear()
                trackLabels.addAll(if (tracks.isEmpty()) listOf("No music found") else tracks.map { it.displayName })
                trackAdapter.notifyDataSetChanged()

                if (tracks.isNotEmpty()) {
                    spinnerTrack.setSelection(0)
                    updateTrackMeta(tracks.first())
                    tvStatus.text = "Found ${tracks.size} local tracks"
                } else {
                    tvStatus.text = "No tracks found in /calcvault/storage/media/music"
                    tvTrackMeta.text = "Put the same song on both devices first"
                }

                pendingInvitePayload?.let {
                    pendingInvitePayload = null
                    handleIncomingInvitePayload(it)
                }
            }
        }
    }

    private fun openFilePicker() {
        val intent = android.content.Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Select Song"),
                REQUEST_CODE_FILE_PICKER
            )
        } catch (e: Exception) {
            toast("File picker not available: ${e.message}")
        }
    }

    private fun onStartOrChangeClicked() {
        when (role) {
            SyncRole.HOST -> {
                if (session == null) {
                    startHostSession()
                } else {
                    changeTrackAsHost()
                }
            }
            SyncRole.FOLLOWER -> {
                if (session == null) {
                    tvStatus.text = "Follower mode waits for host invite"
                } else {
                    requestTrackChangeAsFollower()
                }
            }
        }
    }

    private fun requestTrackChangeAsFollower() {
        val selectedTrack = selectedTrackOrNull() ?: run {
            toast("Select a local song first")
            return
        }
        val activeSession = session ?: return

        tvStatus.text = "Requesting track change..."
        lifecycleScope.launch(Dispatchers.IO) {
            val fingerprint = runCatching { MusicLibraryScanner.fingerprint(selectedTrack) }.getOrNull()
            if (fingerprint == null) {
                launch(Dispatchers.Main) { tvStatus.text = "Unable to hash selected track" }
                return@launch
            }

            val sent = sendSignal(
                SIG_CONTROL_REQUEST,
                JSONObject().apply {
                    put("sessionId", activeSession.sessionId)
                    put("action", "track_change_request")
                    put("track", trackToJson(fingerprint))
                    put("sentAtMs", System.currentTimeMillis())
                }
            )

            launch(Dispatchers.Main) {
                tvStatus.text = if (sent) {
                    "Track change request sent to host"
                } else {
                    "Failed to request track change"
                }
            }
        }
    }

    private fun startHostSession() {
        val selectedTrack = selectedTrackOrNull() ?: run {
            toast("Select a local song first")
            return
        }

        tvStatus.text = "Preparing selected track..."
        lifecycleScope.launch(Dispatchers.IO) {
            val fingerprint = runCatching { MusicLibraryScanner.fingerprint(selectedTrack) }.getOrNull()
            if (fingerprint == null) {
                launch(Dispatchers.Main) { tvStatus.text = "Failed to hash selected track" }
                return@launch
            }

            val sessionId = UUID.randomUUID().toString()
            val created = MusicSession(
                sessionId = sessionId,
                trackId = fingerprint.trackId,
                participants = listOf(localUserId, partnerUserId),
                host = localUserId,
                currentTimeMs = 0L,
                state = PlaybackState.PAUSED
            )

            session = created
            partnerAccepted = false
            loadedTrack = selectedTrack
            loadedTrackFingerprint = fingerprint
            remoteTrackFingerprint = null

            val sent = sendSignal(
                SIG_INVITE,
                JSONObject().apply {
                    put("sessionId", sessionId)
                    put("host", localUserId)
                    put("positionMs", 0)
                    put("state", PlaybackState.PAUSED.name)
                    put("sentAtMs", System.currentTimeMillis())
                    put("participants", JSONArray(created.participants))
                    put("track", trackToJson(fingerprint))
                }
            )

            launch(Dispatchers.Main) {
                preparePlayer(selectedTrack, startPositionMs = 0L, autoPlay = false)
                btnStartOrChange.text = "Change Track"
                btnPlayPause.isEnabled = true
                tvStatus.text = if (sent) {
                    "Invite sent. Waiting for ${partnerDisplayName()} to validate and join..."
                } else {
                    "Failed to send invite"
                }
            }
        }
    }

    private fun changeTrackAsHost() {
        val activeSession = session ?: return
        if (role != SyncRole.HOST) return

        val selectedTrack = selectedTrackOrNull() ?: run {
            toast("Select a track")
            return
        }

        tvStatus.text = "Switching track..."
        lifecycleScope.launch(Dispatchers.IO) {
            val fingerprint = runCatching { MusicLibraryScanner.fingerprint(selectedTrack) }.getOrNull()
            if (fingerprint == null) {
                launch(Dispatchers.Main) { tvStatus.text = "Unable to hash new track" }
                return@launch
            }

            loadedTrack = selectedTrack
            loadedTrackFingerprint = fingerprint
            remoteTrackFingerprint = null
            partnerAccepted = false
            session = activeSession.copy(trackId = fingerprint.trackId, currentTimeMs = 0L, state = PlaybackState.PAUSED)

            sendSignal(
                SIG_EVENT,
                JSONObject().apply {
                    put("sessionId", activeSession.sessionId)
                    put("event", "track_change")
                    put("state", PlaybackState.PAUSED.name)
                    put("positionMs", 0L)
                    put("sentAtMs", System.currentTimeMillis())
                    put("trackId", fingerprint.trackId)
                    put("track", trackToJson(fingerprint))
                }
            )

            launch(Dispatchers.Main) {
                preparePlayer(selectedTrack, startPositionMs = 0L, autoPlay = false)
                tvStatus.text = "Track changed. Waiting for ${partnerDisplayName()} validation..."
            }
        }
    }

    private fun handleIncomingInvitePayload(payload: JSONObject) {
        role = SyncRole.FOLLOWER
        refreshRoleUi(lockRole = true)

        if (trackItems.isEmpty()) {
            pendingInvitePayload = payload
            tvStatus.text = "Waiting for local library scan..."
            return
        }

        val remoteTrack = parseTrack(payload.optJSONObject("track"))
        val sessionId = payload.optString("sessionId")
        val host = payload.optString("host").ifBlank { partnerUserId }
        if (remoteTrack == null || sessionId.isBlank()) {
            tvStatus.text = "Invite payload invalid"
            lifecycleScope.launch(Dispatchers.IO) {
                sendSignal(
                    SIG_REJECT,
                    JSONObject().apply {
                        put("sessionId", sessionId)
                        put("reason", "invalid_invite")
                    }
                )
            }
            return
        }

        remoteTrackFingerprint = remoteTrack

        tvStatus.text = "Validating local copy against host fingerprint..."
        lifecycleScope.launch(Dispatchers.IO) {
            val localMatch = MusicLibraryScanner.findExactMatch(remoteTrack, trackItems)
            if (localMatch == null) {
                sendSignal(
                    SIG_MISMATCH,
                    JSONObject().apply {
                        put("sessionId", sessionId)
                        put("reason", "file_mismatch")
                        put("trackId", remoteTrack.trackId)
                    }
                )
                launch(Dispatchers.Main) {
                    tvStatus.text = "Session blocked: track mismatch (hash/size/duration)"
                    tvSyncState.text = "Sync: blocked"
                    tvSyncState.setTextColor(0xFFFF6B6B.toInt())
                }
                return@launch
            }

            val localFingerprint = MusicLibraryScanner.fingerprint(localMatch)
            session = MusicSession(
                sessionId = sessionId,
                trackId = remoteTrack.trackId,
                participants = listOf(localUserId, host),
                host = host,
                currentTimeMs = payload.optLong("positionMs", 0L),
                state = parseState(payload.optString("state", PlaybackState.PAUSED.name))
            )
            loadedTrack = localMatch
            loadedTrackFingerprint = localFingerprint
            partnerAccepted = true

            sendSignal(
                SIG_ACCEPT,
                JSONObject().apply {
                    put("sessionId", sessionId)
                    put("track", trackToJson(localFingerprint))
                    put("sentAtMs", System.currentTimeMillis())
                }
            )

            launch(Dispatchers.Main) {
                selectTrackInSpinner(localMatch.absolutePath)
                preparePlayer(localMatch, startPositionMs = session?.currentTimeMs ?: 0L, autoPlay = false)
                btnPlayPause.isEnabled = true
                refreshRoleUi(lockRole = true)
                tvStatus.text = "Joined session with ${partnerDisplayName()}"
                startPingLoopIfNeeded()
            }
        }
    }

    private fun handleControlSignal(signal: JSONObject) {
        val type = signal.optString("type")
        if (!type.startsWith("listen_")) return

        val from = signal.optString("from").ifBlank { partnerUserId }
        if (from != partnerUserId) return

        val payload = ListenTogetherCrypto.unwrap(signal.optJSONObject("payload"))
            ?: signal.optJSONObject("payload")
            ?: JSONObject()

        when (type) {
            SIG_INVITE -> handleIncomingInvitePayload(payload)
            SIG_ACCEPT -> onPeerAccepted(payload)
            SIG_REJECT -> tvStatus.text = "Partner rejected session"
            SIG_MISMATCH -> tvStatus.text = "Session blocked by partner (file mismatch)"
            SIG_EVENT -> onPlaybackEvent(payload)
            SIG_CONTROL_REQUEST -> onControlRequest(payload)
            SIG_RESYNC_REQUEST -> {
                if (role == SyncRole.HOST && isCurrentSession(payload)) {
                    broadcastHostSnapshot("resync")
                }
            }
            SIG_PING -> onPing(payload)
            SIG_PONG -> onPong(payload)
            SIG_VOICE_REQUEST -> {
                if (role == SyncRole.HOST && isCurrentSession(payload)) {
                    startVoiceAsHost(sendOffer = true)
                }
            }
            SIG_VOICE_OFFER -> onVoiceOffer(payload)
            SIG_VOICE_ACCEPT -> {
                tvStatus.text = "Voice channel accepted"
            }
            SIG_VOICE_END -> stopVoiceLocal(notifyRemote = false)
        }
    }

    private fun onPeerAccepted(payload: JSONObject) {
        if (role != SyncRole.HOST || !isCurrentSession(payload)) return

        val local = loadedTrackFingerprint ?: return
        val peerTrack = parseTrack(payload.optJSONObject("track")) ?: return

        if (!fingerprintMatches(local, peerTrack)) {
            tvStatus.text = "Partner accepted with mismatched track. Blocking session."
            lifecycleScope.launch(Dispatchers.IO) {
                sendSignal(
                    SIG_MISMATCH,
                    JSONObject().apply {
                        put("sessionId", session?.sessionId)
                        put("reason", "fingerprint_mismatch")
                    }
                )
            }
            return
        }

        remoteTrackFingerprint = peerTrack
        partnerAccepted = true
        tvStatus.text = "${partnerDisplayName()} joined. Synced listening is live."
        broadcastHostSnapshot("resync")
    }

    private fun onPlaybackEvent(payload: JSONObject) {
        if (!isCurrentSession(payload)) return

        val event = payload.optString("event")
        val state = parseState(payload.optString("state"))
        val positionMs = payload.optLong("positionMs", 0L)
        val sentAtMs = payload.optLong("sentAtMs", System.currentTimeMillis())
        val trackId = payload.optString("trackId").ifBlank { session?.trackId.orEmpty() }

        when (event) {
            "track_change" -> {
                val remoteTrack = parseTrack(payload.optJSONObject("track")) ?: return
                applyTrackChangeFromHost(remoteTrack)
            }
            "play", "pause", "seek", "snapshot", "resync" -> {
                if (role == SyncRole.FOLLOWER) {
                    lastHostSnapshot = HostSnapshot(
                        trackId = trackId,
                        state = state,
                        positionMs = positionMs,
                        hostSentAtMs = sentAtMs
                    )
                    applyHostState(lastHostSnapshot!!)
                }
            }
        }
    }

    private fun onControlRequest(payload: JSONObject) {
        if (role != SyncRole.HOST || !isCurrentSession(payload)) return
        if (!partnerAccepted) return

        when (payload.optString("action")) {
            "toggle" -> {
                val playing = mediaPlayer?.isPlaying == true
                if (playing) {
                    mediaPlayer?.pause()
                    broadcastHostSnapshot("pause")
                } else {
                    mediaPlayer?.start()
                    broadcastHostSnapshot("play")
                }
                updatePlayPauseLabel()
            }
            "seek" -> {
                val target = payload.optLong("positionMs", 0L)
                seekLocal(target)
                broadcastHostSnapshot("seek")
            }
            "resync" -> broadcastHostSnapshot("resync")
            "track_change_request" -> {
                val requested = parseTrack(payload.optJSONObject("track")) ?: return
                tvStatus.text = "Partner requested a track change..."
                lifecycleScope.launch(Dispatchers.IO) {
                    val localMatch = MusicLibraryScanner.findExactMatch(requested, trackItems)
                    if (localMatch == null) {
                        sendSignal(
                            SIG_MISMATCH,
                            JSONObject().apply {
                                put("sessionId", session?.sessionId)
                                put("reason", "host_missing_requested_track")
                                put("trackId", requested.trackId)
                            }
                        )
                        launch(Dispatchers.Main) {
                            tvStatus.text = "Requested track unavailable on host"
                        }
                        return@launch
                    }

                    val fingerprint = MusicLibraryScanner.fingerprint(localMatch)
                    loadedTrack = localMatch
                    loadedTrackFingerprint = fingerprint
                    remoteTrackFingerprint = requested
                    session = session?.copy(trackId = fingerprint.trackId, currentTimeMs = 0L, state = PlaybackState.PAUSED)
                    partnerAccepted = true

                    sendSignal(
                        SIG_EVENT,
                        JSONObject().apply {
                            put("sessionId", session?.sessionId)
                            put("event", "track_change")
                            put("state", PlaybackState.PAUSED.name)
                            put("positionMs", 0L)
                            put("sentAtMs", System.currentTimeMillis())
                            put("trackId", fingerprint.trackId)
                            put("track", trackToJson(fingerprint))
                        }
                    )

                    launch(Dispatchers.Main) {
                        selectTrackInSpinner(localMatch.absolutePath)
                        preparePlayer(localMatch, startPositionMs = 0L, autoPlay = false)
                        tvStatus.text = "Track changed from partner request"
                    }
                }
            }
        }
    }

    private fun applyTrackChangeFromHost(remoteTrack: TrackFingerprint) {
        tvStatus.text = "Host changed track. Validating local copy..."
        lifecycleScope.launch(Dispatchers.IO) {
            val localMatch = MusicLibraryScanner.findExactMatch(remoteTrack, trackItems)
            if (localMatch == null) {
                sendSignal(
                    SIG_MISMATCH,
                    JSONObject().apply {
                        put("sessionId", session?.sessionId)
                        put("reason", "file_mismatch")
                        put("trackId", remoteTrack.trackId)
                    }
                )
                launch(Dispatchers.Main) {
                    tvStatus.text = "Track change blocked: file mismatch"
                }
                return@launch
            }

            val localFingerprint = MusicLibraryScanner.fingerprint(localMatch)
            loadedTrack = localMatch
            loadedTrackFingerprint = localFingerprint
            remoteTrackFingerprint = remoteTrack
            session = session?.copy(trackId = remoteTrack.trackId, currentTimeMs = 0L, state = PlaybackState.PAUSED)

            sendSignal(
                SIG_ACCEPT,
                JSONObject().apply {
                    put("sessionId", session?.sessionId)
                    put("track", trackToJson(localFingerprint))
                    put("sentAtMs", System.currentTimeMillis())
                }
            )

            launch(Dispatchers.Main) {
                selectTrackInSpinner(localMatch.absolutePath)
                preparePlayer(localMatch, startPositionMs = 0L, autoPlay = false)
                tvStatus.text = "Track updated and verified"
            }
        }
    }

    private fun handlePlayPauseRequest() {
        if (mediaPlayer == null) return

        if (session == null || !inSyncedMode) {
            toggleLocalPlayPause()
            return
        }

        if (role == SyncRole.HOST) {
            toggleLocalPlayPause()
            broadcastHostSnapshot(if (mediaPlayer?.isPlaying == true) "play" else "pause")
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                sendSignal(
                    SIG_CONTROL_REQUEST,
                    JSONObject().apply {
                        put("sessionId", session?.sessionId)
                        put("action", "toggle")
                        put("sentAtMs", System.currentTimeMillis())
                    }
                )
            }
        }
    }

    private fun handleSeekRequest(targetMs: Long) {
        if (mediaPlayer == null) return

        if (session == null || !inSyncedMode) {
            seekLocal(targetMs)
            return
        }

        if (role == SyncRole.HOST) {
            seekLocal(targetMs)
            broadcastHostSnapshot("seek")
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                sendSignal(
                    SIG_CONTROL_REQUEST,
                    JSONObject().apply {
                        put("sessionId", session?.sessionId)
                        put("action", "seek")
                        put("positionMs", targetMs)
                        put("sentAtMs", System.currentTimeMillis())
                    }
                )
            }
        }
    }

    private fun requestResync() {
        if (session == null) return

        if (role == SyncRole.HOST) {
            broadcastHostSnapshot("resync")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            sendSignal(
                SIG_RESYNC_REQUEST,
                JSONObject().apply {
                    put("sessionId", session?.sessionId)
                    put("sentAtMs", System.currentTimeMillis())
                }
            )
            sendSignal(
                SIG_CONTROL_REQUEST,
                JSONObject().apply {
                    put("sessionId", session?.sessionId)
                    put("action", "resync")
                    put("sentAtMs", System.currentTimeMillis())
                }
            )
        }

        lastHostSnapshot?.let { applyHostState(it) }
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            while (isActive) {
                if (inSyncedMode && session != null) {
                    if (role == SyncRole.HOST && partnerAccepted) {
                        broadcastHostSnapshot("snapshot")
                    } else if (role == SyncRole.FOLLOWER) {
                        lastHostSnapshot?.let { applyHostState(it, updateStatus = false) }
                    }
                }
                delay(SIGNAL_INTERVAL_MS)
            }
        }
    }

    private fun startUiLoop() {
        uiJob?.cancel()
        uiJob = lifecycleScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null) {
                    val duration = player.duration.coerceAtLeast(0)
                    if (duration > 0) seekTimeline.max = duration
                    if (!userSeeking) {
                        val current = player.currentPosition.coerceAtLeast(0)
                        seekTimeline.progress = current
                        tvTime.text = "${formatMs(current.toLong())} / ${formatMs(duration.toLong())}"
                    }
                }
                updatePlayPauseLabel()
                delay(UI_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun startPingLoopIfNeeded() {
        pingJob?.cancel()
        if (role != SyncRole.FOLLOWER || session == null) return

        pingJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && session != null) {
                sendSignal(
                    SIG_PING,
                    JSONObject().apply {
                        put("sessionId", session?.sessionId)
                        put("pingSentAtMs", System.currentTimeMillis())
                    }
                )
                delay(PING_INTERVAL_MS)
            }
        }
    }

    private fun onPing(payload: JSONObject) {
        if (role != SyncRole.HOST || !isCurrentSession(payload)) return
        lifecycleScope.launch(Dispatchers.IO) {
            sendSignal(
                SIG_PONG,
                JSONObject().apply {
                    put("sessionId", session?.sessionId)
                    put("echoPingMs", payload.optLong("pingSentAtMs"))
                    put("hostNowMs", System.currentTimeMillis())
                }
            )
        }
    }

    private fun onPong(payload: JSONObject) {
        if (role != SyncRole.FOLLOWER || !isCurrentSession(payload)) return

        val t0 = payload.optLong("echoPingMs", 0L)
        if (t0 <= 0L) return
        val t3 = System.currentTimeMillis()
        val hostNow = payload.optLong("hostNowMs", t3)
        val rtt = (t3 - t0).coerceAtLeast(0L)
        val freshOffset = hostNow + (rtt / 2L) - t3
        clockOffsetMs = ((clockOffsetMs * 3L) + freshOffset) / 4L
    }

    private fun applyHostState(snapshot: HostSnapshot, updateStatus: Boolean = true) {
        val player = mediaPlayer ?: return
        val activeSession = session ?: return
        if (snapshot.trackId.isNotBlank() && snapshot.trackId != activeSession.trackId) return

        val expected = expectedPosition(snapshot)
        val current = player.currentPosition.toLong()
        val drift = expected - current

        if (abs(drift) > ListenDriftPolicy.HARD_DRIFT_MS) {
            seekLocal(expected)
        }

        when (snapshot.state) {
            PlaybackState.PLAYING -> if (!player.isPlaying) runCatching { player.start() }
            PlaybackState.PAUSED, PlaybackState.SEEKING -> if (player.isPlaying) runCatching { player.pause() }
        }

        session = activeSession.copy(
            currentTimeMs = expected,
            state = snapshot.state
        )

        if (updateStatus) {
            tvSyncState.text = "Sync drift: ${drift}ms"
            val color = if (abs(drift) <= ListenDriftPolicy.SOFT_DRIFT_MS) {
                0xFF6EEB83.toInt()
            } else if (abs(drift) <= ListenDriftPolicy.HARD_DRIFT_MS) {
                0xFFFFD166.toInt()
            } else {
                0xFFFF6B6B.toInt()
            }
            tvSyncState.setTextColor(color)
        }
    }

    private fun expectedPosition(snapshot: HostSnapshot): Long {
        val localEquivalentSentAt = snapshot.hostSentAtMs - clockOffsetMs
        val transitMs = (System.currentTimeMillis() - localEquivalentSentAt).coerceAtLeast(0L)
        val base = when (snapshot.state) {
            PlaybackState.PLAYING -> snapshot.positionMs + transitMs
            PlaybackState.PAUSED, PlaybackState.SEEKING -> snapshot.positionMs
        }
        val duration = mediaPlayer?.duration?.toLong()?.coerceAtLeast(0L) ?: Long.MAX_VALUE
        return base.coerceAtLeast(0L).coerceAtMost(duration)
    }

    private fun broadcastHostSnapshot(event: String) {
        if (role != SyncRole.HOST || !inSyncedMode) return
        if (!partnerAccepted) return
        val activeSession = session ?: return
        val player = mediaPlayer ?: return

        val state = currentPlaybackState()
        val pos = player.currentPosition.toLong().coerceAtLeast(0L)
        session = activeSession.copy(currentTimeMs = pos, state = state)

        val trackId = loadedTrackFingerprint?.trackId ?: activeSession.trackId

        lifecycleScope.launch(Dispatchers.IO) {
            sendSignal(
                SIG_EVENT,
                JSONObject().apply {
                    put("sessionId", activeSession.sessionId)
                    put("event", event)
                    put("state", state.name)
                    put("positionMs", pos)
                    put("sentAtMs", System.currentTimeMillis())
                    put("trackId", trackId)
                    loadedTrackFingerprint?.let { put("track", trackToJson(it)) }
                }
            )
        }
    }

    private fun currentPlaybackState(): PlaybackState {
        val player = mediaPlayer ?: return PlaybackState.PAUSED
        return if (player.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
    }

    private fun preparePlayer(track: LocalMusicTrack, startPositionMs: Long, autoPlay: Boolean) {
        releasePlayer()

        pendingSeekOnPreparedMs = startPositionMs
        pendingStartOnPrepared = autoPlay

        mediaPlayer = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnPreparedListener { mp ->
                val duration = mp.duration.coerceAtLeast(0)
                seekTimeline.max = duration.coerceAtLeast(1)
                seekLocal(pendingSeekOnPreparedMs)
                if (pendingStartOnPrepared) {
                    runCatching { mp.start() }
                }
                mp.setVolume(musicVolume, musicVolume)
                tvTime.text = "${formatMs(mp.currentPosition.toLong())} / ${formatMs(duration.toLong())}"
                updatePlayPauseLabel()
            }
            setOnCompletionListener {
                updatePlayPauseLabel()
                if (role == SyncRole.HOST && session != null && inSyncedMode) {
                    broadcastHostSnapshot("pause")
                }
            }
            setOnErrorListener { _, _, _ ->
                tvStatus.text = "Playback error"
                true
            }

            runCatching {
                setDataSource(track.absolutePath)
                prepareAsync()
            }.onFailure {
                tvStatus.text = "Unable to open selected track"
            }
        }
    }

    private fun seekLocal(targetMs: Long) {
        val player = mediaPlayer ?: return
        val duration = player.duration.toLong().coerceAtLeast(0L)
        val safe = targetMs.coerceAtLeast(0L).coerceAtMost(duration)
        runCatching { player.seekTo(safe.toInt()) }
    }

    private fun toggleLocalPlayPause() {
        val player = mediaPlayer ?: return
        runCatching {
            if (player.isPlaying) player.pause() else player.start()
        }
        updatePlayPauseLabel()
    }

    private fun updatePlayPauseLabel() {
        val playing = mediaPlayer?.isPlaying == true
        btnPlayPause.text = if (playing) "Pause" else "Play"
    }

    private fun onTalkClicked() {
        if (session == null) {
            tvStatus.text = "Start or join a listen session first"
            return
        }

        if (voiceActive) {
            stopVoiceLocal(notifyRemote = true)
            return
        }

        if (!hasRecordAudioPermission()) {
            pendingVoiceStart = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
            return
        }

        if (role == SyncRole.HOST) {
            startVoiceAsHost(sendOffer = true)
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val sent = sendSignal(
                    SIG_VOICE_REQUEST,
                    JSONObject().apply {
                        put("sessionId", session?.sessionId)
                        put("sentAtMs", System.currentTimeMillis())
                    }
                )
                launch(Dispatchers.Main) {
                    tvStatus.text = if (sent) "Voice request sent to host" else "Failed to send voice request"
                }
            }
        }
    }

    private fun startVoiceAsHost(sendOffer: Boolean) {
        if (voiceActive) return

        if (session == null) {
            tvStatus.text = "Start a listen session before voice"
            return
        }

        val engine = ensureVoiceEngine()
        engine.startCall()
        voiceActive = true
        btnTalk.text = "Stop Talk"

        if (!sendOffer) return

        val callerIp = engine.getLocalIp()
        lifecycleScope.launch(Dispatchers.IO) {
            sendSignal(
                SIG_VOICE_OFFER,
                JSONObject().apply {
                    put("sessionId", session?.sessionId)
                    put("callerIp", callerIp)
                    put("port", SocketCallEngine.callPort())
                    put("sentAtMs", System.currentTimeMillis())
                }
            )
        }

        tvStatus.text = "Voice offer sent"
    }

    private fun onVoiceOffer(payload: JSONObject) {
        if (!isCurrentSession(payload)) return
        if (voiceActive) return
        if (!hasRecordAudioPermission()) {
            tvStatus.text = "Voice offer received but microphone permission is missing"
            return
        }

        val callerIp = payload.optString("callerIp")
        if (callerIp.isBlank()) return

        val engine = ensureVoiceEngine()
        engine.answerCall(callerIp)
        voiceActive = true
        btnTalk.text = "Stop Talk"

        lifecycleScope.launch(Dispatchers.IO) {
            sendSignal(
                SIG_VOICE_ACCEPT,
                JSONObject().apply {
                    put("sessionId", session?.sessionId)
                    put("sentAtMs", System.currentTimeMillis())
                }
            )
        }

        tvStatus.text = "Voice channel connecting..."
    }

    private fun stopVoiceLocal(notifyRemote: Boolean) {
        voiceEngine?.endCall()
        voiceActive = false
        btnTalk.text = "Start Talk"

        if (notifyRemote) {
            lifecycleScope.launch(Dispatchers.IO) {
                sendSignal(
                    SIG_VOICE_END,
                    JSONObject().apply {
                        put("sessionId", session?.sessionId)
                        put("sentAtMs", System.currentTimeMillis())
                    }
                )
            }
        }
    }

    private fun ensureVoiceEngine(): SocketCallEngine {
        val existing = voiceEngine
        if (existing != null) return existing

        val created = SocketCallEngine(this).apply {
            suppressInternalLogging = true
            setRemotePlaybackVolume(voiceVolume)
            onStateChanged = { state ->
                runOnUiThread {
                    when (state) {
                        SocketCallEngine.ConnectionState.CONNECTING -> tvStatus.text = "Voice connecting..."
                        SocketCallEngine.ConnectionState.CONNECTED -> tvStatus.text = "Voice connected"
                        SocketCallEngine.ConnectionState.DISCONNECTED -> {
                            voiceActive = false
                            btnTalk.text = "Start Talk"
                            tvStatus.text = "Voice disconnected"
                        }
                        SocketCallEngine.ConnectionState.FAILED -> {
                            voiceActive = false
                            btnTalk.text = "Start Talk"
                            tvStatus.text = "Voice failed"
                        }
                        SocketCallEngine.ConnectionState.INIT -> Unit
                    }
                }
            }
            onError = { err ->
                runOnUiThread {
                    tvStatus.text = "Voice error: $err"
                }
            }
        }

        voiceEngine = created
        return created
    }

    private suspend fun sendSignal(type: String, payload: JSONObject): Boolean {
        val wrapped = ListenTogetherCrypto.wrap(payload)
        val extras = JSONObject().apply {
            put("payload", wrapped)
            payload.optString("sessionId").takeIf { it.isNotBlank() }?.let {
                put("sessionId", it)
            }
        }
        return networkEngine.sendControlSignal(type, extras)
    }

    private fun isCurrentSession(payload: JSONObject): Boolean {
        val sessionId = payload.optString("sessionId")
        val activeId = session?.sessionId
        return !activeId.isNullOrBlank() && activeId == sessionId
    }

    private fun refreshRoleUi(lockRole: Boolean) {
        spinnerRole.isEnabled = !lockRole
        if (role == SyncRole.HOST) {
            spinnerRole.setSelection(0)
            btnStartOrChange.visibility = View.VISIBLE
            btnStartOrChange.text = if (session == null) "Start Listen Session" else "Change Track"
        } else {
            spinnerRole.setSelection(1)
            btnStartOrChange.visibility = View.VISIBLE
            btnStartOrChange.text = if (session == null) "Waiting for Host" else "Request Track Change"
        }
    }

    private fun selectedTrackOrNull(): LocalMusicTrack? {
        if (trackItems.isEmpty()) return null
        return trackItems.getOrNull(spinnerTrack.selectedItemPosition)
    }

    private fun updateTrackMeta(track: LocalMusicTrack) {
        val duration = formatMs(track.durationMs)
        val mb = "%.2f".format(track.sizeBytes / (1024f * 1024f))
        tvTrackMeta.text = "${track.fileName}\n$duration - $mb MB"
        updateArtwork(track)
    }

    private fun updateArtwork(track: LocalMusicTrack) {
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(track.absolutePath)
            val embedded = retriever.embeddedPicture
            if (embedded != null && embedded.isNotEmpty()) {
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }

        if (bitmap != null) {
            ivArtwork.setImageBitmap(bitmap)
        } else {
            ivArtwork.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun parseState(raw: String): PlaybackState {
        return try {
            PlaybackState.valueOf(raw)
        } catch (_: Exception) {
            PlaybackState.PAUSED
        }
    }

    private fun trackToJson(track: TrackFingerprint): JSONObject {
        return JSONObject().apply {
            put("trackId", track.trackId)
            put("fileName", track.fileName)
            put("sha256", track.sha256)
            put("sizeBytes", track.sizeBytes)
            put("durationMs", track.durationMs)
            put("path", track.path)
        }
    }

    private fun parseTrack(json: JSONObject?): TrackFingerprint? {
        if (json == null) return null
        val trackId = json.optString("trackId").ifBlank { json.optString("sha256") }
        val sha = json.optString("sha256").ifBlank { trackId }
        val size = json.optLong("sizeBytes", -1L)
        val duration = json.optLong("durationMs", -1L)
        if (trackId.isBlank() || sha.isBlank() || size < 0L || duration < 0L) return null

        return TrackFingerprint(
            trackId = trackId,
            fileName = json.optString("fileName", "unknown"),
            sha256 = sha,
            sizeBytes = size,
            durationMs = duration,
            path = json.optString("path", "")
        )
    }

    private fun fingerprintMatches(a: TrackFingerprint, b: TrackFingerprint): Boolean {
        if (!a.sha256.equals(b.sha256, ignoreCase = true)) return false
        if (a.sizeBytes != b.sizeBytes) return false
        return abs(a.durationMs - b.durationMs) <= ListenDriftPolicy.DURATION_MATCH_TOLERANCE_MS
    }

    private fun selectTrackInSpinner(path: String) {
        val idx = trackItems.indexOfFirst { it.absolutePath == path }
        if (idx >= 0) {
            spinnerTrack.setSelection(idx)
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(0, 18, 0, 8)
        }
    }

    private fun sliderLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFFB0B0B0.toInt())
            setPadding(0, 8, 0, 4)
        }
    }

    private fun simpleSeekListener(onChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && pendingVoiceStart) {
                pendingVoiceStart = false
                onTalkClicked()
            } else if (!granted) {
                pendingVoiceStart = false
                toast("Microphone permission is required for talk mode")
            }
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun partnerDisplayName(): String {
        return SessionManager.partnerNickname.ifBlank { partnerUserId.ifBlank { "Partner" } }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_FILE_PICKER && resultCode == android.app.Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    val fileName = uri.lastPathSegment ?: "Unknown"
                    val absolutePath = uri.toString()
                    // Add the selected file as a new track
                    val track = LocalMusicTrack(
                        fileName = fileName,
                        absolutePath = absolutePath,
                        sizeBytes = 0L,
                        durationMs = 0L,
                        displayName = fileName
                    )

                    trackItems.add(track)
                    trackLabels.add(fileName)
                    trackAdapter.notifyDataSetChanged()

                    // Select the newly added track
                    spinnerTrack.setSelection(trackItems.indexOf(track))
                    updateTrackMeta(track)

                    tvStatus.text = "Selected: $fileName"
                } catch (e: Exception) {
                    toast("Error selecting file: ${e.message}")
                }
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        uiJob?.cancel()
        pingJob?.cancel()
        stopVoiceLocal(notifyRemote = false)
        releasePlayer()
        IncomingListenLauncher.clear(session?.sessionId)
        networkEngine.disconnect()
        ThemeApplicator.detach(this)
    }
}
