package com.calcvault.ui.keyboard

import android.graphics.Color
import androidx.annotation.ColorInt
import com.calcvault.emotional.ThemeEngine

/**
 * KeyboardTheme - Bridges app themes to keyboard styling
 *
 * The keyboard theme is derived from the current app theme (ThemeEngine)
 * and provides all necessary styling for the virtual keyboard.
 */
data class KeyboardTheme(
    val name: String,
    
    // Primary colors
    @ColorInt val backgroundColor: Int,           // Main keyboard background
    @ColorInt val keyColor: Int,                  // Individual key background
    @ColorInt val keyPressed: Int,                // Key when pressed
    @ColorInt val keyTextColor: Int,              // Key text (auto-contrast)
    
    // Accent colors
    @ColorInt val accentColor: Int,               // Special keys (backspace, enter, etc)
    @ColorInt val accentPressed: Int,             // Special key pressed
    @ColorInt val accentText: Int,                // Special key text
    
    // Additional styling
    @ColorInt val shadowColor: Int,               // Key shadows/elevation
    @ColorInt val dividerColor: Int,              // Between keys
    
    // Effects
    val keyCornerRadius: Float = 8f,              // Rounded key corners
    val keyElevation: Float = 4f,                 // Shadow depth
    val keyPressScale: Float = 0.95f,             // Scale when pressed
    val keyPressDuration: Long = 150L,            // Animation duration
    val enableGlass: Boolean = false,             // Glass morphism
    val glassBlurRadius: Float = 12f,             // Blur radius if enabled
    val backgroundOpacity: Float = 1.0f,          // Keyboard background alpha (0-1)
    
    // Mode
    val mode: KeyboardMode = KeyboardMode.EXPRESSIVE
) {
    
    /**
     * Check if dark theme (adjust text colors accordingly)
     */
    fun isDark(): Boolean {
        val luminance = calculateLuminance(backgroundColor)
        return luminance < 0.5f
    }
    
    /**
     * Get appropriate text color based on background darkness
     */
    fun getAutoTextColor(): Int {
        return if (isDark()) Color.WHITE else Color.BLACK
    }
    
    companion object {
        /**
         * Calculate relative luminance of a color (0-1)
         * Used for auto text color contrast
         */
        fun calculateLuminance(@ColorInt color: Int): Float {
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            
            val rLinear = if (r <= 0.03928f) r / 12.92f else kotlin.math.pow((r + 0.055f) / 1.055f, 2f)
            val gLinear = if (g <= 0.03928f) g / 12.92f else kotlin.math.pow((g + 0.055f) / 1.055f, 2f)
            val bLinear = if (b <= 0.03928f) b / 12.92f else kotlin.math.pow((b + 0.055f) / 1.055f, 2f)
            
            return 0.2126f * rLinear + 0.7152f * gLinear + 0.0722f * bLinear
        }
    }
}

/**
 * Keyboard display mode
 */
enum class KeyboardMode {
    /**
     * MINIMAL: Clean, distraction-free, minimal animations
     */
    MINIMAL,
    
    /**
     * EXPRESSIVE: Full animations, effects, and visual feedback
     */
    EXPRESSIVE,
    
    /**
     * STEALTH: Looks like plain calculator-style keyboard
     * Minimizes Calcvault visual identity
     */
    STEALTH
}

/**
 * KeyboardThemeGenerator - Creates keyboard themes from app themes
 *
 * Automatically converts ThemeEngine.CalcVaultTheme to KeyboardTheme
 * with optional customization and mode adjustments.
 */
object KeyboardThemeGenerator {
    
    /**
     * Generate keyboard theme from app theme
     */
    fun fromAppTheme(
        appTheme: ThemeEngine.CalcVaultTheme,
        mode: KeyboardMode = KeyboardMode.EXPRESSIVE
    ): KeyboardTheme {
        
        // Get background (slightly lighter/darker for keyboard surface)
        val keyboardBg = adjustBrightness(appTheme.surfaceColor, 0.1f)
        val keyBg = adjustBrightness(appTheme.surfaceColor, 0f)  // Keys slightly different
        val keyPressed = adjustBrightness(appTheme.accentColor, -0.3f)
        
        // Text color (auto contrast)
        val textColor = if (KeyboardTheme.calculateLuminance(keyBg) < 0.5f) {
            0xFFFFFFFF.toInt()  // Light text on dark
        } else {
            0xFF1A1C1E.toInt()  // Dark text on light
        }
        
        // Accent styling
        val accentBg = appTheme.accentColor
        val accentPressed = adjustBrightness(appTheme.accentColor, -0.4f)
        val accentText = if (KeyboardTheme.calculateLuminance(accentBg) < 0.5f) {
            0xFFFFFFFF.toInt()
        } else {
            0xFF1A1C1E.toInt()
        }
        
        // Mode-specific settings
        val (glassEnabled, cornerRadius, elevation, pressScale, opacity) = when (mode) {
            KeyboardMode.MINIMAL -> {
                Tuple5(false, 4f, 2f, 0.98f, 0.95f)
            }
            KeyboardMode.EXPRESSIVE -> {
                Tuple5(true, 8f, 4f, 0.95f, 0.98f)
            }
            KeyboardMode.STEALTH -> {
                Tuple5(false, 2f, 1f, 0.97f, 0.90f)
            }
        }
        
        return KeyboardTheme(
            name = "${appTheme.type}_KEYBOARD",
            backgroundColor = keyboardBg,
            keyColor = keyBg,
            keyPressed = keyPressed,
            keyTextColor = textColor,
            accentColor = accentBg,
            accentPressed = accentPressed,
            accentText = accentText,
            shadowColor = adjustBrightness(keyboardBg, -0.3f),
            dividerColor = adjustBrightness(keyboardBg, -0.2f),
            keyCornerRadius = cornerRadius,
            keyElevation = elevation,
            keyPressScale = pressScale,
            enableGlass = glassEnabled,
            backgroundOpacity = opacity,
            mode = mode
        )
    }
    
