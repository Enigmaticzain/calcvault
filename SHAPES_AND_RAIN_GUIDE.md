# Theme Shapes & Rain - Developer Guide

## Quick Start

### 1. Using Improved Clouds

```kotlin
// IN ANY THEME - Draw fluffy cloud
ShapeUtils.drawCloudPuffy(
    canvas, paint,
    cx = width * 0.5f,
    cy = height * 0.2f,
    size = 80f,
    color = Color.WHITE
)

// OR with shadow depth
ShapeUtils.drawCloudGradient(
    canvas, paint,
    cx = width * 0.5f,
    cy = height * 0.2f,
    size = 80f,
    color = Color.WHITE,
    shadowAlpha = 50
)

// OR wispy (high altitude)
ShapeUtils.drawCloudWispy(
    canvas, paint,
    cx = width * 0.3f,
    cy = height * 0.1f,
    size = 70f,
    alpha = 200  // Semi-transparent
)

// OR storm cloud
ShapeUtils.drawCloudStorm(
    canvas, paint,
    cx = width * 0.7f,
    cy = height * 0.3f,
    size = 90f
)
```

### 2. Using Leaf Shapes

```kotlin
// Draw a single oak leaf
ShapeUtils.drawLeaf(
    canvas, paint,
    x = 150f,
    y = 200f,
    angle = 45f,           // Rotation degrees
    size = 20f,
    color = Color.GREEN,
    type = ShapeUtils.LeafType.OAK
)

// Try other types:
// - ShapeUtils.LeafType.SIMPLE (pointed ellipse)
// - ShapeUtils.LeafType.MAPLE (5-pointed star)
// - ShapeUtils.LeafType.GRASS (thin blade)
```

### 3. Adding Rain to a Scene

```kotlin
// IN CLASS (member variable)
private var rainAnimator: RainAnimator? = null
private var rainIntensity = 0f

// IN onSizeChanged()
rainAnimator = RainAnimator(
    screenWidth.toFloat(),
    screenHeight.toFloat(),
    intensity = 0f  // Start with no rain
)

// IN update()
if (rainIntensity > 0f) {
    rainAnimator?.setIntensity(rainIntensity)
    rainAnimator?.update()
    rainIntensity -= 0.01f  // Fade rainout
}

// IN onDraw()
if (rainIntensity > 0f) {
    rainAnimator?.draw(canvas, paint)
}

// TO TRIGGER RAIN
rainIntensity = 0.7f  // Light rain
// or
rainIntensity = 0.9f  // Heavy rain
```

---

## Cloud Shape Details

### Cloud Puffy (Most Common)

**Best For:** Sunny days, daytime scenes, general sky fill

**Dimensions:**
- Made of 7 overlapping circles
- Main body: 3 large circles
- Top bumps: 2 smaller circles
- Bottom bumps: 2 additional circles

**Visual Effect:**
- Soft, fluffy appearance
- Good depth perception
- Friendly, inviting

**Code:**
```kotlin
// 7-circle composition (internal)
canvas.drawCircle(cx - size * 0.4f, cy, size * 0.35f, paint)  // Left
canvas.drawCircle(cx, cy, size * 0.45f, paint)               // Center (largest)
canvas.drawCircle(cx + size * 0.4f, cy, size * 0.35f, paint) // Right

canvas.drawCircle(cx - size * 0.2f, cy - size * 0.3f, size * 0.25f, paint)  // Top-left
canvas.drawCircle(cx + size * 0.2f, cy - size * 0.3f, size * 0.25f, paint)  // Top-right

canvas.drawCircle(cx - size * 0.3f, cy + size * 0.15f, size * 0.22f, paint) // Bottom-left
canvas.drawCircle(cx + size * 0.3f, cy + size * 0.15f, size * 0.22f, paint) // Bottom-right
```

### Cloud Wispy (Thin & Delicate)

**Best For:** High altitude, ethereal sky, light weather

**Characteristics:**
- 5 elongated bumps
- Semi-transparent (alpha ~200/255)
- Light blue-white color

