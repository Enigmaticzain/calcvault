package com.calcvault.house

import org.json.JSONObject
import com.calcvault.storage.provider.StorageManager

/**
 * NeedsSystem.kt — Core virtual-pet needs engine
 *
 * Manages hunger, energy, hygiene, happiness for both Tom and Angela.
 * Needs decay over real time. Critical states trigger distress animations.
 * State persists across sessions via StorageManager.
 */

// ═══════════════════════════════════════════════════════════════════════════
// ENUMS & DATA
// ═══════════════════════════════════════════════════════════════════════════

enum class NeedType {
    HUNGER,   // Fed by food items
    ENERGY,   // Restored by sleeping
    HYGIENE,  // Restored by bathing/showering
    HAPPINESS // Restored by playing, petting, mini-games
}

enum class NeedUrgency {
    SATISFIED,  // 60-100
    MODERATE,   // 25-59
    LOW,        // 10-24
    CRITICAL    // 0-9
}

data class NeedState(
    val type: NeedType,
    val value: Float = 80f,        // 0-100
    val decayRatePerMin: Float = 1f, // Points lost per minute
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    val urgency: NeedUrgency
        get() = when {
            value >= 60f -> NeedUrgency.SATISFIED
            value >= 25f -> NeedUrgency.MODERATE
            value >= 10f -> NeedUrgency.LOW
            else -> NeedUrgency.CRITICAL
        }

    val isCritical: Boolean get() = value < 10f
    val isLow: Boolean get() = value < 25f
    val emoji: String
        get() = when (type) {
            NeedType.HUNGER -> if (isCritical) "🤤" else "🍽️"
            NeedType.ENERGY -> if (isCritical) "😵" else "⚡"
            NeedType.HYGIENE -> if (isCritical) "🤢" else "🚿"
            NeedType.HAPPINESS -> if (isCritical) "😢" else "😊"
        }

    fun toJSON(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("value", value.toDouble())
        put("decayRate", decayRatePerMin.toDouble())
        put("lastUpdate", lastUpdateTime)
    }

    companion object {
        fun fromJSON(json: JSONObject): NeedState = NeedState(
            type = NeedType.valueOf(json.getString("type")),
            value = json.getDouble("value").toFloat(),
            decayRatePerMin = json.optDouble("decayRate", 1.0).toFloat(),
            lastUpdateTime = json.optLong("lastUpdate", System.currentTimeMillis())
        )
    }
}

