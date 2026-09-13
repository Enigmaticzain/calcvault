# Couple Theme - Quick Integration Reference

## 1. ChatActivity Integration

### Add Import
```kotlin
import com.calcvault.emotional.themes.CoupleThemeApplicator
```

### In onCreate() - After setContentView()
```kotlin
// Activate couple theme
CoupleThemeApplicator.activate(this, binding.chatRoot)
```

### In onDestroy()
```kotlin
CoupleThemeApplicator.deactivate()
```

### In networkEngine.onMessageReceived callback
```kotlin
networkEngine.onMessageReceived = { msg ->
    lifecycleScope.launch {
        if (msg.type == AppendOnlyMessageDB.MSG_THEME_SYNC) {
            handleThemeSync(msg.content)
        } else {
            messageDB.markDelivered(msg.id)
            withContext(Dispatchers.Main) {
                adapter.addMessage(msg)
                scrollBottom()
                animEngine.checkMessageTriggers(msg.content)
                
                // ADD THIS LINE:
                CoupleThemeApplicator.onMessageReceived(msg.from, msg.content)
                
                if (msg.type == AppendOnlyMessageDB.MSG_MOOD) updatePartnerMood()
            }
        }
    }
}
```

### In updatePartnerMood()
```kotlin
private fun updatePartnerMood() {
    val mood = messageDB.getLatestMood(partnerUserId)
    val name = SessionManager.partnerNickname.ifBlank { partnerUserId }
    if (mood != null) {
        binding.tvPartnerMood.text = "$name: $mood"
        binding.tvPartnerMood.visibility = View.VISIBLE
        if (mood.contains("❤️")) animEngine.triggerFloatingHearts()
        else if (mood.contains("😔")) animEngine.triggerRainEffect()
        
        // ADD THIS LINE:
        CoupleThemeApplicator.onMoodUpdate(mood)
    } else {
        binding.tvPartnerMood.visibility = View.GONE
    }
}
```

---

## 2. Add Settings Menu Item

### In MainVaultActivity.buildModernUI()
```kotlin
val items = listOf(
    VaultItem("💬", "Chat", "#0A84FF") { startActivity(Intent(this, ChatActivity::class.java)) },
    VaultItem("📞", "Call", "#30D158") { showCallChooser() },
    VaultItem("🎧", "Listen", "#00B8A9") { startActivity(Intent(this, ListenTogetherActivity::class.java)) },
    VaultItem("🎬", "Media", "#FF9500") { startActivity(Intent(this, com.calcvault.ui.media.MediaActivity::class.java)) },
    VaultItem("🖼️", "Files", "#AF52DE") { startActivity(Intent(this, VaultFilesActivity::class.java)) },
    VaultItem("❤️", "Mood", "#FF375F") { startActivity(Intent(this, MoodActivity::class.java)) },
    
    // ADD THIS LINE:
    VaultItem("👫", "Couple Theme", "#FF1493") { startActivity(Intent(this, CoupleThemeSettingsActivity::class.java)) },
    
    VaultItem("⚙️", "Settings", "#8E8E93") { startActivity(Intent(this, SettingsActivity::class.java)) }
)
```

---

## 3. AndroidManifest.xml

### Add Activity Declaration
```xml
<activity
    android:name="com.calcvault.ui.settings.CoupleThemeSettingsActivity"
    android:exported="false"
    android:screenOrientation="portrait" />
```

---

## 4. Programmatic Control Examples

### Change Scene
```kotlin
CoupleThemeApplicator.setScene(SceneType.SUNSET)
```

### Change Interaction
```kotlin
CoupleThemeApplicator.setInteraction(CharacterInteraction.HOLDING_HANDS)
```

### Update Configuration
```kotlin
val currentConfig = CoupleThemeApplicator.getConfig() ?: CoupleThemeConfig()
CoupleThemeApplicator.setConfig(
    currentConfig.copy(
        animationIntensity = 0.7f,
        autoSceneSwitch = false,
        lockedScene = SceneType.BEACH
    )
)
```

### Get Message Bubble Style
```kotlin
val style = CoupleThemeApplicator.getMessageBubbleStyle("zain")
// Use style.backgroundColor, style.textColor, style.characterEmoji, style.characterName
```

---

## 5. Message Trigger Examples

These happen automatically when messages are received:

```
User sends: "I miss you"
→ Interaction changes to HOLDING_HANDS

User sends: "I love you"
→ Interaction changes to LIGHT_HUG

User sends: "Good night"
→ Interaction changes to CUDDLING

User sends: "Good morning"
→ Interaction changes to WATCHING_VIEW
```

---

## 6. Mood Trigger Examples

These happen automatically when mood is updated:

```
Mood: "LOVE"
→ Scene changes to SUNSET

Mood: "BUSY"
→ Scene changes to CALM

Mood: "HAPPY"
→ Scene changes to BEACH

Mood: "RELAXED"
→ Scene changes to WATERFALL
```

---

## 7. Configuration Persistence

All settings are automatically saved to storage:
- Scene preference
- Interaction state
- Animation intensity
- Auto-switch toggle
- Locked scene preference

Loaded automatically on app restart.

---

