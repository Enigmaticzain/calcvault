package com.calcvault.emotional.themes

import android.graphics.*
import kotlin.math.*

/**
 * ShapeUtils
 *
 * Utility functions for drawing enhanced natural shapes:
 *  - Cloud shapes (fluffy, realistic)
 *  - Leaf shapes (with veins, various styles)
 *  - Wind-affected shapes
 *  - Soft shadow/glow effects
 *
 * Usage:
 *   ShapeUtils.drawCloudPuffy(canvas, paint, cx, cy, size)
 *   ShapeUtils.drawLeaf(canvas, paint, x, y, angle, size)
 */
object ShapeUtils {

    /**
     * Draw a fluffy, realistic cloud shape
     * Made of overlapping rounded bumps
     */
    fun drawCloudPuffy(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int = Color.WHITE
    ) {
        paint.color = color
        paint.style = Paint.Style.FILL
        
        // Create cloud path from circles
        // Main body - 3 large bumps
        canvas.drawCircle(cx - size * 0.4f, cy, size * 0.35f, paint)
        canvas.drawCircle(cx, cy, size * 0.45f, paint)
        canvas.drawCircle(cx + size * 0.4f, cy, size * 0.35f, paint)
        
        // Top bumps for fluffiness
        canvas.drawCircle(cx - size * 0.2f, cy - size * 0.3f, size * 0.25f, paint)
        canvas.drawCircle(cx + size * 0.2f, cy - size * 0.3f, size * 0.25f, paint)
        
        // Bottom bumps for extra fluffiness
        canvas.drawCircle(cx - size * 0.3f, cy + size * 0.15f, size * 0.22f, paint)
        canvas.drawCircle(cx + size * 0.3f, cy + size * 0.15f, size * 0.22f, paint)
    }

    /**
     * Draw cloud with gradient for depth
     */
    fun drawCloudGradient(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int = Color.WHITE,
        shadowAlpha: Int = 50
    ) {
        // Draw shadow underneath for depth
        paint.color = Color.argb(shadowAlpha, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + size * 0.3f, size * 0.35f, paint)
        
        // Draw main cloud
        drawCloudPuffy(canvas, paint, cx, cy, size, color)
    }

    /**
     * Draw a thin, wispy cloud (high altitude)
     */
    fun drawCloudWispy(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float,
        alpha: Int = 150
    ) {
        paint.color = Color.argb(alpha, 220, 230, 250)
        paint.style = Paint.Style.FILL
        
        // Thin, elongated bumps
        canvas.drawCircle(cx - size * 0.3f, cy, size * 0.25f, paint)
        canvas.drawCircle(cx, cy, size * 0.30f, paint)
        canvas.drawCircle(cx + size * 0.3f, cy, size * 0.25f, paint)
        canvas.drawCircle(cx + size * 0.6f, cy - size * 0.1f, size * 0.20f, paint)
    }

    /**
     * Draw a storm cloud (dark, dense)
     */
    fun drawCloudStorm(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        size: Float
    ) {
        paint.color = Color.argb(200, 80, 100, 120)
        paint.style = Paint.Style.FILL
        
        // Darker, taller cloud
        canvas.drawCircle(cx - size * 0.4f, cy + size * 0.1f, size * 0.38f, paint)
        canvas.drawCircle(cx, cy - size * 0.1f, size * 0.50f, paint)
        canvas.drawCircle(cx + size * 0.4f, cy + size * 0.1f, size * 0.38f, paint)
        canvas.drawCircle(cx - size * 0.2f, cy - size * 0.3f, size * 0.28f, paint)
        canvas.drawCircle(cx + size * 0.2f, cy - size * 0.3f, size * 0.28f, paint)
    }

    /**
     * Draw a leaf shape with veins
     * Supports multiple leaf types: simple, oak, maple, grass
     */
    fun drawLeaf(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        angle: Float = 0f,
        size: Float = 20f,
        color: Int = Color.GREEN,
        type: LeafType = LeafType.SIMPLE
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        
        when (type) {
            LeafType.SIMPLE -> drawSimpleLeaf(canvas, paint, 0f, 0f, size, color)
            LeafType.OAK -> drawOakLeaf(canvas, paint, 0f, 0f, size, color)
            LeafType.MAPLE -> drawMapleLeaf(canvas, paint, 0f, 0f, size, color)
            LeafType.GRASS -> drawGrassLeaf(canvas, paint, 0f, 0f, size, color)
        }
        
        canvas.restore()
    }

