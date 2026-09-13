package com.calcvault.chat

import android.view.ViewGroup
import com.calcvault.emotional.EmotionalAnimationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BackgroundCharacterInteraction - Controls how background characters react to chat
 *
 * Replaces the fast, unreadable dialogue spam with intelligent interactions:
 * - Idle mode: Characters animate subtly without dialogue
 * - Trigger mode: Characters react to actual message content (recommended)
 * - Emotion mode: Character expressions match message sentiment
 */

/**
 * Background interaction mode
 */
enum class BackgroundInteractionMode {
    /**
     * IDLE: Characters animate softly (bobbing, subtle movement)
     * No dialogue or reactions. Good for distraction-free chatting.
     */
    IDLE,

    /**
     * TRIGGER: Characters react to message content
     * - Doraemon smiles/waves at happy messages
     * - Shizuka shows sadness/concern at sad messages
     * - Both react to keywords (love, miss, happy, etc.)
     * Recommended for immersive experience.
     */
    TRIGGER,

    /**
     * EMOTION: Extended trigger mode with sentiment analysis
     * Characters have nuanced emotional expressions based on full message tone.
     * Most immersive but slower (requires sentiment analysis).
     */
    EMOTION
}

/**
 * Background character animation request
 */
data class CharacterAnimationRequest(
    val character: ChatCharacter,
    val animationType: CharacterAnimationType,
    val intensity: Int = 10, // 1-20
    val duration: Long = 600L,
    val delayMs: Long = 0L
)

/**
 * Types of character animations/reactions
 */
enum class CharacterAnimationType {
    // Doraemon-specific animations
    SMILE, // Happy expression
    BLUSH, // Embarrassed/shy
    WAVE, // Friendly gesture
    NOD, // Agreement
    SHAKE_HEAD, // Disagreement
    SURPRISED, // Shocked expression
    SAD_FACE, // Sad/concerned
    EXCITED_SPIN, // Happy spinning
    THINKING, // Contemplative pose
    HEART_EYES, // Love/affection

    // Movement
    BOUNCE, // Bouncy happy movement
    WOBBLE, // Uncertain/confused
    APPROACH, // Move closer
    RETREAT // Move away
}

/**
 * BackgroundCharacterInteractionEngine - Orchestrates character reactions to chat
 */