**Visual Effect:**
- Feather-like appearance
- Atmospheric depth
- Distant clouds

---

## Leaf Shape Details

### Leaf Types Comparison

```
SIMPLE      MAPLE       OAK         GRASS
  ^         ↑↑↑         ∿∿∿         |
  |         * *         ◊◊◊         /
  |        • • •        |◊|        /
  ↓         * *         ∿∿∿       /
```

### Simple Leaf

**Best For:** Quick foliage, general fill

**Shape:**
- Pointed ellipse (top to bottom)
- Single center vein
- Symmetric sides

**Usage:**
```kotlin
type = ShapeUtils.LeafType.SIMPLE
```

### Oak Leaf

**Best For:** Detailed trees, realistic foliage

**Shape:**
- Wavy, lobed edges
- 3 main lobes per side
- Multiple side veins
- Historically accurate

**Usage:**
```kotlin
type = ShapeUtils.LeafType.OAK
```

### Maple Leaf

**Best For:** Iconic appearance, decoration

**Shape:**
- Perfect 5-pointed star
- Center node
- Symmetrical points
- Recognizable silhouette

**Usage:**
```kotlin
type = ShapeUtils.LeafType.MAPLE
```

### Grass Leaf

**Best For:** Grass blades, thin vegetation

**Shape:**
- Curved thin stroke
- Tapering width
- Natural bend
- Lightweight appearance

**Usage:**
```kotlin
type = ShapeUtils.LeafType.GRASS
paint.strokeWidth = size * 0.1f  // Stroke thickness
```

---

## Rain System - Deep Dive

### RainAnimator Parameters

```kotlin
RainAnimator(
    screenWidth: Float,      // Canvas width
    screenHeight: Float,     // Canvas height
    intensity: Float = 0.5f  // 0.0 (none) to 1.0 (heavy)
)
```

### Intensity Levels

| Value | Effect | Drops | Use Case |
|-------|--------|-------|----------|
| 0.0 | No rain | 0 | Clear sky |
| 0.2 | Drizzle | ~30 | Light mist |
| 0.5 | Moderate | ~75 | Normal rain |
| 0.7 | Heavy | ~105 | Storm |
| 1.0 | Downpour | ~200 | Intense rain |

### Physics Constants

```kotlin
// In RainAnimator.kt companion object:

const val GRAVITY = 0.15f
// How fast raindrops accelerate downward
// Higher = faster fall speed increase
// Realistic value: 0.1-0.2

const val BASE_DROP_SPEED = 8f
// Starting vertical velocity
// Higher = rain appears faster
// Realistic: 5-12

const val WIND_STRENGTH = 0.03f
// How much horizontal drift wind creates
// Example: 0.03f means max 3% of width per frame
// Increases/decreases visual wind intensity

const val SPLASH_DURATION = 30
// Frames until splash completely fades
// 30 frames @ 60fps = 0.5 seconds
// Adjust for faster/slower ripple effect

const val SPLASH_PARTICLES = 4
// Visual ripples per splash (not particles)
// Creates double-ring effect
```

### Customizing Rain Colors

```kotlin
// Default: Light blue
// Color.argb(200, 150, 200, 255)

// To change, modify in RainAnimator.draw():
paint.color = Color.argb(
    (drop.alpha * 255).toInt(),
    150,  // Red   - change for different color
    200,  // Green
    255   // Blue
)
```

### Creating Different Rain Effects

**Light Drizzle:**
```kotlin
rainAnimator?.setIntensity(0.2f)
// Slow speed variant:
// Modify BASE_DROP_SPEED = 3f for slower drops
```

**Thunderstorm:**
```kotlin
rainAnimator?.setIntensity(0.95f)
// Add lightning frequency increase
// Darken background colors
// Add wind gusts
```

**Freezing Rain:**
```kotlin
rainAnimator?.setIntensity(0.6f)
// Change color to whiter (less blue)
// Reduce drop speed variation
// Add horizontal direction bias
```

---

## Animation Timing

### Leaf Flutter

