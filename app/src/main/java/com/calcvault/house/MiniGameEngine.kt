package com.calcvault.house

import android.graphics.*
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs

/**
 * MiniGameEngine.kt — Embedded mini-games: Bubble Pop, Food Catcher, Memory Match, Trampoline
 */

enum class MiniGameType { BUBBLE_POP, FOOD_CATCHER, MEMORY_MATCH, TRAMPOLINE }
enum class MiniGameState { INACTIVE, PLAYING, PAUSED, GAME_OVER }

data class MiniGameResult(val score: Int, val coinsEarned: Int, val happinessBoost: Float)

abstract class BaseMiniGame(val type: MiniGameType) {
    var state = MiniGameState.INACTIVE
    var score = 0
    var timeLeft = 30f // seconds
    var highScore = 0

    open fun start() { state = MiniGameState.PLAYING; score = 0; timeLeft = 30f }
    open fun update(dt: Float) { if (state == MiniGameState.PLAYING) { timeLeft -= dt; if (timeLeft <= 0) end() } }
    abstract fun onTouch(x: Float, y: Float): Boolean
    abstract fun draw(canvas: Canvas, w: Float, h: Float)
    open fun end(): MiniGameResult {
        state = MiniGameState.GAME_OVER
        if (score > highScore) highScore = score
        return MiniGameResult(score, score / 5, score * 0.5f)
    }
}

// ═══════════════════════════════════════════════════════════════
// BUBBLE POP — Tap floating bubbles
// ═══════════════════════════════════════════════════════════════
class BubblePopGame : BaseMiniGame(MiniGameType.BUBBLE_POP) {
    data class Bubble(var x: Float, var y: Float, var r: Float, var speed: Float, var color: Int, var life: Float = 1f)

    private val bubbles = mutableListOf<Bubble>()
    private val random = java.util.Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var spawnTimer = 0f
    private val colors = intArrayOf(
        Color.parseColor("#FF4081"), Color.parseColor("#7C4DFF"), Color.parseColor("#00BCD4"),
        Color.parseColor("#FFD740"), Color.parseColor("#69F0AE"), Color.parseColor("#FF6E40")
    )

    override fun start() { super.start(); bubbles.clear(); spawnTimer = 0f; timeLeft = 25f }

    override fun update(dt: Float) {
        super.update(dt)
        if (state != MiniGameState.PLAYING) return
        spawnTimer += dt
        if (spawnTimer > 0.6f && bubbles.size < 12) {
            spawnTimer = 0f
            bubbles.add(Bubble(random.nextFloat() * 0.8f + 0.1f, 1.1f,
                20f + random.nextFloat() * 25f, 0.4f + random.nextFloat() * 0.6f,
                colors[random.nextInt(colors.size)]))
        }
        bubbles.forEach { it.y -= it.speed * dt; it.x += sin((it.y * 3f).toDouble()).toFloat() * dt * 0.05f }
        bubbles.removeAll { it.y < -0.1f || it.life <= 0f }
    }

    override fun onTouch(x: Float, y: Float): Boolean {
        val hit = bubbles.firstOrNull { b ->
            val dx = x - b.x; val dy = y - b.y
            dx * dx + dy * dy < (b.r / 300f) * (b.r / 300f) + 0.005f
        }
        if (hit != null) { bubbles.remove(hit); score += 10; return true }
        return false
    }

    override fun draw(canvas: Canvas, w: Float, h: Float) {
        bubbles.forEach { b ->
            paint.color = b.color; paint.alpha = 200; paint.style = Paint.Style.FILL
            canvas.drawCircle(b.x * w, b.y * h, b.r, paint)
            paint.color = Color.argb(80, 255, 255, 255); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            canvas.drawCircle(b.x * w, b.y * h, b.r, paint)
            paint.color = Color.argb(120, 255, 255, 255); paint.style = Paint.Style.FILL
            canvas.drawCircle(b.x * w - b.r * 0.25f, b.y * h - b.r * 0.25f, b.r * 0.2f, paint)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// FOOD CATCHER — Catch falling food
// ═══════════════════════════════════════════════════════════════
class FoodCatcherGame : BaseMiniGame(MiniGameType.FOOD_CATCHER) {
    data class FallingFood(var x: Float, var y: Float, var speed: Float, val emoji: String, val value: Int)

    private val foods = mutableListOf<FallingFood>()
    private var catcherX = 0.5f
    private val random = java.util.Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; textAlign = Paint.Align.CENTER }
    private var spawnTimer = 0f
    private val foodEmojis = listOf("🍎" to 5, "🍕" to 10, "🍩" to 8, "🍓" to 6, "🌮" to 12, "🍪" to 7)

    override fun start() { super.start(); foods.clear(); catcherX = 0.5f; timeLeft = 30f }

    override fun update(dt: Float) {
        super.update(dt)
        if (state != MiniGameState.PLAYING) return
        spawnTimer += dt
        if (spawnTimer > 0.8f && foods.size < 8) {
            spawnTimer = 0f
            val (e, v) = foodEmojis[random.nextInt(foodEmojis.size)]
            foods.add(FallingFood(random.nextFloat() * 0.8f + 0.1f, -0.05f, 0.3f + random.nextFloat() * 0.4f, e, v))
        }
        foods.forEach { it.y += it.speed * dt }
        val caught = foods.filter { abs(it.x - catcherX) < 0.08f && it.y > 0.82f && it.y < 0.95f }
        score += caught.sumOf { it.value }
        foods.removeAll(caught.toSet())
        foods.removeAll { it.y > 1.1f }
    }

    override fun onTouch(x: Float, y: Float): Boolean { catcherX = x; return true }

    override fun draw(canvas: Canvas, w: Float, h: Float) {
        // Catcher basket
        paint.color = Color.parseColor("#795548"); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF((catcherX - 0.06f) * w, h * 0.85f, (catcherX + 0.06f) * w, h * 0.9f), 8f, 8f, paint)
        // Falling food
        foods.forEach { f -> canvas.drawText(f.emoji, f.x * w, f.y * h, textP) }
    }
}

// ═══════════════════════════════════════════════════════════════
// TRAMPOLINE JUMP — Tap to bounce higher
// ═══════════════════════════════════════════════════════════════
class TrampolineGame : BaseMiniGame(MiniGameType.TRAMPOLINE) {
    private var charY = 0.7f
    private var velocity = 0f
    private var bounceCount = 0
    private var bestHeight = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; textAlign = Paint.Align.CENTER }

