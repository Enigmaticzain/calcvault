package com.calcvault.house

import com.calcvault.ui.mood.UserMood

/**
 * MoodExtensions.kt - Map between UserMood and EmotionalState
 * Bridges mood system with house companion system
 */

fun UserMood.toEmotionalState(): EmotionalState {
    return when (this.name.lowercase()) {
        "happy", "excited", "joyful" -> EmotionalState.HAPPY
        "sad", "depressed", "melancholic" -> EmotionalState.SAD
        "tired", "exhausted", "sleepy" -> EmotionalState.TIRED
        "romantic", "in love", "affectionate" -> EmotionalState.ROMANTIC
        "playful", "fun", "silly", "goofy" -> EmotionalState.PLAYFUL
        "focused", "concentrated", "determined" -> EmotionalState.FOCUSED
        "resting", "calm", "peaceful" -> EmotionalState.RESTING
        else -> EmotionalState.NEUTRAL
    }
}

fun EmotionalState.toUserMood(userId: String): UserMood {
    val (name, emoji, intensity) = when (this) {
        EmotionalState.HAPPY -> Triple("Happy", "😊", 5)
        EmotionalState.SAD -> Triple("Sad", "😢", 4)
        EmotionalState.TIRED -> Triple("Tired", "😴", 3)
        EmotionalState.ROMANTIC -> Triple("Romantic", "😍", 5)
        EmotionalState.PLAYFUL -> Triple("Playful", "😄", 4)
        EmotionalState.FOCUSED -> Triple("Focused", "🤓", 4)
        EmotionalState.RESTING -> Triple("Resting", "😌", 3)
        EmotionalState.NEUTRAL -> Triple("Neutral", "😐", 2)
    }

    return UserMood(
        id = "${this.name}_${System.currentTimeMillis()}",
        name = name,
        emoji = emoji,
        intensity = intensity,
        isActive = true,
        createdBy = userId,
        visibility = com.calcvault.ui.mood.MoodVisibility.PRIVATE
    )
}

/**
 * Apply mood styling to characters based on current mood
 */
fun EmotionalState.getMoodColor(): Int {
    return when (this) {
        EmotionalState.HAPPY -> 0xFFFFD700.toInt()      // Gold
        EmotionalState.SAD -> 0xFF87CEEB.toInt()        // Sky blue
        EmotionalState.TIRED -> 0xFF708090.toInt()      // Slate gray
        EmotionalState.ROMANTIC -> 0xFFFF69B4.toInt()   // Hot pink
        EmotionalState.PLAYFUL -> 0xFF00FF00.toInt()    // Lime
        EmotionalState.FOCUSED -> 0xFF4169E1.toInt()    // Royal blue
        EmotionalState.RESTING -> 0xFF9370DB.toInt()    // Medium purple
        EmotionalState.NEUTRAL -> 0xFF808080.toInt()    // Gray
    }
}

fun EmotionalState.getScaleModifier(): Float {
    return when (this) {
        EmotionalState.HAPPY -> 1.1f      // Bigger
        EmotionalState.PLAYFUL -> 1.05f   // Slightly bigger
        EmotionalState.ROMANTIC -> 1.0f   // Normal
        EmotionalState.SAD -> 0.95f       // Smaller
        EmotionalState.TIRED -> 0.9f      // Much smaller
        else -> 1.0f                       // Normal
    }
}

fun EmotionalState.getOpacityModifier(): Float {
    return when (this) {
        EmotionalState.HAPPY -> 1.0f
        EmotionalState.PLAYFUL -> 1.0f
        EmotionalState.ROMANTIC -> 1.0f
        EmotionalState.FOCUSED -> 0.95f
        EmotionalState.RESTING -> 0.85f
        EmotionalState.TIRED -> 0.8f
        EmotionalState.SAD -> 0.75f
        EmotionalState.NEUTRAL -> 0.9f
    }
}
