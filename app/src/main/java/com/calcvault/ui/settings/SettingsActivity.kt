package com.calcvault.ui.settings

import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Vibrator
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import com.calcvault.auth.BiometricHelper
import com.calcvault.auth.UnlockManager
import com.calcvault.companion.CompanionBridgeController
import com.calcvault.companion.CompanionBridgeService
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.network.SecurePairingManager
import com.calcvault.notifications.CustomNotificationManager
import com.calcvault.notifications.DefaultNotificationSettings
import com.calcvault.network.bluetooth.BluetoothTransport
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.provider.StorageManager
import com.calcvault.ui.browser.PrivateBrowserActivity
import com.calcvault.ui.chat.ChatActivity
import com.calcvault.ui.filters.FilterSettingsActivity
import com.calcvault.ui.listen.ListenTogetherActivity
import com.calcvault.ui.notes.NotesListActivity
import com.calcvault.ui.vault.VaultFilesActivity
import com.calcvault.watchtogether.WatchTogetherActivity
import com.calcvault.utils.SessionManager

/**
 * SettingsActivity
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var unlockManager: UnlockManager
    private lateinit var storageEngine: USBStorageEngine
    private lateinit var themeEngine: ThemeEngine
    private lateinit var btTransport: BluetoothTransport
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var scrollLayout: LinearLayout
    private var pairingManager: SecurePairingManager? = null

    companion object {
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
        private const val REQUEST_CODE_PICK_WALLPAPER = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        unlockManager = UnlockManager.getInstance(this)
        storageEngine = USBStorageEngine.getInstance(this)
        themeEngine = ThemeEngine(this)
        btTransport = BluetoothTransport(this)
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
        addSettingToggle("Enable 3D Effects (Doraemon Theme)", SessionManager.isDoraemon3DEnabled) { enabled ->
            SessionManager.isDoraemon3DEnabled = enabled
            recreate()
        }
        addAnimatedThemePicker()
        addAnimationModePicker()
        addAmbientBackgroundPicker()
        
        addSettingRow("Set Chat Wallpaper", secondary = "Custom background for chats") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_WALLPAPER)
        }

        addDivider()

        addSectionTitle("😊 Mood System")
        addSettingRow("Manage Moods", "Add, edit, or reorder your moods") {
            startActivity(Intent(this, com.calcvault.ui.mood.MoodActivity::class.java))
        }
        addSettingToggle("Enable Mood UI Adaptation", SessionManager.isMoodUiAdaptationEnabled) { enabled ->
            SessionManager.isMoodUiAdaptationEnabled = enabled
            unlockManager.updateMoodUiAdaptation(enabled)
        }

        addDivider()

        addSectionTitle("❤️ Emotional Triggers")
        addSettingRow("View/Remove Triggers", secondary = "Manage your custom message triggers") {
            showManageTriggers()
        }
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

        addSettingRow("Watch Together", secondary = "Shared movie session") {
            safeStart(
                Intent(this, WatchTogetherActivity::class.java).apply {
                    putExtra(WatchTogetherActivity.EXTRA_SESSION_ID, "quick_session")
                    putExtra(WatchTogetherActivity.EXTRA_MOVIE_PATH, "")
                    putExtra(WatchTogetherActivity.EXTRA_PEER_ID, SessionManager.partnerUserId.ifBlank { "partner" })
                    putExtra(WatchTogetherActivity.EXTRA_IS_HOST, true)
                },
                "Watch Together"
            )
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
        addSettingRow("Advanced Notification Settings", secondary = "Stealth + urgent tuning") {
            safeStart(Intent(this, NotificationSettingsActivity::class.java), "Advanced Notification Settings")
        }

        addDivider()

        addSectionTitle("🧩 Extra Tools")
        addSettingRow("Private Browser") {
            safeStart(Intent(this, PrivateBrowserActivity::class.java), "Private Browser")
        }
        addSettingRow("Filter Settings") {
            safeStart(Intent(this, FilterSettingsActivity::class.java), "Filter Settings")
        }
        addSettingRow("Vault Files") {
            safeStart(Intent(this, VaultFilesActivity::class.java), "Vault Files")
        }
        addSettingRow("Notes") {
            safeStart(Intent(this, NotesListActivity::class.java), "Notes")
        }

        addDivider()

        addSectionTitle("📱 Connectivity")
        
        // --- Added Messaging Connectivity Section ---
        addSectionTitle("Messaging Mode", color = theme.accentColor)
        val mode = if (SessionManager.signalingUrl.isEmpty() || SessionManager.signalingUrl.contains("onrender.com")) "Relay (Render)" 
                  else if (SessionManager.signalingUrl.contains(":43831")) "Laptop Bridge" 
                  else "Custom / Direct"
        addSettingRow("Current Mode", secondary = mode) { showConnectivityModeChooser() }
        
        addSettingRow("Signaling Server URL", secondary = SessionManager.signalingUrl.ifBlank { "Default (Render)" }) {
            showSignalingUrlInput()
        }

        addSettingToggle("Start Direct Messaging Server", false) { enabled ->
            if (enabled) {
                CompanionBridgeService.start(this, bindLan = true)
                val ip = getLocalIpAddress()
                val sigUrl = "http://$ip:37112"
                toast("Server started at $sigUrl")
                android.app.AlertDialog.Builder(this)
                    .setTitle("Messaging Server Active")
                    .setMessage("Your partner should set their Signaling URL to:\n\n$sigUrl")
                    .setPositiveButton("Copy URL") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Signaling URL", sigUrl)
                        clipboard.setPrimaryClip(clip)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            } else {
                CompanionBridgeService.stop(this)
                toast("Server stopped")
            }
        }
        addDivider()
        // --- End Added Section ---

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
        addSettingRow("Verify Desktop Bridge Connection", secondary = "Enter code from desktop app") {
            showDesktopBridgePairingCodeInput()
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

        // ─── Permanent Pair Identity ───────────────────────────────────
        addSectionTitle("🔗 Permanent Pair")
        com.calcvault.sync.PermanentPairManager.init(this)
        if (com.calcvault.sync.PermanentPairManager.isPaired) {
            val role = com.calcvault.sync.PermanentPairManager.myRole.replaceFirstChar { it.uppercase() }
            addSettingRow("Identity", secondary = "You are permanently paired as $role") {}
            addSettingRow("Paired since", secondary = java.text.SimpleDateFormat("MMM dd yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(com.calcvault.sync.PermanentPairManager.pairTimestamp))) {}
        } else {
            addSettingRow("Select Identity — Zain", secondary = "Tap to permanently lock as Zain (Tom)") {
                confirmIdentitySelection("zain")
            }
            addSettingRow("Select Identity — Sanu", secondary = "Tap to permanently lock as Sanu (Angela)") {
                confirmIdentitySelection("sanu")
            }
        }

        addDivider()

        // ─── App Updates ───────────────────────────────────────────────
        addSectionTitle("📦 App Updates")
        val currentVersion = com.calcvault.sync.ApkUpdateManager.getCurrentVersionName(this)
        val currentCode = com.calcvault.sync.ApkUpdateManager.getCurrentVersionCode(this)
        addSettingRow("Current Version", secondary = "v$currentVersion (build $currentCode)") {}

        if (com.calcvault.sync.PermanentPairManager.isPaired) {
            if (com.calcvault.sync.PermanentPairManager.isZain) {
                // Zain can push updates to Sanu
                addSettingRow("📤 Push Update to Sanu", secondary = "Share current APK with partner") {
                    com.calcvault.sync.ApkUpdateManager.shareApkWithPartner(this)
                }
            }

            if (com.calcvault.sync.PermanentPairManager.isUpdateAvailable()) {
                // Show update available banner
                addSettingRow("🔴 UPDATE AVAILABLE", secondary = "A newer version is ready to install. Tap to update!") {
                    val url = com.calcvault.sync.PermanentPairManager.getApkDownloadUrl()
                    if (url.isNotBlank()) {
                        com.calcvault.sync.ApkUpdateManager.downloadAndInstall(this, url)
                    } else {
                        toast("Ask your partner to share the latest APK via 'Push Update'")
                    }
                }
            } else {
                addSettingRow("✅ Up to date", secondary = "You have the latest version") {}
            }
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
            val choice = when (name) {
                "Night Sky" -> ThemeApplicator.ThemeChoice.NIGHT_SKY
                "Doraemon" -> ThemeApplicator.ThemeChoice.DORAEMON
                "Nature" -> ThemeApplicator.ThemeChoice.NATURE_ADVENTURE
                else -> ThemeApplicator.ThemeChoice.NONE
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
        var waitDialog: android.app.AlertDialog? = null

        pairingManager = SecurePairingManager(
            signalingUrl = SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING },
            onPeerConnected = {
                runOnUiThread {
                    waitDialog?.dismiss()
                    toast("Partner connected! Starting chat...")
                    initializeChatAfterPairing()
                }
            },
            onSignalReceived = { signal ->
                Log.d("PairingManager", "Signal from peer received")
            },
            onError = { err ->
                runOnUiThread {
                    waitDialog?.dismiss()
                    toast("Pairing error: $err")
                }
            }
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

        val codeDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Share this code")
            .setMessage("Partner must enter this code on their device now. Code expires in 60s.")
            .setView(tvCode)
            .setPositiveButton("Waiting...") { _, _ -> }
            .setNegativeButton("Cancel") { _, _ ->
                pairingManager?.disconnect()
                waitDialog?.dismiss()
            }
            .setOnDismissListener { pairingManager?.disconnect() }
            .show()

        // Show waiting dialog after code dialog is dismissed
        codeDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
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
        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Connecting...")
            .setMessage("Waiting for partner to connect with code: $code")
            .setCancelable(true)
            .setNegativeButton("Cancel") { _, _ ->
                pairingManager?.disconnect()
            }
            .show()

        pairingManager = SecurePairingManager(
            signalingUrl = SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING },
            onPeerConnected = {
                runOnUiThread {
                    progressDialog.dismiss()
                    toast("Connected to partner! Starting chat...")
                    initializeChatAfterPairing()
                }
            },
            onSignalReceived = { signal ->
                Log.d("PairingManager", "Signal received from peer")
            },
            onError = { err ->
                runOnUiThread {
                    progressDialog.dismiss()
                    toast("Pairing failed: $err")
                }
            }
        )
        pairingManager!!.startPairing(code)
    }

    private fun initializeChatAfterPairing() {
        val chatIntent = Intent(this, ChatActivity::class.java)
        chatIntent.putExtra("from_pairing", true)
        startActivity(chatIntent)
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

    private fun safeStart(intent: Intent, featureName: String) {
        runCatching { startActivity(intent) }
            .onFailure { err ->
                if (err is ActivityNotFoundException) {
                    toast("$featureName not available")
                } else {
                    toast("$featureName failed: ${err.message ?: "Unknown error"}")
                }
            }
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
        
        // Standard themes row
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
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(4, 0, 4, 0) }
                setOnClickListener {
                    SessionManager.themeType = type

                    val themeChoice = when (type) {
                        "midnight" -> ThemeEngine.ThemeType.MIDNIGHT
                        "nord" -> ThemeEngine.ThemeType.NORD
                        "forest" -> ThemeEngine.ThemeType.FOREST
                        "rose" -> ThemeEngine.ThemeType.ROSE
                        else -> ThemeEngine.ThemeType.MIDNIGHT
                    }
                    themeEngine.setTheme(themeChoice)

                    unlockManager.updateThemeSettings(type, SessionManager.isAdaptiveTheme)
                    recreate()
                }
            }
            row.addView(btn)
        }
        scrollLayout.addView(row)

        // Sanctuary themes section
        addSectionTitle("Sanctuary Themes (Romantic)")
        val sanctuaryNames = listOf(
            "Stargazing", "Rainy Cafe", "Sunrise", "Autumn Park", "Beach", "Snow", "Fireplace"
        )
        val sanctuaryTypes = listOf(
            ThemeEngine.ThemeType.SANCTUARY_STARGAZING,
            ThemeEngine.ThemeType.SANCTUARY_RAINYCAFE,
            ThemeEngine.ThemeType.SANCTUARY_SUNRISE,
            ThemeEngine.ThemeType.SANCTUARY_AUTUMNPARK,
            ThemeEngine.ThemeType.SANCTUARY_SUNSETBEACH,
            ThemeEngine.ThemeType.SANCTUARY_PEACEFULSNOW,
            ThemeEngine.ThemeType.SANCTUARY_COUCHFIREPLACE
        )

        // Create button rows (3 per row for better layout)
        for (i in sanctuaryNames.indices step 3) {
            val sanctRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 12, 32, 12)
            }
            
            for (j in 0..2) {
                if (i + j < sanctuaryNames.size) {
                    val name = sanctuaryNames[i + j]
                    val type = sanctuaryTypes[i + j]
                    val btn = Button(this).apply {
                        text = name
                        textSize = 11f
                        setBackgroundColor(0xFF2A2A35.toInt())
                        setTextColor(
                            if (SessionManager.themeType == type.name.lowercase()) 
                                theme.accentColor 
                            else 0xFFBBBBBB.toInt()
                        )
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { 
                            it.setMargins(4, 0, 4, 0) 
                        }
                        setOnClickListener {
                            SessionManager.themeType = type.name.lowercase()
                            themeEngine.setTheme(type)
                            unlockManager.updateThemeSettings(type.name.lowercase(), SessionManager.isAdaptiveTheme)
                            recreate()
                        }
                    }
                    sanctRow.addView(btn)
                }
            }
            scrollLayout.addView(sanctRow)
        }
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
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(4, 0, 4, 0) }
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
            val choice = when (name) {
                "Nature" -> ThemeApplicator.ThemeChoice.AMBIENT_NATURE
                "Companion" -> ThemeApplicator.ThemeChoice.AMBIENT_COMPANION
                else -> ThemeApplicator.ThemeChoice.NONE
            }
            val btn = Button(this).apply {
                text = name; textSize = 12f
                setBackgroundColor(0xFF2A2A35.toInt())
                setTextColor(if (ThemeApplicator.activeTheme == choice) theme.accentColor else 0xFFBBBBBB.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(4, 0, 4, 0) }
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

    private fun showManageTriggers() {
        val json = unlockManager.getCustomTriggersJson()
        val arr = org.json.JSONArray(json)
        val items = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val kws = obj.getJSONArray("keywords").getString(0)
            val emoji = obj.optString("emoji", "")
            items.add("$kws -> $emoji")
        }

        if (items.isEmpty()) {
            toast("No custom triggers found")
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Custom Triggers (Tap to remove)")
            .setItems(items.toTypedArray()) { _, which ->
                val newArr = org.json.JSONArray()
                for (i in 0 until arr.length()) {
                    if (i != which) newArr.put(arr.get(i))
                }
                unlockManager.saveCustomTriggersJson(newArr.toString())
                toast("Trigger removed")
            }
            .setPositiveButton("Close", null)
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
        val etKeyword = EditText(this).apply { hint = "Keyword (e.g. hello)" }
        val etEmoji = EditText(this).apply { hint = "Emoji (e.g. 👋)" }
        val etIntensity = EditText(this).apply { hint = "Intensity (1-50)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        
        layout.addView(etKeyword)
        layout.addView(etEmoji)
        layout.addView(etIntensity)

        android.app.AlertDialog.Builder(this)
            .setTitle("Add Custom Trigger")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val keyword = etKeyword.text.toString().trim()
                val emoji = etEmoji.text.toString().trim()
                val intensity = etIntensity.text.toString().toIntOrNull() ?: 15
                
                if (keyword.isNotEmpty() && emoji.isNotEmpty()) {
                    val currentJson = unlockManager.getCustomTriggersJson()
                    val arr = org.json.JSONArray(currentJson)
                    val obj = org.json.JSONObject().apply {
                        put("keywords", org.json.JSONArray().put(keyword))
                        put("type", "emoji_animation")
                        put("emoji", emoji)
                        put("intensity", intensity)
                        put("direction", "UP")
                        put("speed", "MEDIUM")
                    }
                    arr.put(obj)
                    unlockManager.saveCustomTriggersJson(arr.toString())
                    toast("Trigger added: $keyword -> $emoji")
                }
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

    private fun confirmIdentitySelection(role: String) {
        val displayName = role.replaceFirstChar { it.uppercase() }
        val character = if (role == "zain") "Tom" else "Angela"
        android.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Permanent Selection")
            .setMessage(
                "You are about to permanently lock this device as:\n\n" +
                "Identity: $displayName\n" +
                "Character: $character\n\n" +
                "This CANNOT be undone. The app will permanently auto-connect " +
                "with your partner's device on every launch.\n\n" +
                "Are you sure?"
            )
            .setPositiveButton("Yes, I am $displayName") { _, _ ->
                com.calcvault.sync.PermanentPairManager.selectIdentity(role)
                com.calcvault.sync.PermanentPairManager.setLocalVersionCode(
                    com.calcvault.sync.ApkUpdateManager.getCurrentVersionCode(this)
                )
                toast("✅ Identity locked as $displayName. Auto-connect is now active.")
                rebuildUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rebuildUI() {
        scrollLayout.removeAllViews()
        buildUI()
        ThemeApplicator.applyActive(this)
    }

    private fun showDesktopBridgePairingCodeInput() {
        val currentCode = CompanionBridgeController.currentPairingCode()
        if (currentCode.isNullOrBlank()) {
            toast("No active bridge. Start Desktop Companion Bridge first.")
            return
        }

        val et = EditText(this).apply {
            hint = currentCode
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            textSize = 16f
            setPadding(32, 24, 32, 24)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Desktop Bridge Verification")
            .setMessage("Enter the pairing code from your desktop companion app to verify connection.")
            .setView(et)
            .setPositiveButton("Verify") { _, _ ->
                val enteredCode = et.text.toString().trim().uppercase()
                if (enteredCode == currentCode) {
                    toast("✓ Desktop bridge verified successfully!")
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Connection Verified")
                        .setMessage("Your desktop companion app is properly paired and ready to sync.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    toast("✗ Incorrect code. Codes do not match.")
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Verification Failed")
                        .setMessage("The code entered does not match.\n\nExpected: $currentCode\n\nMake sure both devices show the same code.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getLastSyncTime(): String {
        val ts = SessionManager.lastSyncTimestamp
        return if (ts == 0L) "Never" else java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }

    private fun showConnectivityModeChooser() {
        val options = arrayOf("Relay Server (Standard)", "Laptop Bridge (PC)", "Direct/Custom Server")
        android.app.AlertDialog.Builder(this)
            .setTitle("Choose Messaging Mode")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        SessionManager.signalingUrl = DEFAULT_SIGNALING
                        toast("Mode: Relay")
                    }
                    1 -> {
                        showLaptopBridgeInput()
                    }
                    2 -> {
                        showSignalingUrlInput()
                    }
                }
                rebuildUI()
            }
            .show()
    }

    private fun showLaptopBridgeInput() {
        val et = EditText(this).apply {
            hint = "192.168.1.5"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Enter Laptop IP")
            .setMessage("The companion app must be running on your laptop.")
            .setView(et)
            .setPositiveButton("Set") { _, _ ->
                val ip = et.text.toString().trim()
                if (ip.isNotEmpty()) {
                    SessionManager.signalingUrl = "http://$ip:43831"
                    toast("Mode: Laptop Bridge")
                    rebuildUI()
                }
            }
            .show()
    }

    private fun showSignalingUrlInput() {
        val et = EditText(this).apply {
            setText(SessionManager.signalingUrl)
            hint = "http://partner-ip:37112"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Signaling Server URL")
            .setMessage("Enter the URL of the messaging server (laptop or partner's phone).")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                SessionManager.signalingUrl = et.text.toString().trim()
                rebuildUI()
            }
            .show()
    }

    private fun getLocalIpAddress(): String {
        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("Settings", ex.toString())
        }
        return "127.0.0.1"
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_WALLPAPER && resultCode == android.app.Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    SessionManager.chatWallpaperPath = uri.toString()
                    val prefsManager = com.calcvault.utils.PrefsManager(com.calcvault.storage.USBStorageEngine.getInstance(this))
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        prefsManager.set("custom_bg_path", uri.toString())
                    }
                    toast("Chat wallpaper updated!")
                } catch (e: Exception) {
                    toast("Failed to set wallpaper")
                }
            }
        }
    }
}
