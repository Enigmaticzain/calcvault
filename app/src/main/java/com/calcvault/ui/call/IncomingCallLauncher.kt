package com.calcvault.ui.call

import android.app.Activity
import android.content.Intent
import org.json.JSONObject

object IncomingCallLauncher {
    @Volatile
    private var lastLaunchedCallId: String = ""

    fun launchIfNeeded(activity: Activity, signal: JSONObject, fallbackRemoteUser: String): Boolean {
        if (signal.optString("type") != "call_offer") return false

        val callId = signal.optString("callId")
        val callerIp = signal.optString("callerIp")
        if (callId.isBlank() || callerIp.isBlank() || callId == lastLaunchedCallId) {
            return false
        }

        lastLaunchedCallId = callId
        val isVideo = signal.optString("callType").equals("VIDEO", ignoreCase = true)
        val fromUser = signal.optString("from").ifBlank { fallbackRemoteUser }

        activity.startActivity(
            Intent(activity, CallActivity::class.java).apply {
                putExtra("is_incoming", true)
                putExtra("is_video", isVideo)
                putExtra("call_id", callId)
                putExtra("caller_ip", callerIp)
                putExtra("from_user", fromUser)
            }
        )
        return true
    }

    fun clear(callId: String?) {
        if (!callId.isNullOrBlank() && lastLaunchedCallId == callId) {
            lastLaunchedCallId = ""
        }
    }
}
