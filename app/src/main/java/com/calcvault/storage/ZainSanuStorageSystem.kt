package com.calcvault.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import com.calcvault.storage.provider.StorageManager
import com.calcvault.utils.PairedUserIdentity
import com.calcvault.utils.SessionManager
import org.json.JSONObject

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
    private val storageRoot: String = File(context.filesDir, "calcvault/storage").absolutePath
) {

    companion object {
        private const val TAG = "ZainSanuStorageSystem"
    }

    // ─── Components ────────────────────────────────────────────────────────

    val ownershipManager = UserOwnershipManager(File(storageRoot))
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
        val backupRoot = File(context.filesDir, "CalcvaultBackup")
        val dirs = listOf(
            File(storageRoot, "Zain/large_media/audio"),
            File(storageRoot, "Zain/large_media/video"),
            File(storageRoot, "Zain/large_media/image"),
            File(storageRoot, "Zain/large_media/other"),
            File(storageRoot, "Sanu/media/audio"),
            File(storageRoot, "Sanu/media/video"),
            File(storageRoot, "Sanu/media/image"),
            File(storageRoot, "shared/audio"),
            File(storageRoot, "shared/video"),
            File(storageRoot, "shared/image"),
            File(backupRoot, "chats"),
            File(backupRoot, "media"),
            File(backupRoot, "large_media"),
            File(backupRoot, "thumbnails")
        )

        dirs.forEach { directory ->
            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    Log.w(TAG, "Failed to create app-private directory: ${directory.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create directory: ${directory.absolutePath}", e)
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
        currentUser: String = SessionManager.localUserId.ifBlank { PairedUserIdentity.USER_A },
        fileType: String? = null
    ): FileStorageResult = withContext(Dispatchers.IO) {
        val fileName = sourceFile.name
        if (!StorageManager.isReady()) {
            return@withContext FileStorageResult(success = false, reason = "Vault is locked; unlock it before saving files.")
        }
        if (!sourceFile.isFile || !sourceFile.canRead()) {
            return@withContext FileStorageResult(success = false, reason = "The selected file is missing or unreadable.")
        }
        if (PairedUserIdentity.stableId(currentUser) == null) {
            return@withContext FileStorageResult(success = false, reason = "Select one of the two paired user identities before saving.")
        }

        val fileSize = sourceFile.length()
        val storageDecision = quotaManager.getStoragePath(fileSize, fileName, currentUser)
        if (!storageDecision.allowed) {
            return@withContext FileStorageResult(
                success = false,
                fileId = storageDecision.fileId,
                reason = storageDecision.reason
            )
        }

        val owner = storageDecision.owner
        val fileTypeValue = fileType?.takeIf { it.isNotBlank() } ?: sourceFile.extension.lowercase(Locale.ROOT)
        val fileId = storageDecision.fileId
        val payloadPrefix = "paired-files/$fileId/payload"
        val thumbnailPrefix = "paired-files/$fileId/thumbnail"
        val metadataKey = "file-metadata/$fileId"
        var payloadStored = false
        var thumbnailStored = false
        var thumbnailPath: String? = null
        var temporaryThumbnail: File? = null

        try {
            val payloadInfo = storeEncryptedChunks(payloadPrefix, sourceFile)
            payloadStored = true

            var thumbnailInfo: EncryptedChunkInfo? = null
            if (thumbnailGenerator.supportsThumbnail(sourceFile)) {
                try {
                    val thumbnailTemp = File(context.cacheDir, "cv-thumb-$fileId.jpg")
                    temporaryThumbnail = thumbnailTemp
                    val generated = thumbnailGenerator.generateThumbnail(sourceFile, thumbnailTemp)
                    if (generated != null && generated.isFile) {
                        thumbnailInfo = storeEncryptedChunks(thumbnailPrefix, generated)
                        thumbnailStored = true
                        thumbnailPath = "vault://$thumbnailPrefix"
                    }
                } catch (thumbnailError: Exception) {
                    Log.w(TAG, "Could not create encrypted thumbnail for $fileId", thumbnailError)
                } finally {
                    temporaryThumbnail?.delete()
                }
            }

            val ownership = UserOwnershipManager.FileOwnership(
                fileId = fileId,
                fileName = fileName,
                fileSize = fileSize,
                owner = owner,
                storagePath = "vault://$payloadPrefix",
                isEncrypted = true,
                thumbnailPath = thumbnailPath
            )
            val metadata = ownership.toJson().apply {
                put("fileType", fileTypeValue)
                put("payloadChunkCount", payloadInfo.chunkCount)
                put("payloadByteCount", payloadInfo.byteCount)
                put("payloadSha256", payloadInfo.sha256)
                put("thumbnailChunkCount", thumbnailInfo?.chunkCount ?: 0)
                put("thumbnailByteCount", thumbnailInfo?.byteCount ?: 0L)
                put("thumbnailSha256", thumbnailInfo?.sha256 ?: "")
                put("formatVersion", 1)
            }.toString().toByteArray(Charsets.UTF_8)

            val metadataCommitted = StorageManager.write(metadataKey, metadata)
            metadata.fill(0)
            if (!metadataCommitted) {
                throw IOException("Could not commit encrypted file metadata")
            }

            val logicalPath = "vault://$payloadPrefix"
            Log.i(TAG, "Stored encrypted file $fileId (${formatSize(fileSize)}) for ${owner.displayName}")
            FileStorageResult(
                success = true,
                fileId = fileId,
                filePath = logicalPath,
                thumbnailPath = thumbnailPath,
                owner = owner.displayName,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            StorageManager.delete(metadataKey)
            if (payloadStored) deleteStoredChunks(payloadPrefix)
            if (thumbnailStored) deleteStoredChunks(thumbnailPrefix)
            Log.e(TAG, "Error storing encrypted file", e)
            FileStorageResult(success = false, fileId = fileId, reason = e.message ?: "Encrypted file storage failed")
        } finally {
            temporaryThumbnail?.delete()
        }
    }

    private data class EncryptedChunkInfo(val chunkCount: Int, val byteCount: Long, val sha256: String)

    private fun storeEncryptedChunks(prefix: String, source: File): EncryptedChunkInfo {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        var chunkCount = 0
        var byteCount = 0L
        try {
            source.inputStream().buffered().use { input ->
                var finished = false
                while (!finished) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        finished = true
                    } else {
                        digest.update(buffer, 0, count)
                        byteCount = Math.addExact(byteCount, count.toLong())
                        val chunk = buffer.copyOf(count)
                        try {
                            val key = "$prefix/chunk/${chunkCount.toString().padStart(8, '0')}"
                            if (!StorageManager.write(key, chunk)) throw IOException("Encrypted chunk write failed")
                        } finally {
                            chunk.fill(0)
                        }
                        chunkCount++
                    }
                }
            }
            val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            return EncryptedChunkInfo(chunkCount, byteCount, hash)
        } catch (error: Exception) {
            deleteStoredChunks(prefix)
            throw error
        } finally {
            buffer.fill(0)
        }
    }

    private fun deleteStoredChunks(prefix: String) {
        runCatching {
            StorageManager.list("$prefix/chunk/").forEach { key -> StorageManager.delete(key) }
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

        val backupRoot = File(context.filesDir, "CalcvaultBackup").absolutePath
        val config = PCBackupManager.BackupConfig(
            backupPath = backupRoot,
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
        if (fileId.isBlank()) return@withContext missingFileAccess()

        val localMetadataBytes = if (StorageManager.isReady()) {
            StorageManager.read("file-metadata/$fileId")
        } else {
            null
        }
        if (localMetadataBytes != null) {
            val metadata = try {
                JSONObject(String(localMetadataBytes, Charsets.UTF_8))
            } catch (_: Exception) {
                null
            } finally {
                localMetadataBytes.fill(0)
            }
            val owner = runCatching {
                metadata?.optString("owner")?.let { UserOwnershipManager.FileOwner.valueOf(it) }
            }.getOrNull()
            val chunkCount = metadata?.optInt("payloadChunkCount", -1) ?: -1
            val hasFirstChunk = chunkCount == 0 || StorageManager.exists(
                "paired-files/$fileId/payload/chunk/00000000"
            )
            val validStoredObject = metadata != null && metadata.optBoolean("isEncrypted", false) &&
                metadata.optLong("fileSize", -1L) >= 0L && chunkCount >= 0 && hasFirstChunk
            val canAccess = validStoredObject && owner != null && ownershipManager.canAccessDirectly(userId, owner)
            return@withContext AccessCheckResult(
                canAccess = canAccess,
                isRemote = false,
                owner = owner?.displayName.orEmpty(),
                accessState = if (canAccess) UserOwnershipManager.AccessState.AVAILABLE else UserOwnershipManager.AccessState.LOCKED,
                prompt = if (!validStoredObject) "Encrypted file metadata is incomplete or unavailable." else ""
            )
        }

        val remoteRef = remoteFileRefManager.getRef(fileId)
        if (remoteRef != null) {
            val canAccess = remoteRef.canAccess(userId)
            return@withContext AccessCheckResult(
                canAccess = canAccess,
                isRemote = true,
                owner = remoteRef.owner,
                accessState = if (canAccess) UserOwnershipManager.AccessState.AVAILABLE else UserOwnershipManager.AccessState.LOCKED,
                prompt = if (remoteRef.isGrantExpired(userId)) {
                    "This file grant has expired. Ask the owner to grant access again."
                } else {
                    ownershipManager.getAccessPrompt(remoteRef.owner, userId)
                }
            )
        }

        missingFileAccess()
    }

    private fun missingFileAccess() = AccessCheckResult(
        canAccess = false,
        isRemote = false,
        owner = "",
        accessState = UserOwnershipManager.AccessState.NOT_FOUND,
        prompt = "File not found in this vault or the paired user's available references."
    )

    suspend fun exportFile(fileId: String, userId: String, destination: File): Boolean =
        exportStoredObject(fileId, userId, "payload", destination)

    suspend fun exportThumbnail(fileId: String, userId: String, destination: File): Boolean =
        exportStoredObject(fileId, userId, "thumbnail", destination)

    private suspend fun exportStoredObject(
        fileId: String,
        userId: String,
        objectKind: String,
        destination: File
    ): Boolean = withContext(Dispatchers.IO) {
        val access = canAccessFile(userId, fileId)
        if (!access.canAccess || access.isRemote || !StorageManager.isReady()) return@withContext false
        if (!fileId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) return@withContext false

        val metadataBytes = StorageManager.read("file-metadata/$fileId") ?: return@withContext false
        val metadata = try {
            JSONObject(String(metadataBytes, Charsets.UTF_8))
        } catch (_: Exception) {
            return@withContext false
        } finally {
            metadataBytes.fill(0)
        }
        val countField = if (objectKind == "payload") "payloadChunkCount" else "thumbnailChunkCount"
        val hashField = if (objectKind == "payload") "payloadSha256" else "thumbnailSha256"
        val expectedChunks = metadata.optInt(countField, -1)
        val expectedBytes = metadata.optLong(
            if (objectKind == "payload") "payloadByteCount" else "thumbnailByteCount",
            -1L
        )
        val expectedHash = metadata.optString(hashField)
        if (expectedChunks < 0 || expectedBytes < 0L || expectedHash.isBlank()) return@withContext false

        val parent = destination.absoluteFile.parentFile ?: return@withContext false
        if (!parent.exists() && !parent.mkdirs()) return@withContext false
        val temp = File(parent, ".${destination.name}.$fileId.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var exported = false
        var exportedBytes = 0L
        try {
            temp.outputStream().buffered().use { output ->
                repeat(expectedChunks) { index ->
                    val chunkKey = "paired-files/$fileId/$objectKind/chunk/${index.toString().padStart(8, '0')}"
                    val chunk = StorageManager.read(chunkKey) ?: throw IOException("Encrypted file chunk $index is missing")
                    try {
                        digest.update(chunk)
                        exportedBytes = Math.addExact(exportedBytes, chunk.size.toLong())
                        output.write(chunk)
                    } finally {
                        chunk.fill(0)
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (exportedBytes != expectedBytes || !actualHash.equals(expectedHash, ignoreCase = true)) {
                throw IOException("Encrypted file integrity or size check failed")
            }
            try {
                java.nio.file.Files.move(
                    temp.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temp.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            exported = true
            true
        } catch (error: Exception) {
            Log.e(TAG, "Encrypted file export failed", error)
            false
        } finally {
            if (!exported) temp.delete()
        }
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
        val backupDir = File(context.filesDir, "CalcvaultBackup")
        val backupExists = backupDir.exists()
        val backupIndex = if (backupExists) {
            backupIndexer.loadIndex(backupDir.absolutePath)
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
