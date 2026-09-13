package com.calcvault.house

import android.graphics.PointF
import android.graphics.RectF
import org.json.JSONObject

/**
 * HouseModels.kt - Core data structures for dual-companion house theme
 *
 * Defines:
 * - Room enumeration and properties
 * - Character data models
 * - Interaction zones
 * - Commands and behaviors
 * - Animation states
 */

// ═══════════════════════════════════════════════════════════════════════════
// ROOM & ZONES
// ═══════════════════════════════════════════════════════════════════════════

enum class HouseRoom {
    LIVING,    // Living room with TV, sofa
    KITCHEN,   // Kitchen with stove, counter
    BEDROOM,   // Sleep area with bed
    ACTIVITY,  // Game/play area
    BATHROOM,  // Toilet, shower, bathtub
    GARDEN     // Outdoor patio, trampoline, mini-games
}

enum class ZoneType {
    SOFA,           // Seating zone
    TV,             // Television viewing zone
    KITCHEN,        // Cooking/food prep
    BALCONY,        // Resting area
    BED,            // Sleep area
    GAME_AREA,      // Play/activity zone
    DOOR,           // Room transition
    FLOOR,          // General floor space
    TOILET,         // Bathroom toilet
    SHOWER,         // Bathroom shower
    BATHTUB,        // Bathroom bathtub
    SINK,           // Bathroom sink/mirror
    GARDEN_BENCH,   // Garden seating
    TRAMPOLINE,     // Garden trampoline
    MINI_GAME_CONSOLE, // Garden game console
    FLOWER_BED,     // Garden flowers
    FRIDGE          // Kitchen fridge for food
}

data class InteractionZone(
    val id: String,
    val type: ZoneType,
    val bounds: RectF,
    val roomId: HouseRoom,
    val animationAction: String, // "sit", "cook", "watch", "play", "sleep"
    val dualAction: Boolean = false, // Can both characters do this?
    val primaryPosition: PointF,
    val secondaryPosition: PointF? = null // For dual interactions
)

data class RoomLayout(
    val room: HouseRoom,
    val bounds: RectF,
    val zones: List<InteractionZone>,
    val backgroundColor: Int,
    val backgroundResource: String? = null,
    val suggestedCharacterPositions: List<PointF>
)

// ═══════════════════════════════════════════════════════════════════════════
// CHARACTER MODELS
// ═══════════════════════════════════════════════════════════════════════════

enum class CharacterIdentity {
    ZAIN, // Male character
    SANU // Female character
}

enum class EmotionalState {
    HAPPY, SAD, TIRED, ROMANTIC, PLAYFUL, NEUTRAL, FOCUSED, RESTING
}

enum class CharacterAction {
    IDLE,           // Standing still
    WALKING,        // Moving between zones
    SITTING,        // On sofa/chair
    COOKING,        // In kitchen
    WATCHING_TV,    // At TV
    SLEEPING,       // In bed
    PLAYING,        // Activity area
    TALKING,        // Conversation gesture
    REACHING,       // Reach gesture
    LISTENING,      // Attending gesture
    HELPING,        // Assisting partner
    CUDDLING,       // Close interaction
    HOLDING_HANDS,  // Hand connection
    HEAD_TILT,      // Listening gesture
    EATING,         // Eating food
    BATHING,        // Showering/bathing
    DRINKING,       // Drinking
    BRUSHING_TEETH, // Dental care
    USING_TOILET,   // Bathroom
    GARDENING,      // Tending flowers
    TRAMPOLINING,   // Bouncing
    DANCING,        // Dancing together
    SICK,           // Feeling unwell
    REFUSING,       // Won't do action (needs too low)
    FLINCHING,      // Poke reaction
    LAUGHING,       // Tickle reaction
    PURRING         // Being petted
}

enum class TalkingPetMode {
    IDLE,
    LISTENING,
    TALKING
}

data class CharacterState(
    val identity: CharacterIdentity,
    val position: PointF,
    val targetPosition: PointF = position,
    val action: CharacterAction = CharacterAction.IDLE,
    val emotionalState: EmotionalState = EmotionalState.NEUTRAL,
    val isWalking: Boolean = false,
    val speed: Float = 2.5f, // Pixels per frame
    val rotation: Float = 0f, // 0-360 degrees
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val currentZone: InteractionZone? = null,
    val animationProgress: Float = 0f, // 0-1 for current animation
    val interactingWith: CharacterIdentity? = null,
    val timeInAction: Long = 0L
)

