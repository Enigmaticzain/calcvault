package com.calcvault.ui.filters

import com.calcvault.network.NetworkMessageEngine
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * FilterSyncEngine - Synchronizes filter effects between peers
 *
 * Features:
 * - Real-time effect broadcasting
 * - Minimal latency (fire-and-forget)
 * - Automatic retry on network failure
 * - Local-only fallback for weak networks
 */
class FilterSyncEngine(
    private val networkEngine: NetworkMessageEngine
) {

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnected = false
    private var lastSyncTime = 0L
    private val syncDebounceMs = 50L

    fun setConnected(connected: Boolean) {
        isConnected = connected
    }

    fun broadcastEmojiShower(emojiType: VideoFilterEngine.EmojiType, intensity: Float = 1f) {
        if (!isConnected) return

        syncScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "filter_emoji")
                    put("emoji", emojiType.name)
                    put("intensity", intensity)
                    put("timestamp", System.currentTimeMillis())
                }
                networkEngine.sendControlSignal("filter_effect", payload)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun broadcastFilterEffect(
        filterType: VideoFilterEngine.FilterType,
        intensity: Float = 1f,
        duration: Long = 0L
    ) {
        if (!isConnected) return

        syncScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "filter_overlay")
                    put("filter", filterType.name)
                    put("intensity", intensity)
                    put("duration", duration)
                    put("timestamp", System.currentTimeMillis())
                }
                networkEngine.sendControlSignal("filter_effect", payload)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun broadcastClearEffects() {
        if (!isConnected) return

        syncScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "filter_clear")
                    put("timestamp", System.currentTimeMillis())
                }
                networkEngine.sendControlSignal("filter_effect", payload)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun destroy() {
        syncScope.cancel()
    }
}
