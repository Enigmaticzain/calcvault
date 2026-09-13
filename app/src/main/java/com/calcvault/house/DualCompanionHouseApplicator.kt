package com.calcvault.house

import android.app.Activity
import android.graphics.Color
import android.graphics.PointF
import android.view.ViewGroup
import android.widget.FrameLayout
import org.json.JSONObject
import com.calcvault.storage.provider.StorageManager

/**
 * DualCompanionHouseApplicator.kt - Integration point for the house theme
 *
 * Manages:
 * - Activation/deactivation of theme
 * - Character state management
 * - Message handling and reactions
 * - Configuration persistence
 */

data class HouseThemeConfig(
    val enableCharacters: Boolean = true,
    val enableDragDrop: Boolean = true,
    val enableInteractions: Boolean = true,
    val animationIntensity: Float = 1.0f,
    val autoRoomSwitch: Boolean = false,
    val currentRoom: HouseRoom = HouseRoom.LIVING,
    val debugMode: Boolean = false
)

object DualCompanionHouseApplicator {

    private var config = HouseThemeConfig()
    private var houseEnvironment: HouseEnvironment? = null
    private var behaviorEngine: CharacterBehaviorEngine? = null
    private var dragDropEngine: DragDropInteractionEngine? = null
    private var commandExecutor: CommandExecutor? = null
    private var chatTriggerEngine: ChatTriggerEngine? = null
    private var houseView: HouseSceneRenderer? = null

    // New engines
    var needsEngine: NeedsEngine? = null; private set
    var relationshipEngine: RelationshipEngine? = null; private set
    var autonomousAI: AutonomousBehaviorAI? = null; private set
    var touchReactionEngine: TouchReactionEngine? = null; private set
    var miniGameManager: MiniGameManager? = null; private set
    var particleEngine: ParticleEngine? = null; private set
    var fridge: FridgeInventory? = null; private set

    // Callbacks
    private var onMoodChanged: ((CharacterIdentity, EmotionalState) -> Unit)? = null
    private var onRoomChanged: ((HouseRoom) -> Unit)? = null
    private var onCharacterInteraction: ((CharacterIdentity, CharacterAction) -> Unit)? = null
    private var onMessageProcessed: ((String, String, List<CharacterCommand>) -> Unit)? = null
    private var onSettingChanged: ((String, Any) -> Unit)? = null

    private var isActive = false
    private var currentState = DualCharacterState(
        zain = CharacterState(CharacterIdentity.ZAIN, PointF(0f, 0f)),
        sanu = CharacterState(CharacterIdentity.SANU, PointF(0f, 0f)),
        currentRoom = HouseRoom.LIVING
    )

    private const val CONFIG_KEY = "prefs/house_theme_config"
    private const val STATE_KEY = "prefs/house_theme_state"

    // Callback registration methods
    fun setOnMoodChanged(callback: (CharacterIdentity, EmotionalState) -> Unit) {
        onMoodChanged = callback
    }

    fun setOnRoomChanged(callback: (HouseRoom) -> Unit) {
        onRoomChanged = callback
    }

    fun setOnCharacterInteraction(callback: (CharacterIdentity, CharacterAction) -> Unit) {
        onCharacterInteraction = callback
    }

    fun setOnMessageProcessed(callback: (String, String, List<CharacterCommand>) -> Unit) {
        onMessageProcessed = callback
    }

    fun setOnSettingChanged(callback: (String, Any) -> Unit) {
        onSettingChanged = callback
    }