```kotlin
// Falling speed
vy += 0.05f per frame
// At 60fps, 12 frames to fall 100 pixels

// Rotation
rotationSpeed = (Random.nextFloat() - 0.5f) * 15f
// Ranges from -7.5 to +7.5 degrees per frame
// Results in spinning leaves

// Horizontal drift
vx = (Random.nextFloat() - 0.5f) * 0.8f
// Ranges from -0.4 to +0.4 pixels per frame
// Wind effect subtly moves leaves sideways
```

### Rain Drop Speed

```kotlin
// Initial: BASE_DROP_SPEED (8f pixels/frame)
// Acceleration: GRAVITY (0.15f pixels/frame²)
// At 60fps, takes ~53 frames to fall screen height (720px)
// = ~0.9 seconds of visible rain

// Wind gust frequency
windPhase += 0.02f per update
// sin(windPhase) creates smooth oscillation
// Complete cycle: ~314 frames (~5.2 seconds)
```

### Cloud Positioning

```kotlin
// For clouds that drift
cloud.x += cloud.speed
// Speed typically 0.3f to 0.7f pixels per frame
// 0.5f speed crosses screen in ~24 seconds (1920px @ 60fps)
```

---

## Integration Examples

### Example 1: Forest Rain Scene

```kotlin
override fun onDraw(canvas: Canvas) {
    // Draw forest background
    drawForest(canvas)
    
    // Draw falling leaves
    for (leaf in fallingLeaves) {
        ShapeUtils.drawFallingLeaf(
            canvas, paint,
            leaf.x, leaf.y,
            leaf.rotation,
            leaf.size,
            leaf.color,
            leaf.leafType,
            (leaf.alpha * 255).toInt()
        )
    }
    
    // Draw characters below leaves
    drawCharacters(canvas)
    
    // Draw rain on top (for visibility)
    rainAnimator?.draw(canvas, paint)
}
```

### Example 2: Sunny Day Meadow

```kotlin
override fun onDraw(canvas: Canvas) {
    // Sky with gradient
    paint.shader = LinearGradient(
        0f, 0f, 0f, height * 0.5f,
        intArrayOf(Color.parseColor("#81D4FA"), 
                   Color.parseColor("#B3E5FC")),
        null, Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), 
                    height * 0.5f, paint)
    paint.shader = null
    
    // Fluffy clouds scattered
    ShapeUtils.drawCloudPuffy(
        canvas, paint,
        width * 0.2f, height * 0.15f,
        70f, Color.WHITE
    )
    ShapeUtils.drawCloudPuffy(
        canvas, paint,
        width * 0.8f, height * 0.2f,
        80f, Color.WHITE
    )
    
    // Grass with wind-swayed appearance
    for (i in 0..50) {
        val x = width * (i / 50f)
        val y = height * 0.6f
        val sway = sin(windPhase + i).toFloat() * 5f
        
        ShapeUtils.drawLeaf(
            canvas, paint,
            x + sway, y,
            angle = windPhase.toInt() % 360,  // Gentle rotation
            size = 10f,
            color = Color.parseColor("#66BB6A"),
            type = ShapeUtils.LeafType.GRASS
        )
    }
    
    // Ground
    paint.color = Color.parseColor("#2196F3")
    canvas.drawRect(0f, height * 0.6f, width.toFloat(), 
                    height.toFloat(), paint)
}
```

### Example 3: Autumn Forest

```kotlin
override fun onDraw(canvas: Canvas) {
    // Moody sky
    paint.color = Color.parseColor("#546E7A")
    canvas.drawRect(0f, 0f, width.toFloat(), 
                    height.toFloat(), paint)
    
    // Storm clouds
    ShapeUtils.drawCloudStorm(
        canvas, paint,
        width * 0.4f, height * 0.2f, 100f
    )
    
    // Heavy rain
    rainAnimator?.draw(canvas, paint)
    
    // Falling autumn leaves (multiple colors)
    val autumnColors = listOf(
        Color.parseColor("#FF6F00"),  // Orange
        Color.parseColor("#D32F2F"),  // Red
        Color.parseColor("#F9A825"),  // Gold
        Color.parseColor("#8B4513")   // Brown
    )
    
    for (leaf in fallingLeaves) {
        val color = autumnColors[fallingLeaves.indexOf(leaf) % autumnColors.size]
        
        ShapeUtils.drawFallingLeaf(
            canvas, paint,
            leaf.x, leaf.y,
            leaf.rotation,
            leaf.size,
            color,
            ShapeUtils.LeafType.OAK,
            (leaf.alpha * 200).toInt()
        )
    }
}
```

