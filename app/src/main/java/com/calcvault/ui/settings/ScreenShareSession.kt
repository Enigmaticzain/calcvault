package com.calcvault.call

import android.graphics.Point

enum class ShareMode {
    FULL,
    APP_ONLY
}

enum class ScreenShareState {
    REQUESTED,
    ACTIVE,
    PAUSED,
    ENDED
}

data class WatchIndicator(
    val isActive: Boolean,
    val position: Point,
    val isDraggable: Boolean
)

data class ScreenShareSession(
    val sessionId: String,
    val host: String,
    val viewer: String,
    var isActive: Boolean,
    val mode: ShareMode,
    var state: ScreenShareState = ScreenShareState.REQUESTED
)
