package com.calcvault.ui.settings

import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.notifications.DualNotificationSystem
import com.calcvault.notifications.NotificationMode
import com.calcvault.notifications.NotificationPayload
import com.calcvault.ui.common.GlassUi
import org.json.JSONObject

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var notificationSystem: DualNotificationSystem
    private lateinit var spinnerNotificationMode: Spinner
    private lateinit var etPrivateCode: EditText
    private lateinit var switchShowCodeOnLockScreen: Switch
    private lateinit var switchUrgentAlerts: Switch
    private lateinit var btnTestNotification: Button
    private lateinit var btnCustomizeVibration: Button
    private lateinit var btnCustomizeSound: Button
    private lateinit var tvVibrationPattern: TextView
    private lateinit var tvSoundUri: TextView

    private var vibrationPattern = longArrayOf(0, 500, 200, 500)
    private var soundUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationSystem = DualNotificationSystem(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(android.graphics.Color.parseColor("#0A0A0A"))
        }
        setContentView(root)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(scroll)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setPadding(16, 16, 16, 16)
        }
        scroll.addView(mainLayout)

        // Title
        TextView(this).apply {
            text = "Notification Settings"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 24)
            mainLayout.addView(this)
        }

        // Notification Mode
        addSectionHeader(mainLayout, "📬 Notification Mode")
        spinnerNotificationMode = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
            val modes = arrayOf("Standard (Full Preview)", "Stealth (Hidden Content)")
            adapter = ArrayAdapter(this@NotificationSettingsActivity, android.R.layout.simple_spinner_item, modes)
            mainLayout.addView(this)
        }

        // Private Code
        addSectionHeader(mainLayout, "🔐 Stealth Mode Settings")
        TextView(this).apply {
            text = "Private Code (for Stealth mode)"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
            mainLayout.addView(this)
        }
        etPrivateCode = EditText(this).apply {
            hint = "e.g., 7226"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            mainLayout.addView(this)
        }

        switchShowCodeOnLockScreen = addToggleSetting(mainLayout, "Show Code on Lock Screen", false)

        // Urgent Alerts
        addSectionHeader(mainLayout, "🚨 Urgent Alerts")
        switchUrgentAlerts = addToggleSetting(mainLayout, "Enable Urgent Alerts", true)

        // Vibration Customization
        addSectionHeader(mainLayout, "📳 Vibration")
        tvVibrationPattern = TextView(this).apply {
            text = "Pattern: 0ms, 500ms, 200ms, 500ms"
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
            mainLayout.addView(this)
        }
        btnCustomizeVibration = Button(this).apply {
            text = "Customize Vibration Pattern"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
            setOnClickListener { showVibrationCustomizer() }
            mainLayout.addView(this)
        }

        // Sound Customization
        addSectionHeader(mainLayout, "🔊 Sound")
        tvSoundUri = TextView(this).apply {
            text = "Sound: Default Notification"
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
            mainLayout.addView(this)
        }
        btnCustomizeSound = Button(this).apply {
            text = "Choose Custom Sound"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
            setOnClickListener { showSoundPicker() }
            mainLayout.addView(this)
        }

        // Test Notification
        addSectionHeader(mainLayout, "🧪 Testing")
        btnTestNotification = Button(this).apply {
            text = "Send Test Notification"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 }
            setOnClickListener { sendTestNotification() }
            mainLayout.addView(this)
        }

        // Buttons
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        mainLayout.addView(buttonLayout)

        Button(this).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8 }
            setOnClickListener { finish() }
            buttonLayout.addView(this)
        }

        Button(this).apply {
            text = "Save"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 8 }
            setOnClickListener { saveSettings() }
            buttonLayout.addView(this)
        }

        loadSettings()
        GlassUi.applyThemeChrome(this, root)
        GlassUi.markPanel(mainLayout, alpha = 122, radiusDp = 28)
    }

    override fun onResume() {
        super.onResume()
        GlassUi.applyThemeChrome(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ThemeApplicator.detach(this)
    }

    private fun addSectionHeader(parent: LinearLayout, title: String) {
        TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#0A84FF"))
            setPadding(0, 16, 0, 8)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            parent.addView(this)
        }
    }

    private fun addToggleSetting(parent: LinearLayout, label: String, default: Boolean): Switch {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        parent.addView(container)

        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            container.addView(this)
        }

        return Switch(this).apply {
            isChecked = default
            layoutParams = LinearLayout.LayoutParams(-2, -2)
            container.addView(this)
        }
    }

    private fun showVibrationCustomizer() {
        val dialog = android.app.AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        TextView(this).apply {
            text = "Vibration Pattern (ms): delay, vibrate, pause, vibrate..."
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
            layout.addView(this)
        }

        val etPattern = EditText(this).apply {
            setText(vibrationPattern.joinToString(", "))
            hint = "0, 500, 200, 500"
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setTextColor(android.graphics.Color.WHITE)
            layout.addView(this)
        }

        dialog.setView(layout)
        dialog.setPositiveButton("Save") { _, _ ->
            try {
                vibrationPattern = etPattern.text.toString()
                    .split(",")
                    .map { it.trim().toLong() }
                    .toLongArray()
                tvVibrationPattern.text = "Pattern: ${vibrationPattern.joinToString("ms, ")}ms"
                testVibration()
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid pattern", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.setNegativeButton("Cancel", null)
        dialog.show()
    }

    private fun showSoundPicker() {
        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
        }
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                soundUri = uri.toString()
                val ringtone = RingtoneManager.getRingtone(this, uri)
                tvSoundUri.text = "Sound: ${ringtone.getTitle(this)}"
            }
        }
    }

    private fun testVibration() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(vibrationPattern, -1)
        }
    }

    private fun sendTestNotification() {
        val mode = if (spinnerNotificationMode.selectedItemPosition == 0) {
            NotificationMode.STANDARD
        } else {
            NotificationMode.STEALTH
        }

        val payload = NotificationPayload(
            messageId = System.currentTimeMillis(),
            senderId = "test",
            senderName = "Test User",
            content = "This is a test notification",
            timestamp = System.currentTimeMillis(),
            isUrgent = false
        )

        notificationSystem.showNotification("test_chat", payload)
        Toast.makeText(this, "Test notification sent!", Toast.LENGTH_SHORT).show()
    }

    private fun loadSettings() {
        // val config = storageManager.getJson("notification_config") ?: JSONObject()
        val config = JSONObject()
        val mode = config.optString("mode", "STANDARD")
        spinnerNotificationMode.setSelection(if (mode == "STEALTH") 1 else 0)
        etPrivateCode.setText(config.optString("privateCode", ""))
        switchShowCodeOnLockScreen.isChecked = config.optBoolean("showCodeOnLockScreen", true)
        switchUrgentAlerts.isChecked = config.optBoolean("enableUrgentAlerts", true)
    }

    private fun saveSettings() {
        val config = JSONObject().apply {
            put("mode", if (spinnerNotificationMode.selectedItemPosition == 0) "STANDARD" else "STEALTH")
            put("privateCode", etPrivateCode.text.toString())
            put("showCodeOnLockScreen", switchShowCodeOnLockScreen.isChecked)
            put("enableUrgentAlerts", switchUrgentAlerts.isChecked)
            put("vibrationPattern", vibrationPattern.joinToString(","))
            put("soundUri", soundUri ?: "")
        }
        // storageManager.saveJson("notification_config", config)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
