package com.calcvault.ui.ambient

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/**
 * AmbientAnimationEnhancer
 *
 * Advanced ambient effects for AmbientAnimationView:
 * - Aurora Borealis animations
 * - Lightning storms with sound visualization
 * - Advanced weather transitions
 * - Particle collisions and physics
 * - Interactive background elements
 * - Time-based scene transitions
 * - Depth parallax effects
 * - Light ray effects
 */
class AmbientAnimationEnhancer {

    // ─── Advanced Weather System ──────────────────────────────────────────────

    data class AdvancedWeather(
        val name: String,
        val particleType: String, // "rain", "snow", "leaves", "pollen", "ash"
        val intensity: Float,
        val duration: Long,
        val lightLevel: Float,
        val ambientSound: String?, // visual frequency representation
        val colorMod: Int
    )

    val WEATHER_THUNDERSTORM = AdvancedWeather(
        name = "Thunderstorm",
        particleType = "rain",
        intensity = 1.0f,
        duration = 5000,
        lightLevel = 0.3f,
        ambientSound = "storm",
        colorMod = 0xFF3F3F3F.toInt()
    )

    val WEATHER_BLIZZARD = AdvancedWeather(
        name = "Blizzard",
        particleType = "snow",
        intensity = 0.9f,
        duration = 8000,
        lightLevel = 0.5f,
        ambientSound = "wind",
        colorMod = 0xFFEEEEEE.toInt()
    )

    val WEATHER_AUTUMN = AdvancedWeather(
        name = "Autumn Leaves",
        particleType = "leaves",
        intensity = 0.6f,
        duration = 15000,
        lightLevel = 0.8f,
        ambientSound = null,
        colorMod = 0xFFA0522D.toInt()
    )

    val WEATHER_POLLEN = AdvancedWeather(
        name = "Pollen Season",
        particleType = "pollen",
        intensity = 0.5f,
        duration = 20000,
        lightLevel = 0.9f,
        ambientSound = null,
        colorMod = 0xFFFFDE92.toInt()
    )

    // ─── Aurora Borealis Data ─────────────────────────────────────────────────

    data class AuroraCurtain(
        val seed: Double,
        var time: Float = 0f,
        val colors: List<Int> = listOf(
            0x8800FF00.toInt(), // Green
            0x8800CCFF.toInt(), // Cyan
            0x88FF00FF.toInt(), // Magenta
            0x8800FFCC.toInt() // Teal
        )
    ) {
        fun update(dt: Float) {
            time += dt * 0.5f
        }

        fun draw(canvas: Canvas, width: Float, height: Float, paint: Paint) {
            val t = time
            val bands = 5
            val bandHeight = height / bands

            for (band in 0 until bands) {
                val phase = (seed + band * 0.5 + t * 0.3).toFloat()
                val colorIndex = ((phase * 1000f).toInt() % colors.size).coerceAtLeast(0)
                val color = colors[colorIndex]

                paint.color = color
                paint.alpha = (sin(phase * Math.PI.toFloat()) * 100 + 50).toInt().coerceIn(0, 255)

                val y = band * bandHeight
                canvas.drawRect(0f, y, width, y + bandHeight, paint)
            }
        }
    }

    // ─── Lightning System ──────────────────────────────────────────────────────

    data class LightningBolt(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val branches: Int = 3,
        val intensity: Float = 1.0f,
        var age: Float = 0f,
        val lifetime: Float = 0.2f // 200ms
    ) {
        fun isAlive() = age < lifetime
        fun progress() = age / lifetime

        /** Generate fractal branches for lightning bolt */
        fun generatePoints(): List<PointF> {
            val points = mutableListOf<PointF>()
            points.add(PointF(startX, startY))
            points.add(PointF(endX, endY))

            // Recursive subdivision with random jitter
            var level = 0
            while (level++ < branches) {
                val newPoints = mutableListOf<PointF>()
                for (i in 0 until points.size - 1) {
                    newPoints.add(points[i])
                    val midX = (points[i].x + points[i + 1].x) / 2f
                    val midY = (points[i].y + points[i + 1].y) / 2f
                    val perpX = (points[i + 1].y - points[i].y) * 0.1f * Random.nextFloat()
                    val perpY = (points[i + 1].x - points[i].x) * 0.1f * Random.nextFloat()
                    newPoints.add(PointF(midX + perpX, midY + perpY))
                }
                newPoints.add(points.last())
                points.clear()
                points.addAll(newPoints)
            }
            return points
        }
    }

    // ─── Advanced Particle Collision ───────────────────────────────────────────

