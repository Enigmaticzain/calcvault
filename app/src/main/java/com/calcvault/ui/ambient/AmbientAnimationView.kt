package com.calcvault.ui.ambient

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.calcvault.utils.SessionManager
import java.util.Calendar
import kotlin.math.*
import kotlin.random.Random

/**
 * THE AMBIENT MULTI-THEME ENGINE V3 (DEFINITIVE EDITION)
 * * Theme 1: CINEMATIC_NATURE (Alto's Adventure inspired)
 * - Minimalist procedural hills, atmospheric gradients, and smooth parallax.
 * * Theme 2: EMOTIONAL_COMPANION (Doraemon-inspired)
 * - Peaceful suburban life, rounded friendly silhouettes, pastel time cycles.
 */
class AmbientAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Theme { 
        CINEMATIC_NATURE, EMOTIONAL_COMPANION,
        SANCTUARY_STARGAZING, SANCTUARY_RAINYCAFE, SANCTUARY_SUNRISE,
        SANCTUARY_AUTUMNPARK, SANCTUARY_SUNSETBEACH, SANCTUARY_PEACEFULSNOW,
        SANCTUARY_COUCHFIREPLACE
    }

    companion object {
        fun configureCalmPreset(view: AmbientAnimationView) {
            view.currentTheme = Theme.EMOTIONAL_COMPANION
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            view.currentTimeOfDay = when (hour) {
                in 5..11 -> TimeOfDay.MORNING
                in 12..16 -> TimeOfDay.DAY
                in 17..20 -> TimeOfDay.EVENING
                else -> TimeOfDay.NIGHT
            }
        }

        /** Value from [com.calcvault.auth.UnlockManager.getAmbientScene]: COMPANION or NATURE. */
        fun applyPersistedScene(view: AmbientAnimationView, scene: String) {
            when (scene.uppercase()) {
                "NATURE" -> {
                    view.currentTheme = Theme.CINEMATIC_NATURE
                    view.currentWeather = Weather.values().random()
                }
                else -> configureCalmPreset(view)
            }
        }
    }

    var currentTheme = Theme.CINEMATIC_NATURE
        set(value) {
            field = value
            initEnvironment()
            updateThemePaints()
        }

    private val noise = NoiseGenerator()
    private val particles = mutableListOf<Particle>()
    private val birds = mutableListOf<Bird>()
    private val stars = mutableListOf<Star>()
    private val splashes = mutableListOf<Splash>()
    private val clouds = mutableListOf<Cloud>()
    private val fireflies = mutableListOf<Firefly>()
    private val trees = mutableListOf<Tree>()
    private val puddles = mutableListOf<Puddle>()
    private val shootingStars = mutableListOf<ShootingStar>()
    private val aurora = AuroraCurtain(Random.nextDouble(1000.0))
    private val wind = WindState()

    // Paints
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL) }
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND }
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND }
    private val silhouettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val scarfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND }
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f }
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val celestialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val shootingStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fireflyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fireflyGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val auroraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val puddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f }
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pulseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }

    // Path Cache
    private val bgHillPath = Path()
    private val mgHillPath = Path()
    private val fgHillPath = Path()
    private val scarfPath = Path()
    private val pinePath = Path().apply { moveTo(0f, -160f); lineTo(40f, 0f); lineTo(-40f, 0f); close() }
    private val suburbanPath = Path()

    private val bgHillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val mgHillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fgHillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Dynamic State
    private var lastFrameTime = System.currentTimeMillis()
    private var isRunning = true
    private var holdFactor = 0f
    private var targetHoldFactor = 0f
    private var swipeWindBoost = 0f
    private var errorShakeFactor = 0f
    private var lightningIntensity = 0f
    private var parallaxX = 0f
    private var targetParallaxX = 0f

    enum class Weather(
        val color: Int,
        val scarfColor: Int,
        val skyTop: Int,
        val skyBot: Int,
        val hillColor: Int,
        val celestialColor: Int,
        val fireflyColor: Int,
        val auroraColor: Int
    ) {
        LEAVES(
            Color.parseColor("#99D35400"), // Orange-ish leaves
            Color.parseColor("#AAFFC107"), Color.parseColor("#74B9FF"), // Bright sky top
            Color.parseColor("#81ECEC"), // Bright sky bot
            Color.parseColor("#2D3436"), // Dark minimalist hills
            Color.parseColor("#22FFD54F"), Color.parseColor("#66FFEB3B"), Color.parseColor("#00000000")
        ),
        SNOW(
            Color.WHITE, Color.parseColor("#AAFF5252"), Color.parseColor("#0F0C29"), // Night sky top
            Color.parseColor("#302B63"), // Night sky bot
            Color.parseColor("#24243E"), // Deep blue hills
            Color.parseColor("#55FFFFFF"), Color.parseColor("#00FFFFFF"), Color.parseColor("#154CAF50")
        ),
        RAIN(
            Color.parseColor("#88FFFFFF"), Color.parseColor("#AA00BCD4"), Color.parseColor("#3D3D3D"), // Storm sky top
            Color.parseColor("#1B1B1B"), // Storm sky bot
            Color.parseColor("#000000"), // Pitch black hills
            Color.parseColor("#11FFFFFF"), Color.parseColor("#4481D4FA"), Color.parseColor("#00000000")
        )
    }

    var currentWeather = Weather.values().random()
        set(value) {
            field = value
            updateThemePaints()
            initEnvironment()
        }

    var currentTimeOfDay = TimeOfDay.values().random()
        set(value) {
            field = value
            updateThemePaints()
        }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                swipeWindBoost = (vx / 600f).coerceIn(-30f, 30f)
                return true
            }
            override fun onDown(e: MotionEvent): Boolean {
                targetHoldFactor = 0.3f
                return true
            }
        }
    )

    init {
        updateThemePaints()
        post { initEnvironment() }
    }

    private fun updateThemePaints() {
        if (currentTheme == Theme.CINEMATIC_NATURE) {
            updatePaintsForNature()
        } else if (currentTheme == Theme.EMOTIONAL_COMPANION) {
            updatePaintsForCompanion()
        } else {
            updatePaintsForSanctuary()
        }
    }

    private fun updatePaintsForNature() {
        val c = currentWeather.color
        val h = currentWeather.hillColor
        leafPaint.color = c; snowPaint.color = c; rainPaint.color = c
        scarfPaint.color = currentWeather.scarfColor; splashPaint.color = c
        celestialPaint.color = currentWeather.celestialColor; shootingStarPaint.color = c
        fireflyPaint.color = currentWeather.fireflyColor; auroraPaint.color = currentWeather.auroraColor
        puddlePaint.color = Color.argb(15, Color.red(c), Color.green(c), Color.blue(c))
        ripplePaint.color = Color.argb(40, Color.red(c), Color.green(c), Color.blue(c))
        fogPaint.color = Color.argb(25, Color.red(c), Color.green(c), Color.blue(c))
        grassPaint.color = Color.argb(60, Color.red(h), Color.green(h), Color.blue(h))
        cloudPaint.color = Color.argb(12, 255, 255, 255)

        bgHillPaint.color = Color.argb(255, (Color.red(h) * 0.4f).toInt(), (Color.green(h) * 0.4f).toInt(), (Color.blue(h) * 0.4f).toInt())
        mgHillPaint.color = Color.argb(255, (Color.red(h) * 0.7f).toInt(), (Color.green(h) * 0.7f).toInt(), (Color.blue(h) * 0.7f).toInt())
        fgHillPaint.color = h
        silhouettePaint.color = h

        updateSkyGradient(currentWeather.skyTop, currentWeather.skyBot)
    }

    private fun updatePaintsForCompanion() {
        val themeColors = when (currentTimeOfDay) {
            TimeOfDay.MORNING -> listOf("#FFF9C4", "#FFCCBC", "#A5D6A7", "#455A64", "#FFAB91", "#FFF59D")
            TimeOfDay.DAY -> listOf("#E3F2FD", "#BBDEFB", "#C8E6C9", "#546E7A", "#81C784", "#FFF176")
            TimeOfDay.EVENING -> listOf("#FFCC80", "#FF8A65", "#81C784", "#37474F", "#FFD54F", "#FF7043")
            TimeOfDay.NIGHT -> listOf("#1A237E", "#000000", "#1B5E20", "#212121", "#E0E0E0", "#FFFFFF")
        }

        val skyT = Color.parseColor(themeColors[0])
        val skyB = Color.parseColor(themeColors[1])
        val gC = Color.parseColor(themeColors[2])
        val silC = Color.parseColor(themeColors[3])
        val partC = Color.parseColor(themeColors[4])
        val celC = Color.parseColor(themeColors[5])

        silhouettePaint.color = silC
        leafPaint.color = partC
        celestialPaint.color = celC
        fireflyPaint.color = partC
        cloudPaint.color = Color.argb(80, 255, 255, 255)

        fgHillPaint.color = gC
        mgHillPaint.color = Color.argb(180, Color.red(gC), Color.green(gC), Color.blue(gC))
        bgHillPaint.color = Color.argb(120, Color.red(gC), Color.green(gC), Color.blue(gC))

        updateSkyGradient(skyT, skyB)
    }

    private fun updatePaintsForSanctuary() {
        val themeEngineTheme = when (currentTheme) {
            Theme.SANCTUARY_STARGAZING -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_STARGAZING
            Theme.SANCTUARY_RAINYCAFE -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_RAINYCAFE
            Theme.SANCTUARY_SUNRISE -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_SUNRISE
            Theme.SANCTUARY_AUTUMNPARK -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_AUTUMNPARK
            Theme.SANCTUARY_SUNSETBEACH -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_SUNSETBEACH
            Theme.SANCTUARY_PEACEFULSNOW -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_PEACEFULSNOW
            Theme.SANCTUARY_COUCHFIREPLACE -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_COUCHFIREPLACE
            else -> com.calcvault.emotional.ThemeEngine.THEME_SANCTUARY_STARGAZING
        }

        val h = themeEngineTheme.surfaceColor
        val c = themeEngineTheme.accentColor
        
        leafPaint.color = c; snowPaint.color = c; rainPaint.color = c; emberPaint.color = c
        splashPaint.color = c; celestialPaint.color = themeEngineTheme.primaryText
        shootingStarPaint.color = c; fireflyPaint.color = c; auroraPaint.color = Color.argb(40, Color.red(c), Color.green(c), Color.blue(c))
        puddlePaint.color = Color.argb(15, Color.red(c), Color.green(c), Color.blue(c))
        ripplePaint.color = Color.argb(40, Color.red(c), Color.green(c), Color.blue(c))
        fogPaint.color = Color.argb(25, Color.red(h), Color.green(h), Color.blue(h))
        grassPaint.color = Color.argb(60, Color.red(h), Color.green(h), Color.blue(h))
        cloudPaint.color = Color.argb(12, 255, 255, 255)
        pulseRingPaint.color = Color.argb(100, Color.red(c), Color.green(c), Color.blue(c))

        bgHillPaint.color = Color.argb(255, (Color.red(h) * 0.4f).toInt(), (Color.green(h) * 0.4f).toInt(), (Color.blue(h) * 0.4f).toInt())
        mgHillPaint.color = Color.argb(255, (Color.red(h) * 0.7f).toInt(), (Color.green(h) * 0.7f).toInt(), (Color.blue(h) * 0.7f).toInt())
        fgHillPaint.color = h
        silhouettePaint.color = h

        updateSkyGradient(themeEngineTheme.backgroundStart, themeEngineTheme.backgroundEnd)
    }

    private fun updateSkyGradient(top: Int, bot: Int) {
        if (width > 0 && height > 0) {
            skyPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bot, Shader.TileMode.CLAMP)
        }
    }

    private fun initEnvironment() {
        val w = if (width > 0) width.toFloat() else 1080f
        val h = if (height > 0) height.toFloat() else 1920f

        particles.clear(); birds.clear(); stars.clear(); splashes.clear(); clouds.clear(); fireflies.clear(); trees.clear(); puddles.clear(); shootingStars.clear()

        repeat(80) { stars.add(Star(Random.nextFloat() * w, Random.nextFloat() * h * 0.5f, 0.5f + Random.nextFloat() * 1.2f, Random.nextDouble() * 100)) }
        if (currentTheme == Theme.CINEMATIC_NATURE) {
            repeat(3) { clouds.add(Cloud(Random.nextFloat() * w, 50f + Random.nextFloat() * 300f, 450f + Random.nextFloat() * 600f, 180f + Random.nextFloat() * 80f, 2f + Random.nextFloat() * 6f, Random.nextDouble() * 1000)) }
            repeat(8) { trees.add(Tree(Random.nextFloat() * w, h * 0.8f + Random.nextFloat() * h * 0.05f, 1.0f + Random.nextFloat() * 1.8f, 0)) }
            val pCount = when (currentWeather) { Weather.LEAVES -> 60; Weather.SNOW -> 200; Weather.RAIN -> 300 }
            repeat(pCount) { particles.add(createParticle(Random.nextFloat() * w, Random.nextFloat() * h)) }
            spawnBirdFlock(w, h, 6, 14)
            generateParallaxHills(w, h)
        } else if (currentTheme == Theme.EMOTIONAL_COMPANION) {
            repeat(3) { clouds.add(Cloud(Random.nextFloat() * w, 50f + Random.nextFloat() * 300f, 450f + Random.nextFloat() * 600f, 180f + Random.nextFloat() * 80f, 2f + Random.nextFloat() * 6f, Random.nextDouble() * 1000)) }
            repeat(20) { particles.add(Leaf(Random.nextFloat() * w, Random.nextFloat() * h, 15f + Random.nextFloat() * 10f, 0.6f + Random.nextFloat() * 0.2f)) }
            repeat(10) { fireflies.add(Firefly(Random.nextFloat() * w, h * 0.4f + Random.nextFloat() * h * 0.5f, Random.nextFloat() * w, h * 0.4f + Random.nextFloat() * h * 0.5f)) }
            spawnBirdFlock(w, h, 2, 6)
            generateSuburbanSilhouette(w, h)
            generateParallaxHills(w, h)
        } else {
            when (currentTheme) {
                Theme.SANCTUARY_STARGAZING -> {
                    repeat(120) { stars.add(Star(Random.nextFloat() * w, Random.nextFloat() * h, 0.5f + Random.nextFloat() * 2f, Random.nextDouble() * 100)) }
                    repeat(20) { fireflies.add(Firefly(Random.nextFloat() * w, Random.nextFloat() * h, Random.nextFloat() * w, Random.nextFloat() * h)) }
                }
                Theme.SANCTUARY_RAINYCAFE -> {
                    repeat(200) { particles.add(Rain(Random.nextFloat() * w, Random.nextFloat() * h, 2.5f, 0.3f + Random.nextFloat() * 0.4f)) }
                    repeat(30) { puddles.add(Puddle(Random.nextFloat() * w, h * 0.78f + Random.nextFloat() * 200f, 40f + Random.nextFloat() * 100f, 15f + Random.nextFloat() * 30f)) }
                }
                Theme.SANCTUARY_SUNRISE -> {
                    repeat(4) { clouds.add(Cloud(Random.nextFloat() * w, 50f + Random.nextFloat() * 200f, 300f + Random.nextFloat() * 400f, 100f + Random.nextFloat() * 50f, 1f + Random.nextFloat() * 3f, Random.nextDouble() * 1000)) }
                    spawnBirdFlock(w, h, 10, 25)
                }
                Theme.SANCTUARY_AUTUMNPARK -> {
                    repeat(80) { particles.add(Leaf(Random.nextFloat() * w, Random.nextFloat() * h, 10f + Random.nextFloat() * 20f, 0.5f + Random.nextFloat() * 0.5f)) }
                }
                Theme.SANCTUARY_SUNSETBEACH -> {
                    spawnBirdFlock(w, h, 5, 12)
                }
                Theme.SANCTUARY_PEACEFULSNOW -> {
                    repeat(300) { particles.add(Snow(Random.nextFloat() * w, Random.nextFloat() * h, 2f + Random.nextFloat() * 10f, 0.4f + Random.nextFloat() * 0.6f)) }
                    repeat(10) { trees.add(Tree(Random.nextFloat() * w, h * 0.8f + Random.nextFloat() * h * 0.05f, 1.0f + Random.nextFloat() * 1.8f, 0)) }
                }
                Theme.SANCTUARY_COUCHFIREPLACE -> {
                    repeat(100) { particles.add(Ember(Random.nextFloat() * w, Random.nextFloat() * h, 3f + Random.nextFloat() * 8f, 0.5f + Random.nextFloat() * 0.5f)) }
                }
                else -> {}
            }
            generateParallaxHills(w, h)
        }
        updateThemePaints()
    }

    private fun generateSuburbanSilhouette(w: Float, h: Float) {
        suburbanPath.reset()
        suburbanPath.moveTo(0f, h)
        suburbanPath.lineTo(0f, h * 0.88f)
        for (x in 0..w.toInt() step 180) {
            val rx = x.toFloat()
            val hType = Random.nextInt(3)
            when (hType) {
                0 -> {
                    suburbanPath.lineTo(rx, h * 0.88f)
                    suburbanPath.lineTo(rx + 35, h * 0.83f)
                    suburbanPath.lineTo(rx + 95, h * 0.83f)
                    suburbanPath.lineTo(rx + 130, h * 0.88f)
                }
                1 -> {
                    suburbanPath.lineTo(rx, h * 0.88f)
                    suburbanPath.lineTo(rx, h * 0.78f)
                    suburbanPath.lineTo(rx + 8, h * 0.78f)
                    suburbanPath.lineTo(rx + 8, h * 0.88f)
                }
                else -> suburbanPath.lineTo(rx, h * 0.88f)
            }
        }
        suburbanPath.lineTo(w, h * 0.88f); suburbanPath.lineTo(w, h); suburbanPath.close()
    }

    private fun spawnBirdFlock(w: Float, h: Float, min: Int, max: Int) {
        val baseY = 250f + Random.nextFloat() * 300f
        val baseX = -3500f - Random.nextFloat() * 1000f
        repeat(Random.nextInt(min, max)) { i ->
            val b = Bird(baseX + i * 110f, baseY + (Random.nextFloat() - 0.5f) * 250f, 7f + Random.nextFloat() * 6f)
            b.speed += (Random.nextFloat() - 0.5f) * 80f
            birds.add(b)
        }
    }

    private fun generateParallaxHills(w: Float, h: Float) {
        val baseH = if (currentTheme == Theme.EMOTIONAL_COMPANION) 0.85f else 0.68f
        generateHill(bgHillPath, w, h, baseH, 120f, 60.0)
        generateHill(mgHillPath, w, h, baseH + 0.02f, 80f, 100.0)
        generateHill(fgHillPath, w, h, baseH + 0.05f, 50f, 160.0)
    }

    private fun generateHill(path: Path, w: Float, h: Float, baseY: Float, amp: Float, seedOffset: Double) {
        path.reset(); path.moveTo(0f, h); path.lineTo(0f, h * baseY)
        val step = if (currentTheme == Theme.EMOTIONAL_COMPANION) 60 else 15
        for (x in 0..w.toInt() step step) {
            val noiseY = noise.noise(x / 300.0, seedOffset).toFloat() * amp
            path.lineTo(x.toFloat(), h * baseY + noiseY)
        }
        path.lineTo(w, h * baseY); path.lineTo(w, h); path.close()
    }

    private fun createParticle(x: Float, y: Float): Particle {
        return when (currentWeather) {
            Weather.LEAVES -> Leaf(x, y, 6f + Random.nextFloat() * 15f, 0.4f + Random.nextFloat() * 0.4f)
            Weather.SNOW -> Snow(x, y, 1.2f + Random.nextFloat() * 8f, 0.4f + Random.nextFloat() * 0.5f)
            Weather.RAIN -> Rain(x, y, 2.5f, 0.3f + Random.nextFloat() * 0.4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning || !SessionManager.isAmbientEnabled) return
        val cTime = System.currentTimeMillis(); val dt = (cTime - lastFrameTime) / 1000f; lastFrameTime = cTime
        holdFactor += (targetHoldFactor - holdFactor) * 0.04f; errorShakeFactor *= 0.6f; parallaxX += (targetParallaxX - parallaxX) * 0.025f

        updateWind(dt, cTime); updateParticles(dt, cTime); updateBirds(dt, cTime); updateSplashes(dt); updateShootingStars(dt); updateClouds(dt); updateFireflies(dt, cTime); updateLightning(dt); updateAurora(dt, cTime); updatePuddles(dt)

        canvas.save()
        if (errorShakeFactor > 0.05f) {
            val s = errorShakeFactor * 40f
            canvas.translate((Random.nextFloat() - 0.5f) * s, (Random.nextFloat() - 0.5f) * s)
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)
        if (currentTheme == Theme.CINEMATIC_NATURE || currentTimeOfDay == TimeOfDay.NIGHT || currentTheme == Theme.SANCTUARY_STARGAZING || currentTheme == Theme.SANCTUARY_PEACEFULSNOW) {
            drawStars(canvas, cTime)
        }
        if (currentTheme == Theme.CINEMATIC_NATURE || currentTheme == Theme.SANCTUARY_STARGAZING || currentTheme == Theme.SANCTUARY_PEACEFULSNOW) {
            drawAurora(canvas, cTime); drawShootingStars(canvas)
        }
        if (currentTheme != Theme.SANCTUARY_STARGAZING && currentTheme != Theme.SANCTUARY_COUCHFIREPLACE && currentTheme != Theme.SANCTUARY_RAINYCAFE) {
            drawCelestial(canvas, cTime)
        }
        drawClouds(canvas)

        drawParallax(canvas, bgHillPath, bgHillPaint, parallaxX * 10f)
        drawParallax(canvas, mgHillPath, mgHillPaint, parallaxX * 30f)
        if (currentTheme == Theme.EMOTIONAL_COMPANION) {
            canvas.save(); canvas.translate(parallaxX * 50f, 0f); canvas.drawPath(suburbanPath, silhouettePaint); canvas.restore()
        }
        drawTrees(canvas, parallaxX * 50f)
        drawParallax(canvas, fgHillPath, fgHillPaint, parallaxX * 80f)

        drawPuddles(canvas)
        drawSplashes(canvas)
        drawGrass(canvas, cTime, parallaxX * 80f)
        if (currentWeather == Weather.RAIN) drawFog(canvas, cTime)
        drawEnvironment(canvas)
        drawFireflies(canvas, cTime)

        if (lightningIntensity > 0) {
            lightningPaint.alpha = (lightningIntensity * 100).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightningPaint)
        }

        canvas.restore()
        invalidate()
    }

    private fun updateWind(dt: Float, time: Long) {
        val t = time / 20000.0
        wind.intensity = (noise.noise(t, 0.0) * 0.5 + 0.5).toFloat() * 0.5f + (holdFactor * 0.6f)
        wind.direction = if (noise.noise(0.0, t) > 0) 1f else -1f
        wind.intensity += abs(swipeWindBoost) * 0.008f; swipeWindBoost *= 0.98f
    }

    private fun updateParticles(dt: Float, time: Long) {
        val t = time / 1000.0; val wf = wind.intensity * wind.direction; val gY = height * 0.78f
        particles.forEach { p ->
            val lb = if (lightningIntensity > 0) 1.5f else 1f
            when (p) {
                is Leaf -> {
                    p.y += p.fallSpeed * dt * (1f + holdFactor + errorShakeFactor) * lb
                    p.x += (wf * 150f + noise.noise(p.seed, t * 0.6).toFloat() * 300 * wind.intensity) * dt
                    p.rotation += (p.rotationSpeed + wf * 60f) * (1f + holdFactor + errorShakeFactor)
                    if (p.y > height + 250) resetParticle(p)
                }
                is Snow -> {
                    p.y += p.fallSpeed * dt * (1f + holdFactor * 1.02f + errorShakeFactor * 1.1f)
                    p.x += (wf * 120f + noise.noise(p.seed, t * p.driftFrequency.toDouble()).toFloat() * 200) * dt
                    if (p.y > gY + noise.noise(p.x / 300.0, 160.0).toFloat() * 100f) { if (Random.nextFloat() < 0.1f) createSplash(p.x, p.y); resetParticle(p) }
                }
                is Rain -> {
                    p.y += p.fallSpeed * dt * (1f + holdFactor * 3.0f + errorShakeFactor * 4.0f) * lb
                    p.x += (wf * 500f + p.angleVariance * 300f) * dt
                    if (p.y > gY + noise.noise(p.x / 300.0, 160.0).toFloat() * 100f) { if (Random.nextFloat() < 0.3f) createSplash(p.x, p.y); resetParticle(p) }
                }
                is Ember -> {
                    p.y -= p.riseSpeed * dt * (1f + errorShakeFactor)
                    p.x += (wf * 80f + p.driftSpeed + noise.noise(p.seed, t * 0.8).toFloat() * 100) * dt
                    p.glowPhase += dt * 3f
                    if (p.y < -100) { p.y = height + 100f; p.x = Random.nextFloat() * width }
                }
                is MistDrop -> {
                    p.y -= p.floatSpeed * dt
                    p.x += (sin(t * p.swayFrequency + p.seed) * 50f * dt).toFloat()
                    if (p.y < -100) { p.y = height + 100f; p.x = Random.nextFloat() * width }
                }
            }
            if (p.x < -1800) p.x = width + 1500f; if (p.x > width + 1800) p.x = -1500f
        }
    }

    private fun drawStars(canvas: Canvas, time: Long) { val t = time / 2000.0; stars.forEach { s -> val tw = (noise.noise(t, s.seed) * 0.5 + 0.5).toFloat(); canvas.drawCircle(s.x, s.y, s.size * tw, celestialPaint) } }
    private fun drawAurora(canvas: Canvas, time: Long) { if (currentWeather != Weather.SNOW && currentTheme != Theme.SANCTUARY_STARGAZING && currentTheme != Theme.SANCTUARY_PEACEFULSNOW) return; val t = time / 10000.0; for (i in 0..3) { val aAlpha = (aurora.alpha * 25 * (1f - i * 0.2f)).toInt(); if (aAlpha <= 0) continue; auroraPaint.alpha = aAlpha; val path = Path(); path.moveTo(0f, height * 0.3f + i * 50f); for (x in 0..width.toInt() step 100) { val ny = noise.noise(x / 600.0 + t, aurora.seed + i).toFloat() * 80f; path.lineTo(x.toFloat(), height * 0.3f + i * 50f + ny) }; path.lineTo(width.toFloat(), height * 0.4f); path.lineTo(0f, height * 0.4f); path.close(); canvas.drawPath(path, auroraPaint) } }
    private fun drawShootingStars(canvas: Canvas) { shootingStars.forEach { s -> shootingStarPaint.alpha = (s.life * 180).toInt(); canvas.drawLine(s.x, s.y, s.x - s.vx * 0.1f, s.y - s.vy * 0.1f, shootingStarPaint) } }
    private fun drawCelestial(canvas: Canvas, time: Long) { val t = time / 500000.0; val cx = width * 0.75f + noise.noise(t, 25.0).toFloat() * 100f; val cy = height * 0.25f + noise.noise(25.0, t).toFloat() * 100f; val r = 50f + (holdFactor * 25f); val cCol = if (currentTheme == Theme.EMOTIONAL_COMPANION) Color.WHITE else currentWeather.celestialColor; glowPaint.shader = RadialGradient(cx, cy, r * 2.5f, intArrayOf(cCol, Color.TRANSPARENT), null, Shader.TileMode.CLAMP); canvas.drawCircle(cx, cy, r * 2.5f, glowPaint); canvas.drawCircle(cx, cy, r, celestialPaint) }
    private fun drawClouds(canvas: Canvas) { clouds.forEach { c -> canvas.save(); canvas.translate(c.x, c.y); canvas.drawOval(0f, 0f, c.width, c.height, cloudPaint); canvas.drawOval(c.width * 0.45f, -c.height * 0.25f, c.width * 1.05f, c.height * 0.45f, cloudPaint); canvas.restore() } }
    private fun drawTrees(canvas: Canvas, hs: Float) { trees.forEach { t -> canvas.save(); canvas.translate(t.x + hs * 0.4f, t.y); canvas.scale(t.scale, t.scale); canvas.skew(t.sway, 0f); canvas.drawPath(pinePath, silhouettePaint); canvas.restore() } }
    private fun drawFog(canvas: Canvas, time: Long) { val t = time / 4000.0; val h = height.toFloat(); for (i in 0..4) { val dr = noise.noise(t, i * 8.0).toFloat() * 200f; canvas.drawOval(-400f + dr, h * 0.78f + i * 40f, width + 400f + dr, h + 250f, fogPaint) } }
    private fun drawGrass(canvas: Canvas, time: Long, shift: Float) { val t = time / 700.0; val w = width.toFloat(); val h = height.toFloat(); val wf = wind.intensity * wind.direction; for (x in 0..w.toInt() step 25) { val gy = h * 0.78f + noise.noise(x / 250.0, 160.0).toFloat() * 70f; val bend = noise.noise(t, x / 150.0).toFloat() * 20f + (wf * 40f); canvas.drawLine(x + shift, gy, x + shift + bend, gy - 45f, grassPaint) } }
    private fun drawPuddles(canvas: Canvas) { puddles.forEach { p -> canvas.drawOval(p.x - p.width / 2, p.y - p.height / 2, p.x + p.width / 2, p.y + p.height / 2, puddlePaint); val rs = (sin(p.ripplePhase) * 0.5 + 0.5) * p.width * 0.5; canvas.drawOval((p.x - rs).toFloat(), (p.y - rs * 0.12).toFloat(), (p.x + rs).toFloat(), (p.y + rs * 0.12).toFloat(), ripplePaint) } }
    private fun drawParallax(canvas: Canvas, path: Path, paint: Paint, shift: Float) { canvas.save(); canvas.translate(shift, 0f); canvas.drawPath(path, paint); canvas.restore() }
    private fun resetParticle(p: Particle) { p.y = -Random.nextFloat() * 400f - 200f; p.x = Random.nextFloat() * width }
    private fun updateBirds(dt: Float, time: Long) { val t = time / 15000.0; birds.forEach { b -> b.x += (b.speed + wind.intensity * wind.direction * 180f) * dt; b.y = b.targetY + noise.noise(b.seed, t).toFloat() * 250f; b.wingPhase += dt * (20f + wind.intensity * 20f); if (b.x > width + 3500) { b.x = -4000f; b.targetY = 200f + Random.nextFloat() * 800f } } }
    private fun updateShootingStars(dt: Float) { if (Random.nextFloat() < 0.004f) shootingStars.add(ShootingStar(Random.nextFloat() * width, 0f, 800f + Random.nextFloat() * 500f, 400f + Random.nextFloat() * 200f)); val it = shootingStars.iterator(); while (it.hasNext()) { val s = it.next(); s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt * 2.0f; if (s.life <= 0) it.remove() } }
    private fun updateClouds(dt: Float) { clouds.forEach { c -> c.x += (c.speed + wind.intensity * wind.direction * 40f) * dt; if (c.x > width + 1200) c.x = -c.width - 1200; if (c.x < -c.width - 1200) c.x = width + 1200f } }
    private fun updateFireflies(dt: Float, time: Long) { val t = time / 2000.0; fireflies.forEach { f -> val nx = noise.noise(t, f.seed).toFloat() * 150f; val ny = noise.noise(f.seed, t).toFloat() * 150f; f.x += (f.targetX - f.x) * 0.015f + nx * dt; f.y += (f.targetY - f.y) * 0.015f + ny * dt; if (abs(f.x - f.targetX) < 50) f.targetX = Random.nextFloat() * width; if (abs(f.y - f.targetY) < 50) f.targetY = (height * 0.45f).takeIf { it > 0 } ?: 0f + Random.nextFloat() * ((height * 0.45f).takeIf { it > 0 } ?: 1f) } }
    private fun updateAurora(dt: Float, time: Long) { aurora.alpha = if (currentWeather == Weather.SNOW) min(1f, aurora.alpha + dt * 0.1f) else max(0f, aurora.alpha - dt * 0.3f) }
    private fun updatePuddles(dt: Float) { puddles.forEach { p -> p.ripplePhase += dt * 3.0f; if (p.ripplePhase > PI * 2) p.ripplePhase = 0f } }
    private fun updateLightning(dt: Float) { if (currentWeather == Weather.RAIN) { if (lightningIntensity > 0) lightningIntensity -= dt * 3f else if (Random.nextFloat() < 0.003f) lightningIntensity = 1.0f } else lightningIntensity = 0f }
    private fun createSplash(x: Float, y: Float) { if (splashes.size < 100) splashes.add(Splash(x, y, 0.5f + Random.nextFloat() * 0.5f)) }
    private fun updateSplashes(dt: Float) { val it = splashes.iterator(); while (it.hasNext()) { val s = it.next(); s.life -= dt * 4f; if (s.life <= 0) it.remove() } }
    private fun drawEnvironment(canvas: Canvas) { particles.forEach { p -> when (p) { is Leaf -> { leafPaint.alpha = (p.opacity * 190).toInt(); canvas.save(); canvas.translate(p.x, p.y); canvas.rotate(p.rotation); canvas.drawOval(-p.size, -p.size / 4f, p.size, p.size / 4f, leafPaint); canvas.restore() }; is Snow -> { snowPaint.alpha = (p.opacity * 200).toInt(); canvas.drawCircle(p.x, p.y, p.size / 2, snowPaint) }; is Rain -> { rainPaint.alpha = (p.opacity * 180).toInt(); canvas.drawLine(p.x, p.y, p.x + wind.direction * wind.intensity * 100f, p.y + p.length, rainPaint) }; is Ember -> { val alphaPulse = (sin(p.glowPhase) * 0.5f + 0.5f) * p.opacity; emberPaint.alpha = (alphaPulse * 255).toInt(); canvas.drawCircle(p.x, p.y, p.size, emberPaint) }; is MistDrop -> { snowPaint.alpha = (p.opacity * 100).toInt(); canvas.drawCircle(p.x, p.y, p.size, snowPaint) } } }; birds.forEach { b -> val wY = sin(b.wingPhase) * b.size; canvas.save(); canvas.translate(b.x, b.y); canvas.drawLine(0f, 0f, -b.size, -wY, birdPaint); canvas.drawLine(0f, 0f, b.size, -wY, birdPaint); canvas.restore() } }
    private fun drawSplashes(canvas: Canvas) { splashes.forEach { s -> splashPaint.alpha = (s.life * 190).toInt(); val r = (1.0f - s.life) * 40f; canvas.drawArc(s.x - r, s.y - r / 4f, s.x + r, s.y + r / 4f, 180f, 180f, false, splashPaint) } }
    private fun drawFireflies(canvas: Canvas, time: Long) { val t = time / 500.0; fireflies.forEach { f -> val pulse = (sin(t + f.seed) * 0.5 + 0.5).toFloat(); fireflyGlowPaint.shader = RadialGradient(f.x, f.y, (20f * pulse).coerceAtLeast(1f), intArrayOf(currentWeather.fireflyColor, Color.TRANSPARENT), null, Shader.TileMode.CLAMP); canvas.drawCircle(f.x, f.y, 20f * pulse, fireflyGlowPaint); canvas.drawCircle(f.x, f.y, 3f * pulse, fireflyPaint) } }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event); targetParallaxX = (event.x / (width.takeIf { it > 0 } ?: 1080)) * 1.0f - 0.5f
        if (event.action == MotionEvent.ACTION_MOVE) { val tx = event.x; val ty = event.y; particles.forEach { p -> val dx = p.x - tx; val dy = p.y - ty; val dSq = dx * dx + dy * dy; if (dSq < 3000000) { val f = (1800 - sqrt(dSq)) / 1800f; val ang = atan2(dy, dx); p.x += cos(ang) * f * 150f; p.y += sin(ang) * f * 150f } } } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) { targetHoldFactor = 0f; targetParallaxX = 0f }
        return true
    }

    fun triggerErrorEffect() { errorShakeFactor = 2.0f; swipeWindBoost = (Random.nextFloat() - 0.5f) * 80f; lightningIntensity = 1.0f }
}
