package com.calcvault.chat

import android.graphics.Color
import androidx.annotation.ColorInt
import com.calcvault.emotional.ThemeEngine
import com.calcvault.messaging.MessageRecord

/**
 * CharacterChatTheme - Maps characters to distinct visual styles in chat
 *
 * This system transforms the chat interface into a character-driven conversation,
 * where each character has:
 * - Distinct bubble colors and styles
 * - Character-appropriate animations
 * - Visual identity markers
 * - Emotional reaction capabilities
 */

/**
 * Character identity in the chat system
 */
enum class ChatCharacter(val characterId: String) {
    ZAIN("zain"), // Maps to Doraemon
    SANU("sanu"); // Maps to Girl character

    companion object {
        fun fromSenderId(senderId: String): ChatCharacter {
            return when (senderId.lowercase()) {
                "zain" -> ZAIN
                "sanu" -> SANU
                else -> ZAIN // Default to Zain
            }
        }
    }
}

/**
 * Character bubble style - defines visual appearance for each character's messages
 */
data class CharacterBubbleStyle(
    val character: ChatCharacter,

    // Colors
    @ColorInt val bubbleColor: Int,
    @ColorInt val textColor: Int,
    @ColorInt val shadowColor: Int,
    @ColorInt val borderColor: Int,

    // Style properties
    val cornerRadius: Float = 18f,
    val elevation: Float = 2f,
    val borderWidth: Float = 0f,

    // Animation properties
    val animationIntensity: Float = 1f, // 0-2, 1 = normal
    val enableCharacterReactions: Boolean = true,
    val enableBackgroundInteraction: Boolean = true,

    // Character visual identity
    val characterIconEmoji: String = "", // Optional icon to embed
    val characterNameLabel: String = character.characterId.replaceFirstChar { it.uppercase() }
)

/**
 * Zain's (Doraemon) bubble style
 */
object ZainBubbleStyle {
    fun create(appTheme: ThemeEngine.CalcVaultTheme): CharacterBubbleStyle {
        return CharacterBubbleStyle(
            character = ChatCharacter.ZAIN,
            bubbleColor = adjustBrightness(appTheme.sentBubble, 0.1f), // Slightly adjusted sent color
            textColor = appTheme.primaryText,
            shadowColor = adjustBrightness(appTheme.sentBubble, -0.4f),
            borderColor = appTheme.accentColor,
            cornerRadius = 18f,
            elevation = 3f,
            borderWidth = 1.5f,
            animationIntensity = 1.2f, // Doraemon is expressive
            enableCharacterReactions = true,
            enableBackgroundInteraction = true,
            characterIconEmoji = "🤖", // Doraemon-like icon
            characterNameLabel = "Doraemon" // Optional display name
        )
    }

    private fun adjustBrightness(@ColorInt color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        val r = (Color.red(color) * (1 + factor)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1 + factor)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1 + factor)).toInt().coerceIn(0, 255)
        return Color.argb(alpha, r, g, b)
    }
}

/**
 * Sanu's (Girl) bubble style
 */
object SanuBubbleStyle {
    fun create(appTheme: ThemeEngine.CalcVaultTheme): CharacterBubbleStyle {
        return CharacterBubbleStyle(
            character = ChatCharacter.SANU,
            bubbleColor = adjustBrightness(appTheme.receivedBubble, 0.15f), // Distinct from sent
            textColor = appTheme.primaryText,
            shadowColor = adjustBrightness(appTheme.receivedBubble, -0.35f),
            borderColor = adjustBrightness(appTheme.accentColor, 0.2f), // Softer accent
            cornerRadius = 18f,
            elevation = 2f,
            borderWidth = 1.2f,
            animationIntensity = 0.9f, // More subtle than Doraemon
            enableCharacterReactions = true,
            enableBackgroundInteraction = true,
            characterIconEmoji = "👧", // Girl-like icon
            characterNameLabel = "Shizuka" // Optional display name
        )
    }

    private fun adjustBrightness(@ColorInt color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        val r = (Color.red(color) * (1 + factor)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1 + factor)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1 + factor)).toInt().coerceIn(0, 255)
        return Color.argb(alpha, r, g, b)
    }
}

/**
 * CharacterBubbleStyleProvider - Generates bubble styles based on sender
 */
object CharacterBubbleStyleProvider {

    private var cachedAppTheme: ThemeEngine.CalcVaultTheme? = null
    private var cachedZainStyle: CharacterBubbleStyle? = null
    private var cachedSanuStyle: CharacterBubbleStyle? = null

    /**
     * Get bubble style for a specific character
     */
    fun getStyleForCharacter(
        character: ChatCharacter,
        appTheme: ThemeEngine.CalcVaultTheme
    ): CharacterBubbleStyle {
        // Invalidate cache if theme changed
        if (cachedAppTheme?.type != appTheme.type) {
            cachedAppTheme = appTheme
            cachedZainStyle = null
            cachedSanuStyle = null
        }

        return when (character) {
            ChatCharacter.ZAIN -> {
                if (cachedZainStyle == null) {
                    cachedZainStyle = ZainBubbleStyle.create(appTheme)
                }
                cachedZainStyle!!
            }
            ChatCharacter.SANU -> {
                if (cachedSanuStyle == null) {
                    cachedSanuStyle = SanuBubbleStyle.create(appTheme)
                }
                cachedSanuStyle!!
            }
        }
    }

    /**
     * Get style for message sender
     */
    fun getStyleForSender(
        senderId: String,
        appTheme: ThemeEngine.CalcVaultTheme
    ): CharacterBubbleStyle {
        val character = ChatCharacter.fromSenderId(senderId)
        return getStyleForCharacter(character, appTheme)
    }

