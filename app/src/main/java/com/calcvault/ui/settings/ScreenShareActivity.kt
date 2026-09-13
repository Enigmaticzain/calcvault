package com.calcvault.ui.call

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.call.ScreenShareState
import com.calcvault.call.WatchIndicator
import com.calcvault.call.webrtc.ScreenShareEngine
import com.calcvault.databinding.ActivityScreenshareBinding
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.USBStorageEngine
import com.calcvault.utils.SessionManager
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import kotlin.math.max

class ScreenShareActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROLE = "screen_share_role"
        const val EXTRA_SESSION_ID = "screen_share_session_id"
        const val EXTRA_REMOTE_USER = "screen_share_remote_user"

        const val ROLE_SHARER = "SHARER"
        const val ROLE_VIEWER = "VIEWER"

        private const val RC_SCREEN_CAPTURE = 551
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
    }

    private lateinit var binding: ActivityScreenshareBinding
    private lateinit var screenShareEngine: ScreenShareEngine
    private lateinit var networkEngine: NetworkMessageEngine
    private val eglBase = EglBase.create()

    private var role: String = ROLE_VIEWER
    private var partnerId: String = ""
    private var sessionId: String = ""
    private var shareState: ScreenShareState = ScreenShareState.REQUESTED
    private var watchIndicator = WatchIndicator(
        isActive = false,
        position = Point(60, 160),
        isDraggable = true
    )

    private var remoteWindowWidth = 0
    private var remoteWindowHeight = 0
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SECURE
        )
        ThemeApplicator.applyActive(this)

        binding = ActivityScreenshareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        role = intent.getStringExtra(EXTRA_ROLE).orEmpty().ifBlank { ROLE_VIEWER }
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        partnerId = intent.getStringExtra(EXTRA_REMOTE_USER)
            .orEmpty()
            .ifBlank { SessionManager.partnerUserId }

        val storageEngine = USBStorageEngine.getInstance(this)
        val messageDB = AppendOnlyMessageDB(storageEngine)
        networkEngine = NetworkMessageEngine(this, messageDB, storageEngine)
        networkEngine.initialize(
            SessionManager.localUserId,
            partnerId,
            SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING }
        )

        screenShareEngine = ScreenShareEngine(this, networkEngine, eglBase.eglBaseContext)
        setupViews()
        setupEngineListeners()

        if (role == ROLE_SHARER) {
            binding.statusText.text = "Waiting for capture permission..."
            requestScreenSharePermission()
        } else {
            shareState = ScreenShareState.ACTIVE
            binding.statusText.text = "Live Assist requested. Connecting..."
            screenShareEngine.initViewer(partnerId = partnerId, sessionId = sessionId)
            showViewerWindow(active = false)
        }
    }

    private fun setupViews() {
        binding.remoteVideoView.init(eglBase.eglBaseContext, null)
        binding.remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        binding.remoteVideoView.setEnableHardwareScaler(true)

        binding.localVideoView.init(eglBase.eglBaseContext, null)
        binding.localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        binding.localVideoView.setEnableHardwareScaler(true)
        binding.localVideoView.setZOrderMediaOverlay(true)

        setupViewerMiniWindow()
        setupEyesIndicator()

        binding.btnPauseShare.setOnClickListener {
            shareState = ScreenShareState.PAUSED
            binding.pauseOverlay.visibility = View.VISIBLE
            screenShareEngine.pauseSharing()
            updateControls()
        }

        binding.btnResumeShare.setOnClickListener {
            shareState = ScreenShareState.ACTIVE
            binding.pauseOverlay.visibility = View.GONE
            screenShareEngine.resumeSharing()
            updateControls()
        }

        binding.btnEndShare.setOnClickListener {
            shareState = ScreenShareState.ENDED
            screenShareEngine.endSession(notifyPeer = true)
        }

        binding.btnCloseRemote.setOnClickListener {
            screenShareEngine.endSession(notifyPeer = true)
        }

        binding.btnMinimizeRemote.setOnClickListener {
            val isVisible = binding.remoteVideoView.visibility == View.VISIBLE
            binding.remoteVideoView.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.btnMinimizeRemote.text = if (isVisible) "+" else "−"
        }

        updateControls()
    }

    private fun setupEngineListeners() {
        screenShareEngine.onLocalTrackReady = { localTrack ->
            runOnUiThread {
                localTrack.addSink(binding.localVideoView)
                binding.localVideoView.visibility = View.VISIBLE
                if (role == ROLE_SHARER) {
                    showWatchIndicator(true)
                }
            }
        }

        screenShareEngine.onRemoteStream = { videoTrack, _ ->
            runOnUiThread {
                videoTrack.addSink(binding.remoteVideoView)
                binding.statusText.visibility = View.GONE
                shareState = ScreenShareState.ACTIVE
                binding.pauseOverlay.visibility = View.GONE
                showViewerWindow(active = role == ROLE_VIEWER)
                updateControls()
            }
        }

        screenShareEngine.onPauseStateChanged = { paused ->
            runOnUiThread {
                shareState = if (paused) ScreenShareState.PAUSED else ScreenShareState.ACTIVE
                binding.pauseOverlay.visibility = if (paused) View.VISIBLE else View.GONE
                binding.statusText.visibility = if (paused) View.VISIBLE else View.GONE
                if (paused) binding.statusText.text = "Sharing paused"
                updateControls()
            }
        }

        screenShareEngine.onSessionEnded = {
            runOnUiThread { finish() }
        }

        screenShareEngine.onError = { message ->
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViewerMiniWindow() {
        val display = resources.displayMetrics
        remoteWindowWidth = max((display.widthPixels * 0.25f).toInt(), 260)
        remoteWindowHeight = max((display.heightPixels * 0.25f).toInt(), 180)

        val params = binding.remoteContainer.layoutParams as FrameLayout.LayoutParams
        params.width = remoteWindowWidth
        params.height = remoteWindowHeight
        params.rightMargin = 28
        params.topMargin = 140
        binding.remoteContainer.layoutParams = params

        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    remoteWindowWidth = (remoteWindowWidth * scaleFactor).toInt().coerceIn(220, display.widthPixels - 64)
                    remoteWindowHeight = (remoteWindowHeight * scaleFactor).toInt().coerceIn(160, display.heightPixels - 180)
                    val lp = binding.remoteContainer.layoutParams as FrameLayout.LayoutParams
                    lp.width = remoteWindowWidth
                    lp.height = remoteWindowHeight
                    binding.remoteContainer.layoutParams = lp
                    return true
                }
            }
        )

        var startX = 0f
        var startY = 0f
        binding.remoteContainer.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = view.x - event.rawX
                    startY = view.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleGestureDetector.isInProgress) {
                        val maxX = binding.shareRoot.width - view.width
                        val maxY = binding.shareRoot.height - view.height
                        view.x = (event.rawX + startX).coerceIn(0f, maxX.toFloat())
                        view.y = (event.rawY + startY).coerceIn(0f, maxY.toFloat())
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupEyesIndicator() {
        val blink = ObjectAnimator.ofFloat(binding.eyesIndicator, View.SCALE_Y, 1f, 0.55f, 1f).apply {
            duration = 1400L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        blink.start()

        var startX = 0f
        var startY = 0f
        binding.eyesIndicator.setOnTouchListener { view, event ->
            if (!watchIndicator.isDraggable) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = view.x - event.rawX
                    startY = view.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = binding.shareRoot.width - view.width
                    val maxY = binding.shareRoot.height - view.height
                    val x = (event.rawX + startX).coerceIn(0f, maxX.toFloat())
                    val y = (event.rawY + startY).coerceIn(0f, maxY.toFloat())
                    view.x = x
                    view.y = y
                    binding.eyesLabel.x = x - 12f
                    binding.eyesLabel.y = y + view.height + 8f
                    watchIndicator = watchIndicator.copy(position = Point(x.toInt(), y.toInt()))
                    true
                }
                else -> false
            }
        }
    }

    private fun updateControls() {
        val isSharer = role == ROLE_SHARER
        binding.controls.visibility = if (isSharer) View.VISIBLE else View.GONE
        binding.btnPauseShare.visibility =
            if (isSharer && shareState == ScreenShareState.ACTIVE) View.VISIBLE else View.GONE
        binding.btnResumeShare.visibility =
            if (isSharer && shareState == ScreenShareState.PAUSED) View.VISIBLE else View.GONE
    }

    private fun showViewerWindow(active: Boolean) {
        if (role != ROLE_VIEWER) return
        binding.remoteContainer.visibility = if (active) View.VISIBLE else View.GONE
        binding.localVideoView.visibility = View.GONE
        binding.eyesIndicator.visibility = View.GONE
        binding.eyesLabel.visibility = View.GONE
    }

    private fun showWatchIndicator(isActive: Boolean) {
        watchIndicator = watchIndicator.copy(isActive = isActive)
        val visible = isActive && role == ROLE_SHARER
        binding.eyesIndicator.visibility = if (visible) View.VISIBLE else View.GONE
        binding.eyesLabel.visibility = if (visible) View.VISIBLE else View.GONE
        binding.eyesLabel.text = "Your partner is watching"
    }

    private fun requestScreenSharePermission() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), RC_SCREEN_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_SCREEN_CAPTURE) return

        if (resultCode == Activity.RESULT_OK && data != null) {
            shareState = ScreenShareState.ACTIVE
            binding.statusText.text = "Starting secure Live Assist..."
            screenShareEngine.initSharer(
                partnerId = partnerId,
                mediaProjectionIntent = data,
                sessionId = sessionId
            )
            showWatchIndicator(true)
            updateControls()
        } else {
            Toast.makeText(this, "Screen share permission is required.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (
            role == ROLE_SHARER &&
            shareState != ScreenShareState.REQUESTED &&
            !isChangingConfigurations &&
            !isFinishing
        ) {
            // Safety-first policy: no hidden background sharing.
            screenShareEngine.endSession(notifyPeer = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenShareEngine.release()
        binding.localVideoView.release()
        binding.remoteVideoView.release()
        networkEngine.disconnect()
        eglBase.release()
    }
}
