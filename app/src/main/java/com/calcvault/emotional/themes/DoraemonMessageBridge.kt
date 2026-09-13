package com.calcvault.emotional.themes

import android.util.Log

object DoraemonMessageBridge {
    private var doraemonView: DoraemonView? = null
    private const val TAG = "DoraemonMessageBridge"

    fun setDoraemonView(view: DoraemonView) {
        doraemonView = view
    }

    fun queueChatMessage(sender: String, text: String, displayDurationMs: Long = 3000L) {
        doraemonView?.queueChatMessage(sender, text, displayDurationMs)
            ?: Log.w(TAG, "DoraemonView not initialized for chat message")
    }

    fun clearView() {
        doraemonView = null
    }
}
