package com.calcvault.house

import android.graphics.PointF
import android.view.MotionEvent
import java.util.UUID

/**
 * DragDropInteractionEngine.kt - Handle user drag, drop, and tap interactions
 *
 * Features:
 * - Drag character to new location
 * - Drop-zone detection
 * - Proximity-based attraction
 * - Tap-to-command
 * - Long-press menus
 */

data class DragState(
    val isDragging: Boolean = false,
    val draggedCharacter: CharacterIdentity? = null,
    val dragStartPosition: PointF = PointF(),
    val dragCurrentPosition: PointF = PointF(),
    val dragStartTime: Long = 0L
)

data class DropResult(
    val character: CharacterIdentity,
    val resultPosition: PointF,
    val detectedZone: InteractionZone?,
    val shouldTriggerAction: Boolean,
    val generatedCommand: CharacterCommand?
)

class DragDropInteractionEngine(
    private val environment: HouseEnvironment,
    private val behaviorEngine: CharacterBehaviorEngine
) {
    private var dragState = DragState()
    private val commandQueue = mutableListOf<CharacterCommand>()
    private var onCommandGenerated: (CharacterCommand) -> Unit = {}
    private var onDropResult: (DropResult) -> Unit = {}

    companion object {
        const val DRAG_THRESHOLD = 10f // Pixels to recognize drag
        const val LONG_PRESS_DURATION = 500L // Milliseconds
        const val ZONE_ATTRACTION_RADIUS = 80f // Pixels
    }

    fun handleTouchEvent(
        event: MotionEvent,
        zainPos: PointF,
        sanuPos: PointF
    ): Boolean {
        val x = event.x
        val y = event.y
        val touchPoint = PointF(x, y)

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> handleDown(touchPoint, zainPos, sanuPos)
            MotionEvent.ACTION_MOVE -> handleMove(touchPoint)
            MotionEvent.ACTION_UP -> handleUp(touchPoint, zainPos, sanuPos)
            else -> false
        }
    }

    private fun handleDown(
        point: PointF,
        zainPos: PointF,
        sanuPos: PointF
    ): Boolean {
        // Check if touching a character
        val touchRadius = 60f

        val touchesZain = distance(point, zainPos) < touchRadius
        val touchesSanu = distance(point, sanuPos) < touchRadius

        if (touchesZain) {
            dragState = DragState(
                isDragging = true,
                draggedCharacter = CharacterIdentity.ZAIN,
                dragStartPosition = point,
                dragCurrentPosition = point,
                dragStartTime = System.currentTimeMillis()
            )
            return true
        }

        if (touchesSanu) {
            dragState = DragState(
                isDragging = true,
                draggedCharacter = CharacterIdentity.SANU,
                dragStartPosition = point,
                dragCurrentPosition = point,
                dragStartTime = System.currentTimeMillis()
            )
            return true
        }

        return false
    }

    private fun handleMove(point: PointF): Boolean {
        if (!dragState.isDragging) return false

        dragState = dragState.copy(dragCurrentPosition = point)
        return true
    }

    private fun handleUp(
        point: PointF,
        zainPos: PointF,
        sanuPos: PointF
    ): Boolean {
        if (!dragState.isDragging || dragState.draggedCharacter == null) {
            dragState = DragState()
            return false
        }

        val dragDistance = distance(dragState.dragStartPosition, point)
        val dragDuration = System.currentTimeMillis() - dragState.dragStartTime

        val result = if (dragDistance > DRAG_THRESHOLD) {
            // Was a drag operation
            processDrop(point, dragState.draggedCharacter!!)
        } else if (dragDuration > LONG_PRESS_DURATION) {
            // Was a long press - show context menu
            processLongPress(dragState.draggedCharacter!!)
            null
        } else {
            // Was a simple tap
            processCommandTap(dragState.draggedCharacter!!)
            null
        }

        dragState = DragState()

        if (result != null) {
            onDropResult(result)
        }

        return true
    }

    private fun processDrop(
        dropPoint: PointF,
        character: CharacterIdentity
    ): DropResult {
        val room = environment.getCurrentRoom()

        // Clamp to valid bounds
        val resultPosition = clampToRoom(dropPoint, room)

        // Find nearest zone with attraction
        val nearestZone = environment.getNearestZone(resultPosition, ZONE_ATTRACTION_RADIUS)

        // If found zone, snap to it
        val finalPosition = nearestZone?.primaryPosition ?: resultPosition

        // Check for character overlap and apply repulsion if needed
        val otherCharacter = if (character == CharacterIdentity.ZAIN) CharacterIdentity.SANU else CharacterIdentity.ZAIN
        val overlapAdjustment = checkAndApplyOverlapPrevention(finalPosition, otherCharacter)
        val adjustedPosition = if (overlapAdjustment != null) overlapAdjustment else finalPosition

        // Apply attraction physics towards zone if it exists
        val physicsFinalPosition = if (nearestZone != null) {
            applyAttractionPhysics(adjustedPosition, nearestZone.primaryPosition, 0.8f)
        } else {
            adjustedPosition
        }

        // Generate movement command
        val moveCommand = CharacterCommand(
            id = java.util.UUID.randomUUID().toString(),
            actor = character,
            actionType = ActionType.GO_TO_ZONE,
            targetZone = nearestZone,
            targetLocation = physicsFinalPosition,
            targetRoom = room,
            duration = calculateWalkDuration(physicsFinalPosition),
            timestamp = System.currentTimeMillis()
        )

        onCommandGenerated(moveCommand)

        // If zone has action, generate action command
        val actionCommand = if (nearestZone != null) {
            CharacterCommand(
                id = java.util.UUID.randomUUID().toString(),
                actor = character,
                actionType = ActionType.DO_ACTION,
                targetZone = nearestZone,
                parameter = nearestZone.animationAction,
                timestamp = System.currentTimeMillis() + 500L // Delay action after arrival
            )
        } else {
            null
        }

        if (actionCommand != null) {
            onCommandGenerated(actionCommand)
        }

        return DropResult(
            character = character,
            resultPosition = physicsFinalPosition,
            detectedZone = nearestZone,
            shouldTriggerAction = nearestZone != null,
            generatedCommand = moveCommand
        )
    }

    private fun processLongPress(character: CharacterIdentity) {
        // Show context menu with quick actions for the character
        val quickActions = listOf(
            "Sit" to ActionType.DO_ACTION,
            "Play" to ActionType.DO_ACTION,
            "Sleep" to ActionType.DO_ACTION,
            "Talk" to ActionType.TALK_TO_PARTNER,
            "Dance" to ActionType.DO_ACTION
        )
        // In a real implementation, this would trigger UI menu display
        // For now, generate a talk command as default action
        val talkCommand = CharacterCommand(
            id = java.util.UUID.randomUUID().toString(),
            actor = character,
            actionType = ActionType.TALK_TO_PARTNER,
            timestamp = System.currentTimeMillis()
        )
        onCommandGenerated(talkCommand)
    }

    private fun processCommandTap(character: CharacterIdentity) {
        // Simple tap triggers idle animation or brief interaction
        val tapCommand = CharacterCommand(
            id = java.util.UUID.randomUUID().toString(),
            actor = character,
            actionType = ActionType.DO_ACTION,
            parameter = "tap_reaction",
            timestamp = System.currentTimeMillis()
        )
        onCommandGenerated(tapCommand)
    }

    private fun checkAndApplyOverlapPrevention(position: PointF, otherCharacter: CharacterIdentity): PointF? {
        // Check if position is too close to other character and adjust if needed
        val minDistance = 100f // Minimum distance between characters
        val otherPos = position // Would get actual position from state in real impl
        val currentDist = distance(position, otherPos)

        return if (currentDist < minDistance) {
            // Repel away from other character
            val dx = position.x - otherPos.x
            val dy = position.y - otherPos.y
            val angle = kotlin.math.atan2(dy, dx)
            val repelDistance = minDistance - currentDist + 10f
            PointF(
                position.x + kotlin.math.cos(angle.toDouble()).toFloat() * repelDistance,
                position.y + kotlin.math.sin(angle.toDouble()).toFloat() * repelDistance
            )
        } else {
            null
        }
    }

    private fun applyAttractionPhysics(from: PointF, to: PointF, attractionStrength: Float): PointF {
        // Apply smooth attraction towards target position
        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = distance(from, to)

        return if (distance > 1f) {
            val normalized = distance(PointF(1f, 0f), PointF(1f + dx / distance, dy / distance))
            PointF(
                from.x + (dx / distance) * attractionStrength * 5f,
                from.y + (dy / distance) * attractionStrength * 5f
            )
        } else {
            to
        }
    }

    fun queueCommand(command: CharacterCommand) {
        commandQueue.add(command)
        onCommandGenerated(command)
    }

    fun getNextCommand(): CharacterCommand? {
        return if (commandQueue.isNotEmpty()) commandQueue.removeAt(0) else null
    }

    fun getPendingCommands(): List<CharacterCommand> = commandQueue.toList()

    fun clearQueue() {
        commandQueue.clear()
    }

    fun setOnCommandGenerated(callback: (CharacterCommand) -> Unit) {
        onCommandGenerated = callback
    }

    fun setOnDropResult(callback: (DropResult) -> Unit) {
        onDropResult = callback
    }

    fun getDragState(): DragState = dragState

    fun isDragging(): Boolean = dragState.isDragging

    // Helpers

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun clampToRoom(position: PointF, room: HouseRoom): PointF {
        val layout = environment.getRoomLayout(room) ?: return position
        return PointF(
            position.x.coerceIn(layout.bounds.left + 50, layout.bounds.right - 50),
            position.y.coerceIn(layout.bounds.top + 50, layout.bounds.bottom - 50)
        )
    }

    private fun calculateWalkDuration(targetPosition: PointF): Long {
        // Rough estimate: 500ms + 200ms per 100 pixels
        return 500L + (50L).toLong()
    }
}

