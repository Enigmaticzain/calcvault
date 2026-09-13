# 👫 Dual Companion House Theme - Complete Implementation Guide

> A living dual-character simulation inside your app. Two characters (Zain & Sanu) live together in an interactive house, react to chat, and can be controlled via drag & drop or commands.

---

## 📋 WHAT YOU'RE GETTING

### 7 Core Kotlin Files
1. **HouseModels.kt** - Data structures (rooms, characters, commands, zones)
2. **HouseEnvironment.kt** - Room layout and zone management (4 rooms with interactive zones)
3. **CharacterBehaviorEngine.kt** - Character animation, movement, micro-animations
4. **DragDropInteractionEngine.kt** - Touch control, drag & drop, zone detection
5. **HouseEnvironmentView.kt** - Canvas rendering of characters and environment
6. **ChatTriggerEngine.kt** - Message-to-action mapping (30+ triggers included)
7. **DualCompanionHouseApplicator.kt** - Integration and state management
8. **DualCompanionHouseActivity.kt** - UI and controls

### 1 Layout File
- **activity_dual_companion_house.xml** - Room buttons, mood controls, quick commands

---

## 🎯 KEY FEATURES

### 🏠 House System (4 Rooms)
- **Living Room** - Sofa, TV, floor space
- **Kitchen** - Cooking prep, counter, floor
- **Bedroom** - Bed, sleeping area, floor
- **Activity Area** - Games, play space

