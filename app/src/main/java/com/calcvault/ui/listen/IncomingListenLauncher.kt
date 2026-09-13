package com.calcvault.ui.listen

import android.app.Activity
import android.content.Intent
import com.calcvault.listen.ListenTogetherCrypto
import org.json.JSONObject

object IncomingListenLauncher {
    @Volatile
    private var lastLaunchedSessionId: String = ""

    fun launchIfNeeded(activity: Activity, signal: JSONObject, fallbackRemoteUser: String): Boolean {
        if (signal.optString("type") != "listen_invite") return false

        val payload = ListenTogetherCrypto.unwrap(signal.optJSONObject("payload")) ?: JSONObject()
        val sessionId = signal.optString("sessionId").ifBlank { payload.optString("sessionId") }
        if (sessionId.isBlank() || sessionId == lastLaunchedSessionId) return false

        val fromUser = signal.optString("from").ifBlank { fallbackRemoteUser }
        lastLaunchedSessionId = sessionId

        activity.startActivity(
            Intent(activity, ListenTogetherActivity::class.java).apply {
                putExtra("is_incoming", true)
                putExtra("session_id", sessionId)
                putExtra("from_user", fromUser)
                putExtra("invite_payload", payload.toString())
            }
        )
        return true
    }

    fun clear(sessionId: String?) {
        if (!sessionId.isNullOrBlank() && lastLaunchedSessionId == sessionId) {
            lastLaunchedSessionId = ""
        }
    }
}
