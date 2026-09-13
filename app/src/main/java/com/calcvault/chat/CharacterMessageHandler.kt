package com.calcvault.chat

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.cardview.widget.CardView
import com.calcvault.emotional.ThemeEngine
import com.calcvault.messaging.MessageRecord

/**
 * CharacterMessageHandler - Integrates character bubble styling into MessageAdapter
 *
 * This handler is inserted into MessageAdapter.onBindViewHolder() to:
 * 1. Determine character for message sender
 * 2. Apply character-specific bubble styling
 * 3. Trigger character reactions for emoji/animation
 * 4. Handle entry animations per character
 */
class CharacterMessageHandler(
    private val themeEngine: ThemeEngine,
    private val animationCallback: (CharacterAnimationRequest) -> Unit = {}
) {

    private val handler = Handler(Looper.getMainLooper())
    private val successiveAnimationDelayMs = 100L // Stagger messages by 100ms

    /**
     * Apply character styling to message bubble
     *
     * Usage in MessageAdapter.onBindViewHolder():
     * ```
     * characterHandler.applyCharacterStyling(
     *     message = messageRecord,
     *     bubble = holder.cardBubble,
     *     textView = holder.tvText,
     *     position = position
     * )
     * ```
     */
    fun applyCharacterStyling(
        message: MessageRecord,
        bubble: CardView,
        textView: AppCompatTextView,
        shadowView: View? = null,
        position: Int = 0
    ) {
        try {
            val appTheme = themeEngine.getCurrentTheme()
            val character = ChatCharacter.fromSenderId(message.from)
            val bubbleStyle = CharacterBubbleStyleProvider.getStyleForCharacter(character, appTheme)

            // Apply bubble colors and styling
            bubble.setCardBackgroundColor(bubbleStyle.bubbleColor)
            bubble.elevation = bubbleStyle.elevation
            bubble.radius = bubbleStyle.cornerRadius

            // Apply text color
            textView.setTextColor(bubbleStyle.textColor)

            // Apply shadow if available
            shadowView?.let {
                it.setBackgroundColor(bubbleStyle.shadowColor)
                it.alpha = 0.2f
            }

            // Animate entry
            animateMessageEntry(bubble, textView, position, bubbleStyle.animationIntensity)

            // Check for character reactions
            if (bubbleStyle.enableCharacterReactions) {
                triggerCharacterReaction(message, character, bubbleStyle)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Animate message entry with character-specific timing
     */
    private fun animateMessageEntry(
        bubble: View,
        textView: View,
        position: Int,
        animationIntensity: Float
    ) {
        // Disable staggered delay to prevent screen "moving"/lags on scroll
        // val staggerDelay = position * successiveAnimationDelayMs
        
        // Use a fixed small delay if it's one of the first few items, otherwise none
        val staggerDelay = if (position < 10) position * 30L else 0L

        // Reduced translationY to keep screen steady
        bubble.alpha = 0f
        bubble.translationY = 10f
        textView.alpha = 0f

        // Animate
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(bubble, "alpha", 0f, 1f).apply {
                duration = (120L * animationIntensity).toLong().coerceIn(80L, 200L)
                interpolator = DecelerateInterpolator()
            },
            ObjectAnimator.ofFloat(bubble, "translationY", 10f, 0f).apply {
                duration = (120L * animationIntensity).toLong().coerceIn(80L, 200L)
                interpolator = DecelerateInterpolator()
            },
            ObjectAnimator.ofFloat(textView, "alpha", 0f, 1f).apply {
                duration = (80L * animationIntensity).toLong().coerceIn(60L, 150L)
                interpolator = DecelerateInterpolator()
            }
        )
        set.startDelay = staggerDelay
        set.start()
    }

    /**
     * Trigger character reaction for message content
     */
    private fun triggerCharacterReaction(
        message: MessageRecord,
        character: ChatCharacter,
        bubbleStyle: CharacterBubbleStyle
    ) {
        val trigger = CharacterReactionConfig.findTriggerForMessage(message.content, character)
        if (trigger != null) {
            val request = CharacterAnimationRequest(
                character = character,
                animationType = when (trigger.reactionType) {
                    ReactionType.BLUSH -> CharacterAnimationType.BLUSH
                    ReactionType.SIGH -> CharacterAnimationType.SAD_FACE
                    ReactionType.SURPRISE -> CharacterAnimationType.SURPRISED
                    ReactionType.LAUGH -> CharacterAnimationType.SMILE
                    ReactionType.THINK -> CharacterAnimationType.THINKING
                    ReactionType.WAVE -> CharacterAnimationType.WAVE
                    ReactionType.NODS -> CharacterAnimationType.NOD
                    ReactionType.HEARTS -> CharacterAnimationType.HEART_EYES
                    ReactionType.TEARS -> CharacterAnimationType.SAD_FACE
                    ReactionType.SPIN -> CharacterAnimationType.EXCITED_SPIN
                },
                intensity = trigger.reactionIntensity,
                duration = trigger.animationDuration,
                delayMs = 200L // Small delay after message appears
            )
            animationCallback(request)
        }
    }

    /**
     * Apply pressure/press animation to message bubble
     * Call on message bubble press/click
     */
    fun applyPressAnimation(bubble: CardView, duration: Long = 150L) {
        val scaleDown = ObjectAnimator.ofFloat(bubble, "scaleX", 1f, 0.97f).apply {
            this.duration = duration / 2
        }
        val scaleUp = ObjectAnimator.ofFloat(bubble, "scaleX", 0.97f, 1f).apply {
            this.duration = duration / 2
        }

        val set = AnimatorSet()
        set.playSequentially(scaleDown, scaleUp)
        set.start()
    }

    /**
     * Create a message styling context for advanced use cases
     */
    fun createMessageStylingContext(
        message: MessageRecord
    ): MessageStylingContext {
        val appTheme = themeEngine.getCurrentTheme()
        val character = ChatCharacter.fromSenderId(message.from)
        val bubbleStyle = CharacterBubbleStyleProvider.getStyleForCharacter(character, appTheme)
        val reactionTrigger = CharacterReactionConfig.findTriggerForMessage(message.content, character)

        return MessageStylingContext(
            message = message,
            character = character,
            bubbleStyle = bubbleStyle,
            appTheme = appTheme,
            reactionTrigger = reactionTrigger,
            animateEntry = true,
            animatePressure = true,
            animateReaction = bubbleStyle.enableCharacterReactions
        )
    }

    /**
     * Clear resources
     */
    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}

/**
 * Extension function to easily apply character styling in MessageAdapter
 *
 * Usage:
 * ```
 * holder.applyCharacterStyling(message, characterHandler)
 * ```
 */
fun Any.applyCharacterStylingExt(
    message: MessageRecord,
    handler: CharacterMessageHandler,
    bubble: CardView,
    textView: AppCompatTextView
) {
    handler.applyCharacterStyling(message, bubble, textView)
}
