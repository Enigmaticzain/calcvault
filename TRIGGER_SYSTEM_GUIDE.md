# Trigger System - Complete Guide

## Overview

The improved trigger system in CalcVault enables real-time UI animations based on message content. The system has been refactored from the original implementation to fix critical issues with custom triggers and provide advanced functionality.

## Key Improvements

### ✅ What Was Fixed

| Issue | Solution |
|-------|----------|
| **Silent Exception Handling** | Added proper error logging and validation in `TriggerSystem.loadFromJson()` |
| **Keyword Matching Failures** | All keywords are now normalized to lowercase before storage and matching |
| **Missing Feedback** | Complete logging system with `LoadResult` object providing detailed error reports |
| **Limited Customization** | Support for multiple emoji types, intensity levels, direction, speed, and colors |
| **No Debug Capability** | New `TriggerDebugActivity` for testing and debugging triggers |
| **JSON Parsing Errors** | Robust parsing with detailed error messages for invalid JSON |

### 🎨 Supported Trigger Types

```
EMOJI_ANIMATION  - Floating emoji with physics
PARTICLE_BURST   - Particle effects
GLOW            - Flash/glow effects
RAIN            - Falling water drops
STARS           - Starfall animation
```

## Architecture

### TriggerSystem Class

The core trigger management system with features:

- **Load from JSON**: Parse trigger configurations from JSON strings
- **Add Programmatically**: Create triggers via code
- **Find Matches**: Locate triggers matching message content
- **Enable/Disable**: Toggle triggers on/off
- **Statistics**: Get trigger counts and types
- **Debug Mode**: Enable logging for troubleshooting

### TriggerConfig Data Class

```kotlin
data class TriggerConfig(
    val keywords: List<String>,           // Trigger keywords
    val triggerType: TriggerType,         // Animation type
    val emoji: String? = null,            // Emoji for EMOJI_ANIMATION
    val intensity: Int = 15,              // 1-100 particle count
    val direction: String = "UP",         // UP, DOWN, RANDOM
    val speed: String = "MEDIUM",         // SLOW, MEDIUM, FAST
    val color: Int? = null,               // Custom color (optional)
    var isEnabled: Boolean = true         // Enable/disable toggle
)
```

## Usage Guide

### 1. Basic Trigger Tests

```kotlin
val engine = EmotionalAnimationEngine(context, rootView)

// Initialize built-in triggers
engine.initializeBuiltInTriggers()

// Test with a message
engine.checkMessageTriggers("I love you ❤️")  // Triggers hearts animation
engine.checkMessageTriggers("Goodnight 🌙")  // Triggers starfall
```

### 2. Add Custom Triggers Programmatically

#### Simple Approach (Legacy)
```kotlin
engine.addCustomTrigger(
    keyword = "hello",
    animationType = "hearts"  // "hearts", "stars", "rain", "glow"
)
```

#### Advanced Approach (Recommended)
```kotlin
engine.addCustomEmojiTrigger(
    keyword = "amazing",
    emoji = "😲",
    intensity = 20,           // Number of emojis (1-100)
    direction = "DOWN",       // UP, DOWN, RANDOM
    speed = "FAST"            // SLOW, MEDIUM, FAST
)
```

### 3. Load Triggers from JSON

#### JSON Format
```json
[
  {
    "keywords": ["hello", "hi", "hey"],
    "type": "emoji_animation",
    "emoji": "👋",
    "intensity": 8,
    "direction": "UP",
    "speed": "MEDIUM"
  },
  {
    "keywords": ["wow", "amazing"],
    "type": "emoji_animation",
    "emoji": "😲",
    "intensity": 15,
    "direction": "DOWN",
    "speed": "FAST"
  },
  {
    "keywords": "congratulations",
    "type": "emoji_animation",
    "emoji": "🎊",
    "intensity": 20,
    "direction": "RANDOM",
    "speed": "MEDIUM"
  }
]
```

#### Loading the JSON
```kotlin
val json = """[...]"""  // Your JSON string

val success = engine.loadCustomTriggers(json)
if (success) {
    Log.d("Triggers", "All triggers loaded!")
} else {
    Log.w("Triggers", "Some triggers failed to load")
}
```

### 4. Manage Triggers

