package com.calcvault.auth

import android.util.Log

/**
 * VaultDestructionBus
 *
 * Provides a global registry for vault destruction events.
 */
object VaultDestructionBus {

    private val handlers = mutableListOf<() -> Unit>()

    /**
     * Registers a callback for vault destruction events.
     */
    fun register(handler: () -> Unit) {
        synchronized(handlers) {
            handlers.add(handler)
        }
    }

    /**
     * Triggers a vault destruction event.
     */
    fun trigger() {
        Log.e("VaultDestructionBus", "=== CRITICAL: VAULT DESTRUCTION TRIGGERED ===")
        synchronized(handlers) {
            handlers.forEach { handler ->
                try {
                    handler.invoke()
                } catch (e: Exception) {
                    Log.e("VaultDestructionBus", "Error in vault destruction handler")
                }
            }
        }
    }
}
