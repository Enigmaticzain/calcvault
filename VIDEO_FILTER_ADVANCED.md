# Video Filter System - Advanced Usage & Best Practices

## Advanced Integration Patterns

### Pattern 1: Conditional Filter Activation

```kotlin
private fun initializeFilterSystem() {
    filterEngine = VideoFilterEngine(this, binding.videoOverlay)
    filterSyncEngine = FilterSyncEngine(networkEngine)
    
    // Only show panel if user has enabled filters in settings
    val config = storageManager.getJson("filter_config") ?: JSONObject()
    val filtersEnabled = config.optBoolean("emoji_enabled", true)
    
    if (filtersEnabled) {
        filterControlPanel = FilterControlPanel(this)
        binding.root.addView(filterControlPanel, ...)
        setupFilterCallbacks()
    }
}
```

### Pattern 2: Mood-Based Auto-Effects

```kotlin
private fun applyMoodBasedFilter(mood: String) {
    val filterType = when (mood.lowercase()) {
        "romantic" -> VideoFilterEngine.FilterType.WARM_TONE
        "calm" -> VideoFilterEngine.FilterType.COOL_TONE
        "playful" -> VideoFilterEngine.FilterType.SPARKLE
        "intimate" -> VideoFilterEngine.FilterType.GLOW_AURA
        else -> VideoFilterEngine.FilterType.NONE
    }
    
    if (filterType != VideoFilterEngine.FilterType.NONE) {
        filterEngine.applyOverlayFilter(filterType, 0L, 0.7f)
        filterSyncEngine.broadcastFilterEffect(filterType, 0.7f)
    }
}
```

### Pattern 3: Message-Triggered Effects

```kotlin
private fun handleMessageWithEffects(message: String) {
    val lowerMessage = message.lowercase()
    
    when {
        lowerMessage.contains("love") || lowerMessage.contains("❤️") -> {
            filterEngine.triggerEmojiShower(VideoFilterEngine.EmojiType.HEART, 1f)
            filterSyncEngine.broadcastEmojiShower(VideoFilterEngine.EmojiType.HEART, 1f)
        }
        lowerMessage.contains("haha") || lowerMessage.contains("😂") -> {
            filterEngine.triggerEmojiShower(VideoFilterEngine.EmojiType.LAUGH, 1f)
            filterSyncEngine.broadcastEmojiShower(VideoFilterEngine.EmojiType.LAUGH, 1f)
        }
        lowerMessage.contains("fire") || lowerMessage.contains("🔥") -> {
            filterEngine.triggerEmojiShower(VideoFilterEngine.EmojiType.FIRE, 1f)
            filterSyncEngine.broadcastEmojiShower(VideoFilterEngine.EmojiType.FIRE, 1f)
        }
    }
}
```

### Pattern 4: Gesture-Based Effects

```kotlin
private fun setupGestureDetection() {
    binding.videoOverlay.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                
                // Divide screen into quadrants for different effects
                when {
                    x < v.width / 2 && y < v.height / 2 -> {
                        // Top-left: Heart
                        filterEngine.triggerEmojiShower(
                            VideoFilterEngine.EmojiType.HEART, 1f
                        )
                    }
                    x >= v.width / 2 && y < v.height / 2 -> {
                        // Top-right: Fire
                        filterEngine.triggerEmojiShower(
                            VideoFilterEngine.EmojiType.FIRE, 1f
                        )
                    }
                    x < v.width / 2 && y >= v.height / 2 -> {
                        // Bottom-left: Sparkle
                        filterEngine.applyOverlayFilter(
                            VideoFilterEngine.FilterType.SPARKLE, 0L, 1f
                        )
                    }
                    else -> {
                        // Bottom-right: Clear
                        filterEngine.clearAllEffects()
                    }
                }
            }
        }
        true
    }
}
```

### Pattern 5: Timed Effect Sequences

```kotlin
private fun playEffectSequence() {
    lifecycleScope.launch {
        // Heart shower
        filterEngine.triggerEmojiShower(VideoFilterEngine.EmojiType.HEART, 1f)
        filterSyncEngine.broadcastEmojiShower(VideoFilterEngine.EmojiType.HEART, 1f)
        delay(1000)
        
        // Apply warm tone
        filterEngine.applyOverlayFilter(
            VideoFilterEngine.FilterType.WARM_TONE, 0L, 0.8f
        )
        filterSyncEngine.broadcastFilterEffect(
            VideoFilterEngine.FilterType.WARM_TONE, 0.8f
        )
        delay(2000)
        
        // Add glow
        filterEngine.applyOverlayFilter(
            VideoFilterEngine.FilterType.GLOW_AURA, 0L, 0.6f
        )
        filterSyncEngine.broadcastFilterEffect(
            VideoFilterEngine.FilterType.GLOW_AURA, 0.6f
        )
        delay(3000)
        
        // Clear all
        filterEngine.clearAllEffects()
        filterSyncEngine.broadcastClearEffects()
    }
}
```

