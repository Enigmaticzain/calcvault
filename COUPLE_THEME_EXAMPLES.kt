// Couple Theme - Configuration Examples

// ============================================================================
// EXAMPLE 1: Default Configuration (Recommended for most users)
// ============================================================================

val defaultConfig = CoupleThemeConfig(
    enableCharacters = true,
    enableInteractions = true,
    animationIntensity = 1.0f,
    autoSceneSwitch = true,
    sceneChangeIntervalMs = 45_000L,
    lockedScene = null
)

CoupleThemeApplicator.setConfig(defaultConfig)

// Result:
// - Characters visible and animated
// - Scenes change every 45 seconds
// - Full animation intensity
// - All interactions enabled


// ============================================================================
// EXAMPLE 2: Low Power Mode (Battery conscious)
// ============================================================================

val lowPowerConfig = CoupleThemeConfig(
    enableCharacters = true,
    enableInteractions = true,
    animationIntensity = 0.5f,
    autoSceneSwitch = true,
    sceneChangeIntervalMs = 120_000L,  // Change every 2 minutes
    lockedScene = null
)

CoupleThemeApplicator.setConfig(lowPowerConfig)

// Result:
// - Reduced animation intensity (50%)
// - Slower scene transitions (2 min intervals)
// - Still fully functional, less battery drain


// ============================================================================
// EXAMPLE 3: Minimal Mode (Distraction-free)
// ============================================================================

val minimalConfig = CoupleThemeConfig(
    enableCharacters = false,
    enableInteractions = false,
    animationIntensity = 0.3f,
    autoSceneSwitch = true,
    sceneChangeIntervalMs = 60_000L,
    lockedScene = null
)

CoupleThemeApplicator.setConfig(minimalConfig)

// Result:
// - No characters visible
// - No interactions
// - Minimal background animation
// - Clean, distraction-free chat


// ============================================================================
// EXAMPLE 4: Romantic Mode (Special occasions)
// ============================================================================

val romanticConfig = CoupleThemeConfig(
    enableCharacters = true,
    enableInteractions = true,
    animationIntensity = 1.5f,  // Enhanced (capped at 1.0 internally)
    autoSceneSwitch = false,
    sceneChangeIntervalMs = 45_000L,
    lockedScene = SceneType.SUNSET
)

CoupleThemeApplicator.setConfig(romanticConfig)
CoupleThemeApplicator.setInteraction(CharacterInteraction.CUDDLING)

// Result:
// - Full character animations
// - Locked to SUNSET scene
// - Characters in CUDDLING pose
// - Perfect for romantic moments


// ============================================================================
// EXAMPLE 5: Relaxation Mode (Calm environment)
// ============================================================================

val relaxationConfig = CoupleThemeConfig(
    enableCharacters = true,
    enableInteractions = true,
    animationIntensity = 0.7f,
    autoSceneSwitch = false,
    sceneChangeIntervalMs = 45_000L,
    lockedScene = SceneType.CALM
)

CoupleThemeApplicator.setConfig(relaxationConfig)
CoupleThemeApplicator.setInteraction(CharacterInteraction.SITTING_TOGETHER)

// Result:
// - Peaceful CALM scene
// - Gentle character animations
// - Relaxing atmosphere


// ============================================================================
// EXAMPLE 6: Adventure Mode (Dynamic scenes)
// ============================================================================

val adventureConfig = CoupleThemeConfig(
    enableCharacters = true,
    enableInteractions = true,
    animationIntensity = 1.0f,
    autoSceneSwitch = true,
    sceneChangeIntervalMs = 30_000L,  // Change every 30 seconds
    lockedScene = null
)

CoupleThemeApplicator.setConfig(adventureConfig)

// Result:
// - Scenes change frequently
// - Full animations
// - Dynamic, engaging experience


// ============================================================================
// EXAMPLE 7: Programmatic Scene Control
// ============================================================================

// Change scene based on time of day
fun updateSceneByTime() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val scene = when (hour) {
        in 6..11 -> SceneType.BEACH      // Morning
        in 12..16 -> SceneType.FOREST    // Afternoon
        in 17..19 -> SceneType.SUNSET    // Evening
        else -> SceneType.CALM           // Night
    }
    CoupleThemeApplicator.setScene(scene)
}

// Change scene based on weather (if available)
fun updateSceneByWeather(weatherCondition: String) {
    val scene = when (weatherCondition.lowercase()) {
        "rainy" -> SceneType.FOREST
        "sunny" -> SceneType.BEACH
        "cloudy" -> SceneType.CALM
        "clear night" -> SceneType.CAMPING
        else -> SceneType.WATERFALL
    }
    CoupleThemeApplicator.setScene(scene)
}


