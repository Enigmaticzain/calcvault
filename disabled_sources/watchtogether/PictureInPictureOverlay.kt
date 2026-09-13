package com.calcvault.watchtogether

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.FrameLayout
import android.util.Log
import com.calcvault.sync.PipConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PictureInPictureOverlay - Draggable video overlay for peer's video feed
 *
 * Features:
 * - Drag to reposition on screen
 * - Pinch to resize (optional)
 * - Semi-transparent background
 * - Border styling
 * - Smooth animations
 *
 * Layout:
 * ┌─────────┐
 * │   PiP   │  ← Can be dragged anywhere
 * │ (Peer)  │  ← Border with rounded corners
 * └─────────┘
 */
class PictureInPictureOverlay(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val TAG = "PipOverlay"
        const val MIN_WIDTH = 200
        const val MIN_HEIGHT = 200
        const val MAX_WIDTH = 800
        const val MAX_HEIGHT = 800
        const val BORDER_WIDTH = 3f
        const val CORNER_RADIUS = 12f
    }

    // Configuration
    private var pipConfig = PipConfig()
    private val isDragging = AtomicBoolean(false)

    // Touch tracking
    private var lastX = 0f
    private var lastY = 0f
    private var initialX = 0f
    private var initialY = 0f

    // Paint for borders and decorations
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = BORDER_WIDTH
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = 0xBB000000  // Semi-transparent black
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Callbacks
    var onPositionChanged: ((x: Int, y: Int) -> Unit)? = null
    var onSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    init {
        setBackgroundColor(0x00000000)  // Transparent
        isClickable = true
    }

    /**
     * Update PiP configuration
     */
    fun updateConfig(newConfig: PipConfig) {
        pipConfig = newConfig
        updateLayout()
    }

    /**
     * Get current configuration
     */
    fun getConfig(): PipConfig = pipConfig

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * TOUCH HANDLING & DRAGGING
     * ═══════════════════════════════════════════════════════════════════════════
     */

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(event)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging.get()) {
                    handleTouchMove(event)
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                handleTouchUp(event)
                true
            }

            else -> super.onTouchEvent(event)
        }
    }

    private fun handleTouchDown(event: MotionEvent) {
        lastX = event.rawX
        lastY = event.rawY
        initialX = lastX
        initialY = lastY
        isDragging.set(true)
        Log.d(TAG, "Touch down at ($lastX, $lastY)")
    }

    private fun handleTouchMove(event: MotionEvent) {
        val deltaX = event.rawX - lastX
        val deltaY = event.rawY - lastY

        // Update position
        val newX = pipConfig.positionX + deltaX.toInt()
        val newY = pipConfig.positionY + deltaY.toInt()

        // Clamp to screen boundaries
        val clampedX = newX.coerceIn(0, screenWidth - pipConfig.width)
        val clampedY = newY.coerceIn(0, screenHeight - pipConfig.height)

        pipConfig = pipConfig.copy(positionX = clampedX, positionY = clampedY)
        updateLayout()

        onPositionChanged?.invoke(clampedX, clampedY)

        lastX = event.rawX
        lastY = event.rawY
    }

    private fun handleTouchUp(event: MotionEvent) {
        isDragging.set(false)
        Log.d(TAG, "Touch up at (${event.rawX}, ${event.rawY})")
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * LAYOUT & RENDERING
     * ═══════════════════════════════════════════════════════════════════════════
     */

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background for PiP area
        canvas.drawRect(
            pipConfig.positionX.toFloat(),
            pipConfig.positionY.toFloat(),
            (pipConfig.positionX + pipConfig.width).toFloat(),
            (pipConfig.positionY + pipConfig.height).toFloat(),
            backgroundPaint
        )

        // Draw border
        canvas.drawRect(
            (pipConfig.positionX + BORDER_WIDTH / 2),
            (pipConfig.positionY + BORDER_WIDTH / 2),
            (pipConfig.positionX + pipConfig.width - BORDER_WIDTH / 2).toFloat(),
            (pipConfig.positionY + pipConfig.height - BORDER_WIDTH / 2).toFloat(),
            borderPaint
        )

        // Draw drag handle indicator (small circle in corner)
        val handleRadius = 6f
        canvas.drawCircle(
            (pipConfig.positionX + pipConfig.width - 12f),
            (pipConfig.positionY + 12f),
            handleRadius,
            Paint().apply {
                color = 0xFF00FF00.toInt()
                style = Paint.Style.FILL
            }
        )
    }

    private fun updateLayout() {
        val params = layoutParams as FrameLayout.LayoutParams
        params.width = pipConfig.width
        params.height = pipConfig.height
        params.leftMargin = pipConfig.positionX
        params.topMargin = pipConfig.positionY
        layoutParams = params
        invalidate()
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * SIZING & CONSTRAINTS
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Resize to new dimensions
     */
    fun resize(newWidth: Int, newHeight: Int) {
        val clampedWidth = newWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)
        val clampedHeight = newHeight.coerceIn(MIN_HEIGHT, MAX_HEIGHT)

        pipConfig = pipConfig.copy(width = clampedWidth, height = clampedHeight)
        updateLayout()
        onSizeChanged?.invoke(clampedWidth, clampedHeight)
    }

    /**
     * Move to absolute position
     */
    fun moveTo(x: Int, y: Int) {
        val clampedX = x.coerceIn(0, screenWidth - pipConfig.width)
        val clampedY = y.coerceIn(0, screenHeight - pipConfig.height)

        pipConfig = pipConfig.copy(positionX = clampedX, positionY = clampedY)
        updateLayout()
        onPositionChanged?.invoke(clampedX, clampedY)
    }

    /**
     * Center on screen
     */
    fun centerOnScreen() {
        val centerX = (screenWidth - pipConfig.width) / 2
        val centerY = (screenHeight - pipConfig.height) / 2
        moveTo(centerX, centerY)
    }

    /**
     * Snap to corner (useful for auto-positioning)
     */
    fun snapToCorner(corner: Corner) {
        val margin = 20
        val (x, y) = when (corner) {
            Corner.TOP_LEFT -> Pair(margin, margin)
            Corner.TOP_RIGHT -> Pair(screenWidth - pipConfig.width - margin, margin)
            Corner.BOTTOM_LEFT -> Pair(margin, screenHeight - pipConfig.height - margin)
            Corner.BOTTOM_RIGHT -> Pair(
                screenWidth - pipConfig.width - margin,
                screenHeight - pipConfig.height - margin
            )
        }
        moveTo(x, y)
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private val screenWidth: Int
        get() = context.resources.displayMetrics.widthPixels

    private val screenHeight: Int
        get() = context.resources.displayMetrics.heightPixels

    enum class Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }
}