data class CharacterNeeds(
    val identity: CharacterIdentity,
    val hunger: NeedState = NeedState(NeedType.HUNGER, 75f, 1.2f),
    val energy: NeedState = NeedState(NeedType.ENERGY, 85f, 0.6f),
    val hygiene: NeedState = NeedState(NeedType.HYGIENE, 90f, 0.4f),
    val happiness: NeedState = NeedState(NeedType.HAPPINESS, 70f, 0.8f)
) {
    val allNeeds: List<NeedState> get() = listOf(hunger, energy, hygiene, happiness)

    val mostUrgentNeed: NeedState?
        get() = allNeeds.minByOrNull { it.value }

    val hasCriticalNeed: Boolean
        get() = allNeeds.any { it.isCritical }

    val hasLowNeed: Boolean
        get() = allNeeds.any { it.isLow }

    val overallWellbeing: Float
        get() = allNeeds.map { it.value }.average().toFloat()

    fun getNeed(type: NeedType): NeedState = when (type) {
        NeedType.HUNGER -> hunger
        NeedType.ENERGY -> energy
        NeedType.HYGIENE -> hygiene
        NeedType.HAPPINESS -> happiness
    }

    fun withUpdatedNeed(type: NeedType, newValue: Float): CharacterNeeds {
        val clamped = newValue.coerceIn(0f, 100f)
        return when (type) {
            NeedType.HUNGER -> copy(hunger = hunger.copy(value = clamped, lastUpdateTime = System.currentTimeMillis()))
            NeedType.ENERGY -> copy(energy = energy.copy(value = clamped, lastUpdateTime = System.currentTimeMillis()))
            NeedType.HYGIENE -> copy(hygiene = hygiene.copy(value = clamped, lastUpdateTime = System.currentTimeMillis()))
            NeedType.HAPPINESS -> copy(happiness = happiness.copy(value = clamped, lastUpdateTime = System.currentTimeMillis()))
        }
    }

    fun toJSON(): JSONObject = JSONObject().apply {
        put("identity", identity.name)
        put("hunger", hunger.toJSON())
        put("energy", energy.toJSON())
        put("hygiene", hygiene.toJSON())
        put("happiness", happiness.toJSON())
    }

    companion object {
        fun fromJSON(json: JSONObject): CharacterNeeds = CharacterNeeds(
            identity = CharacterIdentity.valueOf(json.getString("identity")),
            hunger = NeedState.fromJSON(json.getJSONObject("hunger")),
            energy = NeedState.fromJSON(json.getJSONObject("energy")),
            hygiene = NeedState.fromJSON(json.getJSONObject("hygiene")),
            happiness = NeedState.fromJSON(json.getJSONObject("happiness"))
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NEEDS ENGINE
// ═══════════════════════════════════════════════════════════════════════════

class NeedsEngine {

    private var tomNeeds = CharacterNeeds(CharacterIdentity.ZAIN)
    private var angelaNeeds = CharacterNeeds(CharacterIdentity.SANU)
    private var lastTickTime = System.currentTimeMillis()
    private var listeners = mutableListOf<NeedsChangeListener>()

    companion object {
        private const val NEEDS_KEY = "prefs/house_needs_state"
        private const val DECAY_INTERVAL_MS = 5000L // Check every 5 seconds
        private const val FEED_HAPPINESS_BONUS = 3f
        private const val SLEEP_RESTORE_RATE = 15f // Per minute of sleep
        private const val BATH_RESTORE_AMOUNT = 40f
        private const val PLAY_HAPPINESS_BOOST = 12f
        private const val PET_HAPPINESS_BOOST = 5f
        private const val TOGETHER_ACTIVITY_BONUS = 1.3f // 30% bonus when doing things together
    }

    interface NeedsChangeListener {
        fun onNeedChanged(character: CharacterIdentity, type: NeedType, oldValue: Float, newValue: Float)
        fun onNeedCritical(character: CharacterIdentity, type: NeedType)
        fun onNeedSatisfied(character: CharacterIdentity, type: NeedType)
    }

    fun initialize() {
        loadState()
        catchUpDecay()
    }

    /**
     * Called every frame from the behavior engine.
     * Decays needs based on elapsed real time.
     */
    fun tick(deltaTimeMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastTickTime < DECAY_INTERVAL_MS) return
        lastTickTime = now

        val elapsedMinutes = DECAY_INTERVAL_MS / 60000f

        tomNeeds = decayNeeds(tomNeeds, elapsedMinutes)
        angelaNeeds = decayNeeds(angelaNeeds, elapsedMinutes)
    }

    private fun decayNeeds(needs: CharacterNeeds, elapsedMinutes: Float): CharacterNeeds {
        var updated = needs
        for (need in needs.allNeeds) {
            val oldValue = need.value
            val decay = need.decayRatePerMin * elapsedMinutes
            val newValue = (oldValue - decay).coerceAtLeast(0f)

            if (newValue != oldValue) {
                updated = updated.withUpdatedNeed(need.type, newValue)
                notifyChange(needs.identity, need.type, oldValue, newValue)
            }
        }
        return updated
    }

    /**
     * Catch up on time passed while app was closed.
     */
    private fun catchUpDecay() {
        val now = System.currentTimeMillis()
        for (need in tomNeeds.allNeeds) {
            val elapsedMinutes = (now - need.lastUpdateTime) / 60000f
            val decay = need.decayRatePerMin * elapsedMinutes.coerceAtMost(120f) // Cap at 2 hours decay
            tomNeeds = tomNeeds.withUpdatedNeed(need.type, (need.value - decay).coerceAtLeast(0f))
        }
        for (need in angelaNeeds.allNeeds) {
            val elapsedMinutes = (now - need.lastUpdateTime) / 60000f
            val decay = need.decayRatePerMin * elapsedMinutes.coerceAtMost(120f)
            angelaNeeds = angelaNeeds.withUpdatedNeed(need.type, (need.value - decay).coerceAtLeast(0f))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ACTIONS
    // ─────────────────────────────────────────────────────────────────────

    fun feed(character: CharacterIdentity, food: FoodItem): FeedResult {
        val needs = getNeeds(character)
        val currentHunger = needs.hunger.value

        if (currentHunger > 95f) return FeedResult(false, "Not hungry!", 0f)

        val hungerGain = food.hungerValue
        val happinessGain = food.happinessBoost + FEED_HAPPINESS_BONUS

        var updated = needs
            .withUpdatedNeed(NeedType.HUNGER, currentHunger + hungerGain)
            .withUpdatedNeed(NeedType.HAPPINESS, needs.happiness.value + happinessGain)

        // Messy food reduces hygiene slightly
        if (food.isMessing) {
            updated = updated.withUpdatedNeed(NeedType.HYGIENE, needs.hygiene.value - 3f)
        }

        setNeeds(character, updated)
        return FeedResult(true, "${food.emoji} Yum!", hungerGain)
    }

    fun startSleeping(character: CharacterIdentity) {
        val needs = getNeeds(character)
        // Immediate small energy boost when going to bed
        setNeeds(character, needs.withUpdatedNeed(NeedType.ENERGY, needs.energy.value + 5f))
    }

    fun sleepTick(character: CharacterIdentity, deltaMinutes: Float) {
        val needs = getNeeds(character)
        val gain = SLEEP_RESTORE_RATE * deltaMinutes
        setNeeds(character, needs.withUpdatedNeed(NeedType.ENERGY, needs.energy.value + gain))
    }

    fun bathe(character: CharacterIdentity) {
        val needs = getNeeds(character)
        setNeeds(character, needs
            .withUpdatedNeed(NeedType.HYGIENE, needs.hygiene.value + BATH_RESTORE_AMOUNT)
            .withUpdatedNeed(NeedType.HAPPINESS, needs.happiness.value + 5f)
        )
    }

    fun shower(character: CharacterIdentity) {
        val needs = getNeeds(character)
        setNeeds(character, needs.withUpdatedNeed(NeedType.HYGIENE, needs.hygiene.value + 30f))
    }

    fun play(character: CharacterIdentity, partner: CharacterIdentity? = null) {
        val needs = getNeeds(character)
        val boost = if (partner != null) PLAY_HAPPINESS_BOOST * TOGETHER_ACTIVITY_BONUS else PLAY_HAPPINESS_BOOST
        var updated = needs
            .withUpdatedNeed(NeedType.HAPPINESS, needs.happiness.value + boost)
            .withUpdatedNeed(NeedType.ENERGY, needs.energy.value - 3f) // Playing costs energy

        setNeeds(character, updated)

        // Partner also gets a smaller boost
        if (partner != null) {
            val partnerNeeds = getNeeds(partner)
            setNeeds(partner, partnerNeeds.withUpdatedNeed(NeedType.HAPPINESS, partnerNeeds.happiness.value + boost * 0.7f))
        }
    }

    fun pet(character: CharacterIdentity) {
        val needs = getNeeds(character)
        setNeeds(character, needs.withUpdatedNeed(NeedType.HAPPINESS, needs.happiness.value + PET_HAPPINESS_BOOST))
    }

    fun poke(character: CharacterIdentity) {
        val needs = getNeeds(character)
        setNeeds(character, needs.withUpdatedNeed(NeedType.HAPPINESS, (needs.happiness.value - 2f).coerceAtLeast(0f)))
    }

    fun tickle(character: CharacterIdentity) {
        val needs = getNeeds(character)
        setNeeds(character, needs.withUpdatedNeed(NeedType.HAPPINESS, needs.happiness.value + 8f))
    }

    // ─────────────────────────────────────────────────────────────────────
    // GETTERS / SETTERS
    // ─────────────────────────────────────────────────────────────────────

    fun getNeeds(character: CharacterIdentity): CharacterNeeds =
        when (character) {
            CharacterIdentity.ZAIN -> tomNeeds
            CharacterIdentity.SANU -> angelaNeeds
        }

    private fun setNeeds(character: CharacterIdentity, needs: CharacterNeeds) {
        when (character) {
            CharacterIdentity.ZAIN -> tomNeeds = needs
            CharacterIdentity.SANU -> angelaNeeds = needs
        }
    }

    fun getSuggestedAction(character: CharacterIdentity): NeedType? {
        val needs = getNeeds(character)
        val most = needs.mostUrgentNeed ?: return null
        return if (most.value < 40f) most.type else null
    }

    fun addListener(listener: NeedsChangeListener) { listeners.add(listener) }
    fun removeListener(listener: NeedsChangeListener) { listeners.remove(listener) }

    private fun notifyChange(character: CharacterIdentity, type: NeedType, oldVal: Float, newVal: Float) {
        listeners.forEach { it.onNeedChanged(character, type, oldVal, newVal) }
        if (newVal < 10f && oldVal >= 10f) {
            listeners.forEach { it.onNeedCritical(character, type) }
        }
        if (newVal >= 60f && oldVal < 60f) {
            listeners.forEach { it.onNeedSatisfied(character, type) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PERSISTENCE
    // ─────────────────────────────────────────────────────────────────────

    fun saveState() {
        try {
            val json = JSONObject().apply {
                put("tom", tomNeeds.toJSON())
                put("angela", angelaNeeds.toJSON())
                put("savedAt", System.currentTimeMillis())
            }
            StorageManager.write(NEEDS_KEY, json.toString().toByteArray())
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadState() {
        try {
            val data = StorageManager.read(NEEDS_KEY) ?: return
            val json = JSONObject(String(data))
            tomNeeds = CharacterNeeds.fromJSON(json.getJSONObject("tom"))
            angelaNeeds = CharacterNeeds.fromJSON(json.getJSONObject("angela"))
        } catch (e: Exception) { e.printStackTrace() }
    }
}

data class FeedResult(
    val success: Boolean,
    val message: String,
    val hungerGain: Float
)