// ============================================================================
// EXAMPLE 8: Interaction Sequences
// ============================================================================

// Romantic sequence
fun playRomanticSequence() {
    val interactions = listOf(
        CharacterInteraction.SITTING_TOGETHER,
        CharacterInteraction.HOLDING_HANDS,
        CharacterInteraction.LEANING_HEAD,
        CharacterInteraction.LIGHT_HUG,
        CharacterInteraction.CUDDLING
    )
    
    var index = 0
    val handler = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            if (index < interactions.size) {
                CoupleThemeApplicator.setInteraction(interactions[index])
                index++
                handler.postDelayed(this, 5000)  // 5 seconds per interaction
            }
        }
    }
    handler.post(runnable)
}

// Playful sequence
fun playPlayfulSequence() {
    val interactions = listOf(
        CharacterInteraction.TALKING_SOFTLY,
        CharacterInteraction.WATCHING_VIEW,
        CharacterInteraction.TALKING_SOFTLY,
        CharacterInteraction.HOLDING_HANDS
    )
    
    var index = 0
    val handler = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            if (index < interactions.size) {
                CoupleThemeApplicator.setInteraction(interactions[index])
                index++
                handler.postDelayed(this, 3000)  // 3 seconds per interaction
            }
        }
    }
    handler.post(runnable)
}


// ============================================================================
// EXAMPLE 9: Message-Triggered Scenes
// ============================================================================

fun handleSpecialMessages(content: String) {
    when {
        content.contains("beach", ignoreCase = true) -> {
            CoupleThemeApplicator.setScene(SceneType.BEACH)
            CoupleThemeApplicator.setInteraction(CharacterInteraction.WATCHING_VIEW)
        }
        content.contains("camping", ignoreCase = true) -> {
            CoupleThemeApplicator.setScene(SceneType.CAMPING)
            CoupleThemeApplicator.setInteraction(CharacterInteraction.SITTING_TOGETHER)
        }
        content.contains("waterfall", ignoreCase = true) -> {
            CoupleThemeApplicator.setScene(SceneType.WATERFALL)
            CoupleThemeApplicator.setInteraction(CharacterInteraction.WATCHING_VIEW)
        }
        content.contains("sunset", ignoreCase = true) -> {
            CoupleThemeApplicator.setScene(SceneType.SUNSET)
            CoupleThemeApplicator.setInteraction(CharacterInteraction.CUDDLING)
        }
        content.contains("forest", ignoreCase = true) -> {
            CoupleThemeApplicator.setScene(SceneType.FOREST)
            CoupleThemeApplicator.setInteraction(CharacterInteraction.HOLDING_HANDS)
        }
    }
}


// ============================================================================
// EXAMPLE 10: Settings Persistence
// ============================================================================

// Save user preferences
fun saveUserPreferences(
    enableCharacters: Boolean,
    enableInteractions: Boolean,
    intensity: Float,
    autoSwitch: Boolean,
    lockedScene: SceneType?
) {
    val config = CoupleThemeConfig(
        enableCharacters = enableCharacters,
        enableInteractions = enableInteractions,
        animationIntensity = intensity,
        autoSceneSwitch = autoSwitch,
        sceneChangeIntervalMs = 45_000L,
        lockedScene = lockedScene
    )
    CoupleThemeApplicator.setConfig(config)
}

// Load and apply saved preferences
fun loadUserPreferences() {
    val config = CoupleThemeApplicator.getConfig()
    if (config != null) {
        // Config is automatically loaded from storage
        // Just apply it
        CoupleThemeApplicator.setConfig(config)
    }
}


// ============================================================================
// EXAMPLE 11: Conditional Activation
// ============================================================================

// Only activate if user has enabled it
fun conditionallyActivateTheme(activity: Activity, container: ViewGroup) {
    val isEnabled = SessionManager.isCoupleThemeEnabled  // Your preference
    if (isEnabled) {
        CoupleThemeApplicator.activate(activity, container)
    }
}

// Deactivate if battery is low
fun deactivateIfLowBattery(context: Context) {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    
    if (batteryLevel < 20) {  // Less than 20%
        CoupleThemeApplicator.deactivate()
    }
}


// ============================================================================
// EXAMPLE 12: Animation Intensity Presets
// ============================================================================

