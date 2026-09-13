package com.calcvault.ui.ambient

import kotlin.random.Random

data class WindState(
    var intensity: Float = 0f,
    var direction: Float = 1f, // 1 for right, -1 for left
    var targetIntensity: Float = 0.5f,
    var lastUpdate: Long = System.currentTimeMillis()
)

open class Particle(
    var x: Float,
    var y: Float,
    var size: Float,
    var opacity: Float,
    val seed: Double = Random.nextDouble(1000.0)
)

class Leaf(x: Float, y: Float, size: Float, opacity: Float) : Particle(x, y, size, opacity) {
    var rotation: Float = Random.nextFloat() * 360f
    var rotationSpeed: Float = (Random.nextFloat() - 0.5f) * 1.2f
    var driftPhase: Double = Random.nextDouble(6.283185307179586)
    var fallSpeed: Float = 60f + Random.nextFloat() * 60f
}

class Snow(x: Float, y: Float, size: Float, opacity: Float) : Particle(x, y, size, opacity) {
    var fallSpeed: Float = 30f + Random.nextFloat() * 40f
    var driftFrequency: Float = 0.15f + Random.nextFloat() * 0.2f
}

class Rain(x: Float, y: Float, size: Float, opacity: Float) : Particle(x, y, size, opacity) {
    var fallSpeed: Float = 600f + Random.nextFloat() * 300f
    var length: Float = 15f + Random.nextFloat() * 15f
    var angleVariance: Float = (Random.nextFloat() - 0.5f) * 0.05f
}

class Bird(x: Float, y: Float, size: Float) : Particle(x, y, size, 1f) {
    var speed: Float = 80f + Random.nextFloat() * 60f
    var wingPhase: Float = Random.nextFloat() * 6.2831855f
    var targetY: Float = y
}

data class Splash(val x: Float, val y: Float, var life: Float)

class Ember(x: Float, y: Float, size: Float, opacity: Float) : Particle(x, y, size, opacity) {
    var riseSpeed: Float = 40f + Random.nextFloat() * 50f
    var driftSpeed: Float = (Random.nextFloat() - 0.5f) * 30f
    var glowPhase: Float = Random.nextFloat() * 6.28f
}

class MistDrop(x: Float, y: Float, size: Float, opacity: Float) : Particle(x, y, size, opacity) {
    var floatSpeed: Float = 10f + Random.nextFloat() * 20f
    var swayFrequency: Double = 0.5 + Random.nextDouble()
}

class PulseRing(var x: Float, var y: Float, var currentRadius: Float, var maxRadius: Float, var opacity: Float) {
    var expandSpeed: Float = 20f + Random.nextFloat() * 30f
}


data class Star(val x: Float, val y: Float, val size: Float, val seed: Double)

data class ShootingStar(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float = 1.0f
)

data class Cloud(
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var speed: Float,
    val seed: Double
)

data class Firefly(
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    val seed: Double = Random.nextDouble(1000.0)
)

data class Tree(
    val x: Float,
    val y: Float,
    val scale: Float,
    val type: Int,
    var sway: Float = 0f
)

data class AuroraCurtain(
    val seed: Double,
    var alpha: Float = 0f
)

data class Puddle(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    var ripplePhase: Float = 0f
)

/**
 * Character state for emotional companion theme
 */
data class CompanionState(
    var bodyRotation: Float = 0f,
    var headOffset: Float = 0f,
    var breathScale: Float = 1f,
    var interactionFactor: Float = 0f,
    var stateType: CompanionType = CompanionType.SITTING
)

enum class CompanionType {
    SITTING, INTERACTING, PLAYING, RESTING
}

enum class TimeOfDay {
    MORNING, DAY, EVENING, NIGHT
}
