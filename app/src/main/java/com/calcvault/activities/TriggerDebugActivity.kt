package com.calcvault.activities

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.calcvault.R
import com.calcvault.emotional.EmotionalAnimationEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.TriggerSystem
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.ui.ambient.AmbientAnimationView
import org.json.JSONArray
import org.json.JSONObject

/**
 * TriggerDebugActivity
 *
 * Test and debug the custom trigger system in EmotionalAnimationEngine.
 * Allows you to:
 * - View all active triggers
 * - Test triggers with custom messages
 * - Load custom triggers from JSON
 * - Enable/disable triggers
 * - View debug logs
 * - Test notifications with custom sounds and vibrations
 */
class TriggerDebugActivity : AppCompatActivity() {

    private lateinit var emotionalEngine: EmotionalAnimationEngine
    private lateinit var animationView: AmbientAnimationView
    private lateinit var debugOutput: TextView
    private lateinit var triggerInput: EditText
    private lateinit var jsonInput: EditText
    private lateinit var themeEngine: ThemeEngine
    private var selectedSoundUri: String? = null
    private var selectedVibrationPattern: LongArray = longArrayOf(0, 100, 50, 100)

    private val logMessages = mutableListOf<String>()
    private val maxLogs = 100

    companion object {
        private const val TAG = "TriggerDebugActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trigger_debug)

        themeEngine = ThemeEngine(this)
        ThemeApplicator.applyActive(this)

        initializeViews()
        setupAnimationEngine()
        setupButtonListeners()
        setupDebugOutput()
        createNotificationChannel()
    }

    private fun initializeViews() {
        animationView = findViewById<AmbientAnimationView>(R.id.animationView)
        debugOutput = findViewById(R.id.debugOutput)
        triggerInput = findViewById(R.id.triggerInput)
        jsonInput = findViewById(R.id.jsonInput)

        debugOutput.apply {
            movementMethod = ScrollingMovementMethod()
            setBackgroundColor(0x1F000000.toInt())
            setTextColor(0xFF00FF00.toInt())
            textSize = 10f
        }
    }

    private fun setupAnimationEngine() {
        emotionalEngine = EmotionalAnimationEngine(
            context = this,
            rootView = findViewById(android.R.id.content)
        )

        // Enable debug logging
        TriggerSystem.enableDebug(true)

        // Initialize built-in triggers
        emotionalEngine.initializeBuiltInTriggers()

        log("✓ EmotionalAnimationEngine initialized")
        logTriggerStats()
    }

    private fun setupButtonListeners() {
        // Test trigger with input message
        findViewById<Button>(R.id.btnTestTrigger).setOnClickListener {
            val message = triggerInput.text.toString()
            if (message.isNotEmpty()) {
                log("Testing message: \"$message\"")
                emotionalEngine.checkMessageTriggers(message)
            } else {
                log("⚠ Please enter a message to test")
            }
        }

        // Load triggers from JSON
        findViewById<Button>(R.id.btnLoadJSON).setOnClickListener {
            val json = jsonInput.text.toString()
            if (json.isNotEmpty()) {
                log("Loading custom triggers from JSON...")
                val success = emotionalEngine.loadCustomTriggers(json)
                if (success) {
                    log("✓ Custom triggers loaded successfully")
                } else {
                    log("⚠ Some errors occurred loading triggers (see logs above)")
                }
                logTriggerStats()
            } else {
                log("⚠ Please enter JSON in the input field")
            }
        }

        // Add test trigger
        findViewById<Button>(R.id.btnAddTestTrigger).setOnClickListener {
            addTestTriggers()
        }

        // List all triggers
        findViewById<Button>(R.id.btnListTriggers).setOnClickListener {
            listAllTriggers()
        }

        // Clear custom triggers
        findViewById<Button>(R.id.btnClearTriggers).setOnClickListener {
            emotionalEngine.clearCustomTriggers()
            log("✓ All triggers cleared, built-in triggers re-initialized")
            logTriggerStats()
        }

        // Show stats
        findViewById<Button>(R.id.btnStats).setOnClickListener {
            logTriggerStats()
        }

        // Copy example JSON
        findViewById<Button>(R.id.btnExampleJSON).setOnClickListener {
            showExampleJSON()
        }

        // Clear debug output
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            logMessages.clear()
            debugOutput.text = "Debug log cleared"
        }

        // Test notification
        findViewById<Button>(R.id.btnTestNotification)?.setOnClickListener {
            testNotification()
        }

        // Select custom sound
        findViewById<Button>(R.id.btnSelectSound)?.setOnClickListener {
            selectCustomSound()
        }

