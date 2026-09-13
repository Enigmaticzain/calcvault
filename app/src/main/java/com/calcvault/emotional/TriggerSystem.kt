package com.calcvault.emotional

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * TriggerSystem
 *
 * Improved custom trigger system with:
 * - Better error handling and logging
 * - Validation of trigger data
 * - Support for multiple keywords per trigger
 * - Better emoji handling
 * - Debug mode for testing
 * - Callbacks for success/failure
 */
class TriggerSystem {

    enum class TriggerType { EMOJI_ANIMATION, PARTICLE_BURST, GLOW, RAIN, STARS }

    data class TriggerConfig(
        val keywords: List<String>,
        val triggerType: TriggerType,
        val emoji: String? = null, // for EMOJI_ANIMATION
        val intensity: Int = 15, // particle count
        val direction: String = "UP", // UP, DOWN, RANDOM
        val speed: String = "MEDIUM", // SLOW, MEDIUM, FAST
        val color: Int? = null, // custom color
        var isEnabled: Boolean = true
    ) {
        fun isValid(): Boolean {
            if (keywords.isEmpty()) return false
            if (triggerType == TriggerType.EMOJI_ANIMATION && emoji.isNullOrEmpty()) return false
            if (intensity < 1 || intensity > 100) return false
            return true
        }
    }

    data class ParsedTrigger(
        val config: TriggerConfig,
        val source: String = "custom", // "builtin" or "custom"
        val errors: List<String> = emptyList()
    )

    companion object {
        private const val TAG = "TriggerSystem"
        private var debugEnabled = false

        fun enableDebug(enabled: Boolean) {
            debugEnabled = enabled
            if (enabled) {
                Log.d(TAG, "Debug mode enabled")
            }
        }
    }

    private val triggers = mutableListOf<TriggerConfig>()

