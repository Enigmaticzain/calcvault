package com.calcvault.emotional.themes

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * ThemeApplicator
 *
 * Applies a live animated theme to ANY activity in CalcVault.
 *
 * Usage — paste this into any Activity's onCreate():
 *
 *   // Night Sky
 *   ThemeApplicator.applyNightSky(this)
 *
 *   // Doraemon
 *   ThemeApplicator.applyDoraemon(this)
 *
 *   // Nature Adventure
 *   ThemeApplicator.applyNature(this)
 *
 *   // Remove theme
 *   ThemeApplicator.detach(this)
 *
 * Works on: CalculatorActivity, PassphraseActivity, ChatActivity,
 *           CallActivity, MoodActivity, SettingsActivity, MainVaultActivity
 *           — literally any Activity that has a root FrameLayout.
 *
 * If the root is not a FrameLayout, it wraps it automatically.
 */
object ThemeApplicator {

    enum class ThemeChoice { NIGHT_SKY, DORAEMON, NATURE, NONE }

    // Global setting — change this to switch theme app-wide
    var activeTheme: ThemeChoice = ThemeChoice.NONE

    private val nightSkyThemes  = mutableMapOf<Int, NightSkyTheme>()
    private val doraemonThemes  = mutableMapOf<Int, DoraemonTheme>()
    private val natureThemes    = mutableMapOf<Int, NatureAdventureTheme>()

    // ── Apply ──────────────────────────────────────────────────────────────

    fun applyNightSky(activity: Activity) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        val theme = NightSkyTheme(activity)
        theme.attach(root)
        nightSkyThemes[activity.hashCode()] = theme
        activeTheme = ThemeChoice.NIGHT_SKY
    }

    fun applyDoraemon(activity: Activity) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        val theme = DoraemonTheme(activity)
        theme.attach(root)
        doraemonThemes[activity.hashCode()] = theme
        activeTheme = ThemeChoice.DORAEMON
    }

    fun applyNature(activity: Activity) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        val theme = NatureAdventureTheme(activity)
        theme.attach(root)
        natureThemes[activity.hashCode()] = theme
        activeTheme = ThemeChoice.NATURE
    }

    /**
     * Apply whatever the current global theme is.
     * Call this in every Activity.onCreate() to auto-apply theme everywhere.
     *
     *   override fun onCreate(...) {
     *       super.onCreate(...)
     *       ThemeApplicator.applyActive(this)  // ← add this one line
     *       ...
     *   }
     */
    fun applyActive(activity: Activity) {
        when (activeTheme) {
            ThemeChoice.NIGHT_SKY -> applyNightSky(activity)
            ThemeChoice.DORAEMON  -> applyDoraemon(activity)
            ThemeChoice.NATURE    -> applyNature(activity)
            ThemeChoice.NONE      -> { /* no theme */ }
        }
    }

    // ── Detach ─────────────────────────────────────────────────────────────

    fun detach(activity: Activity) {
        val key = activity.hashCode()
        nightSkyThemes.remove(key)?.detach()
        doraemonThemes.remove(key)?.detach()
        natureThemes.remove(key)?.detach()
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private fun getOrWrapRoot(activity: Activity): FrameLayout? {
        val decorView = activity.window.decorView as? ViewGroup ?: return null
        // Find the content view (id = android.R.id.content)
        val content   = decorView.findViewById<ViewGroup>(android.R.id.content) ?: return null

        return if (content is FrameLayout) {
            content
        } else {
            // Wrap existing content in a FrameLayout
            val parent = content.parent as? ViewGroup ?: return null
            val index  = parent.indexOfChild(content)
            val wrapper = FrameLayout(activity)
            parent.removeViewAt(index)
            wrapper.addView(content)
            parent.addView(wrapper, index, content.layoutParams)
            wrapper
        }
    }
}
