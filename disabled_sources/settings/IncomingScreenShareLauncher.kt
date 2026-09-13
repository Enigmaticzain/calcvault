package com.calcvault.ui.call

import android.app.Activity
import android.content.Intent
import org.json.JSONObject

object IncomingScreenShareLauncher {
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

        activity.startActivity(
            Intent(activity, ScreenShareActivity::class.java).apply {
                putExtra(ScreenShareActivity.EXTRA_ROLE, ScreenShareActivity.ROLE_VIEWER)
                putExtra(ScreenShareActivity.EXTRA_SESSION_ID, sessionId)
                putExtra(ScreenShareActivity.EXTRA_REMOTE_USER, fromUser)
            }
        )
        return true
    }

    fun clearAccepted(sessionId: String?) {
        if (!sessionId.isNullOrBlank() && sessionId == lastLaunchedAcceptedSessionId) {
            lastLaunchedAcceptedSessionId = ""
        }
    }
}