    /**
     * Get style for message record
     */
    fun getStyleForMessage(
        message: MessageRecord,
        appTheme: ThemeEngine.CalcVaultTheme
    ): CharacterBubbleStyle {
        return getStyleForSender(message.from, appTheme)
    }

    /**
     * Clear cache (call on theme change)
     */
    fun clearCache() {
        cachedAppTheme = null
        cachedZainStyle = null
        cachedSanuStyle = null
    }
}

/**
 * Character reaction trigger - emotional responses to message content
 */
data class CharacterReactionTrigger(
    val keywords: List<String>,
    val character: ChatCharacter,
    val reactionType: ReactionType,
    val reactionIntensity: Int = 10, // 1-20
    val animationDuration: Long = 800L
)

/**
 * Types of character reactions
 */
enum class ReactionType {
    BLUSH, // Character blushes (love, joy, compliment)
    SIGH, // Character sighs (sadness, resignation)
    SURPRISE, // Character is surprised
    LAUGH, // Character laughs
    THINK, // Character thinks/contemplates
    WAVE, // Character waves
    NODS, // Character nods in agreement
    HEARTS, // Heart particles emit from character
    TEARS, // Sad/emotional tears
    SPIN // Excited spinning
}

/**
 * Character reaction configuration
 */
object CharacterReactionConfig {

    private val zainReactions = listOf(
        // Zain reactions
        CharacterReactionTrigger(
            keywords = listOf("love", "love you", "ily", "❤️", "💕", "adore"),
            character = ChatCharacter.ZAIN,
            reactionType = ReactionType.BLUSH,
            reactionIntensity = 12
        ),
        CharacterReactionTrigger(
            keywords = listOf("sad", "sad emoji", "😢", "cry", "upset", "angry"),
            character = ChatCharacter.ZAIN,
            reactionType = ReactionType.SIGH,
            reactionIntensity = 10
        ),
        CharacterReactionTrigger(
            keywords = listOf("wow", "amazing", "😮", "surprise", "shocked", "!!", "???"),
            character = ChatCharacter.ZAIN,
            reactionType = ReactionType.SURPRISE,
            reactionIntensity = 15
        ),
        CharacterReactionTrigger(
            keywords = listOf("haha", "lol", "😂", "funny", "laugh", "hilarious"),
            character = ChatCharacter.ZAIN,
            reactionType = ReactionType.LAUGH,
            reactionIntensity = 14
        ),
        CharacterReactionTrigger(
            keywords = listOf("yes", "agree", "👍", "correct", "right", "ok"),
            character = ChatCharacter.ZAIN,
            reactionType = ReactionType.NODS,
            reactionIntensity = 8
        )
    )

    private val sanuReactions = listOf(
        // Sanu reactions
        CharacterReactionTrigger(
            keywords = listOf("love", "love you", "ily", "❤️", "💕", "adore"),
            character = ChatCharacter.SANU,
            reactionType = ReactionType.BLUSH,
            reactionIntensity = 10
        ),
        CharacterReactionTrigger(
            keywords = listOf("sad", "sad emoji", "😢", "cry", "upset", "miss you"),
            character = ChatCharacter.SANU,
            reactionType = ReactionType.TEARS,
            reactionIntensity = 12
        ),
        CharacterReactionTrigger(
            keywords = listOf("wow", "amazing", "😮", "surprise", "shocked", "!!", "beautiful"),
            character = ChatCharacter.SANU,
            reactionType = ReactionType.SURPRISE,
            reactionIntensity = 12
        ),
        CharacterReactionTrigger(
            keywords = listOf("haha", "lol", "😂", "funny", "laugh"),
            character = ChatCharacter.SANU,
            reactionType = ReactionType.LAUGH,
            reactionIntensity = 11
        ),
        CharacterReactionTrigger(
            keywords = listOf("yes", "agree", "👍", "okay", "sure"),
            character = ChatCharacter.SANU,
            reactionType = ReactionType.NODS,
            reactionIntensity = 9
        ),
        CharacterReactionTrigger(
            keywords = listOf("happy", "excited", "woohoo", "🎉", "yay", "great"),
            character = ChatCharacter.SANU,
            reactionType = ReactionType.SPIN,
            reactionIntensity = 14
        )
    )

    /**
     * Get reactions for character
     */
    fun getReactionsForCharacter(character: ChatCharacter): List<CharacterReactionTrigger> {
        return when (character) {
            ChatCharacter.ZAIN -> zainReactions
            ChatCharacter.SANU -> sanuReactions
        }
    }

    /**
     * Check if message content triggers a reaction
     */
    fun findTriggerForMessage(
        message: String,
        character: ChatCharacter
    ): CharacterReactionTrigger? {
        val reactions = getReactionsForCharacter(character)
        val lowerMessage = message.lowercase()

        return reactions.firstOrNull { trigger ->
            trigger.keywords.any { keyword ->
                lowerMessage.contains(keyword, ignoreCase = true)
            }
        }
    }
}

/**
 * Message styling context - combines character style with animation settings
 */
data class MessageStylingContext(
    val message: MessageRecord,
    val character: ChatCharacter,
    val bubbleStyle: CharacterBubbleStyle,
    val appTheme: ThemeEngine.CalcVaultTheme,
    val reactionTrigger: CharacterReactionTrigger? = null,
    val animateEntry: Boolean = true,
    val animatePressure: Boolean = true,
    val animateReaction: Boolean = true
)

/**
 * Extension function to convert ReactionType to CharacterAnimationType
 */
fun ReactionType.toAnimationType(): CharacterAnimationType {
    return when (this) {
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
    }
}
