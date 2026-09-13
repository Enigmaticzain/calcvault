package com.calcvault.emotional.themes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View
import com.calcvault.storage.provider.StorageManager
import org.json.JSONObject
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

enum class SceneType {
    WATERFALL, SUNSET, BEACH, FOREST, CAMPING, CALM
}

enum class CharacterInteraction {
    SITTING_TOGETHER,
    TALKING_SOFTLY,
    HOLDING_HANDS,
    LEANING_HEAD,
    LIGHT_HUG,
    CUDDLING,
    WATCHING_VIEW
}

data class CoupleThemeConfig(
    val enableCharacters: Boolean = true,
    val enableInteractions: Boolean = true,
    val animationIntensity: Float = 1.0f,
    val autoSceneSwitch: Boolean = true,
    val sceneChangeIntervalMs: Long = 45_000L,
    val lockedScene: SceneType? = null
)

class DynamicCoupleTheme(private val context: Context) {
    private var config = CoupleThemeConfig()
    private var currentScene = SceneType.CALM
    private var currentInteraction = CharacterInteraction.SITTING_TOGETHER
    private var sceneChangeHandler = Handler(Looper.getMainLooper())
    private var sceneChangeRunnable: Runnable? = null
    private var lastMessageTime = 0L
    private var messageCount = 0

    companion object {
        private const val CONFIG_KEY = "prefs/couple_theme_config"
        private const val SCENE_KEY = "prefs/couple_current_scene"
        private const val INTERACTION_KEY = "prefs/couple_interaction"
    }

    init {
        loadConfig()
        startSceneRotation()
    }

    fun setConfig(newConfig: CoupleThemeConfig) {
        config = newConfig
        saveConfig()
        if (!config.autoSceneSwitch) {
            stopSceneRotation()
        } else {
            startSceneRotation()
        }
    }

    fun getConfig() = config

    fun setScene(scene: SceneType) {
        currentScene = scene
        saveScene()
    }

    fun getScene() = currentScene

    fun setInteraction(interaction: CharacterInteraction) {
        currentInteraction = interaction
        saveInteraction()
    }

    fun getInteraction() = currentInteraction

    fun onMessageReceived(from: String, content: String) {
        lastMessageTime = System.currentTimeMillis()
        messageCount++

        val interaction = when {
            content.contains("miss", ignoreCase = true) -> CharacterInteraction.HOLDING_HANDS
            content.contains("love", ignoreCase = true) -> CharacterInteraction.LIGHT_HUG
            content.contains("good night", ignoreCase = true) -> CharacterInteraction.CUDDLING
            content.contains("good morning", ignoreCase = true) -> CharacterInteraction.WATCHING_VIEW
            else -> currentInteraction
        }
        setInteraction(interaction)
    }

    fun onMoodUpdate(mood: String) {
        val scene = when {
            mood.contains("LOVE", ignoreCase = true) -> SceneType.SUNSET
            mood.contains("BUSY", ignoreCase = true) -> SceneType.CALM
            mood.contains("HAPPY", ignoreCase = true) -> SceneType.BEACH
            mood.contains("RELAXED", ignoreCase = true) -> SceneType.WATERFALL
            else -> currentScene
        }
        if (config.lockedScene == null) {
            setScene(scene)
        }
    }

    private fun startSceneRotation() {
        if (!config.autoSceneSwitch) return
        sceneChangeRunnable = Runnable {
            if (config.lockedScene == null) {
                val scenes = SceneType.values()
                val nextIndex = (scenes.indexOf(currentScene) + 1) % scenes.size
                currentScene = scenes[nextIndex]
                saveScene()
            }
            sceneChangeHandler.postDelayed(sceneChangeRunnable!!, config.sceneChangeIntervalMs)
        }
        sceneChangeHandler.postDelayed(sceneChangeRunnable!!, config.sceneChangeIntervalMs)
    }

    private fun stopSceneRotation() {
        sceneChangeRunnable?.let { sceneChangeHandler.removeCallbacks(it) }
        sceneChangeRunnable = null
    }

    private fun saveConfig() {
        try {
            val json = JSONObject().apply {
                put("enableCharacters", config.enableCharacters)
                put("enableInteractions", config.enableInteractions)
                put("animationIntensity", config.animationIntensity)
                put("autoSceneSwitch", config.autoSceneSwitch)
                put("sceneChangeIntervalMs", config.sceneChangeIntervalMs)
                config.lockedScene?.let { put("lockedScene", it.name) }
            }
            StorageManager.write(CONFIG_KEY, json.toString().toByteArray())
        } catch (e: Exception) {}
    }

    private fun loadConfig() {
        try {
            val data = StorageManager.read(CONFIG_KEY) ?: return
            val json = JSONObject(String(data))
            config = CoupleThemeConfig(
                enableCharacters = json.optBoolean("enableCharacters", true),
                enableInteractions = json.optBoolean("enableInteractions", true),
                animationIntensity = json.optDouble("animationIntensity", 1.0).toFloat(),
                autoSceneSwitch = json.optBoolean("autoSceneSwitch", true),
                sceneChangeIntervalMs = json.optLong("sceneChangeIntervalMs", 45_000L),
                lockedScene = json.optString("lockedScene").takeIf { it.isNotEmpty() }?.let {
                    try { SceneType.valueOf(it) } catch (e: Exception) { null }
                }
            )
        } catch (e: Exception) {}
    }

