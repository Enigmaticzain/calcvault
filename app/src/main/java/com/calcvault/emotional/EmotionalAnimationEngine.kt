package com.calcvault.emotional

import android.animation.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.random.Random

/**
 * EmotionalAnimationEngine
 *
 * Transforms the UI from a communication tool into an emotional space.
 */
class EmotionalAnimationEngine(
    private val context: Context,
    private val rootView: ViewGroup
) {
    enum class AnimationMode { FULL, MEDIUM, LOW }
    enum class MoodType { HAPPY, SAD, LOVE, ANGRY, SLEEPY, BUSY, FOCUS, NEUTRAL }

    var animationMode = AnimationMode.FULL
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeAnimators = mutableListOf<Animator>()
    private val pulseAnimators = mutableMapOf<View, Animator>()

    // ─── New Trigger System ────────────────────────────────────────────────────
    private val triggerSystem = TriggerSystem()
    companion object {
        private const val TAG = "EmotionalAnimationEngine"
    }

    // ─── Custom Emoji Animation Support ────────────────────────────────────────

    /**
     * Trigger custom emoji animation with directional physics.
     */
    fun triggerCustomEmojiAnimation(
        emoji: String,
        intensity: Int = 15,
        direction: String = "UP",
        speed: String = "MEDIUM"
    ) {
        if (animationMode == AnimationMode.LOW) return

        val duration = when (speed.uppercase()) {
            "SLOW" -> 4000L
            "MEDIUM" -> 2000L
            "FAST" -> 1000L
            else -> 2000L
        }

        repeat(intensity) { i ->
            mainHandler.postDelayed({
                val w = if (rootView.width > 0) rootView.width else 1080
                val h = if (rootView.height > 0) rootView.height else 1920
                val startX = Random.nextFloat() * w.toFloat()

                val (startY, goUp) = when (direction.uppercase()) {
                    "UP" -> Pair(h.toFloat(), true)
                    "DOWN" -> Pair(0f, false)
                    "RANDOM" -> if (Random.nextBoolean()) Pair(h.toFloat(), true) else Pair(0f, false)
                    else -> Pair(h.toFloat(), true)
                }

                spawnFloatingEmoji(emoji, startX, startY, goUp = goUp, duration = duration)
            }, i * 100L)
        }
    }

    // ─── Keyword Trigger Detection ─────────────────────────────────────────────

    /**
     * Initialize built-in triggers and attach to the animation engine.
     */
    fun initializeBuiltInTriggers() {
        // Built-in triggers that integrate with the animation engine
        val builtInConfigs = listOf(
            TriggerSystem.TriggerConfig(
                keywords = listOf("i love you", "love you", "ily", "❤️", "💕", "💖"),
                triggerType = TriggerSystem.TriggerType.EMOJI_ANIMATION,
                emoji = "❤️",
                intensity = 20,
                direction = "UP",
                speed = "MEDIUM"
            ),
            TriggerSystem.TriggerConfig(
                keywords = listOf("miss you", "missing you", "i miss you"),
                triggerType = TriggerSystem.TriggerType.EMOJI_ANIMATION,
                emoji = "💕",
                intensity = 10,
                direction = "UP",
                speed = "SLOW"
            ),
            TriggerSystem.TriggerConfig(
                keywords = listOf("good night", "goodnight", "gn 💤", "🌙"),
                triggerType = TriggerSystem.TriggerType.STARS,
                intensity = 15,
                speed = "SLOW"
            ),
            TriggerSystem.TriggerConfig(
                keywords = listOf("good morning", "gm ☀️", "rise and shine", "🌅"),
                triggerType = TriggerSystem.TriggerType.GLOW,
                intensity = 12,
                speed = "MEDIUM"
            ),
            TriggerSystem.TriggerConfig(
                keywords = listOf("😔", "sad", "i'm sad", "crying", "😢", "😭"),
                triggerType = TriggerSystem.TriggerType.RAIN,
                intensity = 20,
                speed = "MEDIUM"
            ),
            TriggerSystem.TriggerConfig(
                keywords = listOf("😘", "kiss", "mwah", "💋"),
                triggerType = TriggerSystem.TriggerType.EMOJI_ANIMATION,
                emoji = "💋",
                intensity = 8,
                direction = "DOWN",
                speed = "FAST"
            )
        )

        for (config in builtInConfigs) {
            triggerSystem.addTrigger(config)
        }

        Log.d(TAG, "Initialized ${builtInConfigs.size} built-in triggers")
    }

    /**
     * Check if a message matches any trigger and execute the animation.
     */
    fun checkMessageTriggers(messageText: String) {
        if (animationMode == AnimationMode.LOW) return

        val trigger = triggerSystem.findMatchingTrigger(messageText) ?: return

        mainHandler.post {
            try {
                executeAnimationForTrigger(trigger)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing trigger animation", e)
            }
        }
    }

    /**
     * Execute the appropriate animation based on trigger type.
     */
    private fun executeAnimationForTrigger(trigger: TriggerSystem.TriggerConfig) {
        when (trigger.triggerType) {
            TriggerSystem.TriggerType.EMOJI_ANIMATION -> {
                trigger.emoji?.let { emoji ->
                    triggerCustomEmojiAnimation(
                        emoji = emoji,
                        intensity = trigger.intensity,
                        direction = trigger.direction,
                        speed = trigger.speed
                    )
                }
            }
            TriggerSystem.TriggerType.PARTICLE_BURST -> {
                triggerFloatingHearts(count = trigger.intensity)
            }
            TriggerSystem.TriggerType.GLOW -> {
                triggerSunriseGlow()
            }
            TriggerSystem.TriggerType.RAIN -> {
                triggerRainEffect(intensity = trigger.intensity / 20f)
            }
            TriggerSystem.TriggerType.STARS -> {
                triggerStarfallAnimation(count = trigger.intensity)
            }
        }
    }

    /**
     * Load custom triggers from JSON string with error handling.
     * JSON format:
     * [
     *   { "keywords": ["hello", "hi"], "type": "emoji_animation", "emoji": "😊", "intensity": 15 },
     *   ...
     * ]
     */
    fun loadCustomTriggers(json: String): Boolean {
        val result = triggerSystem.loadFromJson(json)

        if (result.errors.isNotEmpty()) {
            Log.w(TAG, "Errors loading triggers: ${result.errors.joinToString(", ")}")
        }

        Log.d(TAG, "Load result: ${result.getSummary()}")

        return result.isSuccess()
    }

    /**
     * Legacy support - add a basic trigger by type.
     */
    fun addCustomTrigger(keyword: String, animationType: String): Boolean {
        val triggerType = when (animationType.lowercase()) {
            "hearts" -> TriggerSystem.TriggerType.PARTICLE_BURST
            "stars" -> TriggerSystem.TriggerType.STARS
            "rain" -> TriggerSystem.TriggerType.RAIN
            "glow" -> TriggerSystem.TriggerType.GLOW
            else -> TriggerSystem.TriggerType.PARTICLE_BURST
        }

        val config = TriggerSystem.TriggerConfig(
            keywords = listOf(keyword.lowercase().trim()),
            triggerType = triggerType,
            intensity = 15
        )

        return triggerSystem.addTrigger(config)
    }

    /**
     * Advanced custom trigger with emoji, intensity, direction, and speed.
     */
    fun addCustomEmojiTrigger(
        keyword: String,
        emoji: String,
        intensity: Int = 15,
        direction: String = "UP",
        speed: String = "MEDIUM"
    ): Boolean {
        val config = TriggerSystem.TriggerConfig(
            keywords = listOf(keyword.lowercase().trim()),
            triggerType = TriggerSystem.TriggerType.EMOJI_ANIMATION,
            emoji = emoji,
            intensity = intensity,
            direction = direction,
            speed = speed
        )

        return triggerSystem.addTrigger(config)
    }

    /**
     * Get all active triggers (for debugging).
     */
    fun getAllTriggers(): List<TriggerSystem.TriggerConfig> {
        return triggerSystem.getAllTriggers()
    }

    /**
     * Get trigger statistics.
     */
    fun getTriggerStats(): TriggerSystem.TriggerStats {
        return triggerSystem.getStats()
    }

    /**
     * Enable or disable a trigger.
     */
    fun setTriggerEnabled(keyword: String, enabled: Boolean): Boolean {
        return triggerSystem.setTriggerEnabled(keyword, enabled)
    }

    /**
     * Remove a trigger.
     */
    fun removeTrigger(keyword: String): Boolean {
        return triggerSystem.removeTrigger(keyword)
    }

    /**
     * Clear all custom triggers.
     */
    fun clearCustomTriggers() {
        triggerSystem.clear()
        initializeBuiltInTriggers()
        Log.d(TAG, "Cleared all triggers and re-initialized built-in triggers")
    }

    /**
     * Enable debug mode for trigger system.
     */
    fun enableTriggerDebug(enabled: Boolean) {
        TriggerSystem.enableDebug(enabled)
    }

    // ─── Spring / Interaction Animations ───────────────────────────────────────

    fun springBubble(view: View) {
        if (animationMode == AnimationMode.LOW) return

        val springX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        }
        val springY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        }

        view.scaleX = 0.9f
        view.scaleY = 0.9f

        springX.start()
        springY.start()
    }

    // ─── Call Animations ──────────────────────────────────────────────────────

    fun startCallPulse(view: View) {
        if (animationMode == AnimationMode.LOW) return
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.2f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.2f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.7f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        val set = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1000
        }
        pulseAnimators[view]?.cancel()
        pulseAnimators[view] = set
        set.start()
    }

    fun stopCallPulse(view: View) {
        pulseAnimators[view]?.cancel()
        pulseAnimators.remove(view)
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
    }

    fun animateCallEnd(view: View, onEnd: () -> Unit) {
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
            duration = 500
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    // ─── Mood-Based UI Reactions ───────────────────────────────────────────────

    fun applyMoodTheme(mood: MoodType) {
        mainHandler.post {
            when (mood) {
                MoodType.HAPPY -> {
                    setRootTint(0x10FFFF00)
                    if (animationMode == AnimationMode.FULL) startIdleHeartPulse()
                }
                MoodType.SAD -> {
                    setRootTint(0x15000080)
                    if (animationMode != AnimationMode.LOW) triggerRainEffect(intensity = 0.3f)
                }
                MoodType.LOVE -> {
                    setRootTint(0x15FF0055)
                    if (animationMode == AnimationMode.FULL) {
                        startIdleFloatingHearts()
                    }
                }
                MoodType.ANGRY -> {
                    setRootTint(0x18FF2200)
                }
                MoodType.SLEEPY -> {
                    setRootTint(0x12000020)
                }
                MoodType.BUSY, MoodType.FOCUS -> {
                    setRootTint(0x00000000)
                    cancelAllAnimations()
                }
                MoodType.NEUTRAL -> {
                    setRootTint(0x00000000)
                    cancelAllAnimations()
                }
            }
        }
    }

    // ─── Message Bubble Animations ─────────────────────────────────────────────

    fun animateSentBubble(bubbleView: View) {
        if (animationMode == AnimationMode.LOW) return
        bubbleView.alpha = 0f
        bubbleView.translationY = 30f
        val fade = ObjectAnimator.ofFloat(bubbleView, View.ALPHA, 0f, 1f).setDuration(200)
        val slide = ObjectAnimator.ofFloat(bubbleView, View.TRANSLATION_Y, 30f, 0f).setDuration(200)
        AnimatorSet().apply {
            playTogether(fade, slide)
            start()
        }
    }

    fun animateReceivedBubble(bubbleView: View) {
        if (animationMode == AnimationMode.LOW) return
        bubbleView.alpha = 0f
        bubbleView.scaleX = 0.85f
        bubbleView.scaleY = 0.85f
        val fade = ObjectAnimator.ofFloat(bubbleView, View.ALPHA, 0f, 1f).setDuration(250)
        val scaleX = ObjectAnimator.ofFloat(bubbleView, View.SCALE_X, 0.85f, 1f).setDuration(250)
        val scaleY = ObjectAnimator.ofFloat(bubbleView, View.SCALE_Y, 0.85f, 1f).setDuration(250)
        AnimatorSet().apply {
            playTogether(fade, scaleX, scaleY)
            interpolator = android.view.animation.OvershootInterpolator(1.5f)
            start()
        }
    }

    fun triggerHeartBurst(anchorView: View) {
        if (animationMode == AnimationMode.LOW) return
        val location = IntArray(2)
        anchorView.getLocationInWindow(location)
        val cx = location[0] + anchorView.width / 2f
        val cy = location[1] + anchorView.height / 2f

        repeat(6) { i ->
            mainHandler.postDelayed({
                spawnFloatingEmoji("❤️", cx + Random.nextFloat() * 40 - 20, cy, size = 18f)
            }, i * 60L)
        }
    }

    // ─── Floating Hearts ──────────────────────────────────────────────────────

    fun triggerFloatingHearts(
        count: Int = 15,
        color: Int = 0xFFFF3366.toInt()
    ) {
        if (animationMode == AnimationMode.LOW) return
        val actualCount = if (animationMode == AnimationMode.MEDIUM) count / 2 else count
        repeat(actualCount) { i ->
            mainHandler.postDelayed({
                val w = if (rootView.width > 0) rootView.width else 1080
                val h = if (rootView.height > 0) rootView.height else 1920
                val x = Random.nextFloat() * w.toFloat()
                val y = h.toFloat()
                spawnFloatingEmoji("❤️", x, y)
            }, i * 80L)
        }
    }

    private var idleHeartsJob: Runnable? = null

    private fun startIdleFloatingHearts() {
        stopIdleAnimations()
        idleHeartsJob = object : Runnable {
            override fun run() {
                if (animationMode != AnimationMode.LOW) {
                    val w = if (rootView.width > 0) rootView.width else 1080
                    val h = if (rootView.height > 0) rootView.height else 1920
                    val x = Random.nextFloat() * w.toFloat()
                    spawnFloatingEmoji("❤️", x, h.toFloat(), size = 14f)
                }
                mainHandler.postDelayed(this, 2_000L)
            }
        }
        mainHandler.post(idleHeartsJob!!)
    }

    private fun startIdleHeartPulse() {
        val anim = ObjectAnimator.ofFloat(rootView, View.ALPHA, 1f, 0.93f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        activeAnimators.add(anim)
        anim.start()
    }

    // ─── Stars / Night Effect ─────────────────────────────────────────────────

    fun triggerStarfallAnimation(count: Int = 12) {
        if (animationMode == AnimationMode.LOW) return
        val stars = listOf("⭐", "✨", "🌙", "💫")
        repeat(count) { i ->
            mainHandler.postDelayed({
                val w = if (rootView.width > 0) rootView.width else 1080
                val x = Random.nextFloat() * w.toFloat()
                spawnFloatingEmoji(stars.random(), x, 0f, goUp = false, size = 16f)
            }, i * 120L)
        }
    }

    // ─── Sunrise Glow ────────────────────────────────────────────────────────

    fun triggerSunriseGlow() {
        val overlay = View(context).apply {
            setBackgroundColor(0x40FFAA00)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
        }
        rootView.addView(overlay)

        ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 0.4f, 0f).apply {
            duration = 2_500
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(overlay)
                }
            })
            start()
        }
    }

    // ─── Rain Effect ─────────────────────────────────────────────────────────

    fun triggerRainEffect(intensity: Float = 0.6f) {
        if (animationMode == AnimationMode.LOW) return
        val drops = (8 * intensity).toInt().coerceAtLeast(3)
        repeat(drops) { i ->
            mainHandler.postDelayed({
                val w = if (rootView.width > 0) rootView.width else 1080
                val x = Random.nextFloat() * w.toFloat()
                spawnFloatingEmoji("💧", x, 0f, goUp = false, size = 12f, duration = 1800)
            }, i * 200L)
        }
    }

    // ─── Kiss Animation ───────────────────────────────────────────────────────

    private fun triggerKissAnimation() {
        val w = if (rootView.width > 0) rootView.width else 1080
        val h = if (rootView.height > 0) rootView.height else 1920
        val cx = w / 2f
        val cy = h / 2f
        spawnFloatingEmoji("😘", cx, cy + 100, size = 40f, duration = 1200)
        mainHandler.postDelayed({
            spawnFloatingEmoji("💋", cx + 30, cy - 50, size = 28f)
        }, 300)
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private fun spawnFloatingEmoji(
        emoji: String,
        startX: Float,
        startY: Float,
        goUp: Boolean = true,
        size: Float = 20f,
        duration: Long = 2000
    ) {
        val tv = TextView(context).apply {
            text = emoji
            textSize = size
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            x = startX
            y = startY
            alpha = 1f
        }

        rootView.addView(tv)

        val h = if (rootView.height > 0) rootView.height else 1920
        val targetY = if (goUp) {
            startY - h * 0.6f
        } else {
            startY + h * 0.4f
        }
        val driftX = startX + (Random.nextFloat() - 0.5f) * 120f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tv, View.Y, startY, targetY).setDuration(duration),
                ObjectAnimator.ofFloat(tv, View.X, startX, driftX).setDuration(duration),
                ObjectAnimator.ofFloat(tv, View.ALPHA, 1f, 0f).apply {
                    this.duration = duration
                    startDelay = duration / 3
                }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(tv)
                }
            })
            start()
        }
    }

    private fun setRootTint(color: Int) {
        if (rootView.background == null || rootView.background is android.graphics.drawable.ColorDrawable) {
            rootView.setBackgroundColor(color)
        }
    }

    private fun stopIdleAnimations() {
        idleHeartsJob?.let { mainHandler.removeCallbacks(it) }
        idleHeartsJob = null
    }

    fun cancelAllAnimations() {
        stopIdleAnimations()
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
        pulseAnimators.values.forEach { it.cancel() }
        pulseAnimators.clear()
    }
}
