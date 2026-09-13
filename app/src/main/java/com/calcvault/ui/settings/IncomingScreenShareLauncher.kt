package com.calcvault.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.ui.call.ScreenShareActivity
import org.json.JSONObject

class IncomingScreenShareLauncher : AppCompatActivity() {

    companion object {
        @Volatile
        private var lastLaunchedAcceptedSessionId: String = ""

        fun launchViewerIfAccepted(activity: Activity, signal: JSONObject): Boolean {
            if (signal.optString("type") != "screenshare_accept") return false

            val sessionId = signal.optString("sessionId")
            if (sessionId.isBlank() || sessionId == lastLaunchedAcceptedSessionId) {
                return false
            }

            lastLaunchedAcceptedSessionId = sessionId
            val fromUser = signal.optString("from")

            activity.startActivity(createViewerIntent(activity, sessionId, fromUser))
            return true
        }

        fun clearAccepted(sessionId: String?) {
            if (!sessionId.isNullOrBlank() && sessionId == lastLaunchedAcceptedSessionId) {
                lastLaunchedAcceptedSessionId = ""
            }
        }

        private fun createViewerIntent(
            activity: Activity,
            sessionId: String,
            fromUser: String
        ): Intent {
            return Intent(activity, ScreenShareActivity::class.java).apply {
                putExtra(ScreenShareActivity.EXTRA_ROLE, ScreenShareActivity.ROLE_VIEWER)
                putExtra(ScreenShareActivity.EXTRA_SESSION_ID, sessionId)
                putExtra(ScreenShareActivity.EXTRA_REMOTE_USER, fromUser)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchFromIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchFromIntent(intent)
        finish()
    }

    private fun launchFromIntent(intent: Intent?) {
        val type = intent?.getStringExtra("type").orEmpty()
        if (type.isNotBlank() && type != "screenshare_accept") return

        val sessionId = intent?.getStringExtra(ScreenShareActivity.EXTRA_SESSION_ID)
            ?.ifBlank { intent.getStringExtra("sessionId") }
            .orEmpty()
        val fromUser = intent?.getStringExtra(ScreenShareActivity.EXTRA_REMOTE_USER)
            ?.ifBlank { intent.getStringExtra("from") }
            .orEmpty()

        if (sessionId.isBlank() || sessionId == lastLaunchedAcceptedSessionId) return
        lastLaunchedAcceptedSessionId = sessionId

        startActivity(
            Intent(this, ScreenShareActivity::class.java).apply {
                putExtra(ScreenShareActivity.EXTRA_ROLE, ScreenShareActivity.ROLE_VIEWER)
                putExtra(ScreenShareActivity.EXTRA_SESSION_ID, sessionId)
                putExtra(ScreenShareActivity.EXTRA_REMOTE_USER, fromUser)
            }
        )
    }
}
