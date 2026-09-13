package com.calcvault.emotional.themes

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import com.calcvault.utils.SessionManager

/**
 * DoraemonTheme
 *
 * Upgraded live Doraemon + Shizuka scene with richer park atmosphere,
 * stronger character expressions, and cleaner motion choreography.
 */
import com.calcvault.emotional.themes.DoraemonMessageBridge
class DoraemonTheme(private val context: Context) {

    private var themeView: DoraemonView? = null
    private var attached = false

    fun attach(root: FrameLayout) {
        if (attached) return
        themeView = DoraemonView(context).apply {
            enable3D = SessionManager.isDoraemon3DEnabled
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(themeView, 0)
        themeView?.let { DoraemonMessageBridge.setDoraemonView(it) }
        themeView?.startAnimation()
        attached = true
    }

    fun detach() {
        themeView?.stopAnimation()
        (themeView?.parent as? ViewGroup)?.removeView(themeView)
        DoraemonMessageBridge.clearView()
        themeView = null
        attached = false
    }
}

private enum class CharState { WANDERING, IDLE, TALKING, GADGET_PLAY, SKIP }

private data class Character(
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    var scale: Float = 1f,
    var bobPhase: Float = 0f,
    var wavePhase: Float = 0f,
    var facing: Int = 1,
    var state: CharState = CharState.WANDERING,
    var stateTimer: Int = 0
)

internal data class ChatMessage(
    val sender: String,  // "doraemon" or "shizuka"
    val text: String,
    val displayUntilFrame: Long
)


private data class Cloud(
    var x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val speed: Float
)

private data class Kite(
    var x: Float,
    var y: Float,
    val speed: Float,
    val color: Int,
    val sway: Float
)

private data class Spark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    var size: Float
)

