package com.calcvault.emotional

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.json.JSONObject
import kotlin.math.max

/**
 * Word-triggered global emotion engine.
 *
 * Handles scene-level chat effects with throttling, priority resolution,
 * defaults + custom triggers, and optional real-time typing previews.
 */
class WordTriggeredEmotionEngine(
    private val context: Context,
    private val rootView: ViewGroup,
    private val emotionalEngine: EmotionalAnimationEngine?
) {

    private data class EffectMatch(
        val effect: TriggerEffect,
        val keyword: String
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val customEffects = mutableListOf<TriggerEffect>()
    private var defaultEffects = WordEffectDefaults.build()

    private var wordEffectsEnabled = true
    private var overlayContainer: FrameLayout? = null

    private var lastGlobalTriggerMs = 0L
    private val lastEffectTriggerMs = mutableMapOf<String, Long>()
    private var lastTypingEffectId = ""
    private var lastTypingTriggerMs = 0L

    fun refreshDefaultEffects(localNickname: String, partnerNickname: String) {
        defaultEffects = WordEffectDefaults.build(localNickname, partnerNickname)
    }

    fun setWordEffectsEnabled(enabled: Boolean) {
        wordEffectsEnabled = enabled
    }

    fun isWordEffectsEnabled(): Boolean = wordEffectsEnabled

    fun loadCustomEffectsFromJson(json: String) {
        customEffects.clear()
        customEffects += WordEffectCodec.decode(json)
    }

    fun exportCustomEffectsJson(): String = WordEffectCodec.encode(customEffects)

    fun getCustomEffects(): List<TriggerEffect> = customEffects.toList()

    fun getAllEffects(): List<TriggerEffect> {
        return (customEffects + defaultEffects).distinctBy { it.effectId }
    }

    fun upsertCustomEffect(effect: TriggerEffect) {
        val normalized = effect.copy(
            triggerWords = effect.normalizedWords(),
            intensity = effect.intensity.coerceIn(0.1f, 1f),
            duration = effect.duration.coerceIn(500L, 10_000L),
            priority = effect.priority.coerceIn(1, 100)
        )
        if (normalized.triggerWords.isEmpty()) return

        val idx = customEffects.indexOfFirst { it.effectId == normalized.effectId }
        if (idx >= 0) {
            customEffects[idx] = normalized
        } else {
            customEffects.add(normalized)
        }
    }

    fun removeCustomEffect(effectId: String): Boolean {
        return customEffects.removeAll { it.effectId == effectId }
    }

    fun setCustomEffectEnabled(effectId: String, enabled: Boolean): Boolean {
        val idx = customEffects.indexOfFirst { it.effectId == effectId }
        if (idx < 0) return false
        customEffects[idx] = customEffects[idx].withEnabled(enabled)
        return true
    }

    fun resetCustomEffects() {
        customEffects.clear()
    }

    fun onTypingText(rawText: String) {
        triggerForText(rawText, isRealtime = true)
    }

    fun onMessage(rawText: String) {
        triggerForText(rawText, isRealtime = false)
    }

    fun previewEffect(effect: TriggerEffect) {
        applyEffect(effect, isRealtime = false, force = true)
    }

    private fun triggerForText(rawText: String, isRealtime: Boolean) {
        if (!wordEffectsEnabled) return
        if (rawText.isBlank()) return

        val plain = extractPlainText(rawText)
        if (plain.isBlank()) return

        val normalized = plain.lowercase()
        val matches = findMatches(normalized)
        if (matches.isEmpty()) return

        val selected = matches.maxWithOrNull(
            compareBy<EffectMatch> { it.effect.priority }
                .thenBy { it.effect.intensity }
                .thenBy { it.keyword.length }
        ) ?: return

        if (isRealtime) {
            val now = SystemClock.elapsedRealtime()
            if (selected.effect.effectId == lastTypingEffectId && now - lastTypingTriggerMs < 2_000L) {
                return
            }
            lastTypingEffectId = selected.effect.effectId
            lastTypingTriggerMs = now
        }

        applyEffect(selected.effect, isRealtime = isRealtime)
    }

    private fun findMatches(normalized: String): List<EffectMatch> {
        val matches = mutableListOf<EffectMatch>()
        for (effect in getAllEffects()) {
            if (!effect.isEnabled) continue
            val words = effect.normalizedWords()
            val matchedWord = words.firstOrNull { normalized.contains(it) } ?: continue
            matches += EffectMatch(effect, matchedWord)
        }
        return matches
    }

    private fun applyEffect(effect: TriggerEffect, isRealtime: Boolean, force: Boolean = false) {
        if (!force && !canTrigger(effect, isRealtime)) return

        mainHandler.post {
            when (effect.animationType) {
                AnimationType.LOVE_AURA -> runLoveEffect(effect, isRealtime)
                AnimationType.GOOD_NIGHT -> runGoodNightEffect(effect)
                AnimationType.GOOD_MORNING -> runGoodMorningEffect(effect)
                AnimationType.ANGER_PULSE -> runAngerEffect(effect)
                AnimationType.MISS_FADE -> runMissEffect(effect, isRealtime)
                AnimationType.PARTICLE_WAVE -> runParticleWave(effect, isRealtime)
            }
        }
    }

    private fun canTrigger(effect: TriggerEffect, isRealtime: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        val globalCooldownMs = if (isRealtime) 1_700L else 800L
        if (now - lastGlobalTriggerMs < globalCooldownMs) return false

        val perEffectCooldown = max(1_200L, effect.duration / 2L)
        val lastForEffect = lastEffectTriggerMs[effect.effectId] ?: 0L
        if (now - lastForEffect < perEffectCooldown) return false

        lastGlobalTriggerMs = now
        lastEffectTriggerMs[effect.effectId] = now
        return true
    }

    private fun runLoveEffect(effect: TriggerEffect, isRealtime: Boolean) {
        renderOverlay(effect, 0xFFFF77AA.toInt(), peakAlpha = 0.16f)
        val hearts = (if (isRealtime) 5 else 10) + (effect.intensity * 16f).toInt()
        emotionalEngine?.triggerFloatingHearts(count = hearts.coerceAtMost(28))
    }

    private fun runGoodNightEffect(effect: TriggerEffect) {
        renderOverlay(effect, 0xFF070D2D.toInt(), peakAlpha = 0.22f)
        val stars = (6 + effect.intensity * 14f).toInt().coerceAtMost(22)
        emotionalEngine?.triggerStarfallAnimation(stars)
    }

    private fun runGoodMorningEffect(effect: TriggerEffect) {
        renderGradientOverlay(
            effect = effect,
            topColor = 0x66FFD36D,
            bottomColor = 0x00FFF3CF,
            peakAlpha = 0.26f
        )
        emotionalEngine?.triggerSunriseGlow()
    }

    private fun runAngerEffect(effect: TriggerEffect) {
        renderOverlay(effect, 0xFFFF2A2A.toInt(), peakAlpha = 0.24f)
        subtleScreenShake(effect)
        maybeVibrate(effect)
    }

    private fun runMissEffect(effect: TriggerEffect, isRealtime: Boolean) {
        renderOverlay(effect, 0xFF6A77A8.toInt(), peakAlpha = 0.18f)
        val particles = (if (isRealtime) 4 else 8) + (effect.intensity * 12f).toInt()
        emotionalEngine?.triggerCustomEmojiAnimation(
            emoji = "✨",
            intensity = particles.coerceAtMost(22),
            direction = "UP",
            speed = "SLOW"
        )
    }

    private fun runParticleWave(effect: TriggerEffect, isRealtime: Boolean) {
        val particles = (if (isRealtime) 6 else 10) + (effect.intensity * 20f).toInt()
        emotionalEngine?.triggerCustomEmojiAnimation(
            emoji = "✨",
            intensity = particles.coerceAtMost(28),
            direction = "RANDOM",
            speed = if (isRealtime) "FAST" else "MEDIUM"
        )
    }

    private fun renderOverlay(effect: TriggerEffect, color: Int, peakAlpha: Float) {
        val overlayHost = ensureOverlayHost()
        val overlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(color)
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        overlayHost.addView(overlay)

        val maxAlpha = if (effect.overlayType == OverlayType.NONE) 0f else peakAlpha * effect.intensity
        ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, maxAlpha.coerceAtMost(0.4f), 0f).apply {
            duration = effect.duration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlayHost.removeView(overlay)
                }
            })
            start()
        }
    }

    private fun renderGradientOverlay(effect: TriggerEffect, topColor: Int, bottomColor: Int, peakAlpha: Float) {
        val overlayHost = ensureOverlayHost()
        val overlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(topColor, bottomColor)
            )
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        overlayHost.addView(overlay)

        ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, (peakAlpha * effect.intensity).coerceAtMost(0.45f), 0f).apply {
            duration = effect.duration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlayHost.removeView(overlay)
                }
            })
            start()
        }
    }

    private fun subtleScreenShake(effect: TriggerEffect) {
        val strength = (5f + effect.intensity * 10f).coerceAtMost(14f)
        AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(rootView, View.TRANSLATION_X, 0f, -strength).setDuration(40),
                ObjectAnimator.ofFloat(rootView, View.TRANSLATION_X, -strength, strength).setDuration(70),
                ObjectAnimator.ofFloat(rootView, View.TRANSLATION_X, strength, -strength / 2f).setDuration(70),
                ObjectAnimator.ofFloat(rootView, View.TRANSLATION_X, -strength / 2f, 0f).setDuration(50)
            )
            start()
        }
    }

    private fun maybeVibrate(effect: TriggerEffect) {
        if (effect.intensity < 0.6f) return

        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(28L)
            }
        }
    }

    private fun ensureOverlayHost(): FrameLayout {
        val existing = overlayContainer
        if (existing != null && existing.parent === rootView) return existing

        val host = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        rootView.addView(host)
        overlayContainer = host
        return host
    }

    private fun extractPlainText(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val json = runCatching { JSONObject(trimmed) }.getOrNull()
            val text = json?.optString("text")
            if (!text.isNullOrBlank()) return text
        }
        return rawText
    }
}
