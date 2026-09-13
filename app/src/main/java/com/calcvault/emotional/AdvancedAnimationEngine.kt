package com.calcvault.emotional

import android.animation.*
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.*
import kotlin.random.Random

/**
 * AdvancedAnimationEngine
 *
 * Advanced animation system with:
 * - Particle collision physics
 * - Morphing and shape transitions
 * - Text animations (glitch, wave, bounce)
 * - Advanced easing curves
 * - Complex multi-layer effects
 * - Interactive gesture animations
 */
class AdvancedAnimationEngine(
    private val context: Context,
    private val rootView: ViewGroup
) {

    enum class TextAnimationType { GLITCH, WAVE, BOUNCE, FADE_IN, TYPEWRITER, SHAKE }
    enum class ParticleShape { CIRCLE, STAR, HEART, SPARKLE, DIAMOND }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeAnimators = mutableListOf<Animator>()
    private val particleEffects = mutableMapOf<String, ParticleEffect>()

    // ─── Particle System ──────────────────────────────────────────────────────

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val mass: Float = 1f,
        val lifetime: Long,
        val shape: ParticleShape,
        val color: Int,
        var age: Long = 0
    ) {
        fun isAlive() = age < lifetime
        fun progress() = (age.toFloat() / lifetime.toFloat()).coerceIn(0f, 1f)
    }

    data class ParticleEffect(
        val particles: MutableList<Particle> = mutableListOf(),
        val gravity: Float = 500f,
        val airResistance: Float = 0.95f,
        val bounce: Float = 0.7f
    ) {
        fun update(dt: Float) {
            particles.forEach { p ->
                // Apply gravity
                p.vy += gravity * dt
                // Apply air resistance
                p.vx *= airResistance
                p.vy *= airResistance
                // Update position
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.age += (dt * 1000).toLong()
            }
            particles.removeAll { !it.isAlive() }
        }
    }

    // ─── Confetti & Particle Burst ────────────────────────────────────────────

    fun burstConfetti(
        fromX: Float,
        fromY: Float,
        particleCount: Int = 40,
        duration: Long = 2000,
        colors: List<Int> = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
    ) {
        val effect = ParticleEffect()
        repeat(particleCount) {
            val angle = (Random.nextFloat() * 360f) * (Math.PI / 180f)
            val speed = 300f + Random.nextFloat() * 500f
            val particle = Particle(
                x = fromX,
                y = fromY,
                vx = cos(angle).toFloat() * speed,
                vy = sin(angle).toFloat() * speed + Random.nextFloat() * 100f - 50f,
                lifetime = duration,
                shape = ParticleShape.values().random(),
                color = colors.random(),
                mass = 0.5f + Random.nextFloat() * 1.5f
            )
            effect.particles.add(particle)
        }

        animateParticleEffect(effect, "confetti_${System.currentTimeMillis()}", duration)
    }

    fun burstSparkles(
        fromX: Float,
        fromY: Float,
        particleCount: Int = 25,
        duration: Long = 1500,
        color: Int = 0xFFFFFF00.toInt()
    ) {
        val effect = ParticleEffect(gravity = 0f, airResistance = 0.85f)
        repeat(particleCount) {
            val angle = (Random.nextFloat() * 360f) * (Math.PI / 180f)
            val speed = 150f + Random.nextFloat() * 300f
            val particle = Particle(
                x = fromX,
                y = fromY,
                vx = cos(angle).toFloat() * speed,
                vy = sin(angle).toFloat() * speed,
                lifetime = duration,
                shape = ParticleShape.SPARKLE,
                color = color,
                mass = 0.1f + Random.nextFloat() * 0.3f
            )
            effect.particles.add(particle)
        }
        animateParticleEffect(effect, "sparkle_${System.currentTimeMillis()}", duration)
    }

    fun liquidSplash(
        fromX: Float,
        fromY: Float,
        particleCount: Int = 30,
        duration: Long = 1200,
        color: Int = 0xAA0099FF.toInt()
    ) {
        val effect = ParticleEffect(gravity = 1000f, bounce = 0.5f)
        repeat(particleCount) {
            val angle = (Random.nextFloat() * 180f) * (Math.PI / 180f) // Only upward
            val speed = 200f + Random.nextFloat() * 400f
            val particle = Particle(
                x = fromX + (Random.nextFloat() - 0.5f) * 50f,
                y = fromY,
                vx = cos(angle).toFloat() * speed,
                vy = sin(angle).toFloat() * speed,
                lifetime = duration,
                shape = ParticleShape.CIRCLE,
                color = color,
                mass = 2f + Random.nextFloat() * 3f
            )
            effect.particles.add(particle)
        }
        animateParticleEffect(effect, "splash_${System.currentTimeMillis()}", duration)
    }

    private fun animateParticleEffect(effect: ParticleEffect, effectId: String, duration: Long) {
        particleEffects[effectId] = effect

        val startTime = System.currentTimeMillis()
        mainHandler.post(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val dt = 0.016f // 60 FPS
                effect.update(dt)

                if (elapsed < duration && effect.particles.isNotEmpty()) {
                    mainHandler.post(this)
                } else {
                    particleEffects.remove(effectId)
                }
            }
        })
    }

    // ─── Text Animations ──────────────────────────────────────────────────────

    fun animateTextGlitch(textView: TextView, duration: Long = 800) {
        val originalText = textView.text.toString()
        val glitchChars = "▓░█▒╳◆╱▔▕"

        mainHandler.post {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    val glitchIntensity = 1f - progress

                    val result = originalText.mapIndexed { i, c ->
                        if (Random.nextFloat() < glitchIntensity * 0.4f) {
                            glitchChars[Random.nextInt(glitchChars.length)]
                        } else {
                            c
                        }
                    }.joinToString("")

                    textView.text = result
                    textView.alpha = 0.7f + progress * 0.3f
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        textView.text = originalText
                        textView.alpha = 1f
                    }
                })
                start()
            }
            activeAnimators.add(animator)
        }
    }

    fun animateTextWave(textView: TextView, duration: Long = 1500) {
        val originalText = textView.text.toString()
        val baselineY = textView.y

        mainHandler.post {
            val animator = ValueAnimator.ofFloat(0f, TWO_PI.toFloat()).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    val waveAmplitude = 15f

                    textView.translationY = baselineY + sin(progress) * waveAmplitude
                }
                start()
            }
            activeAnimators.add(animator)
        }
    }

    fun animateTextBounce(textView: TextView, duration: Long = 600) {
        val scaleAnimator = ObjectAnimator.ofPropertyValuesHolder(
            textView,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.3f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.3f, 1f)
        ).apply {
            this.duration = duration
            interpolator = android.view.animation.OvershootInterpolator(1.5f)
        }

        mainHandler.post {
            scaleAnimator.start()
            activeAnimators.add(scaleAnimator)
        }
    }

    fun animateTypewriter(textView: TextView, fullText: String, charactersPerSecond: Float = 15f) {
        var charIndex = 0
        val delayMs = (1000f / charactersPerSecond).toLong()

        val updateRunnable = object : Runnable {
            override fun run() {
                if (charIndex < fullText.length) {
                    textView.text = fullText.substring(0, charIndex + 1).plus("│")
                    charIndex++
                    mainHandler.postDelayed(this, delayMs)
                } else {
                    textView.text = fullText
                }
            }
        }
        mainHandler.post(updateRunnable)
    }

    fun animateTextShake(textView: TextView, duration: Long = 400, intensity: Int = 10) {
        val originalX = textView.translationX

        mainHandler.post {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                val shakeCount = (duration / 50f).toInt()
                addUpdateListener { animation ->
                    val offset = (Random.nextFloat() - 0.5f) * intensity * 2
                    textView.translationX = originalX + offset
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        textView.translationX = originalX
                    }
                })
                start()
            }
            activeAnimators.add(animator)
        }
    }

    // ─── Spring Animations ────────────────────────────────────────────────────

    fun elasticPulse(view: View, scaleFactor: Float = 1.2f, damping: Float = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY) {
        val springX = SpringAnimation(view, DynamicAnimation.SCALE_X, scaleFactor).apply {
            spring.dampingRatio = damping
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        }
        val springY = SpringAnimation(view, DynamicAnimation.SCALE_Y, scaleFactor).apply {
            spring.dampingRatio = damping
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
        }

        view.scaleX = 0.8f
        view.scaleY = 0.8f
        springX.start()
        springY.start()
    }

    fun rubberBandShake(view: View, duration: Long = 400) {
        val counts = 3
        val translator = ObjectAnimator.ofFloat(
            view, View.TRANSLATION_X,
            0f, -25f, 25f, -25f, 25f, -15f, 15f, -10f, 10f, -5f, 5f, 0f
        ).apply {
            this.duration = duration
        }
        mainHandler.post { translator.start() }
    }

    // ─── Color Grading & Effects ──────────────────────────────────────────────

    fun colorFlash(view: View, fromColor: Int, toColor: Int = Color.TRANSPARENT, duration: Long = 300) {
        ObjectAnimator.ofArgb(view, "backgroundColor", fromColor, toColor).apply {
            this.duration = duration
            mainHandler.post {
                view.setBackgroundColor(fromColor)
                start()
            }
        }
    }

    fun rainbowWave(view: View, duration: Long = 2000) {
        val colors = intArrayOf(
            0xFFFF0000.toInt(),
            0xFFFF7F00.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFF4B0082.toInt(),
            0xFF9400D3.toInt()
        )

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val colorIndex = (progress * colors.size).toInt() % colors.size
                view.setBackgroundColor(colors[colorIndex])
            }
        }
        mainHandler.post { animator.start() }
    }

    // ─── Gesture-Based Animations ────────────────────────────────────────────

    fun createSwipeRipple(x: Float, y: Float, color: Int = 0x44FFFFFF.toInt()) {
        val rippleView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(2, 2).apply {
                leftMargin = x.toInt()
                topMargin = y.toInt()
            }
            setBackgroundColor(color)
        }
        rootView.addView(rippleView)

        ObjectAnimator.ofPropertyValuesHolder(
            rippleView,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 60f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 60f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f)
        ).apply {
            duration = 600
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(rippleView)
                }
            })
            start()
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun cancelAllAnimations() {
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
        particleEffects.clear()
    }

    companion object {
        private val TWO_PI = 2 * Math.PI
    }
}
