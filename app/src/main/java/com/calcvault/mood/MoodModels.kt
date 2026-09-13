package com.calcvault.ui.mood

enum class MoodVisibility {
    PUBLIC,
    PRIVATE
}

data class UserMood(
    val id: String,
    val name: String,
    val emoji: String,
    val intensity: Int = 3, // 1-5
    val isActive: Boolean = false,
    val createdBy: String,
    val visibility: MoodVisibility = MoodVisibility.PUBLIC
)
