package com.calcvault.ui.notes

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.ui.common.GlassUi

class NoteEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        setContentView(root)
        GlassUi.applyThemeChrome(this, root)

        val etNoteContent = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
            hint = "Note content"
            val content = intent.getStringExtra("content")
            setText(content)
        }
        root.addView(etNoteContent)

        val btnSaveNote = Button(this).apply {
            text = "Save Note"
            setOnClickListener {
                // Database save logic would go here
                finish()
            }
        }
        root.addView(btnSaveNote)
    }

    override fun onDestroy() {
        super.onDestroy()
        ThemeApplicator.detach(this)
    }
}
