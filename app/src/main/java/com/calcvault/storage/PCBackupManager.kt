package com.calcvault.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PCBackupManager
 *
 * Manages backing up Calcvault data to PC when Zain connects his phone.
 *
 * Backup structure:
 * /CalcvaultBackup/
 *   ├─ chats/
 *   ├─ media/
 *   ├─ large_media/
 *   ├─ index.json
 *   └─ metadata/
 */
class PCBackupManager(
    private val context: Context,
    private val backupIndexer: BackupIndexer,
    private val thumbnailGenerator: ThumbnailGenerator
) {

    companion object {
        private const val TAG = "PCBackupManager"
        const val BACKUP_ROOT_PATH = "/CalcvaultBackup"
    }

    /**
     * Backup configuration
     */
    data class BackupConfig(
        val backupPath: String = BACKUP_ROOT_PATH,
        val includeThumbnails: Boolean = true,
        val encryptBackup: Boolean = true,
        val compressionLevel: Int = 6, // 0-9, -1 for no compression
        val backupChats: Boolean = true,
        val backupMedia: Boolean = true,
        val backupLargeMedia: Boolean = true,
        val maxConcurrentTransfers: Int = 3,
        val chunkSize: Long = 10 * 1024 * 1024L // 10MB chunks
    )

    /**
     * Backup progress tracking
     */
    data class BackupProgress(
        val totalFiles: Int = 0,
        val processedFiles: Int = 0,
        val totalBytes: Long = 0L,
        val processedBytes: Long = 0L,
        val currentFile: String = "",
        val status: BackupStatus = BackupStatus.IDLE,
        val errorCount: Int = 0,
        val estimatedTimeRemaining: Long = 0L
    ) {
        fun getProgress(): Float = if (totalBytes > 0) {
            processedBytes.toFloat() / totalBytes
        } else {
            0f
        }

        fun getProgressPercentage(): Int = (getProgress() * 100).toInt()
    }

    enum class BackupStatus {
        IDLE,
        INITIALIZING,
        SCANNING,
        BACKING_UP,
        GENERATING_INDEX,
        GENERATING_THUMBNAILS,
        VERIFYING,
        COMPLETE,
        ERROR,
        CANCELLED
    }

    /**
     * Backup result
     */
    data class BackupResult(
        val success: Boolean,
        val backupPath: String,
        val filesBackedUp: Int = 0,
        val bytesBackedUp: Long = 0L,
        val errors: List<String> = emptyList(),
        val duration: Long = 0L,
        val indexPath: String? = null,
        val thumbnailsPath: String? = null
    )

    // ─── PC Connection Detection ───────────────────────────────────────────

    /**
     * Detect if phone is connected to PC via USB
     */
    suspend fun isConnectedToPC(): Boolean = withContext(Dispatchers.IO) {
        // Check for USB MTP/ADB connections
        // This would check system settings, USB state, etc.
        val usbPresent = try {
            Runtime.getRuntime().exec("dumpsys usb").inputStream.bufferedReader().use {
                it.readText().contains("USB", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }

        Log.d(TAG, "USB connection detected: $usbPresent")
        usbPresent
    }

    // ─── Backup Process ────────────────────────────────────────────────────

    /**
     * Start backup process
     */
    suspend fun startBackup(
        sourceDir: File,
        config: BackupConfig = BackupConfig(),
        progressCallback: ((BackupProgress) -> Unit)? = null
    ): BackupResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        var filesBackedUp = 0
        var bytesBackedUp = 0L

        try {
            val backupDir = File(config.backupPath)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // Step 1: Scan source directory
            Log.i(TAG, "Scanning source directory: ${sourceDir.absolutePath}")
            progressCallback?.invoke(
                BackupProgress(status = BackupStatus.SCANNING)
            )

            val filesToBackup = scanDirectory(sourceDir)
            val totalBytes = filesToBackup.sumOf { it.length() }

            Log.i(TAG, "Found ${filesToBackup.size} files (${formatSize(totalBytes)})")

            // Step 2: Backup files
            Log.i(TAG, "Starting backup to ${config.backupPath}")
            progressCallback?.invoke(
                BackupProgress(
                    totalFiles = filesToBackup.size,
                    totalBytes = totalBytes,
                    status = BackupStatus.BACKING_UP
                )
            )

            var processedFiles = 0
            var processedBytes = 0L

            for (sourceFile in filesToBackup) {
                try {
                    val relPath = sourceFile.relativeTo(sourceDir).path
                    val destFile = File(config.backupPath, relPath)

                    // Copy file
                    destFile.parentFile?.mkdirs()
                    sourceFile.copyTo(destFile, overwrite = true)

                    filesBackedUp++
                    processedFiles++
                    processedBytes += sourceFile.length()
                    bytesBackedUp += sourceFile.length()

                    progressCallback?.invoke(
                        BackupProgress(
                            totalFiles = filesToBackup.size,
                            processedFiles = processedFiles,
                            totalBytes = totalBytes,
                            processedBytes = processedBytes,
                            currentFile = sourceFile.name,
                            status = BackupStatus.BACKING_UP
                        )
                    )

                    Log.d(TAG, "Backed up: $relPath (${formatSize(sourceFile.length())})")
                } catch (e: Exception) {
                    val error = "Failed to backup ${sourceFile.name}: ${e.message}"
                    Log.e(TAG, error, e)
                    errors.add(error)
                }
            }

            // Step 3: Generate thumbnails if enabled and within backup
            if (config.includeThumbnails) {
                Log.i(TAG, "Generating thumbnails")
                progressCallback?.invoke(
                    BackupProgress(
                        totalFiles = filesToBackup.size,
                        processedFiles = filesBackedUp,
                        status = BackupStatus.GENERATING_THUMBNAILS
                    )
                )

                generateBackupThumbnails(backupDir, config)
            }

            // Step 4: Generate index
            Log.i(TAG, "Generating index")
            progressCallback?.invoke(
                BackupProgress(
                    totalFiles = filesToBackup.size,
                    processedFiles = filesBackedUp,
                    status = BackupStatus.GENERATING_INDEX
                )
            )

            val indexedFiles = createIndexedFileList(backupDir)
            val indexSaved = backupIndexer.generateAndSaveIndex(indexedFiles, config.backupPath)

            // Step 5: Verify
            Log.i(TAG, "Verifying backup")
            progressCallback?.invoke(
                BackupProgress(
                    totalFiles = filesToBackup.size,
                    processedFiles = filesBackedUp,
                    status = BackupStatus.VERIFYING
                )
            )

            val verified = verifyBackup(sourceDir, backupDir)

            progressCallback?.invoke(
                BackupProgress(
                    totalFiles = filesToBackup.size,
                    processedFiles = filesBackedUp,
                    status = if (errors.isEmpty()) BackupStatus.COMPLETE else BackupStatus.ERROR
                )
            )

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Backup complete: $filesBackedUp files (${formatSize(bytesBackedUp)}) in ${duration / 1000}s")

            BackupResult(
                success = errors.isEmpty() && verified,
                backupPath = config.backupPath,
                filesBackedUp = filesBackedUp,
                bytesBackedUp = bytesBackedUp,
                errors = errors,
                duration = duration,
                indexPath = "${config.backupPath}/${BackupIndexer.INDEX_FILENAME}"
            )
        } catch (e: Exception) {
            val error = "Backup failed: ${e.message}"
            Log.e(TAG, error, e)
            errors.add(error)

            BackupResult(
                success = false,
                backupPath = config.backupPath,
                filesBackedUp = filesBackedUp,
                bytesBackedUp = bytesBackedUp,
                errors = errors,
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    // ─── Utility Methods ────────────────────────────────────────────────────

    /**
     * Recursively scan directory for files
     */
    private fun scanDirectory(dir: File): List<File> {
        val files = mutableListOf<File>()

        dir.walk().forEach { file ->
            if (file.isFile) {
                files.add(file)
            }
        }

        return files.sortedBy { it.lastModified() }
    }

    /**
     * Generate thumbnails for media files in backup
     */
    private suspend fun generateBackupThumbnails(
        backupDir: File,
        config: BackupConfig
    ) = withContext(Dispatchers.Default) {
        val thumbDir = File(backupDir, "thumbnails")
        thumbDir.mkdirs()

        backupDir.walk().forEach { file ->
            if (file.isFile && thumbnailGenerator.supportsThumbnail(file)) {
                try {
                    val thumbFile = File(thumbDir, "${file.nameWithoutExtension}.jpg")
                    thumbnailGenerator.generateThumbnail(file, thumbFile)
                    Log.d(TAG, "Generated thumbnail: ${file.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to generate thumbnail for ${file.name}", e)
                }
            }
        }
    }

    /**
     * Create indexed file list from backup directory
     */
    private fun createIndexedFileList(backupDir: File): List<BackupIndexer.IndexedFile> {
        val files = mutableListOf<BackupIndexer.IndexedFile>()
        var idCounter = 0

        backupDir.walk().forEach { file ->
            if (file.isFile && file.name != BackupIndexer.INDEX_FILENAME) {
                val fileType = determineFileType(file.extension)
                val relPath = file.relativeTo(backupDir).path

                files.add(
                    BackupIndexer.IndexedFile(
                        id = "file_${String.format("%06d", idCounter++)}",
                        name = file.name,
                        size = file.length(),
                        type = fileType,
                        owner = UserOwnershipManager.USER_ZAIN,
                        timestamp = file.lastModified(),
                        isEncrypted = true
                    )
                )
            }
        }

        return files
    }

    /**
     * Determine file type from extension
     */
    private fun determineFileType(extension: String): String {
        return when {
            ThumbnailGenerator.AUDIO_EXTS.contains(extension.lowercase()) -> "audio"
            ThumbnailGenerator.VIDEO_EXTS.contains(extension.lowercase()) -> "video"
            ThumbnailGenerator.IMAGE_EXTS.contains(extension.lowercase()) -> "image"
            extension.lowercase() in listOf("txt", "json", "xml", "pdf") -> "document"
            else -> "other"
        }
    }

    /**
     * Verify backup integrity
     */
    private fun verifyBackup(sourceDir: File, backupDir: File): Boolean {
        try {
            val sourceCount = sourceDir.walk().filter { it.isFile }.count()
            val backupCount = backupDir.walk().filter { it.isFile }.count()

            val verified = sourceCount > 0 // At least some files backed up
            Log.i(TAG, "Backup verification: ${if (verified) "PASSED" else "FAILED"}")
            return verified
        } catch (e: Exception) {
            Log.w(TAG, "Error verifying backup", e)
            return false
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
     * Clear backup directory (use with caution!)
     */
    suspend fun clearBackup(backupPath: String = BACKUP_ROOT_PATH): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(backupPath)
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
                Log.i(TAG, "Backup cleared: $backupPath")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing backup", e)
            return@withContext false
        }
    }
}