```kotlin
// Get all active triggers
val triggers = engine.getAllTriggers()

// Get statistics
val stats = engine.getTriggerStats()
// TriggerStats: totalTriggers, enabledTriggers, byType, totalKeywords

// Enable/disable a trigger
engine.setTriggerEnabled("hello", enabled = true)

// Remove a trigger
engine.removeTrigger("hello")

// Clear all triggers and reset to built-in
engine.clearCustomTriggers()
```

### 5. Enable Debug Logging

```kotlin
// Enable detailed logging in trigger system
engine.enableTriggerDebug(true)

// Now you'll see logs like:
// D/TriggerSystem: ✓ Loaded trigger: hello
// D/TriggerSystem: ✓ Matched trigger: love you
// D/TriggerSystem: Load complete: 5 loaded, 1 skipped, 0 errors
```

## Error Handling

### LoadResult Object

When loading triggers from JSON, you get detailed error information:

```kotlin
val result = engine.loadCustomTriggers(json)

println("Loaded: ${result.loaded}")      // Successfully loaded
println("Skipped: ${result.skipped}")    // Failed to load
println("Errors: ${result.errors}")      // Error messages

if (!result.isSuccess()) {
    // Some triggers failed to load
    result.errors.forEach { error ->
        Log.w("Triggers", error)
    }
}

println(result.getSummary())  // "Loaded: 3, Skipped: 1, Errors: 1"
```

### Common Error Messages

```
⚠️ Missing 'keywords' or 'kw' field
   → JSON object is missing keywords

⚠️ Invalid JSON format: ...
   → JSON string is not valid JSON array

⚠️ Emoji animation requires 'emoji' field
   → emoji_animation type must have 'emoji' property

⚠️ Error parsing trigger at index 2: ...
   → Specific entry in JSON array has issues

⚠️ Unknown type: 'xyz'
   → Trigger type is not recognized
```

## Built-in Triggers

The system comes with 6 pre-configured triggers:

| Keywords | Animation | Details |
|----------|-----------|---------|
| "i love you", "ily", "❤️", "💕", "💖" | Floating Hearts | 20 emojis upward, medium speed |
| "miss you", "missing you" | Pink Hearts | 10 emojis upward, slow |
| "good night", "gn 💤", "🌙" | Starfall | 15 stars downward |
| "good morning", "gm ☀️", "🌅" | Sunrise Glow | Flash effect |
| "😔", "sad", "crying", "😢" | Rain Effect | 20 water drops |
| "😘", "kiss", "mwah", "💋" | Kiss Animation | 8 emojis downward |

## Debug Activity

### TriggerDebugActivity

A dedicated debugging interface for testing and validating triggers.

**Features:**
- Test triggers with custom messages
- Load triggers from JSON in real-time
- View all active triggers with details
- See trigger statistics
- Enable/disable individual triggers
- Live debug log output

**Access:**
```kotlin
// Add to main menu or Settings
val intent = Intent(context, TriggerDebugActivity::class.java)
startActivity(intent)
```

## Integration with ChatActivity

The `EmotionalAnimationEngine.checkMessageTriggers()` is called automatically when messages are received:

```kotlin
// In ChatActivity
override fun onMessageReceived(message: String) {
    // ... Message display code ...
    
    // Trigger animations based on message content
    emotionalAnimationEngine.checkMessageTriggers(message)
}
```

## Performance Considerations

- **Trigger Matching**: O(k) where k = number of triggers (typically < 50)
- **Keyword Matching**: Case-insensitive substring search (efficient for chat messages)
- **Animation Spawn**: Staggered with 100ms delays to prevent UI jank
- **Memory**: Each trigger ~200 bytes; 100 triggers ≈ 20KB

## Example: Complete Setup

