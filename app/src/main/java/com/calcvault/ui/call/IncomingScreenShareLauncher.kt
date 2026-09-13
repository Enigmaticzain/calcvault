package com.calcvault.ui.call

import android.app.Activity
import com.calcvault.ui.settings.IncomingScreenShareLauncher as SettingsIncomingScreenShareLauncher
import org.json.JSONObject

object IncomingScreenShareLauncher {
    fun launchViewerIfAccepted(activity: Activity, signal: JSONObject): Boolean {
        return SettingsIncomingScreenShareLauncher.launchViewerIfAccepted(activity, signal)
    }

    fun clearAccepted(sessionId: String?) {
        SettingsIncomingScreenShareLauncher.clearAccepted(sessionId)
    }
}
