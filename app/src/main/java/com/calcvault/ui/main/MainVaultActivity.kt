package com.calcvault.ui.main

import android.content.Intent
import android.content.ActivityNotFoundException
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.calcvault.emotional.EmotionalAnimationEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.USBStorageEngine
import com.calcvault.ui.ambient.AmbientAnimationView
import com.calcvault.ui.calculator.CalculatorActivity
import com.calcvault.ui.chat.ChatActivity
import com.calcvault.ui.call.IncomingCallLauncher
import com.calcvault.ui.common.GlassUi
import com.calcvault.ui.listen.IncomingListenLauncher
import com.calcvault.ui.listen.ListenTogetherActivity
import com.calcvault.ui.media.MediaActivity
import com.calcvault.ui.media.RecordingsActivity
import com.calcvault.ui.mood.MoodActivity
import com.calcvault.ui.settings.SettingsActivity
import com.calcvault.ui.settings.NotificationSettingsActivity
import com.calcvault.ui.notes.NotesListActivity
import com.calcvault.ui.browser.PrivateBrowserActivity
import com.calcvault.ui.filters.FilterSettingsActivity
import com.calcvault.ui.vault.VaultFilesActivity
import com.calcvault.watchtogether.WatchTogetherActivity
import com.calcvault.house.DualCompanionHouseActivity
import com.calcvault.utils.SessionManager

class MainVaultActivity : AppCompatActivity() {

    private lateinit var themeEngine: ThemeEngine
    private lateinit var animEngine: EmotionalAnimationEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var networkEngine: NetworkMessageEngine
    private var rootLayout: FrameLayout? = null
    private var partnerUserId: String = "sanu"

    companion object {
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        themeEngine = ThemeEngine(this)
        messageDB = AppendOnlyMessageDB(USBStorageEngine.getInstance(this))
        networkEngine = NetworkMessageEngine(this, messageDB, USBStorageEngine.getInstance(this))
        
        refreshUI()
    }

