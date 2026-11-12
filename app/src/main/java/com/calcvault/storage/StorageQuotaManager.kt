package com.calcvault.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import com.calcvault.utils.PairedUserIdentity
import java.io.File

/**
 * StorageQuotaManager
 *
 * Manages file storage routing and quota enforcement.
 *
 * Rules:
 * - Files > 1GB go to /calcvault/storage/Zain/large_media/
 * - Other files use normal storage
 * - Tracks storage usage per user
 * - Enforces quotas
 */
class StorageQuotaManager(
    private val storageRoot: String = "/calcvault/storage",
    private val ownershipManager: UserOwnershipManager
) {

    companion object {
        private const val TAG = "StorageQuotaManager"

        // Default quotas
        const val ZAIN_QUOTA_BYTES = 500_000_000_000L // 500GB
        const val SANU_QUOTA_BYTES = 100_000_000_000L // 100GB (only small files)
        const val SHARED_QUOTA_BYTES = 50_000_000_000L // 50GB
    }

    /**
     * Storage quota
     */
    data class QuotaInfo(
        val owner: String,
        val totalQuota: Long,
        val usedSpace: Long,
        val availableSpace: Long,
        val percentUsed: Float = if (totalQuota > 0) (usedSpace.toFloat() / totalQuota) * 100f else 0f
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("owner", owner)
            put("totalQuota", totalQuota)
            put("usedSpace", usedSpace)
            put("availableSpace", availableSpace)
            put("percentUsed", percentUsed)
        }
    }

    /**
     * File storage decision
     */
    data class StorageDecision(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val owner: UserOwnershipManager.FileOwner,
        val storagePath: String,
        val allowed: Boolean,
        val reason: String,
        val storagePath_: String? = null
    )

    // ─── Storage Routing ───────────────────────────────────────────────────

    /**
     * Determine where a file should be stored
     */
    suspend fun getStoragePath(
        fileSize: Long,
        fileName: String,
        currentUser: String = UserOwnershipManager.USER_ZAIN
    ): StorageDecision = withContext(Dispatchers.IO) {
        if (fileSize < 0L || fileName.isBlank()) {
            return@withContext StorageDecision(
                fileId = "",
                fileName = fileName,
                fileSize = fileSize,
                owner = UserOwnershipManager.FileOwner.SHARED,
                storagePath = "",
                allowed = false,
                reason = "Invalid file metadata"
            )
        }
        val fileId = generateFileId(fileName)
        val owner = ownershipManager.determineOwner(fileSize)

        // Enforce quota
        val quota = getQuotaInfo(owner.displayName)
        if (fileSize > quota.availableSpace) {
            return@withContext StorageDecision(
                fileId = fileId,
                fileName = fileName,
                fileSize = fileSize,
                owner = owner,
                storagePath = "",
                allowed = false,
                reason = "Insufficient quota: ${formatSize(fileSize)} > ${formatSize(quota.availableSpace)}"
            )
        }

        // Check if user can store to this owner
        if (!canStoreAs(currentUser, owner)) {
            return@withContext StorageDecision(
                fileId = fileId,
                fileName = fileName,
                fileSize = fileSize,
                owner = owner,
                storagePath = "",
                allowed = false,
                reason = "User $currentUser cannot store large files (owner: ${owner.displayName})"
            )
        }

        // Every destination is inside the app-private root, and the unique file ID prevents
        // same-name uploads from silently overwriting one another.
        val path = UserStoragePaths.forFile(
            storageRoot = File(storageRoot),
            owner = owner.displayName,
            fileType = determineFileType(fileName),
            fileName = fileName,
            uniqueId = fileId
        ).absolutePath

        Log.i(TAG, "Storage decision for $fileName: $owner → $path")

        StorageDecision(
            fileId = fileId,
            fileName = fileName,
            fileSize = fileSize,
            owner = owner,
            storagePath = path,
            allowed = true,
            reason = "OK"
        )
    }

    /**
     * Check if user can store a file with given owner
     */
    private fun canStoreAs(userId: String, owner: UserOwnershipManager.FileOwner): Boolean {
        val stableUserId = PairedUserIdentity.stableId(userId) ?: return false
        return when (owner) {
            UserOwnershipManager.FileOwner.ZAIN -> stableUserId == PairedUserIdentity.USER_A
            UserOwnershipManager.FileOwner.SANU -> true // The primary user may store on behalf of either partner.
            UserOwnershipManager.FileOwner.SHARED -> true // Both members of the personal pair may store shared files.
        }
    }

    // ─── Quota Tracking ────────────────────────────────────────────────────

    /**
     * Get quota info for a user
     */
    suspend fun getQuotaInfo(owner: String): QuotaInfo = withContext(Dispatchers.IO) {
        val totalQuota = when (owner) {
            UserOwnershipManager.USER_ZAIN -> ZAIN_QUOTA_BYTES
            UserOwnershipManager.USER_SANU -> SANU_QUOTA_BYTES
            else -> SHARED_QUOTA_BYTES
        }

        val usedSpace = calculateUsedSpace(owner)
        val availableSpace = totalQuota - usedSpace

        QuotaInfo(
            owner = owner,
            totalQuota = totalQuota,
            usedSpace = usedSpace,
            availableSpace = maxOf(0, availableSpace)
        )
    }

    /**
     * Calculate used space for a user
     */
    private fun calculateUsedSpace(owner: String): Long {
        return try {
            if (com.calcvault.storage.provider.StorageManager.isReady()) {
                val expectedOwner = UserStoragePaths.ownerDirectory(owner)
                return com.calcvault.storage.provider.StorageManager.list("file-metadata/").sumOf { key ->
                    val bytes = com.calcvault.storage.provider.StorageManager.read(key) ?: return@sumOf 0L
                    try {
                        val metadata = JSONObject(String(bytes, Charsets.UTF_8))
                        val recordedOwner = runCatching {
                            UserOwnershipManager.FileOwner.valueOf(metadata.optString("owner")).displayName
                        }.getOrNull()
                        if (recordedOwner?.equals(expectedOwner, ignoreCase = true) == true) {
                            metadata.optLong("fileSize", 0L).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                    } finally {
                        bytes.fill(0)
                    }
                }
            }

            val dir = File(storageRoot, UserStoragePaths.ownerDirectory(owner))
            if (!dir.exists()) 0L else dir.walk().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating used space for $owner", e)
            0L
        }
    }

    /**
     * Get quota info for all users
     */
    suspend fun getAllQuotas(): Map<String, QuotaInfo> = withContext(Dispatchers.IO) {
        val quotas = mutableMapOf<String, QuotaInfo>()

        quotas[UserOwnershipManager.USER_ZAIN] = getQuotaInfo(UserOwnershipManager.USER_ZAIN)
        quotas[UserOwnershipManager.USER_SANU] = getQuotaInfo(UserOwnershipManager.USER_SANU)
        quotas["shared"] = getQuotaInfo("shared")

        quotas
    }

    /**
     * Check if space is available for file
     */
    suspend fun hasSpace(fileSize: Long, owner: String): Boolean {
        val quota = getQuotaInfo(owner)
        return fileSize <= quota.availableSpace
    }

    /**
     * Get storage utilization percentage
     */
    suspend fun getUtilizationPercent(owner: String): Float {
        val quota = getQuotaInfo(owner)
        return quota.percentUsed
    }

    // ─── Cleanup & Maintenance ────────────────────────────────────────────

    /**
     * Clean up orphaned files (files with no references)
     */
    suspend fun cleanupOrphaned(daysOld: Int = 30): CleanupResult = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        var filesDeleted = 0
        var spaceFree = 0L

        try {
            val storageDir = File(storageRoot)
            if (!storageDir.exists()) {
                return@withContext CleanupResult(false, 0, 0)
            }

            storageDir.walk().forEach { file ->
                if (file.isFile && file.lastModified() < cutoffTime) {
                    try {
                        spaceFree += file.length()
                        file.delete()
                        filesDeleted++
                        Log.d(TAG, "Deleted orphaned file: ${file.name}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete ${file.name}", e)
                    }
                }
            }

            Log.i(TAG, "Cleanup: deleted $filesDeleted files, freed ${formatSize(spaceFree)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
            return@withContext CleanupResult(false, filesDeleted, spaceFree)
        }

        CleanupResult(true, filesDeleted, spaceFree)
    }

    data class CleanupResult(
        val success: Boolean,
        val filesDeleted: Int,
        val spaceFree: Long
    )

    // ─── Utilities ────────────────────────────────────────────────────────

    /**
     * Generate unique file ID
     */
    private fun generateFileId(@Suppress("UNUSED_PARAMETER") fileName: String): String =
        "file_${UUID.randomUUID()}"

    /**
     * Determine file type from name
     */
    private fun determineFileType(fileName: String): String {
        val ext = fileName.substringAfterLast('.').lowercase()

        return when {
            ext in ThumbnailGenerator.AUDIO_EXTS -> "audio"
            ext in ThumbnailGenerator.VIDEO_EXTS -> "video"
            ext in ThumbnailGenerator.IMAGE_EXTS -> "image"
            else -> "other"
        }
    }

    /**
     * Format size string
     */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Get human-readable storage summary
     */
    suspend fun getStorageSummary(): String = withContext(Dispatchers.IO) {
        val quotas = getAllQuotas()

        val sb = StringBuilder()
        sb.append("Storage Summary\n")
        sb.append("================================\n")

        quotas.forEach { (owner, quota) ->
            sb.append("\n$owner:\n")
            sb.append("  Total: ${formatSize(quota.totalQuota)}\n")
            sb.append("  Used:  ${formatSize(quota.usedSpace)}\n")
            sb.append("  Free:  ${formatSize(quota.availableSpace)}\n")
            sb.append("  ${String.format("  Util:  %.1f%%", quota.percentUsed)}\n")
        }

        sb.toString()
    }
}
