package com.calcvault.emotional

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import com.calcvault.storage.provider.StorageManager
import com.calcvault.utils.SessionManager
import org.json.JSONObject
import java.util.Calendar

/**
 * ThemeEngine (Improved)
 *
 * Dynamic theme system for CalcVault.
 */
class ThemeEngine(private val context: Context) {

    enum class ThemeType {
        DARK, LIGHT, ROMANTIC, CALM, NIGHT_VIBE, MEMORY, CUSTOM, NEON, PASTEL,
        MIDNIGHT, NORD, FOREST, ROSE, SANCTUARY_STARGAZING, SANCTUARY_RAINYCAFE,
        SANCTUARY_SUNRISE, SANCTUARY_AUTUMNPARK, SANCTUARY_SUNSETBEACH,
        SANCTUARY_PEACEFULSNOW, SANCTUARY_COUCHFIREPLACE
    }

    data class CalcVaultTheme(
        val type: ThemeType,
        val backgroundStart: Int,
        val backgroundEnd: Int,
        val surfaceColor: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val accentColor: Int,
        val sentBubble: Int,
        val receivedBubble: Int,
        val statusBarColor: Int,
        val navBarColor: Int,
        val glowColor: Int,
        val backgroundImage: String? = null
    )

    companion object {
        private const val THEME_KEY = "prefs/current_theme"
        private const val THEME_TYPE_KEY = "prefs/current_theme_type"
        private const val THEME_ADAPTIVE_KEY = "prefs/theme_adaptive_mode"

        val THEME_DARK = CalcVaultTheme(
            type = ThemeType.DARK,
            backgroundStart = 0xFF0F0F14.toInt(),
            backgroundEnd = 0xFF050508.toInt(),
            surfaceColor = 0xCC1A1A24.toInt(),
            primaryText = 0xFFE0E0E0.toInt(),
            secondaryText = 0xFF9090A0.toInt(),
            accentColor = 0xFF6200EE.toInt(),
            sentBubble = 0xAA3700B3.toInt(),
            receivedBubble = 0xAA2C2C38.toInt(),
            statusBarColor = 0xFF0F0F14.toInt(),
            navBarColor = 0xFF050508.toInt(),
            glowColor = 0xFFBB86FC.toInt()
        )

        val THEME_LIGHT = CalcVaultTheme(
            type = ThemeType.LIGHT,
            backgroundStart = 0xFFF5F7FA.toInt(),
            backgroundEnd = 0xFFE4E7EB.toInt(),
            surfaceColor = 0xCCFFFFFF.toInt(),
            primaryText = 0xFF1A1C1E.toInt(),
            secondaryText = 0xFF65676B.toInt(),
            accentColor = 0xFF0061FF.toInt(),
            sentBubble = 0xAA0061FF.toInt(),
            receivedBubble = 0xAAE4E6EB.toInt(),
            statusBarColor = 0xFFF5F7FA.toInt(),
            navBarColor = 0xFFE4E7EB.toInt(),
            glowColor = 0xFF0061FF.toInt()
        )

        val THEME_ROMANTIC = CalcVaultTheme(
            type = ThemeType.ROMANTIC,
            backgroundStart = 0xFF2D0815.toInt(), backgroundEnd = 0xFF14030A.toInt(),
            surfaceColor = 0xCC450D21.toInt(),
            primaryText = 0xFFFFF0F5.toInt(),
            secondaryText = 0xFFFFB6C1.toInt(),
            accentColor = 0xFFFF1493.toInt(),
            sentBubble = 0xAAC71585.toInt(),
            receivedBubble = 0xAA3D0A1D.toInt(),
            statusBarColor = 0xFF2D0815.toInt(),
            navBarColor = 0xFF14030A.toInt(),
            glowColor = 0xFFFF69B4.toInt()
        )

        val THEME_NEON = CalcVaultTheme(
            type = ThemeType.NEON,
            backgroundStart = 0xFF000000.toInt(),
            backgroundEnd = 0xFF000000.toInt(),
            surfaceColor = 0xCC121212.toInt(),
            primaryText = 0xFF00FF41.toInt(),
            secondaryText = 0xFF008F11.toInt(),
            accentColor = 0xFF00FF41.toInt(),
            sentBubble = 0xAA003B00.toInt(),
            receivedBubble = 0xAA1A1A1A.toInt(),
            statusBarColor = 0xFF000000.toInt(),
            navBarColor = 0xFF000000.toInt(),
            glowColor = 0xFF00FF41.toInt()
        )

        val THEME_PASTEL = CalcVaultTheme(
            type = ThemeType.PASTEL,
            backgroundStart = 0xFFFFE4E1.toInt(),
            backgroundEnd = 0xFFE6E6FA.toInt(),
            surfaceColor = 0xCCFFFFFF.toInt(),
            primaryText = 0xFF4B0082.toInt(),
            secondaryText = 0xFF8A2BE2.toInt(),
            accentColor = 0xFFFF69B4.toInt(),
            sentBubble = 0xAAFFB6C1.toInt(),
            receivedBubble = 0xAAF0F8FF.toInt(),
            statusBarColor = 0xFFFFE4E1.toInt(),
            navBarColor = 0xFFE6E6FA.toInt(),
            glowColor = 0xFFFFC0CB.toInt()
        )

        val THEME_CALM = CalcVaultTheme(
            type = ThemeType.CALM,
            backgroundStart = 0xFF0F172A.toInt(), backgroundEnd = 0xFF020617.toInt(),
            surfaceColor = 0xCC1E293B.toInt(),
            primaryText = 0xFFF8FAFC.toInt(),
            secondaryText = 0xFF94A3B8.toInt(),
            accentColor = 0xFF38BDF8.toInt(),
            sentBubble = 0xAA0369A1.toInt(),
            receivedBubble = 0xAA1E293B.toInt(),
            statusBarColor = 0xFF0F172A.toInt(),
            navBarColor = 0xFF020617.toInt(),
            glowColor = 0xFF7DD3FC.toInt()
        )

        val THEME_NIGHT_VIBE = CalcVaultTheme(
            type = ThemeType.NIGHT_VIBE,
            backgroundStart = 0xFF050505.toInt(),
            backgroundEnd = 0xFF000000.toInt(),
            surfaceColor = 0xCC111111.toInt(),
            primaryText = 0xFFFFFFFF.toInt(),
            secondaryText = 0xFF666666.toInt(),
            accentColor = 0xFFE11D48.toInt(),
            sentBubble = 0xAA9F1239.toInt(),
            receivedBubble = 0xAA1F1F1F.toInt(),
            statusBarColor = 0xFF050505.toInt(),
            navBarColor = 0xFF000000.toInt(),
            glowColor = 0xFFFB7185.toInt()
        )

        val THEME_MIDNIGHT = CalcVaultTheme(
            type = ThemeType.MIDNIGHT,
            backgroundStart = 0xFF0B0E14.toInt(),
            backgroundEnd = 0xFF000000.toInt(),
            surfaceColor = 0xCC12151F.toInt(),
            primaryText = 0xFFFFFFFF.toInt(),
            secondaryText = 0xFF8B94A5.toInt(),
            accentColor = 0xFF3B82F6.toInt(),
            sentBubble = 0xAA2563EB.toInt(),
            receivedBubble = 0xAA1E293B.toInt(),
            statusBarColor = 0xFF0B0E14.toInt(),
            navBarColor = 0xFF000000.toInt(),
            glowColor = 0xFF60A5FA.toInt()
        )

        val THEME_NORD = CalcVaultTheme(
            type = ThemeType.NORD,
            backgroundStart = 0xFF2E3440.toInt(),
            backgroundEnd = 0xFF242933.toInt(),
            surfaceColor = 0xCC3B4252.toInt(),
            primaryText = 0xFFECEFF4.toInt(),
            secondaryText = 0xFFD8DEE9.toInt(),
            accentColor = 0xFF88C0D0.toInt(),
            sentBubble = 0xAA5E81AC.toInt(),
            receivedBubble = 0xAA434C5E.toInt(),
            statusBarColor = 0xFF2E3440.toInt(),
            navBarColor = 0xFF242933.toInt(),
            glowColor = 0xFF81A1C1.toInt()
        )

        val THEME_FOREST = CalcVaultTheme(
            type = ThemeType.FOREST,
            backgroundStart = 0xFF0F1711.toInt(),
            backgroundEnd = 0xFF080C09.toInt(),
            surfaceColor = 0xCC16221A.toInt(),
            primaryText = 0xFFE8F5E9.toInt(),
            secondaryText = 0xFFA5D6A7.toInt(),
            accentColor = 0xFF4CAF50.toInt(),
            sentBubble = 0xAA2E7D32.toInt(),
            receivedBubble = 0xAA1B2B20.toInt(),
            statusBarColor = 0xFF0F1711.toInt(),
            navBarColor = 0xFF080C09.toInt(),
            glowColor = 0xFF81C784.toInt()
        )

        val THEME_ROSE = CalcVaultTheme(
            type = ThemeType.ROSE,
            backgroundStart = 0xFF1A0F14.toInt(),
            backgroundEnd = 0xFF120A0E.toInt(),
            surfaceColor = 0xCC26161E.toInt(),
            primaryText = 0xFFFFF0F5.toInt(),
            secondaryText = 0xFFF48FB1.toInt(),
            accentColor = 0xFFE91E63.toInt(),
            sentBubble = 0xAAC2185B.toInt(),
            receivedBubble = 0xAA3E202C.toInt(),
            statusBarColor = 0xFF1A0F14.toInt(),
            navBarColor = 0xFF120A0E.toInt(),
            glowColor = 0xFFF06292.toInt()
        )

        // ========================================
        // SANCTUARY THEMES (Romantic Backdrops)
        // ========================================
        
        val THEME_SANCTUARY_STARGAZING = CalcVaultTheme(
            type = ThemeType.SANCTUARY_STARGAZING,
            backgroundStart = 0xFF0F0A1A.toInt(),
            backgroundEnd = 0xFF08050F.toInt(),
            surfaceColor = 0xCC1A1A2A.toInt(),
            primaryText = 0xFFF5E8FF.toInt(),
            secondaryText = 0xFFB8A0C8.toInt(),
            accentColor = 0xFFFF758C.toInt(),
            sentBubble = 0xAA9D4557.toInt(),
            receivedBubble = 0xAA2A1A28.toInt(),
            statusBarColor = 0xFF0F0A1A.toInt(),
            navBarColor = 0xFF08050F.toInt(),
            glowColor = 0xFFFF9BAA.toInt()
        )

        val THEME_SANCTUARY_RAINYCAFE = CalcVaultTheme(
            type = ThemeType.SANCTUARY_RAINYCAFE,
            backgroundStart = 0xFF1D1B17.toInt(),
            backgroundEnd = 0xFF14120F.toInt(),
            surfaceColor = 0xCC2A261F.toInt(),
            primaryText = 0xFFF5EFE8.toInt(),
            secondaryText = 0xFFC9B8A8.toInt(),
            accentColor = 0xFFE29578.toInt(),
            sentBubble = 0xAAB37550.toInt(),
            receivedBubble = 0xAA2D2620.toInt(),
            statusBarColor = 0xFF1D1B17.toInt(),
            navBarColor = 0xFF14120F.toInt(),
            glowColor = 0xFFDDAE8F.toInt()
        )

        val THEME_SANCTUARY_SUNRISE = CalcVaultTheme(
            type = ThemeType.SANCTUARY_SUNRISE,
            backgroundStart = 0xFF1A1514.toInt(),
            backgroundEnd = 0xFF10090F.toInt(),
            surfaceColor = 0xCC271F1D.toInt(),
            primaryText = 0xFFFFEEDD.toInt(),
            secondaryText = 0xFFD4B8A0.toInt(),
            accentColor = 0xFFFF7976.toInt(),
            sentBubble = 0xAAC96362.toInt(),
            receivedBubble = 0xAA2A1F1C.toInt(),
            statusBarColor = 0xFF1A1514.toInt(),
            navBarColor = 0xFF10090F.toInt(),
            glowColor = 0xFFFFAEAD.toInt()
        )

        val THEME_SANCTUARY_AUTUMNPARK = CalcVaultTheme(
            type = ThemeType.SANCTUARY_AUTUMNPARK,
            backgroundStart = 0xFF1D1517.toInt(),
            backgroundEnd = 0xFF130E10.toInt(),
            surfaceColor = 0xCC291F21.toInt(),
            primaryText = 0xFFF5E8DC.toInt(),
            secondaryText = 0xFFC9A8A0.toInt(),
            accentColor = 0xFFE07A5F.toInt(),
            sentBubble = 0xAAB56245.toInt(),
            receivedBubble = 0xAA291F1C.toInt(),
            statusBarColor = 0xFF1D1517.toInt(),
            navBarColor = 0xFF130E10.toInt(),
            glowColor = 0xFFDBA285.toInt()
        )

        val THEME_SANCTUARY_SUNSETBEACH = CalcVaultTheme(
            type = ThemeType.SANCTUARY_SUNSETBEACH,
            backgroundStart = 0xFF1D0E12.toInt(),
            backgroundEnd = 0xFF120609.toInt(),
            surfaceColor = 0xCC28131A.toInt(),
            primaryText = 0xFFFFEAE0.toInt(),
            secondaryText = 0xFFD9B8A8.toInt(),
            accentColor = 0xFFFFAC81.toInt(),
            sentBubble = 0xAACC8A5E.toInt(),
            receivedBubble = 0xAA2C1419.toInt(),
            statusBarColor = 0xFF1D0E12.toInt(),
            navBarColor = 0xFF120609.toInt(),
            glowColor = 0xFFFFCA9F.toInt()
        )

        val THEME_SANCTUARY_PEACEFULSNOW = CalcVaultTheme(
            type = ThemeType.SANCTUARY_PEACEFULSNOW,
            backgroundStart = 0xFF101623.toInt(),
            backgroundEnd = 0xFF090E19.toInt(),
            surfaceColor = 0xCC181E2F.toInt(),
            primaryText = 0xFFE8F0FF.toInt(),
            secondaryText = 0xFFA8C0D8.toInt(),
            accentColor = 0xFFA2C2E8.toInt(),
            sentBubble = 0xAA5F7FA8.toInt(),
            receivedBubble = 0xAA172437.toInt(),
            statusBarColor = 0xFF101623.toInt(),
            navBarColor = 0xFF090E19.toInt(),
            glowColor = 0xFFC3DDFF.toInt()
        )

        val THEME_SANCTUARY_COUCHFIREPLACE = CalcVaultTheme(
            type = ThemeType.SANCTUARY_COUCHFIREPLACE,
            backgroundStart = 0xFF201517.toInt(),
            backgroundEnd = 0xFF140C0E.toInt(),
            surfaceColor = 0xCC2D1E22.toInt(),
            primaryText = 0xFFFFF0E0.toInt(),
            secondaryText = 0xFFD4B8A0.toInt(),
            accentColor = 0xFFF39C12.toInt(),
            sentBubble = 0xAABE730D.toInt(),
            receivedBubble = 0xAA341C1F.toInt(),
            statusBarColor = 0xFF201517.toInt(),
            navBarColor = 0xFF140C0E.toInt(),
            glowColor = 0xFFFBB945.toInt()
        )

        private var currentTheme: CalcVaultTheme? = null
        var isAdaptiveMode = true
            private set
        private var lockedTheme: CalcVaultTheme? = null
        private var themeInitialized = false

        fun getGlobalTheme(): CalcVaultTheme {
            lockedTheme?.let { return it }
            
            // Lazy load theme on first access on first access
            if (currentTheme == null && !themeInitialized) {
                themeInitialized = true
                try {
                    if (StorageManager.isReady()) {
                        val data = StorageManager.read(THEME_KEY)
                        if (data != null) {
                            val theme = deserializeTheme(String(data, Charsets.UTF_8))
                            currentTheme = theme
                            lockedTheme = theme
                            isAdaptiveMode = false
                            return theme
                        }
                    }
                } catch (e: Exception) {}
                currentTheme = THEME_DARK
            }
            
            return if (isAdaptiveMode) {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (hour in 7..19) THEME_LIGHT else THEME_DARK
            } else {
                currentTheme ?: THEME_DARK
            }
        }
        
        fun getThemeByType(type: ThemeType): CalcVaultTheme = when (type) {
            ThemeType.DARK -> THEME_DARK
            ThemeType.LIGHT -> THEME_LIGHT
            ThemeType.ROMANTIC -> THEME_ROMANTIC
            ThemeType.CALM -> THEME_CALM
            ThemeType.NIGHT_VIBE -> THEME_NIGHT_VIBE
            ThemeType.NEON -> THEME_NEON
            ThemeType.PASTEL -> THEME_PASTEL
            ThemeType.MIDNIGHT -> THEME_MIDNIGHT
            ThemeType.NORD -> THEME_NORD
            ThemeType.FOREST -> THEME_FOREST
            ThemeType.ROSE -> THEME_ROSE
            ThemeType.SANCTUARY_STARGAZING -> THEME_SANCTUARY_STARGAZING
            ThemeType.SANCTUARY_RAINYCAFE -> THEME_SANCTUARY_RAINYCAFE
            ThemeType.SANCTUARY_SUNRISE -> THEME_SANCTUARY_SUNRISE
            ThemeType.SANCTUARY_AUTUMNPARK -> THEME_SANCTUARY_AUTUMNPARK
            ThemeType.SANCTUARY_SUNSETBEACH -> THEME_SANCTUARY_SUNSETBEACH
            ThemeType.SANCTUARY_PEACEFULSNOW -> THEME_SANCTUARY_PEACEFULSNOW
            ThemeType.SANCTUARY_COUCHFIREPLACE -> THEME_SANCTUARY_COUCHFIREPLACE
            else -> THEME_DARK
        }

        fun getAllSanctuaryThemes(): List<CalcVaultTheme> = listOf(
            THEME_SANCTUARY_STARGAZING,
            THEME_SANCTUARY_RAINYCAFE,
            THEME_SANCTUARY_SUNRISE,
            THEME_SANCTUARY_AUTUMNPARK,
            THEME_SANCTUARY_SUNSETBEACH,
            THEME_SANCTUARY_PEACEFULSNOW,
            THEME_SANCTUARY_COUCHFIREPLACE
        )

        fun getSanctuaryThemeNames(): Map<ThemeType, String> = mapOf(
            ThemeType.SANCTUARY_STARGAZING to "Twilight Stargazing",
            ThemeType.SANCTUARY_RAINYCAFE to "Cozy Rainy Cafe",
            ThemeType.SANCTUARY_SUNRISE to "Sunrise Embrace",
            ThemeType.SANCTUARY_AUTUMNPARK to "Autumn Park Dusk",
            ThemeType.SANCTUARY_SUNSETBEACH to "Sunset Beach",
            ThemeType.SANCTUARY_PEACEFULSNOW to "Peaceful Snow",
            ThemeType.SANCTUARY_COUCHFIREPLACE to "Couch Fireplace"
        )

            fun deserializeTheme(json: String): CalcVaultTheme {
                val j = JSONObject(json)
                return CalcVaultTheme(
                    type = ThemeType.valueOf(j.getString("type")),
                    backgroundStart = j.getInt("bgStart"),
                    backgroundEnd = j.getInt("bgEnd"),
                    surfaceColor = j.getInt("surface"),
                    primaryText = j.getInt("primary"),
                    secondaryText = j.getInt("secondary"),
                    accentColor = j.getInt("accent"),
                    sentBubble = j.getInt("sent"),
                    receivedBubble = j.getInt("recv"),
                    statusBarColor = j.getInt("status"),
                    navBarColor = j.getInt("nav"),
                    glowColor = j.getInt("glow"),
                    backgroundImage = j.optString("bgImage").ifEmpty { null }
                )
            }
    }

