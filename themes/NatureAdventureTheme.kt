package com.calcvault.emotional.themes

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import kotlin.math.*
import kotlin.random.Random

/**
 * NatureAdventureTheme
 *
 * A little boy and girl playing together in nature.
 *
 * Scenes (rotate every 2 minutes):
 *  1. 🏖️ Beach — waves, sand, seashells, sunset
 *  2. ⛰️ Mountain — peaks, snow, pine trees, clear sky
 *  3. 🌲 Forest — tall trees, fireflies, green light, falling leaves, rain
 *  4. 🌸 Meadow — flowers, butterflies, rolling hills, floating petals
 *
 * Characters:
 *  - Boy: blue shirt, shorts, running/playing
 *  - Girl: red dress, braids, skipping/dancing
 *
 * Natural animations:
 *  - Waves on beach (oscillating)
 *  - Fireflies in forest (random float)
 *  - Butterflies & falling petals in meadow
 *  - Wind effect on trees
 *  - Falling leaves with flutter animation
 *  - Rain in forest scenes
 *  - Characters play naturally — run, jump, chase, rest
 *
 * Usage:
 *   val theme = NatureAdventureTheme(context)
 *   theme.attach(rootFrameLayout)
 *   theme.detach()
 */
class NatureAdventureTheme(private val context: Context) {

    private var themeView : NatureView? = null
    private var attached  = false

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
        themeView = null; attached = false
    }
}

// ── Scenes ─────────────────────────────────────────────────────────────────

enum class NatureScene { BEACH, MOUNTAIN, FOREST, MEADOW }

// ── Firefly / Butterfly particle ───────────────────────────────────────────
private data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var alpha: Float, var alphaDir: Float,
    val color: Int
)

// ── Falling leaf particle ──────────────────────────────────────────────────
private data class FallingLeaf(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    val size: Float,
    val color: Int,
    val leafType: ShapeUtils.LeafType,
    var alpha: Float
)

// ── View ───────────────────────────────────────────────────────────────────

class NatureView(context: Context) : View(context) {

    private val handler   = Handler(Looper.getMainLooper())
    private var running   = false
    private var frame     = 0
    private var sceneTimer = 0

    private var currentScene = NatureScene.BEACH
    private var sceneFade    = 1f   // 1 = full, fades to 0 on transition

    // Characters
    private var boyX  = 0f; private var boyY  = 0f; private var boyTX = 0f; private var boyTY = 0f
    private var girlX = 0f; private var girlY = 0f; private var girlTX = 0f; private var girlTY = 0f
    private var boyFacing  = 1; private var girlFacing = -1
    private var boyJump    = 0f;   private var girlJump  = 0f
    private var boyAction  = 0;    private var girlAction = 0   // 0=walk, 1=jump, 2=idle, 3=dance
    private var actionTimer = 0

    // Wave data
    private val waveOffsets = FloatArray(40) { it * 0.25f }
    private var wavePhase   = 0f

    // Particles (fireflies, butterflies)
    private val particles   = mutableListOf<Particle>()

    // Falling leaves (for forest and autumn scenes)
    private val fallingLeaves = mutableListOf<FallingLeaf>()
    private var leafSpawnTimer = 0

    // Rain animator
    private var rainAnimator: RainAnimator? = null
    private var rainIntensity = 0f  // 0.0 to 1.0, fades in/out

    // Tree sway
    private var windPhase   = 0f

