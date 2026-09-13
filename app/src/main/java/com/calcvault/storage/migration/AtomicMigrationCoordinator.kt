package com.calcvault.storage.migration

import android.content.Context
import com.calcvault.storage.provider.StorageManager
import com.calcvault.storage.provider.LocalStorageProvider
import com.calcvault.storage.provider.USBStorageProvider
import com.calcvault.storage.provider.LocalKeyStore
import java.io.File

/**
 * AtomicMigrationCoordinator
 *
 * Fixes the atomicity gap in DataMigrationEngine:
 * the old version switched StorageManager to USB provider BEFORE
 * migration was verified complete.
 *
 * Correct atomic sequence:
 *  1. Keep StorageManager pointing at LOCAL provider
 *  2. Open USB provider in READ-WRITE mode (but don't activate it yet)
 *  3. Run full migration: copy all records to USB
 *  4. Verify every record on USB matches local hash
 *  5. ONLY after 100% verification → atomically switch StorageManager pointer
 *  6. Wipe local storage
 *  7. If any step fails → USB provider closed, StorageManager unchanged
 *
 * After switch:
 *  - New writes go to USB
 *  - Old local data wiped (3-pass)
 *  - LocalKeyStore salt destroyed
 */
class AtomicMigrationCoordinator(
    private val context: Context,
    private val usbRoot: File,
    private val masterKey: ByteArray,
    private val macKey: ByteArray
) {
    enum class CoordinatorState {
        IDLE, COPYING, VERIFYING, SWITCHING, WIPING, DONE, FAILED
    }

    data class CoordinatorResult(
        val success: Boolean,
        val recordsMigrated: Int,
        val localWiped: Boolean,
        val failureReason: String = ""
    )

    suspend fun run(
        onState: (CoordinatorState, String) -> Unit = { _, _ -> }
    ): CoordinatorResult {
        // ── 1. Get current local provider ─────────────────────────────────
        val localProvider = StorageManager.provider as? LocalStorageProvider
            ?: return CoordinatorResult(false, 0, false, "Not in local mode")

        // ── 2. Open USB provider (not yet active in StorageManager) ────────
        onState(CoordinatorState.COPYING, "Opening USB provider...")
        val usbProvider = USBStorageProvider(context, usbRoot, masterKey, macKey)
        if (!usbProvider.open()) {
            return CoordinatorResult(false, 0, false, "USB container failed to open")
        }

        // ── 3. Run migration using the two providers directly ──────────────
        onState(CoordinatorState.COPYING, "Copying records to USB...")
        val engine = DataMigrationEngine(context, localProvider, usbProvider)

        if (!engine.isMigrationNeeded()) {
            // Nothing to migrate — just switch
            return atomicSwitch(usbProvider, localProvider, 0, onState)
        }

        val result = engine.migrate(
            onProgress = { p -> onState(CoordinatorState.COPYING, p.message) },
            wipeOnSuccess = false // don't wipe yet — we do it after switch
        )

        if (!result.success) {
            usbProvider.close()
            return CoordinatorResult(
                false,
                result.migratedCount,
                false,
                "Migration failed: ${result.failedKeys.size} records couldn't be verified"
            )
        }

        // ── 4. Verification passed — atomically switch StorageManager ──────
        return atomicSwitch(usbProvider, localProvider, result.migratedCount, onState)
    }

    private fun atomicSwitch(
        usbProvider: USBStorageProvider,
        localProvider: LocalStorageProvider,
        migrated: Int,
        onState: (CoordinatorState, String) -> Unit
    ): CoordinatorResult {
        onState(CoordinatorState.SWITCHING, "Switching to USB...")

        // THE ATOMIC SWITCH — StorageManager now points to USB
        StorageManager.switchToUSB(usbRoot)

        onState(CoordinatorState.WIPING, "Wiping local storage...")

        // Wipe local storage — this can't break anything because USB is now active
        val wiped = localProvider.destroy()

        // Destroy local key salt — local vault is now permanently unreadable
        LocalKeyStore(context).destroy()

        onState(CoordinatorState.DONE, "Migration complete ✅")

        return CoordinatorResult(
            success = true,
            recordsMigrated = migrated,
            localWiped = wiped
        )
    }
}