### 👥 Dual Characters
- **Zain** (Male) - Purple styling (#673AB7)
- **Sanu** (Female) - Pink styling (#E91E63)
- Micro-animations: blinking, breathing, eye tracking, fidgeting
- 8 Emotional States: Happy, Sad, Tired, Romantic, Playful, Neutral, Focused, Resting
- 14 Character Actions: Walking, Sitting, Cooking, Watching TV, Playing, Sleeping, etc.

### 🎮 Control Systems
1. **Drag & Drop** - Tap and drag characters to move them
2. **Command Buttons** - Cook, Watch TV, Play, Relax
3. **Mood Selection** - 5 quick mood buttons
4. **Room Navigation** - 4 buttons for room switching

### 💬 Chat Integration
- **30+ Trigger Keywords** for automatic reactions
- Emotional state changes based on message content
- Dual-character coordination triggers
- Zone-specific responses

### ⚙️ Animation Features
- Natural walk-to-position movement
- Micro-animations loop during idle states
- Breathing and blinking simulation
- Action-specific animations (cooking, playing, etc.)
- Smooth rotation and position transitions
- Interaction connection lines between characters

---

## 🚀 QUICK START (5 MINUTES)

### Step 1: Add to Manifest
```xml
<!-- AndroidManifest.xml -->
<activity
    android:name="com.calcvault.ui.house.DualCompanionHouseActivity"
    android:label="👫 Companion House"
    android:exported="false" />
```

### Step 2: Create Menu Item
```kotlin
// In MainVaultActivity or SettingsActivity
val items = listOf(
    // ... existing items ...
    VaultItem("👫", "Companion House", "#FF1493") {
        startActivity(Intent(this, DualCompanionHouseActivity::class.java))
    }
)
```

### Step 3: Launch Activity
```kotlin
startActivity(Intent(this, DualCompanionHouseActivity::class.java))
```

Done! The house theme is now accessible.

---

## 🔌 INTEGRATION WITH CHAT

### Option A: Chat Reaction Mode (Standalone)
Characters react to messages in real-time while user is in house view.

```kotlin
// In DualCompanionHouseActivity or wherever messages arrive
networkEngine.onMessageReceived = { msg ->
    DualCompanionHouseApplicator.onMessageReceived(msg.from, msg.content)
}
```

### Option B: Embedded in ChatActivity (Advanced)
Show house as background overlay or side panel in chat.

```kotlin
// In ChatActivity.onCreate()
val houseFrame = FrameLayout(this).apply {
    layoutParams = FrameLayout.LayoutParams(width / 2, -1)
}
addContentView(houseFrame, FrameLayout.LayoutParams(width / 2, -1))

DualCompanionHouseApplicator.activate(this, houseFrame)

// Hook messages
networkEngine.onMessageReceived = { msg ->
    DualCompanionHouseApplicator.onMessageReceived(msg.from, msg.content)
}
```

---

## 📝 API REFERENCE

### Main Applicator
```kotlin
// Activate/deactivate
DualCompanionHouseApplicator.activate(activity, containerView)
DualCompanionHouseApplicator.deactivate()

// Message handling
DualCompanionHouseApplicator.onMessageReceived(sender, content)

// Commands
DualCompanionHouseApplicator.executeCommand(CharacterCommand(...))
DualCompanionHouseApplicator.changeRoom(HouseRoom.LIVING)
DualCompanionHouseApplicator.setCharacterMood(CharacterIdentity.ZAIN, EmotionalState.HAPPY)

// State
DualCompanionHouseApplicator.getCharacterState(): DualCharacterState
DualCompanionHouseApplicator.getConfig(): HouseThemeConfig
DualCompanionHouseApplicator.setConfig(newConfig)
```

### Character Command Execution
```kotlin
val command = CharacterCommand(
    id = UUID.randomUUID().toString(),
    actor = CharacterIdentity.ZAIN,
    actionType = ActionType.GO_TO_ZONE,
    targetZone = zone,
    targetRoom = HouseRoom.KITCHEN
)
DualCompanionHouseApplicator.executeCommand(command)
```

### Custom Chat Triggers
```kotlin
DualCompanionHouseApplicator.getChatTriggerEngine().addCustomTrigger(
    ChatTriggerResponse(
        keywords = listOf("custom", "trigger"),
        action = CharacterAction.TALKING,
        emotionalState = EmotionalState.HAPPY,
        zone = ZoneType.SOFA,
        affectsPartner = true
    )
)
```

---

## 🎨 CUSTOMIZATION

### Change Character Colors
Edit `HouseEnvironmentView.drawCharacter()`:
```kotlin
val bodyColor = if (state.identity == CharacterIdentity.ZAIN) {
    Color.parseColor("#YOUR_HEX_COLOR") // Override
} else {
    Color.parseColor("#YOUR_HEX_COLOR")
}
```

### Modify Rooms
Edit `HouseEnvironment.initializeRooms()` to add/change zones.

### Adjust Animation Speed
```kotlin
val config = DualCompanionHouseApplicator.getConfig()
DualCompanionHouseApplicator.setConfig(
    config.copy(animationIntensity = 0.5f) // Slower
)
```

### Enable Debug Mode
```kotlin
val config = DualCompanionHouseApplicator.getConfig()
DualCompanionHouseApplicator.setConfig(
    config.copy(debugMode = true) // Show zones/debug info
)
```

---

## 🎬 CHARACTER ACTIONS & STATES

### Character Actions
```
IDLE, WALKING, SITTING, COOKING, WATCHING_TV, SLEEPING, 
PLAYING, TALKING, LISTENING, HELPING, CUDDLING, HOLDING_HANDS, HEAD_TILT
```

### Emotional States
```
HAPPY, SAD, TIRED, ROMANTIC, PLAYFUL, NEUTRAL, FOCUSED, RESTING
```

### Micro-Animations
- BLINK (eyes)
- BREATHING (chest)
- EYE_TRACK (looking around)
- HEAD_TURN (head rotation)
- SHIFT_WEIGHT (stance change)
- FIDGET (small movements)
- NOD (head nod)
- SMILE (expression)
- SWAY (gentle swaying)
- REACH (arm extension)
- WAVE (waving)
- POINT (pointing)

---

## 🧠 HOW DRAG & DROP WORKS

1. **User taps character** → System detects touch within 60px radius
2. **User drags** → Drag preview shows, nearby zones highlight
3. **User releases** → System calculates drop zone
4. **Snap to zone** → Character moves to nearest zone (within 80px)
5. **Auto-action** → If zone has action, character performs it

---

## 💬 CHAT TRIGGERS (DEFAULT)

### Love/Romance
- Keywords: `love`, `miss you`, `❤️`, `💕`
- Action: HOLDING_HANDS
- Mood: ROMANTIC
- Affects both characters

### Cooking
- Keywords: `cook`, `food`, `meal`, `breakfast`
- Action: COOKING
- Zone: KITCHEN
- Affects both characters

### Sleep
- Keywords: `good night`, `sweet dreams`
- Action: SLEEPING
- Zone: BED
- Mood: TIRED

### Play/Fun
- Keywords: `play`, `game`, `fun`, `laugh`
- Action: PLAYING
- Zone: GAME_AREA
- Mood: PLAYFUL

### Emotional
- Keywords: `sad`, `upset`, `cry`
- Action: LISTENING
- Mood: SAD

### And 20+ more!

---

## 🔐 PERFORMANCE TIPS

1. **Disable animations when not needed**
```kotlin
DualCompanionHouseApplicator.setConfig(
    config.copy(enableCharacters = false)
)
```

2. **Reduce animation intensity**
```kotlin
config.copy(animationIntensity = 0.5f)
```

3. **Limit micro-animations**
Edit `CharacterBehaviorEngine.getMicroAnimations()` to return fewer animations.

4. **Use Canvas rendering** (already done)
The view uses Canvas drawing, which is optimized for this use case.

---

## 🐛 TROUBLESHOOTING

### Characters not moving
- Check `HouseEnvironment` bounds match screen size
- Verify room is set: `setCurrentRoom(HouseRoom.LIVING)`
- Check drag threshold isn't too high (currently 10px)

### Touch not working
- Verify `HouseEnvironmentView.onTouchEvent()` is being called
- Check touch coordinates are within view bounds
- Test with `dragDropEngine.isDragging()`

### No chat reactions
- Verify message sender is "zain" or "sanu" (case-insensitive)
- Check keyword matching in `ChatTriggerEngine`
- Test with keywords from trigger rules

### Memory issues
- Set `animationIntensity = 0` if not needed
- Call `deactivate()` before activity destruction
- Check for handler leaks in `HouseEnvironmentView`

---

## 📚 FILE STRUCTURE

```
app/src/main/java/com/calcvault/
├── emotional/
│   └── house/
│       ├── HouseModels.kt
│       ├── HouseEnvironment.kt
│       ├── CharacterBehaviorEngine.kt
│       ├── DragDropInteractionEngine.kt
│       ├── HouseEnvironmentView.kt
│       ├── ChatTriggerEngine.kt
│       └── DualCompanionHouseApplicator.kt
└── ui/
    └── house/
        └── DualCompanionHouseActivity.kt

app/src/main/res/
└── layout/
    └── activity_dual_companion_house.xml
```

---

## 🎯 NEXT STEPS

1. **Add to Manifest** - Register activity
2. **Create Menu Item** - Add to navigation
3. **Test Drag & Drop** - Verify touch works
4. **Test Chat Integration** - Send messages with keywords
5. **Customize Colors** - Match your app theme
6. **Add Settings** - Create preferences activity
7. **Deploy** - Build APK and test on device

---

## 💡 ADVANCED FEATURES

### Custom Room Creation
```kotlin
val customZone = InteractionZone(
    id = "balcony_zone",
    type = ZoneType.BALCONY,
    bounds = RectF(0f, 0f, 100f, 100f),
    roomId = HouseRoom.LIVING,
    animationAction = "stand",
    dualAction = true,
    primaryPosition = PointF(50f, 50f)
)
```

### Sequential Commands
```kotlin
val sequence = CommandSequence(
    commands = listOf(
        CharacterCommand(...), // Move to kitchen
        CharacterCommand(...)  // Start cooking
    ),
    parallel = false, // Execute one after another
    totalDuration = 3000L
)
```

### Emotion-Based Reactions
```kotlin
when (characterState.emotionalState) {
    EmotionalState.ROMANTIC -> {
        // Draw more expressive eyes, closer stance
    }
    EmotionalState.SAD -> {
        // Downturned mouth, slowed breathing
    }
    else -> {}
}
```

---

## 📞 SUPPORT

This system is fully integrated with CalcVault's emotional animation engine.
All state is persisted via `StorageManager` for consistency.

**Key Integration Points:**
- `emotional/ThemeEngine.kt` - Theme switching
- `EmotionalAnimationEngine.kt` - Base animations
- `messaging/AppendOnlyMessageDB.kt` - Message triggers

---

## 📄 LICENSE

Part of CalcVault - Adult Couple Dynamic Communication Platform

---

**Version:** 1.0  
**Last Updated:** April 2026  
**Status:** Production Ready ✅
