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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * NatureAdventureTheme
 *
 * Upgraded cinematic nature theme with smoother scene transitions,
 * richer atmospherics, and more expressive character motion.
 */
class NatureAdventureTheme(private val context: Context) {

    private var themeView: NatureView? = null
    private var attached = false

    fun attach(root: FrameLayout) {
        if (attached) return
        themeView = NatureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
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
        attached = false
    }
}

enum class NatureScene { BEACH, MOUNTAIN, FOREST, MEADOW }

private enum class NatureParticleKind { FIREFLY, PETAL, BUTTERFLY }

private data class NatureParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var alpha: Float,
    var phase: Float,
    var life: Float,
    val color: Int,
    val kind: NatureParticleKind
)

private data class CloudLayer(
    var x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val speed: Float,
    val depth: Float
)

private data class NatureKid(
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    var facing: Int = 1,
    var action: Int = 0,
    var bobPhase: Float = 0f,
    var jumpOffset: Float = 0f
)

class NatureView(context: Context) : View(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var frame = 0L
    private var sceneTimer = 0
    private var scene = NatureScene.BEACH
    private var nextScene = NatureScene.BEACH
    private var sceneBlend = 1f

    private var timeOfDay = 0.15f
    private var windPhase = 0f
    private var wavePhase = 0f

    private val particles = mutableListOf<NatureParticle>()
    private val clouds = mutableListOf<CloudLayer>()

    private var boy = NatureKid(0f, 0f, 0f, 0f)
    private var girl = NatureKid(0f, 0f, 0f, 0f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            updateFrame()
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        boy = NatureKid(
            x = w * 0.28f,
            y = h * 0.72f,
            targetX = w * 0.28f,
            targetY = h * 0.72f
        )
        girl = NatureKid(
            x = w * 0.64f,
            y = h * 0.72f,
            targetX = w * 0.64f,
            targetY = h * 0.72f,
            facing = -1
        )

        buildClouds(w.toFloat(), h.toFloat())
        spawnSceneParticles(scene, w.toFloat(), h.toFloat())
    }

    private fun buildClouds(w: Float, h: Float) {
        clouds.clear()
        repeat(8) { idx ->
            val depth = 0.65f + Random.nextFloat() * 0.5f
            clouds += CloudLayer(
                x = Random.nextFloat() * w,
                y = h * (0.08f + idx * 0.03f) + Random.nextFloat() * h * 0.08f,
                width = (120f + Random.nextFloat() * 160f) * depth,
                height = (45f + Random.nextFloat() * 35f) * depth,
                speed = (0.16f + Random.nextFloat() * 0.22f) * depth,
                depth = depth
            )
        }
    }

    private fun spawnSceneParticles(targetScene: NatureScene, w: Float, h: Float) {
        particles.clear()
        when (targetScene) {
            NatureScene.FOREST -> repeat(32) {
                particles += NatureParticle(
                    x = Random.nextFloat() * w,
                    y = h * 0.18f + Random.nextFloat() * h * 0.58f,
                    vx = (Random.nextFloat() - 0.5f) * 0.6f,
                    vy = (Random.nextFloat() - 0.5f) * 0.4f,
                    size = 2.5f + Random.nextFloat() * 2.2f,
                    alpha = Random.nextFloat(),
                    phase = Random.nextFloat() * (2f * PI.toFloat()),
                    life = Random.nextFloat(),
                    color = Color.parseColor("#C8FF7A"),
                    kind = NatureParticleKind.FIREFLY
                )
            }
            NatureScene.MEADOW -> {
                repeat(20) {
                    particles += NatureParticle(
                        x = Random.nextFloat() * w,
                        y = h * 0.10f + Random.nextFloat() * h * 0.65f,
                        vx = (Random.nextFloat() - 0.5f) * 0.9f,
                        vy = (Random.nextFloat() - 0.5f) * 0.55f,
                        size = 3f + Random.nextFloat() * 3f,
                        alpha = 0.55f + Random.nextFloat() * 0.4f,
                        phase = Random.nextFloat() * (2f * PI.toFloat()),
                        life = Random.nextFloat(),
                        color = Color.parseColor("#FFD1F2"),
                        kind = NatureParticleKind.PETAL
                    )
                }
                repeat(10) {
                    particles += NatureParticle(
                        x = Random.nextFloat() * w,
                        y = h * 0.20f + Random.nextFloat() * h * 0.42f,
                        vx = (Random.nextFloat() - 0.5f) * 1.1f,
                        vy = (Random.nextFloat() - 0.5f) * 0.7f,
                        size = 5f + Random.nextFloat() * 3f,
                        alpha = 0.75f,
                        phase = Random.nextFloat() * (2f * PI.toFloat()),
                        life = Random.nextFloat(),
                        color = Color.parseColor("#FFB6F9"),
                        kind = NatureParticleKind.BUTTERFLY
                    )
                }
            }
            NatureScene.BEACH -> repeat(12) {
                particles += NatureParticle(
                    x = Random.nextFloat() * w,
                    y = h * 0.62f + Random.nextFloat() * h * 0.11f,
                    vx = 0.2f + Random.nextFloat() * 0.5f,
                    vy = -0.08f + Random.nextFloat() * 0.15f,
                    size = 2f + Random.nextFloat() * 1.8f,
                    alpha = 0.35f + Random.nextFloat() * 0.3f,
                    phase = Random.nextFloat() * (2f * PI.toFloat()),
                    life = Random.nextFloat(),
                    color = Color.parseColor("#D6FBFF"),
                    kind = NatureParticleKind.PETAL
                )
            }
            NatureScene.MOUNTAIN -> repeat(10) {
                particles += NatureParticle(
                    x = Random.nextFloat() * w,
                    y = h * 0.12f + Random.nextFloat() * h * 0.38f,
                    vx = 0.05f + Random.nextFloat() * 0.28f,
                    vy = 0.15f + Random.nextFloat() * 0.3f,
                    size = 2.2f + Random.nextFloat() * 2f,
                    alpha = 0.45f + Random.nextFloat() * 0.4f,
                    phase = Random.nextFloat() * (2f * PI.toFloat()),
                    life = Random.nextFloat(),
                    color = Color.parseColor("#FFFFFF"),
                    kind = NatureParticleKind.PETAL
                )
            }
        }
    }

    private fun updateFrame() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        frame += 1
        sceneTimer += 1

        windPhase += 0.018f
        wavePhase += 0.05f
        timeOfDay = (timeOfDay + 0.00033f) % 1f

        if (sceneBlend >= 1f && sceneTimer > 5200) {
            val scenes = NatureScene.values()
            nextScene = scenes[(scene.ordinal + 1) % scenes.size]
            sceneBlend = 0f
            sceneTimer = 0
            spawnSceneParticles(nextScene, w, h)
        } else if (sceneBlend < 1f) {
            sceneBlend += 0.0085f
            if (sceneBlend >= 1f) {
                sceneBlend = 1f
                scene = nextScene
            }
        }

        if (frame % 220L == 0L) {
            assignKidTargets(w, h)
        }

        updateClouds(w)
        updateKid(boy, speed = 1.75f)
        updateKid(girl, speed = 1.58f)
        updateParticles(w, h)
    }

    private fun assignKidTargets(w: Float, h: Float) {
        boy.action = Random.nextInt(4)
        girl.action = Random.nextInt(4)

        val shouldInteract = Random.nextFloat() < 0.35f
        if (shouldInteract) {
            val mid = w * (0.40f + Random.nextFloat() * 0.2f)
            boy.targetX = mid - 64f
            girl.targetX = mid + 64f
            boy.targetY = h * 0.72f
            girl.targetY = h * 0.72f
            boy.action = 3
            girl.action = 3
            return
        }

        boy.targetX = w * 0.12f + Random.nextFloat() * w * 0.36f
        girl.targetX = w * 0.50f + Random.nextFloat() * w * 0.35f
        boy.targetY = h * (0.68f + Random.nextFloat() * 0.08f)
        girl.targetY = h * (0.68f + Random.nextFloat() * 0.08f)
    }

    private fun updateClouds(w: Float) {
        for (cloud in clouds) {
            cloud.x += cloud.speed
            if (cloud.x - cloud.width > w + 30f) {
                cloud.x = -cloud.width - 30f
            }
        }
    }

    private fun updateKid(kid: NatureKid, speed: Float) {
        val dx = kid.targetX - kid.x
        val dy = kid.targetY - kid.y
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > 1.5f) {
            kid.x += (dx / max(1f, dist)) * speed
            kid.y += (dy / max(1f, dist)) * speed
            kid.facing = if (dx >= 0f) 1 else -1
        }

        kid.bobPhase += if (kid.action == 0) 0.22f else 0.14f
        kid.jumpOffset = if (kid.action == 1) {
            abs(sin(kid.bobPhase.toDouble())).toFloat() * 24f
        } else {
            kid.jumpOffset * 0.82f
        }
    }

    private fun updateParticles(w: Float, h: Float) {
        particles.forEach { p ->
            p.life += 0.008f
            p.phase += 0.05f

            when (p.kind) {
                NatureParticleKind.FIREFLY -> {
                    p.x += p.vx + sin(p.phase.toDouble()).toFloat() * 0.22f
                    p.y += p.vy + cos((p.phase * 0.8f).toDouble()).toFloat() * 0.12f
                    p.alpha = (0.2f + abs(sin((p.life * 4f + p.phase).toDouble())).toFloat() * 0.8f).coerceIn(0f, 1f)
                }
                NatureParticleKind.PETAL -> {
                    p.x += p.vx + sin((windPhase + p.phase).toDouble()).toFloat() * 0.45f
                    p.y += p.vy + 0.08f
                    p.alpha = (0.25f + abs(sin((p.life * 2f).toDouble())).toFloat() * 0.7f).coerceIn(0f, 1f)
                }
                NatureParticleKind.BUTTERFLY -> {
                    p.x += p.vx + sin((p.phase * 1.4f).toDouble()).toFloat() * 0.55f
                    p.y += p.vy + cos((p.phase * 1.1f).toDouble()).toFloat() * 0.34f
                    p.alpha = 0.7f + 0.3f * abs(sin((p.life * 3f).toDouble())).toFloat()
                }
            }

            if (p.x < -20f) p.x = w + 20f
            if (p.x > w + 20f) p.x = -20f
            if (p.y < h * 0.05f) p.y = h * 0.15f
            if (p.y > h * 0.92f) p.y = h * 0.3f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawSkyAndAtmosphere(canvas, w, h)
        drawClouds(canvas)

        if (sceneBlend < 1f) {
            drawSceneLayer(canvas, scene, alpha = 1f - sceneBlend, w = w, h = h)
            drawSceneLayer(canvas, nextScene, alpha = sceneBlend, w = w, h = h)
        } else {
            drawSceneLayer(canvas, scene, alpha = 1f, w = w, h = h)
        }

        drawParticles(canvas)
        drawKids(canvas)
    }

    private fun drawSkyAndAtmosphere(canvas: Canvas, w: Float, h: Float) {
        val morning = Color.parseColor("#95D7FF")
        val noon = Color.parseColor("#69B7FF")
        val dusk = Color.parseColor("#FF9E73")
        val night = Color.parseColor("#0F1D4D")

        val skyTop = when {
            timeOfDay < 0.25f -> lerpColor(morning, noon, timeOfDay / 0.25f)
            timeOfDay < 0.50f -> lerpColor(noon, dusk, (timeOfDay - 0.25f) / 0.25f)
            timeOfDay < 0.75f -> lerpColor(dusk, night, (timeOfDay - 0.50f) / 0.25f)
            else -> lerpColor(night, morning, (timeOfDay - 0.75f) / 0.25f)
        }

        val skyBottom = when {
            timeOfDay < 0.25f -> lerpColor(Color.parseColor("#D8F0FF"), Color.parseColor("#BDE3FF"), timeOfDay / 0.25f)
            timeOfDay < 0.50f -> lerpColor(Color.parseColor("#BDE3FF"), Color.parseColor("#FFC9A8"), (timeOfDay - 0.25f) / 0.25f)
            timeOfDay < 0.75f -> lerpColor(Color.parseColor("#FFC9A8"), Color.parseColor("#1D2B63"), (timeOfDay - 0.50f) / 0.25f)
            else -> lerpColor(Color.parseColor("#1D2B63"), Color.parseColor("#D8F0FF"), (timeOfDay - 0.75f) / 0.25f)
        }

        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h,
            intArrayOf(skyTop, skyBottom),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val celestialX = w * ((timeOfDay * 1.2f) % 1f)
        val celestialY = h * (0.16f + abs(sin((timeOfDay * PI).toDouble())).toFloat() * 0.24f)

        val isNight = timeOfDay > 0.58f && timeOfDay < 0.95f
        if (isNight) {
            paint.color = Color.parseColor("#F5F0CE")
            paint.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(celestialX, celestialY, 26f, paint)
            paint.maskFilter = null
            paint.color = Color.parseColor("#FFFAD1")
            canvas.drawCircle(celestialX, celestialY, 20f, paint)
        } else {
            paint.color = Color.parseColor("#FFE77A")
            paint.maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(celestialX, celestialY, 34f, paint)
            paint.maskFilter = null
            paint.color = Color.parseColor("#FFD15A")
            canvas.drawCircle(celestialX, celestialY, 26f, paint)
        }

        if (isNight) {
            repeat(26) { i ->
                val sx = (i * 97f + frame * 0.4f) % w
                val sy = (i * 57f + frame * 0.2f) % (h * 0.56f)
                paint.color = Color.argb(120, 255, 255, 255)
                canvas.drawCircle(sx, sy, 1.6f, paint)
            }
        }
    }

    private fun drawClouds(canvas: Canvas) {
        clouds.forEachIndexed { idx, cloud ->
            val drift = sin((windPhase + idx).toDouble()).toFloat() * 5f * cloud.depth
            paint.color = Color.argb((120f * cloud.depth).toInt().coerceIn(30, 190), 255, 255, 255)
            canvas.drawOval(
                cloud.x,
                cloud.y + drift,
                cloud.x + cloud.width,
                cloud.y + cloud.height + drift,
                paint
            )
            canvas.drawOval(
                cloud.x + cloud.width * 0.16f,
                cloud.y - cloud.height * 0.36f + drift,
                cloud.x + cloud.width * 0.64f,
                cloud.y + cloud.height * 0.65f + drift,
                paint
            )
            canvas.drawOval(
                cloud.x + cloud.width * 0.46f,
                cloud.y - cloud.height * 0.28f + drift,
                cloud.x + cloud.width * 0.92f,
                cloud.y + cloud.height * 0.62f + drift,
                paint
            )
        }
    }

    private fun drawSceneLayer(canvas: Canvas, activeScene: NatureScene, alpha: Float, w: Float, h: Float) {
        if (alpha <= 0f) return
        val layer = canvas.saveLayerAlpha(0f, 0f, w, h, (alpha * 255).toInt().coerceIn(0, 255))
        when (activeScene) {
            NatureScene.BEACH -> drawBeachScene(canvas, w, h)
            NatureScene.MOUNTAIN -> drawMountainScene(canvas, w, h)
            NatureScene.FOREST -> drawForestScene(canvas, w, h)
            NatureScene.MEADOW -> drawMeadowScene(canvas, w, h)
        }
        canvas.restoreToCount(layer)
    }

    private fun drawBeachScene(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(
            0f,
            h * 0.52f,
            0f,
            h * 0.76f,
            intArrayOf(Color.parseColor("#6FD8FF"), Color.parseColor("#34A6DD")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.52f, w, h * 0.76f, paint)
        paint.shader = null

        val wavePath = Path()
        wavePath.moveTo(0f, h * 0.60f)
        for (i in 0..60) {
            val x = w * (i / 60f)
            val y = h * 0.60f + sin((wavePhase + i * 0.35f).toDouble()).toFloat() * 7f
            wavePath.lineTo(x, y)
        }
        wavePath.lineTo(w, h * 0.66f)
        wavePath.lineTo(0f, h * 0.66f)
        wavePath.close()
        paint.color = Color.argb(130, 210, 247, 255)
        canvas.drawPath(wavePath, paint)

        paint.shader = LinearGradient(
            0f,
            h * 0.70f,
            0f,
            h,
            intArrayOf(Color.parseColor("#F2CC72"), Color.parseColor("#DCA94D")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.70f, w, h, paint)
        paint.shader = null

        repeat(10) { i ->
            val sx = w * 0.08f + i * w * 0.09f + sin((windPhase + i).toDouble()).toFloat() * 6f
            val sy = h * 0.81f + abs(sin((windPhase + i * 0.6f).toDouble())).toFloat() * 10f
            paint.color = if (i % 2 == 0) Color.parseColor("#FFDFE8") else Color.parseColor("#F5F0D0")
            canvas.drawOval(sx - 8f, sy - 4f, sx + 9f, sy + 4f, paint)
        }

        drawPalmSilhouette(canvas, w * 0.10f, h * 0.72f, 56f)
        drawPalmSilhouette(canvas, w * 0.90f, h * 0.74f, 48f)
    }

    private fun drawPalmSilhouette(canvas: Canvas, x: Float, y: Float, size: Float) {
        paint.color = Color.parseColor("#5E3A1E")
        canvas.drawRoundRect(RectF(x - 6f, y - size * 1.1f, x + 6f, y + size * 0.2f), 6f, 6f, paint)

        paint.color = Color.parseColor("#2E7D32")
        repeat(5) { i ->
            val angle = -1.1f + i * 0.55f + sin((windPhase + i * 0.4f).toDouble()).toFloat() * 0.08f
            val leafLen = size * (0.85f + i * 0.07f)
            val lx = x + cos(angle.toDouble()).toFloat() * leafLen
            val ly = y - size + sin(angle.toDouble()).toFloat() * leafLen
            strokePaint.color = Color.parseColor("#2C8E3A")
            strokePaint.strokeWidth = 8f
            canvas.drawLine(x, y - size * 0.95f, lx, ly, strokePaint)
        }
    }

    private fun drawMountainScene(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(
            0f,
            h * 0.40f,
            0f,
            h,
            intArrayOf(Color.parseColor("#4A90C8"), Color.parseColor("#2B6CA1")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.40f, w, h, paint)
        paint.shader = null

        drawMountainPeak(canvas, w * 0.20f, h * 0.70f, w * 0.55f, h * 0.22f, Color.parseColor("#6B7B8A"))
        drawMountainPeak(canvas, w * 0.48f, h * 0.72f, w * 0.58f, h * 0.18f, Color.parseColor("#5D6E7A"))
        drawMountainPeak(canvas, w * 0.78f, h * 0.70f, w * 0.45f, h * 0.20f, Color.parseColor("#738391"))

        paint.color = Color.WHITE
        drawSnowCap(canvas, w * 0.20f, h * 0.22f, 60f)
        drawSnowCap(canvas, w * 0.48f, h * 0.18f, 72f)
        drawSnowCap(canvas, w * 0.78f, h * 0.20f, 52f)

        paint.shader = LinearGradient(
            0f,
            h * 0.68f,
            0f,
            h,
            intArrayOf(Color.parseColor("#4A9549"), Color.parseColor("#2C6432")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.68f, w, h, paint)
        paint.shader = null

        repeat(8) { i ->
            val px = w * (0.06f + i * 0.12f) + sin((windPhase + i).toDouble()).toFloat() * 3f
            drawPineTree(canvas, px, h * 0.72f, 34f + (i % 3) * 9f)
        }
    }

    private fun drawMountainPeak(canvas: Canvas, cx: Float, baseY: Float, baseW: Float, tipY: Float, color: Int) {
        paint.color = color
        val path = Path().apply {
            moveTo(cx - baseW / 2f, baseY)
            lineTo(cx, tipY)
            lineTo(cx + baseW / 2f, baseY)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSnowCap(canvas: Canvas, cx: Float, tipY: Float, size: Float) {
        val path = Path().apply {
            moveTo(cx - size * 0.55f, tipY + size * 0.60f)
            lineTo(cx, tipY)
            lineTo(cx + size * 0.55f, tipY + size * 0.62f)
            lineTo(cx + size * 0.25f, tipY + size * 0.75f)
            lineTo(cx - size * 0.20f, tipY + size * 0.80f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPineTree(canvas: Canvas, cx: Float, baseY: Float, size: Float) {
        val sway = sin((windPhase + cx * 0.01f).toDouble()).toFloat() * 2.5f
        paint.color = Color.parseColor("#5D4037")
        canvas.drawRoundRect(RectF(cx - 4f + sway, baseY + size * 0.25f, cx + 4f + sway, baseY + size * 0.9f), 4f, 4f, paint)

        paint.color = Color.parseColor("#1F7D3A")
        for (i in 0..2) {
            val width = size * (0.7f - i * 0.16f)
            val y = baseY - i * size * 0.28f
            val path = Path().apply {
                moveTo(cx - width + sway, y + size * 0.35f)
                lineTo(cx + sway, y - size * 0.35f)
                lineTo(cx + width + sway, y + size * 0.35f)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawForestScene(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h,
            intArrayOf(Color.parseColor("#0F4024"), Color.parseColor("#1E5F31"), Color.parseColor("#245A2C")),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Light shafts
        repeat(6) { i ->
            val x = w * (0.08f + i * 0.16f) + sin((windPhase + i).toDouble()).toFloat() * 16f
            paint.color = Color.argb(28, 255, 245, 180)
            val shaft = Path().apply {
                moveTo(x, 0f)
                lineTo(x + 65f, 0f)
                lineTo(x + 180f, h * 0.82f)
                lineTo(x + 120f, h * 0.82f)
                close()
            }
            canvas.drawPath(shaft, paint)
        }

        // Trunks and canopy
        repeat(9) { i ->
            val tx = w * (0.03f + i * 0.11f) + sin((windPhase * 0.6f + i).toDouble()).toFloat() * 5f
            paint.color = Color.parseColor("#4B2F1E")
            canvas.drawRoundRect(RectF(tx, h * 0.30f, tx + 18f, h), 8f, 8f, paint)

            paint.color = Color.parseColor("#2D7A3A")
            canvas.drawCircle(tx + 8f, h * 0.26f, 40f, paint)
            paint.color = Color.parseColor("#245F31")
            canvas.drawCircle(tx - 10f, h * 0.30f, 28f, paint)
            canvas.drawCircle(tx + 24f, h * 0.30f, 28f, paint)
        }

        paint.shader = LinearGradient(
            0f,
            h * 0.74f,
            0f,
            h,
            intArrayOf(Color.parseColor("#335D2D"), Color.parseColor("#1E3E1D")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.74f, w, h, paint)
        paint.shader = null
    }

    private fun drawMeadowScene(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(
            0f,
            h * 0.56f,
            0f,
            h,
            intArrayOf(Color.parseColor("#7FD58D"), Color.parseColor("#45A557")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.56f, w, h, paint)
        paint.shader = null

        paint.color = Color.parseColor("#64C16F")
        val hillPath = Path().apply {
            moveTo(0f, h * 0.68f)
            cubicTo(w * 0.18f, h * 0.52f, w * 0.32f, h * 0.74f, w * 0.52f, h * 0.60f)
            cubicTo(w * 0.68f, h * 0.50f, w * 0.88f, h * 0.68f, w, h * 0.57f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(hillPath, paint)

        val flowerPalette = intArrayOf(
            Color.parseColor("#FFE26B"),
            Color.parseColor("#FF8ACB"),
            Color.parseColor("#B8F76A"),
            Color.parseColor("#89D4FF"),
            Color.parseColor("#FFFFFF")
        )

        repeat(34) { i ->
            val fx = w * (0.03f + i * 0.028f) + sin((windPhase + i * 0.5f).toDouble()).toFloat() * 6f
            val fy = h * (0.67f + (i % 6) * 0.04f)
            strokePaint.color = Color.parseColor("#4E9A4F")
            strokePaint.strokeWidth = 2.2f
            canvas.drawLine(fx, fy, fx + sin((windPhase + i).toDouble()).toFloat() * 3f, fy - 19f, strokePaint)

            paint.color = flowerPalette[i % flowerPalette.size]
            canvas.drawCircle(fx, fy - 21f, 5.2f, paint)
            paint.color = Color.parseColor("#F7E37A")
            canvas.drawCircle(fx, fy - 21f, 2.2f, paint)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { p ->
            val alpha = (p.alpha * 255f).toInt().coerceIn(0, 255)
            when (p.kind) {
                NatureParticleKind.FIREFLY -> {
                    paint.color = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
                    paint.maskFilter = BlurMaskFilter(p.size * 2.4f, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawCircle(p.x, p.y, p.size * 1.5f, paint)
                    paint.maskFilter = null
                    paint.color = Color.argb((alpha * 0.85f).toInt(), 255, 255, 210)
                    canvas.drawCircle(p.x, p.y, p.size * 0.75f, paint)
                }
                NatureParticleKind.PETAL -> {
                    val swing = sin((p.phase + p.life * 2f).toDouble()).toFloat() * 18f
                    canvas.save()
                    canvas.rotate(swing, p.x, p.y)
                    paint.color = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
                    canvas.drawOval(p.x - p.size, p.y - p.size * 0.4f, p.x + p.size, p.y + p.size * 0.45f, paint)
                    canvas.restore()
                }
                NatureParticleKind.BUTTERFLY -> {
                    val flap = abs(sin((p.phase * 2.8f).toDouble())).toFloat()
                    val wing = p.size * (0.55f + flap * 0.7f)
                    paint.color = Color.argb(alpha, 255, 178, 242)
                    canvas.drawOval(p.x - wing, p.y - p.size * 0.55f, p.x - 1.5f, p.y + p.size * 0.55f, paint)
                    canvas.drawOval(p.x + 1.5f, p.y - p.size * 0.55f, p.x + wing, p.y + p.size * 0.55f, paint)
                    paint.color = Color.argb(alpha, 132, 58, 169)
                    canvas.drawRoundRect(RectF(p.x - 1.8f, p.y - p.size * 0.62f, p.x + 1.8f, p.y + p.size * 0.62f), 2f, 2f, paint)
                }
            }
        }
    }

    private fun drawKids(canvas: Canvas) {
        drawBoy(canvas, boy.x, boy.y - boy.jumpOffset, boy.facing, boy.action)
        drawGirl(canvas, girl.x, girl.y - girl.jumpOffset, girl.facing, girl.action)
    }

    private fun drawBoy(canvas: Canvas, cx: Float, cy: Float, facing: Int, action: Int) {
        val s = 33f
        val walkSwing = if (action == 0 || action == 3) sin((boy.bobPhase * 1.1f).toDouble()).toFloat() * 11f else 0f
        val armSwing = if (action == 3) sin((boy.bobPhase * 1.8f).toDouble()).toFloat() * 18f else walkSwing * 0.7f

        canvas.save()
        if (facing < 0) canvas.scale(-1f, 1f, cx, cy)

        paint.color = Color.parseColor("#1A5EBD")
        canvas.drawRoundRect(RectF(cx - s * 0.26f, cy + s * 0.50f, cx - s * 0.02f, cy + s * 1.18f + walkSwing), 7f, 7f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.02f, cy + s * 0.50f, cx + s * 0.26f, cy + s * 1.18f - walkSwing), 7f, 7f, paint)

        paint.color = Color.parseColor("#2D2D2D")
        canvas.drawOval(cx - s * 0.32f, cy + s * 1.12f + walkSwing, cx - 1f, cy + s * 1.30f + walkSwing, paint)
        canvas.drawOval(cx + 1f, cy + s * 1.12f - walkSwing, cx + s * 0.32f, cy + s * 1.30f - walkSwing, paint)

        paint.color = Color.parseColor("#42A5F5")
        canvas.drawRoundRect(RectF(cx - s * 0.40f, cy - s * 0.08f, cx + s * 0.40f, cy + s * 0.58f), 10f, 10f, paint)

        paint.color = Color.parseColor("#FFD6B0")
        canvas.drawRoundRect(RectF(cx - s * 0.70f, cy - s * 0.04f + armSwing, cx - s * 0.35f, cy + s * 0.42f + armSwing), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.35f, cy - s * 0.04f - armSwing, cx + s * 0.70f, cy + s * 0.42f - armSwing), 6f, 6f, paint)

        paint.color = Color.parseColor("#FFD6B0")
        canvas.drawCircle(cx, cy - s * 0.36f, s * 0.38f, paint)

        paint.color = Color.parseColor("#3B2A21")
        canvas.drawArc(RectF(cx - s * 0.39f, cy - s * 0.78f, cx + s * 0.39f, cy - s * 0.08f), 180f, 180f, true, paint)

        paint.color = Color.parseColor("#142654")
        canvas.drawCircle(cx - s * 0.12f, cy - s * 0.40f, s * 0.06f, paint)
        canvas.drawCircle(cx + s * 0.12f, cy - s * 0.40f, s * 0.06f, paint)

        strokePaint.color = Color.parseColor("#C43D32")
        strokePaint.strokeWidth = 2.1f
        canvas.drawArc(RectF(cx - s * 0.14f, cy - s * 0.24f, cx + s * 0.14f, cy - s * 0.09f), 0f, 180f, false, strokePaint)

        canvas.restore()
    }

    private fun drawGirl(canvas: Canvas, cx: Float, cy: Float, facing: Int, action: Int) {
        val s = 31f
        val dance = if (action == 3) sin((girl.bobPhase * 1.35f).toDouble()).toFloat() * 9f else 0f
        val legSwing = if (action == 0) sin((girl.bobPhase * 1.1f).toDouble()).toFloat() * 10f else 0f

        canvas.save()
        if (facing < 0) canvas.scale(-1f, 1f, cx, cy)

        paint.color = Color.parseColor("#E2494D")
        val dress = Path().apply {
            moveTo(cx - s * 0.33f, cy + s * 0.08f + dance)
            lineTo(cx + s * 0.33f, cy + s * 0.08f - dance)
            lineTo(cx + s * 0.53f, cy + s * 1.02f)
            lineTo(cx - s * 0.53f, cy + s * 1.02f)
            close()
        }
        canvas.drawPath(dress, paint)

        paint.color = Color.parseColor("#FFD8B8")
        canvas.drawRoundRect(RectF(cx - s * 0.24f, cy + s * 0.98f, cx - s * 0.06f, cy + s * 1.40f + legSwing), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.06f, cy + s * 0.98f, cx + s * 0.24f, cy + s * 1.40f - legSwing), 6f, 6f, paint)

        paint.color = Color.parseColor("#792E5C")
        canvas.drawOval(cx - s * 0.30f, cy + s * 1.34f + legSwing, cx + 1f, cy + s * 1.52f + legSwing, paint)
        canvas.drawOval(cx - 1f, cy + s * 1.34f - legSwing, cx + s * 0.30f, cy + s * 1.52f - legSwing, paint)

        val armSwing = if (action == 3) sin((girl.bobPhase * 1.7f).toDouble()).toFloat() * 14f else sin((girl.bobPhase * 1.1f).toDouble()).toFloat() * 8f
        paint.color = Color.parseColor("#FFD8B8")
        canvas.drawRoundRect(RectF(cx - s * 0.62f, cy - s * 0.08f + armSwing, cx - s * 0.32f, cy + s * 0.35f + armSwing), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.32f, cy - s * 0.08f - armSwing, cx + s * 0.62f, cy + s * 0.35f - armSwing), 6f, 6f, paint)

        paint.color = Color.parseColor("#FFD8B8")
        canvas.drawCircle(cx, cy - s * 0.39f, s * 0.36f, paint)

        paint.color = Color.parseColor("#4E3225")
        canvas.drawArc(RectF(cx - s * 0.36f, cy - s * 0.79f, cx + s * 0.36f, cy - s * 0.08f), 180f, 180f, true, paint)
        canvas.drawRoundRect(RectF(cx - s * 0.45f, cy - s * 0.60f, cx - s * 0.30f, cy + s * 0.08f), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx + s * 0.30f, cy - s * 0.60f, cx + s * 0.45f, cy + s * 0.08f), 6f, 6f, paint)

        paint.color = Color.parseColor("#7D2A89")
        canvas.drawCircle(cx - s * 0.12f, cy - s * 0.42f, s * 0.06f, paint)
        canvas.drawCircle(cx + s * 0.12f, cy - s * 0.42f, s * 0.06f, paint)

        strokePaint.color = Color.parseColor("#C8426C")
        strokePaint.strokeWidth = 2.1f
        canvas.drawArc(RectF(cx - s * 0.13f, cy - s * 0.26f, cx + s * 0.13f, cy - s * 0.11f), 0f, 180f, false, strokePaint)

        canvas.restore()
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped).toInt()
        return Color.argb(a, r, g, b)
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
