package com.calcvault.emotional

import org.json.JSONArray
import org.json.JSONObject

/**
 * Scene-level emotion trigger model used by the global word effect system.
 */
enum class AnimationType {
    LOVE_AURA,
    GOOD_NIGHT,
    GOOD_MORNING,
    ANGER_PULSE,
    MISS_FADE,
    PARTICLE_WAVE
}

enum class OverlayType {
    NONE,
    SOFT_GLOW,
    DARK_FADE,
    WARM_GRADIENT,
    RED_PULSE,
    SOFT_FADE
}

data class TriggerEffect(
    val effectId: String,
    val triggerWords: List<String>,
    val animationType: AnimationType,
    val intensity: Float,
    val duration: Long,
    val overlayType: OverlayType,
    val priority: Int,
    val isEnabled: Boolean = true
) {
    fun normalizedWords(): List<String> {
        return triggerWords.mapNotNull { raw ->
            val cleaned = raw.trim().lowercase()
            if (cleaned.isEmpty()) null else cleaned
        }.distinct()
    }

    fun withEnabled(enabled: Boolean): TriggerEffect = copy(isEnabled = enabled)
}

object WordEffectDefaults {

    fun build(localNickname: String = "", partnerNickname: String = ""): List<TriggerEffect> {
        val defaults = mutableListOf<TriggerEffect>()

        defaults += TriggerEffect(
            effectId = stableId("default_love", listOf("love", "ily", "i love you", "love you", "my love", "darling", "❤️", "💕")),
            triggerWords = listOf("love", "ily", "i love you", "love you", "my love", "darling", "❤️", "💕"),
            animationType = AnimationType.LOVE_AURA,
            intensity = 0.8f,
            duration = 2800L,
            overlayType = OverlayType.SOFT_GLOW,
            priority = 90,
            isEnabled = true
        )

        defaults += TriggerEffect(
            effectId = stableId("default_miss", listOf("miss you", "missing you", "i miss you", "come back", "wish you were here", "🫂")),
            triggerWords = listOf("miss you", "missing you", "i miss you", "come back", "wish you were here", "🫂"),
            animationType = AnimationType.MISS_FADE,
            intensity = 0.75f,
            duration = 3200L,
            overlayType = OverlayType.SOFT_FADE,
            priority = 85,
            isEnabled = true
        )

        defaults += TriggerEffect(
            effectId = stableId("default_good_night", listOf("good night", "goodnight", "gn", "sleep well", "sweet dreams", "🌙")),
            triggerWords = listOf("good night", "goodnight", "gn", "sleep well", "sweet dreams", "🌙"),
            animationType = AnimationType.GOOD_NIGHT,
            intensity = 0.65f,
            duration = 3500L,
            overlayType = OverlayType.DARK_FADE,
            priority = 70,
            isEnabled = true
        )

        defaults += TriggerEffect(
            effectId = stableId("default_good_morning", listOf("good morning", "gm", "rise and shine", "sunshine", "☀️")),
            triggerWords = listOf("good morning", "gm", "rise and shine", "sunshine", "☀️"),
            animationType = AnimationType.GOOD_MORNING,
            intensity = 0.7f,
            duration = 2600L,
            overlayType = OverlayType.WARM_GRADIENT,
            priority = 72,
            isEnabled = true
        )

        defaults += TriggerEffect(
            effectId = stableId("default_anger", listOf("angry", "hate", "furious", "mad", "shut up", "😡", "annoyed")),
            triggerWords = listOf("angry", "hate", "furious", "mad", "shut up", "😡", "annoyed"),
            animationType = AnimationType.ANGER_PULSE,
            intensity = 0.85f,
            duration = 1200L,
            overlayType = OverlayType.RED_PULSE,
            priority = 100,
            isEnabled = true
        )

        val nicknameWords = listOf(localNickname, partnerNickname)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        if (nicknameWords.isNotEmpty()) {
            defaults += TriggerEffect(
                effectId = stableId("default_nickname", nicknameWords),
                triggerWords = nicknameWords,
                animationType = AnimationType.LOVE_AURA,
                intensity = 0.6f,
                duration = 2200L,
                overlayType = OverlayType.SOFT_GLOW,
                priority = 78,
                isEnabled = true
            )
        }

        return defaults
    }

    private fun stableId(prefix: String, words: List<String>): String {
        val cleaned = words.map { it.trim().lowercase() }.sorted().joinToString("|")
        return "$prefix:${cleaned.hashCode()}"
    }
}

object WordEffectCodec {

    fun encode(effects: List<TriggerEffect>): String {
        val arr = JSONArray()
        effects.forEach { arr.put(toJson(it)) }
        return arr.toString()
    }

