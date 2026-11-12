package com.calcvault.ui.watch

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.calcvault.network.privacy.PrivacyControlStore
import com.calcvault.network.privacy.PrivacyFeature
import com.calcvault.ui.settings.PrivacyControlsActivity
import com.calcvault.watchtogether.WatchTogetherActivity
import org.json.JSONObject

/** Prompts before joining; invitation metadata is not a movie transfer or authenticated identity. */
object IncomingWatchLauncher {
    @Volatile
    private var lastPromptedSessionId = ""

    fun promptIfNeeded(activity: Activity, signal: JSONObject, expectedPeerId: String): Boolean {
        if (signal.optString("type") != "watch_invite") return false
        val sessionId = signal.optString("sessionId")
        val senderId = signal.optString("from")
        val movieHash = signal.optString("movieHash")
        val movieSize = signal.optLong("movieSizeBytes", 0L)
        if (sessionId.isBlank() || senderId.isBlank() || senderId != expectedPeerId ||
            !movieHash.matches(Regex("[a-fA-F0-9]{64}")) || movieSize <= 0L ||
            sessionId == lastPromptedSessionId
        ) return true

        lastPromptedSessionId = sessionId
        val mode = PrivacyControlStore(activity).mode(PrivacyFeature.WATCH_TOGETHER)
        val routeDisclosure = when (mode) {
            com.calcvault.network.privacy.PrivacyMode.MAX_PRIVACY ->
                "Your saved maximum-privacy preference is selected; this app currently has no private Watch Together signaling route."
            else -> PrivacyControlStore(activity).let { store ->
                "Your saved Watch Together route preference is ${mode.name}."
            }
        }
        AlertDialog.Builder(activity)
            .setTitle("Watch Together invitation")
            .setMessage(
                "$senderId invites you to a synchronized watch room. Joining uses the configured signaling service, which can see your network IP, account IDs, room ID, movie SHA-256 hash and file size, timestamps, and playback-control/position traffic. " +
                    "The peer receives the room ID and movie hash/size; the movie name, path, and video bytes are not sent. You need the same local video. Peer identity is not cryptographically verified by this app.\n\n" +
                    "$routeDisclosure Proceeding is a one-time less-private route override for this room; it does not change your saved preference. Alternative: decline and wait until a private signaling route is available."
            )
            .setPositiveButton("Review privacy settings") { _, _ ->
                activity.startActivity(Intent(activity, PrivacyControlsActivity::class.java))
            }
            .setNeutralButton("Join") { _, _ ->
                activity.startActivity(
                    Intent(activity, WatchTogetherActivity::class.java).apply {
                        putExtra(WatchTogetherActivity.EXTRA_SESSION_ID, sessionId)
                        putExtra(WatchTogetherActivity.EXTRA_MOVIE_PATH, "")
                        putExtra(WatchTogetherActivity.EXTRA_PEER_ID, senderId)
                        putExtra(WatchTogetherActivity.EXTRA_IS_HOST, false)
                        putExtra(WatchTogetherActivity.EXTRA_PEER_MOVIE_HASH, movieHash.lowercase())
                        putExtra(WatchTogetherActivity.EXTRA_PEER_MOVIE_SIZE, movieSize)
                        putExtra(WatchTogetherActivity.EXTRA_SIGNALING_CONSENT, true)
                    }
                )
            }
            .setNegativeButton("Decline", null)
            .show()
        return true
    }
}