object AnimationIntensityPresets {
    const val OFF = 0.0f
    const val LOW = 0.3f
    const val MEDIUM = 0.6f
    const val HIGH = 1.0f
    const val ULTRA = 1.5f  // Will be capped at 1.0
}

fun setAnimationIntensity(preset: Float) {
    val config = CoupleThemeApplicator.getConfig() ?: CoupleThemeConfig()
    CoupleThemeApplicator.setConfig(
        config.copy(animationIntensity = preset.coerceIn(0f, 1.5f))
    )
}

// Usage
setAnimationIntensity(AnimationIntensityPresets.MEDIUM)


// ============================================================================
// EXAMPLE 13: Scene Change Interval Presets
// ============================================================================

object SceneChangeIntervalPresets {
    const val VERY_FAST = 15_000L    // 15 seconds
    const val FAST = 30_000L         // 30 seconds
    const val NORMAL = 45_000L       // 45 seconds (default)
    const val SLOW = 60_000L         // 1 minute
    const val VERY_SLOW = 120_000L   // 2 minutes
}

fun setSceneChangeInterval(interval: Long) {
    val config = CoupleThemeApplicator.getConfig() ?: CoupleThemeConfig()
    CoupleThemeApplicator.setConfig(
        config.copy(sceneChangeIntervalMs = interval)
    )
}

// Usage
setSceneChangeInterval(SceneChangeIntervalPresets.FAST)


// ============================================================================
// EXAMPLE 14: Complete Settings Activity Integration
// ============================================================================

class CoupleThemeSettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val config = CoupleThemeApplicator.getConfig() ?: CoupleThemeConfig()
        
        // Enable/Disable Characters
        characterToggle.isChecked = config.enableCharacters
        characterToggle.setOnCheckedChangeListener { _, isChecked ->
            CoupleThemeApplicator.setConfig(config.copy(enableCharacters = isChecked))
        }
        
        // Enable/Disable Interactions
        interactionToggle.isChecked = config.enableInteractions
        interactionToggle.setOnCheckedChangeListener { _, isChecked ->
            CoupleThemeApplicator.setConfig(config.copy(enableInteractions = isChecked))
        }
        
        // Animation Intensity Slider
        intensitySlider.progress = (config.animationIntensity * 100).toInt()
        intensitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                CoupleThemeApplicator.setConfig(
                    config.copy(animationIntensity = progress / 100f)
                )
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Auto Scene Switch
        autoSwitchToggle.isChecked = config.autoSceneSwitch
        autoSwitchToggle.setOnCheckedChangeListener { _, isChecked ->
            CoupleThemeApplicator.setConfig(config.copy(autoSceneSwitch = isChecked))
        }
        
        // Scene Selector
        sceneSpinner.setSelection(config.lockedScene?.ordinal ?: 0)
        sceneSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val scene = if (position == 0) null else SceneType.values()[position - 1]
                CoupleThemeApplicator.setConfig(config.copy(lockedScene = scene))
                if (scene != null) CoupleThemeApplicator.setScene(scene)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}


// ============================================================================
// EXAMPLE 15: Testing & Debugging
// ============================================================================

fun debugCoupleTheme() {
    val config = CoupleThemeApplicator.getConfig()
    Log.d("CoupleTheme", """
        Configuration:
        - Characters Enabled: ${config?.enableCharacters}
        - Interactions Enabled: ${config?.enableInteractions}
        - Animation Intensity: ${config?.animationIntensity}
        - Auto Scene Switch: ${config?.autoSceneSwitch}
        - Scene Change Interval: ${config?.sceneChangeIntervalMs}ms
        - Locked Scene: ${config?.lockedScene}
        - Theme Active: ${CoupleThemeApplicator.isActive()}
    """.trimIndent())
}

fun testAllScenes() {
    val scenes = SceneType.values()
    var index = 0
    val handler = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            if (index < scenes.size) {
                Log.d("CoupleTheme", "Testing scene: ${scenes[index]}")
                CoupleThemeApplicator.setScene(scenes[index])
                index++
                handler.postDelayed(this, 5000)  // 5 seconds per scene
            }
        }
    }
    handler.post(runnable)
}

fun testAllInteractions() {
    val interactions = CharacterInteraction.values()
    var index = 0
    val handler = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            if (index < interactions.size) {
                Log.d("CoupleTheme", "Testing interaction: ${interactions[index]}")
                CoupleThemeApplicator.setInteraction(interactions[index])
                index++
                handler.postDelayed(this, 3000)  // 3 seconds per interaction
            }
        }
    }
    handler.post(runnable)
}