## Performance Optimization Tips

### Tip 1: Reduce Particle Count on Low-End Devices

```kotlin
private fun initializeFilterSystem() {
    filterEngine = VideoFilterEngine(this, binding.videoOverlay)
    
    // Check device capabilities
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory() / (1024 * 1024)
    
    if (maxMemory < 512) {
        // Low-end device: reduce FPS target
        filterEngine.setTargetFps(30)
    }
}
```

### Tip 2: Monitor and Adapt Quality

```kotlin
private fun startQualityMonitoring() {
    lifecycleScope.launch {
        while (isActive) {
            val fps = filterEngine.getCurrentFps()
            val quality = filterEngine.getQualityLevel()
            
            Log.d("FilterEngine", "FPS: $fps, Quality: $quality")
            
            if (fps < 24) {
                Log.w("FilterEngine", "Low FPS detected, reducing effects")
                // Disable complex filters
                filterControlPanel.collapse()
            }
            
            delay(5000)
        }
    }
}
```

### Tip 3: Batch Effect Updates

```kotlin
private fun applyMultipleEffects(effects: List<Pair<String, Float>>) {
    // Apply all effects at once instead of individually
    effects.forEach { (effectName, intensity) ->
        when (effectName) {
            "warm" -> filterEngine.applyOverlayFilter(
                VideoFilterEngine.FilterType.WARM_TONE, 0L, intensity
            )
            "sparkle" -> filterEngine.applyOverlayFilter(
                VideoFilterEngine.FilterType.SPARKLE, 0L, intensity
            )
        }
    }
    
    // Broadcast once
    filterSyncEngine.broadcastFilterEffect(
        VideoFilterEngine.FilterType.WARM_TONE, 0.8f
    )
}
```

### Tip 4: Lazy Load Filter Panel

```kotlin
private fun lazyLoadFilterPanel() {
    // Don't create panel until first video call
    if (!::filterControlPanel.isInitialized) {
        filterControlPanel = FilterControlPanel(this)
        binding.root.addView(filterControlPanel, ...)
        setupFilterCallbacks()
    }
}
```

## Best Practices

### Practice 1: Always Cleanup Resources

```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // Cleanup in correct order
    if (::filterControlPanel.isInitialized) {
        filterControlPanel.collapse()
    }
    
    if (::filterEngine.isInitialized) {
        filterEngine.destroy()
    }
    
    if (::filterSyncEngine.isInitialized) {
        filterSyncEngine.destroy()
    }
}
```

### Practice 2: Handle Network Failures Gracefully

```kotlin
private fun broadcastEffectWithFallback(
    emoji: VideoFilterEngine.EmojiType
) {
    try {
        filterSyncEngine.broadcastEmojiShower(emoji, 1f)
    } catch (e: Exception) {
        Log.w("FilterSync", "Failed to broadcast: ${e.message}")
        // Effect still renders locally
        filterEngine.triggerEmojiShower(emoji, 1f)
    }
}
```

### Practice 3: Validate User Input

```kotlin
private fun applyUserSelectedFilter(filterIndex: Int, intensity: Int) {
    // Validate inputs
    if (filterIndex < 0 || filterIndex > 7) {
        Log.e("FilterPanel", "Invalid filter index: $filterIndex")
        return
    }
    
    if (intensity < 0 || intensity > 100) {
        Log.e("FilterPanel", "Invalid intensity: $intensity")
        return
    }
    
    val filterType = when (filterIndex) {
        0 -> VideoFilterEngine.FilterType.NONE
        1 -> VideoFilterEngine.FilterType.WARM_TONE
        // ... etc
        else -> return
    }
    
    filterEngine.applyOverlayFilter(filterType, 0L, intensity / 100f)
}
```

### Practice 4: Log Performance Metrics

```kotlin
private fun logPerformanceMetrics() {
    val fps = filterEngine.getCurrentFps()
    val quality = filterEngine.getQualityLevel()
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    
    Log.d("FilterMetrics", """
        FPS: $fps
        Quality: $quality
        Memory: ${usedMemory}MB
    """.trimIndent())
}
```

### Practice 5: Respect User Preferences

