package com.calcvault.emotional

import android.animation.*
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.sqrt

/**
 * GestureAnimationController
 *
 * Interactive animations triggered by user gestures:
 * - Tap effects
 * - Swipe trails
 * - Long-press reactions
 * - Double-tap magic
 * - Drag & throw physics
 */
class GestureAnimationController(
    private val context: Context,
    private val rootView: ViewGroup,
    private val advancedEngine: AdvancedAnimationEngine? = null
) : View.OnTouchListener {

    interface GestureCallback {
        fun onTap(x: Float, y: Float) {}
        fun onDoubleTap(x: Float, y: Float) {}
        fun onLongPress(x: Float, y: Float) {}
        fun onSwipe(startX: Float, startY: Float, endX: Float, endY: Float, velocity: Float) {}
        fun onMultiTouch(points: List<PointF>) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var gestureDetector: GestureDetector? = null
    private var callback: GestureCallback? = null
    private var trailEnabled = true
    private val trails = mutableListOf<TrailParticle>()

    data class TrailParticle(
        val x: Float,
        val y: Float,
        val radius: Float = 8f,
        val color: Int,
        var age: Float = 0f,
        val lifetime: Float = 0.3f // seconds
    )

    // ─── Initialization ────────────────────────────────────────────────────────

    fun initialize(callback: GestureCallback? = null) {
        this.callback = callback

        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                callback?.onTap(e.x, e.y)
                onTap(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                callback?.onDoubleTap(e.x, e.y)
                onDoubleTap(e.x, e.y)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                callback?.onLongPress(e.x, e.y)
                onLongPress(e.x, e.y)
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val distance = sqrt((e2.x - e1.x) * (e2.x - e1.x) + (e2.y - e1.y) * (e2.y - e1.y))
                val velocity = sqrt(velocityX * velocityX + velocityY * velocityY)

                callback?.onSwipe(e1.x, e1.y, e2.x, e2.y, velocity)
                onSwipe(e1.x, e1.y, e2.x, e2.y, velocity)
                return true
            }
        }

        gestureDetector = GestureDetector(context, listener)
    }

    // ─── Gesture Handlers ─────────────────────────────────────────────────────

    private fun onTap(x: Float, y: Float) {
        if (advancedEngine == null) return

        // Create expanding ripple
        advancedEngine.createSwipeRipple(x, y, 0x44AAAAAA.toInt())

        // Small sparkle burst
        advancedEngine.burstSparkles(x, y, particleCount = 8, duration = 500)
    }

    private fun onDoubleTap(x: Float, y: Float) {
        if (advancedEngine == null) return

        // Larger effect for double tap
        repeat(3) {
            mainHandler.postDelayed({
                advancedEngine.createSwipeRipple(x, y, 0x66FFFFFF.toInt())
            }, (it * 100).toLong())
        }

        // Heart burst for love effect
        advancedEngine.burstConfetti(
            x,
            y,
            particleCount = 15,
            colors = listOf(0xFFFF1493.toInt(), 0xFFFFB6C1.toInt())
        )
    }

    private fun onLongPress(x: Float, y: Float) {
        if (advancedEngine == null) return

        // Strong pulse and glow
        advancedEngine.liquidSplash(
            x,
            y,
            particleCount = 25,
            color = 0x8800FF00.toInt()
        )

        // Create expanding circle
        val expandView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(4, 4).apply {
                leftMargin = x.toInt() - 2
                topMargin = y.toInt() - 2
            }
            setBackgroundColor(0xFF00FF00.toInt())
        }
        rootView.addView(expandView)

        val expandAnimator = ObjectAnimator.ofPropertyValuesHolder(
            expandView,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 25f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 25f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f)
        ).apply {
            duration = 1200
            interpolator = android.view.animation.DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(expandView)
                }
            })
        }
        mainHandler.post { expandAnimator.start() }
    }

    private fun onSwipe(startX: Float, startY: Float, endX: Float, endY: Float, velocity: Float) {
        if (advancedEngine == null) return

        // Create swipe trail
        val velocityNorm = velocity.coerceAtMost(2000f) / 2000f
        val trailParticles = 20
        val stepX = (endX - startX) / trailParticles
        val stepY = (endY - startY) / trailParticles

        repeat(trailParticles) { i ->
            val x = startX + stepX * i
            val y = startY + stepY * i
            advancedEngine.createSwipeRipple(x, y, 0x33FF00FF.toInt())
        }

        // Velocity-based reaction
        if (velocity > 1000f) {
            // Fast swipe - big burst
            advancedEngine.burstConfetti(
                endX,
                endY,
                particleCount = (velocity / 1000f * 40).toInt()
            )
        } else {
            // Gentle swipe - sparkles
            advancedEngine.burstSparkles(endX, endY, particleCount = 12)
        }
    }

    // ─── Touch Trail Effect ────────────────────────────────────────────────────

    fun enableTouchTrail(enabled: Boolean) {
        trailEnabled = enabled
    }

    private fun addTrailParticle(x: Float, y: Float, color: Int = 0xFF00CCFF.toInt()) {
        if (!trailEnabled) return
        trails.add(TrailParticle(x, y, color = color))
    }

    fun updateAndDrawTrails() {
        if (trails.isEmpty()) return

        val dt = 0.016f // ~60 FPS
        trails.forEach { it.age += dt }
        trails.removeAll { it.age >= it.lifetime }
    }

    // ─── Overloads for Touch Event Handling ────────────────────────────────────

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        // Track trail for touch
        if (trailEnabled && (event.action == MotionEvent.ACTION_MOVE)) {
            addTrailParticle(event.x, event.y)
        }

        return gestureDetector?.onTouchEvent(event) ?: false
    }

    // ─── Advanced Interaction Effects ─────────────────────────────────────────

    fun createDragMagnet(view: View, magnetX: Float, magnetY: Float, strength: Float = 1f) {
        val springX = androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_X, magnetX).apply {
            spring.dampingRatio = androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            spring.stiffness = androidx.dynamicanimation.animation.SpringForce.STIFFNESS_MEDIUM
        }
        val springY = androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, magnetY).apply {
            spring.dampingRatio = androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            spring.stiffness = androidx.dynamicanimation.animation.SpringForce.STIFFNESS_MEDIUM
        }

        mainHandler.post {
            springX.start()
            springY.start()
        }
    }

    fun createParticleAttraction(sourceX: Float, sourceY: Float, targetX: Float, targetY: Float) {
        if (advancedEngine == null) return

        // Visual trail from source to target
        val steps = 30
        val stepX = (targetX - sourceX) / steps
        val stepY = (targetY - sourceY) / steps

        repeat(steps) { i ->
            val x = sourceX + stepX * i
            val y = sourceY + stepY * i
            mainHandler.postDelayed({
                advancedEngine.burstSparkles(x, y, particleCount = 3, duration = 200)
            }, (i * 20).toLong())
        }
    }

    fun createTouchFirework(
        x: Float,
        y: Float,
        colorPalette: List<Int> = listOf(
            0xFFFF1493.toInt(),
            0xFF00CCFF.toInt(),
            0xFFFFD700.toInt(),
            0xFF00FF00.toInt()
        )
    ) {
        if (advancedEngine == null) return

        repeat(4) { i ->
            mainHandler.postDelayed({
                advancedEngine.burstConfetti(
                    x,
                    y,
                    particleCount = 15,
                    colors = listOf(colorPalette[i % colorPalette.size])
                )
            }, (i * 150).toLong())
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun reset() {
        gestureDetector = null
        callback = null
        trails.clear()
    }
}
