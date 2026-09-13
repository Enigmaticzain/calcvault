package com.calcvault.house

import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * TouchReactionEngine.kt — Gesture detection for poke, pet, tickle, slap
 */

enum class TouchGesture {
    POKE,       // Quick single tap on character
    PET,        // Slow stroke along head/back
    TICKLE,     // Rapid multi-touch on belly
    SLAP,       // Fast horizontal swipe
    RUB_BELLY,  // Circular motion on torso
    PICK_UP,    // Long press then drag up
    FEED,       // Drag item to mouth area
    NONE
}

data class TouchRegion(val name: String, val yMin: Float, val yMax: Float) {
    companion object {
        val HEAD = TouchRegion("head", -80f, -30f)
        val EARS = TouchRegion("ears", -95f, -70f)
        val BELLY = TouchRegion("belly", -10f, 40f)
        val PAWS = TouchRegion("paws", 40f, 70f)
        val TAIL = TouchRegion("tail", 10f, 50f) // x offset check needed
    }
}

data class GestureResult(
    val gesture: TouchGesture,
    val region: TouchRegion?,
    val character: CharacterIdentity,
    val intensity: Float = 1f
)

class TouchReactionEngine(private val needsEngine: NeedsEngine?) {

    private val touchHistory = mutableListOf<PointF>()
    private var touchStartTime = 0L
    private var touchChar: CharacterIdentity? = null
    private var lastGestureTime = 0L

    companion object {
        const val POKE_MAX_DURATION = 200L
        const val PET_MIN_DURATION = 400L
        const val PET_MIN_DISTANCE = 40f
        const val PET_MAX_SPEED = 8f
        const val SLAP_MIN_SPEED = 25f
        const val TICKLE_MIN_TOUCHES = 5
        const val TICKLE_MAX_AREA = 60f
        const val GESTURE_COOLDOWN = 300L
    }

    fun onTouchDown(point: PointF, character: CharacterIdentity) {
        touchHistory.clear()
        touchHistory.add(point)
        touchStartTime = System.currentTimeMillis()
        touchChar = character
    }

    fun onTouchMove(point: PointF) {
        touchHistory.add(point)
        if (touchHistory.size > 30) touchHistory.removeAt(0)
    }

    fun onTouchUp(point: PointF, charPosition: PointF): GestureResult? {
        val char = touchChar ?: return null
        val now = System.currentTimeMillis()
        if (now - lastGestureTime < GESTURE_COOLDOWN) return null

        touchHistory.add(point)
        val duration = now - touchStartTime
        val totalDist = calculateTotalDistance()
        val avgSpeed = if (duration > 0) totalDist / duration * 1000f else 0f
        val localPoint = PointF(point.x - charPosition.x, point.y - charPosition.y)
        val region = detectRegion(localPoint)

        val gesture = classifyGesture(duration, totalDist, avgSpeed, region)
        lastGestureTime = now

        if (gesture != TouchGesture.NONE) {
            applyNeedsEffect(char, gesture)
            return GestureResult(gesture, region, char, (avgSpeed / 20f).coerceIn(0.5f, 2f))
        }
        return null
    }

    private fun classifyGesture(duration: Long, dist: Float, speed: Float, region: TouchRegion?): TouchGesture {
        return when {
            duration < POKE_MAX_DURATION && dist < 15f -> TouchGesture.POKE
            speed > SLAP_MIN_SPEED && isHorizontalSwipe() -> TouchGesture.SLAP
            duration > PET_MIN_DURATION && dist > PET_MIN_DISTANCE && speed < PET_MAX_SPEED
                && (region == TouchRegion.HEAD || region == TouchRegion.EARS) -> TouchGesture.PET
            isCircularMotion() && region == TouchRegion.BELLY -> TouchGesture.RUB_BELLY
            touchHistory.size >= TICKLE_MIN_TOUCHES && getSpreadArea() < TICKLE_MAX_AREA
                && region == TouchRegion.BELLY -> TouchGesture.TICKLE
            else -> TouchGesture.NONE
        }
    }

    private fun detectRegion(local: PointF): TouchRegion? = when {
        local.y in TouchRegion.EARS.yMin..TouchRegion.EARS.yMax -> TouchRegion.EARS
        local.y in TouchRegion.HEAD.yMin..TouchRegion.HEAD.yMax -> TouchRegion.HEAD
        local.y in TouchRegion.BELLY.yMin..TouchRegion.BELLY.yMax -> TouchRegion.BELLY
        local.y in TouchRegion.PAWS.yMin..TouchRegion.PAWS.yMax -> TouchRegion.PAWS
        else -> null
    }

    private fun calculateTotalDistance(): Float {
        if (touchHistory.size < 2) return 0f
        var total = 0f
        for (i in 1 until touchHistory.size) {
            val dx = touchHistory[i].x - touchHistory[i - 1].x
            val dy = touchHistory[i].y - touchHistory[i - 1].y
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    private fun isHorizontalSwipe(): Boolean {
        if (touchHistory.size < 3) return false
        val first = touchHistory.first(); val last = touchHistory.last()
        val dx = abs(last.x - first.x); val dy = abs(last.y - first.y)
        return dx > dy * 2.5f && dx > 50f
    }

    private fun isCircularMotion(): Boolean {
        if (touchHistory.size < 8) return false
        var crossings = 0
        val cx = touchHistory.map { it.x }.average().toFloat()
        for (i in 1 until touchHistory.size) {
            if ((touchHistory[i].x > cx) != (touchHistory[i - 1].x > cx)) crossings++
        }
        return crossings >= 3
    }

    private fun getSpreadArea(): Float {
        if (touchHistory.isEmpty()) return 0f
        val xs = touchHistory.map { it.x }; val ys = touchHistory.map { it.y }
        return ((xs.max() - xs.min()) + (ys.max() - ys.min())) / 2f
    }

    private fun applyNeedsEffect(character: CharacterIdentity, gesture: TouchGesture) {
        val engine = needsEngine ?: return
        when (gesture) {
            TouchGesture.PET -> engine.pet(character)
            TouchGesture.POKE -> engine.poke(character)
            TouchGesture.TICKLE -> engine.tickle(character)
            TouchGesture.RUB_BELLY -> engine.tickle(character)
            TouchGesture.SLAP -> engine.poke(character)
            else -> {}
        }
    }

    fun getReactionAction(gesture: TouchGesture): CharacterAction = when (gesture) {
        TouchGesture.POKE -> CharacterAction.IDLE // flinch handled in renderer
        TouchGesture.PET -> CharacterAction.LISTENING // purring
        TouchGesture.TICKLE -> CharacterAction.PLAYING // laughing
        TouchGesture.RUB_BELLY -> CharacterAction.PLAYING
        TouchGesture.SLAP -> CharacterAction.IDLE // dizzy handled in renderer
        TouchGesture.PICK_UP -> CharacterAction.REACHING
        TouchGesture.FEED -> CharacterAction.EATING
        TouchGesture.NONE -> CharacterAction.IDLE
    }

    fun getReactionParticle(gesture: TouchGesture): ParticleType? = when (gesture) {
        TouchGesture.PET -> ParticleType.HEART
        TouchGesture.TICKLE -> ParticleType.STAR
        TouchGesture.RUB_BELLY -> ParticleType.SPARKLE
        TouchGesture.SLAP -> ParticleType.ANGER_PUFF
        TouchGesture.POKE -> ParticleType.SWEAT_DROP
        else -> null
    }
}