## 8. Testing Checklist

```
[ ] Characters render on chat screen
[ ] Scenes change every 45 seconds (if auto-switch enabled)
[ ] Message "miss you" triggers HOLDING_HANDS
[ ] Message "love" triggers LIGHT_HUG
[ ] Message "good night" triggers CUDDLING
[ ] Mood "LOVE" changes scene to SUNSET
[ ] Settings persist after app restart
[ ] No memory leaks (check Android Profiler)
[ ] Animations smooth at 60 FPS
[ ] Chat UI remains responsive
[ ] Theme deactivates cleanly on exit
```

---

## 9. Troubleshooting

### Characters not visible
```kotlin
// Check if enabled
val config = CoupleThemeApplicator.getConfig()
if (config?.enableCharacters == false) {
    // Enable in settings
}
```

### Scenes not changing
```kotlin
// Check if auto-switch enabled
val config = CoupleThemeApplicator.getConfig()
if (config?.autoSceneSwitch == false) {
    // Enable in settings or manually call:
    CoupleThemeApplicator.setScene(SceneType.SUNSET)
}
```

### Animations stuttering
```kotlin
// Reduce intensity
val config = CoupleThemeApplicator.getConfig()
CoupleThemeApplicator.setConfig(
    config.copy(animationIntensity = 0.5f)
)
```

---

## 10. File Locations

```
app/src/main/java/com/calcvault/emotional/themes/
├── DynamicCoupleTheme.kt          (Core engine)
├── CoupleThemeApplicator.kt       (Integration layer)
└── CoupleThemeSettingsActivity.kt (Settings UI)
```

---

## 11. Key Classes Reference

### DynamicCoupleTheme
```kotlin
class DynamicCoupleTheme(context: Context) {
    fun setConfig(config: CoupleThemeConfig)
    fun getConfig(): CoupleThemeConfig
    fun setScene(scene: SceneType)
    fun getScene(): SceneType
    fun setInteraction(interaction: CharacterInteraction)
    fun getInteraction(): CharacterInteraction
    fun onMessageReceived(from: String, content: String)
    fun onMoodUpdate(mood: String)
    fun cleanup()
}
```

### CoupleThemeApplicator
```kotlin
object CoupleThemeApplicator {
    fun activate(activity: Activity, rootContainer: ViewGroup)
    fun deactivate()
    fun onMessageReceived(from: String, content: String)
    fun onMoodUpdate(mood: String)
    fun getMessageBubbleStyle(from: String): MessageBubbleStyle
    fun getConfig(): CoupleThemeConfig?
    fun setConfig(config: CoupleThemeConfig)
    fun setScene(scene: SceneType)
    fun setInteraction(interaction: CharacterInteraction)
    fun isActive(): Boolean
}
```

### SceneBackgroundView
```kotlin
class SceneBackgroundView(context: Context) : View(context) {
    fun setScene(newScene: SceneType)
    fun startAnimation()
    fun stopAnimation()
}
```

### CoupleCharacterView
```kotlin
class CoupleCharacterView(context: Context) : View(context) {
    fun setInteraction(newInteraction: CharacterInteraction)
    fun startAnimation()
    fun stopAnimation()
}
```

---

## 12. Enums Reference

### SceneType
```kotlin
WATERFALL, SUNSET, BEACH, FOREST, CAMPING, CALM
```

### CharacterInteraction
```kotlin
SITTING_TOGETHER, TALKING_SOFTLY, HOLDING_HANDS, 
LEANING_HEAD, LIGHT_HUG, CUDDLING, WATCHING_VIEW
```

---

## 13. Data Classes

### CoupleThemeConfig
```kotlin
data class CoupleThemeConfig(
    val enableCharacters: Boolean = true,
    val enableInteractions: Boolean = true,
    val animationIntensity: Float = 1.0f,
    val autoSceneSwitch: Boolean = true,
    val sceneChangeIntervalMs: Long = 45_000L,
    val lockedScene: SceneType? = null
)
```

### MessageBubbleStyle
```kotlin
data class MessageBubbleStyle(
    val backgroundColor: Int,
    val textColor: Int,
    val characterEmoji: String,
    val characterName: String
)
```

---

## 14. Complete Integration Example

```kotlin
// ChatActivity.kt
class ChatActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setup ...
        
        // Activate couple theme
        CoupleThemeApplicator.activate(this, binding.chatRoot)
        
        // Hook network events
        networkEngine.onMessageReceived = { msg ->
            lifecycleScope.launch {
                messageDB.markDelivered(msg.id)
                withContext(Dispatchers.Main) {
                    adapter.addMessage(msg)
                    CoupleThemeApplicator.onMessageReceived(msg.from, msg.content)
                    if (msg.type == AppendOnlyMessageDB.MSG_MOOD) {
                        updatePartnerMood()
                    }
                }
            }
        }
    }
    
    private fun updatePartnerMood() {
        val mood = messageDB.getLatestMood(partnerUserId)
        if (mood != null) {
            CoupleThemeApplicator.onMoodUpdate(mood)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        CoupleThemeApplicator.deactivate()
    }
}
```

---

That's it! The couple theme is now fully integrated and ready to use.
