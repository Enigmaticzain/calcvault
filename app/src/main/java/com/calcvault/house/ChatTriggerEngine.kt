package com.calcvault.house

/**
 * ChatTriggerEngine.kt - Map chat messages to character reactions
 *
 * Features:
 * - Keyword detection in chat messages
 * - Emotion state changes based on content
 * - Dual character responses and interactions
 * - Micro-animation triggers
 * - Zone-specific reactions
 */

class ChatTriggerEngine(
    private val environment: HouseEnvironment,
    private val dragDropEngine: DragDropInteractionEngine
) {

    private val triggerRules = mutableListOf<ChatTriggerResponse>()

    init {
        loadDefaultTriggers()
    }

    private fun loadDefaultTriggers() {
        // Love/Romantic triggers
        triggerRules.addAll(
            listOf(
                ChatTriggerResponse(
                    keywords = listOf("love", "miss you", "❤️", "💕"),
                    action = CharacterAction.HOLDING_HANDS,
                    emotionalState = EmotionalState.ROMANTIC,
                    zone = null,
                    animationType = MicroAnimationType.REACH,
                    affectsPartner = true
                ),
                ChatTriggerResponse(
                    keywords = listOf("good night", "sleep well", "sweet dreams"),
                    action = CharacterAction.SLEEPING,
                    emotionalState = EmotionalState.TIRED,
                    zone = ZoneType.BED,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = true
                ),
                ChatTriggerResponse(
                    keywords = listOf("good morning", "wake up", "morning"),
                    action = CharacterAction.WATCHING_TV,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.REACH,
                    affectsPartner = true
                ),

                // Cooking/Food triggers
                ChatTriggerResponse(
                    keywords = listOf("cook", "cooking", "food", "meal", "breakfast", "lunch", "dinner"),
                    action = CharacterAction.COOKING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.KITCHEN,
                    animationType = MicroAnimationType.FIDGET,
                    affectsPartner = true
                ),

                // Sad/Upset
                ChatTriggerResponse(
                    keywords = listOf("sad", "upset", "hurts", "😭", "cry", "bad day"),
                    action = CharacterAction.LISTENING,
                    emotionalState = EmotionalState.SAD,
                    zone = null,
                    animationType = MicroAnimationType.HEAD_TURN,
                    affectsPartner = true
                ),

                // Playful/Fun
                ChatTriggerResponse(
                    keywords = listOf("play", "game", "fun", "haha", "😄", "laugh"),
                    action = CharacterAction.PLAYING,
                    emotionalState = EmotionalState.PLAYFUL,
                    zone = ZoneType.GAME_AREA,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = true
                ),

                // Tired/Rest
                ChatTriggerResponse(
                    keywords = listOf("tired", "exhausted", "rest", "relax", "break"),
                    action = CharacterAction.SITTING,
                    emotionalState = EmotionalState.TIRED,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = false
                ),

                // Focused/Busy
                ChatTriggerResponse(
                    keywords = listOf("work", "busy", "focus", "busy", "working", "task"),
                    action = CharacterAction.IDLE,
                    emotionalState = EmotionalState.FOCUSED,
                    zone = null,
                    animationType = MicroAnimationType.FIDGET,
                    affectsPartner = false
                ),

                // TV/Entertainment
                ChatTriggerResponse(
                    keywords = listOf("watch", "movie", "show", "netflix", "tv", "series"),
                    action = CharacterAction.WATCHING_TV,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = true
                ),

                // Together/Romantic triggers
                ChatTriggerResponse(
                    keywords = listOf("together", "with you", "hug", "cuddle", "embrace"),
                    action = CharacterAction.CUDDLING,
                    emotionalState = EmotionalState.ROMANTIC,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = true
                ),

                // Talking triggers
                ChatTriggerResponse(
                    keywords = listOf("talk", "chat", "tell", "say", "discuss"),
                    action = CharacterAction.TALKING,
                    emotionalState = EmotionalState.NEUTRAL,
                    zone = null,
                    animationType = MicroAnimationType.NOD,
                    affectsPartner = true
                ),

                // ─── NEW TRIGGERS ───

                // Hunger/Eating
                ChatTriggerResponse(
                    keywords = listOf("hungry", "starving", "eat", "snack", "yummy", "delicious"),
                    action = CharacterAction.EATING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.KITCHEN,
                    animationType = MicroAnimationType.FIDGET,
                    affectsPartner = true
                ),

                // Bath/Hygiene
                ChatTriggerResponse(
                    keywords = listOf("bath", "shower", "clean", "wash", "dirty", "stinky"),
                    action = CharacterAction.BATHING,
                    emotionalState = EmotionalState.NEUTRAL,
                    zone = ZoneType.SHOWER,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = false
                ),

                // Dancing
                ChatTriggerResponse(
                    keywords = listOf("dance", "dancing", "music", "song", "sing", "🎵", "🎶"),
                    action = CharacterAction.DANCING,
                    emotionalState = EmotionalState.PLAYFUL,
                    zone = ZoneType.GAME_AREA,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = true
                ),

                // Kiss/Affection
                ChatTriggerResponse(
                    keywords = listOf("kiss", "kisses", "smooch", "😘", "💋", "mwah"),
                    action = CharacterAction.CUDDLING,
                    emotionalState = EmotionalState.ROMANTIC,
                    zone = null,
                    animationType = MicroAnimationType.REACH,
                    affectsPartner = true
                ),

                // Gift giving
                ChatTriggerResponse(
                    keywords = listOf("gift", "present", "surprise", "🎁", "give you"),
                    action = CharacterAction.REACHING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = null,
                    animationType = MicroAnimationType.REACH,
                    affectsPartner = true
                ),

                // Garden/Nature
                ChatTriggerResponse(
                    keywords = listOf("garden", "flowers", "plant", "nature", "outside", "🌸", "🌻"),
                    action = CharacterAction.GARDENING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.FLOWER_BED,
                    animationType = MicroAnimationType.FIDGET,
                    affectsPartner = true
                ),

                // Exercise/Jump
                ChatTriggerResponse(
                    keywords = listOf("exercise", "jump", "bounce", "trampoline", "workout"),
                    action = CharacterAction.TRAMPOLINING,
                    emotionalState = EmotionalState.PLAYFUL,
                    zone = ZoneType.TRAMPOLINE,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = true
                ),

                // Party/Celebrate
                ChatTriggerResponse(
                    keywords = listOf("party", "celebrate", "birthday", "🎉", "🥳", "congrats"),
                    action = CharacterAction.DANCING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.GAME_AREA,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = true
                ),

                // Angry/Fight
                ChatTriggerResponse(
                    keywords = listOf("angry", "mad", "fight", "argue", "😡", "annoyed"),
                    action = CharacterAction.IDLE,
                    emotionalState = EmotionalState.SAD,
                    zone = null,
                    animationType = MicroAnimationType.HEAD_TURN,
                    affectsPartner = false
                ),

                // Scared
                ChatTriggerResponse(
                    keywords = listOf("scared", "afraid", "scary", "ghost", "😱", "dark"),
                    action = CharacterAction.CUDDLING,
                    emotionalState = EmotionalState.SAD,
                    zone = ZoneType.BED,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = true
                ),

                // ────── NEW TRIGGERS (15 MORE) ──────

                // Cooking together
                ChatTriggerResponse(
                    keywords = listOf("cook together", "let's cook", "making dinner", "meal prep"),
                    action = CharacterAction.COOKING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.KITCHEN,
                    animationType = MicroAnimationType.FIDGET,
                    affectsPartner = true
                ),

                // Video call/FaceTime
                ChatTriggerResponse(
                    keywords = listOf("video call", "facetime", "let me see", "show me", "see you"),
                    action = CharacterAction.TALKING,
                    emotionalState = EmotionalState.ROMANTIC,
                    zone = null,
                    animationType = MicroAnimationType.WAVE,
                    affectsPartner = true
                ),

                // Confession/Deep talk
                ChatTriggerResponse(
                    keywords = listOf("i love you", "soulmate", "forever", "marry", "always"),
                    action = CharacterAction.CUDDLING,
                    emotionalState = EmotionalState.ROMANTIC,
                    zone = ZoneType.BED,
                    animationType = MicroAnimationType.REACH,
                    affectsPartner = true
                ),

                // Jokes/Funny
                ChatTriggerResponse(
                    keywords = listOf("haha", "lol", "joke", "funny", "😂", "hilarious"),
                    action = CharacterAction.DANCING,
                    emotionalState = EmotionalState.PLAYFUL,
                    zone = null,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = true
                ),

                // Missing each other
                ChatTriggerResponse(
                    keywords = listOf("miss you", "miss you so much", "wish you were", "apart"),
                    action = CharacterAction.SITTING,
                    emotionalState = EmotionalState.SAD,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = false
                ),

                // Reading/Books
                ChatTriggerResponse(
                    keywords = listOf("read", "reading", "book", "story", "novel", "📚"),
                    action = CharacterAction.SITTING,
                    emotionalState = EmotionalState.FOCUSED,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.FIDGET,
                    affectsPartner = false
                ),

                // Breakfast time
                ChatTriggerResponse(
                    keywords = listOf("breakfast", "morning routine", "coffee", "tea", "toast"),
                    action = CharacterAction.COOKING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.KITCHEN,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = true
                ),

                // Movie night
                ChatTriggerResponse(
                    keywords = listOf("movie night", "film", "cinema", "popcorn", "thriller"),
                    action = CharacterAction.WATCHING_TV,
                    emotionalState = EmotionalState.HAPPY,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = true
                ),

                // Workout/Gym
                ChatTriggerResponse(
                    keywords = listOf("workout", "gym", "exercise", "cardio", "weights", "💪"),
                    action = CharacterAction.PLAYING,
                    emotionalState = EmotionalState.PLAYFUL,
                    zone = ZoneType.GAME_AREA,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = false
                ),

                // Apologize/Make up
                ChatTriggerResponse(
                    keywords = listOf("sorry", "apologize", "forgive", "i'm sorry", "make up"),
                    action = CharacterAction.CUDDLING,
                    emotionalState = EmotionalState.ROMANTIC,
                    zone = null,
                    animationType = MicroAnimationType.REACH,
                    affectsPartner = true
                ),

                // Feeling sick
                ChatTriggerResponse(
                    keywords = listOf("sick", "fever", "cold", "ill", "not feeling well", "🤒"),
                    action = CharacterAction.SLEEPING,
                    emotionalState = EmotionalState.SAD,
                    zone = ZoneType.BED,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = false
                ),

                // Romantic dinner
                ChatTriggerResponse(
                    keywords = listOf("dinner date", "romantic dinner", "fancy dinner", "candlelight"),
                    action = CharacterAction.COOKING,
                    emotionalState = EmotionalState.ROMANTIC,
                    zone = ZoneType.KITCHEN,
                    animationType = MicroAnimationType.FIDGET,
                    affectsPartner = true
                ),

                // Adventure/Travel
                ChatTriggerResponse(
                    keywords = listOf("travel", "adventure", "trip", "vacation", "explore", "✈️"),
                    action = CharacterAction.DANCING,
                    emotionalState = EmotionalState.PLAYFUL,
                    zone = ZoneType.GAME_AREA,
                    animationType = MicroAnimationType.WAVE,
                    affectsPartner = true
                ),

                // Congratulations/Proud
                ChatTriggerResponse(
                    keywords = listOf("proud", "achievement", "win", "success", "🏆", "congratulations"),
                    action = CharacterAction.DANCING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = null,
                    animationType = MicroAnimationType.SHIFT_WEIGHT,
                    affectsPartner = true
                ),

                // Photography/Selfie
                ChatTriggerResponse(
                    keywords = listOf("photo", "picture", "selfie", "smile", "📸", "camera"),
                    action = CharacterAction.TALKING,
                    emotionalState = EmotionalState.HAPPY,
                    zone = null,
                    animationType = MicroAnimationType.WAVE,
                    affectsPartner = true
                ),

                // Weather-related
                ChatTriggerResponse(
                    keywords = listOf("rain", "sunny", "weather", "cloudy", "snowy", "☀️", "🌧️"),
                    action = CharacterAction.WATCHING_TV,
                    emotionalState = EmotionalState.NEUTRAL,
                    zone = ZoneType.SOFA,
                    animationType = MicroAnimationType.SWAY,
                    affectsPartner = false
                )
            )
        )
    }

    fun processMessage(
        sender: String,
        content: String,
        currentState: DualCharacterState
    ): Pair<List<CharacterCommand>, DualCharacterState> {
        val commands = mutableListOf<CharacterCommand>()
        var updatedState = currentState

        // Identify who sent the message
        val senderIdentity = when (sender.lowercase()) {
            "zain" -> CharacterIdentity.ZAIN
            "sanu" -> CharacterIdentity.SANU
            else -> null
        }

        // Find matching triggers
        val matchedTriggers = triggerRules.filter { rule ->
            rule.keywords.any { keyword ->
                content.contains(keyword, ignoreCase = true)
            }
        }

        // Apply triggers
        matchedTriggers.forEach { trigger ->
            val characterToReact = senderIdentity ?: CharacterIdentity.ZAIN
            val partnerIdentity = if (characterToReact == CharacterIdentity.ZAIN) {
                CharacterIdentity.SANU
            } else {
                CharacterIdentity.ZAIN
            }

            // Update emotional state
            val emotionalCommand = CharacterCommand(
                id = java.util.UUID.randomUUID().toString(),
                actor = characterToReact,
                actionType = ActionType.CHANGE_MOOD,
                parameter = trigger.emotionalState.name.lowercase()
            )
            commands.add(emotionalCommand)

            // If has zone, create movement command
            trigger.zone?.let { zoneType ->
                val layout = environment.getRoomLayout(currentState.currentRoom)
                val targetZone = layout?.zones?.firstOrNull { it.type == zoneType }

                targetZone?.let { zone ->
                    val moveCommand = CharacterCommand(
                        id = java.util.UUID.randomUUID().toString(),
                        actor = characterToReact,
                        actionType = ActionType.GO_TO_ZONE,
                        targetZone = zone,
                        targetRoom = zone.roomId
                    )
                    commands.add(moveCommand)

                    // If action should affect partner too
                    if (trigger.affectsPartner && zone.dualAction) {
                        val partnerMoveCommand = CharacterCommand(
                            id = java.util.UUID.randomUUID().toString(),
                            actor = partnerIdentity,
                            actionType = ActionType.GO_TO_ZONE,
                            targetZone = zone,
                            targetRoom = zone.roomId
                        )
                        commands.add(partnerMoveCommand)

                        // Enable interaction
                        updatedState = updatedState.copy(
                            interactingTogether = true,
                            interactionType = trigger.emotionalState.name.lowercase()
                        )
                    }
                }
            }

            // Trigger micro-animation if specified
            trigger.animationType?.let { animType ->
                val animation = when (animType) {
                    MicroAnimationType.REACH -> MicroAnimation(
                        type = MicroAnimationType.REACH,
                        duration = 800L,
                        loopCount = 1,
                        intensity = 1.0f
                    )
                    MicroAnimationType.NOD -> MicroAnimation(
                        type = MicroAnimationType.NOD,
                        duration = 600L,
                        loopCount = 1,
                        intensity = 0.8f
                    )
                    MicroAnimationType.FIDGET -> MicroAnimation(
                        type = MicroAnimationType.FIDGET,
                        duration = 1500L,
                        loopCount = 1,
                        intensity = 0.7f
                    )
                    else -> MicroAnimation(
                        type = animType,
                        duration = 1000L,
                        loopCount = 1,
                        intensity = 0.8f
                    )
                }
                // Could queue micro-animations separately
            }
        }

        return Pair(commands, updatedState)
    }

    fun addCustomTrigger(trigger: ChatTriggerResponse) {
        triggerRules.add(trigger)
    }

    fun removeTrigger(keywords: List<String>) {
        triggerRules.removeAll { it.keywords == keywords }
    }

    fun getTriggers(): List<ChatTriggerResponse> = triggerRules.toList()
}
