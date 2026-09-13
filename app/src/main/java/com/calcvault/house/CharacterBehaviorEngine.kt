package com.calcvault.house

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * CharacterBehaviorEngine.kt - Character animation, movement, and micro-behaviors
 *
 * Manages:
 * - Character position and movement interpolation
 * - Micro-animations (blinking, breathing, etc.)
 * - Emotional state transitions
 * - Natural movement timing
 * - Character interaction logic
 */

class CharacterBehaviorEngine(
    private val environment: HouseEnvironment
) {
    private val microAnimations = mutableListOf<MicroAnimation>()
    private var animationTime = 0L
    private var isEnabled = true

    companion object {
        const val WALK_SPEED = 2.5f // Pixels per update
        const val TURN_SPEED = 15f // Degrees per update
        const val BREATHING_INTENSITY = 0.05f
        const val BLINK_DURATION = 100L
        const val BLINK_INTERVAL = 3500L // Average blink interval
    }

    fun updateCharacter(
        state: CharacterState,
        deltaTime: Long
    ): CharacterState {
        if (!isEnabled) return state

        var newState = state.copy(timeInAction = state.timeInAction + deltaTime)

        // Update position if walking
        if (newState.isWalking && newState.position != newState.targetPosition) {
            newState = moveTowardTarget(newState, deltaTime)
        }

        // Update animation progress
        newState = updateAnimationProgress(newState, deltaTime)

        // Ensure valid position
        if (!environment.isValidPosition(newState.position, environment.getCurrentRoom())) {
            newState = newState.copy(position = clampPosition(newState.position))
        }

        return newState
    }

    fun updateDualCharacters(
        state: DualCharacterState,
        deltaTime: Long
    ): DualCharacterState {
        var updated = state.copy(
            zain = updateCharacter(state.zain, deltaTime),
            sanu = updateCharacter(state.sanu, deltaTime)
        )

        // Handle interactions
        if (updated.interactingTogether) {
            updated = updateInteraction(updated, deltaTime)
        }

        return updated
    }

    private fun moveTowardTarget(
        state: CharacterState,
        deltaTime: Long
    ): CharacterState {
        val dx = state.targetPosition.x - state.position.x
        val dy = state.targetPosition.y - state.position.y
        val distance = sqrt(dx * dx + dy * dy)

        // Close enough to target?
        if (distance < WALK_SPEED) {
            return state.copy(
                position = state.targetPosition,
                isWalking = false,
                action = CharacterAction.IDLE
            )
        }

        // Normalize direction
        val dirX = dx / distance
        val dirY = dy / distance

        // Calculate rotation angle (0 = right, 90 = down, 180 = left, 270 = up)
        val targetRotation = (kotlin.math.atan2(dirY, dirX) * 180f / kotlin.math.PI).toFloat()

        // Smooth rotation
        val newRotation = rotateTowardAngle(state.rotation, targetRotation, TURN_SPEED)

        // Move
        val newX = state.position.x + dirX * state.speed
        val newY = state.position.y + dirY * state.speed

        return state.copy(
            position = PointF(newX, newY),
            rotation = newRotation,
            isWalking = true,
            action = CharacterAction.WALKING
        )
    }

    private fun updateAnimationProgress(
        state: CharacterState,
        deltaTime: Long
    ): CharacterState {
        val progressIncrement = (deltaTime / 1000f) // Convert to seconds
        val newProgress = (state.animationProgress + progressIncrement) % 1f
        return state.copy(animationProgress = newProgress)
    }

    private fun updateInteraction(
        state: DualCharacterState,
        deltaTime: Long
    ): DualCharacterState {
        // Attraction: if characters are close, they should look at each other
        val dx = state.sanu.position.x - state.zain.position.x
        val dy = state.sanu.position.y - state.zain.position.y
        val distance = sqrt(dx * dx + dy * dy)

        val proximityThreshold = 150f

        if (distance < proximityThreshold) {
            // Characters should face each other
            val lookAngleToSanu = (kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI).toFloat()
            val lookAngleToZain = lookAngleToSanu + 180f

            return state.copy(
                zain = state.zain.copy(
                    rotation = rotateTowardAngle(state.zain.rotation, lookAngleToZain, TURN_SPEED * 0.5f)
                ),
                sanu = state.sanu.copy(
                    rotation = rotateTowardAngle(state.sanu.rotation, lookAngleToSanu, TURN_SPEED * 0.5f)
                )
            )
        }

        return state
    }

    fun applyCommand(
        characterState: CharacterState,
        command: CharacterCommand
    ): CharacterState {
        return when (command.actionType) {
            ActionType.GO_TO_ZONE -> {
                val zone = command.targetZone ?: return characterState
                characterState.copy(
                    targetPosition = zone.primaryPosition,
                    isWalking = true,
                    currentZone = zone
                )
            }
            ActionType.DO_ACTION -> {
                val zone = command.targetZone
                characterState.copy(
                    action = when (zone?.animationAction) {
                        "sit" -> CharacterAction.SITTING
                        "cook" -> CharacterAction.COOKING
                        "watch" -> CharacterAction.WATCHING_TV
                        "play" -> CharacterAction.PLAYING
                        "sleep" -> CharacterAction.SLEEPING
                        else -> CharacterAction.IDLE
                    },
                    isWalking = false,
                    animationProgress = 0f
                )
            }
            ActionType.CHANGE_MOOD -> {
                characterState.copy(
                    emotionalState = when (command.parameter) {
                        "happy" -> EmotionalState.HAPPY
                        "sad" -> EmotionalState.SAD
                        "tired" -> EmotionalState.TIRED
                        "romantic" -> EmotionalState.ROMANTIC
                        "playful" -> EmotionalState.PLAYFUL
                        else -> characterState.emotionalState
                    }
                )
            }
            ActionType.TALK_TO_PARTNER -> {
                characterState.copy(
                    action = CharacterAction.TALKING,
                    animationProgress = 0f
                )
            }
            else -> characterState
        }
    }

    // Get micro-animations for current state
    fun getMicroAnimations(state: CharacterState): List<MicroAnimation> {
        val animations = mutableListOf<MicroAnimation>()

        // Always apply idle breathing
        if (state.action == CharacterAction.IDLE || state.action == CharacterAction.SITTING) {
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.BREATHING,
                    duration = 3000L,
                    loopCount = -1, // Infinite
                    intensity = 0.8f
                )
            )
        }

        // Eye tracking if listening
        if (state.action == CharacterAction.LISTENING || state.action == CharacterAction.TALKING) {
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.EYE_TRACK,
                    duration = 2000L,
                    loopCount = -1
                )
            )
            // Add nod animation when talking
            if (state.action == CharacterAction.TALKING) {
                animations.add(
                    MicroAnimation(
                        type = MicroAnimationType.NOD,
                        duration = 600L,
                        loopCount = 3,
                        intensity = 0.9f
                    )
                )
            }
        }

        // Fidget if taking action
        if (state.action == CharacterAction.COOKING) {
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.FIDGET,
                    duration = 1500L,
                    loopCount = -1,
                    intensity = 0.7f
                )
            )
        }

        // Sway if watching
        if (state.action == CharacterAction.WATCHING_TV) {
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.SWAY,
                    duration = 4000L,
                    loopCount = -1,
                    intensity = 0.6f
                )
            )
        }

        // Dancing animations
        if (state.action == CharacterAction.DANCING) {
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.SWAY,
                    duration = 1000L,
                    loopCount = -1,
                    intensity = 1.0f
                )
            )
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.SHIFT_WEIGHT,
                    duration = 500L,
                    loopCount = -1,
                    intensity = 0.9f
                )
            )
        }

        // Reaching animations (gift, reaching, beckoning)
        if (state.action == CharacterAction.REACHING) {
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.REACH,
                    duration = 800L,
                    loopCount = 2,
                    intensity = 0.9f
                )
            )
        }

        // Waving animations
        if (state.emotionalState == EmotionalState.PLAYFUL && state.action == CharacterAction.IDLE) {
            // Randomly add wave animation
            if ((animationTime / 1000L) % 5 == 0L) {
                animations.add(
                    MicroAnimation(
                        type = MicroAnimationType.WAVE,
                        duration = 900L,
                        loopCount = 1,
                        intensity = 0.8f
                    )
                )
            }
        }

        // Emotional state animations
        when (state.emotionalState) {
            EmotionalState.SAD -> {
                animations.add(
                    MicroAnimation(
                        type = MicroAnimationType.HEAD_TURN,
                        duration = 1200L,
                        loopCount = -1,
                        intensity = 0.5f
                    )
                )
            }
            EmotionalState.ROMANTIC -> {
                animations.add(
                    MicroAnimation(
                        type = MicroAnimationType.REACH,
                        duration = 1200L,
                        loopCount = -1,
                        intensity = 0.7f
                    )
                )
            }
            EmotionalState.PLAYFUL -> {
                // Already handled above
            }
            else -> {}
        }

        // Periodic blinking (all states)
        val blinkTiming = (animationTime % BLINK_INTERVAL) / BLINK_INTERVAL
        if (blinkTiming > 0.85f) { // Blink near end of interval
            animations.add(
                MicroAnimation(
                    type = MicroAnimationType.BLINK,
                    duration = BLINK_DURATION,
                    loopCount = 1,
                    intensity = 1f
                )
            )
        }

        return animations
    }

    // Get animation-specific visual values
    fun getAnimationValue(
        animation: MicroAnimation,
        elapsed: Long,
        intensity: Float = 1.0f
    ): Float {
        val cycle = if (animation.duration > 0) {
            ((elapsed % animation.duration) / animation.duration.toFloat())
        } else {
            0f
        }

        return when (animation.type) {
            MicroAnimationType.BREATHING -> {
                // Sine wave for smooth breathing
                (kotlin.math.sin(cycle * 2 * kotlin.math.PI) * intensity).toFloat()
            }
            MicroAnimationType.BLINK -> {
                // Sharp blink - closed most of time
                if (cycle < 0.2f) 1f else 0f
            }
            MicroAnimationType.SWAY -> {
                // Smooth side-to-side
                (kotlin.math.sin(cycle * 2 * kotlin.math.PI) * intensity).toFloat()
            }
            MicroAnimationType.FIDGET -> {
                // Random-ish jitter
                val jitter = ((cycle * 13) % 1f - 0.5f) * intensity
                jitter * 2
            }
            MicroAnimationType.SHIFT_WEIGHT -> {
                // Alternate weight shift
                if (cycle < 0.5f) {
                    cycle / 0.5f * intensity
                } else {
                    (1f - (cycle - 0.5f) / 0.5f) * intensity
                }
            }
            MicroAnimationType.NOD -> {
                // Head nod motion (down then up)
                val nod = kotlin.math.sin(cycle * kotlin.math.PI) * intensity
                nod.toFloat()
            }
            MicroAnimationType.HEAD_TURN -> {
                // Head turning left-right
                (kotlin.math.sin(cycle * 2 * kotlin.math.PI) * intensity).toFloat()
            }
            MicroAnimationType.REACH -> {
                // Reaching motion (extend then retract)
                val reach = kotlin.math.sin(cycle * kotlin.math.PI)
                (reach * intensity).toFloat()
            }
            MicroAnimationType.WAVE -> {
                // Waving motion (up-down with arm)
                val wave = kotlin.math.sin(cycle * 2 * kotlin.math.PI)
                (wave * intensity).toFloat()
            }
            MicroAnimationType.POINT -> {
                // Pointing gesture (extend quickly)
                val point = if (cycle < 0.3f) (cycle / 0.3f) else 1f
                (point * intensity).toFloat()
            }
            MicroAnimationType.TILT -> {
                // Head tilt animation
                (kotlin.math.sin(cycle * kotlin.math.PI) * intensity).toFloat()
            }
            else -> cycle * intensity
        }
    }

    // Helper: smooth angle rotation
    private fun rotateTowardAngle(current: Float, target: Float, speed: Float): Float {
        var diff = target - current

        // Normalize to -180 to 180
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360

        // Limit rotation speed
        diff = diff.coerceIn(-speed, speed)

        return (current + diff) % 360
    }

    // Helper: clamp position to valid bounds
    private fun clampPosition(position: PointF): PointF {
        val layout = environment.getRoomLayout(environment.getCurrentRoom()) ?: return position
        return PointF(
            position.x.coerceIn(layout.bounds.left, layout.bounds.right),
            position.y.coerceIn(layout.bounds.top, layout.bounds.bottom)
        )
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun tick(deltaTime: Long) {
        animationTime += deltaTime
    }
}