class DoraemonView(context: Context) : View(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var frame = 0L

    private val doraemon = Character(0f, 0f, 0f, 0f)
    private val shizuka = Character(0f, 0f, 0f, 0f, facing = -1)

    private val clouds = mutableListOf<Cloud>()
    private val kites = mutableListOf<Kite>()
    private val sparks = mutableListOf<Spark>()

    private var dayPhase = 0.15f
    var enable3D = true
    private val chatMessages = mutableListOf<ChatMessage>()
    private var lastDoraemonMessage: ChatMessage? = null
    private var lastShizukaMessage: ChatMessage? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        doraemon.x = w * 0.26f
        doraemon.y = h * 0.73f
        doraemon.targetX = doraemon.x
        doraemon.targetY = doraemon.y

        shizuka.x = w * 0.70f
        shizuka.y = h * 0.75f
        shizuka.targetX = shizuka.x
        shizuka.targetY = shizuka.y

        buildClouds(w.toFloat(), h.toFloat())
        buildKites(w.toFloat(), h.toFloat())
    }

    private fun buildClouds(w: Float, h: Float) {
        clouds.clear()
        repeat(7) { i ->
            clouds += Cloud(
                x = Random.nextFloat() * w,
                y = h * (0.08f + i * 0.04f) + Random.nextFloat() * h * 0.06f,
                width = 120f + Random.nextFloat() * 170f,
                height = 38f + Random.nextFloat() * 34f,
                speed = 0.16f + Random.nextFloat() * 0.27f
            )
        }
    }

    private fun buildKites(w: Float, h: Float) {
        kites.clear()
        val palette = intArrayOf(
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#FFD166"),
            Color.parseColor("#4ECDC4"),
            Color.parseColor("#8E7CFF")
        )
        repeat(4) { i ->
            kites += Kite(
                x = w * (0.12f + i * 0.2f),
                y = h * (0.18f + i * 0.05f),
                speed = 0.45f + i * 0.1f,
                color = palette[i % palette.size],
                sway = 0.8f + i * 0.35f
            )
        }
    }

    private fun updateFrame() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        frame += 1
        dayPhase = (dayPhase + 0.00045f) % 1f

        updateClouds(w)
        updateKites(w)

        if (frame % 260L == 0L) {
            assignBehavior(doraemon, w, h)
            assignBehavior(shizuka, w, h)

            if (Random.nextFloat() < 0.34f) {
                doraemon.state = CharState.TALKING
                shizuka.state = CharState.TALKING
                val center = w * (0.42f + Random.nextFloat() * 0.16f)
                doraemon.targetX = center - 70f
                shizuka.targetX = center + 70f
                doraemon.targetY = h * 0.73f
                shizuka.targetY = h * 0.74f
            }

            if (Random.nextFloat() < 0.24f) {
                doraemon.state = CharState.GADGET_PLAY
                shizuka.state = CharState.SKIP
                doraemon.targetX = w * 0.38f
                shizuka.targetX = w * 0.62f
            }
        }

        updateCharacter(doraemon, speed = 1.65f)
        updateCharacter(shizuka, speed = 1.45f)

        if (doraemon.state == CharState.GADGET_PLAY && frame % 5L == 0L) {
            spawnSpark(doraemon.x + 26f, doraemon.y - 18f)
        }

        updateSparks()
    }

    private fun assignBehavior(char: Character, w: Float, h: Float) {
        char.state = CharState.values().random()
        char.stateTimer = 0
        when (char.state) {
            CharState.WANDERING -> {
                char.targetX = w * 0.10f + Random.nextFloat() * w * 0.80f
                char.targetY = h * 0.67f + Random.nextFloat() * h * 0.11f
            }
            CharState.IDLE -> {
                char.targetX = char.x
                char.targetY = char.y
            }
            CharState.TALKING,
            CharState.GADGET_PLAY,
            CharState.SKIP -> {
                char.targetX = w * 0.18f + Random.nextFloat() * w * 0.64f
                char.targetY = h * 0.70f + Random.nextFloat() * h * 0.08f
            }
        }
    }

    private fun updateCharacter(char: Character, speed: Float) {
        val dx = char.targetX - char.x
        val dy = char.targetY - char.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 1.5f) {
            char.x += (dx / max(1f, dist)) * speed
            char.y += (dy / max(1f, dist)) * speed
            char.facing = if (dx > 0f) 1 else -1
        }

        char.stateTimer += 1
        char.bobPhase += when (char.state) {
            CharState.WANDERING -> 0.18f
            CharState.SKIP -> 0.26f
            CharState.GADGET_PLAY -> 0.20f
            else -> 0.12f
        }
        char.wavePhase += when (char.state) {
            CharState.TALKING -> 0.24f
            CharState.GADGET_PLAY -> 0.28f
            CharState.SKIP -> 0.20f
            else -> 0.10f
        }

        char.scale = when (char.state) {
            CharState.GADGET_PLAY -> 1f + sin((char.bobPhase * 1.8f).toDouble()).toFloat() * 0.04f
            CharState.SKIP -> 1f + abs(sin((char.bobPhase * 1.4f).toDouble())).toFloat() * 0.03f
            else -> 1f
        }
    }

    private fun updateClouds(w: Float) {
        clouds.forEach { cloud ->
            cloud.x += cloud.speed
            if (cloud.x - cloud.width > w + 20f) {
                cloud.x = -cloud.width - 20f
            }
        }
    }

    private fun updateKites(w: Float) {
        kites.forEachIndexed { idx, kite ->
            kite.x += kite.speed
            kite.y += sin((frame * 0.014f + idx * 0.8f) * kite.sway).toFloat() * 0.22f
            if (kite.x > w + 50f) {
                kite.x = -50f
            }
        }
    }

    private fun spawnSpark(x: Float, y: Float) {
        repeat(2) {
            sparks += Spark(
                x = x,
                y = y,
                vx = (Random.nextFloat() - 0.5f) * 2.2f,
                vy = -1.5f - Random.nextFloat() * 1.2f,
                alpha = 1f,
                size = 2.2f + Random.nextFloat() * 2.4f
            )
        }
    }

    private fun updateSparks() {
        val iterator = sparks.iterator()
        while (iterator.hasNext()) {
            val spark = iterator.next()
            spark.x += spark.vx
            spark.y += spark.vy
            spark.vy += 0.04f
            spark.alpha -= 0.03f
            spark.size *= 0.985f
            if (spark.alpha <= 0.03f || spark.size <= 0.5f) {
                iterator.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawBackdrop(canvas, w, h)
        drawClouds(canvas)
        drawKites(canvas)
        drawParkGround(canvas, w, h)
        drawTownSilhouette(canvas, w, h)
        drawTrees(canvas, w, h)
        drawSparks(canvas)

        drawDoraemon(canvas, doraemon)
        drawShizuka(canvas, shizuka)

        if (doraemon.state == CharState.TALKING) {
            val msg = lastDoraemonMessage?.takeIf { frame <= it.displayUntilFrame }
            val text = msg?.text ?: listOf("Hi!", "Yay!", "♪", "Let's play!").random()
            drawSpeechBubble(canvas, doraemon.x + 64f, doraemon.y - 118f, text)
        }
        if (shizuka.state == CharState.TALKING) {
            val msg = lastShizukaMessage?.takeIf { frame <= it.displayUntilFrame }
            val text = msg?.text ?: listOf("Hehe", "Okay!", "♥", "Nice!").random()
            drawSpeechBubble(canvas, shizuka.x - 74f, shizuka.y - 126f, text)
        }
    }

    private fun drawBackdrop(canvas: Canvas, w: Float, h: Float) {
        val dawn = Color.parseColor("#9BD7FF")
        val noon = Color.parseColor("#6EC3FF")
        val evening = Color.parseColor("#FFB081")
        val twilight = Color.parseColor("#4A6CB8")

        val top = when {
            dayPhase < 0.25f -> lerpColor(dawn, noon, dayPhase / 0.25f)
            dayPhase < 0.50f -> lerpColor(noon, evening, (dayPhase - 0.25f) / 0.25f)
            dayPhase < 0.75f -> lerpColor(evening, twilight, (dayPhase - 0.50f) / 0.25f)
            else -> lerpColor(twilight, dawn, (dayPhase - 0.75f) / 0.25f)
        }

        val bottom = when {
            dayPhase < 0.25f -> lerpColor(Color.parseColor("#D8F2FF"), Color.parseColor("#C5E9FF"), dayPhase / 0.25f)
            dayPhase < 0.50f -> lerpColor(Color.parseColor("#C5E9FF"), Color.parseColor("#FFD8BF"), (dayPhase - 0.25f) / 0.25f)
            dayPhase < 0.75f -> lerpColor(Color.parseColor("#FFD8BF"), Color.parseColor("#7C89C8"), (dayPhase - 0.50f) / 0.25f)
            else -> lerpColor(Color.parseColor("#7C89C8"), Color.parseColor("#D8F2FF"), (dayPhase - 0.75f) / 0.25f)
        }

        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(top, bottom), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val celestialX = w * ((dayPhase * 1.15f) % 1f)
        val celestialY = h * (0.15f + abs(sin((dayPhase * PI).toDouble())).toFloat() * 0.22f)
        val night = dayPhase > 0.58f && dayPhase < 0.95f

        paint.color = if (night) Color.parseColor("#FFF2BE") else Color.parseColor("#FFD36A")
        paint.maskFilter = BlurMaskFilter(if (night) 16f else 20f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(celestialX, celestialY, if (night) 20f else 28f, paint)
        paint.maskFilter = null
        paint.color = if (night) Color.parseColor("#FFF7D8") else Color.parseColor("#FFEEA4")
        canvas.drawCircle(celestialX, celestialY, if (night) 14f else 20f, paint)

        if (night) {
            repeat(24) { i ->
                paint.color = Color.argb(120, 255, 255, 255)
                canvas.drawCircle((i * 71f + frame * 0.4f) % w, (i * 57f + frame * 0.2f) % (h * 0.56f), 1.6f, paint)
            }
        }
    }

    private fun drawClouds(canvas: Canvas) {
        clouds.forEachIndexed { idx, cloud ->
            val drift = sin((frame * 0.01f + idx).toDouble()).toFloat() * 4f
            paint.color = Color.argb(190, 255, 255, 255)
            canvas.drawOval(cloud.x, cloud.y + drift, cloud.x + cloud.width, cloud.y + cloud.height + drift, paint)
            canvas.drawOval(
                cloud.x + cloud.width * 0.15f,
                cloud.y - cloud.height * 0.35f + drift,
                cloud.x + cloud.width * 0.62f,
                cloud.y + cloud.height * 0.62f + drift,
                paint
            )
            canvas.drawOval(
                cloud.x + cloud.width * 0.48f,
                cloud.y - cloud.height * 0.24f + drift,
                cloud.x + cloud.width * 0.90f,
                cloud.y + cloud.height * 0.60f + drift,
                paint
            )
        }
    }

    private fun drawKites(canvas: Canvas) {
        kites.forEachIndexed { idx, kite ->
            val wobble = sin((frame * 0.04f + idx).toDouble()).toFloat() * 12f
            val x = kite.x
            val y = kite.y + wobble

            paint.color = kite.color
            val body = Path().apply {
                moveTo(x, y - 12f)
                lineTo(x + 13f, y)
                lineTo(x, y + 12f)
                lineTo(x - 13f, y)
                close()
            }
            canvas.drawPath(body, paint)

            strokePaint.color = Color.argb(150, 255, 255, 255)
            strokePaint.strokeWidth = 1.6f
            canvas.drawLine(x, y + 10f, x - 28f, y + 58f + sin((frame * 0.03f).toDouble()).toFloat() * 8f, strokePaint)
        }
    }

    private fun drawParkGround(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(
            0f,
            h * 0.68f,
            0f,
            h,
            intArrayOf(Color.parseColor("#67C567"), Color.parseColor("#2F8B42")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.68f, w, h, paint)
        paint.shader = null

        if (enable3D) {
            val path = Path().apply {
                moveTo(w * 0.12f, h)
                cubicTo(w * 0.25f, h * 0.86f, w * 0.55f, h * 0.93f, w * 0.86f, h * 0.80f)
                lineTo(w, h)
                close()
            }
            paint.color = Color.parseColor("#D6BC89")
            canvas.drawPath(path, paint)

            repeat(30) { i ->
                val gx = w * (0.02f + i * 0.033f)
                val gy = h * (0.70f + (i % 6) * 0.04f)
                strokePaint.color = Color.parseColor("#4A9D4F")
                strokePaint.strokeWidth = 2f
                canvas.drawLine(gx, gy + 14f, gx + sin((frame * 0.02f + i).toDouble()).toFloat() * 3f, gy, strokePaint)
            }
        }
    }
    private fun drawTownSilhouette(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(70, 34, 54, 98)
        val skyline = Path().apply {
            moveTo(0f, h * 0.63f)
            lineTo(w * 0.08f, h * 0.63f)
            lineTo(w * 0.08f, h * 0.52f)
            lineTo(w * 0.15f, h * 0.52f)
            lineTo(w * 0.15f, h * 0.61f)
            lineTo(w * 0.22f, h * 0.61f)
            lineTo(w * 0.22f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.60f)
            lineTo(w * 0.43f, h * 0.60f)
            lineTo(w * 0.43f, h * 0.47f)
            lineTo(w * 0.52f, h * 0.47f)
            lineTo(w * 0.52f, h * 0.58f)
            lineTo(w * 0.63f, h * 0.58f)
            lineTo(w * 0.63f, h * 0.51f)
            lineTo(w * 0.72f, h * 0.51f)
            lineTo(w * 0.72f, h * 0.62f)
            lineTo(w * 0.85f, h * 0.62f)
            lineTo(w * 0.85f, h * 0.54f)
            lineTo(w, h * 0.54f)
            lineTo(w, h * 0.66f)
            lineTo(0f, h * 0.66f)
            close()
        }
        canvas.drawPath(skyline, paint)
    }

    private fun drawTrees(canvas: Canvas, w: Float, h: Float) {
        repeat(6) { i ->
            val x = w * (0.08f + i * 0.16f)
            val sway = if (enable3D) sin((frame * 0.016f + i).toDouble()).toFloat() * 2.4f else 0f

            paint.color = Color.parseColor("#6A462C")
            canvas.drawRoundRect(RectF(x - 5f + sway, h * 0.61f, x + 5f + sway, h * 0.77f), 4f, 4f, paint)

            paint.color = Color.parseColor("#2F9847")
            canvas.drawCircle(x + sway, h * 0.58f, 32f, paint)
            
            if (enable3D) {
                paint.color = Color.parseColor("#27803D")
                canvas.drawCircle(x - 14f + sway, h * 0.61f, 20f, paint)
                canvas.drawCircle(x + 16f + sway, h * 0.61f, 20f, paint)
            }
        }
    }

    private fun drawSparks(canvas: Canvas) {
        sparks.forEach { spark ->
            paint.color = Color.argb((spark.alpha * 255f).toInt().coerceIn(0, 255), 255, 255, 170)
            paint.maskFilter = BlurMaskFilter(spark.size * 1.5f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(spark.x, spark.y, spark.size, paint)
            paint.maskFilter = null
            paint.color = Color.argb((spark.alpha * 255f).toInt().coerceIn(0, 255), 255, 240, 120)
            canvas.drawCircle(spark.x, spark.y, spark.size * 0.5f, paint)
        }
    }

    private fun drawDoraemon(canvas: Canvas, char: Character) {
        val bob = sin(char.bobPhase.toDouble()).toFloat() * 4f
        val cx = char.x
        val cy = char.y + bob
        val s = 56f * char.scale

        canvas.save()
        if (char.facing < 0) canvas.scale(-1f, 1f, cx, cy)

        // Body
        paint.color = Color.parseColor("#00A3EB")
        canvas.drawCircle(cx, cy, s, paint)

        // White face
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy - s * 0.09f, s * 0.73f, paint)

        // Eyes
        paint.color = Color.WHITE
        canvas.drawCircle(cx - s * 0.22f, cy - s * 0.35f, s * 0.18f, paint)
        canvas.drawCircle(cx + s * 0.22f, cy - s * 0.35f, s * 0.18f, paint)

        val blink = if (char.state == CharState.IDLE && frame % 160L < 10L) 0.02f else 0.10f
        paint.color = Color.parseColor("#111111")
        canvas.drawOval(cx - s * 0.19f, cy - s * (0.35f + blink), cx - s * 0.11f, cy - s * (0.35f - blink), paint)
        canvas.drawOval(cx + s * 0.11f, cy - s * (0.35f + blink), cx + s * 0.19f, cy - s * (0.35f - blink), paint)

        // Nose
        paint.color = Color.parseColor("#F54040")
        canvas.drawCircle(cx, cy - s * 0.12f, s * 0.09f, paint)

        // Mouth
        strokePaint.color = Color.parseColor("#2E2E2E")
        strokePaint.strokeWidth = 3f
        val smileHeight = when (char.state) {
            CharState.TALKING -> 0.34f
            CharState.GADGET_PLAY -> 0.30f
            else -> 0.24f
        }
        val smile = Path().apply {
            moveTo(cx - s * 0.34f, cy + s * 0.04f)
            cubicTo(cx - s * 0.16f, cy + s * smileHeight, cx + s * 0.16f, cy + s * smileHeight, cx + s * 0.34f, cy + s * 0.04f)
        }
        canvas.drawPath(smile, strokePaint)

        // Whiskers
        strokePaint.strokeWidth = 2f
        for (i in -1..1) {
            canvas.drawLine(cx - s * 0.03f, cy + i * s * 0.09f, cx - s * 0.62f, cy + i * s * 0.13f, strokePaint)
            canvas.drawLine(cx + s * 0.03f, cy + i * s * 0.09f, cx + s * 0.62f, cy + i * s * 0.13f, strokePaint)
        }

        // Collar + bell
        paint.color = Color.parseColor("#FF2C2C")
        canvas.drawRoundRect(RectF(cx - s * 0.55f, cy + s * 0.55f, cx + s * 0.55f, cy + s * 0.70f), 8f, 8f, paint)
        paint.color = Color.parseColor("#FFD54F")
        canvas.drawCircle(cx, cy + s * 0.70f, s * 0.11f, paint)
        paint.color = Color.parseColor("#FFF8D0")
        canvas.drawCircle(cx + s * 0.03f, cy + s * 0.67f, s * 0.03f, paint)

        // Arms
        val wave = when (char.state) {
            CharState.TALKING -> sin(char.wavePhase.toDouble()).toFloat() * 16f
            CharState.GADGET_PLAY -> sin(char.wavePhase.toDouble()).toFloat() * 22f
            else -> sin(char.wavePhase.toDouble()).toFloat() * 6f
        }

        paint.color = Color.parseColor("#00A3EB")
        canvas.drawRoundRect(RectF(cx - s * 0.96f, cy + s * 0.10f - wave, cx - s * 0.50f, cy + s * 0.41f - wave * 0.45f), 12f, 12f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.50f, cy + s * 0.10f + wave, cx + s * 0.96f, cy + s * 0.41f + wave * 0.45f), 12f, 12f, paint)

        // Legs + feet
        canvas.drawRoundRect(RectF(cx - s * 0.45f, cy + s * 0.80f, cx - s * 0.10f, cy + s * 1.2f), 10f, 10f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.10f, cy + s * 0.80f, cx + s * 0.45f, cy + s * 1.2f), 10f, 10f, paint)
        paint.color = Color.WHITE
        canvas.drawOval(cx - s * 0.50f, cy + s * 1.14f, cx - s * 0.05f, cy + s * 1.35f, paint)
        canvas.drawOval(cx + s * 0.05f, cy + s * 1.14f, cx + s * 0.50f, cy + s * 1.35f, paint)

        // Pocket
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(cx - s * 0.30f, cy + s * 0.34f, cx + s * 0.30f, cy + s * 0.66f), 13f, 13f, paint)
        strokePaint.color = Color.parseColor("#00A3EB")
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(cx - s * 0.30f, cy + s * 0.34f, cx + s * 0.30f, cy + s * 0.66f), 13f, 13f, strokePaint)

        if (char.state == CharState.GADGET_PLAY) {
            paint.color = Color.parseColor("#F5FF7A")
            paint.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(cx + s * 0.02f, cy + s * 0.50f, s * 0.13f, paint)
            paint.maskFilter = null
        }

        canvas.restore()
    }

    private fun drawShizuka(canvas: Canvas, char: Character) {
        val bob = sin((char.bobPhase + 1.1f).toDouble()).toFloat() * 3f
        val cx = char.x
        val cy = char.y + bob
        val s = 41f * char.scale

        canvas.save()
        if (char.facing < 0) canvas.scale(-1f, 1f, cx, cy)

        paint.shader = LinearGradient(
            cx,
            cy,
            cx,
            cy + s * 1.2f,
            intArrayOf(Color.parseColor("#FFC1D8"), Color.parseColor("#FF8FB8")),
            null,
            Shader.TileMode.CLAMP
        )
        val dress = Path().apply {
            moveTo(cx - s * 0.35f, cy + s * 0.12f)
            lineTo(cx + s * 0.35f, cy + s * 0.12f)
            lineTo(cx + s * 0.58f, cy + s * 1.12f)
            lineTo(cx - s * 0.58f, cy + s * 1.12f)
            close()
        }
        canvas.drawPath(dress, paint)
        paint.shader = null

        // Head
        paint.color = Color.parseColor("#FFD8B8")
        canvas.drawCircle(cx, cy - s * 0.58f, s * 0.45f, paint)

        // Hair + ribbon
        paint.color = Color.parseColor("#2F1A12")
        canvas.drawArc(RectF(cx - s * 0.45f, cy - s * 1.12f, cx + s * 0.45f, cy - s * 0.30f), 180f, 180f, true, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.28f, cy - s * 1.00f, cx + s * 0.62f, cy - s * 0.56f), 8f, 8f, paint)
        paint.color = Color.parseColor("#FF5555")
        canvas.drawCircle(cx + s * 0.45f, cy - s * 0.96f, s * 0.10f, paint)

        // Eyes
        paint.color = Color.parseColor("#311818")
        canvas.drawOval(cx - s * 0.17f, cy - s * 0.72f, cx - s * 0.05f, cy - s * 0.58f, paint)
        canvas.drawOval(cx + s * 0.05f, cy - s * 0.72f, cx + s * 0.17f, cy - s * 0.58f, paint)

        // Smile
        strokePaint.color = Color.parseColor("#B23D59")
        strokePaint.strokeWidth = 2f
        canvas.drawArc(RectF(cx - s * 0.12f, cy - s * 0.46f, cx + s * 0.12f, cy - s * 0.30f), 0f, 180f, false, strokePaint)

        val armWave = when (char.state) {
            CharState.SKIP -> sin(char.wavePhase.toDouble()).toFloat() * 18f
            CharState.TALKING -> sin(char.wavePhase.toDouble()).toFloat() * 12f
            else -> sin(char.wavePhase.toDouble()).toFloat() * 6f
        }

        paint.color = Color.parseColor("#FFD8B8")
        canvas.drawRoundRect(RectF(cx - s * 0.72f, cy - s * 0.08f + armWave, cx - s * 0.34f, cy + s * 0.22f + armWave), 8f, 8f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.34f, cy - s * 0.08f - armWave, cx + s * 0.72f, cy + s * 0.22f - armWave), 8f, 8f, paint)

        // Legs + shoes
        val skipSwing = if (char.state == CharState.SKIP) abs(sin((char.bobPhase * 1.4f).toDouble())).toFloat() * 12f else 0f
        canvas.drawRoundRect(RectF(cx - s * 0.28f, cy + s * 1.05f, cx - s * 0.08f, cy + s * 1.50f + skipSwing), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.08f, cy + s * 1.05f, cx + s * 0.28f, cy + s * 1.50f - skipSwing), 6f, 6f, paint)

        paint.color = Color.parseColor("#7E4B2A")
        canvas.drawOval(cx - s * 0.35f, cy + s * 1.44f + skipSwing, cx + 1f, cy + s * 1.64f + skipSwing, paint)
        canvas.drawOval(cx - 1f, cy + s * 1.44f - skipSwing, cx + s * 0.35f, cy + s * 1.64f - skipSwing, paint)

        if (char.state == CharState.SKIP) {
            strokePaint.color = Color.parseColor("#D8A45A")
            strokePaint.strokeWidth = 3f
            val rope = Path().apply {
                moveTo(cx - s * 0.82f, cy + s * 0.14f + armWave)
                cubicTo(cx - s * 0.58f, cy - s * 0.90f, cx + s * 0.58f, cy - s * 0.90f, cx + s * 0.82f, cy + s * 0.14f - armWave)
            }
            canvas.drawPath(rope, strokePaint)
        }

        canvas.restore()
    }

    private fun drawSpeechBubble(canvas: Canvas, x: Float, y: Float, text: String) {
        // Wrap text if too long
        val maxChars = 18
        val displayText = if (text.length > maxChars) text.substring(0, maxChars) + "…" else text
        
        paint.color = Color.argb(240, 255, 255, 255)
        val bubble = RectF(x - 56f, y - 28f, x + 56f, y + 12f)
        canvas.drawRoundRect(bubble, 14f, 14f, paint)
        strokePaint.color = Color.parseColor("#D5D5D5")
        strokePaint.strokeWidth = 2f
        canvas.drawRoundRect(bubble, 14f, 14f, strokePaint)

        paint.color = Color.WHITE
        val tail = Path().apply {
            moveTo(x - 10f, y + 12f)
            lineTo(x + 2f, y + 12f)
            lineTo(x - 6f, y + 26f)
            close()
        }
        canvas.drawPath(tail, paint)
        strokePaint.color = Color.parseColor("#D5D5D5")
        canvas.drawPath(tail, strokePaint)

        canvas.drawText(displayText, x, y + 2f, textPaint)
    }

    private fun cleanupExpiredMessages() {
        chatMessages.removeAll { it.displayUntilFrame < frame }
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val v = t.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * v).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * v).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * v).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * v).toInt()
        return Color.argb(a, r, g, b)
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            updateFrame()
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    fun startAnimation() {
        running = true
        handler.post(frameRunnable)
    }

    fun stopAnimation() {
        running = false
        handler.removeCallbacks(frameRunnable)
    }

    fun queueChatMessage(sender: String, text: String, displayDurationMs: Long = 3000L) {
        val msg = ChatMessage(sender, text, frame + (displayDurationMs / 16))
        chatMessages.add(msg)
        when (sender) {
            "doraemon" -> lastDoraemonMessage = msg
            "shizuka" -> lastShizukaMessage = msg
        }
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