    fun activate(activity: Activity, rootContainer: ViewGroup, sceneView: io.github.sceneview.SceneView) {
        if (isActive) return

        // Load configuration
        loadConfig()

        // Initialize engines
        val width = rootContainer.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val height = rootContainer.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        houseEnvironment = HouseEnvironment(width, height)
        behaviorEngine = houseEnvironment?.let { CharacterBehaviorEngine(it) }
        dragDropEngine = dragDropEngine ?: (
            houseEnvironment?.let { env ->
                behaviorEngine?.let { behave ->
                    DragDropInteractionEngine(env, behave)
                }
            }
            )
        commandExecutor = houseEnvironment?.let { CommandExecutor(it, behaviorEngine!!) }
        chatTriggerEngine = houseEnvironment?.let { ChatTriggerEngine(it, dragDropEngine!!) }

        // Initialize new engines
        needsEngine = NeedsEngine().apply { initialize() }
        relationshipEngine = RelationshipEngine().apply { initialize() }
        fridge = FridgeInventory()
        particleEngine = ParticleEngine()
        touchReactionEngine = TouchReactionEngine(needsEngine)
        miniGameManager = MiniGameManager()
        autonomousAI = houseEnvironment?.let { env ->
            needsEngine?.let { needs ->
                relationshipEngine?.let { rel ->
                    AutonomousBehaviorAI(needs, rel, env)
                }
            }
        }

        // Create and setup 3D renderer
        houseView = HouseSceneRenderer(activity, sceneView, width, height).apply {
            setDebugMode(config.debugMode)
            startAnimation()
        }

        // Set initial positions
        val env = houseEnvironment ?: return
        val (pos1, pos2) = env.getCharacterStartPositions(config.currentRoom)
        currentState = currentState.copy(
            zain = currentState.zain.copy(position = pos1),
            sanu = currentState.sanu.copy(position = pos2),
            currentRoom = config.currentRoom
        )

        houseView?.setCharacterState(currentState)
        env.setCurrentRoom(config.currentRoom)
        
        // Apply initial room background
        env.getRoomLayout(config.currentRoom)?.let { layout ->
            houseView?.switchToRoom(config.currentRoom, layout.backgroundColor)
        }

        isActive = true
    }

    fun deactivate() {
        houseView?.stopAnimation()
        saveConfig()
        saveState()
        needsEngine?.saveState()
        relationshipEngine?.saveState()
        houseView?.destroy()

        houseEnvironment = null
        behaviorEngine = null
        dragDropEngine = null
        commandExecutor = null
        chatTriggerEngine = null
        needsEngine = null
        relationshipEngine = null
        autonomousAI = null
        touchReactionEngine = null
        miniGameManager = null
        particleEngine = null
        fridge = null
        houseView = null

        isActive = false
    }

    fun onMessageReceived(from: String, content: String) {
        if (!isActive) return
        val trigger = chatTriggerEngine ?: return
        val exec = commandExecutor ?: return
        val view = houseView ?: return

        // Process message to generate commands
        val (commands, newState) = trigger.processMessage(from, content, currentState)

        // Execute commands
        commands.forEach { command ->
            when (command.actor) {
                CharacterIdentity.ZAIN -> {
                    currentState = currentState.copy(
                        zain = exec.executeCommand(command, currentState.zain)
                    )
                }
                CharacterIdentity.SANU -> {
                    currentState = currentState.copy(
                        sanu = exec.executeCommand(command, currentState.sanu)
                    )
                }
            }
        }

        currentState = newState
        view.setCharacterState(currentState)
        onMessageProcessed?.invoke(from, content, commands)
    }

    fun executeCommand(command: CharacterCommand) {
        if (!isActive) return
        val exec = commandExecutor ?: return
        val view = houseView ?: return

        when (command.actor) {
            CharacterIdentity.ZAIN -> {
                currentState = currentState.copy(
                    zain = exec.executeCommand(command, currentState.zain)
                )
            }
            CharacterIdentity.SANU -> {
                currentState = currentState.copy(
                    sanu = exec.executeCommand(command, currentState.sanu)
                )
            }
        }

        view.setCharacterState(currentState)
    }

    fun changeRoom(room: HouseRoom) {
        if (!isActive) return
        val env = houseEnvironment ?: return
        val view = houseView ?: return

        env.setCurrentRoom(room)
        currentState = currentState.copy(currentRoom = room)

        val (pos1, pos2) = env.getCharacterStartPositions(room)
        currentState = currentState.copy(
            zain = currentState.zain.copy(position = pos1),
            sanu = currentState.sanu.copy(position = pos2)
        )

        view.setCharacterState(currentState)
        
        // Update background color in 3D scene
        env.getRoomLayout(room)?.let { layout ->
            view.switchToRoom(room, layout.backgroundColor)
        }

        onRoomChanged?.invoke(room)
    }

