package com.calcvault.ui.filters

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.SurfaceTexture
import android.view.TextureView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * VideoFilterEngine - Real-time filter system for video calls
 *
 * Features:
 * - Emoji shower effects (hearts, fire, laughing, etc.)
 * - Mood-based overlay filters (warm, cool, dark)
 * - Visual effects (sparkles, glow, blur)
 * - GPU-accelerated rendering
 * - 60 FPS target with adaptive quality
 * - Low-latency trigger response
 */
class VideoFilterEngine(
    private val context: Context,
    private val textureView: TextureView
) : TextureView.SurfaceTextureListener {

    enum class FilterType {
        NONE,
        WARM_TONE,
        COOL_TONE,
        DARK_TONE,
        SPARKLE,
        GLOW_AURA,
        SOFT_BLUR,
        FLOATING_PARTICLES
    }

    enum class EmojiType(val emoji: String, val color: Int) {
        HEART("❤️", 0xFFFF1744.toInt()),
        FIRE("🔥", 0xFFFF6F00.toInt()),
        LAUGH("😂", 0xFFFFD600.toInt()),
        STAR("⭐", 0xFFFFD600.toInt()),
        SPARKLE("✨", 0xFFE91E63.toInt()),
        KISS("💋", 0xFFFF1744.toInt()),
        LOVE_EYES("😍", 0xFFFF1744.toInt())
    }

    data class EmojiParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val emoji: EmojiType,
        var alpha: Float = 1f,
        var scale: Float = 1f,
        var lifetime: Long = 3000L,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isAlive(): Boolean = System.currentTimeMillis() - createdAt < lifetime
        fun getProgress(): Float = (System.currentTimeMillis() - createdAt).toFloat() / lifetime
    }

    data class VisualEffect(
        val type: FilterType,
        var intensity: Float = 1f,
        var duration: Long = 0L,
        val startTime: Long = System.currentTimeMillis()
    ) {
        fun isActive(): Boolean = duration == 0L || System.currentTimeMillis() - startTime < duration
    }

    private val emojiParticles = mutableListOf<EmojiParticle>()
    private val activeEffects = mutableListOf<VisualEffect>()
    private val renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var renderThread: Thread? = null
    private var isRendering = false
    private var surfaceTexture: SurfaceTexture? = null
    private var canvas: Canvas? = null
    private var paint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }

    private var targetFps = 60
    private var currentFps = 60
    private var frameCount = 0L
    private var lastFpsCheck = System.currentTimeMillis()
    private var isLowEndDevice = false
    private var qualityLevel = 1f // 1.0 = full, 0.5 = reduced

    init {
        textureView.surfaceTextureListener = this
        detectDeviceCapabilities()
    }

    private fun detectDeviceCapabilities() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        isLowEndDevice = maxMemory < 512
        qualityLevel = if (isLowEndDevice) 0.5f else 1f
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceTexture = surface
        startRenderThread()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRenderThread()
        return true
    }

    override fun onSurfaceTextureFrameAvailable(surface: SurfaceTexture) {}

    private fun startRenderThread() {
        if (renderThread != null) return
        isRendering = true
        renderThread = Thread {
            while (isRendering) {
                try {
                    renderFrame()
                    val frameTime = 1000L / targetFps
                    Thread.sleep(frameTime)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.apply { start() }
    }

    private fun stopRenderThread() {
        isRendering = false
        renderThread?.join(1000)
        renderThread = null
    }

    private fun renderFrame() {
        try {
            val surface = surfaceTexture ?: return
            val holder = textureView.holder ?: return

            canvas = holder.lockCanvas() ?: return
            canvas?.let { c ->
                // Clear canvas
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                // Apply active overlay filters
                applyOverlayFilters(c)

                // Render emoji particles
                updateAndRenderEmojis(c)

                // Render visual effects
                renderVisualEffects(c)

                holder.unlockCanvasAndPost(c)
            }

            updateFpsMetrics()
            adaptQualityIfNeeded()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyOverlayFilters(canvas: Canvas) {
        activeEffects.forEach { effect ->
            if (!effect.isActive()) return@forEach

            when (effect.type) {
                FilterType.WARM_TONE -> {
                    paint.color = Color.argb((50 * effect.intensity * qualityLevel).toInt(), 255, 150, 100)
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                }
                FilterType.COOL_TONE -> {
                    paint.color = Color.argb((50 * effect.intensity * qualityLevel).toInt(), 100, 150, 255)
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                }
                FilterType.DARK_TONE -> {
                    paint.color = Color.argb((80 * effect.intensity * qualityLevel).toInt(), 0, 0, 0)
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                }
                FilterType.GLOW_AURA -> {
                    drawGlowAura(canvas, effect.intensity)
                }
                FilterType.SOFT_BLUR -> {
                    drawSoftBlur(canvas, effect.intensity)
                }
                FilterType.SPARKLE -> {
                    drawSparkles(canvas, effect.intensity)
                }
                FilterType.FLOATING_PARTICLES -> {
                    drawFloatingParticles(canvas, effect.intensity)
                }
                else -> {}
            }
        }
    }

    private fun drawGlowAura(canvas: Canvas, intensity: Float) {
        val centerX = canvas.width / 2f
        val centerY = canvas.height / 2f
        val radius = (canvas.width / 3f) * intensity * qualityLevel

        paint.shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(Color.argb(100, 255, 200, 100), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.shader = null
    }

    private fun drawSoftBlur(canvas: Canvas, intensity: Float) {
        paint.color = Color.argb((30 * intensity * qualityLevel).toInt(), 200, 200, 200)
        paint.maskFilter = BlurMaskFilter(10f * intensity * qualityLevel, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
        paint.maskFilter = null
    }

    private fun drawSparkles(canvas: Canvas, intensity: Float) {
        val sparkleCount = (20 * intensity * qualityLevel).toInt()
        repeat(sparkleCount) {
            val x = Random.nextFloat() * canvas.width
            val y = Random.nextFloat() * canvas.height
            val size = Random.nextFloat() * 4f * intensity
            paint.color = Color.argb(200, 255, 255, 100)
            canvas.drawCircle(x, y, size, paint)
        }
    }

    private fun drawFloatingParticles(canvas: Canvas, intensity: Float) {
        val particleCount = (15 * intensity * qualityLevel).toInt()
        repeat(particleCount) {
            val x = (System.currentTimeMillis() / 10 + it * 50) % canvas.width
            val y = (System.currentTimeMillis() / 20 + it * 30) % canvas.height
            paint.color = Color.argb(150, 200, 150, 255)
            canvas.drawCircle(x.toFloat(), y.toFloat(), 3f, paint)
        }
    }

    private fun updateAndRenderEmojis(canvas: Canvas) {
        val iterator = emojiParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            if (!particle.isAlive()) {
                iterator.remove()
                continue
            }

            val progress = particle.getProgress()
            particle.alpha = 1f - progress
            particle.scale = 1f + (progress * 0.5f)

            particle.x += particle.vx
            particle.y += particle.vy
            particle.vy += 0.2f // gravity

            drawEmoji(canvas, particle)
        }
    }

    private fun drawEmoji(canvas: Canvas, particle: EmojiParticle) {
        paint.alpha = (particle.alpha * 255).toInt()
        paint.textSize = (40f * particle.scale * qualityLevel)
        paint.textAlign = Paint.Align.CENTER

        canvas.drawText(
            particle.emoji.emoji,
            particle.x,
            particle.y,
            paint
        )
        paint.alpha = 255
    }

    private fun renderVisualEffects(canvas: Canvas) {
        activeEffects.removeAll { !it.isActive() }
    }

    private fun updateFpsMetrics() {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsCheck >= 1000) {
            currentFps = frameCount.toInt()
            frameCount = 0
            lastFpsCheck = now
        }
    }

    private fun adaptQualityIfNeeded() {
        if (currentFps < 24) {
            qualityLevel = (qualityLevel * 0.9f).coerceAtLeast(0.3f)
        } else if (currentFps > 50 && qualityLevel < 1f) {
            qualityLevel = (qualityLevel * 1.05f).coerceAtMost(1f)
        }
    }

    fun triggerEmojiShower(emojiType: EmojiType, intensity: Float = 1f) {
        val showerCount = (30 * intensity * qualityLevel).toInt()
        repeat(showerCount) {
            val particle = EmojiParticle(
                x = Random.nextFloat() * textureView.width,
                y = -50f,
                vx = (Random.nextFloat() - 0.5f) * 4f,
                vy = Random.nextFloat() * 2f + 1f,
                emoji = emojiType,
                lifetime = 3000L + Random.nextLong(1000)
            )
            emojiParticles.add(particle)
        }
    }

    fun applyOverlayFilter(filterType: FilterType, duration: Long = 0L, intensity: Float = 1f) {
        activeEffects.removeAll { it.type == filterType }
        activeEffects.add(VisualEffect(filterType, intensity, duration))
    }

    fun removeOverlayFilter(filterType: FilterType) {
        activeEffects.removeAll { it.type == filterType }
    }

    fun clearAllEffects() {
        emojiParticles.clear()
        activeEffects.clear()
    }

    fun getCurrentFps(): Int = currentFps

    fun getQualityLevel(): Float = qualityLevel

    fun setTargetFps(fps: Int) {
        targetFps = fps.coerceIn(24, 60)
    }

    fun destroy() {
        stopRenderThread()
        renderScope.cancel()
        emojiParticles.clear()
        activeEffects.clear()
    }
}
