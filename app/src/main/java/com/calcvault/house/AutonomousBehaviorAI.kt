package com.calcvault.house

import android.graphics.PointF
import java.util.Calendar

/**
 * AutonomousBehaviorAI.kt — Characters act on their own when player watches
 */
class AutonomousBehaviorAI(
    private val needsEngine: NeedsEngine,
    private val relationshipEngine: RelationshipEngine,
    private val environment: HouseEnvironment
) {
    private var lastDecisionTime = 0L
    private var idleTimer = 0L
    private val random = java.util.Random()
    private var currentBehavior: AutonomousAction? = null

    companion object {
        const val DECISION_INTERVAL = 8000L  // Decide every 8 seconds
        const val IDLE_THRESHOLD = 12000L    // Start autonomous after 12s idle
        const val WANDER_CHANCE = 0.3f
        const val PARTNER_FOLLOW_CHANCE = 0.4f
    }

    enum class AutonomousAction {
        WANDER, GO_EAT, GO_SLEEP, GO_BATHE, GO_PLAY,
        FOLLOW_PARTNER, WAVE_AT_PARTNER, SIT_TOGETHER,
        IDLE_ANIMATION, YAWN, STRETCH, LOOK_AROUND,
        PLAY_WITH_TAIL, FIX_BOW, ADMIRE_MIRROR
    }

    data class AIDecision(
        val character: CharacterIdentity,
        val action: AutonomousAction,
        val targetZone: ZoneType? = null,
        val targetRoom: HouseRoom? = null,
        val characterAction: CharacterAction = CharacterAction.IDLE,
        val mood: EmotionalState? = null,
        val duration: Long = 3000L
    )

    fun tick(deltaTime: Long, dualState: DualCharacterState): List<AIDecision> {
        val now = System.currentTimeMillis()
        idleTimer += deltaTime

        // Only act after idle threshold and at decision intervals
        if (idleTimer < IDLE_THRESHOLD || now - lastDecisionTime < DECISION_INTERVAL) return emptyList()
        lastDecisionTime = now

        val decisions = mutableListOf<AIDecision>()

        // Decide for Tom
        if (dualState.zain.action == CharacterAction.IDLE || dualState.zain.action == CharacterAction.SITTING) {
            decideForCharacter(CharacterIdentity.ZAIN, dualState)?.let { decisions.add(it) }
        }
        // Decide for Angela
        if (dualState.sanu.action == CharacterAction.IDLE || dualState.sanu.action == CharacterAction.SITTING) {
            decideForCharacter(CharacterIdentity.SANU, dualState)?.let { decisions.add(it) }
        }

        return decisions
    }

    fun resetIdleTimer() { idleTimer = 0L }

    private fun decideForCharacter(character: CharacterIdentity, state: DualCharacterState): AIDecision? {
        val needs = needsEngine.getNeeds(character)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 22 || hour < 6
        val isTom = character == CharacterIdentity.ZAIN
        val love = relationshipEngine.state.loveMeter

        // Priority 1: Critical needs
        needs.mostUrgentNeed?.let { urgent ->
            if (urgent.isCritical) return needsBasedDecision(character, urgent.type)
        }

        // Priority 2: Low needs
        if (random.nextFloat() < 0.6f) {
            needs.mostUrgentNeed?.let { urgent ->
                if (urgent.isLow) return needsBasedDecision(character, urgent.type)
            }
        }

        // Priority 3: Night time → sleepy behaviors
        if (isNight && random.nextFloat() < 0.5f) {
            return AIDecision(character, AutonomousAction.YAWN, characterAction = CharacterAction.IDLE,
                mood = EmotionalState.TIRED, duration = 2000L)
        }

        // Priority 4: Relationship-driven partner interactions
        if (love > 40f && random.nextFloat() < PARTNER_FOLLOW_CHANCE) {
            return partnerInteraction(character, love)
        }

        // Priority 5: Character-specific idle animations
        if (random.nextFloat() < 0.4f) {
            return characterIdleAnimation(character, isTom)
        }

        // Priority 6: Random wandering
        if (random.nextFloat() < WANDER_CHANCE) {
            return AIDecision(character, AutonomousAction.WANDER, characterAction = CharacterAction.WALKING, duration = 4000L)
        }

        return null
    }

    private fun needsBasedDecision(character: CharacterIdentity, need: NeedType): AIDecision = when (need) {
        NeedType.HUNGER -> AIDecision(character, AutonomousAction.GO_EAT, ZoneType.KITCHEN, HouseRoom.KITCHEN,
            CharacterAction.WALKING, EmotionalState.SAD, 5000L)
        NeedType.ENERGY -> AIDecision(character, AutonomousAction.GO_SLEEP, ZoneType.BED, HouseRoom.BEDROOM,
            CharacterAction.SLEEPING, EmotionalState.TIRED, 10000L)
        NeedType.HYGIENE -> AIDecision(character, AutonomousAction.GO_BATHE, ZoneType.SHOWER, HouseRoom.BATHROOM,
            CharacterAction.BATHING, null, 6000L)
        NeedType.HAPPINESS -> AIDecision(character, AutonomousAction.GO_PLAY, ZoneType.GAME_AREA, HouseRoom.ACTIVITY,
            CharacterAction.PLAYING, EmotionalState.PLAYFUL, 8000L)
    }

    private fun partnerInteraction(character: CharacterIdentity, love: Float): AIDecision {
        return when {
            love > 70f && random.nextFloat() < 0.3f -> AIDecision(character, AutonomousAction.SIT_TOGETHER,
                ZoneType.SOFA, HouseRoom.LIVING, CharacterAction.SITTING, EmotionalState.ROMANTIC, 6000L)
            love > 50f && random.nextFloat() < 0.4f -> AIDecision(character, AutonomousAction.WAVE_AT_PARTNER,
                characterAction = CharacterAction.TALKING, mood = EmotionalState.HAPPY, duration = 2000L)
            else -> AIDecision(character, AutonomousAction.FOLLOW_PARTNER,
                characterAction = CharacterAction.WALKING, duration = 4000L)
        }
    }

    private fun characterIdleAnimation(character: CharacterIdentity, isTom: Boolean): AIDecision {
        val anim = if (isTom) {
            listOf(AutonomousAction.PLAY_WITH_TAIL, AutonomousAction.STRETCH,
                AutonomousAction.LOOK_AROUND, AutonomousAction.YAWN).random()
        } else {
            listOf(AutonomousAction.FIX_BOW, AutonomousAction.ADMIRE_MIRROR,
                AutonomousAction.LOOK_AROUND, AutonomousAction.STRETCH).random()
        }
        return AIDecision(character, anim, characterAction = CharacterAction.IDLE,
            mood = EmotionalState.NEUTRAL, duration = 3000L)
    }
}