```kotlin
private fun loadUserPreferences() {
    val config = storageManager.getJson("filter_config") ?: JSONObject()
    
    val emojiEnabled = config.optBoolean("emoji_enabled", true)
    val overlayEnabled = config.optBoolean("overlay_enabled", true)
    val autoQuality = config.optBoolean("auto_quality", true)
    val defaultIntensity = config.optInt("default_intensity", 70)
    
    if (!emojiEnabled) {
        // Disable emoji buttons
        filterControlPanel.disableEmojiButtons()
    }
    
    if (!overlayEnabled) {
        // Disable filter selector
        filterControlPanel.disableFilterSelector()
    }
    
    if (autoQuality) {
        // Enable quality monitoring
        startQualityMonitoring()
    }
}
```

## Testing Strategies

### Test 1: Unit Test Particle Lifecycle

```kotlin
@Test
fun testEmojiParticleLifecycle() {
    val particle = VideoFilterEngine.EmojiParticle(
        x = 100f,
        y = 100f,
        vx = 1f,
        vy = 1f,
        emoji = VideoFilterEngine.EmojiType.HEART,
        lifetime = 1000L
    )
    
    assertTrue(particle.isAlive())
    
    Thread.sleep(1100)
    
    assertFalse(particle.isAlive())
}
```

### Test 2: Integration Test Effect Sync

```kotlin
@Test
fun testEffectSync() {
    val mockNetwork = mockk<NetworkMessageEngine>()
    val syncEngine = FilterSyncEngine(mockNetwork)
    
    syncEngine.broadcastEmojiShower(
        VideoFilterEngine.EmojiType.HEART, 1f
    )
    
    verify {
        mockNetwork.sendControlSignal(
            "filter_effect",
            any()
        )
    }
}
```

### Test 3: Performance Test FPS

```kotlin
@Test
fun testEmojiShowerFps() {
    val startTime = System.currentTimeMillis()
    var frameCount = 0
    
    repeat(300) {
        filterEngine.triggerEmojiShower(
            VideoFilterEngine.EmojiType.HEART, 1f
        )
        frameCount++
    }
    
    val duration = System.currentTimeMillis() - startTime
    val fps = (frameCount * 1000) / duration
    
    assertTrue(fps >= 24)
}
```

## Debugging Tips

### Tip 1: Enable Verbose Logging

```kotlin
private fun enableVerboseLogging() {
    Log.d("FilterEngine", "Emoji particles: ${filterEngine.emojiParticles.size}")
    Log.d("FilterEngine", "Active effects: ${filterEngine.activeEffects.size}")
    Log.d("FilterEngine", "Current FPS: ${filterEngine.getCurrentFps()}")
    Log.d("FilterEngine", "Quality level: ${filterEngine.getQualityLevel()}")
}
```

### Tip 2: Visualize Performance

```kotlin
private fun drawPerformanceOverlay() {
    val fps = filterEngine.getCurrentFps()
    val quality = filterEngine.getQualityLevel()
    
    binding.tvDebugInfo.text = """
        FPS: $fps
        Quality: $quality
        Particles: ${filterEngine.emojiParticles.size}
        Effects: ${filterEngine.activeEffects.size}
    """.trimIndent()
}
```

### Tip 3: Test Network Conditions

```kotlin
private fun simulateWeakNetwork() {
    // Throttle network to 1Mbps
    // Trigger effects
    // Verify local rendering works
    // Verify no call disruption
}
```

## Common Pitfalls to Avoid

### Pitfall 1: Not Cleaning Up Resources
❌ **Wrong:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    // Forgot to cleanup filter system
}
```

✅ **Right:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    filterEngine.destroy()
    filterSyncEngine.destroy()
}
```

### Pitfall 2: Blocking Main Thread
❌ **Wrong:**
```kotlin
filterEngine.triggerEmojiShower(emoji, 1f)
Thread.sleep(1000)  // Blocks UI!
```

✅ **Right:**
```kotlin
filterEngine.triggerEmojiShower(emoji, 1f)
lifecycleScope.launch {
    delay(1000)
    // Continue on main thread
}
```

### Pitfall 3: Not Handling Network Failures
❌ **Wrong:**
```kotlin
filterSyncEngine.broadcastEmojiShower(emoji, 1f)
// Assumes success
```

✅ **Right:**
```kotlin
try {
    filterSyncEngine.broadcastEmojiShower(emoji, 1f)
} catch (e: Exception) {
    Log.w("FilterSync", "Broadcast failed: ${e.message}")
    // Effect still renders locally
}
```

## Summary

- Use conditional activation for better UX
- Monitor performance metrics continuously
- Respect user preferences
- Always cleanup resources
- Handle network failures gracefully
- Test on various devices
- Log performance data
- Validate user input
- Use lazy loading where appropriate
- Batch updates when possible

For more information, see [VIDEO_FILTER_GUIDE.md](VIDEO_FILTER_GUIDE.md).
