package com.calcvault.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * RemoteFileRefManager
 *
 * Manages metadata references to files stored on remote devices (e.g., Sanu's references to Zain's files).
 *
 * When a file is too large for Sanu to store locally, this manager creates a reference containing:
 * - File ID
 * - Name and size
 * - Owner (Zain)
 * - Thumbnail path
 * - Access state (LOCKED/AVAILABLE)
 * - Sync metadata
 *
 * Storage path:
 * /calcvault/storage/remote_refs.json
 */
class RemoteFileRefManager(private val storageRoot: String = "/calcvault/storage") {

    companion object {
        private const val TAG = "RemoteFileRefManager"
        const val REFS_FILENAME = "remote_refs.json"
    }

    /**
     * Remote references database
     */
    data class RemoteRefsDB(
        val version: String = "1.0",
        val createdAt: Long = System.currentTimeMillis(),
        val refs: MutableList<UserOwnershipManager.RemoteFileRef> = mutableListOf()
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("version", version)
            obj.put("createdAt", createdAt)

            val refsArray = JSONArray()
            refs.forEach { ref ->
                refsArray.put(ref.toJson())
            }
            obj.put("refs", refsArray)

            return obj
        }

        companion object {
            fun fromJson(obj: JSONObject): RemoteRefsDB {
                val refsArray = obj.optJSONArray("refs") ?: JSONArray()
                val refs = mutableListOf<UserOwnershipManager.RemoteFileRef>()

                for (i in 0 until refsArray.length()) {
                    val refObj = refsArray.getJSONObject(i)
                    UserOwnershipManager.RemoteFileRef.fromJson(refObj)?.let {
                        refs.add(it)
                    }
                }

                return RemoteRefsDB(
                    version = obj.getString("version"),
                    createdAt = obj.getLong("createdAt"),
                    refs = refs
                )
            }
        }
    }

    // In-memory cache
    private var db: RemoteRefsDB? = null

    // ─── Database Operations ───────────────────────────────────────────────

    /**
     * Load remote references from disk
     */
    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        try {
            val refFile = File(storageRoot, REFS_FILENAME)

            if (!refFile.exists()) {
                Log.d(TAG, "No existing refs file, creating new database")
                db = RemoteRefsDB()
                return@withContext true
            }

            val jsonString = refFile.readText()
            val jsonObj = JSONObject(jsonString)
            db = RemoteRefsDB.fromJson(jsonObj)

            Log.i(TAG, "Loaded ${db?.refs?.size ?: 0} remote references")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading remote references", e)
            db = RemoteRefsDB() // Fall back to empty
            false
        }
    }

    /**
     * Save remote references to disk
     */
    suspend fun save(): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = db ?: return@withContext false

            val refDir = File(storageRoot)
            if (!refDir.exists()) {
                refDir.mkdirs()
            }

            val refFile = File(refDir, REFS_FILENAME)
            refFile.writeText(db.toJson().toString(2))

            Log.d(TAG, "Saved ${db.refs.size} remote references")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving remote references", e)
            false
        }
    }

    // ─── Reference Management ──────────────────────────────────────────────

    /**
     * Add a new remote file reference
     */
    suspend fun addRef(ref: UserOwnershipManager.RemoteFileRef): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) {
                db = RemoteRefsDB()
            }

            // Remove if already exists
            db!!.refs.removeAll { it.id == ref.id }

            // Add new reference
            db!!.refs.add(ref)

            Log.i(TAG, "Added remote ref: ${ref.name} (${ref.owner})")
            save()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding remote reference", e)
            false
        }
    }

    /**
     * Get reference by ID
     */
    suspend fun getRef(id: String): UserOwnershipManager.RemoteFileRef? = withContext(Dispatchers.IO) {
        val db = db
        if (db == null) {
            load()
        }
        return@withContext db?.refs?.find { it.id == id }
    }

    /**
     * Get all references by owner
     */
    suspend fun getRefsByOwner(owner: String): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        val db = db
        if (db == null) {
            load()
        }
        return@withContext db?.refs?.filter { it.owner == owner } ?: emptyList()
    }

    /**
     * Get all references
     */
    suspend fun getAllRefs(): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        val db = db
        if (db == null) {
            load()
        }
        return@withContext db?.refs?.toList() ?: emptyList()
    }

    /**
     * Update reference access state
     */
    suspend fun updateAccessState(
        id: String,
        state: UserOwnershipManager.AccessState
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) {
                load()
            }

            val ref = db?.refs?.find { it.id == id } ?: return@withContext false
            val index = db!!.refs.indexOf(ref)

            db!!.refs[index] = ref.copy(
                accessState = state,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            Log.d(TAG, "Updated access state for ${ref.name}: $state")
            save()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating access state", e)
            false
        }
    }

    /**
     * Update thumbnail path
     */
    suspend fun updateThumbnail(id: String, thumbnailPath: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) {
                load()
            }

            val ref = db?.refs?.find { it.id == id } ?: return@withContext false
            val index = db!!.refs.indexOf(ref)

            db!!.refs[index] = ref.copy(thumbnailPath = thumbnailPath)

            Log.d(TAG, "Updated thumbnail for ${ref.name}")
            save()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating thumbnail", e)
            false
        }
    }

    /**
     * Remove reference
     */
    suspend fun removeRef(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) {
                load()
            }

            val removed = db?.refs?.removeAll { it.id == id } ?: return@withContext false

            if (removed) {
                Log.i(TAG, "Removed remote ref: $id")
                save()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing reference", e)
            false
        }
    }

    /**
     * Clear all references
     */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) {
                db = RemoteRefsDB()
            } else {
                db!!.refs.clear()
            }

            Log.i(TAG, "Cleared all remote references")
            save()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing references", e)
            false
        }
    }

    // ─── Search & Filter ───────────────────────────────────────────────────

    /**
     * Search references by name
     */
    suspend fun searchByName(query: String): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        val db = db
        if (db == null) {
            load()
        }

        return@withContext db?.refs?.filter {
            it.name.contains(query, ignoreCase = true)
        } ?: emptyList()
    }

    /**
     * Get references that are locked (require device connection)
     */
    suspend fun getLockedRefs(): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        val db = db
        if (db == null) {
            load()
        }

        return@withContext db?.refs?.filter {
            it.accessState == UserOwnershipManager.AccessState.LOCKED
        } ?: emptyList()
    }

    /**
     * Get references that are available
     */
    suspend fun getAvailableRefs(): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        val db = db
        if (db == null) {
            load()
        }

        return@withContext db?.refs?.filter {
            it.accessState == UserOwnershipManager.AccessState.AVAILABLE
        } ?: emptyList()
    }

    /**
     * Get statistics
     */
    suspend fun getStats(): RefStatistics = withContext(Dispatchers.IO) {
        val db = db
        if (db == null) {
            load()
        }

        val refs = db?.refs ?: emptyList()

        return@withContext RefStatistics(
            totalRefs = refs.size,
            totalSize = refs.sumOf { it.size },
            byOwner = refs.groupingBy { it.owner }.eachCount(),
            byAccessState = refs.groupingBy { it.accessState }.eachCount(),
            lockedCount = refs.count { it.accessState == UserOwnershipManager.AccessState.LOCKED },
            availableCount = refs.count { it.accessState == UserOwnershipManager.AccessState.AVAILABLE }
        )
    }

    data class RefStatistics(
        val totalRefs: Int = 0,
        val totalSize: Long = 0L,
        val byOwner: Map<String, Int> = emptyMap(),
        val byAccessState: Map<UserOwnershipManager.AccessState, Int> = emptyMap(),
        val lockedCount: Int = 0,
        val availableCount: Int = 0
    )

    // ─── Sync with Backup Index ────────────────────────────────────────────

    /**
     * Sync references with backup index (when backup is available)
     */
    suspend fun syncWithBackupIndex(
        backupIndex: BackupIndexer.BackupIndex
    ): Int = withContext(Dispatchers.IO) {
        var synced = 0

        try {
            for (indexedFile in backupIndex.files) {
                val ref = UserOwnershipManager.RemoteFileRef(
                    id = indexedFile.id,
                    name = indexedFile.name,
                    size = indexedFile.size,
                    owner = indexedFile.owner,
                    thumbnailPath = indexedFile.thumbnailPath,
                    accessState = UserOwnershipManager.AccessState.AVAILABLE,
                    timestamp = indexedFile.timestamp
                )

                if (addRef(ref)) {
                    synced++
                }
            }

            Log.i(TAG, "Synced $synced references from backup index")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with backup index", e)
        }

        return@withContext synced
    }
}
