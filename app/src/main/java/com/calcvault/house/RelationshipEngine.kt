package com.calcvault.house

import org.json.JSONObject
import com.calcvault.storage.provider.StorageManager

/**
 * RelationshipEngine.kt — Love meter, relationship stages, couple dynamics
 */

enum class RelationshipStage(val minLove: Int, val label: String, val emoji: String) {
    ACQUAINTANCE(0, "Acquaintances", "🤝"),
    FRIENDS(20, "Friends", "😊"),
    CLOSE(40, "Close Friends", "💛"),
    IN_LOVE(60, "In Love", "💕"),
    SOULMATES(85, "Soulmates", "💖")
}

data class RelationshipState(
    val loveMeter: Float = 30f,  // 0-100
    val totalInteractions: Int = 0,
    val giftCount: Int = 0,
    val fightCount: Int = 0,
    val lastInteractionTime: Long = System.currentTimeMillis()
) {
    val stage: RelationshipStage
        get() = RelationshipStage.values().lastOrNull { loveMeter >= it.minLove } ?: RelationshipStage.ACQUAINTANCE

    val canHoldHands: Boolean get() = loveMeter >= 40f
    val canCuddle: Boolean get() = loveMeter >= 60f
    val canDance: Boolean get() = loveMeter >= 50f
    val canGift: Boolean get() = loveMeter >= 70f

    fun toJSON() = JSONObject().apply {
        put("love", loveMeter.toDouble()); put("interactions", totalInteractions)
        put("gifts", giftCount); put("fights", fightCount); put("lastTime", lastInteractionTime)
    }

    companion object {
        fun fromJSON(j: JSONObject) = RelationshipState(
            j.optDouble("love", 30.0).toFloat(), j.optInt("interactions"), j.optInt("gifts"),
            j.optInt("fights"), j.optLong("lastTime", System.currentTimeMillis())
        )
    }
}

class RelationshipEngine {

    var state = RelationshipState(); private set
    private var listeners = mutableListOf<(RelationshipState, RelationshipState) -> Unit>()

    companion object {
        private const val KEY = "prefs/house_relationship"
        // Love changes
        const val ACTIVITY_TOGETHER = 3f
        const val COOK_TOGETHER = 4f
        const val PLAY_TOGETHER = 3.5f
        const val WATCH_TOGETHER = 2.5f
        const val SLEEP_TOGETHER = 5f
        const val FEED_PARTNER = 3f
        const val GIFT_BONUS = 8f
        const val CHAT_ROMANTIC = 6f
        const val CHAT_LOVING = 4f
        const val FIGHT_PENALTY = -8f
        const val IGNORE_PENALTY = -1.5f  // Per hour of no interaction
        const val CUDDLE_BONUS = 6f
        const val HOLD_HANDS = 2f
    }

    fun initialize() { loadState() }

    fun onActivityTogether(activity: String) {
        val boost = when (activity.lowercase()) {
            "cook" -> COOK_TOGETHER
            "play" -> PLAY_TOGETHER
            "watch" -> WATCH_TOGETHER
            "sleep" -> SLEEP_TOGETHER
            "cuddle" -> CUDDLE_BONUS
            "hold_hands" -> HOLD_HANDS
            else -> ACTIVITY_TOGETHER
        }
        changeLove(boost, "activity_$activity")
    }

    fun onFeedPartner() { changeLove(FEED_PARTNER, "feed") }
    fun onGift() { changeLove(GIFT_BONUS, "gift"); state = state.copy(giftCount = state.giftCount + 1) }
    fun onFight() { changeLove(FIGHT_PENALTY, "fight"); state = state.copy(fightCount = state.fightCount + 1) }
    fun onRomanticChat() { changeLove(CHAT_ROMANTIC, "romantic_chat") }
    fun onLovingChat() { changeLove(CHAT_LOVING, "loving_chat") }

    fun checkIgnoreDecay() {
        val hoursSince = (System.currentTimeMillis() - state.lastInteractionTime) / 3600000f
        if (hoursSince > 2f) {
            val penalty = IGNORE_PENALTY * (hoursSince - 2f).coerceAtMost(24f)
            changeLove(penalty, "ignore_decay")
        }
    }

    fun getUnlockedActivities(): List<String> {
        val list = mutableListOf("sit_together", "cook_together", "watch_together", "play_together")
        if (state.canHoldHands) list.add("hold_hands")
        if (state.canDance) list.add("dance_together")
        if (state.canCuddle) list.add("cuddle")
        if (state.canGift) list.add("give_gift")
        return list
    }

    private fun changeLove(amount: Float, source: String) {
        val old = state
        val newLove = (state.loveMeter + amount).coerceIn(0f, 100f)
        state = state.copy(
            loveMeter = newLove,
            totalInteractions = state.totalInteractions + 1,
            lastInteractionTime = System.currentTimeMillis()
        )
        if (old.stage != state.stage) {
            listeners.forEach { it(old, state) }
        }
        saveState()
    }

    fun onStageChange(listener: (RelationshipState, RelationshipState) -> Unit) { listeners.add(listener) }

    fun saveState() {
        try { StorageManager.write(KEY, state.toJSON().toString().toByteArray()) }
        catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadState() {
        try {
            val data = StorageManager.read(KEY) ?: return
            state = RelationshipState.fromJSON(JSONObject(String(data)))
        } catch (e: Exception) { e.printStackTrace() }
    }
}
