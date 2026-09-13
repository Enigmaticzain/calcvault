package com.calcvault.emotional.themes

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/**
 * RainAnimator
 *
 * Creates realistic rain animation with:
 *  - Falling raindrops with gravity
 *  - Wind effect (raindrops drift sideways)
 *  - Splash particles on ground
 *  - Variable rain intensity (light, moderate, heavy)
 *  - Streaky rain lines for speed effect
 *
 * Usage:
 *   val rain = RainAnimator(screenWidth, screenHeight, intensity = 0.7f)
 *   rain.update()
 *   rain.draw(canvas, paint)
 */
class RainAnimator(
    private val screenWidth: Float,
    private val screenHeight: Float,
    var intensity: Float = 0.5f  // 0.0 to 1.0
) {

    private data class Raindrop(
        var x: Float,
        var y: Float,
        var vx: Float,              // horizontal velocity (wind)
        var vy: Float,              // vertical velocity (gravity + wind)
        val length: Float,          // raindrop streak length
        val thickness: Float,       // raindrop thickness
        var alpha: Float = 1f,
        val color: Int = Color.argb(200, 150, 200, 255)  // Light blue
    )

    private data class Splash(
        var x: Float,
        var y: Float,
        var progress: Float = 0f,   // 0.0 to 1.0
        val maxRadius: Float,
        var alpha: Float = 1f
    )

    private val raindrops = mutableListOf<Raindrop>()
    private val splashes = mutableListOf<Splash>()
    
    private var windPhase = 0f
    private var frameCount = 0
    
    // Physics constants
    companion object {
        const val GRAVITY = 0.15f
        const val BASE_DROP_SPEED = 8f
        const val WIND_STRENGTH = 0.03f
        const val SPLASH_DURATION = 30  // frames
        const val SPLASH_PARTICLES = 4
    }

    init {
        spawnInitialDrops()
    }

    private fun spawnInitialDrops() {
        raindrops.clear()
        val dropCount = (intensity * 150).toInt().coerceIn(10, 200)
        repeat(dropCount) {
            raindrops.add(createRandomRaindrop())
        }
    }

    private fun createRandomRaindrop(): Raindrop {
        val startY = Random.nextFloat() * screenHeight - 200f
        return Raindrop(
            x = Random.nextFloat() * screenWidth,
            y = startY,
            vx = (Random.nextFloat() - 0.5f) * 1.5f,  // Small horizontal drift
            vy = BASE_DROP_SPEED + Random.nextFloat() * 2f,
            length = Random.nextFloat() * 8f + 12f,
            thickness = Random.nextFloat() * 1.5f + 0.8f,
            alpha = 0.6f + Random.nextFloat() * 0.4f
        )
    }

    fun update() {
        frameCount++
        windPhase += 0.02f
        
        // Calculate wind effect (sine wave for natural wind gusts)
        val windForce = sin(windPhase.toDouble()).toFloat() * WIND_STRENGTH

        // Update raindrops
        val dropsToRemove = mutableListOf<Raindrop>()
        
        for (drop in raindrops) {
            // Apply gravity and wind
            drop.vy = (drop.vy + GRAVITY).coerceAtMost(15f)  // Terminal velocity cap
            drop.vx += windForce
            
            // Move drop
            drop.x += drop.vx
            drop.y += drop.vy
            
            // Horizontal wrapping
            if (drop.x < -10f) drop.x = screenWidth + 10f
            if (drop.x > screenWidth + 10f) drop.x = -10f
            
            // Check if hit ground or off screen
            if (drop.y > screenHeight) {
                // Create splash
                createSplash(drop.x, screenHeight - 5f)
                dropsToRemove.add(drop)
            } else if (drop.y < -50f) {
                // Respawn at top
                drop.y = -20f
                drop.x = Random.nextFloat() * screenWidth
                drop.vy = BASE_DROP_SPEED + Random.nextFloat() * 2f
            }
        }
        
        raindrops.removeAll(dropsToRemove)
        
        // Respawn lost drops to maintain intensity
        while (raindrops.size < (intensity * 150).toInt().coerceIn(10, 200)) {
            raindrops.add(createRandomRaindrop())
        }

        // Update splashes
        val splashesToRemove = mutableListOf<Splash>()
        for (splash in splashes) {
            splash.progress += 1f / SPLASH_DURATION
            splash.alpha = (1f - splash.progress).coerceAtLeast(0f)
            
            if (splash.progress >= 1f) {
                splashesToRemove.add(splash)
            }
        }
        splashes.removeAll(splashesToRemove)
    }

    private fun createSplash(x: Float, y: Float) {
        splashes.add(Splash(
            x = x,
            y = y,
            maxRadius = Random.nextFloat() * 20f + 15f
        ))
    }

    fun draw(canvas: Canvas, paint: Paint) {
        // Draw raindrops as streaks
        drawRaindrops(canvas, paint)
        
        // Draw splash particles
        drawSplashes(canvas, paint)
    }

    private fun drawRaindrops(canvas: Canvas, paint: Paint) {
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        
        for (drop in raindrops) {
            // Calculate angle based on velocity
            val angle = atan2(drop.vy, drop.vx)
            
            // Start and end points of rain streak
            val startX = drop.x - drop.length * 0.5f * cos(angle)
            val startY = drop.y - drop.length * 0.5f * sin(angle)
            val endX = drop.x + drop.length * 0.5f * cos(angle)
            val endY = drop.y + drop.length * 0.5f * sin(angle)
            
            // Color with alpha
            paint.color = Color.argb(
                (drop.alpha * 255).toInt(),
                150, 200, 255
            )
            paint.strokeWidth = drop.thickness
            
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
    }

    private fun drawSplashes(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.STROKE
        
        for (splash in splashes) {
            // Expanding circle ripple
            val radius = splash.maxRadius * (splash.progress * splash.progress)  // Ease-out
            
            paint.color = Color.argb(
                (splash.alpha * 100).toInt(),
                150, 200, 255
            )
            paint.strokeWidth = 1.5f
            
            canvas.drawCircle(splash.x, splash.y, radius, paint)
            
            // Inner ripple (delayed)
            if (splash.progress > 0.3f) {
                val innerProgress = (splash.progress - 0.3f) / 0.7f
                val innerRadius = splash.maxRadius * 0.3f * (innerProgress * innerProgress)
                
                paint.color = Color.argb(
                    (splash.alpha * 50).toInt(),
                    150, 200, 255
                )
                canvas.drawCircle(splash.x, splash.y, innerRadius, paint)
            }
        }
    }

    fun setIntensity(newIntensity: Float) {
        intensity = newIntensity.coerceIn(0f, 1f)
        // Auto-adjust drop count
        val targetCount = (intensity * 150).toInt().coerceIn(10, 200)
        while (raindrops.size < targetCount) {
            raindrops.add(createRandomRaindrop())
        }
        while (raindrops.size > targetCount) {
            raindrops.removeAt(raindrops.lastIndex)
        }
    }

    fun clear() {
        raindrops.clear()
        splashes.clear()
    }
}
