package com.calcvault.ui.settings

import android.content.Intent
import android.os.Vibrator
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.auth.BiometricHelper
import com.calcvault.auth.UnlockManager
import com.calcvault.companion.CompanionBridgeController
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.network.SecurePairingManager
import com.calcvault.notifications.CustomNotificationManager
import com.calcvault.notifications.DefaultNotificationSettings
import com.calcvault.network.bluetooth.BluetoothTransport
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.provider.StorageManager
import com.calcvault.ui.listen.ListenTogetherActivity
import com.calcvault.utils.SessionManager

/**
 * SettingsActivity
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var unlockManager  : UnlockManager
    private lateinit var storageEngine  : USBStorageEngine
    private lateinit var themeEngine    : ThemeEngine
    private lateinit var btTransport    : BluetoothTransport
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var scrollLayout   : LinearLayout
    private var pairingManager          : SecurePairingManager? = null

    companion object {
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        unlockManager = UnlockManager.getInstance(this)
        storageEngine = USBStorageEngine.getInstance(this)
        themeEngine   = ThemeEngine(this)
        btTransport   = BluetoothTransport(this)
        biometricHelper = BiometricHelper(this)

        buildUI()
        // Fix: Apply theme AFTER setContentView so it's not removed
        ThemeApplicator.applyActive(this)
    }

    private fun buildUI() {
        val theme = themeEngine.getCurrentTheme()
        val scroll = ScrollView(this)
        setContentView(scroll)
        scroll.setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) theme.backgroundStart else android.graphics.Color.TRANSPARENT)

        scrollLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 48, 0, 80)
        }
        scroll.addView(scrollLayout)

        addHeader("Settings")

        addSectionTitle("🔐 Security")
        addSettingRow("Change Passphrase") { showChangePassphrase() }
        addSettingRow("Change Decoy Passphrase") { showChangeDecoy() }
        addSettingToggle("Enable Face/Fingerprint Lock", SessionManager.isBiometricEnabled) { enabled ->
            if (!biometricHelper.isAvailable()) {
                SessionManager.isBiometricEnabled = false
                unlockManager.updateBiometricEnabled(false)
                toast("Face/Fingerprint sensor not found or not set up")
                rebuildUI()
            } else if (enabled) {
                biometricHelper.authenticate(
                    onSuccess = {
                        SessionManager.isBiometricEnabled = true
                        unlockManager.updateBiometricEnabled(true)
                        toast("Biometric lock enabled")
                    },
                    onFailed = {
                        SessionManager.isBiometricEnabled = false
                        unlockManager.updateBiometricEnabled(false)
                        toast("Biometric not recognized")
                        rebuildUI()
                    },
                    onError = { _, msg ->
                        SessionManager.isBiometricEnabled = false
                        unlockManager.updateBiometricEnabled(false)
                        toast(msg)
                        rebuildUI()
                    }
                )
            } else {
                SessionManager.isBiometricEnabled = false
                unlockManager.updateBiometricEnabled(false)
                toast("Biometric lock disabled")
            }
        }
        addSettingSlider("Inactivity Timeout", "${SessionManager.inactivityTimeoutMs / 60000} min") { mins ->
            val timeoutMs = mins * 60000L
            SessionManager.inactivityTimeoutMs = timeoutMs
            unlockManager.updateInactivityTimeout(timeoutMs)
        }
        addSettingRow("Emergency Recovery Key", secondary = "Show recovery key") {
            showRecoveryKey()
        }

        addDivider()

        addSectionTitle("🎨 Appearance")
        addThemePicker()
        addSettingToggle("Adaptive Theme (auto day/night)", SessionManager.isAdaptiveTheme) { enabled ->
            SessionManager.isAdaptiveTheme = enabled
            themeEngine.setAdaptive(enabled)
            unlockManager.updateThemeSettings(SessionManager.themeType, enabled)
            recreate()
        }
        addAnimatedThemePicker()
        addAnimationModePicker()
        addAmbientBackgroundPicker()

        addDivider()

        addSectionTitle("😊 Mood System")
        addSettingRow("Manage Moods", "Add, edit, or reorder your moods") {
            // In a full implementation, this would open a new activity for mood management.
            toast("Mood management settings coming soon!")
        }
        addSettingToggle("Enable Mood UI Adaptation", SessionManager.isMoodUiAdaptationEnabled) { enabled ->
            SessionManager.isMoodUiAdaptationEnabled = enabled
            unlockManager.updateMoodUiAdaptation(enabled)
        }

        addDivider()

        addSectionTitle("❤️ Emotional Triggers")
        addSettingRow("Built-in Triggers", secondary = "love, miss you, good night...") {
            showBuiltInTriggers()
        }
        addSettingRow("Add Advanced Emoji Trigger") { showAddCustomEmojiTrigger() }

        addDivider()

        addSectionTitle("🔁 Sync")
        addSettingRow("Sync with Partner USB", secondary = "Connect both USBs to trigger") {
            triggerManualSync()
        }
        addSettingRow("Last Sync", secondary = getLastSyncTime()) {}
        addSettingRow("Rebuild Chat from Instagram Export", secondary = "Re-runs native reconstruction on next Chat open") {
            scheduleInstagramReimport()
        }

        addDivider()

        addSectionTitle("🔔 Notification Feedback")
        val defaultSoundName = DefaultNotificationSettings.PREDEFINED_SOUNDS.find { it.id == SessionManager.defaultNotificationSound }?.name ?: "Default"
        addSettingRow("Message Sound", secondary = defaultSoundName) {
            showSoundPicker(isUrgent = false)
        }
        val defaultVibeName = DefaultNotificationSettings.PREDEFINED_VIBRATIONS.find { it.id == SessionManager.defaultVibrationPattern }?.name ?: "Default"
        addSettingRow("Message Vibration", secondary = defaultVibeName) {
            showVibrationPicker(isUrgent = false)
        }
        addSettingRow("Test Notification") {
            CustomNotificationManager(this).testNotification()
        }
        addSectionTitle("Urgent Notifications", color = theme.accentColor)
        val urgentSoundName = DefaultNotificationSettings.PREDEFINED_SOUNDS.find { it.id == SessionManager.urgentNotificationSound }?.name ?: "Default"
        addSettingRow("Urgent Sound", secondary = urgentSoundName) {
            showSoundPicker(isUrgent = true)
        }
        val urgentVibeName = DefaultNotificationSettings.PREDEFINED_VIBRATIONS.find { it.id == SessionManager.urgentVibrationPattern }?.name ?: "Default"
        addSettingRow("Urgent Vibration", secondary = urgentVibeName) {
            showVibrationPicker(isUrgent = true)
        }

        addDivider()

        addSectionTitle("📱 Connectivity")
        addSettingRow("Start Desktop Companion Bridge", secondary = "USB local-only mode") {
            CompanionBridgeController.startUsbOnly(this, autoApprove = com.calcvault.BuildConfig.DEBUG)
            val code = CompanionBridgeController.currentPairingCode() ?: "pending"
            toast("Companion bridge started. Pairing code: $code")
        }
        addSettingRow("Show Current Pairing Code", secondary = "Use in desktop companion app") {
            val code = CompanionBridgeController.currentPairingCode()
            if (code.isNullOrBlank()) {
                toast("No active pairing ticket. Start bridge first.")
            } else {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Desktop Pairing Code")
                    .setMessage(code)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        addSettingRow("Stop Desktop Companion Bridge") {
            CompanionBridgeController.stop(this)
            toast("Companion bridge stopped")
        }
        addSettingRow("Pair with Partner", secondary = "Fast, secure connection using a code") {
            showPairingOptions()
        }

        addSettingRow("Listen Together", secondary = "Synchronized local music with partner") {
            startActivity(Intent(this, ListenTogetherActivity::class.java))
        }

        addSettingRow("My Identity & Nicknames", secondary = "Current: ${SessionManager.localNickname.ifBlank { SessionManager.localUserId }}") {
            showNicknamePicker()
        }

        addSettingRow("Partner Nickname", secondary = SessionManager.partnerNickname.ifBlank { "Not set" }) {
            showPartnerNicknamePicker()
        }
        
        addDivider()

        addSectionTitle("⚠️ Danger Zone", color = 0xFFCC3333.toInt())
        addDangerRow("ERASE ALL DATA (Phone + USB)") { confirmFullWipe() }
    }

    private fun addAnimatedThemePicker() {
        val theme = themeEngine.getCurrentTheme()
        addSectionTitle("Live Animated Theme", color = theme.secondaryText)
        val choices = listOf("None", "Night Sky", "Doraemon", "Nature")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 8, 32, 8)
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) theme.surfaceColor else 0x88000000.toInt())
        }
        val horizontalScroll = HorizontalScrollView(this)
        horizontalScroll.addView(row)
        scrollLayout.addView(horizontalScroll)

        for (name in choices) {
            val choice = when(name) {
                "Night Sky" -> ThemeApplicator.ThemeChoice.NIGHT_SKY
                "Doraemon"  -> ThemeApplicator.ThemeChoice.DORAEMON
                "Nature"    -> ThemeApplicator.ThemeChoice.NATURE_ADVENTURE
                else        -> ThemeApplicator.ThemeChoice.NONE
            }
            val btn = Button(this).apply {
                text = name; textSize = 11f
                setBackgroundColor(0xFF2A2A35.toInt())
                setTextColor(if (ThemeApplicator.activeTheme == choice) theme.accentColor else 0xFFBBBBBB.toInt())
                setPadding(12, 8, 12, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(4, 0, 4, 0) }
                setOnClickListener {
                    ThemeApplicator.activeTheme = choice
                    unlockManager.updateActiveAnimatedTheme(choice.name)
                    recreate()
                }
            }
            row.addView(btn)
        }
    }

    private fun showPairingOptions() {
        val options = arrayOf("Create Pairing Code", "Enter Pairing Code")
        android.app.AlertDialog.Builder(this)
            .setTitle("Pair with Partner")
            .setItems(options) { _, which ->
                if (which == 0) startCreateCodeFlow() else startJoinCodeFlow()
            }
            .show()
    }

    private fun scheduleInstagramReimport() {
        val folder = "zain_17882311679967359"
        val stateKey = "imports/instagram/$folder/state"
        StorageManager.delete(stateKey)
        toast("Instagram reconstruction will rerun when you open Chat")
    }

    private fun startCreateCodeFlow() {
        pairingManager = SecurePairingManager(
            signalingUrl = SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING },
            onPeerConnected = {
                runOnUiThread { toast("Partner connected! Handshake successful.") }
            },
            onSignalReceived = { },
            onError = { err -> runOnUiThread { toast("Pairing error: $err") } }
        )
        
        val code = pairingManager!!.generatePairingCode()
        pairingManager!!.startPairing(code)

        val theme = themeEngine.getCurrentTheme()
        val tvCode = TextView(this).apply {
            text = code
            textSize = 42f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(theme.accentColor)
            setPadding(0, 48, 0, 48)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Share this code")
            .setMessage("Partner must enter this code on their device now. Code expires in 60s.")
            .setView(tvCode)
            .setPositiveButton("Done") { _, _ -> pairingManager?.disconnect() }
            .setOnDismissListener { pairingManager?.disconnect() }
            .show()
    }

    private fun startJoinCodeFlow() {
        val et = EditText(this).apply {
            hint = "ABCDEF"
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            textSize = 24f
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Enter Pairing Code")
            .setMessage("Enter the 6-character code shown on your partner's screen.")
            .setView(et)
            .setPositiveButton("Connect") { _, _ ->
                val code = et.text.toString().trim().uppercase()
                if (code.length == 6) {
                    joinCode(code)
                } else {
                    toast("Invalid code format")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinCode(code: String) {
        pairingManager = SecurePairingManager(
            signalingUrl = SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING },
            onPeerConnected = {
                runOnUiThread { toast("Connected to partner!") }
            },
            onSignalReceived = { },
            onError = { err -> runOnUiThread { toast("Pairing failed: $err") } }
        )
        pairingManager!!.startPairing(code)
    }

    private fun addHeader(title: String) {
        val tv = TextView(this).apply {
            text = title; textSize = 26f; setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 24, 32, 8)
        }
        scrollLayout.addView(tv)
    }

    private fun addSectionTitle(title: String, color: Int = 0xFF888888.toInt()) {
        val tv = TextView(this).apply {
            text = title; textSize = 13f; setTextColor(color)
            setPadding(32, 24, 32, 8)
        }
        scrollLayout.addView(tv)
    }

    private fun addSettingRow(title: String, secondary: String = "", onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val tvTitle = TextView(this).apply {
            text = title; textSize = 16f; setTextColor(0xFFEEEEEE.toInt())
        }
        row.addView(tvTitle)

        if (secondary.isNotEmpty()) {
            val tvSec = TextView(this).apply {
                text = secondary; textSize = 12f; setTextColor(0xFF888888.toInt())
                setPadding(0, 4, 0, 0)
            }
            row.addView(tvSec)
        }

        scrollLayout.addView(row)
    }

    private fun addSettingToggle(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = title; textSize = 16f; setTextColor(0xFFEEEEEE.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        row.addView(tvTitle)

        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        row.addView(sw)

        scrollLayout.addView(row)
    }

    private fun addSettingSlider(title: String, valueLabel: String, onValueChange: (Int) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvTitle = TextView(this).apply {
            text = title; textSize = 16f; setTextColor(0xFFEEEEEE.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val tvVal = TextView(this).apply {
            text = valueLabel; textSize = 14f; setTextColor(0xFF888888.toInt())
        }
        header.addView(tvTitle); header.addView(tvVal)
        row.addView(header)

        val seek = SeekBar(this).apply {
            max = 60
            progress = SessionManager.inactivityTimeoutMs.toInt() / 60000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    tvVal.text = "$p min"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    onValueChange(progress)
                }
            })
        }
        row.addView(seek)
        scrollLayout.addView(row)
    }

    private fun addThemePicker() {
        val theme = themeEngine.getCurrentTheme()
        addSectionTitle("Base Theme (Colors)")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) theme.surfaceColor else 0x88000000.toInt())
        }
        val choices = listOf("Midnight", "Nord", "Forest", "Rose")
        for (name in choices) {
            val type = name.lowercase()
            val btn = Button(this).apply {
                text = name; textSize = 12f
                setBackgroundColor(0xFF2A2A35.toInt())
                setTextColor(if (SessionManager.themeType == type) theme.accentColor else 0xFFBBBBBB.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(4,0,4,0) }
                setOnClickListener {
                    SessionManager.themeType = type
                    
                    val themeChoice = when (type) {
                        "midnight" -> ThemeEngine.ThemeType.MIDNIGHT
                        "nord"     -> ThemeEngine.ThemeType.NORD
                        "forest"   -> ThemeEngine.ThemeType.FOREST
                        "rose"     -> ThemeEngine.ThemeType.ROSE
                        else       -> ThemeEngine.ThemeType.MIDNIGHT
                    }
                    themeEngine.setTheme(themeChoice)
                    
                    unlockManager.updateThemeSettings(type, SessionManager.isAdaptiveTheme)
                    recreate()
                }
            }
            row.addView(btn)
        }
        scrollLayout.addView(row)
    }

    private fun addAnimationModePicker() {
        val theme = themeEngine.getCurrentTheme()
        addSectionTitle("Animation Intensity")
        val modes = listOf("Static", "Gentle", "Dynamic")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
        }
        for (m in modes) {
            val btn = Button(this).apply {
                text = m; textSize = 12f
                setBackgroundColor(0xFF2A2A35.toInt())
                setTextColor(if (SessionManager.animationMode == m.lowercase()) theme.accentColor else 0xFFBBBBBB.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(4,0,4,0) }
                setOnClickListener {
                    SessionManager.animationMode = m.lowercase()
                    rebuildUI()
                }
            }
            row.addView(btn)
        }
        scrollLayout.addView(row)
    }

    private fun addAmbientBackgroundPicker() {
        val theme = themeEngine.getCurrentTheme()
        addSectionTitle("Ambient Background Scene")
        val choices = listOf("None", "Nature", "Companion")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
        }
        for (name in choices) {
            val choice = when(name) {
                "Nature" -> ThemeApplicator.ThemeChoice.AMBIENT_NATURE
                "Companion" -> ThemeApplicator.ThemeChoice.AMBIENT_COMPANION
                else -> ThemeApplicator.ThemeChoice.NONE
            }
            val btn = Button(this).apply {
                text = name; textSize = 12f
                setBackgroundColor(0xFF2A2A35.toInt())
                setTextColor(if (ThemeApplicator.activeTheme == choice) theme.accentColor else 0xFFBBBBBB.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(4,0,4,0) }
                setOnClickListener {
                    ThemeApplicator.activeTheme = choice
                    unlockManager.updateActiveAnimatedTheme(choice.name)
                    recreate()
                }
            }
            row.addView(btn)
        }
        scrollLayout.addView(row)
    }

    private fun showSoundPicker(isUrgent: Boolean) {
        val sounds = DefaultNotificationSettings.PREDEFINED_SOUNDS
        val soundNames = sounds.map { it.name }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Select Sound")
            .setItems(soundNames) { dialog, which ->
                val selectedSoundId = sounds[which].id
                if (isUrgent) {
                    SessionManager.urgentNotificationSound = selectedSoundId
                } else {
                    SessionManager.defaultNotificationSound = selectedSoundId
                }
                rebuildUI()
                dialog.dismiss()
            }
            .show()
    }

    private fun showVibrationPicker(isUrgent: Boolean) {
        val vibrations = DefaultNotificationSettings.PREDEFINED_VIBRATIONS
        val vibrationNames = vibrations.map { it.name }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Select Vibration Pattern")
            .setItems(vibrationNames) { dialog, which ->
                val selectedVibrationId = vibrations[which].id
                if (isUrgent) {
                    SessionManager.urgentVibrationPattern = selectedVibrationId
                } else {
                    SessionManager.defaultVibrationPattern = selectedVibrationId
                }
                // Test the vibration
                (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(vibrations[which].pattern, -1)
                rebuildUI()
                dialog.dismiss()
            }
            .show()
    }

    private fun addDangerRow(title: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setOnClickListener { onClick() }
        }
        val tv = TextView(this).apply {
            text = title; textSize = 16f; setTextColor(0xFFCC3333.toInt()); gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        row.addView(tv)
        scrollLayout.addView(row)
    }

    private fun addDivider() {
        val v = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 2)
            setBackgroundColor(0xFF222222.toInt())
        }
        scrollLayout.addView(v)
    }

    private fun showChangePassphrase() { toast("Coming soon: Secure Passphrase Change") }
    private fun showChangeDecoy() { toast("Coming soon: Decoy Passphrase Change") }
    private fun showRecoveryKey() {
        val key = SessionManager.recoveryKey
        android.app.AlertDialog.Builder(this)
            .setTitle("Emergency Recovery Key")
            .setMessage("Keep this safe. If you forget your passphrase, this is the ONLY way to recover your data.\n\n$key")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showBuiltInTriggers() {
        val list = "love, miss you, good night, good morning, sad, kiss"
        android.app.AlertDialog.Builder(this)
            .setTitle("Built-in Triggers")
            .setMessage(list)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAddCustomEmojiTrigger() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etEmoji = EditText(this).apply { hint = "Emoji (e.g. 🦄)" }
        val etAnim = EditText(this).apply { hint = "Anim Type (HEART, STAR, RAINBOW...)" }
        layout.addView(etEmoji); layout.addView(etAnim)

        android.app.AlertDialog.Builder(this)
            .setTitle("Add Custom Trigger")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                toast("Trigger added!")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNicknamePicker() {
        val et = EditText(this).apply {
            setText(SessionManager.localNickname)
            hint = "Your Nickname"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Your Identity")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                SessionManager.localNickname = et.text.toString().trim()
                rebuildUI()
            }
            .show()
    }

    private fun showPartnerNicknamePicker() {
        val et = EditText(this).apply {
            setText(SessionManager.partnerNickname)
            hint = "Partner Nickname"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Partner Identity")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                SessionManager.partnerNickname = et.text.toString().trim()
                rebuildUI()
            }
            .show()
    }

    private fun triggerManualSync() {
        toast("Sync triggered")
    }

    private fun confirmFullWipe() {
        android.app.AlertDialog.Builder(this)
            .setTitle("ERASE ALL DATA?")
            .setMessage("This will permanently delete everything from your phone and your vault USB. This cannot be undone.")
            .setPositiveButton("DELETE EVERYTHING") { _, _ ->
                toast("Wiping data...")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rebuildUI() {
        scrollLayout.removeAllViews()
        buildUI()
        ThemeApplicator.applyActive(this)
    }

    private fun getLastSyncTime(): String {
        val ts = SessionManager.lastSyncTimestamp
        return if (ts == 0L) "Never" else java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
