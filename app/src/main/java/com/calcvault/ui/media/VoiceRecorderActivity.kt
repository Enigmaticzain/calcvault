package com.calcvault.ui.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.calcvault.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Voice Recorder Activity with social media-style UI
 * Supports:
 * - Press & hold to record (WhatsApp style)
 * - Real-time waveform visualization
 * - Recording duration display
 * - Playback preview
 * - Send/Cancel options
 */
class VoiceRecorderActivity : AppCompatActivity() {

    private lateinit var recordButton: LinearLayout
    private lateinit var playButton: ImageButton
    private lateinit var sendButton: Button
    private lateinit var cancelButton: Button
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var waveformView: WaveformView
    private lateinit var playbackProgressBar: ProgressBar

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingTimer: CountDownTimer? = null
    private var playbackTimer: CountDownTimer? = null

    private val PERMISSION_CODE_AUDIO = 101
    private val MAX_RECORDING_DURATION = 300000L // 5 minutes in milliseconds
    private var recordingStartTime = 0L
    
    // Slide to cancel gesture tracking
    private var recordButtonStartX = 0f
    private var isSlideToCancelActive = false
    private val SLIDE_CANCEL_THRESHOLD = -100 // pixels to swipe left

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recorder)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        initializeViews()
        setupListeners()

        // Check permissions
        if (!hasAudioPermissions()) {
            requestAudioPermissions()
        }
    }

    private fun initializeViews() {
        recordButton = findViewById(R.id.recordButton)
        playButton = findViewById(R.id.playButton)
        sendButton = findViewById(R.id.sendButton)
        cancelButton = findViewById(R.id.cancelButton)
        timerText = findViewById(R.id.timerText)
        statusText = findViewById(R.id.statusText)
        waveformView = findViewById(R.id.waveformView)
        playbackProgressBar = findViewById(R.id.playbackProgressBar)

        // Initial state
        updateUIState(UIState.READY)
    }

    private fun setupListeners() {
        recordButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isRecording && hasAudioPermissions()) {
                        recordButtonStartX = event.x
                        isSlideToCancelActive = false
                        startRecording()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isRecording) {
                        val deltaX = event.x - recordButtonStartX
                        
                        // Slide left to cancel (WhatsApp style)
                        if (deltaX < SLIDE_CANCEL_THRESHOLD) {
                            if (!isSlideToCancelActive) {
                                isSlideToCancelActive = true
                                showCancelIndicator()
                            }
                        } else {
                            if (isSlideToCancelActive) {
                                isSlideToCancelActive = false
                                hideCancelIndicator()
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        val deltaX = event.x - recordButtonStartX
                        
                        // If swiped far enough left, cancel recording
                        if (deltaX < SLIDE_CANCEL_THRESHOLD - 50) {
                            cancelRecording()
                            isSlideToCancelActive = false
                            hideCancelIndicator()
                        } else {
                            // Otherwise just pause
                            pauseRecording()
                            isSlideToCancelActive = false
                            hideCancelIndicator()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        playButton.setOnClickListener {
            if (recordingFile?.exists() == true) {
                if (mediaPlayer == null || mediaPlayer?.isPlaying == false) {
                    playRecording()
                } else {
                    stopPlayback()
                }
            }
        }

        sendButton.setOnClickListener {
            if (recordingFile?.exists() == true) {
                sendRecording()
            } else {
                Toast.makeText(this, "No recording to send", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            stopRecording()
            recordingFile?.delete()
            finish()
        }
    }

    private fun startRecording() {
        try {
            // Create output file
            recordingFile = createRecordingFile()

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(recordingFile?.absolutePath)

                try {
                    prepare()
                    start()
                    isRecording = true
                    isPaused = false
                    recordingStartTime = System.currentTimeMillis()

                    updateUIState(UIState.RECORDING)
                    startRecordingTimer()
                    waveformView.startAnimation()

                    Toast.makeText(
                        this@VoiceRecorderActivity,
                        "Recording... Release to stop",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(this@VoiceRecorderActivity, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot start recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pauseRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    pause()
                    isPaused = true
                } else {
                    // For older Android versions, we save and stop
                    stop()
                    isRecording = false
                }
            }
            waveformView.stopAnimation()
            recordingTimer?.cancel()
            updateUIState(UIState.PAUSED)
        } catch (e: Exception) {
            Toast.makeText(this, "Error pausing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeRecording() {
        if (!isPaused) return

        try {
            mediaRecorder?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    resume()
                    isPaused = false
                    isRecording = true
                    startRecordingTimer()
                    waveformView.startAnimation()
                    updateUIState(UIState.RECORDING)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error resuming: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        if (!isRecording && !isPaused) return

        try {
            mediaRecorder?.apply {
                if (isRecording || isPaused) {
                    stop()
                }
                release()
            }
            mediaRecorder = null
            isRecording = false
            isPaused = false
            waveformView.stopAnimation()
            recordingTimer?.cancel()

            if (recordingFile?.length() ?: 0 > 0) {
                updateUIState(UIState.STOPPED)
            } else {
                recordingFile?.delete()
                updateUIState(UIState.READY)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelRecording() {
        if (!isRecording && !isPaused) return

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Already stopped
                }
                release()
            }
            mediaRecorder = null
            isRecording = false
            isPaused = false
            waveformView.stopAnimation()
            recordingTimer?.cancel()
            
            // Delete the file without sending
            recordingFile?.delete()
            recordingFile = null
            
            updateUIState(UIState.READY)
            timerText.text = "0:00"
            statusText.text = "🎤 Press & Hold to Record"
            
            Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error cancelling: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCancelIndicator() {
        // Show visual feedback for slide-to-cancel
        statusText.text = "⬅️ Slide to Cancel"
        statusText.setTextColor(android.graphics.Color.parseColor("#FF6B9D"))
        recordButton.alpha = 0.5f
    }

    private fun hideCancelIndicator() {
        // Hide cancel indicator
        if (isRecording) {
            statusText.text = "🔴 RECORDING..."
            statusText.setTextColor(android.graphics.Color.parseColor("#FF6B9D"))
        }
        recordButton.alpha = 1f
    }

    private fun playRecording() {
        if (recordingFile?.exists() != true) return

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recordingFile?.absolutePath)
                prepare()
                start()

                setOnCompletionListener {
                    updateUIState(UIState.STOPPED)
                    playbackTimer?.cancel()
                }

                updateUIState(UIState.PLAYING)
                startPlaybackTimer(duration.toLong())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            playbackTimer?.cancel()
            playbackProgressBar.progress = 0
            updateUIState(UIState.STOPPED)
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping playback: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendRecording() {
        if (recordingFile?.exists() != true) {
            Toast.makeText(this, "No recording to send", Toast.LENGTH_SHORT).show()
            return
        }

        // Pass file path back to ChatActivity
        val duration = getDurationString(recordingFile?.length() ?: 0)
        val intent = Intent().apply {
            putExtra("voice_file_path", recordingFile?.absolutePath)
            putExtra("voice_duration", duration)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun createRecordingFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val voiceDir = File(getExternalFilesDir(null), "voice_messages")
        if (!voiceDir.exists()) {
            voiceDir.mkdirs()
        }
        return File(voiceDir, "voice_$timestamp.m4a")
    }

    private fun startRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = object : CountDownTimer(MAX_RECORDING_DURATION, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = MAX_RECORDING_DURATION - millisUntilFinished
                timerText.text = formatDuration(elapsed)
                waveformView.updateWaveform((elapsed * 100) / MAX_RECORDING_DURATION)
            }

            override fun onFinish() {
                stopRecording()
                Toast.makeText(
                    this@VoiceRecorderActivity,
                    "Max recording time reached",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun startPlaybackTimer(duration: Long) {
        playbackTimer?.cancel()
        playbackProgressBar.max = duration.toInt()
        playbackTimer = object : CountDownTimer(duration, 50) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = duration - millisUntilFinished
                playbackProgressBar.progress = progress.toInt()
                timerText.text = formatDuration(progress)
            }

            override fun onFinish() {
                playbackProgressBar.progress = 0
            }
        }.start()
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 60000) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun getDurationString(fileSize: Long): String {
        // Estimate: ~16KB per second for AAC 128kbps
        val seconds = fileSize / 16000
        return formatDuration(seconds * 1000)
    }

    private fun updateUIState(state: UIState) {
        when (state) {
            UIState.READY -> {
                recordButton.alpha = 1f
                recordButton.isEnabled = true
                playButton.isEnabled = false
                sendButton.isEnabled = false
                timerText.text = "0:00"
                statusText.text = "🎤 Press & Hold to Record"
                playbackProgressBar.progress = 0
            }
            UIState.RECORDING -> {
                recordButton.alpha = 0.5f
                recordButton.isEnabled = true
                playButton.isEnabled = false
                sendButton.isEnabled = false
                statusText.text = "🔴 RECORDING..."
            }
            UIState.PAUSED -> {
                recordButton.alpha = 1f
                recordButton.isEnabled = true
                playButton.isEnabled = true
                sendButton.isEnabled = true
                statusText.text = "⏸️ Release to save"
            }
            UIState.STOPPED -> {
                recordButton.alpha = 1f
                recordButton.isEnabled = true
                playButton.isEnabled = true
                sendButton.isEnabled = true
                statusText.text = "✓ Ready to send"
            }
            UIState.PLAYING -> {
                playButton.isEnabled = true
                statusText.text = "🔊 Playing..."
            }
        }
    }

    private fun hasAudioPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_CODE_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                // Already stopped
            }
            release()
        }
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        recordingTimer?.cancel()
        playbackTimer?.cancel()
    }

    companion object {
        fun startRecording(context: Context) {
            context.startActivity(Intent(context, VoiceRecorderActivity::class.java))
        }
    }

    private enum class UIState {
        READY, RECORDING, PAUSED, STOPPED, PLAYING
    }
}

/**
 * Custom view for waveform visualization during recording
 */
class WaveformView(context: Context?, attrs: android.util.AttributeSet?) :
    View(context, attrs) {

    private val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FF6B9D")
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val waveformData = mutableListOf<Float>()
    private var isAnimating = false

    init {
        // Initialize with some bars
        repeat(30) {
            waveformData.add((Math.random() * 0.3f).toFloat())
        }
    }

    fun startAnimation() {
        isAnimating = true
        post(animationRunnable)
    }

    fun stopAnimation() {
        isAnimating = false
        removeCallbacks(animationRunnable)
    }

    fun updateWaveform(progress: Long) {
        // Add a new bar based on progress
        val newHeight = 0.2f + (Math.random() * 0.8f).toFloat()
        if (waveformData.size > 50) {
            waveformData.removeAt(0)
        }
        waveformData.add(newHeight)
        invalidate()
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                invalidate()
                postDelayed(this, 30)
            }
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        val barWidth = width.toFloat() / waveformData.size
        var x = 0f

        for (height in waveformData) {
            val barHeight = height * (this.height * 0.8f)
            val startY = (this.height - barHeight) / 2
            canvas.drawLine(x + barWidth / 2, startY, x + barWidth / 2, startY + barHeight, paint)
            x += barWidth
        }
    }
}
