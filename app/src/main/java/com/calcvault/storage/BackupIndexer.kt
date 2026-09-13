package com.calcvault.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * BackupIndexer
 *
 * Manages the backup index system for PC backups.
 * Generates, stores, and retrieves `index.json` containing file metadata.
 *
 * Index location:
 * /CalcvaultBackup/index.json
 */
class BackupIndexer {

    companion object {
        private const val TAG = "BackupIndexer"
        const val INDEX_FILENAME = "index.json"
        const val BACKUP_ROOT = "/CalcvaultBackup"
    }

    /**
     * Indexed file entry
     */
    data class IndexedFile(
        val id: String,
        val name: String,
        val size: Long,
        val type: String, // audio, video, image, chat, etc.
        val owner: String = UserOwnershipManager.USER_ZAIN,
        val thumbnailPath: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val checksum: String? = null, // Optional: SHA256 for integrity
        val isEncrypted: Boolean = true
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("size", size)
            put("type", type)
            put("owner", owner)
            put("thumbnailPath", thumbnailPath ?: "")
            put("timestamp", timestamp)
            put("checksum", checksum ?: "")
            put("isEncrypted", isEncrypted)
        }

        companion object {
            fun fromJson(obj: JSONObject): IndexedFile? = try {
                IndexedFile(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    size = obj.getLong("size"),
                    type = obj.getString("type"),
                    owner = obj.optString("owner", UserOwnershipManager.USER_ZAIN),
                    thumbnailPath = obj.optString("thumbnailPath", "").takeIf { it.isNotEmpty() },
                    timestamp = obj.getLong("timestamp"),
                    checksum = obj.optString("checksum", "").takeIf { it.isNotEmpty() },
                    isEncrypted = obj.optBoolean("isEncrypted", true)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing IndexedFile JSON", e)
                null
            }
        }
    }

    /**
     * Complete backup index
     */
    data class BackupIndex(
        val version: String = "1.0",
        val generatedAt: Long = System.currentTimeMillis(),
        val backupLocation: String = BACKUP_ROOT,
        val files: List<IndexedFile> = emptyList(),
        val statistics: IndexStatistics = IndexStatistics()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("version", version)
            put("generatedAt", generatedAt)
            put("backupLocation", backupLocation)

            val filesArray = JSONArray()
            files.forEach { file ->
                filesArray.put(file.toJson())
            }
            put("files", filesArray)

            put("statistics", statistics.toJson())
        }