    override fun start() { super.start(); charY = 0.7f; velocity = 0f; bounceCount = 0; bestHeight = 0f; timeLeft = 20f }

    override fun update(dt: Float) {
        super.update(dt)
        if (state != MiniGameState.PLAYING) return
        velocity += 1.2f * dt // gravity
        charY += velocity * dt
        if (charY >= 0.75f) { charY = 0.75f; velocity = -velocity * 0.3f } // natural bounce
        val height = (0.75f - charY).coerceAtLeast(0f)
        if (height > bestHeight) { bestHeight = height; score = (bestHeight * 200).toInt() }
    }

    override fun onTouch(x: Float, y: Float): Boolean {
        if (charY >= 0.7f) { velocity = -1.8f; bounceCount++; score += 5 }
        return true
    }

    override fun draw(canvas: Canvas, w: Float, h: Float) {
        // Trampoline
        paint.color = Color.parseColor("#FF5722"); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(w * 0.25f, h * 0.82f, w * 0.75f, h * 0.86f), 6f, 6f, paint)
        paint.color = Color.parseColor("#BF360C")
        canvas.drawRect(RectF(w * 0.28f, h * 0.86f, w * 0.32f, h * 0.92f), paint)
        canvas.drawRect(RectF(w * 0.68f, h * 0.86f, w * 0.72f, h * 0.92f), paint)
        // Character
        canvas.drawText("😸", w * 0.5f, charY * h, textP)
    }
}

// ═══════════════════════════════════════════════════════════════
// MINI-GAME MANAGER
// ═══════════════════════════════════════════════════════════════
class MiniGameManager {
    private val games = mapOf(
        MiniGameType.BUBBLE_POP to BubblePopGame(),
        MiniGameType.FOOD_CATCHER to FoodCatcherGame(),
        MiniGameType.TRAMPOLINE to TrampolineGame()
    )
    var activeGame: BaseMiniGame? = null; private set
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = Color.WHITE; textAlign = Paint.Align.LEFT }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun startGame(type: MiniGameType) { activeGame = games[type]; activeGame?.start() }
    fun stopGame() { activeGame?.state = MiniGameState.INACTIVE; activeGame = null }
    fun isPlaying() = activeGame?.state == MiniGameState.PLAYING

    fun update(dt: Float) { activeGame?.update(dt) }

    fun onTouch(x: Float, y: Float, viewW: Float, viewH: Float): Boolean {
        val game = activeGame ?: return false
        if (game.state == MiniGameState.GAME_OVER) { stopGame(); return true }
        return game.onTouch(x / viewW, y / viewH)
    }

    fun draw(canvas: Canvas, w: Float, h: Float) {
        val game = activeGame ?: return
        // Semi-transparent overlay
        bgPaint.color = Color.argb(180, 20, 15, 30); canvas.drawRect(0f, 0f, w, h, bgPaint)
        game.draw(canvas, w, h)
        // HUD
        hudPaint.color = Color.WHITE; hudPaint.textSize = 18f
        canvas.drawText("Score: ${game.score}", 20f, 40f, hudPaint)
        canvas.drawText("Time: ${game.timeLeft.toInt()}s", w - 120f, 40f, hudPaint)
        if (game.state == MiniGameState.GAME_OVER) {
            hudPaint.textSize = 28f; hudPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Game Over! Score: ${game.score}", w / 2, h / 2, hudPaint)
            hudPaint.textSize = 16f
            canvas.drawText("Tap to continue", w / 2, h / 2 + 35f, hudPaint)
            hudPaint.textAlign = Paint.Align.LEFT
        }
    }

    fun getLastResult(): MiniGameResult? {
        val game = activeGame ?: return null
        return if (game.state == MiniGameState.GAME_OVER) game.end() else null
    }
}
