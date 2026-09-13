package com.calcvault.ui.call

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFF30D158.toInt()
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val bars = 30
    private val heights = FloatArray(bars) { 0.1f }
    private var animating = false
    private val handler = Handler(Looper.getMainLooper())

    private val animRunnable = object : Runnable {
        override fun run() {
            if (!animating) return
            for (i in heights.indices) {
                heights[i] = 0.1f + Math.random().toFloat() * 0.8f
            }
            invalidate()
            handler.postDelayed(this, 100)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val spacing = w / bars
        val barWidth = spacing * 0.6f

        for (i in 0 until bars) {
            val x = i * spacing + spacing / 2
            val barHeight = heights[i] * h * 0.8f
            val top = h / 2 - barHeight / 2
            val bottom = h / 2 + barHeight / 2
            canvas.drawLine(x, top, x, bottom, paint)
        }
    }

    fun startAnimating() {
        animating = true
        handler.post(animRunnable)
    }

    fun stopAnimating() {
        animating = false
        handler.removeCallbacks(animRunnable)
        heights.fill(0.1f)
        invalidate()
    }
}
