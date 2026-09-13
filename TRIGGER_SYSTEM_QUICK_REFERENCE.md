# Trigger System - Quick Reference

## ⚡ Quick Start

### 1. Initialize
```kotlin
val engine = EmotionalAnimationEngine(context, rootView)
engine.initializeBuiltInTriggers()
```

### 2. Test a Trigger
```kotlin
engine.checkMessageTriggers("I love you ❤️")
```

### 3. Add Custom Trigger
```kotlin
engine.addCustomEmojiTrigger("hello", emoji = "👋")
```

### 4. Load from JSON
```kotlin
val json = """[{"keywords":["hi"],"type":"emoji_animation","emoji":"👋"}]"""
engine.loadCustomTriggers(json)
```

---

## 📝 JSON Template

### Basic Emoji Trigger
```json
{
  "keywords": ["hello", "hi"],
  "type": "emoji_animation",
  "emoji": "👋",
  "intensity": 8,
  "direction": "UP",
  "speed": "MEDIUM"
}
```

### Multiple Triggers
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
    "keywords": ["awesome", "wow"],
    "type": "emoji_animation",
    "emoji": "😲",
    "intensity": 15,
    "direction": "DOWN",
    "speed": "FAST"
  }
]
```

### Minimal JSON
```json
{
  "keywords": "hello",
  "type": "emoji_animation",
  "emoji": "👋"
}
```
(Uses defaults: intensity=15, direction=UP, speed=MEDIUM)

---

## 🎨 Emoji Suggestions by Type

### Greetings
```json
{
  "keywords": ["hello", "hi", "hey"],
  "emoji": "👋"
}
```

### Celebration
```json
{
  "keywords": ["awesome", "amazing", "great"],
  "emoji": "🎉"
}
```

### Love/Romance
```json
{
  "keywords": ["love", "baby", "dear"],
  "emoji": "❤️"
}
```

### Reactions
```json
{
  "keywords": ["wow", "cool"],
  "emoji": "😲"
}
```

### Excitement
```json
{
  "keywords": ["excited", "woo", "yeah"],
  "emoji": "🎊"
}
```

### Fun
```json
{
  "keywords": ["joke", "funny", "lol"],
  "emoji": "😂"
}
```

---

## 🎬 Animation Types

### EMOJI_ANIMATION (Recommended)
Falling or rising emojis with physics
```json
{
  "type": "emoji_animation",
  "emoji": "❤️",
  "intensity": 15,
  "direction": "UP",
  "speed": "MEDIUM"
}
```

### PARTICLE_BURST
Particle explosion effect
```json
{
  "type": "particle",
  "intensity": 20
}
```

### GLOW
Flash glow effect
```json
{
  "type": "glow",
  "intensity": 10
}
```

### RAIN
Falling water drops
```json
{
  "type": "rain",
  "intensity": 15
}
```

### STARS
Falling stars
```json
{
  "type": "stars",
  "intensity": 12
}
```

---

## ⚙️ Parameters

### Direction
- **UP** - Emojis float upward (default)
- **DOWN** - Emojis fall downward
- **RANDOM** - Mix of up and down

### Speed
- **SLOW** - 4000ms duration
- **MEDIUM** - 2000ms duration (default)
- **FAST** - 1000ms duration

### Intensity
- **1-20** - Subtle effects
- **20-50** - Moderate effects
- **50-100** - Intense effects (use sparingly)

---

## 🔧 Common Recipes

### Welcome Greeting
```json
{
  "keywords": ["welcome", "glad you're here"],
  "type": "emoji_animation",
  "emoji": "🎉",
  "intensity": 10,
  "speed": "MEDIUM"
}
```

### Soft Welcome
```json
{
  "keywords": ["hello", "hi"],
  "type": "emoji_animation",
  "emoji": "👋",
  "intensity": 5,
  "speed": "SLOW"
}
```

### Celebration
```json
{
  "keywords": ["success", "done", "finished"],
  "type": "emoji_animation",
  "emoji": "🎊",
  "intensity": 20,
  "direction": "RANDOM",
  "speed": "FAST"
}
```

### Romantic
```json
{
  "keywords": ["love you", "miss you"],
  "type": "emoji_animation",
  "emoji": "💕",
  "intensity": 15,
  "direction": "UP",
  "speed": "SLOW"
}
```

### Surprise
```json
{
  "keywords": ["surprise", "whoa"],
  "type": "emoji_animation",
  "emoji": "😲",
  "intensity": 12,
  "direction": "DOWN",
  "speed": "FAST"
}
```

### Calm/Peaceful
```json
{
  "keywords": ["relax", "chill", "peace"],
  "type": "emoji_animation",
  "emoji": "🧘",
  "intensity": 3,
  "direction": "UP",
  "speed": "SLOW"
}
```

### Enthusiasm
```json
{
  "keywords": ["excited", "pumped"],
  "type": "emoji_animation",
  "emoji": "🔥",
  "intensity": 18,
  "direction": "RANDOM",
  "speed": "FAST"
}
```

---

## 🐛 Debug

### Enable Logging
```kotlin
engine.enableTriggerDebug(true)
// Now see detailed logs in Logcat
```

### Open Debug Activity
```kotlin
startActivity(Intent(context, TriggerDebugActivity::class.java))
```

### View All Triggers
```kotlin
val triggers = engine.getAllTriggers()
triggers.forEach { trigger ->
    println("${trigger.keywords} → ${trigger.emoji}")
}
```

### View Statistics
```kotlin
val stats = engine.getTriggerStats()
println("Total: ${stats.totalTriggers}")
println("Enabled: ${stats.enabledTriggers}")
println("By type: ${stats.byType}")
```

---

## ✅ Validation Checklist

**Before using JSON triggers:**
- [ ] JSON is valid (use jsonlint.com)
- [ ] All keywords are lowercase
- [ ] Emoji field is present for emoji_animation type
- [ ] Keywords field is array or string
- [ ] Valid trigger type specified
- [ ] Parameters are in valid ranges

**Common Mistakes:**
- ❌ Keywords not lowercase: `"Hello"` → `"hello"`
- ❌ Keywords field missing: Add `"keywords": [...]`
- ❌ No emoji for EMOJI_ANIMATION: Add `"emoji": "❤️"`
- ❌ Invalid JSON syntax: Quotes, commas, brackets
- ❌ Wrong type name: `"rain_effect"` → `"rain"`

---

## 🚀 Advanced Usage

### Load from Shared Preferences
```kotlin
val json = PreferenceManager.getDefaultSharedPreferences(context)
    .getString("custom_triggers", "[]")
