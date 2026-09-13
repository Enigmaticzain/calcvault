# Adult Couple Dynamic Theme System - Integration Guide

## Overview

The Adult Couple Dynamic Theme creates an immersive, emotionally-connected chat experience where:
- Two adult characters (Zain & Sanu) exist in a living, animated environment
- Background scenes change dynamically (waterfall, sunset, beach, forest, camping, calm)
- Characters perform subtle, looped relationship animations
- Message bubbles are styled per character identity
- Emotional triggers from chat content drive scene and interaction changes

---

## Architecture

### Core Components

#### 1. **DynamicCoupleTheme.kt**
Main theme engine managing:
- Scene rotation and locking
- Character interaction state
- Emotional triggers from messages and moods
- Configuration persistence

**Key Classes:**
- `DynamicCoupleTheme` - Main controller
- `SceneBackgroundView` - Animated background renderer
- `CoupleCharacterView` - Character animation renderer

#### 2. **CoupleThemeApplicator.kt**
Integration layer providing:
- Activation/deactivation in ChatActivity
- Message bubble styling per character
- Scene and interaction updates
- Configuration management

#### 3. **CoupleThemeSettingsActivity.kt**
User settings UI for:
- Enable/disable characters
- Enable/disable interactions
- Animation intensity slider (0-100%)
- Auto scene switch toggle
- Scene locking
- Interaction preview

---

## Features

### 🎭 Character Interactions

```kotlin
enum class CharacterInteraction {
    SITTING_TOGETHER,      // Default idle state
    TALKING_SOFTLY,        // Gentle bobbing animation
    HOLDING_HANDS,         // Hand connection line
    LEANING_HEAD,          // Head tilt toward partner
    LIGHT_HUG,             // Subtle embrace
    CUDDLING,              // Close proximity
    WATCHING_VIEW          // Swaying together
}
```

### 🌄 Scene Types

```kotlin
enum class SceneType {
    WATERFALL,  // Flowing water, green forest
    SUNSET,     // Golden sun, warm colors
    BEACH,      // Sand, ocean waves
    FOREST,     // Trees, earth tones
    CAMPING,    // Fire, night sky
    CALM        // Peaceful, minimal
}
```

### 💬 Message Triggers

Automatic interaction changes based on message content:

```
"miss" → HOLDING_HANDS
"love" → LIGHT_HUG
"good night" → CUDDLING
"good morning" → WATCHING_VIEW
```

### 😊 Mood-Based Scenes

Scene changes based on partner mood:

```
LOVE → SUNSET
BUSY → CALM
HAPPY → BEACH
RELAXED → WATERFALL
```

---

## Integration Steps

### Step 1: Add to ChatActivity

```kotlin
import com.calcvault.emotional.themes.CoupleThemeApplicator

class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setup ...
        
        // Activate couple theme
        CoupleThemeApplicator.activate(this, binding.chatRoot)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        CoupleThemeApplicator.deactivate()
    }
}
```

### Step 2: Hook Message Events

```kotlin
networkEngine.onMessageReceived = { msg ->
    // ... existing handling ...
    CoupleThemeApplicator.onMessageReceived(msg.from, msg.content)
}
```

### Step 3: Hook Mood Updates

```kotlin
private fun updatePartnerMood() {
    val mood = messageDB.getLatestMood(partnerUserId)
    if (mood != null) {
        CoupleThemeApplicator.onMoodUpdate(mood)
    }
}
```

### Step 4: Add Settings Menu Item

In SettingsActivity or MainVaultActivity:

```kotlin
VaultItem("👫", "Couple Theme", "#FF1493") { 
    startActivity(Intent(this, CoupleThemeSettingsActivity::class.java)) 
}
```

---

## Configuration

### Default Config

```kotlin
CoupleThemeConfig(
    enableCharacters = true,
    enableInteractions = true,
    animationIntensity = 1.0f,
    autoSceneSwitch = true,
    sceneChangeIntervalMs = 45_000L,
    lockedScene = null
)
```

### Programmatic Control

```kotlin
// Get current config
val config = CoupleThemeApplicator.getConfig()

// Update config
CoupleThemeApplicator.setConfig(
    config.copy(
        animationIntensity = 0.5f,
        autoSceneSwitch = false,
        lockedScene = SceneType.SUNSET
    )
)

// Change scene manually
CoupleThemeApplicator.setScene(SceneType.BEACH)

// Change interaction manually
CoupleThemeApplicator.setInteraction(CharacterInteraction.HOLDING_HANDS)
```

---

## Message Bubble Styling

Characters get distinct bubble colors:

```kotlin
// Zain (Guy) - Purple
backgroundColor = Color.parseColor("#3700b3")
characterEmoji = "👨"

// Sanu (Girl) - Pink
backgroundColor = Color.parseColor("#c2185b")
characterEmoji = "👩"
```