    /**
     * Load triggers from JSON string with full error handling and validation.
     * Expected JSON format:
     * [
     *   {
     *     "keywords": ["love you", "i love you", "❤️"],  // or single string: "love you"
     *     "type": "emoji_animation",
     *     "emoji": "❤️",
     *     "intensity": 20,
     *     "direction": "UP",
     *     "speed": "MEDIUM"
     *   },
     *   ...
     * ]
     */
    fun loadFromJson(json: String): LoadResult {
        triggers.clear()
        val result = LoadResult()

        if (json.isBlank()) {
            log("No JSON provided")
            return result
        }

        try {
            val array = JSONArray(json)
            log("Parsing ${array.length()} trigger entries")

            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val parsed = parseTriggerObject(obj)

                    if (parsed.errors.isNotEmpty()) {
                        log("Trigger $i has errors: ${parsed.errors.joinToString(", ")}")
                        result.skipped++
                    } else if (parsed.config.isValid()) {
                        triggers.add(parsed.config)
                        log("✓ Loaded trigger: ${parsed.config.keywords.firstOrNull()}")
                        result.loaded++
                    } else {
                        log("Trigger $i is invalid (missing required fields)")
                        result.skipped++
                    }
                } catch (e: Exception) {
                    log("Error parsing trigger at index $i: ${e.message}")
                    result.errors.add("Entry $i: ${e.message}")
                    result.skipped++
                }
            }
        } catch (e: JSONException) {
            val msg = "Invalid JSON format: ${e.message}"
            log(msg)
            result.errors.add(msg)
            return result
        } catch (e: Exception) {
            val msg = "Unexpected error: ${e.message}"
            log(msg)
            result.errors.add(msg)
        }

        log("Load complete: ${result.loaded} loaded, ${result.skipped} skipped, ${result.errors.size} errors")
        return result
    }

    /**
     * Parse a single trigger JSON object with validation.
     */
    private fun parseTriggerObject(obj: JSONObject): ParsedTrigger {
        val errors = mutableListOf<String>()
        val keywords = mutableListOf<String>()

        // Parse keywords (can be string or array)
        if (obj.has("keywords")) {
            try {
                val keywordData = obj.get("keywords")
                when (keywordData) {
                    is String -> keywords.add(keywordData.lowercase().trim())
                    is JSONArray -> {
                        for (i in 0 until keywordData.length()) {
                            val kw = keywordData.getString(i).lowercase().trim()
                            if (kw.isNotEmpty()) keywords.add(kw)
                        }
                    }
                    else -> errors.add("'keywords' must be string or array")
                }
            } catch (e: Exception) {
                errors.add("Error parsing keywords: ${e.message}")
            }
        } else if (obj.has("kw")) {
            // Legacy format support
            try {
                keywords.add(obj.getString("kw").lowercase().trim())
            } catch (e: Exception) {
                errors.add("Error parsing 'kw': ${e.message}")
            }
        } else {
            errors.add("Missing 'keywords' or 'kw' field")
        }

        // Parse trigger type
        val typeStr = obj.optString("type", "emoji_animation").lowercase()
        val triggerType = when (typeStr) {
            "emoji_animation", "emoji" -> TriggerType.EMOJI_ANIMATION
            "particle", "particles" -> TriggerType.PARTICLE_BURST
            "glow", "flash" -> TriggerType.GLOW
            "rain" -> TriggerType.RAIN
            "stars" -> TriggerType.STARS
            else -> {
                errors.add("Unknown type: '$typeStr'")
                TriggerType.EMOJI_ANIMATION
            }
        }

        // Parse emoji (required for emoji animations)
        val emoji = obj.optString("emoji", obj.optString("em", "")).takeIf { it.isNotEmpty() }
        if (triggerType == TriggerType.EMOJI_ANIMATION && emoji.isNullOrEmpty()) {
            errors.add("Emoji animation requires 'emoji' field")
        }

        // Parse numeric fields
        val intensity = obj.optInt("intensity", obj.optInt("int", 15)).coerceIn(1, 100)

        val direction = obj.optString("direction", obj.optString("dir", "UP"))
            .uppercase()
            .takeIf { it in listOf("UP", "DOWN", "RANDOM") } ?: "UP"

        val speed = obj.optString("speed", obj.optString("spd", "MEDIUM"))
            .uppercase()
            .takeIf { it in listOf("SLOW", "MEDIUM", "FAST") } ?: "MEDIUM"

        // Parse color (optional)
        val color = if (obj.has("color")) {
            try {
                obj.getInt("color")
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val isEnabled = obj.optBoolean("enabled", true)

        val config = TriggerConfig(
            keywords = keywords,
            triggerType = triggerType,
            emoji = emoji,
            intensity = intensity,
            direction = direction,
            speed = speed,
            color = color,
            isEnabled = isEnabled
        )

        return ParsedTrigger(config, errors = errors)
    }

    /**
     * Add a trigger programmatically.
     */
    fun addTrigger(config: TriggerConfig): Boolean {
        if (!config.isValid()) {
            log("Invalid trigger config: missing required fields")
            return false
        }

        // Check for duplicates
        val duplicates = triggers.filter { it.keywords.intersect(config.keywords.toSet()).isNotEmpty() }
        if (duplicates.isNotEmpty()) {
            log("Warning: new trigger shares keywords with existing triggers")
        }

        triggers.add(config)
        log("✓ Added trigger: ${config.keywords.firstOrNull()}")
        return true
    }

    /**
     * Check if a message matches any trigger and return matching trigger.
     */
    fun findMatchingTrigger(messageText: String): TriggerConfig? {
        if (messageText.isBlank()) return null

        val lower = messageText.lowercase()

        for (trigger in triggers) {
            if (!trigger.isEnabled) continue

            // Check if any keyword matches
            if (trigger.keywords.any { lower.contains(it) }) {
                log("✓ Matched trigger: ${trigger.keywords.firstOrNull()}")
                return trigger
            }
        }

        return null
    }

    /**
     * Get all triggers for inspection/debugging.
     */
    fun getAllTriggers(): List<TriggerConfig> = triggers.toList()

    /**
     * Enable/disable a trigger by keyword.
     */
    fun setTriggerEnabled(keyword: String, enabled: Boolean): Boolean {
        val trigger = triggers.firstOrNull { it.keywords.contains(keyword.lowercase()) }
        if (trigger != null) {
            trigger.isEnabled = enabled
            log("Trigger '$keyword' isEnabled=$enabled")
            return true
        }
        return false
    }

    /**
     * Remove a trigger by keyword.
     */
    fun removeTrigger(keyword: String): Boolean {
        val key = keyword.lowercase()
        val removed = triggers.removeAll { it.keywords.contains(key) }
        if (removed) {
            log("✓ Removed trigger: '$keyword'")
        }
        return removed
    }

    /**
     * Clear all triggers.
     */
    fun clear() {
        triggers.clear()
        log("All triggers cleared")
    }

    /**
     * Get trigger statistics.
     */
    fun getStats(): TriggerStats {
        return TriggerStats(
            totalTriggers = triggers.size,
            enabledTriggers = triggers.count { it.isEnabled },
            byType = triggers.groupingBy { it.triggerType }.eachCount(),
            totalKeywords = triggers.sumOf { it.keywords.size }
        )
    }

    data class TriggerStats(
        val totalTriggers: Int,
        val enabledTriggers: Int,
        val byType: Map<TriggerType, Int>,
        val totalKeywords: Int
    )

    data class LoadResult(
        var loaded: Int = 0,
        var skipped: Int = 0,
        val errors: MutableList<String> = mutableListOf()
    ) {
        fun isSuccess() = errors.isEmpty() && loaded > 0
        fun getSummary() = "Loaded: $loaded, Skipped: $skipped, Errors: ${errors.size}"
    }

    private fun log(message: String) {
        if (debugEnabled) {
            Log.d(TAG, message)
        }
    }
}
