package com.calcvault.emotional

import android.animation.*
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.sin
import kotlin.random.Random

/**
 * MoodReactionSystem
 *
 * Advanced mood-based visual reactions with:
 * - Color psychology
 * - Particle effects per mood
 * - Doppler-like sound elevation (visual)
 * - Breathing animations
 * - Glow/aura effects
 * - Dynamic light shifts
 */
class MoodReactionSystem(
    private val context: Context,
    private val rootView: ViewGroup,
    private val advancedEngine: AdvancedAnimationEngine? = null
) {

    enum class Mood(
        val visualColor: Int,
        val glowColor: Int,
        val energyLevel: Float // 0-1
    ) {
        HAPPY(0xFFFFC107.toInt(), 0xFFFFD700.toInt(), 0.9f),
        SAD(0xFF3F51B5.toInt(), 0xFF1A237E.toInt(), 0.2f),
        LOVE(0xFFE91E63.toInt(), 0xFFC2185B.toInt(), 0.8f),
        ANGRY(0xFFD32F2F.toInt(), 0xFF8B0000.toInt(), 1.0f),
        SLEEPY(0xFF546E7A.toInt(), 0xFF263238.toInt(), 0.1f),
        CALM(0xFF4CAF50.toInt(), 0xFF2E7D32.toInt(), 0.4f),
        EXCITED(0xFFFF5722.toInt(), 0xFFE64A19.toInt(), 1.0f),
        NERVOUS(0xFF9C27B0.toInt(), 0xFF6A1B9A.toInt(), 0.6f),
        PEACEFUL(0xFF00BCD4.toInt(), 0xFF00838F.toInt(), 0.3f)
    }

    data class MoodEffect(
        val particleCount: Int,
        val particleColor: Int,
        val duration: Long,
        val intensity: Float,
        val pulseRate: Long // milliseconds for pulse cycle
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentMood: Mood? = null
    private val moodEffectAnimators = mutableListOf<Animator>()
    private val glowLayers = mutableMapOf<String, View>()
    private var breathingAnimator: ValueAnimator? = null

    // ─── Mood Configuration ────────────────────────────────────────────────────

    private val moodEffectConfig = mapOf(
        Mood.HAPPY to MoodEffect(
            particleCount = 30,
            particleColor = 0xFFFFD700.toInt(),
            duration = 2000,
            intensity = 0.8f,
            pulseRate = 500
        ),
        Mood.SAD to MoodEffect(
            particleCount = 15,
            particleColor = 0xFF1A237E.toInt(),
            duration = 3000,
            intensity = 0.3f,
            pulseRate = 1000
        ),
        Mood.LOVE to MoodEffect(
            particleCount = 40,
            particleColor = 0xFFFF1493.toInt(),
            duration = 2500,
            intensity = 0.9f,
            pulseRate = 400
        ),
        Mood.ANGRY to MoodEffect(
            particleCount = 50,
            particleColor = 0xFF8B0000.toInt(),
            duration = 1500,
            intensity = 1.0f,
            pulseRate = 200
        ),
        Mood.SLEEPY to MoodEffect(
            particleCount = 5,
            particleColor = 0xFF546E7A.toInt(),
            duration = 4000,
            intensity = 0.1f,
            pulseRate = 2000
        ),
        Mood.CALM to MoodEffect(
            particleCount = 20,
            particleColor = 0xFF2E7D32.toInt(),
            duration = 3500,
            intensity = 0.5f,
            pulseRate = 1200
        ),
        Mood.EXCITED to MoodEffect(
            particleCount = 60,
            particleColor = 0xFFFF5722.toInt(),
            duration = 1200,
            intensity = 1.0f,
            pulseRate = 150
        ),
        Mood.NERVOUS to MoodEffect(
            particleCount = 25,
            particleColor = 0xFF6A1B9A.toInt(),
            duration = 2000,
            intensity = 0.7f,
            pulseRate = 600
        ),
        Mood.PEACEFUL to MoodEffect(
            particleCount = 15,
            particleColor = 0xFF00838F.toInt(),
            duration = 3000,
            intensity = 0.4f,
            pulseRate = 1500
        )
    )

    // ─── Apply Mood with Full Visual Effect ────────────────────────────────────

    fun setMood(mood: Mood, includeParticles: Boolean = true) {
        currentMood = mood

        // Color transition
        applyMoodColor(mood)

        // Glow effect
        applyMoodGlow(mood)

        // Breathing animation
        startBreathingAnimation(mood)

        // Particle burst
        if (includeParticles && advancedEngine != null) {
            val config = moodEffectConfig[mood] ?: return
            burstMoodParticles(mood, config)
        }
    }

    fun transitionMood(fromMood: Mood, toMood: Mood, duration: Long = 1500) {
        val colorAnimator = ValueAnimator.ofArgb(fromMood.visualColor, toMood.visualColor).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                rootView.setBackgroundColor(color)
            }
        }

        mainHandler.post {
            colorAnimator.start()
            moodEffectAnimators.add(colorAnimator)
        }

        mainHandler.postDelayed({ setMood(toMood) }, duration / 2)
    }

    // ─── Color & Glow Effects ─────────────────────────────────────────────────

    private fun applyMoodColor(mood: Mood) {
        val fadeAnimator = ObjectAnimator.ofArgb(
            rootView,
            "backgroundColor",
            (rootView.background?.let { Color.BLACK } ?: Color.BLACK),
            mood.visualColor
        ).apply {
            duration = 600
        }

        mainHandler.post {
            rootView.setBackgroundColor(mood.visualColor)
        }
    }

    private fun applyMoodGlow(mood: Mood) {
        // Remove existing glow
        glowLayers.forEach { (_, view) -> rootView.removeView(view) }
        glowLayers.clear()

        // Create new glow layer with pulsing animation
        val glowView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(mood.glowColor)
            alpha = 0.15f
        }
        rootView.addView(glowView)
        glowLayers["mood_glow"] = glowView

        // Pulse the glow
        val pulseAnimator = ObjectAnimator.ofFloat(glowView, View.ALPHA, 0.15f, 0.35f, 0.15f).apply {
            duration = moodEffectConfig[mood]?.pulseRate ?: 1000
            repeatCount = ValueAnimator.INFINITE
        }
        mainHandler.post { pulseAnimator.start() }
        moodEffectAnimators.add(pulseAnimator)
    }

    // ─── Breathing Animation ──────────────────────────────────────────────────

    private fun startBreathingAnimation(mood: Mood) {
        breathingAnimator?.cancel()

        val breathingDuration = (4000 / mood.energyLevel).toLong().coerceIn(2000, 6000)

        val scale = { t: Float ->
            0.95f + 0.05f * sin(t * Math.PI.toFloat()) // Subtle breathing effect
        }

        breathingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = breathingDuration
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val scaleValue = scale(progress)
                // Very subtle scale for visual breathing
                rootView.scaleX = scaleValue
                rootView.scaleY = scaleValue
            }
        }

        mainHandler.post {
            breathingAnimator?.start()
            moodEffectAnimators.add(breathingAnimator!!)
        }
    }

    // ─── Particle Effects Per Mood ────────────────────────────────────────────

    private fun burstMoodParticles(mood: Mood, config: MoodEffect) {
        if (advancedEngine == null) return

        when (mood) {
            Mood.HAPPY -> {
                // Golden confetti
                val centerX = rootView.width / 2f
                val centerY = rootView.height / 2f
                advancedEngine.burstConfetti(
                    centerX,
                    centerY,
                    particleCount = config.particleCount,
                    colors = listOf(0xFFFFD700.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt())
                )
            }
            Mood.LOVE -> {
                // Red/pink hearts sparkle
                repeat(3) {
                    advancedEngine.burstSparkles(
                        rootView.width / 2f + Random.nextFloat() * 100f - 50f,
                        rootView.height / 2f + Random.nextFloat() * 100f - 50f,
                        particleCount = config.particleCount / 3,
                        color = 0xFFFF1493.toInt()
                    )
                }
            }
            Mood.EXCITED -> {
                // Multi-color explosion
                val colors = listOf(0xFFFF5722.toInt(), 0xFFFF9800.toInt(), 0xFFFFc107.toInt())
                advancedEngine.burstConfetti(
                    rootView.width / 2f,
                    rootView.height / 3f,
                    particleCount = config.particleCount,
                    colors = colors
                )
            }
            Mood.ANGRY -> {
                // Red energy burst
                advancedEngine.burstConfetti(
                    rootView.width / 2f,
                    rootView.height / 2f,
                    particleCount = config.particleCount,
                    colors = listOf(0xFF8B0000.toInt(), 0xFFFF0000.toInt())
                )
            }
            Mood.SAD -> {
                // Slow blue droplets
                advancedEngine.liquidSplash(
                    rootView.width / 2f,
                    rootView.height / 3f,
                    particleCount = config.particleCount,
                    color = 0x66000080.toInt()
                )
            }
            Mood.CALM, Mood.PEACEFUL -> {
                // Gentle sparkles
                advancedEngine.burstSparkles(
                    rootView.width / 2f,
                    rootView.height / 2f,
                    particleCount = config.particleCount / 2,
                    color = mood.glowColor
                )
            }
            Mood.SLEEPY -> {
                // Few slow falling particles
                advancedEngine.liquidSplash(
                    rootView.width / 2f,
                    0f,
                    particleCount = config.particleCount,
                    color = 0x33546E7A.toInt()
                )
            }
            Mood.NERVOUS -> {
                // Scattered jittery sparks
                repeat(config.particleCount / 5) {
                    advancedEngine.burstSparkles(
                        Random.nextFloat() * rootView.width,
                        Random.nextFloat() * rootView.height,
                        particleCount = 3,
                        color = 0xFF6A1B9A.toInt()
                    )
                }
            }
        }
    }

    // ─── Dynamic Light Shifts ────────────────────────────────────────────────

    fun createLightFlash(color: Int = 0xFFFFFFFF.toInt(), duration: Long = 300) {
        val flashView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(color)
            alpha = 0.8f
        }
        rootView.addView(flashView)

        val fadeOut = ObjectAnimator.ofFloat(flashView, View.ALPHA, 0.8f, 0f).apply {
            this.duration = duration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(flashView)
                }
            })
        }
        mainHandler.post { fadeOut.start() }
    }

    fun createMoodAura(mood: Mood, duration: Long = 2000) {
        val auraView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(mood.glowColor)
            alpha = 0f
        }
        rootView.addView(auraView)

        val pulseAnim = ObjectAnimator.ofFloat(auraView, View.ALPHA, 0f, 0.2f, 0f).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(auraView)
                }
            })
        }
        mainHandler.post { pulseAnim.start() }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun clearAllEffects() {
        moodEffectAnimators.forEach { it.cancel() }
        moodEffectAnimators.clear()
        breathingAnimator?.cancel()
        breathingAnimator = null
        glowLayers.forEach { (_, view) -> runCatching { rootView.removeView(view) } }
        glowLayers.clear()
        currentMood = null
    }

    fun getCurrentMood() = currentMood
}
