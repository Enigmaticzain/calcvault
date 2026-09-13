package com.calcvault.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ZainSanuStorageSystem
 *
 * Comprehensive storage system for the Zain-Sanu architecture.
 *
 * Responsibilities:
 * 1. Route files based on size and ownership
 * 2. Manage PC backup when Zain connects
 * 3. Generate indexes and thumbnails
 * 4. Sync metadata between devices
 * 5. Enforce storage quotas
 * 6. Handle remote file references
 */
class ZainSanuStorageSystem(
    private val context: Context,
    private val storageRoot: String = "/calcvault/storage"
) {

    companion object {
        private const val TAG = "ZainSanuStorageSystem"
    }

    // ─── Components ────────────────────────────────────────────────────────

    val ownershipManager = UserOwnershipManager()
    val backupIndexer = BackupIndexer()
    val thumbnailGenerator = ThumbnailGenerator(context)
    val pcBackupManager = PCBackupManager(context, backupIndexer, thumbnailGenerator)
    val remoteFileRefManager = RemoteFileRefManager(storageRoot)
    val quotaManager = StorageQuotaManager(storageRoot, ownershipManager)

    // ─── System Initialization ────────────────────────────────────────────

    /**
     * Initialize the storage system
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.i(TAG, "Initializing Zain-Sanu Storage System")

            // Load remote file references
            remoteFileRefManager.load()
            Log.d(TAG, "Loaded remote file references")

            // Create storage directories
            createStorageDirectories()
            Log.d(TAG, "Created storage directories")

            Log.i(TAG, "Storage system initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing storage system", e)
            false
        }
    }

    /**
     * Create necessary storage directories
     */
    private fun createStorageDirectories() {
        val dirs = listOf(
            "$storageRoot/${UserOwnershipManager.USER_ZAIN}/large_media/audio",
            "$storageRoot/${UserOwnershipManager.USER_ZAIN}/large_media/video",
            "$storageRoot/${UserOwnershipManager.USER_ZAIN}/large_media/image",
            "$storageRoot/${UserOwnershipManager.USER_ZAIN}/large_media/other",
            "$storageRoot/${UserOwnershipManager.USER_SANU}/media/audio",
            "$storageRoot/${UserOwnershipManager.USER_SANU}/media/video",
            "$storageRoot/${UserOwnershipManager.USER_SANU}/media/image",
            "$storageRoot/shared/audio",
            "$storageRoot/shared/video",
            "$storageRoot/shared/image",
            "/CalcvaultBackup/chats",
            "/CalcvaultBackup/media",
            "/CalcvaultBackup/large_media",
            "/CalcvaultBackup/thumbnails"
        )

        dirs.forEach { path ->
            try {
                File(path).mkdirs()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create directory: $path", e)
            }
        }
    }

    // ─── File Storage Workflow ──────────────────────────────────────────────

    /**
     * Store a file through the system
     *
     * @param sourceFile Source file to store
     * @param currentUser User who is storing the file
     * @param fileType Category (audio, video, image, etc.)
     * @return FileStorageResult with details
     */
    suspend fun storeFile(
        sourceFile: File,
        currentUser: String = UserOwnershipManager.USER_ZAIN,
        fileType: String? = null
    ): FileStorageResult = withContext(Dispatchers.IO) {
        try {
            val fileName = sourceFile.name
            val fileSize = sourceFile.length()

            Log.d(TAG, "Storing file: $fileName (${formatSize(fileSize)})")

            // 1. Determine storage location
            val storageDecision = quotaManager.getStoragePath(fileSize, fileName, currentUser)
            if (!storageDecision.allowed) {
                Log.w(TAG, "Storage denied: ${storageDecision.reason}")
                return@withContext FileStorageResult(
                    success = false,
                    fileId = storageDecision.fileId,
                    reason = storageDecision.reason
                )
            }

            // 2. Determine ownership
            val owner = ownershipManager.determineOwner(fileSize)

            // 3. Create ownership record
            val ownership = UserOwnershipManager.FileOwnership(
                fileId = storageDecision.fileId,
                fileName = fileName,
                fileSize = fileSize,
                owner = owner,
                storagePath = storageDecision.storagePath,
                isEncrypted = true
            )

            // 4. Copy file to destination
            val destFile = File(storageDecision.storagePath)
            destFile.parentFile?.mkdirs()
            sourceFile.copyTo(destFile, overwrite = true)

            // 5. Generate thumbnail if supported
            var thumbnailPath: String? = null
            if (thumbnailGenerator.supportsThumbnail(sourceFile)) {
                try {
                    val thumbFile = File(
                        "$storageRoot/thumbnails",
                        "${ownership.fileId}.jpg"
                    )
                    if (thumbnailGenerator.generateThumbnail(sourceFile, thumbFile) != null) {
                        thumbnailPath = thumbFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate thumbnail", e)
                }
            }

            // 6. If file is large and current user is Sanu, create remote reference
            if (ownershipManager.isLargeFile(fileSize) && currentUser == UserOwnershipManager.USER_SANU) {
                val remoteRef = UserOwnershipManager.RemoteFileRef(
                    id = ownership.fileId,
                    name = fileName,
                    size = fileSize,
                    owner = UserOwnershipManager.USER_ZAIN,
                    thumbnailPath = thumbnailPath,
                    accessState = UserOwnershipManager.AccessState.LOCKED,
                    timestamp = System.currentTimeMillis()
                )
                remoteFileRefManager.addRef(remoteRef)
                Log.d(TAG, "Created remote reference for $fileName")
            }

            Log.i(TAG, "File stored successfully: ${storageDecision.storagePath}")

            FileStorageResult(
                success = true,
                fileId = ownership.fileId,
                filePath = storageDecision.storagePath,
                thumbnailPath = thumbnailPath,
                owner = owner.displayName,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error storing file", e)
            FileStorageResult(
                success = false,
                reason = "Error: ${e.message}"
            )
        }
    }

    /**
     * File storage result
     */
    data class FileStorageResult(
        val success: Boolean,
        val fileId: String = "",
        val filePath: String = "",
        val thumbnailPath: String? = null,
        val owner: String = "",
        val fileSize: Long = 0L,
        val reason: String = ""
    )

    // ─── PC Backup Workflow ────────────────────────────────────────────────

    /**
     * Execute PC backup (called when Zain connects to PC)
     */
    suspend fun performPCBackup(
        sourceDir: File = File(storageRoot),
        progressCallback: ((PCBackupManager.BackupProgress) -> Unit)? = null
    ): PCBackupManager.BackupResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting PC backup process")

        val config = PCBackupManager.BackupConfig(
            backupPath = "/CalcvaultBackup",
            includeThumbnails = true,
            encryptBackup = true,
            backupChats = true,
            backupMedia = true,
            backupLargeMedia = true
        )

        val result = pcBackupManager.startBackup(sourceDir, config, progressCallback)

        if (result.success) {
            Log.i(TAG, "PC backup completed successfully")
            Log.i(TAG, "Result: ${result.filesBackedUp} files (${formatSize(result.bytesBackedUp)})")

            // Load and sync index with remote refs
            val index = backupIndexer.loadIndex(config.backupPath)
            if (index != null) {
                val synced = remoteFileRefManager.syncWithBackupIndex(index)
                Log.i(TAG, "Synced $synced remote references from backup index")
            }
        } else {
            Log.e(TAG, "PC backup failed: ${result.errors.joinToString(", ")}")
        }

        result
    }

    // ─── Remote File Access ────────────────────────────────────────────────

    /**
     * Check if a user can access a file
     */
    suspend fun canAccessFile(
        userId: String,
        fileId: String
    ): AccessCheckResult = withContext(Dispatchers.IO) {
        // Try to find in remote references
        val remoteRef = remoteFileRefManager.getRef(fileId)
        if (remoteRef != null) {
            val canAccess = remoteRef.accessState == UserOwnershipManager.AccessState.AVAILABLE ||
                userId == remoteRef.owner

            return@withContext AccessCheckResult(
                canAccess = canAccess,
                isRemote = true,
                owner = remoteRef.owner,
                accessState = remoteRef.accessState,
                prompt = ownershipManager.getAccessPrompt(remoteRef.owner, userId)
            )
        }

        // Try to find in local storage
        val fileOwner = if (fileId.contains(UserOwnershipManager.USER_ZAIN)) {
            UserOwnershipManager.FileOwner.ZAIN
        } else {
            UserOwnershipManager.FileOwner.SHARED
        }

        val canAccess = ownershipManager.canAccessDirectly(userId, fileOwner)

        AccessCheckResult(
            canAccess = canAccess,
            isRemote = false,
            owner = fileOwner.displayName,
            accessState = if (canAccess) {
                UserOwnershipManager.AccessState.AVAILABLE
            } else {
                UserOwnershipManager.AccessState.LOCKED
            }
        )
    }

    data class AccessCheckResult(
        val canAccess: Boolean,
        val isRemote: Boolean,
        val owner: String,
        val accessState: UserOwnershipManager.AccessState,
        val prompt: String = ""
    )

    // ─── System Status ────────────────────────────────────────────────────

    /**
     * Get system status
     */
    suspend fun getSystemStatus(): SystemStatus = withContext(Dispatchers.IO) {
        val quotas = quotaManager.getAllQuotas()
        val allRefs = remoteFileRefManager.getAllRefs()
        val refStats = remoteFileRefManager.getStats()

        // Check if backup exists
        val backupDir = File("/CalcvaultBackup")
        val backupExists = backupDir.exists()
        val backupIndex = if (backupExists) {
            backupIndexer.loadIndex()
        } else {
            null
        }

        SystemStatus(
            quotas = quotas,
            remoteRefsCount = allRefs.size,
            remoteRefsStats = refStats,
            backupExists = backupExists,
            backupIndexExists = backupIndex != null,
            backupFileCount = backupIndex?.files?.size ?: 0,
            lastBackupTimestamp = backupIndex?.generatedAt ?: 0L
        )
    }

    data class SystemStatus(
        val quotas: Map<String, StorageQuotaManager.QuotaInfo>,
        val remoteRefsCount: Int,
        val remoteRefsStats: RemoteFileRefManager.RefStatistics,
        val backupExists: Boolean,
        val backupIndexExists: Boolean,
        val backupFileCount: Int,
        val lastBackupTimestamp: Long
    )

    // ─── Utilities ────────────────────────────────────────────────────────

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Get full system summary
     */
    suspend fun getSystemSummary(): String = withContext(Dispatchers.IO) {
        val status = getSystemStatus()
        val storageSummary = quotaManager.getStorageSummary()

        val sb = StringBuilder()
        sb.append("╔════════════════════════════════════════════════════════════╗\n")
        sb.append("║      ZAIN-SANU STORAGE SYSTEM STATUS                       ║\n")
        sb.append("╚════════════════════════════════════════════════════════════╝\n\n")

        sb.append(storageSummary)
        sb.append("\n\n")

        sb.append("Remote File References:\n")
        sb.append("  Total: ${status.remoteRefsCount}\n")
        sb.append("  Locked: ${status.remoteRefsStats.lockedCount}\n")
        sb.append("  Available: ${status.remoteRefsStats.availableCount}\n")
        sb.append("  Total Size: ${formatSize(status.remoteRefsStats.totalSize)}\n")

        sb.append("\n\nPC Backup:\n")
        sb.append("  Exists: ${status.backupExists}\n")
        sb.append("  Index: ${status.backupIndexExists}\n")
        sb.append("  Files: ${status.backupFileCount}\n")
        if (status.lastBackupTimestamp > 0) {
            sb.append("  Last Backup: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(status.lastBackupTimestamp)}\n")
        }

        sb.toString()
    }
}
