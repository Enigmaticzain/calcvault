# Video Filter System - Quick Start (5 Minutes)

## Installation

### Step 1: Copy Files (1 min)
```bash
# Core filter system
app/src/main/java/com/calcvault/call/filters/
├── VideoFilterEngine.kt
├── FilterControlPanel.kt
├── FilterSyncEngine.kt
└── FilterSettingsActivity.kt

# Layouts
app/src/main/res/layout/
├── panel_filter_control.xml
└── activity_filter_settings.xml
```

### Step 2: Update CallActivity (2 min)

Add to `onCreate()`:
```kotlin
if (isVideo) {
    initializeFilterSystem()
}
```

Add method:
```kotlin
private fun initializeFilterSystem() {
    filterEngine = VideoFilterEngine(this, binding.videoOverlay)
    filterSyncEngine = FilterSyncEngine(networkEngine)
    filterControlPanel = FilterControlPanel(this)
    
    binding.root.addView(filterControlPanel, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        setMargins(0, 100, 16, 0)
    })
    
    filterControlPanel.onEmojiTrigger = { emoji ->
        filterEngine.triggerEmojiShower(emoji, 1f)
        filterSyncEngine.broadcastEmojiShower(emoji, 1f)
    }
    
    filterControlPanel.onFilterSelected = { filterType, intensity ->
        if (filterType != VideoFilterEngine.FilterType.NONE) {
            filterEngine.applyOverlayFilter(filterType, 0L, intensity)
            filterSyncEngine.broadcastFilterEffect(filterType, intensity)
        }
    }
    
    filterControlPanel.onClearEffects = {
        filterEngine.clearAllEffects()
        filterSyncEngine.broadcastClearEffects()
    }
    
    observeRemoteFilterEffects()
}
```

### Step 3: Handle Remote Effects (1 min)

```kotlin
private fun observeRemoteFilterEffects() {
    networkEngine.onControlSignalReceived = { json ->
        if (json.optString("type") == "filter_effect") {
            handleRemoteFilterEffect(json)
        } else {
            handleControlSignal(json)
        }
    }
}

private fun handleRemoteFilterEffect(json: JSONObject) {
    when (json.optString("type")) {
        "filter_emoji" -> {
            val emoji = VideoFilterEngine.EmojiType.valueOf(
                json.optString("emoji")
            )
            filterEngine.triggerEmojiShower(emoji, 1f)
        }
        "filter_overlay" -> {
            val filterType = VideoFilterEngine.FilterType.valueOf(
                json.optString("filter")
            )
            filterEngine.applyOverlayFilter(filterType, 0L, 1f)
        }
        "filter_clear" -> filterEngine.clearAllEffects()
    }
}
```

### Step 4: Cleanup (1 min)

In `onDestroy()`:
```kotlin
if (isVideo) {
    filterEngine.destroy()
    filterSyncEngine.destroy()
    filterControlPanel.collapse()
}
```

## Quick Examples

### Trigger Emoji Shower
```kotlin
// Heart shower
filterEngine.triggerEmojiShower(VideoFilterEngine.EmojiType.HEART, 1f)

// Fire shower with 50% intensity
filterEngine.triggerEmojiShower(VideoFilterEngine.EmojiType.FIRE, 0.5f)
```

### Apply Filter
```kotlin
// Warm tone
filterEngine.applyOverlayFilter(
    VideoFilterEngine.FilterType.WARM_TONE,
    duration = 0L,
    intensity = 0.8f
)

// Sparkle effect
filterEngine.applyOverlayFilter(
    VideoFilterEngine.FilterType.SPARKLE,
    duration = 0L,
    intensity = 1f
)
```

### Clear Effects
```kotlin
filterEngine.clearAllEffects()
```

## Testing

### Manual Testing

1. **Start Video Call**
   - Launch app
   - Initiate video call
   - Wait for call to connect

2. **Test Emoji Shower**
   - Tap filter panel toggle (top-right)
   - Click heart emoji button
   - Verify hearts fall from top
   - Verify remote peer sees hearts