data class DualCharacterState(
    val zain: CharacterState,
    val sanu: CharacterState,
    val currentRoom: HouseRoom,
    val interactingTogether: Boolean = false,
    val interactionType: String? = null // "cooking", "watching", "cuddling", etc.
)

// ═══════════════════════════════════════════════════════════════════════════
// COMMANDS & ACTIONS
// ═══════════════════════════════════════════════════════════════════════════

enum class ActionType {
    GO_TO_ZONE, // Move to specific zone
    DO_ACTION, // Perform action at current location
    INTERACT_TOGETHER, // Both characters do action together
    PLAY_ANIMATION, // Play micro-animation
    CHANGE_MOOD, // Shift emotional state
    TALK_TO_PARTNER // Conversation gesture
}

data class CharacterCommand(
    val id: String,
    val actor: CharacterIdentity,
    val actionType: ActionType,
    val targetZone: InteractionZone? = null,
    val targetLocation: PointF? = null,
    val targetRoom: HouseRoom? = null,
    val parameter: String? = null,
    val duration: Long = 0L,
    val shouldNotifyPartner: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class CommandSequence(
    val commands: List<CharacterCommand>,
    val parallel: Boolean = false, // Execute simultaneously or sequentially?
    val totalDuration: Long = 0L
)

// ═══════════════════════════════════════════════════════════════════════════
// MICRO-ANIMATIONS
// ═══════════════════════════════════════════════════════════════════════════

enum class MicroAnimationType {
    BLINK, // Eye blink
    BREATHING, // Chest breathing
    EYE_TRACK, // Eyes tracking movement
    HEAD_TURN, // Head rotation
    SHIFT_WEIGHT, // Stance shift
    FIDGET, // Small movements
    NOD, // Head nod
    SMILE, // Facial expression
    SWAY, // Gentle swaying
    REACH, // Arm reach
    WAVE, // Waving gesture
    POINT, // Pointing gesture
    TILT // Head tilt animation
}

data class MicroAnimation(
    val type: MicroAnimationType,
    val duration: Long, // Milliseconds
    val loopCount: Int = 1,
    val intensity: Float = 1.0f, // 0.5 = subtle, 1.0 = normal, 1.5 = exaggerated
    val delayMs: Long = 0L
)

// ═══════════════════════════════════════════════════════════════════════════
// CHAT TRIGGERS
// ═══════════════════════════════════════════════════════════════════════════

data class ChatTriggerResponse(
    val keywords: List<String>,
    val action: CharacterAction,
    val emotionalState: EmotionalState,
    val zone: ZoneType? = null,
    val animationType: MicroAnimationType? = null,
    val affectsPartner: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════
// SERIALIZATION HELPERS
// ═══════════════════════════════════════════════════════════════════════════

fun CharacterState.toJSON(): JSONObject = JSONObject().apply {
    put("identity", identity.name)
    put("x", position.x)
    put("y", position.y)
    put("targetX", targetPosition.x)
    put("targetY", targetPosition.y)
    put("action", action.name)
    put("emotionalState", emotionalState.name)
    put("walking", isWalking)
    put("speed", speed)
    put("rotation", rotation)
    put("scale", scale)
    put("opacity", opacity)
    put("animProgress", animationProgress)
    put("timeInAction", timeInAction)
}

fun JSONObject.toCharacterState(): CharacterState = CharacterState(
    identity = CharacterIdentity.valueOf(getString("identity")),
    position = PointF(getDouble("x").toFloat(), getDouble("y").toFloat()),
    targetPosition = PointF(getDouble("targetX").toFloat(), getDouble("targetY").toFloat()),
    action = CharacterAction.valueOf(getString("action")),
    emotionalState = EmotionalState.valueOf(getString("emotionalState")),
    isWalking = getBoolean("walking"),
    speed = getDouble("speed").toFloat(),
    rotation = getDouble("rotation").toFloat(),
    scale = getDouble("scale").toFloat(),
    opacity = getDouble("opacity").toFloat(),
    animationProgress = getDouble("animProgress").toFloat(),
    timeInAction = getLong("timeInAction")
)
