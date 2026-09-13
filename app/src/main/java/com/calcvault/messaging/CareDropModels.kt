package com.calcvault.messaging

enum class DropType {
    GLOW_DROP,
    HEART_DROP,
    CALM_DROP,
    BOOST_DROP
}

data class CareDrop(
    val id: String,
    val message: String,
    val type: DropType,
    val sender: String,
    val timestamp: Long
)

object CareDropPresets {
    val defaults: List<Pair<String, DropType>> = listOf(
        "Best of luck \uD83C\uDF40" to DropType.BOOST_DROP,
        "You've got this \uD83D\uDCAA" to DropType.BOOST_DROP,
        "I'm proud of you ❤️" to DropType.HEART_DROP,
        "Miss you \uD83E\uDEC2" to DropType.CALM_DROP,
        "Take care \uD83C\uDF19" to DropType.CALM_DROP
    )
}
