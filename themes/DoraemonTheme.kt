package com.calcvault.emotional.themes

import android.animation.*
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import kotlin.math.*
import kotlin.random.Random

/**
 * DoraemonTheme
 *
 * Live animated Doraemon and Shizuka theme.
 *
 * Characters:
 *  - Doraemon: blue round cat-robot, drawn programmatically
 *  - Shizuka: small girl with ponytail, drawn programmatically
 *
 * Natural randomness:
 *  - Both characters wander freely around the screen
 *  - They sometimes move toward each other (playing together)
 *  - They sometimes move apart (exploring)
 *  - Random idle animations: bobbing, waving, spinning
 *  - Occasional interaction: both face each other and "talk"
 *  - Soft sky blue + white cloud background
 *
 * Usage:
 *   val theme = DoraemonTheme(context)
 *   theme.attach(rootFrameLayout)
 *   theme.detach()
 */
class DoraemonTheme(private val context: Context) {

    private var themeView : DoraemonView? = null
    private var attached  = false

    fun attach(root: FrameLayout) {
        if (attached) return
        themeView = DoraemonView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(themeView, 0)
        themeView?.startAnimation()
        attached = true
    }

    fun detach() {
        themeView?.stopAnimation()
        (themeView?.parent as? ViewGroup)?.removeView(themeView)
        themeView = null
        attached  = false
    }
}

// ── Character state ────────────────────────────────────────────────────────

private data class Character(
    var x        : Float,
    var y        : Float,
    var targetX  : Float,
    var targetY  : Float,
    var scale    : Float   = 1f,
    var bobAngle : Float   = 0f,    // up/down bob
    var waveAngle: Float   = 0f,    // arm wave
    var facing   : Int     = 1,     // 1=right, -1=left
    var state    : CharState = CharState.WANDERING,
    var stateTimer: Int    = 0
)

private enum class CharState { WANDERING, IDLE, TALKING, PLAYING }

// ── View ───────────────────────────────────────────────────────────────────

class DoraemonView(context: Context) : View(context) {

    private val handler  = Handler(Looper.getMainLooper())
    private var running  = false
    private var frame    = 0

    // Characters
    private val doraemon = Character(0f, 0f, 0f, 0f)
    private val shizuka  = Character(0f, 0f, 0f, 0f)

    // Cloud positions (background decorations)
    private data class Cloud(var x: Float, val y: Float, val w: Float, val speed: Float)
    private val clouds   = mutableListOf<Cloud>()

    // Paints
    private val paint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.parseColor("#333333")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        // Place characters in lower portion of screen
        doraemon.x = w * 0.25f; doraemon.y = h * 0.72f
        shizuka.x  = w * 0.70f; shizuka.y  = h * 0.74f
        doraemon.targetX = doraemon.x; doraemon.targetY = doraemon.y
        shizuka.targetX  = shizuka.x;  shizuka.targetY  = shizuka.y