/**
 * CommandExecutor.kt - Execute queued commands and manage state
 */
class CommandExecutor(
    private val environment: HouseEnvironment,
    private val behaviorEngine: CharacterBehaviorEngine
) {
    private val executionQueue = mutableListOf<CharacterCommand>()
    private val executingCommands = mutableMapOf<String, Long>() // ID -> start time

    fun executeCommand(
        command: CharacterCommand,
        characterState: CharacterState
    ): CharacterState {
        executingCommands[command.id] = System.currentTimeMillis()
        return behaviorEngine.applyCommand(characterState, command)
    }

    fun updateExecutingCommands(
        zainState: CharacterState,
        sanuState: CharacterState,
        deltaTime: Long
    ): Pair<CharacterState, CharacterState> {
        // Check if any commands have completed
        val completedIds = mutableSetOf<String>()

        executingCommands.forEach { (id, startTime) ->
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 2000L) { // Commands default to 2s if not specified
                completedIds.add(id)
            }
        }

        completedIds.forEach { executingCommands.remove(it) }

        return Pair(zainState, sanuState)
    }

    fun queueCommand(command: CharacterCommand) {
        executionQueue.add(command)
    }

    fun getNextCommand(): CharacterCommand? {
        return if (executionQueue.isNotEmpty()) executionQueue.removeAt(0) else null
    }

    fun getExecutingCommands(): Map<String, Long> = executingCommands.toMap()

    fun cancelCommand(commandId: String) {
        executingCommands.remove(commandId)
        executionQueue.removeAll { it.id == commandId }
    }

    fun clear() {
        executionQueue.clear()
        executingCommands.clear()
    }
}
