package com.calcvault.security

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.calcvault.storage.USBStorageEngine

/**
 * AutoLockManager
 *
 * Single place for ALL lock triggers.
 *
 * Triggers:
 *  1. Inactivity timeout (default 5 min, configurable)
 *  2. USB removal (immediate)
 *  3. App minimized (immediate)
 *  4. Explicit lock call
 *  5. Security threat detected
 *
 * On lock:
 *  → flushes USB writes
 *  → clears session key from RAM
 *  → kills vault UI
 *  → returns to calculator
 */
class AutoLockManager(
    private val context: Context,
    private val engine: USBStorageEngine
) {
    private val handler = Handler(Looper.getMainLooper())
    private var inactivityMs = 5 * 60 * 1000L // 5 minutes default
    private var lockCallback: (() -> Unit)? = null

    private val inactivityRunnable = Runnable { triggerLock(LockReason.INACTIVITY) }

    enum class LockReason {
        INACTIVITY, USB_REMOVED, APP_MINIMIZED, MANUAL, SECURITY_THREAT
    }

    fun setLockCallback(cb: () -> Unit) { lockCallback = cb }

    fun setInactivityTimeout(ms: Long) { inactivityMs = ms }

    /** Call on every user interaction to reset inactivity timer */
    fun onUserInteraction() {
        handler.removeCallbacks(inactivityRunnable)
        handler.postDelayed(inactivityRunnable, inactivityMs)
    }

    /** Start the inactivity timer */
    fun startInactivityTimer() {
        handler.postDelayed(inactivityRunnable, inactivityMs)
    }

    /** Cancel all timers */
    fun cancelTimers() {
        handler.removeCallbacks(inactivityRunnable)
    }

    /** Trigger lock immediately */
    fun triggerLock(reason: LockReason) {
        cancelTimers()
        engine.lock()
        lockCallback?.invoke()
    }
}