    fun setAnimationIntensity(intensity: Float) {
        config = config.copy(animationIntensity = intensity)
        onSettingChanged?.invoke("animationIntensity", intensity)
    }

    fun setDragDropEnabled(enabled: Boolean) {
        config = config.copy(enableDragDrop = enabled)
        onSettingChanged?.invoke("enableDragDrop", enabled)
    }

    fun setInteractionsEnabled(enabled: Boolean) {
        config = config.copy(enableInteractions = enabled)
        onSettingChanged?.invoke("enableInteractions", enabled)
    }

    fun setAutoRoomSwitch(enabled: Boolean) {
        config = config.copy(autoRoomSwitch = enabled)
        onSettingChanged?.invoke("autoRoomSwitch", enabled)
    }

    fun setDebugMode(enabled: Boolean) {
        config = config.copy(debugMode = enabled)
        houseView?.setDebugMode(enabled)
        onSettingChanged?.invoke("debugMode", enabled)
    }


    fun setCharacterMood(character: CharacterIdentity, mood: EmotionalState) {
        if (!isActive) return

        when (character) {
            CharacterIdentity.ZAIN -> {
                currentState = currentState.copy(
                    zain = currentState.zain.copy(emotionalState = mood)
                )
            }
            CharacterIdentity.SANU -> {
                currentState = currentState.copy(
                    sanu = currentState.sanu.copy(emotionalState = mood)
                )
            }
        }

        houseView?.setCharacterState(currentState)
        onMoodChanged?.invoke(character, mood)
    }

    fun performTalkingPetAction(character: CharacterIdentity, mode: TalkingPetMode) {
        if (!isActive) return

        currentState = when (character) {
            CharacterIdentity.ZAIN -> currentState.copy(
                zain = currentState.zain.toTalkingPetState(mode, CharacterIdentity.SANU),
                interactingTogether = mode == TalkingPetMode.TALKING,
                interactionType = "tom_${mode.name.lowercase()}"
            )
            CharacterIdentity.SANU -> currentState.copy(
                sanu = currentState.sanu.toTalkingPetState(mode, CharacterIdentity.ZAIN),
                interactingTogether = mode == TalkingPetMode.TALKING,
                interactionType = "angela_${mode.name.lowercase()}"
            )
        }

        houseView?.setCharacterState(currentState)
    }

    fun performDualAction(parameter: String, zoneType: ZoneType) {
        if (!isActive) return
        val env = houseEnvironment ?: return
        val view = houseView ?: return
        val normalized = parameter.lowercase()
        val zone = findActionZone(env, normalized, zoneType) ?: return

        env.setCurrentRoom(zone.roomId)
        val secondaryPosition = zone.secondaryPosition ?: PointF(
            zone.primaryPosition.x + 90f,
            zone.primaryPosition.y
        )
        val action = actionFor(normalized, zone)

        currentState = currentState.copy(
            zain = currentState.zain.copy(
                position = zone.primaryPosition,
                targetPosition = zone.primaryPosition,
                action = action,
                isWalking = false,
                currentZone = zone,
                animationProgress = 0f
            ),
            sanu = currentState.sanu.copy(
                position = secondaryPosition,
                targetPosition = secondaryPosition,
                action = action,
                isWalking = false,
                currentZone = zone,
                animationProgress = 0f
            ),
            currentRoom = zone.roomId,
            interactingTogether = true,
            interactionType = normalized
        )

        view.setCharacterState(currentState)
    }

    fun getConfig(): HouseThemeConfig = config

    fun setConfig(newConfig: HouseThemeConfig) {
        config = newConfig
        saveConfig()

        houseView?.setDebugMode(config.debugMode)
        behaviorEngine?.setEnabled(config.enableCharacters)
    }

    fun getCharacterState(): DualCharacterState = currentState

    fun isActive(): Boolean = isActive

    // ─────────────────────────────────────────────────────────────────
    // NEW: NEEDS-BASED ACTIONS
    // ─────────────────────────────────────────────────────────────────