Access via:

```kotlin
val style = CoupleThemeApplicator.getMessageBubbleStyle("zain")
// Returns: MessageBubbleStyle(
//   backgroundColor = 0xFF3700B3,
//   textColor = 0xFFFFFFFF,
//   characterEmoji = "👨",
//   characterName = "Zain"
// )
```

---

## Performance Optimization

### Animation FPS
- Scenes: 60 FPS (16ms per frame)
- Characters: 60 FPS (16ms per frame)
- Smooth without battery drain

### Lazy Loading
- Scenes render on-demand
- Characters only animate when visible
- Animations stop when activity pauses

### Memory Management
- Handlers properly cleaned up
- Animations stopped in onDestroy
- No memory leaks from long-running tasks

---

## Customization

### Add Custom Scene

```kotlin
// In SceneBackgroundView.onDraw()
private fun drawCustomScene(canvas: Canvas) {
    val w = width.toFloat()
    val h = height.toFloat()
    
    paint.color = Color.parseColor("#your_color")
    // Draw your scene
}

// In onDraw() switch statement
SceneType.CUSTOM -> drawCustomScene(canvas)
```

### Add Custom Interaction

```kotlin
// In CoupleCharacterView.onDraw()
private fun drawCustomInteraction(canvas: Canvas) {
    // Draw your interaction
}

// In onDraw() switch statement
CharacterInteraction.CUSTOM -> drawCustomInteraction(canvas)
```

### Add Custom Trigger

```kotlin
// In DynamicCoupleTheme.onMessageReceived()
val interaction = when {
    content.contains("your_trigger", ignoreCase = true) -> 
        CharacterInteraction.YOUR_INTERACTION
    // ... other triggers ...
}
```

---

## UX Principles

### Visual Hierarchy
- Characters are foreground (behind chat UI)
- Scenes are background
- Chat bubbles overlay cleanly
- No visual clutter

### Emotional Connection
- Subtle animations (not distracting)
- Slow, smooth transitions
- Responsive to message content
- Mood-aware scene changes

### Performance
- Smooth 60 FPS animations
- No jank or stuttering
- Minimal battery impact
- Responsive UI

---

## Testing

### Manual Testing Checklist

- [ ] Characters render correctly
- [ ] Scenes change smoothly
- [ ] Interactions trigger on messages
- [ ] Mood updates change scenes
- [ ] Settings persist across sessions
- [ ] No memory leaks (check with Android Profiler)
- [ ] Animations smooth at 60 FPS
- [ ] Chat UI remains responsive
- [ ] Message bubbles styled correctly
- [ ] Theme deactivates cleanly on exit

### Test Triggers

```
Message: "miss you" → Should trigger HOLDING_HANDS
Message: "i love you" → Should trigger LIGHT_HUG
Message: "good night" → Should trigger CUDDLING
Mood: "LOVE" → Should change to SUNSET scene
Mood: "BUSY" → Should change to CALM scene
```

---

## Troubleshooting

### Characters Not Showing
- Check `enableCharacters` in config
- Verify `CoupleThemeApplicator.activate()` called
- Check logcat for rendering errors

### Scenes Not Changing
- Check `autoSceneSwitch` is enabled
- Verify `lockedScene` is null
- Check mood updates are being received

### Animations Stuttering
- Reduce `animationIntensity` in settings
- Check device performance (Android Profiler)
- Verify no other heavy animations running

### Memory Leaks
- Ensure `CoupleThemeApplicator.deactivate()` called in onDestroy
- Check handlers are properly cleaned up
- Verify no circular references

---

## Files Created

1. **DynamicCoupleTheme.kt** - Core theme engine
2. **CoupleThemeApplicator.kt** - Integration layer
3. **CoupleThemeSettingsActivity.kt** - Settings UI
4. **ChatActivity.kt** (modified) - Integration hooks

---

## Future Enhancements

- [ ] Custom character avatars
- [ ] Voice-triggered interactions
- [ ] Seasonal scene variations
- [ ] Photo-based backgrounds
- [ ] Custom animation sequences
- [ ] Particle effects (hearts, stars, etc.)
- [ ] Scene transitions with effects
- [ ] Character customization UI

---

## Content Guidelines

✅ **Appropriate:**
- Respectful interactions
- Emotionally warm moments
- Non-explicit affection
- Peaceful environments

❌ **Inappropriate:**
- Explicit content
- Violent scenes
- Disrespectful interactions
- Inappropriate animations

---

## Support

For issues or questions:
1. Check troubleshooting section
2. Review logcat for errors
3. Verify all integration steps completed
4. Check Android Profiler for performance issues
