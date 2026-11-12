package com.calcvault.ui.call

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.memory.MemoryGuard

/** Legacy entry point redirected away from public-IP raw TCP calling. */
class SimpleCallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MemoryGuard.secureWindow(this)
        Toast.makeText(
            this,
            "Direct-IP calling is disabled. Opening the privacy-aware WebRTC call screen.",
            Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(this, CallActivity::class.java).apply {
                putExtra("is_incoming", intent.getBooleanExtra("is_incoming", false))
                putExtra("is_video", false)
                putExtra("call_id", intent.getStringExtra("call_id") ?: "")
                putExtra("from_user", intent.getStringExtra("from_user") ?: "")
            }
        )
        finish()
    }
}