    /**
     * Simple leaf: pointed ellipse with center vein
     */
    private fun drawSimpleLeaf(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        
        // Leaf shape (pointed oval)
        val leafPath = Path().apply {
            moveTo(x, y - size)  // tip
            cubicTo(
                x + size * 0.4f, y - size * 0.5f,
                x + size * 0.5f, y + size * 0.3f,
                x, y + size * 0.5f  // base
            )
            cubicTo(
                x - size * 0.5f, y + size * 0.3f,
                x - size * 0.4f, y - size * 0.5f,
                x, y - size  // back to tip
            )
            close()
        }
        
        canvas.drawPath(leafPath, paint)
        
        // Center vein
        paint.color = Color.argb(150, 0, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        canvas.drawLine(x, y - size, x, y + size * 0.5f, paint)
    }

    /**
     * Oak leaf: rounded lobed shape
     */
    private fun drawOakLeaf(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        paint.color = color
        paint.style = Paint.Style.FILL
        
        // Lobed shape (simplified oak leaf)
        val oakPath = Path().apply {
            moveTo(x, y - size)  // top
            
            // Upper lobes
            cubicTo(x + size * 0.3f, y - size * 0.6f, x + size * 0.5f, y - size * 0.3f, x + size * 0.4f, y)
            cubicTo(x + size * 0.6f, y + size * 0.1f, x + size * 0.5f, y + size * 0.4f, x + size * 0.2f, y + size * 0.5f)
            
            // Base
            cubicTo(x, y + size * 0.6f, x - size * 0.2f, y + size * 0.5f, x - size * 0.2f, y + size * 0.5f)
            
            // Lower lobes (mirrored)
            cubicTo(x - size * 0.5f, y + size * 0.4f, x - size * 0.6f, y + size * 0.1f, x - size * 0.4f, y)
            cubicTo(x - size * 0.5f, y - size * 0.3f, x - size * 0.3f, y - size * 0.6f, x, y - size)
            
            close()
        }
        
        canvas.drawPath(oakPath, paint)
        
        // Veins
        paint.color = Color.argb(100, 0, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.3f
        
        // Center vein
        canvas.drawLine(x, y - size, x, y + size * 0.5f, paint)
        
        // Side veins
        canvas.drawLine(x + size * 0.2f, y - size * 0.6f, x + size * 0.4f, y, paint)
        canvas.drawLine(x - size * 0.2f, y - size * 0.6f, x - size * 0.4f, y, paint)
    }

    /**
     * Maple leaf: pointy lobed shape
     */
    private fun drawMapleLeaf(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        paint.color = color
        paint.style = Paint.Style.FILL
        
        // Star-like 5-point leaf
        val points = 5
        val outerRadius = size
        val innerRadius = size * 0.4f
        
        val maplePath = Path()
        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = (i * PI / points) - (PI / 2)
            val px = x + radius * cos(angle).toFloat()
            val py = y + radius * sin(angle).toFloat()
            
            if (i == 0) maplePath.moveTo(px, py) else maplePath.lineTo(px, py)
        }
        maplePath.close()
        
        canvas.drawPath(maplePath, paint)
        
        // Center node
        paint.color = Color.argb(150, Color.red(color) / 2, Color.green(color) / 2, Color.blue(color) / 2)
        canvas.drawCircle(x, y, size * 0.15f, paint)
    }

    /**
     * Grass blade: thin and curved
     */
    private fun drawGrassLeaf(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.1f
        paint.strokeCap = Paint.Cap.ROUND
        
        // Curved grass blade
        val grassPath = Path().apply {
            moveTo(x, y)
            quadTo(
                x + sin(0f) * size * 0.2f, y - size * 0.5f,
                x + sin(0.5f) * size * 0.3f, y - size
            )
        }
        
        canvas.drawPath(grassPath, paint)
    }

    enum class LeafType {
        SIMPLE, OAK, MAPLE, GRASS
    }

    /**
     * Draw falling leaf particle with flutter animation
     */
    fun drawFallingLeaf(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        rotation: Float,
        size: Float,
        color: Int,
        type: LeafType = LeafType.SIMPLE,
        alpha: Int = 255
    ) {
        paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        drawLeaf(canvas, paint, x, y, rotation, size, paint.color, type)
    }
}
