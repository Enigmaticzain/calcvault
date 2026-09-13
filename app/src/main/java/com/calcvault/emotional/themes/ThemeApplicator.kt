package com.calcvault.emotional.themes

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import com.calcvault.ui.ambient.AmbientAnimationView
import com.calcvault.utils.SessionManager

/**
 * ThemeApplicator
 *
 * Applies a live animated theme to ANY activity in CalcVault.
 * Now unified to support both dedicated themes and ambient engine scenes.
 * Strips backgrounds recursively to ensure theme visibility.
 */
object ThemeApplicator {

    enum class ThemeChoice { NIGHT_SKY, DORAEMON, NATURE_ADVENTURE, AMBIENT_NATURE, AMBIENT_COMPANION, COUPLE, NONE }

    // Global setting — linked to SessionManager
    var activeTheme: ThemeChoice
        get() = when (SessionManager.activeAnimatedTheme) {
            "NIGHT_SKY" -> ThemeChoice.NIGHT_SKY
            "DORAEMON" -> ThemeChoice.DORAEMON
            "NATURE_ADVENTURE" -> ThemeChoice.NATURE_ADVENTURE
            "AMBIENT_NATURE" -> ThemeChoice.AMBIENT_NATURE
            "AMBIENT_COMPANION" -> ThemeChoice.AMBIENT_COMPANION
            "COUPLE" -> ThemeChoice.COUPLE
            else -> ThemeChoice.NONE
        }
        set(value) {
            SessionManager.activeAnimatedTheme = value.name
        }

    private val nightSkyThemes = mutableMapOf<Int, NightSkyTheme>()
    private val doraemonThemes = mutableMapOf<Int, DoraemonTheme>()
    private val natureThemes = mutableMapOf<Int, NatureAdventureTheme>()
    private val ambientThemes = mutableMapOf<Int, AmbientAnimationView>()
    private val coupleThemes = mutableMapOf<Int, Activity>()

    // ── Apply ──────────────────────────────────────────────────────────────

    fun applyNightSky(activity: Activity) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        val theme = NightSkyTheme(activity)
        theme.attach(root)
        nightSkyThemes[activity.hashCode()] = theme
    }

    fun applyDoraemon(activity: Activity) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        val theme = DoraemonTheme(activity)
        theme.attach(root)
        doraemonThemes[activity.hashCode()] = theme
    }

    fun applyNatureAdventure(activity: Activity) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        val theme = NatureAdventureTheme(activity)
        theme.attach(root)
        natureThemes[activity.hashCode()] = theme
    }

    fun applyAmbient(activity: Activity, scene: AmbientAnimationView.Theme) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        val ambient = AmbientAnimationView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            currentTheme = scene
            if (scene == AmbientAnimationView.Theme.EMOTIONAL_COMPANION) {
                AmbientAnimationView.configureCalmPreset(this)
            }
        }
        root.addView(ambient, 0)
        ambientThemes[activity.hashCode()] = ambient
    }

    fun applyCouple(activity: Activity) {
        val root = getOrWrapRoot(activity) ?: return
        detach(activity)
        CoupleThemeApplicator.activate(activity, root)
        coupleThemes[activity.hashCode()] = activity
    }

    /**
     * Applies the currently selected live theme.
     * IMPORTANT: This MUST be called AFTER Activity.setContentView() * because setContentView clears the root container.
     */
    fun applyActive(activity: Activity) {
        // STEALTH GUARD: Never apply vault themes to the Calculator screen to avoid looking suspicious
        if (activity.javaClass.simpleName == "CalculatorActivity") {
            return
        }

        val choice = activeTheme

        if (choice != ThemeChoice.NONE) {
            // Ensure window itself doesn't have a solid background color
            activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        // Force activity content backgrounds to transparent/semi-transparent so theme is visible
        makeActivityTransparent(activity)

        if (choice == ThemeChoice.NONE) {
            detach(activity)
            return
        }

        when (choice) {
            ThemeChoice.NIGHT_SKY -> applyNightSky(activity)
            ThemeChoice.DORAEMON -> applyDoraemon(activity)
            ThemeChoice.NATURE_ADVENTURE -> applyNatureAdventure(activity)
            ThemeChoice.AMBIENT_NATURE -> applyAmbient(activity, AmbientAnimationView.Theme.CINEMATIC_NATURE)
            ThemeChoice.AMBIENT_COMPANION -> applyAmbient(activity, AmbientAnimationView.Theme.EMOTIONAL_COMPANION)
            ThemeChoice.COUPLE -> applyCouple(activity)
            else -> {}
        }
    }

    /**
     * Finds the activity's main layout and strips its background recursively.
     */
    private fun makeActivityTransparent(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val themeActive = activeTheme != ThemeChoice.NONE
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            stripBackgroundsRecursive(child, themeActive)
        }
    }

    private fun stripBackgroundsRecursive(view: View, themeActive: Boolean) {
        // Skip our theme views
        if (view is NightSkyView || view is DoraemonView || view is NatureView || view is AmbientAnimationView) {
            return
        }

        if (!themeActive) return

        if (view is ViewGroup) {
            val className = view.javaClass.name

            // Clear backgrounds of layout containers to let theme show through
            if (view is LinearLayout || view is FrameLayout || view is RelativeLayout || view is ScrollView || className.contains("ConstraintLayout") || className.contains("GridLayout") || className.contains("CoordinatorLayout") || className.contains("NestedScrollView") || className.contains("RecyclerView") || view is AdapterView<*>) {
                view.background = null
            }

            // Glassmorphism effect for CardViews
            if (view is CardView) {
                view.setCardBackgroundColor(Color.argb(110, 30, 30, 45))
                view.cardElevation = 0f
            } else if (className.contains("MaterialCardView")) {
                try {
                    val method = view.javaClass.getMethod("setCardBackgroundColor", Int::class.javaPrimitiveType)
                    method.invoke(view, Color.argb(110, 30, 30, 45))
                } catch (e: Exception) {}
            }

            // Ensure child views aren't clipped so shadows and transparency look better
            view.clipChildren = false
            view.clipToPadding = false

            for (i in 0 until view.childCount) {
                stripBackgroundsRecursive(view.getChildAt(i), themeActive)
            }
        } else {
            // For standard buttons and interactive elements, make them slightly transparent
            if (view is Button || view is ImageButton || view is ProgressBar || view is SeekBar) {
                view.alpha = 0.85f
            }
            // Clear backgrounds from other views that might have them (e.g. some text labels)
            if (view is TextView && view !is Button) {
                view.background = null
            }
        }
    }

    // ── Detach ─────────────────────────────────────────────────────────────

    fun detach(activity: Activity) {
        val key = activity.hashCode()
        nightSkyThemes.remove(key)?.detach()
        doraemonThemes.remove(key)?.detach()
        natureThemes.remove(key)?.detach()
        ambientThemes.remove(key)?.let { (it.parent as? ViewGroup)?.removeView(it) }
        if (coupleThemes.remove(key) != null) {
            CoupleThemeApplicator.deactivate()
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private fun getOrWrapRoot(activity: Activity): FrameLayout? {
        return activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout
    }
}