    fun feedCharacter(character: CharacterIdentity, food: FoodItem): FeedResult? {
        if (!isActive) return null
        val result = needsEngine?.feed(character, food) ?: return null
        if (result.success) {
            val action = CharacterAction.EATING
            when (character) {
                CharacterIdentity.ZAIN -> currentState = currentState.copy(
                    zain = currentState.zain.copy(action = action, emotionalState = EmotionalState.HAPPY, animationProgress = 0f))
                CharacterIdentity.SANU -> currentState = currentState.copy(
                    sanu = currentState.sanu.copy(action = action, emotionalState = EmotionalState.HAPPY, animationProgress = 0f))
            }
            houseView?.setCharacterState(currentState)
            particleEngine?.emit(ParticleType.STAR, getCharPos(character).x, getCharPos(character).y - 60f, 5)
            relationshipEngine?.onActivityTogether("eat")
        }
        return result
    }

    fun batheCharacter(character: CharacterIdentity) {
        if (!isActive) return
        needsEngine?.bathe(character)
        when (character) {
            CharacterIdentity.ZAIN -> currentState = currentState.copy(
                zain = currentState.zain.copy(action = CharacterAction.BATHING, animationProgress = 0f))
            CharacterIdentity.SANU -> currentState = currentState.copy(
                sanu = currentState.sanu.copy(action = CharacterAction.BATHING, animationProgress = 0f))
        }
        houseView?.setCharacterState(currentState)
        particleEngine?.emit(ParticleType.BUBBLE, getCharPos(character).x, getCharPos(character).y - 40f, 8)
    }

    fun putToSleep(character: CharacterIdentity) {
        if (!isActive) return
        needsEngine?.startSleeping(character)
        changeRoom(HouseRoom.BEDROOM)
        when (character) {
            CharacterIdentity.ZAIN -> currentState = currentState.copy(
                zain = currentState.zain.copy(action = CharacterAction.SLEEPING, emotionalState = EmotionalState.TIRED))
            CharacterIdentity.SANU -> currentState = currentState.copy(
                sanu = currentState.sanu.copy(action = CharacterAction.SLEEPING, emotionalState = EmotionalState.TIRED))
        }
        houseView?.setCharacterState(currentState)
        particleEngine?.emit(ParticleType.SLEEP_Z, getCharPos(character).x, getCharPos(character).y - 60f, 3)
    }

    fun startMiniGame(type: MiniGameType) {
        if (!isActive) return
        miniGameManager?.startGame(type)
    }

    fun getAvailableFoods(): List<Pair<FoodItem, Int>> = fridge?.getAvailableFoods() ?: emptyList()

    fun takeFoodFromFridge(foodId: String): FoodItem? = fridge?.takeFood(foodId)

    private fun getCharPos(c: CharacterIdentity): PointF = when (c) {
        CharacterIdentity.ZAIN -> currentState.zain.position
        CharacterIdentity.SANU -> currentState.sanu.position
    }

    private fun findActionZone(
        env: HouseEnvironment,
        parameter: String,
        requestedZone: ZoneType
    ): InteractionZone? {
        val room = when (parameter) {
            "cook" -> HouseRoom.KITCHEN
            "play" -> HouseRoom.ACTIVITY
            "relax" -> HouseRoom.LIVING
            "watch" -> HouseRoom.LIVING
            else -> currentState.currentRoom
        }
        val preferredTypes = when (parameter) {
            "watch", "relax" -> listOf(requestedZone, ZoneType.SOFA, ZoneType.FLOOR)
            "play" -> listOf(requestedZone, ZoneType.GAME_AREA, ZoneType.FLOOR)
            "cook" -> listOf(requestedZone, ZoneType.KITCHEN, ZoneType.FLOOR)
            else -> listOf(requestedZone, ZoneType.FLOOR)
        }
        val roomZones = env.getZonesForRoom(room)
        return preferredTypes.firstNotNullOfOrNull { type ->
            roomZones.firstOrNull { it.type == type }
        } ?: roomZones.firstOrNull { it.dualAction }
    }

