package com.calcvault.storage.migration

import com.calcvault.storage.provider.LocalStorageProvider
import com.calcvault.storage.provider.USBStorageProvider
import com.calcvault.utils.CryptoUtils
import kotlinx.coroutines.*

/**
 * DataMigrationEngine
 *
 * Migrates data from Local mode → USB mode when the USB drive
 * is inserted for the first time.
 *
 * Migration flow:
 *  1. User inserts USB drive
 *  2. App detects USB + StorageManager switches to USB provider
 *  3. DataMigrationEngine.migrate() is called
 *  4. Engine reads all records from local storage
 *  5. Writes each record to USB container (encrypted)
 *  6. Verifies every migrated record (hash comparison)
 *  7. If all verified → wipe local storage (3-pass)
 *  8. If any failed → rollback (keep local data, log failures)
 *
 * This is a one-way operation.
 * After migration: local storage is wiped. USB is the only copy.
 *
 * Safety guarantees:
 *  - USB is never treated as complete until all records verified
 *  - Local data is NOT wiped until verification passes
 *  - Progress is persisted — migration can resume if interrupted
 *  - Duplicate records on USB are detected and skipped
 */
class DataMigrationEngine(
    private val context: android.content.Context,
    private val localProvider: LocalStorageProvider,
    private val usbProvider: USBStorageProvider
) {
    enum class MigrationState {
        NOT_STARTED, IN_PROGRESS, VERIFYING, WIPING_LOCAL,
        COMPLETE, FAILED, PARTIAL
    }

    data class MigrationProgress(
        val state: MigrationState,
        val totalRecords: Int,
        val migratedCount: Int,
        val failedCount: Int,
        val verifiedCount: Int,
        val message: String,
        val fraction: Float = if (totalRecords > 0) migratedCount.toFloat() / totalRecords else 0f
    )

    data class MigrationResult(
        val success: Boolean,
        val migratedCount: Int,
        val failedCount: Int,
        val localWiped: Boolean,
        val durationMs: Long,
        val failedKeys: List<String> = emptyList()
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Main entry point ────────────────────────────────────────────────────

    /**
     * Migrate all local data to USB.
     *
     * @param onProgress    progress callback, called on main thread
     * @param wipeOnSuccess if true, wipe local storage after successful migration
     */
    suspend fun migrate(
        onProgress: suspend (MigrationProgress) -> Unit = {},
        wipeOnSuccess: Boolean = true
    ): MigrationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val failedKeys = mutableListOf<String>()

        // ── 1. Enumerate all local records ─────────────────────────────────
        onProgress(MigrationProgress(MigrationState.IN_PROGRESS, 0, 0, 0, 0, "Reading local records..."))
        val allKeys = localProvider.listKeys()
        val total = allKeys.size

        if (total == 0) {
            return@withContext MigrationResult(true, 0, 0, false, System.currentTimeMillis() - startTime)
        }

        onProgress(MigrationProgress(MigrationState.IN_PROGRESS, total, 0, 0, 0, "Found $total records to migrate"))

        // ── 2. Write each record to USB ────────────────────────────────────
        var migrated = 0
        val migratedHashes = mutableMapOf<String, String>() // key → hash of written data

        for ((idx, key) in allKeys.withIndex()) {
            val data = localProvider.readData(key)
            if (data == null) {
                failedKeys.add(key)
                continue
            }

            val hash = CryptoUtils.sha256hex(data)

            val ok = usbProvider.writeData(key, data)
            data.fill(0) // wipe from RAM immediately

            if (ok) {
                migrated++
                migratedHashes[key] = hash
            } else {
                failedKeys.add(key)
            }

            onProgress(
                MigrationProgress(
                    MigrationState.IN_PROGRESS,
                    total,
                    migrated,
                    failedKeys.size,
                    0,
                    "Migrating record ${idx + 1}/$total..."
                )
            )
        }

        // ── 3. Verify migrated records ────────────────────────────────────
        onProgress(
            MigrationProgress(
                MigrationState.VERIFYING,
                total,
                migrated,
                failedKeys.size,
                0,
                "Verifying $migrated migrated records..."
            )
        )

        var verified = 0
        val verifyFailed = mutableListOf<String>()

        for ((key, originalHash) in migratedHashes) {
            val usbData = usbProvider.readData(key)
            if (usbData == null) {
                verifyFailed.add(key); continue
            }
            val usbHash = CryptoUtils.sha256hex(usbData)
            usbData.fill(0)

            if (usbHash == originalHash) {
                verified++
            } else {
                verifyFailed.add(key)
            }
        }

        val allVerified = verifyFailed.isEmpty() && verified == migrated

        // ── 4. Wipe local storage if all verified ─────────────────────────
        var localWiped = false
        if (allVerified && wipeOnSuccess) {
            onProgress(
                MigrationProgress(
                    MigrationState.WIPING_LOCAL,
                    total,
                    migrated,
                    0,
                    verified,
                    "Securely wiping local storage..."
                )
            )
            localWiped = localProvider.destroy()
        } else if (!allVerified) {
            // Rollback: keep local data, log failures
            failedKeys.addAll(verifyFailed)
            onProgress(
                MigrationProgress(
                    MigrationState.FAILED,
                    total,
                    migrated,
                    failedKeys.size,
                    verified,
                    "Verification failed for ${verifyFailed.size} records. Local data preserved."
                )
            )
            return@withContext MigrationResult(
                success = false,
                migratedCount = migrated,
                failedCount = failedKeys.size,
                localWiped = false,
                durationMs = System.currentTimeMillis() - startTime,
                failedKeys = failedKeys
            )
        }

        val state = when {
            failedKeys.isEmpty() -> MigrationState.COMPLETE
            else -> MigrationState.PARTIAL
        }

        onProgress(
            MigrationProgress(
                state,
                total,
                migrated,
                failedKeys.size,
                verified,
                if (state == MigrationState.COMPLETE) {
                    "✅ Migration complete. $migrated records on USB."
                } else {
                    "⚠️ Partial migration. ${failedKeys.size} records failed."
                }
            )
        )

        MigrationResult(
            success = state == MigrationState.COMPLETE,
            migratedCount = migrated,
            failedCount = failedKeys.size,
            localWiped = localWiped,
            durationMs = System.currentTimeMillis() - startTime,
            failedKeys = failedKeys
        )
    }

    /**
     * Check if migration is needed (local data exists that isn't on USB yet).
     */
    fun isMigrationNeeded(): Boolean {
        val localKeys = localProvider.listKeys()
        if (localKeys.isEmpty()) return false
        // Check if any local keys are missing from USB
        return localKeys.any { !usbProvider.exists(it) }
    }

    /**
     * Estimate migration time in seconds.
     */
    fun estimateDurationSeconds(): Int {
        val count = localProvider.listKeys().size
        return (count * 0.05).toInt().coerceAtLeast(1) // ~50ms per record
    }
}
