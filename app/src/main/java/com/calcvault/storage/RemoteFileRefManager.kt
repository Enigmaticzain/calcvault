package com.calcvault.storage

import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Remote references are local metadata only. This manager does not transfer files or enforce
 * grants on an owner device; authorization here is advisory until a remote enforcement service
 * checks the same grant before each read.
 */
class RemoteFileRefManager(private val storageRoot: String) {

    companion object {
        private const val TAG = "RemoteFileRefManager"
        const val REFS_FILENAME = "remote_refs.json"
    }

    data class RemoteRefsDB(
        val version: String = "1.0",
        val createdAt: Long = System.currentTimeMillis(),
        val refs: MutableList<UserOwnershipManager.RemoteFileRef> = mutableListOf()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("version", version)
            put("createdAt", createdAt)
            put("refs", JSONArray().apply { refs.forEach { put(it.toJson()) } })
        }

        companion object {
            fun fromJson(obj: JSONObject): RemoteRefsDB {
                val refsArray = obj.optJSONArray("refs") ?: JSONArray()
                val refs = mutableListOf<UserOwnershipManager.RemoteFileRef>()
                for (i in 0 until refsArray.length()) {
                    UserOwnershipManager.RemoteFileRef.fromJson(refsArray.getJSONObject(i))?.let(refs::add)
                }
                return RemoteRefsDB(
                    version = obj.optString("version", "1.0"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    refs = refs
                )
            }
        }
    }

