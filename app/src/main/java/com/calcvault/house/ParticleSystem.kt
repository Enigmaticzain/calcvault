package com.calcvault.house

import android.graphics.*
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/**
 * ParticleSystem.kt — Lightweight particle effects engine
 * Hearts, stars, bubbles, music notes, sleep Z's, sparkles, steam, confetti
 */

enum class ParticleType {
    HEART, STAR, BUBBLE, MUSIC_NOTE, SLEEP_Z, SPARKLE, WATER_DROP,
    FOOD_CRUMB, CONFETTI, STEAM, TEAR, ANGER_PUFF, SWEAT_DROP
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float = 1f,      // 1.0 = just born, 0.0 = dead
    var decay: Float = 0.015f,
    var size: Float = 12f,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    val type: ParticleType,
    val color: Int = Color.WHITE,
    var alpha: Int = 255
)

class ParticleEngine {

    private val particles = mutableListOf<Particle>()
    private val maxParticles = 120
    private val random = java.util.Random()

    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    fun emit(type: ParticleType, x: Float, y: Float, count: Int = 5, color: Int = Color.WHITE) {
        if (particles.size >= maxParticles) return
        repeat(count.coerceAtMost(maxParticles - particles.size)) {
            particles.add(createParticle(type, x, y, color))
        }
    }

    fun emitBurst(type: ParticleType, x: Float, y: Float, count: Int = 12) {
        repeat(count.coerceAtMost(maxParticles - particles.size)) { i ->
            val angle = (i.toFloat() / count) * 2f * PI.toFloat()
            val speed = 2f + random.nextFloat() * 3f
            val p = createParticle(type, x, y, Color.WHITE).copy(
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                decay = 0.02f + random.nextFloat() * 0.01f
            )
            particles.add(p)
        }
    }

    private fun createParticle(type: ParticleType, x: Float, y: Float, color: Int): Particle {
        return when (type) {
            ParticleType.HEART -> Particle(
                x = x + (random.nextFloat() - 0.5f) * 40f, y = y,
                vx = (random.nextFloat() - 0.5f) * 1.5f, vy = -1.5f - random.nextFloat() * 2f,
                size = 14f + random.nextFloat() * 8f, decay = 0.012f,
                type = type, color = Color.parseColor("#FF4081"),
                rotationSpeed = (random.nextFloat() - 0.5f) * 3f
            )
            ParticleType.STAR -> Particle(
                x = x + (random.nextFloat() - 0.5f) * 50f, y = y + (random.nextFloat() - 0.5f) * 50f,
                vx = (random.nextFloat() - 0.5f) * 2f, vy = -1f - random.nextFloat(),
                size = 10f + random.nextFloat() * 6f, decay = 0.02f,
                type = type, color = Color.parseColor("#FFD700"),
                rotationSpeed = random.nextFloat() * 5f
            )
            ParticleType.BUBBLE -> Particle(
                x = x + (random.nextFloat() - 0.5f) * 30f, y = y,
                vx = (random.nextFloat() - 0.5f) * 0.8f, vy = -0.8f - random.nextFloat() * 1.2f,
                size = 8f + random.nextFloat() * 12f, decay = 0.008f,
                type = type, color = Color.parseColor("#80D8FF")
            )
            ParticleType.MUSIC_NOTE -> Particle(
                x = x, y = y,
                vx = (random.nextFloat() - 0.5f) * 1.5f, vy = -1.5f - random.nextFloat(),
                size = 18f, decay = 0.015f,
                type = type, color = Color.parseColor("#7C4DFF")
            )
            ParticleType.SLEEP_Z -> Particle(
                x = x + 15f, y = y - 40f,
                vx = 0.3f, vy = -0.6f,
                size = 16f + random.nextFloat() * 8f, decay = 0.008f,
                type = type, color = Color.parseColor("#90CAF9")
            )
            ParticleType.SPARKLE -> Particle(
                x = x + (random.nextFloat() - 0.5f) * 60f, y = y + (random.nextFloat() - 0.5f) * 60f,
                vx = 0f, vy = 0f,
                size = 6f + random.nextFloat() * 8f, decay = 0.03f,
                type = type, color = Color.parseColor("#FFF176"),
                rotationSpeed = random.nextFloat() * 8f
            )
            ParticleType.WATER_DROP -> Particle(
                x = x + (random.nextFloat() - 0.5f) * 40f, y = y,
                vx = (random.nextFloat() - 0.5f) * 0.5f, vy = 2f + random.nextFloat() * 3f,
                size = 6f + random.nextFloat() * 4f, decay = 0.025f,
                type = type, color = Color.parseColor("#4FC3F7")
            )
            ParticleType.STEAM -> Particle(
                x = x + (random.nextFloat() - 0.5f) * 20f, y = y,
                vx = (random.nextFloat() - 0.5f) * 0.5f, vy = -0.5f - random.nextFloat() * 0.5f,
                size = 15f + random.nextFloat() * 10f, decay = 0.01f,
                type = type, color = Color.argb(100, 255, 255, 255)
            )
            ParticleType.TEAR -> Particle(
                x = x, y = y,
                vx = 0f, vy = 1.5f + random.nextFloat(),
                size = 6f, decay = 0.03f,
                type = type, color = Color.parseColor("#64B5F6")
            )
            ParticleType.CONFETTI -> Particle(
                x = x, y = y,
                vx = (random.nextFloat() - 0.5f) * 6f, vy = -3f - random.nextFloat() * 4f,
                size = 8f + random.nextFloat() * 4f, decay = 0.012f,
                type = type, color = arrayOf(
                    Color.parseColor("#FF4081"), Color.parseColor("#FFD740"),
                    Color.parseColor("#69F0AE"), Color.parseColor("#40C4FF"),
                    Color.parseColor("#E040FB")
                ).random(),
                rotationSpeed = random.nextFloat() * 10f
            )
            else -> Particle(
                x = x, y = y, vx = 0f, vy = -1f,
                size = 8f, decay = 0.02f, type = type, color = color
            )
        }
    }