class BackgroundCharacterInteractionEngine(
    private val rootContainer: ViewGroup,
    private val animationEngine: EmotionalAnimationEngine,
    private val coroutineScope: CoroutineScope
) {

    private val isRunning = AtomicBoolean(false)
    private var currentMode = BackgroundInteractionMode.TRIGGER
    private var idleAnimationRunning = AtomicBoolean(false)

    // Configuration
    var idleAnimationSpeed = 1.0f // 0.5 = half speed, 2.0 = double speed
    var triggerAnimationIntensity = 1.0f
    var enableCharacterInteractions = true

    // Last interaction timestamp (prevent spam)
    private var lastInteractionTime = 0L
    private val interactionCooldownMs = 500L // Min time between reactions

    /**
     * Initialize the interaction engine
     */
    fun initialize(mode: BackgroundInteractionMode = BackgroundInteractionMode.TRIGGER) {
        if (isRunning.getAndSet(true)) {
            return // Already running
        }

        currentMode = mode
        startIdleAnimation()
    }

    /**
     * Set interaction mode
     */
    fun setInteractionMode(mode: BackgroundInteractionMode) {
        currentMode = mode

        when (mode) {
            BackgroundInteractionMode.IDLE -> {
                // Stop trigger animations, keep idle running
                startIdleAnimation()
            }
            BackgroundInteractionMode.TRIGGER -> {
                startIdleAnimation()
                // Trigger animations will activate on message insertions
            }
            BackgroundInteractionMode.EMOTION -> {
                startIdleAnimation()
                // Extended triggers with sentiment
            }
        }
    }

    /**
     * Start subtle idle animation loop
     * Characters bob, sway, and move naturally without dialogue
     */
    private fun startIdleAnimation() {
        if (idleAnimationRunning.getAndSet(true)) {
            return // Already running
        }

        coroutineScope.launch {
            while (isActive && idleAnimationRunning.get()) {
                try {
                    // Zain idle animation
                    animateCharacterIdle(ChatCharacter.ZAIN)
                    delay(3000L / idleAnimationSpeed.toLong()) // 3 seconds at normal speed

                    // Sanu idle animation
                    animateCharacterIdle(ChatCharacter.SANU)
                    delay(3500L / idleAnimationSpeed.toLong()) // 3.5 seconds at normal speed
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Perform idle animation for character (no dialogue, just natural movement)
     */
    private suspend fun animateCharacterIdle(character: ChatCharacter) {
        when (character) {
            ChatCharacter.ZAIN -> {
                // Doraemon bobs up and down, maybe waves hand
                animateRequest(
                    CharacterAnimationRequest(
                        character = character,
                        animationType = CharacterAnimationType.BOUNCE,
                        intensity = 4, // Subtle
                        duration = 2000L
                    )
                )
            }
            ChatCharacter.SANU -> {
                // Shizuka sways or looks around
                animateRequest(
                    CharacterAnimationRequest(
                        character = character,
                        animationType = CharacterAnimationType.WOBBLE,
                        intensity = 3, // Extra subtle
                        duration = 2000L
                    )
                )
            }
        }
    }

    /**
     * Trigger character reaction based on message content
     * Call this when a new message arrives
     */
    fun onMessageArrived(message: String, senderCharacter: ChatCharacter) {
        if (!enableCharacterInteractions || currentMode == BackgroundInteractionMode.IDLE) {
            return
        }

        if (!canTriggerInteraction()) {
            return // Cooldown active
        }

        val otherCharacter = if (senderCharacter == ChatCharacter.ZAIN) {
            ChatCharacter.SANU
        } else {
            ChatCharacter.ZAIN
        }

        // Find trigger for sender's message
        val trigger = CharacterReactionConfig.findTriggerForMessage(message, senderCharacter)
        if (trigger != null && currentMode == BackgroundInteractionMode.TRIGGER) {
            // Sender reacts to their own message
            triggerCharacterReaction(senderCharacter, trigger.reactionType.toAnimationType())

            // Other character responds
            val responseDelay = 400L + (triggerAnimationIntensity * 200L).toLong()
            coroutineScope.launch {
                delay(responseDelay)
                reactToSenderMessage(otherCharacter, message)
            }

            lastInteractionTime = System.currentTimeMillis()
        }
    }

    /**
     * Other character responds to sender's message
     */
    private fun reactToSenderMessage(respondingCharacter: ChatCharacter, message: String) {
        // Determine appropriate response animation
        val responseType = when {
            message.contains(Regex("love|adore|❤️|💕")) -> {
                CharacterAnimationType.HEART_EYES
            }
            message.contains(Regex("sad|😢|cry|upset|miss")) -> {
                CharacterAnimationType.SAD_FACE
            }
            message.contains(Regex("wow|amazing|😮|surprise|!+")) -> {
                CharacterAnimationType.SURPRISED
            }
            message.contains(Regex("haha|lol|😂|funny")) -> {
                CharacterAnimationType.SMILE
            }
            message.contains(Regex("yes|agree|👍|ok")) -> {
                CharacterAnimationType.NOD
            }
            message.contains(Regex("excited|happy|woohoo|🎉")) -> {
                CharacterAnimationType.EXCITED_SPIN
            }
            else -> CharacterAnimationType.SMILE // Default: friendly
        }

        triggerCharacterReaction(respondingCharacter, responseType)
    }

    /**
     * Trigger a specific character reaction
     */
    private fun triggerCharacterReaction(character: ChatCharacter, reactionType: CharacterAnimationType) {
        val intensity = (triggerAnimationIntensity * 12f).toInt().coerceIn(5, 20)

        animateRequest(
            CharacterAnimationRequest(
                character = character,
                animationType = reactionType,
                intensity = intensity,
                duration = 700L
            )
        )
    }

    /**
     * Execute animation request
     */
    private fun animateRequest(request: CharacterAnimationRequest) {
        coroutineScope.launch {
            if (request.delayMs > 0) {
                delay(request.delayMs)
            }

            try {
                when (request.animationType) {
                    // Emoji/particle animations
                    CharacterAnimationType.HEART_EYES -> {
                        animationEngine.triggerCustomEmojiAnimation(
                            emoji = "❤️",
                            intensity = request.intensity,
                            direction = "UP",
                            speed = if (request.intensity > 15) "FAST" else "MEDIUM"
                        )
                    }
                    CharacterAnimationType.EXCITED_SPIN -> {
                        animationEngine.triggerCustomEmojiAnimation(
                            emoji = "✨",
                            intensity = request.intensity,
                            direction = "RANDOM",
                            speed = "FAST"
                        )
                    }
                    CharacterAnimationType.SMILE -> {
                        animationEngine.triggerCustomEmojiAnimation(
                            emoji = "😊",
                            intensity = 5,
                            direction = "UP"
                        )
                    }
                    CharacterAnimationType.SURPRISED -> {
                        animationEngine.triggerCustomEmojiAnimation(
                            emoji = "😮",
                            intensity = request.intensity,
                            direction = "UP",
                            speed = "FAST"
                        )
                    }
                    CharacterAnimationType.SAD_FACE -> {
                        animationEngine.triggerCustomEmojiAnimation(
                            emoji = "😢",
                            intensity = 8,
                            direction = "DOWN",
                            speed = "SLOW"
                        )
                    }
                    CharacterAnimationType.BLUSH -> {
                        animationEngine.triggerCustomEmojiAnimation(
                            emoji = "💕",
                            intensity = 10,
                            direction = "UP"
                        )
                    }

                    // Movement-based animations (use call pulse)
                    CharacterAnimationType.BOUNCE,
                    CharacterAnimationType.WOBBLE,
                    CharacterAnimationType.NOD,
                    CharacterAnimationType.WAVE -> {
                        animationEngine.startCallPulse(rootContainer)
                    }

                    else -> {
                        // Default: gentle particle effect
                        animationEngine.triggerCustomEmojiAnimation(
                            emoji = "✨",
                            intensity = 3,
                            direction = "UP"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Check if we can trigger interaction (cooldown)
     */
    private fun canTriggerInteraction(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastInteractionTime) >= interactionCooldownMs
    }

    /**
     * Stop interaction engine
     */
    fun stop() {
        isRunning.set(false)
        idleAnimationRunning.set(false)
    }

    /**
     * Check if running
     */
    fun isActive(): Boolean = isRunning.get()
}

/**
 * Helper to create interaction engine and attach to activity
 */
object BackgroundCharacterInteractionHelper {

    fun createAndInitialize(
        rootContainer: ViewGroup,
        animationEngine: EmotionalAnimationEngine,
        coroutineScope: CoroutineScope,
        mode: BackgroundInteractionMode = BackgroundInteractionMode.TRIGGER
    ): BackgroundCharacterInteractionEngine {
        val engine = BackgroundCharacterInteractionEngine(
            rootContainer,
            animationEngine,
            coroutineScope
        )
        engine.initialize(mode)
        return engine
    }
}
