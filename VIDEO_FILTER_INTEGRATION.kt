package com.calcvault.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.calcvault.call.CallEngine
import com.calcvault.call.filters.FilterControlPanel
import com.calcvault.call.filters.FilterSyncEngine
import com.calcvault.call.filters.VideoFilterEngine
import com.calcvault.databinding.ActivityCallBinding
import com.calcvault.emotional.EmotionalAnimationEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.USBStorageEngine
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * CallActivity - INTEGRATION EXAMPLE
 *
 * Shows how to integrate VideoFilterEngine and FilterSyncEngine
 * into existing CallActivity with minimal changes.
 *
 * Key additions:
 * 1. VideoFilterEngine initialization
 * 2. FilterSyncEngine initialization
 * 3. FilterControlPanel setup
 * 4. Filter effect handlers
 * 5. Remote filter effect reception
 */
class CallActivityWithFilters : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private lateinit var callEngine: CallEngine
    private lateinit var networkEngine: NetworkMessageEngine
    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var themeEngine: ThemeEngine
    private lateinit var animEngine: EmotionalAnimationEngine

    // NEW: Filter system components
    private lateinit var filterEngine: VideoFilterEngine
    private lateinit var filterSyncEngine: FilterSyncEngine
    private lateinit var filterControlPanel: FilterControlPanel

    private var callStartTime = 0L
    private var timerJob: Job? = null
    private var isVideo = false
    private var localUserId = "zain"
    private var partnerUserId = "sanu"

    companion object {
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
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
        callEngine.attachSignaling(networkEngine)
        themeEngine = ThemeEngine(this)
        animEngine = EmotionalAnimationEngine(this, binding.root)

        // NEW: Initialize filter system
        isVideo = intent.getBooleanExtra("is_video", false)
        if (isVideo) {
            initializeFilterSystem()
        }

        val isIncoming = intent.getBooleanExtra("is_incoming", false)

        setupUI()
        observeCallState()
        observeControlSignals()

        if (isIncoming) {
            callEngine.onIncomingCall(
                callId = intent.getStringExtra("call_id") ?: "",
                fromUser = intent.getStringExtra("from_user") ?: partnerUserId,
                type = if (isVideo) CallEngine.CallType.VIDEO else CallEngine.CallType.VOICE,
                callerIp = intent.getStringExtra("caller_ip") ?: "",
                localUser = localUserId
            )
            renderIncomingRinging()
        } else {
            callEngine.startCall(localUserId, partnerUserId, if (isVideo) CallEngine.CallType.VIDEO else CallEngine.CallType.VOICE)
            renderOutgoingRinging()
        }

        if (isVideo) startCamera()
    }

    // NEW: Initialize filter system
    private fun initializeFilterSystem() {
        filterEngine = VideoFilterEngine(this, binding.videoOverlay)
        filterSyncEngine = FilterSyncEngine(networkEngine)
        filterControlPanel = FilterControlPanel(this)

        // Add filter panel to layout
        binding.root.addView(
            filterControlPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 100, 16, 0)
            }
        )

        // Setup filter panel callbacks
        filterControlPanel.onEmojiTrigger = { emoji ->
            filterEngine.triggerEmojiShower(emoji, 1f)
            filterSyncEngine.broadcastEmojiShower(emoji, 1f)
        }

        filterControlPanel.onFilterSelected = { filterType, intensity ->
            if (filterType != VideoFilterEngine.FilterType.NONE) {
                filterEngine.applyOverlayFilter(filterType, duration = 0L, intensity = intensity)
                filterSyncEngine.broadcastFilterEffect(filterType, intensity)
            } else {
                filterEngine.clearAllEffects()
                filterSyncEngine.broadcastClearEffects()
            }
        }

        filterControlPanel.onClearEffects = {
            filterEngine.clearAllEffects()
            filterSyncEngine.broadcastClearEffects()
        }

        // Listen for remote filter effects
        observeRemoteFilterEffects()
    }

    // NEW: Handle remote filter effects
    private fun observeRemoteFilterEffects() {
        networkEngine.onControlSignalReceived = { json ->
            if (json.optString("type") == "filter_effect") {
                handleRemoteFilterEffect(json)
            } else {
                handleControlSignal(json)
            }
        }
    }

    // NEW: Process incoming filter effects from remote peer
    private fun handleRemoteFilterEffect(json: JSONObject) {
        val effectType = json.optString("type")
        when (effectType) {
            "filter_emoji" -> {
                val emojiName = json.optString("emoji")
                val intensity = json.optDouble("intensity", 1.0).toFloat()
                try {
                    val emoji = VideoFilterEngine.EmojiType.valueOf(emojiName)
                    filterEngine.triggerEmojiShower(emoji, intensity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            "filter_overlay" -> {
                val filterName = json.optString("filter")
                val intensity = json.optDouble("intensity", 1.0).toFloat()
                val duration = json.optLong("duration", 0L)
                try {
                    val filterType = VideoFilterEngine.FilterType.valueOf(filterName)
                    filterEngine.applyOverlayFilter(filterType, duration, intensity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            "filter_clear" -> {
                filterEngine.clearAllEffects()
            }
        }
    }

    private fun setupUI() {
        binding.tvPartnerName.text = SessionManager.partnerNickname.ifBlank { SessionManager.partnerUserId.ifBlank { "Secure Partner" } }

        binding.btnAnswer.setOnClickListener { callEngine.answerCall() }
        binding.btnDecline.setOnClickListener { callEngine.declineCall(); finish() }
        binding.btnEnd.setOnClickListener { callEngine.endCall(); finish() }

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

        binding.btnRecord.setOnClickListener {
            binding.layoutRecording.visibility = if (binding.layoutRecording.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

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

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
                binding.viewFinder.visibility = View.VISIBLE
                binding.videoOverlay.visibility = View.VISIBLE
            } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

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
        binding.tvCallStatus.text = if (isVideo) {
            "Voice active, video preview local only"
        } else {
            "Secure Voice Active"
        }
        binding.rowAnswerDecline.visibility = View.GONE
        binding.layoutActiveControls.visibility = View.VISIBLE
        binding.tvTimer.visibility = View.VISIBLE
        binding.waveformView.visibility = View.VISIBLE
        binding.waveformView.startAnimating()
        startCallTimer()
        animEngine.stopCallPulse(binding.avatarCard)

        // NEW: Show filter panel when call is active
        if (isVideo) {
            filterControlPanel.visibility = View.VISIBLE
            filterSyncEngine.setConnected(true)
        }
    }

    private fun renderCallEnded() {
        timerJob?.cancel()
        binding.waveformView.stopAnimating()
        val currentText = binding.tvCallStatus.text.toString()
        if (!currentText.contains("Failed", ignoreCase = true) &&
            !currentText.contains("declined", ignoreCase = true) &&
            !currentText.contains("timed out", ignoreCase = true) &&
            !currentText.contains("No answer", ignoreCase = true)) {
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
                    CallEngine.CallState.ACTIVE -> renderActiveCall()
                    CallEngine.CallState.ENDED -> renderCallEnded()
                    CallEngine.CallState.FAILED -> renderCallFailed()
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
        networkEngine.onControlSignalReceived = { json ->
            handleControlSignal(json)
        }
    }

    private fun handleControlSignal(json: JSONObject) {
        val from = json.optString("from").ifBlank { partnerUserId }
        if (from != partnerUserId) return

        when (json.optString("type")) {
            "call_accept" -> callEngine.onRemoteCallAccepted(json.optString("callId"))
            "call_reject" -> callEngine.onRemoteCallRejected(
                json.optString("callId"),
                json.optString("reason", "DECLINED")
            )
            "call_end" -> callEngine.onRemoteCallEnded(
                json.optString("callId"),
                json.optString("reason", "REMOTE_ENDED")
            )
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
        IncomingCallLauncher.clear(intent.getStringExtra("call_id"))
        callEngine.endCall()
        networkEngine.disconnect()
        ThemeApplicator.detach(this)

        // NEW: Cleanup filter system
        if (isVideo) {
            filterEngine.destroy()
            filterSyncEngine.destroy()
            filterControlPanel.collapse()
        }
    }
}