    fun update() {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx
            p.y += p.vy
            p.life -= p.decay
            p.rotation += p.rotationSpeed
            p.alpha = (p.life * 255).toInt().coerceIn(0, 255)

            // Gravity for certain types
            when (p.type) {
                ParticleType.CONFETTI -> p.vy += 0.12f
                ParticleType.WATER_DROP -> p.vy += 0.15f
                ParticleType.FOOD_CRUMB -> p.vy += 0.1f
                ParticleType.BUBBLE -> p.vx += sin(p.y * 0.05f).toFloat() * 0.1f
                else -> {}
            }

            if (p.life <= 0f) iter.remove()
        }
    }

    fun draw(canvas: Canvas) {
        particles.forEach { p ->
            heartPaint.alpha = p.alpha
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)

            when (p.type) {
                ParticleType.HEART -> drawHeart(canvas, p)
                ParticleType.STAR -> drawStar(canvas, p)
                ParticleType.BUBBLE -> drawBubble(canvas, p)
                ParticleType.MUSIC_NOTE -> drawText(canvas, p, "♪")
                ParticleType.SLEEP_Z -> drawText(canvas, p, "Z")
                ParticleType.SPARKLE -> drawSparkle(canvas, p)
                ParticleType.TEAR -> drawTear(canvas, p)
                ParticleType.CONFETTI -> drawConfetti(canvas, p)
                ParticleType.STEAM -> drawSteam(canvas, p)
                ParticleType.WATER_DROP -> drawTear(canvas, p)
                else -> {
                    heartPaint.color = p.color
                    canvas.drawCircle(0f, 0f, p.size * 0.5f, heartPaint)
                }
            }
            canvas.restore()
        }
    }

    private fun drawHeart(canvas: Canvas, p: Particle) {
        val s = p.size
        val path = Path().apply {
            moveTo(0f, s * 0.3f)
            cubicTo(-s * 0.5f, -s * 0.2f, -s * 0.5f, -s * 0.55f, 0f, -s * 0.25f)
            cubicTo(s * 0.5f, -s * 0.55f, s * 0.5f, -s * 0.2f, 0f, s * 0.3f)
            close()
        }
        heartPaint.color = p.color
        heartPaint.style = Paint.Style.FILL
        canvas.drawPath(path, heartPaint)
    }

    private fun drawStar(canvas: Canvas, p: Particle) {
        heartPaint.color = p.color
        heartPaint.style = Paint.Style.FILL
        val s = p.size * 0.5f
        val path = Path()
        for (i in 0 until 5) {
            val outerAngle = (i * 72 - 90) * PI.toFloat() / 180f
            val innerAngle = ((i * 72 + 36) - 90) * PI.toFloat() / 180f
            val ox = cos(outerAngle) * s; val oy = sin(outerAngle) * s
            val ix = cos(innerAngle) * s * 0.4f; val iy = sin(innerAngle) * s * 0.4f
            if (i == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
            path.lineTo(ix, iy)
        }
        path.close()
        canvas.drawPath(path, heartPaint)
    }

    private fun drawBubble(canvas: Canvas, p: Particle) {
        heartPaint.color = p.color; heartPaint.style = Paint.Style.STROKE; heartPaint.strokeWidth = 1.5f
        canvas.drawCircle(0f, 0f, p.size * 0.5f, heartPaint)
        heartPaint.style = Paint.Style.FILL
        heartPaint.color = Color.argb(p.alpha / 3, 255, 255, 255)
        canvas.drawCircle(-p.size * 0.15f, -p.size * 0.15f, p.size * 0.12f, heartPaint)
    }

    private fun drawSparkle(canvas: Canvas, p: Particle) {
        heartPaint.color = p.color; heartPaint.style = Paint.Style.STROKE; heartPaint.strokeWidth = 2f
        val s = p.size * 0.5f
        canvas.drawLine(-s, 0f, s, 0f, heartPaint); canvas.drawLine(0f, -s, 0f, s, heartPaint)
        val d = s * 0.6f
        canvas.drawLine(-d, -d, d, d, heartPaint); canvas.drawLine(-d, d, d, -d, heartPaint)
    }

    private fun drawTear(canvas: Canvas, p: Particle) {
        heartPaint.color = p.color; heartPaint.style = Paint.Style.FILL
        val path = Path().apply {
            moveTo(0f, -p.size * 0.5f)
            cubicTo(p.size * 0.3f, 0f, p.size * 0.2f, p.size * 0.4f, 0f, p.size * 0.5f)
            cubicTo(-p.size * 0.2f, p.size * 0.4f, -p.size * 0.3f, 0f, 0f, -p.size * 0.5f)
            close()
        }
        canvas.drawPath(path, heartPaint)
    }

    private fun drawConfetti(canvas: Canvas, p: Particle) {
        heartPaint.color = p.color; heartPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(-p.size * 0.5f, -p.size * 0.2f, p.size * 0.5f, p.size * 0.2f), 2f, 2f, heartPaint)
    }

    private fun drawSteam(canvas: Canvas, p: Particle) {
        heartPaint.color = p.color; heartPaint.style = Paint.Style.FILL
        heartPaint.maskFilter = BlurMaskFilter(p.size * 0.3f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(0f, 0f, p.size * 0.5f, heartPaint)
        heartPaint.maskFilter = null
    }

    private fun drawText(canvas: Canvas, p: Particle, text: String) {
        textPaint.color = p.color; textPaint.alpha = p.alpha; textPaint.textSize = p.size
        canvas.drawText(text, 0f, p.size * 0.35f, textPaint)
    }

    fun clear() = particles.clear()
    fun particleCount() = particles.size
    fun hasParticles() = particles.isNotEmpty()
}