    fun decode(json: String): List<TriggerEffect> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    fromJson(obj)?.let { add(it) }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun toJson(effect: TriggerEffect): JSONObject {
        return JSONObject().apply {
            put("effectId", effect.effectId)
            put("triggerWords", JSONArray(effect.triggerWords))
            put("animationType", effect.animationType.name)
            put("intensity", effect.intensity)
            put("duration", effect.duration)
            put("overlayType", effect.overlayType.name)
            put("priority", effect.priority)
            put("isEnabled", effect.isEnabled)
        }
    }

    fun fromJson(obj: JSONObject): TriggerEffect? {
        // New format
        val animation = obj.optString("animationType")
        if (animation.isNotBlank()) {
            val words = parseWords(obj.opt("triggerWords") ?: obj.opt("keywords"))
            if (words.isEmpty()) return null

            val animationType = runCatching { AnimationType.valueOf(animation) }
                .getOrElse { return null }
            val intensity = obj.optDouble("intensity", 0.65).toFloat().coerceIn(0.1f, 1f)
            val duration = obj.optLong("duration", 2200L).coerceIn(500L, 10_000L)
            val overlayType = runCatching {
                OverlayType.valueOf(obj.optString("overlayType", defaultOverlay(animationType).name))
            }.getOrElse { defaultOverlay(animationType) }
            val priority = obj.optInt("priority", defaultPriority(animationType)).coerceIn(1, 100)
            val enabled = obj.optBoolean("isEnabled", true)

            return TriggerEffect(
                effectId = obj.optString("effectId").ifBlank { stableId(words, animationType) },
                triggerWords = words,
                animationType = animationType,
                intensity = intensity,
                duration = duration,
                overlayType = overlayType,
                priority = priority,
                isEnabled = enabled
            )
        }

        // Legacy format fallback (old custom trigger JSON)
        val legacyType = obj.optString("type").lowercase()
        if (legacyType.isNotBlank()) {
            val words = parseWords(obj.opt("keywords") ?: obj.opt("kw"))
            if (words.isEmpty()) return null

            val animationType = when (legacyType) {
                "rain" -> AnimationType.MISS_FADE
                "stars" -> AnimationType.GOOD_NIGHT
                "glow" -> AnimationType.GOOD_MORNING
                "particle", "particles" -> AnimationType.PARTICLE_WAVE
                else -> AnimationType.LOVE_AURA
            }
            val intensity = (obj.optInt("intensity", obj.optInt("int", 18)).toFloat() / 30f).coerceIn(0.1f, 1f)
            val duration = when (obj.optString("speed", "MEDIUM").uppercase()) {
                "FAST" -> 1200L
                "SLOW" -> 3200L
                else -> 2200L
            }
            val enabled = obj.optBoolean("enabled", true)

            return TriggerEffect(
                effectId = stableId(words, animationType),
                triggerWords = words,
                animationType = animationType,
                intensity = intensity,
                duration = duration,
                overlayType = defaultOverlay(animationType),
                priority = defaultPriority(animationType),
                isEnabled = enabled
            )
        }

        return null
    }

    fun parseWords(raw: Any?): List<String> {
        return when (raw) {
            is JSONArray -> {
                buildList {
                    for (i in 0 until raw.length()) {
                        val word = raw.optString(i).trim().lowercase()
                        if (word.isNotBlank()) add(word)
                    }
                }.distinct()
            }
            is String -> raw.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
            else -> emptyList()
        }
    }

    fun stableId(words: List<String>, type: AnimationType): String {
        val normalized = words.map { it.trim().lowercase() }.sorted().joinToString("|")
        return "${type.name}:${normalized.hashCode()}"
    }

    fun defaultOverlay(type: AnimationType): OverlayType {
        return when (type) {
            AnimationType.LOVE_AURA -> OverlayType.SOFT_GLOW
            AnimationType.GOOD_NIGHT -> OverlayType.DARK_FADE
            AnimationType.GOOD_MORNING -> OverlayType.WARM_GRADIENT
            AnimationType.ANGER_PULSE -> OverlayType.RED_PULSE
            AnimationType.MISS_FADE -> OverlayType.SOFT_FADE
            AnimationType.PARTICLE_WAVE -> OverlayType.NONE
        }
    }

    fun defaultPriority(type: AnimationType): Int {
        return when (type) {
            AnimationType.ANGER_PULSE -> 100
            AnimationType.LOVE_AURA -> 88
            AnimationType.MISS_FADE -> 82
            AnimationType.GOOD_MORNING -> 70
            AnimationType.GOOD_NIGHT -> 68
            AnimationType.PARTICLE_WAVE -> 60
        }
    }
}
