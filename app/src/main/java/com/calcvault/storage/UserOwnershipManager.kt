package com.calcvault.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * UserOwnershipManager
 *
 * Manages ownership and access control for Zain and Sanu.
 *
 * Rules:
 * - Files > 1GB stored ONLY under Zain's storage
 * - Sanu gets metadata references only
 * - Zain is the primary storage authority
 */
class UserOwnershipManager {

    companion object {
        private const val TAG = "UserOwnershipManager"

        // Hard-coded users
        const val USER_ZAIN = "Zain"
        const val USER_SANU = "Sanu"

        // File size threshold
        const val LARGE_FILE_THRESHOLD_BYTES = 1_000_000_000L // 1GB
    }

    /**
     * Ownership model for files
     */
    enum class FileOwner(val displayName: String) {
        ZAIN("Zain"),
        SANU("Sanu"),
        SHARED("Shared")
    }

    /**
     * Access state for remote files
     */
    enum class AccessState {
        LOCKED, // File on remote device, requires connection
        AVAILABLE, // File available (through backup or direct)
        SYNCING, // Currently transferring
        NOT_FOUND // File missing
    }

    /**
     * File ownership metadata
     */
    data class FileOwnership(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val owner: FileOwner,
        val storagePath: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isEncrypted: Boolean = true,
        val thumbnailPath: String? = null
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("fileId", fileId)
            put("fileName", fileName)
            put("fileSize", fileSize)
            put("owner", owner.name)
            put("storagePath", storagePath)
            put("timestamp", timestamp)
            put("isEncrypted", isEncrypted)
            put("thumbnailPath", thumbnailPath ?: "")
        }

        companion object {
            fun fromJson(obj: JSONObject): FileOwnership? = try {
                FileOwnership(
                    fileId = obj.getString("fileId"),
                    fileName = obj.getString("fileName"),
                    fileSize = obj.getLong("fileSize"),
                    owner = FileOwner.valueOf(obj.getString("owner")),
                    storagePath = obj.getString("storagePath"),
                    timestamp = obj.getLong("timestamp"),
                    isEncrypted = obj.getBoolean("isEncrypted"),
                    thumbnailPath = obj.optString("thumbnailPath", null).takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing FileOwnership JSON", e)
                null
            }
        }
    }

    /**
     * Remote file reference for non-owner devices
     */
    data class RemoteFileRef(
        val id: String,
        val name: String,
        val size: Long,
        val owner: String = USER_ZAIN,
        val thumbnailPath: String? = null,
        val accessState: AccessState = AccessState.LOCKED,
        val timestamp: Long = System.currentTimeMillis(),
        val lastSyncTimestamp: Long = 0L
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("size", size)
            put("owner", owner)
            put("thumbnailPath", thumbnailPath ?: "")
            put("accessState", accessState.name)
            put("timestamp", timestamp)
            put("lastSyncTimestamp", lastSyncTimestamp)
        }

        companion object {
            fun fromJson(obj: JSONObject): RemoteFileRef? = try {
                RemoteFileRef(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    size = obj.getLong("size"),
                    owner = obj.optString("owner", USER_ZAIN),
                    thumbnailPath = obj.optString("thumbnailPath", null).takeIf { it.isNotEmpty() },
                    accessState = try {
                        AccessState.valueOf(obj.getString("accessState"))
                    } catch (e: Exception) {
                        AccessState.LOCKED
                    },
                    timestamp = obj.getLong("timestamp"),
                    lastSyncTimestamp = obj.optLong("lastSyncTimestamp", 0L)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing RemoteFileRef JSON", e)
                null
            }
        }
    }

    // ─── Ownership Determination ───────────────────────────────────────────

    /**
     * Determine who should own a file based on size.
     */
    fun determineOwner(fileSize: Long): FileOwner {
        return if (fileSize > LARGE_FILE_THRESHOLD_BYTES) {
            FileOwner.ZAIN
        } else {
            FileOwner.SHARED
        }
    }

    /**
     * Check if file should be stored only under Zain.
     */
    fun isLargeFile(fileSize: Long): Boolean {
        return fileSize > LARGE_FILE_THRESHOLD_BYTES
    }

    /**
     * Get storage path for a file based on owner.
     */
    suspend fun getStoragePath(
        owner: FileOwner,
        fileName: String,
        fileType: String = "media" // media, chat, recordings, etc.
    ): String = withContext(Dispatchers.IO) {
        return@withContext when (owner) {
            FileOwner.ZAIN -> {
                "/calcvault/storage/$USER_ZAIN/large_media/$fileType/$fileName"
            }
            FileOwner.SANU -> {
                "/calcvault/storage/$USER_SANU/media/$fileType/$fileName"
            }
            FileOwner.SHARED -> {
                "/calcvault/storage/shared/$fileType/$fileName"
            }
        }
    }

    // ─── Access Control ───────────────────────────────────────────────────

    /**
     * Can the given user access this file directly?
     */
    fun canAccessDirectly(userId: String, fileOwner: FileOwner): Boolean {
        return when {
            userId == USER_ZAIN -> true // Zain can access anything
            fileOwner == FileOwner.ZAIN -> false // Others cannot access Zain's large files
            fileOwner == FileOwner.SHARED -> true // Everyone can access shared
            else -> userId == fileOwner.displayName // Can access own files
        }
    }

    /**
     * Get access prompt message for locked files.
     */
    fun getAccessPrompt(fileOwner: String, userId: String): String {
        return if (userId == USER_ZAIN) {
            "This file is owned by $fileOwner"
        } else {
            "Ask $fileOwner to connect their phone to PC to access this file"
        }
    }

    // ─── Logging & Auditing ───────────────────────────────────────────────

    fun logOwnershipChange(
        fileId: String,
        oldOwner: FileOwner,
        newOwner: FileOwner,
        reason: String = ""
    ) {
        Log.i(TAG, "File $fileId: $oldOwner → $newOwner ${if (reason.isNotEmpty()) "($reason)" else ""}")
    }

    fun logAccessAttempt(
        userId: String,
        fileId: String,
        fileOwner: FileOwner,
        allowed: Boolean
    ) {
        val status = if (allowed) "ALLOWED" else "DENIED"
        Log.d(TAG, "Access $status: $userId trying to access $fileId owned by ${fileOwner.displayName}")
    }
}
