package com.calcvault.utils

/**
 * SessionManager
 *
 * Singleton holding all live session state in RAM.
 * Cleared on lock, USB removal, or process death.
 */
object SessionManager {

    // ─── Identity (loaded from USB on unlock) ─────────────────────────────
    var localUserId: String = ""
    var localDeviceId: String = ""
    var partnerUserId: String = ""

    // Nicknames
    var localNickname: String = ""
    var partnerNickname: String = ""

    // Wallpaper
    var chatWallpaperPath: String = ""

    // ─── Signaling config ─────────────────────────────────────────────────
    var signalingUrl: String = ""
    var internetOnlyMode: Boolean = true

    // ─── Bluetooth pairing address ────────────────────────────────────────
    var partnerBtAddress: String = ""

    // ─── Session flags ────────────────────────────────────────────────────
    var isVaultOpen: Boolean = false
    var isDecoyMode: Boolean = false
    var usbMountPath: String = ""

    // ─── Runtime preferences ──────────────────────────────────────────────
    var inactivityTimeoutMs: Long = 5 * 60 * 1000L
    var animationMode: String = "FULL"
    var themeType: String = "DARK"
    var isAdaptiveTheme: Boolean = true
    var isBiometricEnabled: Boolean = true
    var isAmbientEnabled: Boolean = true
    var isWordEffectsEnabled: Boolean = true
    var isCareDropsEnabled: Boolean = true
    var isMoodUiAdaptationEnabled: Boolean = true
    var isDoraemon3DEnabled: Boolean = true
    var defaultNotificationSound: String = "default"
    var defaultVibrationPattern: String = "default"
    var urgentNotificationSound: String = "default"
    var urgentVibrationPattern: String = "urgent_wave"

    // Animated Theme Choice (V6)
    var activeAnimatedTheme: String = "NONE" // NONE, NIGHT_SKY, DORAEMON, NATURE

    // Theme Sync
    var isThemeSyncEnabled: Boolean = true

    // Persistent data placeholders
    var lastSyncTimestamp: Long = 0L
    var recoveryKey: String = ""

    // ─── Clear everything on lock ─────────────────────────────────────────
    fun clear() {
        localUserId = ""
        localDeviceId = ""
        partnerUserId = ""
        localNickname = ""
        partnerNickname = ""
        chatWallpaperPath = ""
        signalingUrl = ""
        internetOnlyMode = true
        partnerBtAddress = ""
        isVaultOpen = false
        isDecoyMode = false
        usbMountPath = ""
        inactivityTimeoutMs = 5 * 60 * 1000L
        animationMode = "FULL"
        themeType = "DARK"
        isAdaptiveTheme = true
        isBiometricEnabled = true
        isAmbientEnabled = true
        isWordEffectsEnabled = true
        isCareDropsEnabled = true
        isMoodUiAdaptationEnabled = true
        isDoraemon3DEnabled = true
        defaultNotificationSound = "default"
        defaultVibrationPattern = "default"
        urgentNotificationSound = "default"
        urgentVibrationPattern = "urgent_wave"
        activeAnimatedTheme = "NONE"
        isThemeSyncEnabled = true
        lastSyncTimestamp = 0L
        recoveryKey = ""
    }

    fun isReady(): Boolean = isVaultOpen && localUserId.isNotBlank()
}