---

## Performance Tips

✅ **Best Practices**

1. **Leaf Count**
   - Cap at 30 falling leaves
   - Recycle old leaves (don't create new lists)

2. **Rain Intensity**
   - Use 0.0-0.3 for light effects
   - Use 0.5-0.7 for noticeable effects
   - Use 0.8-1.0 only for short durations

3. **Cloud Rendering**
   - Use gradient version only when needed
   - Cache paint color before loops
   - Reuse path objects

4. **Memory**
   - RainAnimator auto-manages drops
   - Remove leaves when off-screen
   - Clear animators when scenes change

---

## Troubleshooting

### Clouds Not Visible

**Issue:** Clouds not appearing on screen

**Causes:**
- Wrong paint style (try `paint.style = Paint.Style.FILL`)
- Color fully transparent (check alpha in color)
- Size too small (increase size parameter)

**Solution:**
```kotlin
paint.color = Color.WHITE  // Ensure opaque
paint.style = Paint.Style.FILL
ShapeUtils.drawCloudPuffy(canvas, paint, x, y, 80f)
```

### Leaves Not Falling

**Issue:** Leaves spawn but don't appear

**Causes:**
- Not calling `drawFallingLeaves()` in `onDraw()`
- Leaves spawn off-screen
- Alpha set to 0

**Solution:**
```kotlin
// Verify in onDraw():
drawFallingLeaves(canvas)  // Must call

// Check leaf data:
Log.d("Leaves", "Count: ${fallingLeaves.size}, First: ${fallingLeaves.firstOrNull()?.y}")
```

### Rain Not Showing

**Issue:** Rain animator created but invisible

**Causes:**
- `rainIntensity` never set above 0
- `rainAnimator?.draw()` not called
- Paint color wrong

**Solution:**
```kotlin
// In update loop:
rainIntensity = 0.5f

// In draw loop:
if (rainIntensity > 0f) {
    rainAnimator?.draw(canvas, paint)
}
```

---

## Advanced Customization

### Custom Leaf Color Gradients

```kotlin
// Mix leaf colors based on scene
val leafColor = when (currentScene) {
    Scene.SPRING -> Color.parseColor("#90EE90")  // Light green
    Scene.SUMMER -> Color.parseColor("#228B22")  // Forest green
    Scene.AUTUMN -> Color.parseColor("#FF8C00")  // Dark orange
    Scene.WINTER -> Color.parseColor("#F0F8FF")  // Alice blue
}

ShapeUtils.drawLeaf(canvas, paint, x, y, 
                   angle, size, leafColor, leafType)
```

### Dynamic Rain Triggering

```kotlin
// Trigger rain based on time or events
val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
if (hourOfDay in 18..21) {  // Evening
    rainIntensity = 0.4f  // Likely evening shower
}

// Or based on scene progression
if (sceneTimer > 5000) {
    rainIntensity = 0.6f  // Intensify after 5 seconds
}
```

### Particle Count Scaling

```kotlin
// Adjust based on device performance
val deviceTier = when {
    Runtime.getRuntime().maxMemory() > 3_000_000_000 -> Tier.HIGH
    Runtime.getRuntime().maxMemory() > 1_000_000_000 -> Tier.MEDIUM
    else -> Tier.LOW
}

val leafCap = when (deviceTier) {
    Tier.HIGH -> 50
    Tier.MEDIUM -> 30
    Tier.LOW -> 15
}
```

---

**Reference Status**: ✅ COMPLETE - Full developer guide available