    private val paint       = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Init ───────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0) return
        boyX  = w * 0.3f; boyY  = h * 0.72f; boyTX = boyX;  boyTY = boyY
        girlX = w * 0.6f; girlY = h * 0.72f; girlTX = girlX; girlTY = girlY
        
        // Initialize rain animator
        rainAnimator = RainAnimator(w.toFloat(), h.toFloat(), intensity = 0f)
        
        spawnParticles(w, h)
    }

    private fun spawnParticles(w: Int, h: Int) {
        particles.clear()
        val color = when (currentScene) {
            NatureScene.FOREST  -> Color.parseColor("#AAFFAA")
            NatureScene.MEADOW  -> Color.parseColor("#FFAAFF")
            else -> Color.WHITE
        }
        repeat(15) {
            particles.add(Particle(
                x = Random.nextFloat() * w, y = h * 0.2f + Random.nextFloat() * h * 0.5f,
                vx = (Random.nextFloat() - 0.5f) * 1.2f, vy = (Random.nextFloat() - 0.5f) * 0.8f,
                alpha = Random.nextFloat(), alphaDir = if (Random.nextBoolean()) 0.02f else -0.02f,
                color = color
            ))
        }
    }

    // ── Update ─────────────────────────────────────────────────────────────

    private fun update() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        frame++; sceneTimer++; actionTimer++
        wavePhase  += 0.04f
        windPhase  += 0.02f

        // Change scene every 2 minutes (7200 frames @ 60fps)
        if (sceneTimer >= 7200) {
            sceneTimer = 0
            currentScene = NatureScene.values()[(currentScene.ordinal + 1) % NatureScene.values().size]
            spawnParticles(w.toInt(), h.toInt())
            
            // Trigger rain in forest scenes
            if (currentScene == NatureScene.FOREST && Random.nextFloat() < 0.4f) {
                rainIntensity = 0.6f
            } else {
                rainIntensity = 0f
            }
        }

        // Update rain intensity smoothly
        if (rainIntensity > 0f) {
            rainAnimator?.setIntensity(rainIntensity)
            rainAnimator?.update()
            rainIntensity -= 0.003f  // Fade out slowly
        } else {
            rainAnimator?.clear()
        }

        // Spawn falling leaves in forest
        if (currentScene == NatureScene.FOREST) {
            leafSpawnTimer++
            if (leafSpawnTimer > 5 && fallingLeaves.size < 30) {
                fallingLeaves.add(FallingLeaf(
                    x = Random.nextFloat() * w,
                    y = -20f,
                    vx = (Random.nextFloat() - 0.5f) * 0.8f,
                    vy = 0.8f + Random.nextFloat() * 0.3f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 15f,
                    size = Random.nextFloat() * 8f + 4f,
                    color = Color.parseColor(listOf("#2E7D32", "#388E3C", "#66BB6A", "#81C784").random()),
                    leafType = ShapeUtils.LeafType.values().random(),
                    alpha = 0.8f
                ))
                leafSpawnTimer = 0
            }
        } else {
            leafSpawnTimer = 0
        }

        // Update falling leaves
        val leavesToRemove = mutableListOf<FallingLeaf>()
        for (leaf in fallingLeaves) {
            leaf.x += leaf.vx
            leaf.y += leaf.vy
            leaf.rotation += leaf.rotationSpeed
            leaf.vy = (leaf.vy + 0.05f).coerceAtMost(2f)  // Gravity
            
            if (leaf.y > h) {
                leavesToRemove.add(leaf)
            }
        }
        fallingLeaves.removeAll(leavesToRemove)

        // Update character actions
        if (actionTimer >= 180 + Random.nextInt(120)) {
            actionTimer = 0
            boyAction  = Random.nextInt(4)
            girlAction = Random.nextInt(4)
            // New target positions
            boyTX  = w * 0.1f + Random.nextFloat() * w * 0.4f
            boyTY  = h * 0.70f + Random.nextFloat() * h * 0.06f
            girlTX = w * 0.45f + Random.nextFloat() * w * 0.4f
            girlTY = h * 0.70f + Random.nextFloat() * h * 0.06f
            // Sometimes chase each other
            if (Random.nextFloat() < 0.25f) { girlTX = boyX + 60f; girlTY = boyY }
        }

        // Move characters
        moveChar(true,  w, h)
        moveChar(false, w, h)

        // Jump animation
        if (boyAction == 1)  boyJump  = abs(sin(frame * 0.15f)) * 28f else boyJump  = boyJump  * 0.85f
        if (girlAction == 1) girlJump = abs(sin(frame * 0.13f)) * 22f else girlJump = girlJump * 0.85f

        // Update particles
        for (p in particles) {
            p.x += p.vx; p.y += p.vy
            p.alpha += p.alphaDir
            if (p.alpha > 1f || p.alpha < 0f) { p.alphaDir = -p.alphaDir; p.alpha = p.alpha.coerceIn(0f, 1f) }
            if (p.x < 0 || p.x > w) p.vx = -p.vx
            if (p.y < h * 0.1f || p.y > h * 0.75f) p.vy = -p.vy
        }
    }

    private fun moveChar(isBoy: Boolean, w: Float, h: Float) {
        val tx = if (isBoy) boyTX  else girlTX
        val ty = if (isBoy) boyTY  else girlTY
        val cx = if (isBoy) boyX   else girlX
        val cy = if (isBoy) boyY   else girlY
        val dx = tx - cx; val dy = ty - cy
        val dist = sqrt(dx * dx + dy * dy)
        val speed = if (isBoy) 1.8f else 1.5f
        if (dist > 2f) {
            if (isBoy) { boyX  += (dx / dist) * speed; boyY  += (dy / dist) * speed; boyFacing  = if (dx > 0) 1 else -1 }
            else        { girlX += (dx / dist) * speed; girlY += (dy / dist) * speed; girlFacing = if (dx > 0) 1 else -1 }
        }
    }

    // ── Draw ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return

        when (currentScene) {
            NatureScene.BEACH    -> drawBeach(canvas, w, h)
            NatureScene.MOUNTAIN -> drawMountain(canvas, w, h)
            NatureScene.FOREST   -> drawForest(canvas, w, h)
            NatureScene.MEADOW   -> drawMeadow(canvas, w, h)
        }

        drawParticles(canvas)
        drawFallingLeaves(canvas)
        drawBoy(canvas, boyX, boyY - boyJump, boyFacing, boyAction)
        drawGirl(canvas, girlX, girlY - girlJump, girlFacing, girlAction)
        drawRain(canvas)
    }

    // ── Scene: Beach ───────────────────────────────────────────────────────
    private fun drawBeach(canvas: Canvas, w: Float, h: Float) {
        // Sunset sky
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.65f,
            intArrayOf(Color.parseColor("#FF7043"), Color.parseColor("#FFB74D"), Color.parseColor("#FFF9C4")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.65f, paint); paint.shader = null

        // Clouds in the sky
        ShapeUtils.drawCloudGradient(canvas, paint, w * 0.2f, h * 0.2f, 80f, Color.WHITE, shadowAlpha = 50)
        ShapeUtils.drawCloudWispy(canvas, paint, w * 0.75f, h * 0.15f, 70f, alpha = 200)

        // Sun
        paint.color = Color.parseColor("#FFD700"); paint.style = Paint.Style.FILL
        canvas.drawCircle(w * 0.8f, h * 0.2f, 45f, paint)

        // Sand
        paint.shader = LinearGradient(0f, h * 0.65f, 0f, h,
            intArrayOf(Color.parseColor("#F4D03F"), Color.parseColor("#D4AC0D")),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.65f, w, h, paint); paint.shader = null

        // Waves
        paint.color = Color.parseColor("#4FC3F7"); paint.style = Paint.Style.FILL
        val wavePath = Path()
        wavePath.moveTo(0f, h * 0.68f)
        for (i in waveOffsets.indices) {
            val x = w * i / waveOffsets.size.toFloat()
            val y = h * 0.68f + sin((wavePhase + i * 0.5f).toDouble()).toFloat() * 8f
            waveOffsets[i] = y
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        wavePath.lineTo(w, h * 0.72f); wavePath.lineTo(0f, h * 0.72f); wavePath.close()
        canvas.drawPath(wavePath, paint)

        // Seashells
        paint.color = Color.parseColor("#F8BBD9")
        listOf(0.15f, 0.45f, 0.75f, 0.88f).forEach { xf ->
            canvas.drawOval(w * xf - 8f, h * 0.78f - 5f, w * xf + 8f, h * 0.78f + 5f, paint)
        }
    }

    // ── Scene: Mountain ────────────────────────────────────────────────────
    private fun drawMountain(canvas: Canvas, w: Float, h: Float) {
        // Clear blue sky
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.6f,
            intArrayOf(Color.parseColor("#1565C0"), Color.parseColor("#42A5F5"), Color.parseColor("#B3E5FC")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.6f, paint); paint.shader = null

        // Fluffy clouds in mountains
        ShapeUtils.drawCloudPuffy(canvas, paint, w * 0.15f, h * 0.15f, 75f, Color.WHITE)
        ShapeUtils.drawCloudPuffy(canvas, paint, w * 0.65f, h * 0.22f, 80f, Color.WHITE)
        ShapeUtils.drawCloudWispy(canvas, paint, w * 0.45f, h * 0.08f, 60f, alpha = 220)

        // Mountains (back)
        paint.color = Color.parseColor("#546E7A"); paint.style = Paint.Style.FILL
        drawMountainPeak(canvas, w * 0.2f, h * 0.45f, w * 0.5f, h * 0.1f)
        drawMountainPeak(canvas, w * 0.7f, h * 0.45f, w * 0.45f, h * 0.08f)
        // Snow caps
        paint.color = Color.WHITE
        drawSnowCap(canvas, w * 0.2f, h * 0.1f, w * 0.12f)
        drawSnowCap(canvas, w * 0.7f, h * 0.08f, w * 0.10f)

        // Green ground
        paint.shader = LinearGradient(0f, h * 0.6f, 0f, h,
            intArrayOf(Color.parseColor("#388E3C"), Color.parseColor("#1B5E20")),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.6f, w, h, paint); paint.shader = null

        // Pine trees
        paint.color = Color.parseColor("#1B5E20"); paint.shader = null; paint.style = Paint.Style.FILL
        val sway = sin(windPhase.toDouble()).toFloat() * 3f
        drawPineTree(canvas, w * 0.08f + sway, h * 0.68f, 30f)
        drawPineTree(canvas, w * 0.18f - sway, h * 0.65f, 38f)
        drawPineTree(canvas, w * 0.82f + sway, h * 0.67f, 32f)
        drawPineTree(canvas, w * 0.91f - sway, h * 0.64f, 40f)
    }

    private fun drawMountainPeak(canvas: Canvas, cx: Float, baseY: Float, baseW: Float, tipY: Float) {
        val path = Path().apply {
            moveTo(cx - baseW / 2, baseY); lineTo(cx, tipY); lineTo(cx + baseW / 2, baseY); close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSnowCap(canvas: Canvas, cx: Float, tipY: Float, size: Float) {
        val path = Path().apply {
            moveTo(cx - size / 2, tipY + size * 0.6f); lineTo(cx, tipY); lineTo(cx + size / 2, tipY + size * 0.6f); close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPineTree(canvas: Canvas, cx: Float, baseY: Float, size: Float) {
        paint.color = Color.parseColor("#2E7D32")
        for (i in 0..2) {
            val path = Path().apply {
                val w2 = size * (1f - i * 0.2f) * (1.2f - i * 0.1f)
                val y  = baseY - i * size * 0.35f
                moveTo(cx - w2, y); lineTo(cx, y - size * 0.55f); lineTo(cx + w2, y); close()
            }
            canvas.drawPath(path, paint)
        }
        // trunk
        paint.color = Color.parseColor("#5D4037")
        canvas.drawRect(cx - 4f, baseY - size * 0.1f, cx + 4f, baseY, paint)
    }

    // ── Scene: Forest ──────────────────────────────────────────────────────
    private fun drawForest(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(Color.parseColor("#1B5E20"), Color.parseColor("#2E7D32"), Color.parseColor("#388E3C")),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null

        // Light rays from above
        paint.color = Color.argb(30, 255, 255, 150)
        for (i in 0..5) {
            val x = w * 0.1f * i + sin((windPhase + i).toDouble()).toFloat() * 20f
            canvas.drawLine(x, 0f, x + 50f, h * 0.8f, paint.also { it.strokeWidth = 15f; it.style = Paint.Style.STROKE })
        }
        paint.style = Paint.Style.FILL

        // Big trees (sides)
        val sway = sin(windPhase.toDouble()).toFloat()
        drawForestTree(canvas, w * 0.05f, h, 60f, sway)
        drawForestTree(canvas, w * 0.20f, h, 50f, -sway)
        drawForestTree(canvas, w * 0.75f, h, 55f, sway)
        drawForestTree(canvas, w * 0.90f, h, 65f, -sway)

        // Ground
        paint.color = Color.parseColor("#1A4A1A")
        canvas.drawRect(0f, h * 0.75f, w, h, paint)
        // Moss/flowers
        paint.color = Color.parseColor("#66BB6A")
        for (i in 0..8) { canvas.drawCircle(w * 0.1f * i + 20f, h * 0.77f, 12f, paint) }
    }

    private fun drawForestTree(canvas: Canvas, baseX: Float, baseY: Float, size: Float, sway: Float) {
        paint.color = Color.parseColor("#5D4037")
        canvas.drawRect(baseX - size * 0.08f + sway * 3f, baseY - size * 1.5f, baseX + size * 0.08f, baseY, paint)
        paint.color = Color.parseColor("#1B5E20")
        for (i in 0..2) {
            val y  = baseY - size * (0.9f + i * 0.45f)
            val w2 = size * (0.7f - i * 0.15f)
            val sx = sway * (i + 1) * 2f
            val path = Path().apply {
                moveTo(baseX - w2 + sx, y); lineTo(baseX + sx, y - size * 0.6f); lineTo(baseX + w2 + sx, y); close()
            }
            canvas.drawPath(path, paint)
        }
    }

    // ── Scene: Meadow ──────────────────────────────────────────────────────
    private fun drawMeadow(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.6f,
            intArrayOf(Color.parseColor("#81D4FA"), Color.parseColor("#B3E5FC")),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.6f, paint); paint.shader = null

        // Fluffy clouds in bright sky
        ShapeUtils.drawCloudPuffy(canvas, paint, w * 0.25f, h * 0.12f, 85f, Color.WHITE)
        ShapeUtils.drawCloudPuffy(canvas, paint, w * 0.78f, h * 0.18f, 75f, Color.WHITE)

        // Rolling hills
        paint.color = Color.parseColor("#66BB6A"); paint.shader = null
        val hillPath = Path().apply {
            moveTo(0f, h * 0.65f)
            cubicTo(w * 0.25f, h * 0.45f, w * 0.5f, h * 0.55f, w * 0.75f, h * 0.48f)
            cubicTo(w * 0.85f, h * 0.45f, w * 0.95f, h * 0.55f, w, h * 0.60f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        canvas.drawPath(hillPath, paint)

        // Flowers
        val flowerColors = intArrayOf(
            Color.parseColor("#FF4081"), Color.parseColor("#FFD740"),
            Color.parseColor("#69F0AE"), Color.parseColor("#40C4FF"),
            Color.parseColor("#FFFFFF")
        )
        for (i in 0..20) {
            val fx = w * 0.05f + i * w * 0.045f + sin(i * 1.3f) * 15f
            val fy = h * 0.62f + sin(i * 0.9f + windPhase) * 6f
            paint.color = Color.parseColor("#8D6E63")
            canvas.drawLine(fx, fy, fx + sin(windPhase + i) * 4f, fy - 20f, paint.also { it.strokeWidth = 2f; it.style = Paint.Style.STROKE })
            paint.style = Paint.Style.FILL
            paint.color = flowerColors[i % flowerColors.size]
            canvas.drawCircle(fx + sin(windPhase + i) * 4f, fy - 22f, 7f, paint)
        }
    }

    // ── Particles ──────────────────────────────────────────────────────────
    private fun drawParticles(canvas: Canvas) {
        if (currentScene != NatureScene.FOREST && currentScene != NatureScene.MEADOW) return
        for (p in particles) {
            paint.color = Color.argb((p.alpha * 200).toInt(), Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            paint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(p.x, p.y, 4f, paint)
        }
        paint.maskFilter = null
    }

    // ── Falling Leaves ────────────────────────────────────────────────────
    private fun drawFallingLeaves(canvas: Canvas) {
        if (currentScene != NatureScene.FOREST) return
        paint.style = Paint.Style.FILL
        
        for (leaf in fallingLeaves) {
            ShapeUtils.drawFallingLeaf(
                canvas, paint,
                leaf.x, leaf.y,
                leaf.rotation,
                leaf.size,
                leaf.color,
                leaf.leafType,
                alpha = (leaf.alpha * 200).toInt()
            )
        }
    }

    // ── Rain ───────────────────────────────────────────────────────────────
    private fun drawRain(canvas: Canvas) {
        if (rainAnimator == null || rainIntensity <= 0f) return
        paint.style = Paint.Style.STROKE
        rainAnimator?.draw(canvas, paint)
    }

    // ── Draw Boy ───────────────────────────────────────────────────────────
    private fun drawBoy(canvas: Canvas, cx: Float, cy: Float, facing: Int, action: Int) {
        val s = 32f
        canvas.save()
        if (facing == -1) canvas.scale(-1f, 1f, cx, cy)

        val legSwing = if (action == 0) sin(frame * 0.2f) * 12f else 0f

        // Legs
        paint.color = Color.parseColor("#1565C0"); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(cx - s*0.28f, cy+s*0.5f, cx-s*0.05f, cy+s*1.15f+legSwing), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx+s*0.05f, cy+s*0.5f, cx+s*0.28f, cy+s*1.15f-legSwing), 6f, 6f, paint)
        // Shoes
        paint.color = Color.parseColor("#333333")
        canvas.drawOval(cx-s*0.32f, cy+s*1.1f+legSwing, cx, cy+s*1.28f+legSwing, paint)
        canvas.drawOval(cx, cy+s*1.1f-legSwing, cx+s*0.32f, cy+s*1.28f-legSwing, paint)
        // Body
        paint.color = Color.parseColor("#1E88E5")
        canvas.drawRoundRect(RectF(cx-s*0.38f, cy-s*0.1f, cx+s*0.38f, cy+s*0.55f), 8f, 8f, paint)
        // Arms
        val armSwing = if (action == 0) sin(frame * 0.2f + PI.toFloat()) * 12f else 0f
        paint.color = Color.parseColor("#FFDAB9")
        canvas.drawRoundRect(RectF(cx-s*0.65f, cy-s*0.05f+armSwing, cx-s*0.35f, cy+s*0.45f+armSwing), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(cx+s*0.35f, cy-s*0.05f-armSwing, cx+s*0.65f, cy+s*0.45f-armSwing), 6f, 6f, paint)
        // Head
        paint.color = Color.parseColor("#FFDAB9")
        canvas.drawCircle(cx, cy-s*0.35f, s*0.38f, paint)
        // Hair
        paint.color = Color.parseColor("#3E2723")
        canvas.drawArc(RectF(cx-s*0.38f, cy-s*0.78f, cx+s*0.38f, cy-s*0.1f), 180f, 180f, true, paint)
        // Eyes
        paint.color = Color.parseColor("#1A237E")
        canvas.drawCircle(cx-s*0.13f, cy-s*0.38f, s*0.07f, paint)
        canvas.drawCircle(cx+s*0.13f, cy-s*0.38f, s*0.07f, paint)
        // Smile
        paint.color = Color.parseColor("#C62828"); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawArc(RectF(cx-s*0.14f, cy-s*0.22f, cx+s*0.14f, cy-s*0.08f), 0f, 180f, false, paint)
        paint.style = Paint.Style.FILL

        canvas.restore()
    }

    // ── Draw Girl ──────────────────────────────────────────────────────────
    private fun drawGirl(canvas: Canvas, cx: Float, cy: Float, facing: Int, action: Int) {
        val s = 30f
        canvas.save()
        if (facing == -1) canvas.scale(-1f, 1f, cx, cy)

        val danceOff = if (action == 3) sin(frame * 0.18f) * 8f else 0f
        val legSwing = if (action == 0) sin(frame * 0.22f) * 10f else 0f

        // Dress
        paint.color = Color.parseColor("#E53935"); paint.style = Paint.Style.FILL
        val dressPath = Path().apply {
            moveTo(cx-s*0.32f, cy+s*0.05f+danceOff)
            lineTo(cx+s*0.32f, cy+s*0.05f-danceOff)
            lineTo(cx+s*0.50f, cy+s*1.0f)
            lineTo(cx-s*0.50f, cy+s*1.0f); close()
        }
        canvas.drawPath(dressPath, paint)
        // Legs
        paint.color = Color.parseColor("#FFDAB9")
        canvas.drawRoundRect(RectF(cx-s*0.24f, cy+s*0.95f, cx-s*0.06f, cy+s*1.4f+legSwing), 5f, 5f, paint)
        canvas.drawRoundRect(RectF(cx+s*0.06f, cy+s*0.95f, cx+s*0.24f, cy+s*1.4f-legSwing), 5f, 5f, paint)
        // Shoes
        paint.color = Color.parseColor("#880E4F")
        canvas.drawOval(cx-s*0.30f, cy+s*1.35f+legSwing, cx+s*0.02f, cy+s*1.52f+legSwing, paint)
        canvas.drawOval(cx-s*0.02f, cy+s*1.35f-legSwing, cx+s*0.30f, cy+s*1.52f-legSwing, paint)
        // Arms
        val armSwing = if (action == 3) sin(frame * 0.18f + PI.toFloat()) * 18f else sin(frame * 0.22f) * 8f
        paint.color = Color.parseColor("#FFDAB9")
        canvas.drawRoundRect(RectF(cx-s*0.58f, cy-s*0.08f+armSwing, cx-s*0.30f, cy+s*0.35f+armSwing), 5f, 5f, paint)
        canvas.drawRoundRect(RectF(cx+s*0.30f, cy-s*0.08f-armSwing, cx+s*0.58f, cy+s*0.35f-armSwing), 5f, 5f, paint)
        // Head
        canvas.drawCircle(cx, cy-s*0.38f, s*0.36f, paint)
        // Braids
        paint.color = Color.parseColor("#4E342E")
        canvas.drawArc(RectF(cx-s*0.36f, cy-s*0.78f, cx+s*0.36f, cy-s*0.10f), 180f, 180f, true, paint)
        canvas.drawRoundRect(RectF(cx-s*0.44f, cy-s*0.60f, cx-s*0.30f, cy+s*0.10f), 5f, 5f, paint)
        canvas.drawRoundRect(RectF(cx+s*0.30f, cy-s*0.60f, cx+s*0.44f, cy+s*0.10f), 5f, 5f, paint)
        // Hair ribbon
        paint.color = Color.parseColor("#FFC107")
        canvas.drawCircle(cx-s*0.36f, cy-s*0.30f, s*0.08f, paint)
        canvas.drawCircle(cx+s*0.36f, cy-s*0.30f, s*0.08f, paint)
        // Eyes
        paint.color = Color.parseColor("#4A148C")
        canvas.drawCircle(cx-s*0.12f, cy-s*0.42f, s*0.07f, paint)
        canvas.drawCircle(cx+s*0.12f, cy-s*0.42f, s*0.07f, paint)
        // Smile
        paint.color = Color.parseColor("#AD1457"); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawArc(RectF(cx-s*0.13f, cy-s*0.27f, cx+s*0.13f, cy-s*0.12f), 0f, 180f, false, paint)
        paint.style = Paint.Style.FILL

        canvas.restore()
    }

    // ── Animation loop ─────────────────────────────────────────────────────
    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            update(); invalidate(); handler.postDelayed(this, 16)
        }
    }

    fun startAnimation() { running = true; handler.post(frameRunnable) }
    fun stopAnimation()  { running = false; handler.removeCallbacks(frameRunnable) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopAnimation() }
}
