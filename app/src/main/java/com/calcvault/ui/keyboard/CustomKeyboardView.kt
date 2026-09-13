package com.calcvault.ui.keyboard

import android.content.Context
import android.util.AttributeSet
import android.widget.GridLayout
import androidx.appcompat.widget.AppCompatButton
import com.calcvault.emotional.ThemeEngine

/**
 * CustomKeyboardView - Themed numeric/text keyboard that matches app theme
 *
 * Applies current app theme to keyboard buttons, colors, and animations.
 * Used in PassphraseActivity and CalculatorActivity for vault unlock flow.
 */
class CustomKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : GridLayout(context, attrs, defStyle) {

    private val themeEngine = ThemeEngine(context)
    private var keyboardTheme: KeyboardTheme
    private var onKeyPress: ((String) -> Unit)? = null

    init {
        columnCount = 3
        rowCount = 4
        
        // Generate keyboard theme from current app theme
        val appTheme = themeEngine.getCurrentTheme()
        keyboardTheme = KeyboardThemeGenerator.fromAppTheme(appTheme, KeyboardMode.EXPRESSIVE)
        
        // Apply keyboard background theme
        setBackgroundColor(keyboardTheme.backgroundColor)
        
        setupKeyboard()
    }

    private fun setupKeyboard() {
        // Clear any existing views
        removeAllViews()
        
        // Row 1: 1, 2, 3
        addKeyButton("1")
        addKeyButton("2")
        addKeyButton("3")
        
        // Row 2: 4, 5, 6
        addKeyButton("4")
        addKeyButton("5")
        addKeyButton("6")
        
        // Row 3: 7, 8, 9
        addKeyButton("7")
        addKeyButton("8")
        addKeyButton("9")
        
        // Row 4: 0, ⌫ (backspace), ⏎ (enter)
        addKeyButton("0")
        addKeyButton("⌫", isSpecial = true)  // Backspace
        addKeyButton("⏎", isSpecial = true)  // Enter
    }

    private fun addKeyButton(text: String, isSpecial: Boolean = false) {
        val button = AppCompatButton(context).apply {
            setText(text)
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = 0
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            lp.leftMargin = 8
            lp.rightMargin = 8
            lp.topMargin = 8
            lp.bottomMargin = 8
            layoutParams = lp
            
            // Apply theme colors
            if (isSpecial) {
                setBackgroundColor(keyboardTheme.accentColor)
                setTextColor(keyboardTheme.accentText)
            } else {
                setBackgroundColor(keyboardTheme.keyColor)
                setTextColor(keyboardTheme.keyTextColor)
            }
            
            // Apply styling
            isAllCaps = false
            textSize = 18f
            elevation = keyboardTheme.keyElevation
            
            setOnClickListener {
                onKeyPress?.invoke(text)
                handleKeyPress(this)
            }
        }
        
        addView(button)
    }

    private fun handleKeyPress(button: AppCompatButton) {
        // Animate key press
        button.scaleX = keyboardTheme.keyPressScale
        button.scaleY = keyboardTheme.keyPressScale
        
        button.postDelayed({
            button.scaleX = 1f
            button.scaleY = 1f
        }, keyboardTheme.keyPressDuration)
    }

    fun setOnKeyPressListener(listener: (String) -> Unit) {
        onKeyPress = listener
    }

    fun updateTheme() {
        // Re-apply theme when app theme changes
        val appTheme = themeEngine.getCurrentTheme()
        val newTheme = KeyboardThemeGenerator.fromAppTheme(appTheme, KeyboardMode.EXPRESSIVE)
        keyboardTheme = newTheme
        
        // Update background
        setBackgroundColor(newTheme.backgroundColor)
        
        // Update all child buttons with new theme
        for (i in 0 until childCount) {
            val button = getChildAt(i) as? AppCompatButton ?: continue
            val isSpecial = button.text in listOf("⌫", "⏎")
            
            if (isSpecial) {
                button.setBackgroundColor(newTheme.accentColor)
                button.setTextColor(newTheme.accentText)
            } else {
                button.setBackgroundColor(newTheme.keyColor)
                button.setTextColor(newTheme.keyTextColor)
            }
        }
    }
}