        // Generate clouds
        clouds.clear()
        repeat(5) {
            clouds.add(Cloud(
                x     = Random.nextFloat() * w,
                y     = h * (0.05f + Random.nextFloat() * 0.2f),
                w     = 120f + Random.nextFloat() * 160f,
                speed = 0.3f + Random.nextFloat() * 0.4f
            ))
        }
    }

    // ── Update ─────────────────────────────────────────────────────────────

    private fun update() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        frame++

        // Move clouds
        for (cloud in clouds) {
            cloud.x += cloud.speed
            if (cloud.x > w + 200f) cloud.x = -200f
        }

        // Update character states every ~5 seconds
        if (frame % 300 == 0) {
            assignNewBehavior(doraemon, w, h)
            assignNewBehavior(shizuka,  w, h)

            // 30% chance they play together
            if (Random.nextFloat() < 0.3f) {
                doraemon.state = CharState.PLAYING
                shizuka.state  = CharState.PLAYING
                // Move toward each other
                val midX = (doraemon.x + shizuka.x) / 2f
                doraemon.targetX = midX - 80f
                shizuka.targetX  = midX + 80f
            }

            // 20% chance they "talk"
            if (Random.nextFloat() < 0.2f) {
                doraemon.state = CharState.TALKING
                shizuka.state  = CharState.TALKING
            }
        }

        // Move toward targets smoothly
        moveTowardTarget(doraemon, 1.5f)
        moveTowardTarget(shizuka,  1.2f)

        // Bob and wave animation
        doraemon.bobAngle  += 0.06f
        shizuka.bobAngle   += 0.05f
        doraemon.waveAngle += if (doraemon.state == CharState.TALKING || doraemon.state == CharState.PLAYING) 0.12f else 0.02f
        shizuka.waveAngle  += if (shizuka.state  == CharState.TALKING || shizuka.state  == CharState.PLAYING) 0.10f else 0.02f

        // Scale pulse when playing
        if (doraemon.state == CharState.PLAYING) {
            doraemon.scale = 1f + sin(frame * 0.1f).toFloat() * 0.06f
            shizuka.scale  = 1f + sin(frame * 0.1f + 1f).toFloat() * 0.06f
        } else {
            doraemon.scale = 1f; shizuka.scale = 1f
        }
    }

    private fun assignNewBehavior(c: Character, w: Float, h: Float) {
        c.state = CharState.values().random()
        when (c.state) {
            CharState.WANDERING -> {
                c.targetX = w * 0.1f + Random.nextFloat() * w * 0.8f
                c.targetY = h * 0.65f + Random.nextFloat() * h * 0.15f
            }
            CharState.IDLE -> { /* stay put */ }
            else -> {}
        }
    }

    private fun moveTowardTarget(c: Character, speed: Float) {
        val dx = c.targetX - c.x
        val dy = c.targetY - c.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 2f) {
            c.x += (dx / dist) * speed
            c.y += (dy / dist) * speed
            c.facing = if (dx > 0) 1 else -1
        }
    }

    // ── Draw ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return

        drawBackground(canvas, w, h)
        drawClouds(canvas)
        drawGround(canvas, w, h)
        drawDoraemon(canvas, doraemon)
        drawShizuka(canvas, shizuka)
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        // Sky gradient
        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.75f,
            intArrayOf(
                Color.parseColor("#87CEEB"),
                Color.parseColor("#B0E2FF")
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h * 0.75f, paint)
        paint.shader = null
    }

    private fun drawClouds(canvas: Canvas) {
        for (cloud in clouds) {
            // Use enhanced cloud shape from ShapeUtils
            ShapeUtils.drawCloudGradient(
                canvas, paint,
                cloud.x, cloud.y,
                cloud.w * 0.5f,
                Color.WHITE,
                shadowAlpha = 30
            )
        }
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float) {
        // Green grass
        paint.shader = LinearGradient(
            0f, h * 0.72f, 0f, h,
            intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#2E7D32")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.72f, w, h, paint)
        paint.shader = null
    }

    // ── Draw Doraemon ──────────────────────────────────────────────────────

    private fun drawDoraemon(canvas: Canvas, c: Character) {
        val bob = sin(c.bobAngle.toDouble()).toFloat() * 4f
        val cx  = c.x
        val cy  = c.y + bob
        val s   = 55f * c.scale   // body size

        canvas.save()
        if (c.facing == -1) {
            canvas.scale(-1f, 1f, cx, cy)
        }

        // Body (blue circle)
        paint.color = Color.parseColor("#00A0E9")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, s, paint)

        // White face area
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy - s * 0.1f, s * 0.72f, paint)

        // Eyes
        paint.color = Color.WHITE
        canvas.drawCircle(cx - s * 0.22f, cy - s * 0.35f, s * 0.18f, paint)
        canvas.drawCircle(cx + s * 0.22f, cy - s * 0.35f, s * 0.18f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(cx - s * 0.18f, cy - s * 0.32f, s * 0.10f, paint)
        canvas.drawCircle(cx + s * 0.26f, cy - s * 0.32f, s * 0.10f, paint)
        // Eye shine
        paint.color = Color.WHITE
        canvas.drawCircle(cx - s * 0.15f, cy - s * 0.36f, s * 0.04f, paint)
        canvas.drawCircle(cx + s * 0.29f, cy - s * 0.36f, s * 0.04f, paint)

        // Nose (red)
        paint.color = Color.parseColor("#FF3333")
        canvas.drawCircle(cx, cy - s * 0.12f, s * 0.09f, paint)

        // Mouth smile
        paint.color = Color.parseColor("#333333")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val smilePath = Path().apply {
            moveTo(cx - s * 0.35f, cy + s * 0.05f)
            cubicTo(cx - s * 0.15f, cy + s * 0.25f, cx + s * 0.15f, cy + s * 0.25f, cx + s * 0.35f, cy + s * 0.05f)
        }
        canvas.drawPath(smilePath, paint)
        paint.style = Paint.Style.FILL

        // Whiskers
        paint.color = Color.parseColor("#333333")
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        for (i in -1..1 step 1) {
            canvas.drawLine(cx - s * 0.05f, cy + i * s * 0.08f, cx - s * 0.6f, cy + i * s * 0.12f, paint)
            canvas.drawLine(cx + s * 0.05f, cy + i * s * 0.08f, cx + s * 0.6f, cy + i * s * 0.12f, paint)
        }
        paint.style = Paint.Style.FILL

        // Bell collar
        paint.color = Color.parseColor("#FF0000")
        val collarRect = RectF(cx - s * 0.55f, cy + s * 0.55f, cx + s * 0.55f, cy + s * 0.70f)
        canvas.drawRoundRect(collarRect, 8f, 8f, paint)
        paint.color = Color.parseColor("#FFD700")
        canvas.drawCircle(cx, cy + s * 0.70f, s * 0.10f, paint)

        // Arms (with wave if talking/playing)
        paint.color = Color.parseColor("#00A0E9")
        val waveOffset = if (c.state == CharState.TALKING || c.state == CharState.PLAYING)
            sin(c.waveAngle.toDouble()).toFloat() * 18f else 0f
        // Left arm
        canvas.drawRoundRect(RectF(cx - s * 0.95f, cy + s * 0.1f - waveOffset, cx - s * 0.5f, cy + s * 0.4f - waveOffset * 0.5f), 12f, 12f, paint)
        // Right arm
        canvas.drawRoundRect(RectF(cx + s * 0.5f, cy + s * 0.1f + waveOffset, cx + s * 0.95f, cy + s * 0.4f + waveOffset * 0.5f), 12f, 12f, paint)

        // Legs
        canvas.drawRoundRect(RectF(cx - s * 0.45f, cy + s * 0.8f, cx - s * 0.1f, cy + s * 1.2f), 10f, 10f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.1f, cy + s * 0.8f, cx + s * 0.45f, cy + s * 1.2f), 10f, 10f, paint)
        // Feet (white)
        paint.color = Color.WHITE
        canvas.drawOval(cx - s * 0.5f, cy + s * 1.15f, cx - s * 0.05f, cy + s * 1.35f, paint)
        canvas.drawOval(cx + s * 0.05f, cy + s * 1.15f, cx + s * 0.5f, cy + s * 1.35f, paint)

        // Pocket
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(cx - s * 0.30f, cy + s * 0.35f, cx + s * 0.30f, cy + s * 0.65f), 12f, 12f, paint)
        paint.color = Color.parseColor("#00A0E9")
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(cx - s * 0.30f, cy + s * 0.35f, cx + s * 0.30f, cy + s * 0.65f), 12f, 12f, paint)
        paint.style = Paint.Style.FILL

        canvas.restore()

        // Speech bubble if talking
        if (c.state == CharState.TALKING) {
            drawSpeechBubble(canvas, cx + s, cy - s * 1.3f, "♪")
        }
    }

    // ── Draw Shizuka ───────────────────────────────────────────────────────

    private fun drawShizuka(canvas: Canvas, c: Character) {
        val bob = sin(c.bobAngle.toDouble() + 1.0).toFloat() * 3f
        val cx  = c.x
        val cy  = c.y + bob
        val s   = 40f * c.scale

        canvas.save()
        if (c.facing == -1) {
            canvas.scale(-1f, 1f, cx, cy)
        }

        // Body (pink dress)
        paint.color = Color.parseColor("#FFB6C1")
        paint.style = Paint.Style.FILL
        // Dress (trapezoid shape)
        val dressPath = Path().apply {
            moveTo(cx - s * 0.35f, cy + s * 0.1f)
            lineTo(cx + s * 0.35f, cy + s * 0.1f)
            lineTo(cx + s * 0.55f, cy + s * 1.1f)
            lineTo(cx - s * 0.55f, cy + s * 1.1f)
            close()
        }
        canvas.drawPath(dressPath, paint)

        // Skin - head
        paint.color = Color.parseColor("#FFDAB9")
        canvas.drawCircle(cx, cy - s * 0.6f, s * 0.45f, paint)

        // Hair (dark, with ponytail)
        paint.color = Color.parseColor("#2C1810")
        // Main hair
        canvas.drawArc(RectF(cx - s * 0.45f, cy - s * 1.1f, cx + s * 0.45f, cy - s * 0.3f), 180f, 180f, true, paint)
        // Side hair
        canvas.drawRoundRect(RectF(cx - s * 0.48f, cy - s * 0.9f, cx - s * 0.35f, cy - s * 0.3f), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.35f, cy - s * 0.9f, cx + s * 0.48f, cy - s * 0.3f), 6f, 6f, paint)
        // Ponytail
        canvas.drawRoundRect(RectF(cx + s * 0.30f, cy - s * 1.05f, cx + s * 0.6f, cy - s * 0.55f), 8f, 8f, paint)
        // Hair ribbon (red)
        paint.color = Color.parseColor("#FF4444")
        canvas.drawCircle(cx + s * 0.45f, cy - s * 0.95f, s * 0.10f, paint)

        // Eyes
        paint.color = Color.parseColor("#2C1810")
        canvas.drawOval(cx - s * 0.18f, cy - s * 0.72f, cx - s * 0.06f, cy - s * 0.58f, paint)
        canvas.drawOval(cx + s * 0.06f, cy - s * 0.72f, cx + s * 0.18f, cy - s * 0.58f, paint)
        // Eye shine
        paint.color = Color.WHITE
        canvas.drawCircle(cx - s * 0.10f, cy - s * 0.68f, s * 0.04f, paint)
        canvas.drawCircle(cx + s * 0.14f, cy - s * 0.68f, s * 0.04f, paint)

        // Smile
        paint.color = Color.parseColor("#CC4444")
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        val smilePath = Path().apply {
            moveTo(cx - s * 0.12f, cy - s * 0.46f)
            cubicTo(cx - s * 0.04f, cy - s * 0.36f, cx + s * 0.04f, cy - s * 0.36f, cx + s * 0.12f, cy - s * 0.46f)
        }
        canvas.drawPath(smilePath, paint)
        paint.style = Paint.Style.FILL

        // Arms
        paint.color = Color.parseColor("#FFDAB9")
        val waveOffset = if (c.state == CharState.TALKING || c.state == CharState.PLAYING)
            sin(c.waveAngle.toDouble()).toFloat() * 14f else 0f
        canvas.drawRoundRect(RectF(cx - s * 0.7f, cy - s * 0.1f - waveOffset, cx - s * 0.32f, cy + s * 0.2f), 8f, 8f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.32f, cy - s * 0.1f + waveOffset, cx + s * 0.7f, cy + s * 0.2f), 8f, 8f, paint)

        // Legs
        paint.color = Color.parseColor("#FFDAB9")
        canvas.drawRoundRect(RectF(cx - s * 0.32f, cy + s * 1.05f, cx - s * 0.08f, cy + s * 1.5f), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.08f, cy + s * 1.05f, cx + s * 0.32f, cy + s * 1.5f), 6f, 6f, paint)
        // Shoes
        paint.color = Color.parseColor("#8B4513")
        canvas.drawOval(cx - s * 0.38f, cy + s * 1.42f, cx - s * 0.04f, cy + s * 1.62f, paint)
        canvas.drawOval(cx + s * 0.04f, cy + s * 1.42f, cx + s * 0.38f, cy + s * 1.62f, paint)

        canvas.restore()

        if (c.state == CharState.TALKING) {
            drawSpeechBubble(canvas, cx - s * 1.5f, cy - s * 1.8f, "♥")
        }
    }

    private fun drawSpeechBubble(canvas: Canvas, x: Float, y: Float, text: String) {
        paint.color = Color.WHITE; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(x - 30f, y - 25f, x + 30f, y + 10f), 12f, 12f, paint)
        paint.color = Color.parseColor("#DDDDDD"); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(x - 30f, y - 25f, x + 30f, y + 10f), 12f, 12f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawText(text, x, y, textPaint)
    }

    // ── Animation loop ─────────────────────────────────────────────────────

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            update()
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    fun startAnimation() { running = true; handler.post(frameRunnable) }
    fun stopAnimation()  { running = false; handler.removeCallbacks(frameRunnable) }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow(); stopAnimation()
    }
}