    private fun saveScene() {
        try {
            StorageManager.write(SCENE_KEY, currentScene.name.toByteArray())
        } catch (e: Exception) {}
    }

    private fun saveInteraction() {
        try {
            StorageManager.write(INTERACTION_KEY, currentInteraction.name.toByteArray())
        } catch (e: Exception) {}
    }

    fun cleanup() {
        stopSceneRotation()
    }
}

class SceneBackgroundView(context: Context) : View(context) {
    private var scene = SceneType.CALM
    private var animationTime = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    fun setScene(newScene: SceneType) {
        scene = newScene
        animationTime = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        animationTime += 0.016f
        if (animationTime > 1f) animationTime = 0f

        when (scene) {
            SceneType.WATERFALL -> drawWaterfall(canvas)
            SceneType.SUNSET -> drawSunset(canvas)
            SceneType.BEACH -> drawBeach(canvas)
            SceneType.FOREST -> drawForest(canvas)
            SceneType.CAMPING -> drawCamping(canvas)
            SceneType.CALM -> drawCalm(canvas)
        }
    }

    private fun drawWaterfall(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.parseColor("#1a472a")
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = Color.parseColor("#2d5a3d")
        for (i in 0..3) {
            val offset = (animationTime * 100 + i * 50) % h
            canvas.drawRect(w * 0.4f, offset, w * 0.6f, offset + 30, paint)
        }

        paint.color = Color.parseColor("#87ceeb")
        for (i in 0..5) {
            val x = w * 0.3f + i * 20
            val y = h * 0.6f + sin(animationTime * PI * 2 + i).toFloat() * 10
            canvas.drawCircle(x, y, 3f, paint)
        }
    }

    private fun drawSunset(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.parseColor("#ff6b35")
        canvas.drawRect(0f, 0f, w, h * 0.5f, paint)

        paint.color = Color.parseColor("#f7931e")
        canvas.drawRect(0f, h * 0.5f, w, h, paint)

        paint.color = Color.parseColor("#ffd700")
        val sunY = h * 0.4f + sin(animationTime * PI * 0.5).toFloat() * 20
        canvas.drawCircle(w * 0.5f, sunY, 60f, paint)

        paint.color = Color.parseColor("#ff8c42")
        for (i in 0..2) {
            val radius = 60f + i * 15
            paint.alpha = (255 * (1 - i * 0.3)).toInt()
            canvas.drawCircle(w * 0.5f, sunY, radius, paint)
        }
    }

    private fun drawBeach(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.parseColor("#87ceeb")
        canvas.drawRect(0f, 0f, w, h * 0.6f, paint)

        paint.color = Color.parseColor("#f4a460")
        canvas.drawRect(0f, h * 0.6f, w, h, paint)

        paint.color = Color.parseColor("#4a90e2")
        for (i in 0..3) {
            val waveY = h * 0.55f + sin(animationTime * PI * 2 + i * PI * 0.5).toFloat() * 15
            canvas.drawRect(0f, waveY, w, waveY + 8, paint)
        }
    }

    private fun drawForest(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.parseColor("#0d3b0d")
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = Color.parseColor("#1a5c1a")
        for (i in 0..4) {
            val x = w * 0.2f + i * w * 0.15f
            val treeHeight = h * 0.6f
            canvas.drawRect(x - 10, h - treeHeight, x + 10, h, paint)

            paint.color = Color.parseColor("#2d7a2d")
            canvas.drawCircle(x, h - treeHeight - 30, 40f, paint)
            paint.color = Color.parseColor("#1a5c1a")
        }

        paint.color = Color.parseColor("#8b7355")
        canvas.drawRect(0f, h * 0.8f, w, h, paint)
    }

    private fun drawCamping(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.parseColor("#1a1a2e")
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = Color.parseColor("#16213e")
        canvas.drawRect(0f, h * 0.7f, w, h, paint)

        paint.color = Color.parseColor("#ffd700")
        val fireY = h * 0.65f
        val flameOffset = sin(animationTime * PI * 3).toFloat() * 10
        canvas.drawCircle(w * 0.5f, fireY + flameOffset, 20f, paint)

        paint.color = Color.parseColor("#ff8c00")
        canvas.drawCircle(w * 0.5f, fireY + flameOffset - 10, 15f, paint)

        paint.color = Color.parseColor("#ffff99")
        for (i in 0..20) {
            val angle = (animationTime * PI * 2 + i * PI * 0.1).toFloat()
            val x = w * 0.5f + cos(angle) * 30
            val y = fireY + sin(angle) * 30
            canvas.drawCircle(x, y, 2f, paint)
        }
    }