3. **Test Overlay Filters**
   - Select "Warm Tone" from dropdown
   - Adjust intensity slider
   - Verify warm orange tint appears
   - Verify remote peer sees filter

4. **Test Clear**
   - Click "Clear All" button
   - Verify all effects disappear

### Performance Testing

```kotlin
// Check FPS
val fps = filterEngine.getCurrentFps()
Log.d("FilterEngine", "Current FPS: $fps")

// Check quality level
val quality = filterEngine.getQualityLevel()
Log.d("FilterEngine", "Quality: $quality")

// Adjust target FPS
filterEngine.setTargetFps(30)
```

### Network Testing

1. **Good Network**
   - Effects sync instantly
   - No latency visible

2. **Weak Network**
   - Effects render locally
   - Remote effects may be delayed
   - No blocking of local effects

## Emoji Types

| Emoji | Name | Color | Use Case |
|-------|------|-------|----------|
| ❤️ | HEART | Red | Love, affection |
| 🔥 | FIRE | Orange | Passion, excitement |
| 😂 | LAUGH | Yellow | Joy, humor |
| ⭐ | STAR | Yellow | Celebration, admiration |
| ✨ | SPARKLE | Pink | Magic, wonder |
| 💋 | KISS | Red | Affection, intimacy |
| 😍 | LOVE_EYES | Red | Adoration, love |

## Filter Types

| Filter | Effect | Use Case |
|--------|--------|----------|
| WARM_TONE | Orange tint | Romantic, intimate |
| COOL_TONE | Blue tint | Calm, peaceful |
| DARK_TONE | Dark overlay | Night mode, mysterious |
| SPARKLE | Random sparkles | Magical, celebratory |
| GLOW_AURA | Center glow | Soft, dreamy |
| SOFT_BLUR | Blur effect | Romantic, soft |
| FLOATING_PARTICLES | Drifting particles | Playful, dynamic |

## Troubleshooting

### Effects Not Showing
```kotlin
// Check if filter panel is visible
Log.d("FilterPanel", "Visible: ${filterControlPanel.visibility}")

// Check if engine is initialized
Log.d("FilterEngine", "Initialized: ${::filterEngine.isInitialized}")

// Check device memory
val runtime = Runtime.getRuntime()
val maxMemory = runtime.maxMemory() / (1024 * 1024)
Log.d("Device", "Max Memory: ${maxMemory}MB")
```

### Laggy Performance
```kotlin
// Reduce intensity
filterEngine.applyOverlayFilter(
    VideoFilterEngine.FilterType.SPARKLE,
    0L,
    0.5f  // 50% instead of 100%
)

// Lower target FPS
filterEngine.setTargetFps(30)

// Check current FPS
val fps = filterEngine.getCurrentFps()
if (fps < 24) {
    Log.w("FilterEngine", "Low FPS: $fps")
}
```

### Remote Effects Not Received
```kotlin
// Verify network connection
Log.d("Network", "Connected: ${networkEngine.isConnected}")

// Check sync engine
filterSyncEngine.setConnected(true)

// Verify signal handler
networkEngine.onControlSignalReceived = { json ->
    Log.d("Signal", "Received: ${json.optString("type")}")
}
```

## Performance Tips

1. **Reduce Particle Count**
   - Lower intensity slider
   - Reduces CPU usage

2. **Disable Complex Filters**
   - Use WARM_TONE instead of SPARKLE
   - Simpler = faster

3. **Monitor FPS**
   - Check `getCurrentFps()`
   - Adapt quality if needed

4. **Test on Target Device**
   - Low-end devices auto-reduce quality
   - Verify 24+ FPS minimum

## Next Steps

1. ✅ Copy files to project
2. ✅ Update CallActivity
3. ✅ Add remote effect handler
4. ✅ Add cleanup code
5. ✅ Test locally
6. ✅ Test with remote peer
7. ✅ Deploy to production

## Support

For issues or questions:
1. Check VIDEO_FILTER_GUIDE.md for detailed docs
2. Review VIDEO_FILTER_INTEGRATION.kt for example code
3. Check logcat for error messages
4. Monitor FPS metrics
