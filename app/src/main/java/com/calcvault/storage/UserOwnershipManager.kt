package com.calcvault.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import com.calcvault.utils.PairedUserIdentity

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
class UserOwnershipManager(private val storageRoot: File) {

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
        val lastSyncTimestamp: Long = 0L,
        val allowedUsers: List<String> = emptyList(),
        val expiresAtMs: Long = 0L,
        val revoked: Boolean = false,
        val userGrantExpiriesMs: Map<String, Long> = emptyMap()
    ) {
        fun canAccess(userId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
            if (userId.isBlank() || accessState != AccessState.AVAILABLE) return false
            if (PairedUserIdentity.stableId(userId) == PairedUserIdentity.USER_A) return true
            if (PairedUserIdentity.sameUser(owner, userId)) return true
            if (revoked) return false
            val explicitGrant = userGrantExpiriesMs.entries.firstOrNull {
                PairedUserIdentity.sameUser(it.key, userId)
            }
            val legacyGrant = if (explicitGrant == null && allowedUsers.any {
                    PairedUserIdentity.sameUser(it, userId)
                }
            ) {
                expiresAtMs
            } else {
                null
            }
            val expiry = explicitGrant?.value ?: legacyGrant ?: return false
            return expiry == 0L || nowMs < expiry
        }

        fun isGrantExpired(userId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
            val explicitExpiry = userGrantExpiriesMs.entries.firstOrNull {
                PairedUserIdentity.sameUser(it.key, userId)
            }?.value
            val expiry = explicitExpiry ?: if (allowedUsers.any { PairedUserIdentity.sameUser(it, userId) }) {
                expiresAtMs
            } else {
                null
            }
            return expiry != null && expiry > 0L && nowMs >= expiry
        }

        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("size", size)
            put("owner", owner)
            put("thumbnailPath", thumbnailPath ?: "")
            put("accessState", accessState.name)
            put("timestamp", timestamp)
            put("lastSyncTimestamp", lastSyncTimestamp)
            put("allowedUsers", org.json.JSONArray(allowedUsers))
            put("expiresAtMs", expiresAtMs)
            put("revoked", revoked)
            put("userGrantExpiriesMs", JSONObject().apply {
                userGrantExpiriesMs.forEach { (userId, expiry) -> put(userId, expiry) }
            })
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
                    lastSyncTimestamp = obj.optLong("lastSyncTimestamp", 0L),
                    allowedUsers = obj.optJSONArray("allowedUsers")?.let { users ->
                        (0 until users.length()).mapNotNull { index ->
                            users.optString(index).takeIf { it.isNotBlank() }
                        }
                    } ?: emptyList(),
                    expiresAtMs = obj.optLong("expiresAtMs", 0L),
                    revoked = obj.optBoolean("revoked", false),
                    userGrantExpiriesMs = obj.optJSONObject("userGrantExpiriesMs")?.let { grants ->
                        buildMap {
                            grants.keys().forEach { userId -> put(userId, grants.optLong(userId, 0L)) }
                        }
                    } ?: emptyMap()
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
        fileType: String = "other"
    ): String = withContext(Dispatchers.IO) {
        UserStoragePaths.forFile(
            storageRoot = storageRoot,
            owner = owner.displayName,
            fileType = fileType,
            fileName = fileName
        ).absolutePath
    }

    // ─── Access Control ───────────────────────────────────────────────────

    /**
     * Can the given user access this file directly?
     */
    fun canAccessDirectly(userId: String, fileOwner: FileOwner): Boolean {
        return when (PairedUserIdentity.stableId(userId)) {
            PairedUserIdentity.USER_A -> true // Zain is the designated large-file owner.
            PairedUserIdentity.USER_B -> fileOwner != FileOwner.ZAIN
            else -> false
        }
    }

    /**
     * Get access prompt message for locked files.
     */
    fun getAccessPrompt(fileOwner: String, userId: String): String {
        return if (PairedUserIdentity.stableId(userId) == PairedUserIdentity.USER_A) {
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