    init {
        // Theme is lazily loaded on first access via getGlobalTheme()
    }

    fun getCurrentTheme(): CalcVaultTheme = getGlobalTheme()

    fun setTheme(type: ThemeType, customBgImagePath: String? = null) {
        val newTheme = when (type) {
            ThemeType.DARK -> THEME_DARK
            ThemeType.LIGHT -> THEME_LIGHT
            ThemeType.ROMANTIC -> THEME_ROMANTIC
            ThemeType.CALM -> THEME_CALM
            ThemeType.NIGHT_VIBE -> THEME_NIGHT_VIBE
            ThemeType.NEON -> THEME_NEON
            ThemeType.PASTEL -> THEME_PASTEL
            ThemeType.MIDNIGHT -> THEME_MIDNIGHT
            ThemeType.NORD -> THEME_NORD
            ThemeType.FOREST -> THEME_FOREST
            ThemeType.ROSE -> THEME_ROSE
            ThemeType.SANCTUARY_STARGAZING -> THEME_SANCTUARY_STARGAZING
            ThemeType.SANCTUARY_RAINYCAFE -> THEME_SANCTUARY_RAINYCAFE
            ThemeType.SANCTUARY_SUNRISE -> THEME_SANCTUARY_SUNRISE
            ThemeType.SANCTUARY_AUTUMNPARK -> THEME_SANCTUARY_AUTUMNPARK
            ThemeType.SANCTUARY_SUNSETBEACH -> THEME_SANCTUARY_SUNSETBEACH
            ThemeType.SANCTUARY_PEACEFULSNOW -> THEME_SANCTUARY_PEACEFULSNOW
            ThemeType.SANCTUARY_COUCHFIREPLACE -> THEME_SANCTUARY_COUCHFIREPLACE
            ThemeType.MEMORY -> THEME_DARK.copy(
                type = ThemeType.MEMORY,
                backgroundImage = customBgImagePath
            )
            ThemeType.CUSTOM -> (currentTheme ?: THEME_DARK).copy(type = ThemeType.CUSTOM)
        }

        lockedTheme = newTheme
        currentTheme = newTheme
        isAdaptiveMode = false
        saveTheme(newTheme)
    }

