package com.calcvault.ui.main

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.emotional.themes.ThemeApplicator

/**
 * DecoyActivity
 */
class DecoyActivity : AppCompatActivity() {

    data class FakeMessage(val sender: String, val text: String, val time: String, val isMine: Boolean)

    private val fakeMessages = listOf(
        FakeMessage("Alex", "Hey, did you finish that report?", "Mon 09:14", false),
        FakeMessage("Me", "Yeah, sent it over this morning", "Mon 09:16", true),
        FakeMessage("Alex", "Perfect. See you at the meeting at 2?", "Mon 09:17", false),
        FakeMessage("Me", "Yep, I'll be there", "Mon 09:18", true),
        FakeMessage("Alex", "Grabbing lunch? I know a good place nearby", "Mon 12:30", false),
        FakeMessage("Me", "Sure, where?", "Mon 12:31", true),
        FakeMessage("Alex", "That Italian spot on 5th, 1pm?", "Mon 12:32", false),
        FakeMessage("Me", "Sounds good 👍", "Mon 12:33", true),
        FakeMessage("Alex", "Great, see you then!", "Mon 12:33", false),
        FakeMessage("Me", "Just finished lunch, that place was solid", "Mon 14:05", true),
        FakeMessage("Alex", "Right? Their pasta is legit", "Mon 14:07", false),
        FakeMessage("Me", "Definitely going back", "Mon 14:08", true),
        FakeMessage("Alex", "Meeting in 5 btw", "Mon 13:55", false),
        FakeMessage("Me", "On my way", "Mon 13:56", true),
        FakeMessage("Alex", "Good presentation today btw", "Mon 16:30", false),
        FakeMessage("Me", "Thanks, been working on it all week", "Mon 16:31", true),
        FakeMessage("Alex", "Talk tomorrow!", "Mon 17:00", false),
        FakeMessage("Me", "Night 👋", "Mon 17:01", true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        buildDecoyUI()

        // Fix: Apply theme AFTER building UI
        ThemeApplicator.applyActive(this)
    }

    private fun buildDecoyUI() {
        val root = FrameLayout(this)
        // Background color for non-theme mode
        root.setBackgroundColor(0xFF1C1C1E.toInt())
        setContentView(root)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Use semi-transparency if theme is active
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) 0x00000000 else 0x44000000.toInt())
        }
        root.addView(layout)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) 0xFF2C2C2E.toInt() else 0xAA2C2C2E.toInt())
            setPadding(24, 48, 24, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        layout.addView(header)

        val tvBack = TextView(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 16, 0)
        }
        header.addView(tvBack)

        val tvAvatar = TextView(this).apply {
            text = "A"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4466AA.toInt())
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(80, 80)
        }
        header.addView(tvAvatar)

        val nameStatusLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 0, 0, 0)
        }
        header.addView(nameStatusLayout)

        val tvName = TextView(this).apply {
            text = "Alex"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        nameStatusLayout.addView(tvName)

        val tvLastSeen = TextView(this).apply {
            text = "Last seen today at 17:01"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }
        nameStatusLayout.addView(tvLastSeen)

        // Messages scroll
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        layout.addView(scroll)

        val msgContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scroll.addView(msgContainer)

        for (msg in fakeMessages) {
            val msgLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = if (msg.isMine) Gravity.END else Gravity.START
                setPadding(0, 4, 0, 4)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            msgContainer.addView(msgLayout)

            val tvMsgText = TextView(this).apply {
                text = msg.text
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(if (msg.isMine) 0xFF2A3A5C.toInt() else 0xFF2C2C2E.toInt())
                setPadding(20, 12, 20, 12)
                maxWidth = 700
            }
            msgLayout.addView(tvMsgText)

            val tvMsgTime = TextView(this).apply {
                text = msg.time
                textSize = 11f
                setTextColor(0xFF555555.toInt())
            }
            msgLayout.addView(tvMsgTime)
        }

        // Input bar
        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) 0xFF2C2C2E.toInt() else 0xAA2C2C2E.toInt())
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
        }
        layout.addView(inputBar)

        val etInput = EditText(this).apply {
            hint = "Message"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF555555.toInt())
            background = null
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        inputBar.addView(etInput)

        val tvSend = TextView(this).apply {
            text = "➤"
            textSize = 20f
            setTextColor(0xFF4466AA.toInt())
            setPadding(16, 0, 0, 0)
        }
        inputBar.addView(tvSend)
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