    data class PhysicsParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val radius: Float,
        val color: Int,
        var age: Float = 0f,
        val lifetime: Float
    ) {
        fun update(dt: Float, gravity: Float = 500f, drag: Float = 0.95f) {
            vy += gravity * dt
            vx *= drag
            vy *= drag
            x += vx * dt
            y += vy * dt
            age += dt
        }

        fun isAlive() = age < lifetime
    }

    // ─── Light Ray Effects ────────────────────────────────────────────────────

    data class LightRay(
        val sourceX: Float,
        val sourceY: Float,
        val angle: Float,
        val length: Float = 500f,
        val spread: Float = 0.2f,
        var intensity: Float = 0.3f,
        var fadeOut: Float = 1.0f
    ) {
        fun draw(canvas: Canvas, paint: Paint) {
            val endX = sourceX + cos(angle) * length
            val endY = sourceY + sin(angle) * length

            paint.color = 0xFFFFDE92.toInt()
            paint.alpha = (intensity * fadOut * 150).toInt()
            paint.style = Paint.Style.FILL

            // Draw light cone
            val path = Path()
            path.moveTo(sourceX, sourceY)
            val leftAngle = angle - spread
            val rightAngle = angle + spread
            path.lineTo(
                sourceX + cos(leftAngle) * length,
                sourceY + sin(leftAngle) * length
            )
            path.lineTo(
                sourceX + cos(rightAngle) * length,
                sourceY + sin(rightAngle) * length
            )
            path.close()
            canvas.drawPath(path, paint)
        }

        private val fadOut get() = fadeOut
    }

    // ─── Scene Transition System ──────────────────────────────────────────────

    enum class SceneTransition {
        SUNRISE {
            override fun getColorGradient(progress: Float): Pair<Int, Int> {
                val start = 0xFF1A1A2E.toInt()
                val mid1 = 0xFFE67E22.toInt()
                val mid2 = 0xFFFF9FF5.toInt()
                val end = 0xFF87CEEB.toInt()

                return when {
                    progress < 0.33f -> {
                        val p = progress / 0.33f
                        Pair(
                            interpolateColor(start, mid1, p),
                            interpolateColor(0xFF000000.toInt(), 0xFFD2691E.toInt(), p)
                        )
                    }
                    progress < 0.66f -> {
                        val p = (progress - 0.33f) / 0.33f
                        Pair(
                            interpolateColor(mid1, mid2, p),
                            interpolateColor(0xFFD2691E.toInt(), 0xFFFF69B4.toInt(), p)
                        )
                    }
                    else -> {
                        val p = (progress - 0.66f) / 0.34f
                        Pair(
                            interpolateColor(mid2, end, p),
                            interpolateColor(0xFFFF69B4.toInt(), 0xFF87CEEB.toInt(), p)
                        )
                    }
                }
            }
        },
        SUNSET {
            override fun getColorGradient(progress: Float): Pair<Int, Int> {
                val start = 0xFF87CEEB.toInt()
                val mid1 = 0xFFFF69B4.toInt()
                val mid2 = 0xFFE67E22.toInt()
                val end = 0xFF1A1A2E.toInt()

                return when {
                    progress < 0.33f -> {
                        val p = progress / 0.33f
                        Pair(
                            interpolateColor(start, mid1, p),
                            interpolateColor(0xFF87CEEB.toInt(), 0xFFFF69B4.toInt(), p)
                        )
                    }
                    progress < 0.66f -> {
                        val p = (progress - 0.33f) / 0.33f
                        Pair(
                            interpolateColor(mid1, mid2, p),
                            interpolateColor(0xFFFF69B4.toInt(), 0xFFC41E3A.toInt(), p)
                        )
                    }
                    else -> {
                        val p = (progress - 0.66f) / 0.34f
                        Pair(
                            interpolateColor(mid2, end, p),
                            interpolateColor(0xFFC41E3A.toInt(), 0xFF000000.toInt(), p)
                        )
                    }
                }
            }
        },
        DUSK_TO_NIGHT {
            override fun getColorGradient(progress: Float): Pair<Int, Int> {
                val start = 0xFF2E4053.toInt()
                val mid = 0xFF34495E.toInt()
                val end = 0xFF0F0F1E.toInt()

                return if (progress < 0.5f) {
                    val p = progress / 0.5f
                    Pair(
                        interpolateColor(start, mid, p),
                        interpolateColor(0xFF2E4053.toInt(), 0xFF1C2833.toInt(), p)
                    )
                } else {
                    val p = (progress - 0.5f) / 0.5f
                    Pair(
                        interpolateColor(mid, end, p),
                        interpolateColor(0xFF1C2833.toInt(), 0xFF000000.toInt(), p)
                    )
                }
            }
        }

        ;

        companion object {
            private fun interpolateColor(from: Int, to: Int, progress: Float): Int {
                val fromR = Color.red(from)
                val fromG = Color.green(from)
                val fromB = Color.blue(from)
                val toR = Color.red(to)
                val toG = Color.green(to)
                val toB = Color.blue(to)

                return Color.rgb(
                    (fromR + (toR - fromR) * progress).toInt(),
                    (fromG + (toG - fromG) * progress).toInt(),
                    (fromB + (toB - fromB) * progress).toInt()
                )
            }
        }

        abstract fun getColorGradient(progress: Float): Pair<Int, Int>
    }

    // ─── Utility Functions ────────────────────────────────────────────────────

    // ─── Depth-Based Parallax ─────────────────────────────────────────────────

    data class ParallaxLayer(
        val depth: Float, // 0.0 = far back, 1.0 = front
        val paint: Paint,
        val drawFn: (canvas: Canvas, paint: Paint, offset: Float) -> Unit
    ) {
        fun draw(canvas: Canvas, cameraX: Float) {
            val offset = cameraX * (1f - depth)
            drawFn(canvas, paint, offset)
        }
    }
}