    /**
     * Adjust color brightness
     * @param color Color to adjust
     * @param factor Brightness factor (-1 to 1, negative = darker)
     */
    private fun adjustBrightness(@androidx.annotation.ColorInt color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        var r = (Color.red(color) * (1 + factor)).toInt().coerceIn(0, 255)
        var g = (Color.green(color) * (1 + factor)).toInt().coerceIn(0, 255)
        var b = (Color.blue(color) * (1 + factor)).toInt().coerceIn(0, 255)
        
        return Color.argb(alpha, r, g, b)
    }
    
    /**
     * Helper data class for tuple return
     */
    data class Tuple5(
        val first: Boolean,
        val second: Float,
        val third: Float,
        val fourth: Float,
        val fifth: Float
    )
}

/**
 * Keyboard layout configuration
 */
data class KeyboardLayout(
    val rows: List<List<KeyDefinition>>
) {
    companion object {
        /**
         * Standard QWERTY layout
         */
        fun qwerty(): KeyboardLayout {
            return KeyboardLayout(
                rows = listOf(
                    // Row 1: Q W E R T Y U I O P
                    listOf(
                        KeyDefinition("Q", "q"), KeyDefinition("W", "w"),
                        KeyDefinition("E", "e"), KeyDefinition("R", "r"),
                        KeyDefinition("T", "t"), KeyDefinition("Y", "y"),
                        KeyDefinition("U", "u"), KeyDefinition("I", "i"),
                        KeyDefinition("O", "o"), KeyDefinition("P", "p")
                    ),
                    // Row 2: A S D F G H J K L
                    listOf(
                        KeyDefinition("A", "a"), KeyDefinition("S", "s"),
                        KeyDefinition("D", "d"), KeyDefinition("F", "f"),
                        KeyDefinition("G", "g"), KeyDefinition("H", "h"),
                        KeyDefinition("J", "j"), KeyDefinition("K", "k"),
                        KeyDefinition("L", "l")
                    ),
                    // Row 3: Z X C V B N M (with space)
                    listOf(
                        KeyDefinition("Z", "z"), KeyDefinition("X", "x"),
                        KeyDefinition("C", "c"), KeyDefinition("V", "v"),
                        KeyDefinition("B", "b"), KeyDefinition("N", "n"),
                        KeyDefinition("M", "m")
                    ),
                    // Row 4: Bottom control keys
                    listOf(
                        KeyDefinition("emoji", "emoji", type = KeyType.EMOJI),
                        KeyDefinition(" ", " ", type = KeyType.SPACE, weight = 3f),
                        KeyDefinition("⌫", "", type = KeyType.BACKSPACE),
                        KeyDefinition("⏎", "\n", type = KeyType.ENTER)
                    )
                )
            )
        }
    }
}

/**
 * Individual key definition
 */
data class KeyDefinition(
    val display: String,              // What shows on key
    val output: String,               // What gets inserted
    val type: KeyType = KeyType.NORMAL,
    val weight: Float = 1f            // Relative width
)

/**
 * Types of keys
 */
enum class KeyType {
    NORMAL,        // Regular letter/number key
    SPACE,         // Space bar
    BACKSPACE,     // Delete key
    ENTER,         // Return/Send key
    EMOJI,         // Emoji toggle
    GIF,           // GIF insert
    VOICE,         // Voice input
    SPECIAL        // Any other special key
}

/**
 * Keyboard event listener
 */
interface KeyboardListener {
    /**
     * Called when a key is pressed
     */
    fun onKeyPress(key: KeyDefinition)
    
    /**
     * Called when backspace is pressed
     */
    fun onBackspace()
    
    /**
     * Called when enter/send is pressed
     */
    fun onEnter()
    
    /**
     * Called when emoji button is pressed
     */
    fun onEmojiToggle()
    
    /**
     * Called when keyboard is shown/hidden
     */
    fun onVisibilityChanged(isVisible: Boolean)
}

/**
 * Keyboard state
 */
data class KeyboardState(
    val isVisible: Boolean = false,
    val isEmojiVisible: Boolean = false,
    val selectedKey: KeyDefinition? = null,
    val mode: KeyboardMode = KeyboardMode.EXPRESSIVE
)
