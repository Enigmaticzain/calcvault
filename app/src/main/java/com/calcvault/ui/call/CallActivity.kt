package com.calcvault.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.calcvault.call.CallEngine
import com.calcvault.call.CallForegroundService
import com.calcvault.databinding.ActivityCallBinding
import com.calcvault.emotional.EmotionalAnimationEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.USBStorageEngine
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.*
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * CallActivity
 *
 * Full VoIP UI for Secure Voice and Video calls.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private lateinit var callEngine: CallEngine
    private lateinit var networkEngine: NetworkMessageEngine
    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var themeEngine: ThemeEngine
    private lateinit var animEngine: EmotionalAnimationEngine

    private var callStartTime = 0L
    private var timerJob: Job? = null
    private var isVideo = false
    private var activeRouteDisclosure = ""
    private var localUserId = "zain"
    private var partnerUserId = "sanu"
    private var incomingCallLaunch = false
    private var pendingPermissionAction: (() -> Unit)? = null
    private var localVideoRenderer: SurfaceViewRenderer? = null
    private var remoteVideoRenderer: SurfaceViewRenderer? = null
    private var callForegroundStarted = false

    companion object {
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
        private const val REQUEST_CALL_PERMISSIONS = 4021
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeApplicator.applyActive(this)

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
        callEngine = CallEngine(this, storageEngine, messageDB)
        callEngine.attachSignaling(networkEngine, partnerUserId)
        themeEngine = ThemeEngine(this)
        animEngine = EmotionalAnimationEngine(this, binding.root)

        isVideo = intent.getBooleanExtra("is_video", false)
        val isIncoming = intent.getBooleanExtra("is_incoming", false)
        incomingCallLaunch = isIncoming

        setupUI()
        observeCallState()
        observeControlSignals()
        callEngine.onWebRtcManagerCreated = { manager -> runOnUiThread { setupWebRtcRenderers(manager.eglBaseContext) } }
        callEngine.onLocalVideoTrack = { track ->
            runOnUiThread { localVideoRenderer?.let { track.addSink(it); it.visibility = View.VISIBLE } }
        }
        callEngine.onRemoteVideoTrack = { track ->
            runOnUiThread { remoteVideoRenderer?.let { track.addSink(it); it.visibility = View.VISIBLE } }
        }
        callEngine.onCallRouteDisclosure = { disclosure ->
            activeRouteDisclosure = disclosure
            runOnUiThread { binding.tvCallStatus.text = disclosure }
        }

        if (isIncoming) {
            val callId = intent.getStringExtra("call_id") ?: ""
            callEngine.onIncomingCall(
                callId = callId,
                fromUser = intent.getStringExtra("from_user") ?: partnerUserId,
                type = if (isVideo) CallEngine.CallType.VIDEO else CallEngine.CallType.VOICE,
                localUser = localUserId
            )
            CallSignalInbox.drain(callId).forEach(callEngine::handleIncomingSignal)
            renderIncomingRinging()
        } else {
            requireCallPermissions {
                callEngine.startCall(
                    localUserId,
                    partnerUserId,
                    if (isVideo) CallEngine.CallType.VIDEO else CallEngine.CallType.VOICE
                )
            }
            renderOutgoingRinging()
        }
    }

    private fun setupUI() {
        binding.tvPartnerName.text = SessionManager.partnerNickname.ifBlank { SessionManager.partnerUserId.ifBlank { "Secure Partner" } }

        binding.btnAnswer.setOnClickListener { requireCallPermissions { callEngine.answerCall() } }
        binding.btnDecline.setOnClickListener {
            binding.tvCallStatus.text = "Declining call..."
            callEngine.declineCall()
        }
        binding.btnEnd.setOnClickListener {
            binding.tvCallStatus.text = "Ending call..."
            callEngine.endCall()
        }

        binding.btnMute.setOnClickListener {
            callEngine.toggleMute()
            val isMuted = callEngine.currentCall?.isMuted == true
            binding.btnMute.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (isMuted) com.calcvault.R.color.cv_danger else com.calcvault.R.color.cv_button_neutral
            )
        }

        binding.btnSpeaker.setOnClickListener {
            callEngine.toggleSpeaker()
            val isOn = callEngine.currentCall?.isSpeakerOn == true
            binding.btnSpeaker.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (isOn) com.calcvault.R.color.cv_accent else com.calcvault.R.color.cv_button_neutral
            )
        }

        // Recording is disabled until its consented, encrypted-storage flow is implemented.
        binding.btnRecord.visibility = View.GONE
        binding.layoutRecording.visibility = View.GONE

        binding.btnStartRecording.setOnClickListener {
            val btn = it as Button
            if (callEngine.currentCall?.isRecording == true) {
                callEngine.stopRecording()
                btn.text = "Start Recording"
                btn.backgroundTintList = ContextCompat.getColorStateList(this, com.calcvault.R.color.cv_danger)
            } else {
                val target = when {
                    binding.rbMine.isChecked -> CallEngine.RecordingTarget.MINE
                    binding.rbPartner.isChecked -> CallEngine.RecordingTarget.PARTNER
                    else -> CallEngine.RecordingTarget.BOTH
                }
                callEngine.startRecording(target)
                btn.text = "Stop Recording"
                btn.backgroundTintList = ContextCompat.getColorStateList(this, com.calcvault.R.color.cv_button_neutral)
            }
        }
    }

    private fun requireCallPermissions(onGranted: () -> Unit) {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (isVideo) add(Manifest.permission.CAMERA)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onGranted()
            return
        }
        pendingPermissionAction = onGranted
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_CALL_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CALL_PERMISSIONS) return
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) {
            action?.invoke()
        } else {
            binding.tvCallStatus.text = "Microphone/camera permission was denied. Call not started."
            if (incomingCallLaunch) callEngine.declineCall()
        }
    }

    private fun setupWebRtcRenderers(eglContext: EglBase.Context) {
        if (!isVideo || remoteVideoRenderer != null) return
        val root = binding.root
        val remote = SurfaceViewRenderer(this).apply {
            init(eglContext, null)
            setEnableHardwareScaler(true)
            visibility = View.GONE
        }
        val remoteParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )
        root.addView(remote, 0, remoteParams)
        remoteVideoRenderer = remote

        val local = SurfaceViewRenderer(this).apply {
            init(eglContext, null)
            setEnableHardwareScaler(true)
            setMirror(true)
            visibility = View.GONE
        }
        val localParams = ConstraintLayout.LayoutParams(dp(132), dp(176)).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = dp(84)
            marginEnd = dp(16)
        }
        root.addView(local, 1, localParams)
        localVideoRenderer = local
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderIncomingRinging() {
        binding.tvCallStatus.text = "Incoming ${if (isVideo) "Video" else "Voice"} Call..."
        binding.rowAnswerDecline.visibility = View.VISIBLE
        binding.layoutActiveControls.visibility = View.GONE
        animEngine.startCallPulse(binding.avatarCard)
    }

    private fun renderOutgoingRinging() {
        binding.tvCallStatus.text = if (isVideo) "Requesting video call..." else "Calling..."
        binding.rowAnswerDecline.visibility = View.VISIBLE
        binding.btnAnswer.visibility = View.GONE
        binding.layoutActiveControls.visibility = View.GONE
    }

    private fun renderConnecting() {
        binding.tvCallStatus.text = "Connecting secure transport..."
        binding.rowAnswerDecline.visibility = View.GONE
        binding.layoutActiveControls.visibility = View.GONE
    }

    private fun renderActiveCall() {
        val mediaStatus = if (isVideo) "Video call active" else "Voice call active"
        val routeStatus = activeRouteDisclosure.ifBlank { "Route details unavailable" }
        binding.tvCallStatus.text = "$mediaStatus using DTLS-SRTP. Peer identity is not verified. $routeStatus"
        binding.rowAnswerDecline.visibility = View.GONE
        binding.layoutActiveControls.visibility = View.VISIBLE
        binding.tvTimer.visibility = View.VISIBLE
        binding.waveformView.visibility = View.VISIBLE
        binding.waveformView.startAnimating()
        if (timerJob?.isActive != true) startCallTimer()
        animEngine.stopCallPulse(binding.avatarCard)
    }

    private fun renderCallEnded() {
        timerJob?.cancel()
        binding.waveformView.stopAnimating()
        val currentText = binding.tvCallStatus.text.toString()
        if (!currentText.contains("Failed", ignoreCase = true) && !currentText.contains("declined", ignoreCase = true) &&
            !currentText.contains("timed out", ignoreCase = true) &&
            !currentText.contains("No answer", ignoreCase = true)
        ) {
            binding.tvCallStatus.text = "Call Ended"
        }
        lifecycleScope.launch {
            delay(1500)
            finish()
        }
    }

    private fun renderCallFailed() {
        timerJob?.cancel()
        binding.waveformView.stopAnimating()
        val currentText = binding.tvCallStatus.text.toString()
        if (currentText.isBlank() || currentText.contains("Calling", ignoreCase = true) || currentText.contains("Ringing", ignoreCase = true)) {
            binding.tvCallStatus.text = "Call Failed"
        }
        lifecycleScope.launch {
            delay(2000)
            finish()
        }
    }

    private fun observeCallState() {
        callEngine.onCallStateChanged = { state ->
            runOnUiThread {
                when (state) {
                    CallEngine.CallState.OUTGOING_RINGING -> renderOutgoingRinging()
                    CallEngine.CallState.INCOMING_RINGING -> renderIncomingRinging()
                    CallEngine.CallState.CONNECTING -> renderConnecting()
                    CallEngine.CallState.ACTIVE -> {
                        renderActiveCall()
                        startCallForegroundService()
                    }
                    CallEngine.CallState.ENDED -> {
                        stopCallForegroundService()
                        renderCallEnded()
                    }
                    CallEngine.CallState.FAILED -> {
                        stopCallForegroundService()
                        renderCallFailed()
                    }
                    else -> {}
                }
            }
        }
        callEngine.onCallError = { error ->
            runOnUiThread {
                binding.tvCallStatus.text = error
            }
        }
    }

    private fun observeControlSignals() {
        callEngine.attachSignalHandler()
    }

    private fun startCallForegroundService() {
        if (callForegroundStarted) return
        runCatching {
            val serviceIntent = android.content.Intent(this, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START
                putExtra(CallForegroundService.EXTRA_VIDEO, isVideo)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            callForegroundStarted = true
        }.onFailure { error ->
            binding.tvCallStatus.text = "Call is active, but background-call service could not start: ${error.message ?: "unknown error"}"
        }
    }

    private fun stopCallForegroundService() {
        if (!callForegroundStarted) return
        callForegroundStarted = false
        runCatching {
            startService(android.content.Intent(this, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_STOP
            })
        }
    }

    private fun startCallTimer() {
        callStartTime = SystemClock.elapsedRealtime()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - callStartTime
                val m = (elapsed / 60000) % 60
                val s = (elapsed / 1000) % 60
                binding.tvTimer.text = String.format("%02d:%02d", m, s)
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        val callId = intent.getStringExtra("call_id")
        IncomingCallLauncher.clear(callId)
        CallSignalInbox.discard(callId)
        stopCallForegroundService()
        callEngine.destroy()
        localVideoRenderer?.release()
        remoteVideoRenderer?.release()
        networkEngine.disconnect()
        ThemeApplicator.detach(this)
    }
}