    fun applyToView(rootView: View, theme: CalcVaultTheme = getCurrentTheme()) {
        // STEALTH GUARD: Never apply vault colors/backgrounds to the Calculator screen
        if (context is android.app.Activity && context.javaClass.simpleName == "CalculatorActivity") {
            return
        }

        if (SessionManager.isAmbientEnabled) {
            rootView.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        if (theme.backgroundImage != null) {
            try {
                val uri = Uri.parse(theme.backgroundImage)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                rootView.background = BitmapDrawable(context.resources, bitmap)
                return
            } catch (e: Exception) { }
        }
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(theme.backgroundStart, theme.backgroundEnd)
        )
        rootView.background = gradient
    }

    fun setAdaptive(enabled: Boolean) {
        isAdaptiveMode = enabled
        if (enabled) lockedTheme = null
        saveTheme(getGlobalTheme())
    }

    fun saveTheme(theme: CalcVaultTheme) {
        try {
            if (StorageManager.isReady()) {
                val json = serializeTheme(theme)
                StorageManager.write(THEME_KEY, json.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {}
    }

    fun loadSavedTheme() {
        if (!StorageManager.isReady()) return
        val data = StorageManager.read(THEME_KEY)
        if (data != null) {
            try {
                val theme = deserializeTheme(String(data, Charsets.UTF_8))
                currentTheme = theme
                lockedTheme = theme
                isAdaptiveMode = false
            } catch (e: Exception) { }
        }
    }

    fun serializeTheme(theme: CalcVaultTheme): String = JSONObject().apply {
        put("type", theme.type.name)
        put("bgStart", theme.backgroundStart)
        put("bgEnd", theme.backgroundEnd)
        put("surface", theme.surfaceColor)
        put("primary", theme.primaryText)
        put("secondary", theme.secondaryText)
        put("accent", theme.accentColor)
        put("sent", theme.sentBubble)
        put("recv", theme.receivedBubble)
        put("status", theme.statusBarColor)
        put("nav", theme.navBarColor)
        put("glow", theme.glowColor)
        theme.backgroundImage?.let { put("bgImage", it) }
    }.toString()

}