    private fun drawCalm(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.parseColor("#0f172a")
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = Color.parseColor("#1e293b")
        for (i in 0..10) {
            val x = (i * w * 0.1f + animationTime * 20) % w
            val y = h * 0.3f + sin(animationTime * PI + i * 0.5).toFloat() * 20
            canvas.drawCircle(x, y, 2f, paint)
        }
    }

    fun startAnimation() {
        animationRunnable = Runnable {
            invalidate()
            handler.postDelayed(animationRunnable!!, 16)
        }
        handler.post(animationRunnable!!)
    }

    fun stopAnimation() {
        animationRunnable?.let { handler.removeCallbacks(it) }
    }
}

class CoupleCharacterView(context: Context) : View(context) {
    private var interaction = CharacterInteraction.SITTING_TOGETHER
    private var animationTime = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    fun setInteraction(newInteraction: CharacterInteraction) {
        interaction = newInteraction
        animationTime = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        animationTime += 0.016f
        if (animationTime > 1f) animationTime = 0f

        when (interaction) {
            CharacterInteraction.SITTING_TOGETHER -> drawSittingTogether(canvas)
            CharacterInteraction.TALKING_SOFTLY -> drawTalkingSoftly(canvas)
            CharacterInteraction.HOLDING_HANDS -> drawHoldingHands(canvas)
            CharacterInteraction.LEANING_HEAD -> drawLeaningHead(canvas)
            CharacterInteraction.LIGHT_HUG -> drawLightHug(canvas)
            CharacterInteraction.CUDDLING -> drawCuddling(canvas)
            CharacterInteraction.WATCHING_VIEW -> drawWatchingView(canvas)
        }
    }

    private fun drawSittingTogether(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawGuy(canvas, w * 0.25f, h * 0.6f, 0f)
        drawGirl(canvas, w * 0.75f, h * 0.6f, 0f)
    }

    private fun drawTalkingSoftly(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bob = sin(animationTime * PI * 2).toFloat() * 5

        drawGuy(canvas, w * 0.25f, h * 0.6f + bob, 0f)
        drawGirl(canvas, w * 0.75f, h * 0.6f - bob, 0f)
    }

    private fun drawHoldingHands(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawGuy(canvas, w * 0.3f, h * 0.6f, 0f)
        drawGirl(canvas, w * 0.7f, h * 0.6f, 0f)

        paint.color = Color.parseColor("#ffb6c1")
        paint.strokeWidth = 4f
        canvas.drawLine(w * 0.35f, h * 0.65f, w * 0.65f, h * 0.65f, paint)
    }

    private fun drawLeaningHead(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawGuy(canvas, w * 0.25f, h * 0.6f, 0f)
        drawGirl(canvas, w * 0.75f, h * 0.55f, -15f)
    }

    private fun drawLightHug(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val hug = sin(animationTime * PI * 2).toFloat() * 3

        drawGuy(canvas, w * 0.35f - hug, h * 0.6f, 0f)
        drawGirl(canvas, w * 0.65f + hug, h * 0.6f, 0f)
    }

    private fun drawCuddling(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawGuy(canvas, w * 0.3f, h * 0.65f, 0f)
        drawGirl(canvas, w * 0.7f, h * 0.6f, -20f)
    }

    private fun drawWatchingView(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sway = sin(animationTime * PI * 0.5).toFloat() * 2

        drawGuy(canvas, w * 0.25f + sway, h * 0.6f, 0f)
        drawGirl(canvas, w * 0.75f - sway, h * 0.6f, 0f)
    }

    private fun drawGuy(canvas: Canvas, x: Float, y: Float, tilt: Float) {
        paint.color = Color.parseColor("#8b7355")
        canvas.drawCircle(x, y - 40, 15f, paint)

        paint.color = Color.parseColor("#d4a574")
        canvas.drawRect(x - 12, y - 20, x + 12, y + 30, paint)

        paint.color = Color.parseColor("#8b7355")
        canvas.drawRect(x - 15, y + 30, x - 5, y + 50, paint)
        canvas.drawRect(x + 5, y + 30, x + 15, y + 50, paint)
    }

    private fun drawGirl(canvas: Canvas, x: Float, y: Float, tilt: Float) {
        paint.color = Color.parseColor("#d4a574")
        canvas.drawCircle(x, y - 40, 15f, paint)

        paint.color = Color.parseColor("#ff69b4")
        canvas.drawRect(x - 12, y - 20, x + 12, y + 35, paint)

        paint.color = Color.parseColor("#8b7355")
        canvas.drawRect(x - 15, y + 35, x - 5, y + 55, paint)
        canvas.drawRect(x + 5, y + 35, x + 15, y + 55, paint)
    }

    fun startAnimation() {
        animationRunnable = Runnable {
            invalidate()
            handler.postDelayed(animationRunnable!!, 16)
        }
        handler.post(animationRunnable!!)
    }

    fun stopAnimation() {
        animationRunnable?.let { handler.removeCallbacks(it) }
    }
}