```kotlin
class ChatActivity : AppCompatActivity() {
    private lateinit var emotionalEngine: EmotionalAnimationEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup engine
        val animationView = findViewById<AmbientAnimationView>(R.id.ambientAnimationView)
        emotionalEngine = EmotionalAnimationEngine(this, animationView)
        
        // Initialize built-in triggers
        emotionalEngine.initializeBuiltInTriggers()
        
        // Load custom triggers
        val customTriggersJson = loadCustomTriggersFromSharedPrefs()
        if (customTriggersJson.isNotEmpty()) {
            emotionalEngine.loadCustomTriggers(customTriggersJson)
        }
        
        // Enable debug mode (development only)
        if (BuildConfig.DEBUG) {
            emotionalEngine.enableTriggerDebug(true)
        }
    }
    
    private fun onMessageReceived(message: String) {
        // Display message...
        
        // Trigger animations
        emotionalEngine.checkMessageTriggers(message)
    }
    
    private fun loadCustomTriggersFromSharedPrefs(): String {
        // Load from your preference/database
        return SharedPreferences.getTriggersJson()
    }
}
```

## Testing Checklist

- [ ] Built-in triggers activate when keywords detected
- [ ] Custom triggers load without errors
- [ ] JSON parsing reports errors properly
- [ ] Keyword matching is case-insensitive
- [ ] Multiple keywords work for single trigger
- [ ] Emoji display correctly in animations
- [ ] Intensity/direction/speed parameters work
- [ ] Disabled triggers don't execute
- [ ] Remove trigger prevents execution
- [ ] Statistics report correct counts

## Migration from Old System

If you have code using `TriggerRule` and `EmojiAnimationParams`:

**Old Code:**
```kotlin
addCustomEmojiTrigger("hello", EmojiAnimationParams("👋", 10, "UP", "MEDIUM"))
```

**New Code:**
```kotlin
addCustomEmojiTrigger("hello", "👋", intensity = 10, direction = "UP", speed = "MEDIUM")
```

## Troubleshooting

### Triggers Not Firing
1. Enable debug mode: `engine.enableTriggerDebug(true)`
2. Check logs for "Matched trigger" messages
3. Verify keywords are in lowercase (they're normalized automatically)
4. Test with exact keyword: `engine.checkMessageTriggers("hello")`

### JSON Loading Fails
1. Validate JSON with online JSON validator
2. Check that all required fields are present
3. Review error messages in `LoadResult.errors`
4. Use the debug activity to test JSON

### Animation Not Visible
1. Check that `animationMode` is not `LOW`
2. Verify rootView is properly sized
3. Check `AmbientAnimationView` is added to layout
4. Look for exceptions in logcat

### Performance Issues
1. Reduce `intensity` values in triggers
2. Limit number of simultaneous animations
3. Use `AnimationMode.MEDIUM` or `LOW` for battery saving
4. Profile with Android Profiler

## API Reference

### EmotionalAnimationEngine

```kotlin
// Initialization
fun initializeBuiltInTriggers()
fun enableTriggerDebug(enabled: Boolean)

// Message Processing
fun checkMessageTriggers(messageText: String)

// Add Triggers
fun addCustomTrigger(keyword: String, animationType: String): Boolean
fun addCustomEmojiTrigger(
    keyword: String,
    emoji: String,
    intensity: Int = 15,
    direction: String = "UP",
    speed: String = "MEDIUM"
): Boolean

// Load Triggers
fun loadCustomTriggers(json: String): Boolean

// Query Triggers
fun getAllTriggers(): List<TriggerConfig>
fun getTriggerStats(): TriggerStats

// Manage Triggers
fun setTriggerEnabled(keyword: String, enabled: Boolean): Boolean
fun removeTrigger(keyword: String): Boolean
fun clearCustomTriggers()
```

### TriggerSystem

```kotlin
companion object {
    fun enableDebug(enabled: Boolean)
}

// Public Methods
fun loadFromJson(json: String): LoadResult
fun addTrigger(config: TriggerConfig): Boolean
fun findMatchingTrigger(messageText: String): TriggerConfig?
fun getAllTriggers(): List<TriggerConfig>
fun setTriggerEnabled(keyword: String, enabled: Boolean): Boolean
fun removeTrigger(keyword: String): Boolean
fun clear()
fun getStats(): TriggerStats
```

## Future Enhancements

- [ ] Conditional triggers (emoji if time of day is...)
- [ ] Weighted triggers (probability-based)
- [ ] Animation combinations (chain multiple effects)
- [ ] Custom callback support
- [ ] Trigger usage analytics
- [ ] Export/import trigger configs
- [ ] Gesture-based trigger creation UI