        // Select vibration pattern
        findViewById<Button>(R.id.btnSelectVibration)?.setOnClickListener {
            selectVibrationPattern()
        }
    }

    private fun setupDebugOutput() {
        log("═══════════════════════════════════════════════════")
        log("Trigger Debug Activity")
        log("═══════════════════════════════════════════════════")
        log("")
        log("Use this activity to:")
        log("• Test triggers with custom messages")
        log("• Load triggers from JSON")
        log("• View all active triggers")
        log("• Debug trigger execution")
        log("• Test notifications with sounds & vibrations")
        log("")
    }

    private fun addTestTriggers() {
        log("Adding test triggers...")

        // Test 1: Simple hello emoji trigger
        emotionalEngine.addCustomEmojiTrigger(
            keyword = "hello",
            emoji = "👋",
            intensity = 5,
            direction = "UP",
            speed = "MEDIUM"
        )
        log("✓ Added: hello → 👋")

        // Test 2: Celebration trigger
        emotionalEngine.addCustomEmojiTrigger(
            keyword = "awesome",
            emoji = "🎉",
            intensity = 10,
            direction = "DOWN",
            speed = "FAST"
        )
        log("✓ Added: awesome → 🎉")

        // Test 3: Rainbow trigger
        emotionalEngine.addCustomEmojiTrigger(
            keyword = "rainbow",
            emoji = "🌈",
            intensity = 20,
            direction = "RANDOM",
            speed = "SLOW"
        )
        log("✓ Added: rainbow → 🌈")

        logTriggerStats()
    }

    private fun listAllTriggers() {
        val triggers = emotionalEngine.getAllTriggers()

        log("")
        log("═══ ALL ACTIVE TRIGGERS (${triggers.size}) ═══")

        if (triggers.isEmpty()) {
            log("No triggers loaded")
            return
        }

        triggers.forEach { trigger ->
            val keywords = trigger.keywords.joinToString(", ")
            val type = trigger.triggerType.name
            val emoji = trigger.emoji?.let { " [$it]" } ?: ""
            val status = if (trigger.isEnabled) "✓" else "✗"

            log("$status [$type] Keywords: {$keywords}$emoji")
            log("  └─ Intensity: ${trigger.intensity}, Direction: ${trigger.direction}, Speed: ${trigger.speed}")
        }

        log("")
    }

    private fun logTriggerStats() {
        val stats = emotionalEngine.getTriggerStats()

        log("")
        log("═══ TRIGGER STATISTICS ═══")
        log("Total triggers: ${stats.totalTriggers}")
        log("Enabled: ${stats.enabledTriggers}")
        log("Total keywords: ${stats.totalKeywords}")

        if (stats.byType.isNotEmpty()) {
            log("")
            log("By Type:")
            stats.byType.forEach { (type, count) ->
                log("  • ${type.name}: $count")
            }
        }

        log("")
    }

    private fun showExampleJSON() {
        val example = JSONArray().apply {
            put(
                JSONObject().apply {
                    put(
                        "keywords",
                        JSONArray().apply {
                            put("hello")
                            put("hi")
                            put("hey")
                        }
                    )
                    put("type", "emoji_animation")
                    put("emoji", "👋")
                    put("intensity", 8)
                    put("direction", "UP")
                    put("speed", "MEDIUM")
                }
            )
            put(
                JSONObject().apply {
                    put(
                        "keywords",
                        JSONArray().apply {
                            put("wow")
                            put("amazing")
                            put("cool")
                        }
                    )
                    put("type", "emoji_animation")
                    put("emoji", "😲")
                    put("intensity", 15)
                    put("direction", "DOWN")
                    put("speed", "FAST")
                }
            )
            put(
                JSONObject().apply {
                    put("keywords", "congratulations")
                    put("type", "emoji_animation")
                    put("emoji", "🎊")
                    put("intensity", 20)
                    put("direction", "RANDOM")
                    put("speed", "MEDIUM")
                }
            )
        }.toString()

        jsonInput.setText(example)
        log("Example JSON loaded into input field")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "trigger_notifications",
                "Trigger Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for trigger testing"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun testNotification() {
        log("Testing notification...")
        if (!canPostNotifications()) {
            log("⚠ POST_NOTIFICATIONS permission is missing")
            return
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, "trigger_notifications")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Trigger Test")
            .setContentText("This is a test notification")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply {
                selectedSoundUri?.let { setSound(android.net.Uri.parse(it)) }
                    ?: setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                setVibrate(selectedVibrationPattern)
            }
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        log("✓ Notification sent with sound and vibration")
    }

    private fun selectCustomSound() {
        log("Sound options:")
        log("1. Default notification sound")
        log("2. Alarm sound")
        log("3. Ringtone")

        selectedSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString()
        log("✓ Selected: Default notification sound")
    }

    private fun selectVibrationPattern() {
        val patterns = mapOf(
            "Light" to longArrayOf(0, 50),
            "Medium" to longArrayOf(0, 100, 50, 100),
            "Strong" to longArrayOf(0, 200, 100, 200),
            "SOS" to longArrayOf(0, 100, 100, 100, 100, 100, 100, 100),
            "Pulse" to longArrayOf(0, 150, 100, 150, 100, 150)
        )

        log("Vibration patterns available:")
        patterns.forEach { (name, pattern) ->
            log("  • $name: ${pattern.joinToString(",")}")
            selectedVibrationPattern = pattern
        }
        log("✓ Selected: Medium vibration pattern")
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        logMessages.add(message)

        // Keep only recent logs
        while (logMessages.size > maxLogs) {
            logMessages.removeAt(0)
        }

        debugOutput.text = logMessages.joinToString("\n")

        // Auto-scroll to bottom
        debugOutput.post {
            val layout = debugOutput.layout
            if (layout != null) {
                val scrollAmount = layout.getLineTop(debugOutput.lineCount) - debugOutput.height
                debugOutput.scrollTo(0, scrollAmount.coerceAtLeast(0))
            }
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
