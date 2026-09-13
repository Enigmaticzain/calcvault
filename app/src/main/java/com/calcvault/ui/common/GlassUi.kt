package com.calcvault.ui.common

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import kotlin.math.max

object GlassUi {
    private data class PanelTag(val alpha: Int, val radiusDp: Int)

    fun applyThemeChrome(activity: Activity, root: View? = null) {
        val engine = ThemeEngine(activity)
        val theme = engine.getCurrentTheme()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val target = root ?: content ?: return

        if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
            engine.applyToView(target, theme)
        } else {
            target.setBackgroundColor(Color.TRANSPARENT)
        }

        ThemeApplicator.applyActive(activity)
        styleReadable(content ?: target, theme)
    }

    fun markPanel(view: View, alpha: Int = 145, radiusDp: Int = 24) {
        view.tag = PanelTag(alpha, radiusDp)
        view.background = glassDrawable(view.context, alpha = alpha, radiusDp = radiusDp)
    }

    fun glassDrawable(
        context: Context,
        alpha: Int = 150,
        radiusDp: Int = 20,
        red: Int = 18,
        green: Int = 18,
        blue: Int = 28
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(Color.argb(alpha, red, green, blue))
            setStroke(dp(context, 1), Color.argb(75, 255, 255, 255))
        }
    }

    fun styleButton(button: Button, danger: Boolean = false) {
        if (button is CompoundButton) {
            button.setTextColor(Color.WHITE)
            button.alpha = 0.95f
            return
        }

        val label = button.text?.toString()?.lowercase().orEmpty()
        val dangerButton = danger ||
            label.contains("end") ||
            label.contains("delete") ||
            label.contains("lock")

        button.isAllCaps = false
        button.minHeight = max(button.minHeight, dp(button.context, 46))
        button.setPadding(dp(button.context, 14), dp(button.context, 8), dp(button.context, 14), dp(button.context, 8))
        button.setTextColor(Color.WHITE)
        button.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        if (button.textSize / button.resources.displayMetrics.scaledDensity < 13f) {
            button.textSize = 13f
        }
        button.background = glassDrawable(
            button.context,
            alpha = if (dangerButton) 220 else 176,
            radiusDp = 18,
            red = if (dangerButton) 210 else 32,
            green = if (dangerButton) 44 else 36,
            blue = if (dangerButton) 54 else 48
        )
    }

    private fun styleReadable(view: View, theme: ThemeEngine.CalcVaultTheme) {
        (view.tag as? PanelTag)?.let { tag ->
            view.background = glassDrawable(view.context, alpha = tag.alpha, radiusDp = tag.radiusDp)
        }

        when (view) {
            is CompoundButton -> {
                view.setTextColor(theme.primaryText)
                view.alpha = 0.95f
            }
            is Button -> styleButton(view)
            is ImageButton -> styleImageButton(view)
            is EditText -> styleEditText(view, theme)
            is Spinner -> {
                view.background = glassDrawable(view.context, alpha = 135, radiusDp = 16)
                view.alpha = 0.96f
                view.minimumHeight = dp(view.context, 48)
            }
            is SeekBar -> view.alpha = 0.92f
            is TextView -> {
                if (view.currentTextColor == Color.BLACK || view.currentTextColor == Color.TRANSPARENT) {
                    view.setTextColor(theme.primaryText)
                }
                view.setShadowLayer(2.5f, 0f, 1f, Color.argb(135, 0, 0, 0))
            }
        }

        if (view is ViewGroup) {
            view.clipChildren = false
            view.clipToPadding = false
            for (index in 0 until view.childCount) {
                styleReadable(view.getChildAt(index), theme)
            }
        }
    }

    private fun styleEditText(editText: EditText, theme: ThemeEngine.CalcVaultTheme) {
        editText.setTextColor(theme.primaryText)
        editText.setHintTextColor(theme.secondaryText)
        editText.setPadding(dp(editText.context, 14), dp(editText.context, 10), dp(editText.context, 14), dp(editText.context, 10))
        editText.background = glassDrawable(editText.context, alpha = 120, radiusDp = 16)
    }

    private fun styleImageButton(button: ImageButton) {
        button.alpha = 0.92f
        button.setPadding(dp(button.context, 10), dp(button.context, 10), dp(button.context, 10), dp(button.context, 10))
        button.background = glassDrawable(button.context, alpha = 160, radiusDp = 18)
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
