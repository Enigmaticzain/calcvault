package com.calcvault.ui.keyboard

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.calcvault.emotional.ThemeEngine
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ThemeSynchronizationBridge - Keeps keyboard theme in sync with app theme
 *
 * This bridge establishes a reactive connection between the app's ThemeEngine
 * and the keyboard system, ensuring the keyboard instantly adapts to theme changes
 * without requiring restart or flicker.
 *
 * Features:
 * - Real-time theme change detection
 * - Debounced theme updates (prevents rapid flicker)
 * - Lifecycle-aware subscription
 * - Thread-safe color updates
 * - Mode-aware theme generation
 */
class ThemeSynchronizationBridge(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val sessionManager: SessionManager
) {
    private val themeEngine = ThemeEngine(activity)

    // Theme state - observed by keyboard views
    private val _currentTheme = MutableStateFlow<KeyboardTheme?>(null)
    val currentTheme: StateFlow<KeyboardTheme?> = _currentTheme

    // App theme state
    private val _appTheme = MutableStateFlow<ThemeEngine.CalcVaultTheme?>(null)
    val appTheme: StateFlow<ThemeEngine.CalcVaultTheme?> = _appTheme

    // Keyboard mode
    private val _keyboardMode = MutableStateFlow(KeyboardMode.EXPRESSIVE)
    val keyboardMode: StateFlow<KeyboardMode> = _keyboardMode

    // Listeners for theme changes
    private val themeChangeListeners = mutableSetOf<ThemeChangeListener>()

    // Synchronization state
    private val isSynced = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private val lastThemeHash = AtomicReference<String?>(null)

    // Debounce handler
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingThemeUpdate: Runnable? = null
    private val debounceDelayMs = 200L // Prevents flicker from rapid theme changes

    /**
     * Initialize sync bridge - starts listening for theme changes
     */
    fun initialize() {
        if (isInitialized.getAndSet(true)) {
            return // Already initialized
        }

        lifecycleOwner.lifecycleScope.launch {
            startThemeMonitoring()
            applyCurrentTheme()
        }
    }

    /**
     * Start monitoring theme changes from SessionManager
     */
    private suspend fun startThemeMonitoring() {
        try {
            // Initial theme load
            val initialTheme = themeEngine.getCurrentTheme()
            updateTheme(initialTheme)

            // Monitor for theme changes by polling activeAnimatedTheme
            // In a production system, you might want to use a StateFlow from SessionManager
            startPeriodicThemeCheck()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Periodic check for theme changes (every 500ms)
     */
    private fun startPeriodicThemeCheck() {
        debounceHandler.post(object : Runnable {
            override fun run() {
                try {
                    val appTheme = themeEngine.getCurrentTheme()

                    // Check if theme actually changed (using hash comparison)
                    val themeHash = appTheme.hashCode().toString()
                    if (themeHash != lastThemeHash.get()) {
                        lastThemeHash.set(themeHash)
                        scheduleThemeUpdate(appTheme)
                    }

                    debounceHandler.postDelayed(this, 500)
                } catch (e: Exception) {
                    e.printStackTrace()
                    debounceHandler.postDelayed(this, 500)
                }
            }
        })
    }

    /**
     * Schedule theme update with debounce
     */
    private fun scheduleThemeUpdate(appTheme: ThemeEngine.CalcVaultTheme) {
        // Cancel pending update
        pendingThemeUpdate?.let { debounceHandler.removeCallbacks(it) }

        // Schedule new update
        pendingThemeUpdate = Runnable {
            updateTheme(appTheme)
        }

        debounceHandler.postDelayed(pendingThemeUpdate!!, debounceDelayMs)
    }

    /**
     * Update the keyboard theme from app theme
     */
    private fun updateTheme(appTheme: ThemeEngine.CalcVaultTheme) {
        try {
            _appTheme.value = appTheme

            // Generate keyboard theme from app theme
            val keyboardTheme = KeyboardThemeGenerator.fromAppTheme(
                appTheme,
                _keyboardMode.value
            )

            _currentTheme.value = keyboardTheme
            isSynced.set(true)

            // Notify listeners
            notifyThemeChanged(keyboardTheme, appTheme)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Apply current theme immediately
     */
    private suspend fun applyCurrentTheme() {
        try {
            val appTheme = themeEngine.getCurrentTheme()
            val keyboardTheme = KeyboardThemeGenerator.fromAppTheme(
                appTheme,
                _keyboardMode.value
            )

            _appTheme.value = appTheme
            _currentTheme.value = keyboardTheme
            isSynced.set(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Change keyboard mode and regenerate theme
     */
    fun setKeyboardMode(mode: KeyboardMode) {
        _keyboardMode.value = mode

        // Regenerate theme with new mode
        _appTheme.value?.let { appTheme ->
            val newTheme = KeyboardThemeGenerator.fromAppTheme(appTheme, mode)
            _currentTheme.value = newTheme
            notifyThemeChanged(newTheme, appTheme)
        }
    }

    /**
     * Force refresh theme from SessionManager
     */
    fun forceRefresh() {
        try {
            val appTheme = themeEngine.getCurrentTheme()
            updateTheme(appTheme)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get current keyboard theme
     */
    fun getCurrentTheme(): KeyboardTheme? = _currentTheme.value

    /**
     * Check if sync is active
     */
    fun isSynchronized(): Boolean = isSynced.get()

    /**
     * Add theme change listener
     */
    fun addThemeChangeListener(listener: ThemeChangeListener) {
        themeChangeListeners.add(listener)
    }

    /**
     * Remove theme change listener
     */
    fun removeThemeChangeListener(listener: ThemeChangeListener) {
        themeChangeListeners.remove(listener)
    }

    /**
     * Notify all listeners of theme change
     */
    private fun notifyThemeChanged(keyboardTheme: KeyboardTheme, appTheme: ThemeEngine.CalcVaultTheme) {
        lifecycleOwner.lifecycleScope.launch {
            themeChangeListeners.forEach { listener ->
                try {
                    listener.onThemeChanged(keyboardTheme, appTheme)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        debounceHandler.removeCallbacksAndMessages(null)
        themeChangeListeners.clear()
        _currentTheme.value = null
        _appTheme.value = null
    }
}

/**
 * Listen for keyboard theme changes
 */
interface ThemeChangeListener {
    /**
     * Called when theme changes
     */
    fun onThemeChanged(keyboardTheme: KeyboardTheme, appTheme: ThemeEngine.CalcVaultTheme)
}

/**
 * Keyboard theme cache - prevents redundant theme generation
 */
object KeyboardThemeCache {
    private val themeCache = mutableMapOf<String, KeyboardTheme>()
    private val cacheExpiryMs = 5000L // 5 second cache
    private val cacheTimestamps = mutableMapOf<String, Long>()

    /**
     * Get or generate cached theme
     */
    fun getOrGenerate(
        appTheme: ThemeEngine.CalcVaultTheme,
        mode: KeyboardMode
    ): KeyboardTheme {
        val key = "${appTheme.type}_$mode"
        val now = System.currentTimeMillis()

        // Check if cached and not expired
        val cached = themeCache[key]
        val timestamp = cacheTimestamps[key] ?: 0

        if (cached != null && (now - timestamp) < cacheExpiryMs) {
            return cached
        }

        // Generate new theme
        val theme = KeyboardThemeGenerator.fromAppTheme(appTheme, mode)
        themeCache[key] = theme
        cacheTimestamps[key] = now

        return theme
    }

    /**
     * Clear cache
     */
    fun clear() {
        themeCache.clear()
        cacheTimestamps.clear()
    }
}

/**
 * ThemeSynchronizationMonitor - Diagnostic tool for debugging theme sync
 */
object ThemeSynchronizationMonitor {
    private val syncEvents = mutableListOf<SyncEvent>()
    private val maxEvents = 50

    data class SyncEvent(
        val timestamp: Long,
        val eventType: String,
        val details: String
    )

    fun recordEvent(type: String, details: String) {
        val event = SyncEvent(
            timestamp = System.currentTimeMillis(),
            eventType = type,
            details = details
        )

        syncEvents.add(event)
        if (syncEvents.size > maxEvents) {
            syncEvents.removeAt(0)
        }
    }

    fun getEvents(): List<SyncEvent> = syncEvents.toList()

    fun clear() {
        syncEvents.clear()
    }

    fun printLog() {
        println("=== Keyboard Theme Sync Log ===")
        syncEvents.forEach { event ->
            println("[${event.timestamp}] ${event.eventType}: ${event.details}")
        }
    }
}
