package com.calcvault.emotional.themes

import android.animation.*
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.*
import kotlin.random.Random

/**
 * NightSkyTheme
 *
 * Live animated night sky background.
 */
class NightSkyTheme(private val context: Context) {

    private var skyView: NightSkyView? = null
    private var attached = false

    fun attach(root: FrameLayout) {
        if (attached) return
        skyView = NightSkyView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(skyView, 0)
        skyView?.startAnimation()
        attached = true
    }

    fun detach() {
        skyView?.stopAnimation()
        (skyView?.parent as? ViewGroup)?.removeView(skyView)
        skyView = null
        attached = false
    }

    fun isAttached() = attached
}

class NightSkyView(context: Context) : View(context) {

    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val baseAlpha: Float,
        var alpha: Float,
        val twinkleSpeed: Float,
        var twinklePhase: Float
    )

    private data class ShootingStar(
        var x: Float,
        var y: Float,
        val angle: Float,
        val speed: Float,
        var progress: Float = 0f,
        val length: Float,
        val alpha: Float = 1f
    ) {
        val dx = cos(angle) * speed
        val dy = sin(angle) * speed
    }

    private val stars = mutableListOf<Star>()
    private val shootingStars = mutableListOf<ShootingStar>()
    private val handler = Handler(Looper.getMainLooper())
    private var frameCount = 0L
    private var running = false

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val shootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private val bgPaint = Paint()
    private var bgShader: RadialGradient? = null
    private var nebShader: RadialGradient? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        generateStars(w, h)
        buildShaders(w, h)
    }

    private fun generateStars(w: Int, h: Int) {
        stars.clear()
        val count = (w * h / 4000).coerceIn(80, 200)
        repeat(count) {
            val r = Random.nextFloat() * 2.5f + 0.5f
            val alpha = Random.nextFloat() * 0.6f + 0.4f
            stars.add(
                Star(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    radius = r,
                    baseAlpha = alpha,
                    alpha = alpha,
                    twinkleSpeed = Random.nextFloat() * 0.04f + 0.01f,
                    twinklePhase = Random.nextFloat() * (2 * PI).toFloat()
                )
            )
        }
    }

    private fun buildShaders(w: Int, h: Int) {
        bgShader = RadialGradient(
            w * 0.5f,
            h * 0.3f,
            h * 0.8f,
            intArrayOf(
                Color.parseColor("#0D0D2B"),
                Color.parseColor("#050510")
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        nebShader = RadialGradient(
            w * 0.3f,
            h * 0.4f,
            h * 0.5f,
            intArrayOf(
                Color.parseColor("#1A0A2E"),
                Color.parseColor("#0A0520"),
                Color.parseColor("#00000000")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        bgShader?.let {
            bgPaint.shader = it
            canvas.drawRect(0f, 0f, w, h, bgPaint)
        }

        nebShader?.let {
            bgPaint.shader = it
            bgPaint.alpha = 80
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            bgPaint.alpha = 255
        }

        val t = frameCount * 0.016f
        for (star in stars) {
            val flicker = (sin((t * star.twinkleSpeed * 10f + star.twinklePhase).toDouble()) * 0.4 + 0.6).toFloat()
            val a = (star.baseAlpha * flicker * 255).toInt().coerceIn(40, 255)
            starPaint.alpha = a
            if (star.radius > 1.8f) {
                starPaint.maskFilter = BlurMaskFilter(star.radius * 2, BlurMaskFilter.Blur.NORMAL)
                starPaint.alpha = a / 3
                canvas.drawCircle(star.x, star.y, star.radius * 2.5f, starPaint)
                starPaint.maskFilter = null
            }
            starPaint.alpha = a
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
        }

        val iterator = shootingStars.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.x += s.dx
            s.y += s.dy
            s.progress += 0.02f
            if (s.progress >= 1f) { iterator.remove(); continue }

            val alpha = ((1f - s.progress) * 220).toInt()
            val tailX = s.x - cos(s.angle) * s.length * (1f - s.progress)
            val tailY = s.y - sin(s.angle) * s.length * (1f - s.progress)

            val gradient = LinearGradient(
                tailX,
                tailY,
                s.x,
                s.y,
                intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            shootPaint.shader = gradient
            shootPaint.strokeWidth = 2.5f
            canvas.drawLine(tailX, tailY, s.x, s.y, shootPaint)
        }
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            frameCount++
            if (frameCount % 180 == 0L && Random.nextFloat() < 0.7f) {
                spawnShootingStar()
            }
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    private fun spawnShootingStar() {
        if (width == 0 || height == 0) return
        val angle = Random.nextFloat() * 0.6f + 0.2f
        shootingStars.add(
            ShootingStar(
                x = Random.nextFloat() * width * 0.7f,
                y = Random.nextFloat() * height * 0.4f,
                angle = angle,
                speed = Random.nextFloat() * 12f + 8f,
                length = Random.nextFloat() * 150f + 80f
            )
        )
    }

    fun startAnimation() {
        running = true
        handler.post(frameRunnable)
    }

    fun stopAnimation() {
        running = false
        handler.removeCallbacks(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