    private fun initNetworkEngine() {
        val localUserId = SessionManager.localUserId.ifBlank { "zain" }
        partnerUserId = SessionManager.partnerUserId.ifBlank {
            if (localUserId == "zain") "sanu" else "zain"
        }
        
        networkEngine.initialize(
            localUserId,
            partnerUserId,
            SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING }
        )
        networkEngine.onControlSignalReceived = { signal ->
            runOnUiThread {
                val handledCall = IncomingCallLauncher.launchIfNeeded(
                    this,
                    signal,
                    partnerUserId
                )
                if (!handledCall) {
                    IncomingListenLauncher.launchIfNeeded(
                        this,
                        signal,
                        partnerUserId
                    )
                }
            }
        }
    }

    private fun refreshUI() {
        initNetworkEngine()
        buildModernUI()
        // Fix: Apply theme AFTER building UI
        GlassUi.applyThemeChrome(this)
    }

    private fun buildModernUI() {
        val theme = themeEngine.getCurrentTheme()
        val root = FrameLayout(this)
        rootLayout = root
        setContentView(root)

        // If no animated theme, use the static background
        if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
            root.setBackgroundColor(theme.backgroundStart)
        } else {
            root.setBackgroundColor(Color.TRANSPARENT)
        }

        if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
            val ambient = AmbientAnimationView(this).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                AmbientAnimationView.configureCalmPreset(this)
            }
            root.addView(ambient)
        }

        animEngine = EmotionalAnimationEngine(this, root)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(mainLayout)

        // Header Section
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 80, 32, 40)
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) Color.TRANSPARENT else Color.argb(80, 0, 0, 0))
        }
        mainLayout.addView(header)

        TextView(this).apply {
            text = "Secure Vault"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            header.addView(this)
        }

        val partnerName = SessionManager.partnerNickname.ifBlank { SessionManager.partnerUserId.ifBlank { "Partner" } }
        val rawMood = messageDB.getLatestMood(SessionManager.partnerUserId) ?: "Neutral"

        TextView(this).apply {
            text = "$partnerName is feeling: $rawMood"
            textSize = 15f
            setTextColor(theme.secondaryText)
            setPadding(0, 8, 0, 0)
            header.addView(this)
        }

        // Action Grid
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            setBackgroundColor(Color.TRANSPARENT)
        }
        mainLayout.addView(scroll)

        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(24, 0, 24, 40)
            useDefaultMargins = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        scroll.addView(grid)
        val cards = mutableListOf<CardView>()

        val items = listOf(
            VaultItem("💬", "Chat", "#0A84FF") { safeStart(Intent(this, ChatActivity::class.java), "Chat") },
            VaultItem("📞", "Call", "#30D158") { showCallChooser() },
            VaultItem("🎧", "Listen", "#00B8A9") { safeStart(Intent(this, ListenTogetherActivity::class.java), "Listen") },
            VaultItem("🎬", "Media", "#FF9500") { safeStart(Intent(this, MediaActivity::class.java), "Media") },
            VaultItem("🎥", "Recordings", "#FF7A00") { safeStart(Intent(this, RecordingsActivity::class.java), "Recordings") },
            VaultItem("📝", "Notes", "#AF52DE") { safeStart(Intent(this, NotesListActivity::class.java), "Notes") },
            VaultItem("🌐", "Browser", "#2EC4B6") { safeStart(Intent(this, PrivateBrowserActivity::class.java), "Browser") },
            VaultItem("🎞️", "Watch", "#5E60CE") {
                safeStart(
                    Intent(this, WatchTogetherActivity::class.java).apply {
                        putExtra(WatchTogetherActivity.EXTRA_SESSION_ID, "quick_session")
                        putExtra(WatchTogetherActivity.EXTRA_MOVIE_PATH, "")
                        putExtra(WatchTogetherActivity.EXTRA_PEER_ID, partnerUserId)
                        putExtra(WatchTogetherActivity.EXTRA_IS_HOST, true)
                    },
                    "Watch Together"
                )
            },
            VaultItem("🎨", "Filters", "#00A6FB") { safeStart(Intent(this, FilterSettingsActivity::class.java), "Filters") },
            VaultItem("🔔", "Alerts", "#FFD166") { safeStart(Intent(this, NotificationSettingsActivity::class.java), "Notification Settings") },
            VaultItem("❤️", "Mood", "#FF375F") { safeStart(Intent(this, MoodActivity::class.java), "Mood") },
            VaultItem("🏡", "House", "#F4A261") { safeStart(Intent(this, DualCompanionHouseActivity::class.java), "House") },
            VaultItem("🗂️", "Vault Files", "#90BE6D") { safeStart(Intent(this, VaultFilesActivity::class.java), "Vault Files") },
            VaultItem("⚙️", "Settings", "#8E8E93") { safeStart(Intent(this, SettingsActivity::class.java), "Settings") }
        )

        val displayMetrics = resources.displayMetrics
        val cardSize = (displayMetrics.widthPixels - 80) / 2

        for (item in items) {
            val card = CardView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardSize - 32
                    height = cardSize - 16
                    setMargins(16, 16, 16, 16)
                }
                radius = 30f
                cardElevation = 10f
                maxCardElevation = 18f
                useCompatPadding = true
                if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
                    setCardBackgroundColor(Color.argb(232, 34, 34, 44))
                } else {
                    setCardBackgroundColor(Color.argb(150, 255, 255, 255))
                }
                setOnClickListener { item.action() }
            }
            cards.add(card)

            val cardContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
            }
            card.addView(cardContent)

            TextView(this).apply {
                text = item.icon
                textSize = 44f
                cardContent.addView(this)
            }

            TextView(this).apply {
                text = item.label
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.DEFAULT_BOLD)
                setShadowLayer(3f, 0f, 1f, Color.BLACK)
                setPadding(0, 12, 0, 0)
                cardContent.addView(this)
            }

            grid.addView(card)
        }
        configureDynamicScroll(scroll, header, cards)

        // Bottom Lock Button
        val footer = FrameLayout(this).apply {
            setPadding(40, 20, 40, 40)
        }
        mainLayout.addView(footer)

        val lockButton = Button(this).apply {
            text = "LOCK VAULT"
            setTextColor(Color.WHITE)
            setBackgroundResource(android.R.drawable.btn_default)
            background.setTint(Color.parseColor("#FF453A"))
            setOnClickListener { lockAndReturn() }
            layoutParams = FrameLayout.LayoutParams(-1, 140)
        }
        GlassUi.styleButton(lockButton, danger = true)
        footer.addView(lockButton)
    }

    private data class VaultItem(val icon: String, val label: String, val color: String, val action: () -> Unit)

    private fun configureDynamicScroll(scroll: ScrollView, header: View, cards: List<CardView>) {
        fun applyEffects(scrollY: Int) {
            // Disabled aggressive moving effects to keep screen stable
            /*
            header.translationY = -scrollY * 0.34f
            header.scaleX = (1f - scrollY / 2400f).coerceIn(0.92f, 1f)
            header.scaleY = header.scaleX
            header.alpha = (1f - scrollY / 420f).coerceIn(0.52f, 1f)

            val viewportCenter = scrollY + scroll.height * 0.48f
            cards.forEach { card ->
                val cardCenter = card.top + card.height / 2f
                val distance = kotlin.math.abs(cardCenter - viewportCenter)
                val normalized = (distance / scroll.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                val direction = if (cardCenter < viewportCenter) -1f else 1f
                val scale = 1f - normalized * 0.16f
                card.scaleX = scale
                card.scaleY = scale
                card.translationY = direction * normalized * 34f
                card.translationX = direction * normalized * 8f
                card.rotationX = direction * normalized * 6f
                card.translationZ = 22f - normalized * 14f
                card.alpha = 1f - normalized * 0.28f
            }
            */
            // Very subtle header fade only
            header.alpha = (1f - scrollY / 1200f).coerceIn(0.85f, 1f)
        }
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ -> applyEffects(scrollY) }
        scroll.post { applyEffects(scroll.scrollY) }
    }

    private fun lockAndReturn() {
        USBStorageEngine.getInstance(this).lock()
        SessionManager.clear()
        startActivity(
            Intent(this, CalculatorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun showCallChooser() {
        val options = arrayOf("Voice Call", "Video Call")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Initiate Secure Call")
            .setItems(options) { _, which ->
                val intent = Intent(this, com.calcvault.ui.call.CallActivity::class.java).apply {
                    putExtra("is_incoming", false)
                    putExtra("is_video", which == 1)
                }
                safeStart(intent, "Call")
            }
            .show()
    }

    private fun safeStart(intent: Intent, featureName: String) {
        runCatching {
            startActivity(intent)
        }.onFailure { err ->
            if (err is ActivityNotFoundException) {
                showNotAvailable(featureName)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Unable to Open")
                    .setMessage("$featureName failed: ${err.message ?: "Unknown error"}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showNotAvailable(feature: String) {
        AlertDialog.Builder(this)
            .setTitle("Feature Not Available")
            .setMessage("$feature is currently being configured.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkEngine.disconnect()
        ThemeApplicator.detach(this)
    }
}
