package com.calcvault.ui.chat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Compatibility launcher for legacy manifest route.
 * Forwards to the current chat implementation.
 */
class CharacterChatActivityTemplate : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, ChatActivity::class.java).apply {
                this@CharacterChatActivityTemplate.intent?.extras?.let { putExtras(it) }
            }
        )
        finish()
    }
}