    private fun actionFor(parameter: String, zone: InteractionZone): CharacterAction {
        return when (parameter.ifBlank { zone.animationAction }) {
            "cook" -> CharacterAction.COOKING
            "watch" -> CharacterAction.WATCHING_TV
            "play" -> CharacterAction.PLAYING
            "relax" -> CharacterAction.SITTING
            "sleep" -> CharacterAction.SLEEPING
            else -> CharacterAction.IDLE
        }
    }

    private fun CharacterState.toTalkingPetState(
        mode: TalkingPetMode,
        partner: CharacterIdentity
    ): CharacterState {
        val action = when (mode) {
            TalkingPetMode.IDLE -> CharacterAction.IDLE
            TalkingPetMode.LISTENING -> CharacterAction.LISTENING
            TalkingPetMode.TALKING -> CharacterAction.TALKING
        }
        val mood = when (mode) {
            TalkingPetMode.IDLE -> EmotionalState.HAPPY
            TalkingPetMode.LISTENING -> EmotionalState.FOCUSED
            TalkingPetMode.TALKING -> EmotionalState.PLAYFUL
        }
        val petScale = when (mode) {
            TalkingPetMode.IDLE -> 1.0f
            TalkingPetMode.LISTENING -> 1.04f
            TalkingPetMode.TALKING -> 1.08f
        }
        return copy(
            action = action,
            emotionalState = mood,
            isWalking = false,
            scale = petScale,
            animationProgress = 0f,
            interactingWith = if (mode == TalkingPetMode.TALKING) partner else null,
            timeInAction = 0L
        )
    }

    private fun saveConfig() {
        try {
            val json = JSONObject().apply {
                put("enableCharacters", config.enableCharacters)
                put("enableDragDrop", config.enableDragDrop)
                put("enableInteractions", config.enableInteractions)
                put("animationIntensity", config.animationIntensity)
                put("autoRoomSwitch", config.autoRoomSwitch)
                put("currentRoom", config.currentRoom.name)
                put("debugMode", config.debugMode)
            }
            StorageManager.write(CONFIG_KEY, json.toString().toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadConfig() {
        try {
            val data = StorageManager.read(CONFIG_KEY) ?: return
            val json = JSONObject(String(data))
            config = HouseThemeConfig(
                enableCharacters = json.optBoolean("enableCharacters", true),
                enableDragDrop = json.optBoolean("enableDragDrop", true),
                enableInteractions = json.optBoolean("enableInteractions", true),
                animationIntensity = json.optDouble("animationIntensity", 1.0).toFloat(),
                autoRoomSwitch = json.optBoolean("autoRoomSwitch", false),
                currentRoom = try {
                    HouseRoom.valueOf(json.optString("currentRoom", "LIVING"))
                } catch (e: Exception) {
                    HouseRoom.LIVING
                },
                debugMode = json.optBoolean("debugMode", false)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveState() {
        try {
            val json = JSONObject().apply {
                put("zain", currentState.zain.toJSON())
                put("sanu", currentState.sanu.toJSON())
                put("room", currentState.currentRoom.name)
                put("interacting", currentState.interactingTogether)
            }
            StorageManager.write(STATE_KEY, json.toString().toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Helper: add message bubble styling
data class HouseMessageBubbleStyle(
    val backgroundColor: Int,
    val textColor: Int,
    val characterEmoji: String,
    val characterName: String,
    val characterIdentity: CharacterIdentity
)

fun getHouseMessageBubbleStyle(from: String): HouseMessageBubbleStyle {
    return when (from.lowercase()) {
        "zain" -> HouseMessageBubbleStyle(
            backgroundColor = Color.parseColor("#673AB7"),
            textColor = Color.WHITE,
            characterEmoji = "👨",
            characterName = "Zain",
            characterIdentity = CharacterIdentity.ZAIN
        )
        "sanu" -> HouseMessageBubbleStyle(
            backgroundColor = Color.parseColor("#E91E63"),
            textColor = Color.WHITE,
            characterEmoji = "👩",
            characterName = "Sanu",
            characterIdentity = CharacterIdentity.SANU
        )
        else -> HouseMessageBubbleStyle(
            backgroundColor = Color.parseColor("#424242"),
            textColor = Color.WHITE,
            characterEmoji = "💬",
            characterName = from,
            characterIdentity = CharacterIdentity.ZAIN
        )
    }
}