    // In-memory cache; grant/revoke mutations are serialized within this manager instance.
    private var db: RemoteRefsDB? = null
    private val grantMutationMutex = Mutex()

    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        try {
            val refFile = File(storageRoot, REFS_FILENAME)
            val backupFile = File("${refFile.path}.bak")
            if (!refFile.exists() && !backupFile.exists()) {
                Log.d(TAG, "No existing refs file, creating new database")
                db = RemoteRefsDB()
                return@withContext true
            }
            val jsonString = AtomicFile(refFile).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            db = RemoteRefsDB.fromJson(JSONObject(jsonString))
            Log.i(TAG, "Loaded ${db?.refs?.size ?: 0} remote references")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading remote references", e)
            db = RemoteRefsDB()
            false
        }
    }

    suspend fun save(): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = db ?: return@withContext false
            val refDir = File(storageRoot)
            if (!refDir.isDirectory && !refDir.mkdirs()) return@withContext false
            val atomicFile = AtomicFile(File(refDir, REFS_FILENAME))
            var stream: java.io.FileOutputStream? = null
            try {
                val pending = atomicFile.startWrite()
                stream = pending
                pending.write(current.toJson().toString(2).toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(pending)
                stream = null
            } finally {
                stream?.let { pending -> runCatching { atomicFile.failWrite(pending) } }
            }
            Log.d(TAG, "Saved ${current.refs.size} remote references")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving remote references", e)
            false
        }
    }

    suspend fun addRef(ref: UserOwnershipManager.RemoteFileRef): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) load()
            val current = db ?: RemoteRefsDB().also { db = it }
            current.refs.removeAll { it.id == ref.id }
            current.refs.add(ref)
            save()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding remote reference", e)
            false
        }
    }

    suspend fun getRef(id: String): UserOwnershipManager.RemoteFileRef? = withContext(Dispatchers.IO) {
        if (db == null) load()
        db?.refs?.find { it.id == id }
    }

    suspend fun getRefsByOwner(owner: String): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        if (db == null) load()
        db?.refs?.filter { it.owner == owner } ?: emptyList()
    }

    suspend fun getAllRefs(): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        if (db == null) load()
        db?.refs?.toList() ?: emptyList()
    }

    /** Grant one named user future access in this local reference metadata. */
    suspend fun grantAccess(
        id: String,
        ownerId: String,
        userId: String,
        expiresAtMs: Long = 0L
    ): Boolean = withContext(Dispatchers.IO) {
        grantMutationMutex.lock()
        try {
            val owner = ownerId.trim()
            val user = userId.trim()
            if (id.isBlank() || owner.isBlank() || user.isBlank() || user.equals(owner, ignoreCase = true) ||
                (expiresAtMs > 0L && expiresAtMs <= System.currentTimeMillis())
            ) return@withContext false
            if (db == null) load()
            val refs = db?.refs ?: return@withContext false
            val index = refs.indexOfFirst { it.id == id && it.owner.equals(owner, ignoreCase = true) }
            if (index < 0) return@withContext false
            val ref = refs[index]
            val grantExpiries = ref.userGrantExpiriesMs.toMutableMap()
            grantExpiries.keys.filter { it.equals(user, ignoreCase = true) }.forEach(grantExpiries::remove)
            grantExpiries[user] = expiresAtMs
            refs[index] = ref.copy(
                accessState = UserOwnershipManager.AccessState.AVAILABLE,
                allowedUsers = ref.allowedUsers.filterNot { it.equals(user, ignoreCase = true) },
                revoked = false,
                userGrantExpiriesMs = grantExpiries,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            save()
        } finally {
            grantMutationMutex.unlock()
        }
    }

    /** Revoke future local checks. Previously downloaded copies cannot be recalled. */
    suspend fun revokeAccess(id: String, ownerId: String, userId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            grantMutationMutex.lock()
            try {
                val owner = ownerId.trim()
                if (id.isBlank() || owner.isBlank()) return@withContext false
                if (db == null) load()
                val refs = db?.refs ?: return@withContext false
                val index = refs.indexOfFirst { it.id == id && it.owner.equals(owner, ignoreCase = true) }
                if (index < 0) return@withContext false
                val ref = refs[index]
                val remainingLegacyUsers = if (userId.isNullOrBlank()) emptyList() else
                    ref.allowedUsers.filterNot { it.equals(userId.trim(), ignoreCase = true) }
                val remainingGrants = if (userId.isNullOrBlank()) emptyMap() else
                    ref.userGrantExpiriesMs.filterNot { it.key.equals(userId.trim(), ignoreCase = true) }
                if (!userId.isNullOrBlank() && remainingLegacyUsers.size == ref.allowedUsers.size &&
                    remainingGrants.size == ref.userGrantExpiriesMs.size
                ) return@withContext false

                val now = System.currentTimeMillis()
                val liveLegacyUsers = remainingLegacyUsers.filter {
                    ref.expiresAtMs == 0L || now < ref.expiresAtMs
                }
                val liveGrants = remainingGrants.filterValues { expiry -> expiry == 0L || now < expiry }
                val hasGrants = liveLegacyUsers.isNotEmpty() || liveGrants.isNotEmpty()
                refs[index] = ref.copy(
                    accessState = if (hasGrants) UserOwnershipManager.AccessState.AVAILABLE
                    else UserOwnershipManager.AccessState.LOCKED,
                    allowedUsers = liveLegacyUsers,
                    revoked = !hasGrants,
                    userGrantExpiriesMs = liveGrants,
                    lastSyncTimestamp = now
                )
                save()
            } finally {
                grantMutationMutex.unlock()
            }
        }

    /** AVAILABLE does not grant access without a named, unexpired recipient grant. */
    suspend fun updateAccessState(
        id: String,
        state: UserOwnershipManager.AccessState
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) load()
            val current = db ?: return@withContext false
            val index = current.refs.indexOfFirst { it.id == id }
            if (index < 0) return@withContext false
            current.refs[index] = current.refs[index].copy(
                accessState = state,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            save()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating access state", e)
            false
        }
    }

    suspend fun updateThumbnail(id: String, thumbnailPath: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) load()
            val current = db ?: return@withContext false
            val index = current.refs.indexOfFirst { it.id == id }
            if (index < 0) return@withContext false
            current.refs[index] = current.refs[index].copy(thumbnailPath = thumbnailPath)
            save()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating thumbnail", e)
            false
        }
    }

    suspend fun removeRef(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (db == null) load()
            val current = db ?: return@withContext false
            val removed = current.refs.removeAll { it.id == id }
            if (removed) save() else false
        } catch (e: Exception) {
            Log.e(TAG, "Error removing reference", e)
            false
        }
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        db = RemoteRefsDB()
        save()
    }

    suspend fun searchByName(query: String): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        if (db == null) load()
        val needle = query.trim().lowercase()
        db?.refs?.filter { it.name.lowercase().contains(needle) } ?: emptyList()
    }

    suspend fun getLockedRefs(): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        if (db == null) load()
        db?.refs?.filter { it.accessState == UserOwnershipManager.AccessState.LOCKED } ?: emptyList()
    }

    suspend fun getAvailableRefs(): List<UserOwnershipManager.RemoteFileRef> = withContext(Dispatchers.IO) {
        if (db == null) load()
        db?.refs?.filter { it.accessState == UserOwnershipManager.AccessState.AVAILABLE } ?: emptyList()
    }

    data class RefStatistics(
        val totalCount: Int,
        val lockedCount: Int,
        val availableCount: Int,
        val totalSize: Long,
        val byOwner: Map<String, Int>
    )

    suspend fun getStats(): RefStatistics = withContext(Dispatchers.IO) {
        if (db == null) load()
        val refs = db?.refs.orEmpty()
        RefStatistics(
            totalCount = refs.size,
            lockedCount = refs.count { it.accessState == UserOwnershipManager.AccessState.LOCKED },
            availableCount = refs.count { it.accessState == UserOwnershipManager.AccessState.AVAILABLE },
            totalSize = refs.sumOf { it.size },
            byOwner = refs.groupingBy { it.owner }.eachCount()
        )
    }

    suspend fun syncWithBackupIndex(backupIndex: BackupIndexer.BackupIndex): Int = withContext(Dispatchers.IO) {
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
                if (addRef(ref)) synced++
            }
            Log.i(TAG, "Synced $synced references from backup index")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with backup index", e)
        }
        synced
    }
}
