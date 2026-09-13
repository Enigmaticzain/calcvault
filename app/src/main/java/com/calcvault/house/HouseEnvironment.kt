package com.calcvault.house

import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Color

/**
 * HouseEnvironment.kt - House layout, rooms, and zone management
 *
 * Manages:
 * - Room definitions and layouts
 * - Interaction zone creation
 * - Character positioning logic
 * - Environment state
 */

class HouseEnvironment(
    val screenWidth: Int,
    val screenHeight: Int
) {
    private val roomLayouts = mutableMapOf<HouseRoom, RoomLayout>()
    private val zoneRegistry = mutableMapOf<String, InteractionZone>()
    private var currentRoom = HouseRoom.LIVING

    init {
        initializeRooms()
    }

    private fun initializeRooms() {
        // ───────────────────────────────────────────────────────────────────
        // LIVING ROOM - TV, Sofa, Floor
        // ───────────────────────────────────────────────────────────────────
        val livingBounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        val livingZones = listOf(
            // TV Zone (top center)
            InteractionZone(
                id = "sofa_zone",
                type = ZoneType.SOFA,
                bounds = RectF(
                    screenWidth * 0.2f,
                    screenHeight * 0.3f,
                    screenWidth * 0.8f,
                    screenHeight * 0.5f
                ),
                roomId = HouseRoom.LIVING,
                animationAction = "sit",
                dualAction = true,
                primaryPosition = PointF(screenWidth * 0.35f, screenHeight * 0.4f),
                secondaryPosition = PointF(screenWidth * 0.65f, screenHeight * 0.4f)
            ),
            // Floor center
            InteractionZone(
                id = "living_floor",
                type = ZoneType.FLOOR,
                bounds = RectF(
                    screenWidth * 0.1f,
                    screenHeight * 0.5f,
                    screenWidth * 0.9f,
                    screenHeight * 0.85f
                ),
                roomId = HouseRoom.LIVING,
                animationAction = "stand",
                dualAction = true,
                primaryPosition = PointF(screenWidth * 0.35f, screenHeight * 0.65f),
                secondaryPosition = PointF(screenWidth * 0.65f, screenHeight * 0.65f)
            )
        )

        roomLayouts[HouseRoom.LIVING] = RoomLayout(
            room = HouseRoom.LIVING,
            bounds = livingBounds,
            zones = livingZones,
            backgroundColor = Color.parseColor("#BBDEFB"),
            backgroundResource = "living_room_bg",
            suggestedCharacterPositions = listOf(
                PointF(screenWidth * 0.35f, screenHeight * 0.4f),
                PointF(screenWidth * 0.65f, screenHeight * 0.4f)
            )
        )

        // ───────────────────────────────────────────────────────────────────
        // KITCHEN - Stove, Counter, Food prep area
        // ───────────────────────────────────────────────────────────────────
        val kitchenBounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        val kitchenZones = listOf(
            // Cooking prep area
            InteractionZone(
                id = "kitchen_counter",
                type = ZoneType.KITCHEN,
                bounds = RectF(
                    screenWidth * 0.15f,
                    screenHeight * 0.25f,
                    screenWidth * 0.85f,
                    screenHeight * 0.5f
                ),
                roomId = HouseRoom.KITCHEN,
                animationAction = "cook",
                dualAction = true,
                primaryPosition = PointF(screenWidth * 0.35f, screenHeight * 0.35f),
                secondaryPosition = PointF(screenWidth * 0.65f, screenHeight * 0.35f)
            ),
            // Kitchen floor
            InteractionZone(
                id = "kitchen_floor",
                type = ZoneType.FLOOR,
                bounds = RectF(
                    screenWidth * 0.1f,
                    screenHeight * 0.5f,
                    screenWidth * 0.9f,
                    screenHeight * 0.85f
                ),
                roomId = HouseRoom.KITCHEN,
                animationAction = "stand",
                dualAction = true,
                primaryPosition = PointF(screenWidth * 0.35f, screenHeight * 0.65f),
                secondaryPosition = PointF(screenWidth * 0.65f, screenHeight * 0.65f)
            )
        )

        roomLayouts[HouseRoom.KITCHEN] = RoomLayout(
            room = HouseRoom.KITCHEN,
            bounds = kitchenBounds,
            zones = kitchenZones,
            backgroundColor = Color.parseColor("#FFE0B2"),
            backgroundResource = "kitchen_bg",
            suggestedCharacterPositions = listOf(
                PointF(screenWidth * 0.35f, screenHeight * 0.35f),
                PointF(screenWidth * 0.65f, screenHeight * 0.35f)
            )
        )

        // ───────────────────────────────────────────────────────────────────
        // BEDROOM - Bed, Nightstands, Floor
        // ───────────────────────────────────────────────────────────────────
        val bedroomBounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        val bedroomZones = listOf(
            // Bed zone
            InteractionZone(
                id = "bed_zone",
                type = ZoneType.BED,
                bounds = RectF(
                    screenWidth * 0.2f,
                    screenHeight * 0.25f,
                    screenWidth * 0.8f,
                    screenHeight * 0.55f
                ),
                roomId = HouseRoom.BEDROOM,
                animationAction = "sleep",
                dualAction = true,
                primaryPosition = PointF(screenWidth * 0.35f, screenHeight * 0.4f),
                secondaryPosition = PointF(screenWidth * 0.65f, screenHeight * 0.4f)
            ),
            // Bedroom floor
            InteractionZone(
                id = "bedroom_floor",
                type = ZoneType.FLOOR,
                bounds = RectF(
                    screenWidth * 0.1f,
                    screenHeight * 0.55f,
                    screenWidth * 0.9f,
                    screenHeight * 0.9f
                ),
                roomId = HouseRoom.BEDROOM,
                animationAction = "stand",
                dualAction = true,
                primaryPosition = PointF(screenWidth * 0.35f, screenHeight * 0.7f),
                secondaryPosition = PointF(screenWidth * 0.65f, screenHeight * 0.7f)
            )
        )

        roomLayouts[HouseRoom.BEDROOM] = RoomLayout(
            room = HouseRoom.BEDROOM,
            bounds = bedroomBounds,
            zones = bedroomZones,
            backgroundColor = Color.parseColor("#E1BEE7"),
            backgroundResource = "bedroom_bg",
            suggestedCharacterPositions = listOf(
                PointF(screenWidth * 0.35f, screenHeight * 0.4f),
                PointF(screenWidth * 0.65f, screenHeight * 0.4f)
            )
        )

        // ───────────────────────────────────────────────────────────────────
        // ACTIVITY AREA - Games, Play, Recreation
        // ───────────────────────────────────────────────────────────────────
        val activityBounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        val activityZones = listOf(
            // Game/activity zone
            InteractionZone(
                id = "game_zone",
                type = ZoneType.GAME_AREA,
                bounds = RectF(
                    screenWidth * 0.15f,
                    screenHeight * 0.2f,
                    screenWidth * 0.85f,
                    screenHeight * 0.8f
                ),
                roomId = HouseRoom.ACTIVITY,
                animationAction = "play",
                dualAction = true,
                primaryPosition = PointF(screenWidth * 0.35f, screenHeight * 0.5f),
                secondaryPosition = PointF(screenWidth * 0.65f, screenHeight * 0.5f)
            )
        )

        roomLayouts[HouseRoom.ACTIVITY] = RoomLayout(
            room = HouseRoom.ACTIVITY,
            bounds = activityBounds,
            zones = activityZones,
            backgroundColor = Color.parseColor("#C8E6C9"),
            backgroundResource = "activity_bg",
            suggestedCharacterPositions = listOf(
                PointF(screenWidth * 0.35f, screenHeight * 0.5f),
                PointF(screenWidth * 0.65f, screenHeight * 0.5f)
            )
        )

        // ───────────────────────────────────────────────────────────────────
        // BATHROOM — Toilet, Shower, Bathtub, Sink
        // ───────────────────────────────────────────────────────────────────
        val bathroomBounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        val bathroomZones = listOf(
            InteractionZone("shower_zone", ZoneType.SHOWER,
                RectF(screenWidth * 0.05f, screenHeight * 0.2f, screenWidth * 0.35f, screenHeight * 0.55f),
                HouseRoom.BATHROOM, "shower", true,
                PointF(screenWidth * 0.2f, screenHeight * 0.38f),
                PointF(screenWidth * 0.2f, screenHeight * 0.42f)),
            InteractionZone("bathtub_zone", ZoneType.BATHTUB,
                RectF(screenWidth * 0.4f, screenHeight * 0.2f, screenWidth * 0.95f, screenHeight * 0.5f),
                HouseRoom.BATHROOM, "bathe", true,
                PointF(screenWidth * 0.6f, screenHeight * 0.35f),
                PointF(screenWidth * 0.75f, screenHeight * 0.35f)),
            InteractionZone("toilet_zone", ZoneType.TOILET,
                RectF(screenWidth * 0.05f, screenHeight * 0.6f, screenWidth * 0.35f, screenHeight * 0.85f),
                HouseRoom.BATHROOM, "toilet", false,
                PointF(screenWidth * 0.2f, screenHeight * 0.72f)),
            InteractionZone("sink_zone", ZoneType.SINK,
                RectF(screenWidth * 0.5f, screenHeight * 0.55f, screenWidth * 0.9f, screenHeight * 0.75f),
                HouseRoom.BATHROOM, "brush", true,
                PointF(screenWidth * 0.65f, screenHeight * 0.65f),
                PointF(screenWidth * 0.78f, screenHeight * 0.65f))
        )
        roomLayouts[HouseRoom.BATHROOM] = RoomLayout(
            room = HouseRoom.BATHROOM,
            bounds = bathroomBounds,
            zones = bathroomZones,
            backgroundColor = Color.parseColor("#B2EBF2"),
            backgroundResource = "bathroom_bg",
            suggestedCharacterPositions = listOf(
                PointF(screenWidth * 0.35f, screenHeight * 0.5f),
                PointF(screenWidth * 0.65f, screenHeight * 0.5f)
            )
        )

        // ───────────────────────────────────────────────────────────────────
        // GARDEN — Bench, Trampoline, Mini-game console, Flowers
        // ───────────────────────────────────────────────────────────────────
        val gardenBounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
        val gardenZones = listOf(
            InteractionZone("garden_bench", ZoneType.GARDEN_BENCH,
                RectF(screenWidth * 0.05f, screenHeight * 0.3f, screenWidth * 0.4f, screenHeight * 0.5f),
                HouseRoom.GARDEN, "sit", true,
                PointF(screenWidth * 0.18f, screenHeight * 0.4f),
                PointF(screenWidth * 0.32f, screenHeight * 0.4f)),
            InteractionZone("trampoline_zone", ZoneType.TRAMPOLINE,
                RectF(screenWidth * 0.5f, screenHeight * 0.25f, screenWidth * 0.95f, screenHeight * 0.55f),
                HouseRoom.GARDEN, "jump", true,
                PointF(screenWidth * 0.72f, screenHeight * 0.4f)),
            InteractionZone("game_console", ZoneType.MINI_GAME_CONSOLE,
                RectF(screenWidth * 0.05f, screenHeight * 0.6f, screenWidth * 0.45f, screenHeight * 0.85f),
                HouseRoom.GARDEN, "play", true,
                PointF(screenWidth * 0.25f, screenHeight * 0.72f),
                PointF(screenWidth * 0.35f, screenHeight * 0.72f)),
            InteractionZone("flower_bed", ZoneType.FLOWER_BED,
                RectF(screenWidth * 0.55f, screenHeight * 0.6f, screenWidth * 0.95f, screenHeight * 0.85f),
                HouseRoom.GARDEN, "garden", false,
                PointF(screenWidth * 0.75f, screenHeight * 0.72f))
        )
        roomLayouts[HouseRoom.GARDEN] = RoomLayout(
            room = HouseRoom.GARDEN,
            bounds = gardenBounds,
            zones = gardenZones,
            backgroundColor = Color.parseColor("#DCEDC8"),
            backgroundResource = "garden_bg",
            suggestedCharacterPositions = listOf(
                PointF(screenWidth * 0.3f, screenHeight * 0.5f),
                PointF(screenWidth * 0.7f, screenHeight * 0.5f)
            )
        )

        // Register all zones
        roomLayouts.values.forEach { layout ->
            layout.zones.forEach { zone ->
                zoneRegistry[zone.id] = zone
            }
        }
    }

    fun getRoomLayout(room: HouseRoom): RoomLayout? = roomLayouts[room]

    fun getAllRoomLayouts(): Map<HouseRoom, RoomLayout> = roomLayouts

    fun getZone(zoneId: String): InteractionZone? = zoneRegistry[zoneId]

    fun getZonesForRoom(room: HouseRoom): List<InteractionZone> {
        return roomLayouts[room]?.zones ?: emptyList()
    }

    fun getZoneAtPosition(position: PointF, room: HouseRoom): InteractionZone? {
        return getZonesForRoom(room).firstOrNull { zone ->
            position.x >= zone.bounds.left && position.x <= zone.bounds.right &&
                position.y >= zone.bounds.top && position.y <= zone.bounds.bottom
        }
    }

    fun setCurrentRoom(room: HouseRoom) {
        currentRoom = room
    }

    fun getCurrentRoom(): HouseRoom = currentRoom

    fun getCharacterStartPositions(room: HouseRoom): Pair<PointF, PointF> {
        val layout = getRoomLayout(room) ?: return Pair(
            PointF(screenWidth * 0.35f, screenHeight * 0.5f),
            PointF(screenWidth * 0.65f, screenHeight * 0.5f)
        )
        return Pair(
            layout.suggestedCharacterPositions.getOrNull(0) ?: PointF(screenWidth * 0.35f, screenHeight * 0.5f),
            layout.suggestedCharacterPositions.getOrNull(1) ?: PointF(screenWidth * 0.65f, screenHeight * 0.5f)
        )
    }

    fun getNearestZone(position: PointF, maxDistance: Float = 100f): InteractionZone? {
        val layouts = roomLayouts.values
        var nearest: InteractionZone? = null
        var minDistance = maxDistance

        for (layout in layouts) {
            for (zone in layout.zones) {
                val dx = position.x - zone.primaryPosition.x
                val dy = position.y - zone.primaryPosition.y
                val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                if (distance < minDistance) {
                    minDistance = distance
                    nearest = zone
                }
            }
        }

        return nearest
    }

    fun isValidPosition(position: PointF, room: HouseRoom): Boolean {
        val layout = getRoomLayout(room) ?: return true
        return position.x >= layout.bounds.left && position.x <= layout.bounds.right &&
            position.y >= layout.bounds.top && position.y <= layout.bounds.bottom
    }
}