/**
 * PiP Manager - Manages overlay state and animations
 */
class PipManager(private val pipOverlay: PictureInPictureOverlay) {

    private var isVisible = false
    var onVisibilityChanged: ((Boolean) -> Unit)? = null

    /**
     * Show or hide the overlay with animation
     */
    fun setVisible(visible: Boolean, animate: Boolean = true) {
        if (isVisible == visible) return

        isVisible = visible
        
        if (animate) {
            pipOverlay.animate()
                .alpha(if (visible) 1f else 0f)
                .setDuration(300)
                .withStartAction {
                    if (visible) pipOverlay.visibility = android.view.View.VISIBLE
                }
                .withEndAction {
                    if (!visible) pipOverlay.visibility = android.view.View.GONE
                }
                .start()
        } else {
            pipOverlay.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            pipOverlay.alpha = if (visible) 1f else 0f
        }

        onVisibilityChanged?.invoke(visible)
    }

    /**
     * Update peer video feed (would be called with actual video frames)
     */
    fun updateVideoFeed(frame: android.graphics.Bitmap?) {
        // TODO: Render frame into PiP container
        // This would involve:
        // 1. Creating a SurfaceTexture for the PiP
        // 2. Rendering video frames into it
        // 3. Handling lifecycle and cleanup
    }

    /**
     * Enable/disable touch interactions
     */
    fun setTouchEnabled(enabled: Boolean) {
        pipOverlay.isEnabled = enabled
        pipOverlay.alpha = if (enabled) 1f else 0.5f
    }

    /**
     * Configure size and position presets
     */
    fun applyPreset(preset: SizePreset) {
        when (preset) {
            SizePreset.COMPACT -> pipOverlay.resize(250, 250)
            SizePreset.MEDIUM -> pipOverlay.resize(350, 350)
            SizePreset.LARGE -> pipOverlay.resize(500, 500)
        }
    }

    enum class SizePreset {
        COMPACT, MEDIUM, LARGE
    }
}