        companion object {
            fun fromJson(obj: JSONObject): BackupIndex? = try {
                val filesArray = obj.getJSONArray("files")
                val files = mutableListOf<IndexedFile>()
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    IndexedFile.fromJson(fileObj)?.let { files.add(it) }
                }

                BackupIndex(
                    version = obj.getString("version"),
                    generatedAt = obj.getLong("generatedAt"),
                    backupLocation = obj.getString("backupLocation"),
                    files = files,
                    statistics = obj.optJSONObject("statistics")?.let {
                        IndexStatistics.fromJson(it)
                    } ?: IndexStatistics()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing BackupIndex JSON", e)
                null
            }
        }
    }

    /**
     * Statistics for the backup
     */
    data class IndexStatistics(
        val totalFiles: Int = 0,
        val totalSize: Long = 0L,
        val filesByType: Map<String, Int> = emptyMap(),
        val sizeByType: Map<String, Long> = emptyMap(),
        val largeFiles: Int = 0,
        val lastUpdated: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("totalFiles", totalFiles)
            put("totalSize", totalSize)

            val typeObj = JSONObject()
            filesByType.forEach { (type, count) -> typeObj.put(type, count) }
            put("filesByType", typeObj)

            val sizeObj = JSONObject()
            sizeByType.forEach { (type, size) -> sizeObj.put(type, size) }
            put("sizeByType", sizeObj)

            put("largeFiles", largeFiles)
            put("lastUpdated", lastUpdated)
        }

        companion object {
            fun fromJson(obj: JSONObject): IndexStatistics? = try {
                val filesByType = mutableMapOf<String, Int>()
                val typeObj = obj.optJSONObject("filesByType")
                typeObj?.keys()?.forEach { key ->
                    filesByType[key] = typeObj.getInt(key)
                }

                val sizeByType = mutableMapOf<String, Long>()
                val sizeObj = obj.optJSONObject("sizeByType")
                sizeObj?.keys()?.forEach { key ->
                    sizeByType[key] = sizeObj.getLong(key)
                }

                IndexStatistics(
                    totalFiles = obj.getInt("totalFiles"),
                    totalSize = obj.getLong("totalSize"),
                    filesByType = filesByType,
                    sizeByType = sizeByType,
                    largeFiles = obj.getInt("largeFiles"),
                    lastUpdated = obj.getLong("lastUpdated")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing IndexStatistics JSON", e)
                null
            }
        }
    }

    // ─── Index Management ───────────────────────────────────────────────────

    /**
     * Generate index from file list and save to disk
     */
    suspend fun generateAndSaveIndex(
        files: List<IndexedFile>,
        backupPath: String = BACKUP_ROOT
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Calculate statistics
            val stats = calculateStatistics(files)

            // Create index
            val index = BackupIndex(
                files = files,
                backupLocation = backupPath,
                statistics = stats
            )

            // Save to disk
            val backupDir = File(backupPath)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val indexFile = File(backupDir, INDEX_FILENAME)
            indexFile.writeText(index.toJson().toString(2)) // Pretty print with indent

            Log.i(TAG, "Index saved: ${indexFile.absolutePath} (${files.size} files)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving index", e)
            false
        }
    }

    /**
     * Load index from disk
     */
    suspend fun loadIndex(backupPath: String = BACKUP_ROOT): BackupIndex? = withContext(Dispatchers.IO) {
        try {
            val indexFile = File(backupPath, INDEX_FILENAME)
            if (!indexFile.exists()) {
                Log.w(TAG, "Index file not found: ${indexFile.absolutePath}")
                return@withContext null
            }

            val jsonString = indexFile.readText()
            val jsonObj = JSONObject(jsonString)

            Log.i(TAG, "Index loaded: ${indexFile.absolutePath}")
            BackupIndex.fromJson(jsonObj)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading index", e)
            null
        }
    }

    /**
     * Search index for files by name or type
     */
    fun searchIndex(
        index: BackupIndex,
        query: String? = null,
        fileType: String? = null
    ): List<IndexedFile> {
        return index.files.filter { file ->
            val matchesQuery = query?.let { file.name.contains(query, ignoreCase = true) } ?: true

            val matchesType = fileType?.let { file.type.equals(fileType, ignoreCase = true) } ?: true

            matchesQuery && matchesType
        }
    }

    /**
     * Get files by owner
     */
    fun getFilesByOwner(index: BackupIndex, owner: String): List<IndexedFile> {
        return index.files.filter { it.owner == owner }
    }

    /**
     * Get large files (> 1GB)
     */
    fun getLargeFiles(index: BackupIndex): List<IndexedFile> {
        return index.files.filter { it.size > UserOwnershipManager.LARGE_FILE_THRESHOLD_BYTES }
    }

    // ─── Statistics ────────────────────────────────────────────────────────

    /**
     * Calculate statistics from file list
     */
    private fun calculateStatistics(files: List<IndexedFile>): IndexStatistics {
        val filesByType = mutableMapOf<String, Int>()
        val sizeByType = mutableMapOf<String, Long>()

        var largeFileCount = 0
        var totalSize = 0L

        files.forEach { file ->
            // Count by type
            filesByType[file.type] = (filesByType[file.type] ?: 0) + 1
            sizeByType[file.type] = (sizeByType[file.type] ?: 0L) + file.size

            // Count large files
            if (file.size > UserOwnershipManager.LARGE_FILE_THRESHOLD_BYTES) {
                largeFileCount++
            }

            totalSize += file.size
        }

        return IndexStatistics(
            totalFiles = files.size,
            totalSize = totalSize,
            filesByType = filesByType,
            sizeByType = sizeByType,
            largeFiles = largeFileCount,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Get formatted size string (KB, MB, GB, etc.)
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Get summary of index
     */
    fun getSummary(index: BackupIndex): String {
        return """
            Backup Index Summary
            ════════════════════════════════════════
            Version: ${index.version}
            Generated: ${index.generatedAt}
            Location: ${index.backupLocation}
            
            Files: ${index.statistics.totalFiles}
            Total Size: ${formatSize(index.statistics.totalSize)}
            Large Files: ${index.statistics.largeFiles}
            
            By Type:
            ${index.statistics.filesByType.entries.joinToString("\n") { (type, count) ->
            "  $type: $count files (${formatSize(index.statistics.sizeByType[type] ?: 0L)})"
        }}
        """.trimIndent()
    }
}
