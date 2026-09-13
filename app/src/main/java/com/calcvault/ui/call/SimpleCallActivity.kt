package com.calcvault.ui.call

import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.call.SocketCallEngine
import com.calcvault.memory.MemoryGuard
import kotlinx.coroutines.*

/**
 * SimpleCallActivity
 *
 * Clean call UI using SocketCallEngine (no WebRTC).
 */
class SimpleCallActivity : AppCompatActivity() {

    private lateinit var engine: SocketCallEngine
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvInfo: TextView
    private lateinit var btnEnd: Button
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button

    private var timerJob: Job? = null
    private var callStartMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MemoryGuard.secureWindow(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        engine = SocketCallEngine(this)
        buildUI()

        val isOutgoing = intent.getBooleanExtra("is_outgoing", true)
        val callerIp = intent.getStringExtra("caller_ip") ?: ""

        engine.onStateChanged = { state ->
            runOnUiThread { handleState(state) }
        }
        engine.onError = { msg ->
            runOnUiThread {
                tvStatus.text = msg
                btnEnd.text = "Close"
            }
        }

        if (isOutgoing) {
            val localIp = engine.getLocalIp()
            tvInfo.text = "Your IP: $localIp\nShare this with partner\nWaiting for them to call..."
            engine.startCall()
        } else {
            if (callerIp.isNotBlank()) {
                tvInfo.text = "Connecting to $callerIp..."
                engine.answerCall(callerIp)
            } else {
                askForCallerIp()
            }
        }
    }

    private fun buildUI() {
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF0A0A0F.toInt())
        setContentView(root)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 80, 48, 80)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(layout)

        // Avatar
        val tvAvatar = TextView(this).apply {
            text = "📞"
            textSize = 72f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        layout.addView(tvAvatar)

        tvStatus = TextView(this).apply {
            text = "Connecting..."
            textSize = 18f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        layout.addView(tvStatus)

        tvTimer = TextView(this).apply {
            text = "00:00"
            textSize = 22f
            setTextColor(0xFF4C8EFF.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvTimer)

        tvInfo = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(tvInfo)

        // Controls row
        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        layout.addView(controlsRow)

        btnMute = Button(this).apply {
            text = "🎤 Mute"
            setBackgroundColor(0xFF222228.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).also { it.setMargins(8, 0, 8, 0) }
            setOnClickListener {
                engine.toggleMute()
                text = if (text == "🎤 Mute") "🔇 Muted" else "🎤 Mute"
            }
        }
        controlsRow.addView(btnMute)

        btnSpeaker = Button(this).apply {
            text = "🔈 Speaker"
            setBackgroundColor(0xFF222228.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).also { it.setMargins(8, 0, 8, 0) }
            var speakerOn = false
            setOnClickListener {
                speakerOn = !speakerOn
                engine.toggleSpeaker(speakerOn)
                text = if (speakerOn) "🔊 Speaker" else "🔈 Speaker"
            }
        }
        controlsRow.addView(btnSpeaker)

        // End button
        btnEnd = Button(this).apply {
            text = "End Call"
            setBackgroundColor(0xFFCC2222.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            ).also { it.setMargins(0, 32, 0, 0) }
            setOnClickListener {
                engine.endCall()
                finish()
            }
        }
        layout.addView(btnEnd)
    }

    private fun handleState(state: SocketCallEngine.ConnectionState) {
        when (state) {
            SocketCallEngine.ConnectionState.INIT -> tvStatus.text = "Initializing..."
            SocketCallEngine.ConnectionState.CONNECTING -> tvStatus.text = "Connecting..."
            SocketCallEngine.ConnectionState.CONNECTED -> {
                tvStatus.text = "● Connected"
                tvStatus.setTextColor(0xFF44FF88.toInt())
                tvInfo.text = ""
                tvTimer.visibility = View.VISIBLE
                startTimer()
            }
            SocketCallEngine.ConnectionState.DISCONNECTED -> {
                timerJob?.cancel()
                tvStatus.text = "Call ended"
                tvStatus.setTextColor(0xFF888888.toInt())
                btnEnd.text = "Close"
                btnMute.isEnabled = false
                btnSpeaker.isEnabled = false
            }
            SocketCallEngine.ConnectionState.FAILED -> {
                timerJob?.cancel()
                tvStatus.text = "Connection failed"
                tvStatus.setTextColor(0xFFFF0000.toInt())
            }
        }
    }

    private fun startTimer() {
        callStartMs = SystemClock.elapsedRealtime()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - callStartMs
                val m = (elapsed / 60_000).toInt()
                val s = ((elapsed % 60_000) / 1000).toInt()
                tvTimer.text = "%02d:%02d".format(m, s)
                delay(1_000)
            }
        }
    }

    private fun askForCallerIp() {
        val et = EditText(this).apply {
            hint = "Enter caller's IP address"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Enter Caller IP")
            .setView(et)
            .setPositiveButton("Connect") { _, _ ->
                val ip = et.text.toString().trim()
                if (ip.isNotBlank()) {
                    tvInfo.text = "Connecting to $ip..."
                    engine.answerCall(ip)
                } else {
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        engine.endCall()
    }
}
