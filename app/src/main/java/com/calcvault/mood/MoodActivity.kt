package com.calcvault.ui.mood

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.emotional.EmotionalAnimationEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.USBStorageEngine
import com.calcvault.ui.ambient.AmbientAnimationView
import android.content.Context
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * MoodActivity
 *
 * Mood selection screen.
 */
class MoodActivity : AppCompatActivity() {

    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var networkEngine: NetworkMessageEngine
    private lateinit var themeEngine: ThemeEngine
    private lateinit var animEngine: EmotionalAnimationEngine

    private val localUserId = SessionManager.localUserId.ifBlank { "USER_A" }
    private var selectedMood = ""

    data class MoodOption(val emoji: String, val label: String, val color: Int)

    private val defaultMoods = listOf(
        MoodOption("😐", "Neutral", 0xCC2A2A2A.toInt()),
        MoodOption("😊", "Happy", 0xCC2A3A1A.toInt()),
        MoodOption("😔", "Sad", 0xCC1A2A3A.toInt()),
        MoodOption("😡", "Angry", 0xCC3A1A1A.toInt()),
        MoodOption("❤️", "Love", 0xCC3A1A2A.toInt()),
        MoodOption("😴", "Sleepy", 0xCC1A1A3A.toInt()),
        MoodOption("🎯", "Busy", 0xCC2A2A1A.toInt()),
        MoodOption("🧠", "Focus", 0xCC1A2A2A.toInt())
    )

    private fun getCustomMoods(): List<MoodOption> {
        val prefs = getSharedPreferences("mood_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_moods", "[]")
        val list = mutableListOf<MoodOption>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(MoodOption(obj.getString("emoji"), obj.getString("label"), obj.getInt("color")))
            }
        } catch (e: Exception) {}
        return list
    }

    private fun saveCustomMood(mood: MoodOption) {
        val list = getCustomMoods().toMutableList()
        list.add(mood)
        saveMoodList(list)
    }

    private fun removeCustomMood(mood: MoodOption) {
        val list = getCustomMoods().toMutableList()
        list.removeAll { it.label == mood.label && it.emoji == mood.emoji }
        saveMoodList(list)
    }

    private fun saveMoodList(list: List<MoodOption>) {
        val arr = JSONArray()
        list.forEach { m ->
            val obj = JSONObject()
            obj.put("emoji", m.emoji)
            obj.put("label", m.label)
            obj.put("color", m.color)
            arr.put(obj)
        }
        val prefs = getSharedPreferences("mood_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_moods", arr.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        networkEngine = NetworkMessageEngine(this, messageDB, storageEngine)
        themeEngine = ThemeEngine(this)

        buildUI()

        // Fix: Apply theme AFTER buildUI (which calls setContentView)
        ThemeApplicator.applyActive(this)
    }

    private lateinit var layout: LinearLayout

    private fun buildUI() {
        val theme = themeEngine.getCurrentTheme()
        val root = FrameLayout(this)
        setContentView(root)

        if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
            val ambient = AmbientAnimationView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                visibility = if (SessionManager.isAmbientEnabled) View.VISIBLE else View.GONE
                AmbientAnimationView.configureCalmPreset(this)
            }
            root.addView(ambient)
        }

        animEngine = EmotionalAnimationEngine(this, root)

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(scroll)

        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 64, 32, 120) // Increased bottom padding
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        scroll.addView(layout)

        val tvTitle = TextView(this).apply {
            text = "How are you feeling?"
            textSize = 22f
            setTextColor(theme.primaryText)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        layout.addView(tvTitle)

        val partnerId = SessionManager.partnerUserId.ifBlank { "USER_B" }
        val currentMood = messageDB.getLatestMood(partnerId) ?: "Unknown"
        val tvPartnerMood = TextView(this).apply {
            text = "Partner: $currentMood"
            textSize = 14f
            setTextColor(theme.secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(tvPartnerMood)

        val grid = GridLayout(this).apply {
            columnCount = 2
        }
        layout.addView(grid)

        val allMoods = defaultMoods + getCustomMoods()

        for (mood in allMoods) {
            val moodLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(if (SessionManager.isAmbientEnabled && ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) mood.color else mood.color or 0xFF000000.toInt())
                setPadding(24, 32, 24, 32)
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params
                setOnClickListener { onMoodSelected(mood) }
                setOnLongClickListener {
                    if (getCustomMoods().contains(mood)) {
                        removeCustomMood(mood)
                        layout.removeAllViews()
                        buildUI()
                        true
                    } else false
                }
            }
            grid.addView(moodLayout)

            val tvEmoji = TextView(this).apply {
                text = mood.emoji
                textSize = 36f
                gravity = Gravity.CENTER
            }
            moodLayout.addView(tvEmoji)

            val tvLabel = TextView(this).apply {
                text = mood.label
                textSize = 13f
                setTextColor(0xFFCCCCCC.toInt())
                gravity = Gravity.CENTER
            }
            moodLayout.addView(tvLabel)
        }

        val btnAddCustom = Button(this).apply {
            text = "Add Custom Mood ➕"
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = 48
            }
            setOnClickListener { showAddCustomMoodDialog() }
        }
        layout.addView(btnAddCustom)
    }

    private fun showAddCustomMoodDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etEmoji = EditText(this).apply { hint = "Emoji (e.g. 🦄)" }
        val etLabel = EditText(this).apply { hint = "Label (e.g. Magical)" }
        dialogLayout.addView(etEmoji)
        dialogLayout.addView(etLabel)

        android.app.AlertDialog.Builder(this)
            .setTitle("New Custom Mood")
            .setView(dialogLayout)
            .setPositiveButton("Add") { _, _ ->
                val emoji = etEmoji.text.toString().trim()
                val label = etLabel.text.toString().trim()
                if (emoji.isNotEmpty() && label.isNotEmpty()) {
                    saveCustomMood(MoodOption(emoji, label, 0xCC4A2A4A.toInt()))
                    layout.removeAllViews()
                    buildUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onMoodSelected(mood: MoodOption) {
        selectedMood = mood.label

        val animType = when (mood.label) {
            "Love" -> EmotionalAnimationEngine.MoodType.LOVE
            "Happy" -> EmotionalAnimationEngine.MoodType.HAPPY
            "Sad" -> EmotionalAnimationEngine.MoodType.SAD
            "Angry" -> EmotionalAnimationEngine.MoodType.ANGRY
            "Sleepy" -> EmotionalAnimationEngine.MoodType.SLEEPY
            "Busy" -> EmotionalAnimationEngine.MoodType.BUSY
            "Focus" -> EmotionalAnimationEngine.MoodType.FOCUS
            else -> EmotionalAnimationEngine.MoodType.NEUTRAL
        }
        animEngine.applyMoodTheme(animType)

        lifecycleScope.launch {
            messageDB.saveMood(localUserId, "${mood.emoji} ${mood.label}")
            val encrypted = storageEngine.encrypt(mood.label.toByteArray())
            networkEngine.sendEncryptedPayload(
                System.currentTimeMillis(),
                encrypted,
                AppendOnlyMessageDB.MSG_MOOD
            )
            runOnUiThread {
                Toast.makeText(this@MoodActivity, "Mood updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeApplicator.applyActive(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ThemeApplicator.detach(this)
    }
}