engine.loadCustomTriggers(json)
```

### Save Custom Triggers
```kotlin
val triggers = engine.getAllTriggers()
val json = JSONArray().apply {
    triggers.forEach { trigger ->
        put(JSONObject().apply {
            put("keywords", JSONArray(trigger.keywords))
            put("type", trigger.triggerType.name.lowercase())
            put("emoji", trigger.emoji)
            put("intensity", trigger.intensity)
            put("direction", trigger.direction)
            put("speed", trigger.speed)
        })
    }
}.toString()
PreferenceManager.getDefaultSharedPreferences(context)
    .edit()
    .putString("custom_triggers", json)
    .apply()
```

### Toggle Trigger
```kotlin
engine.setTriggerEnabled("hello", enabled = false)
```

### Remove Trigger
```kotlin
engine.removeTrigger("hello")
```

### Clear All
```kotlin
engine.clearCustomTriggers()  // Reloads built-in triggers
```

---

## 📊 Performance Tips

- Use **MEDIUM** or **SLOW** speed for battery conservation
- Keep intensity **< 20** for smooth animations
- Limit total triggers to < 50
- Use **MEDIUM** AnimationMode for balance

---

## 🎯 Example: Complete Implementation

```kotlin
class MyActivity : AppCompatActivity() {
    private lateinit var engine: EmotionalAnimationEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup
        val animView = findViewById<AmbientAnimationView>(R.id.ambient)
        engine = EmotionalAnimationEngine(this, animView)
        engine.initializeBuiltInTriggers()
        
        // Load custom
        val customJson = """[
            {
              "keywords": ["hello", "hi"],
              "type": "emoji_animation",
              "emoji": "👋",
              "intensity": 8
            }
        ]"""
        engine.loadCustomTriggers(customJson)
        
        // Debug (dev only)
        if (BuildConfig.DEBUG) {
            engine.enableTriggerDebug(true)
        }
    }
    
    private fun onMessageReceived(text: String) {
        // Display message...
        
        // Trigger animation
        engine.checkMessageTriggers(text)
    }
}
```

---

## 📚 Related Files

- **Main Implementation**: `EmotionalAnimationEngine.kt`
- **Trigger Core**: `TriggerSystem.kt`
- **Debug Tool**: `TriggerDebugActivity.kt`
- **Full Guide**: `TRIGGER_SYSTEM_GUIDE.md`
- **Documentation**: `SOCKET_CALL_ENGINE_DOCUMENTATION.md`
