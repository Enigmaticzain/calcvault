package com.calcvault.crypto.ratchet

import com.calcvault.storage.provider.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * RatchetStateStore
 *
 * Persists and restores DoubleRatchet state to/from StorageManager.
 *
 * Called:
 *  - On vault open  → loadState() to restore previous ratchet position
 *  - On vault close → saveState() to persist current ratchet position
 *  - On lock/USB remove → saveState() then wipe() RAM
 *
 * Storage key: "ratchet/{localUserId}"
 *
 * The ratchet state blob itself is encrypted by StorageManager (AES-256-GCM),
 * so the chain keys, skipped message keys etc. are always encrypted at rest.
 *
 * Why this matters:
 *  Without persistence, a new ratchet starts from scratch on every open.
 *  That would break the chain — partner's messages sent while you were
 *  offline would be undecryptable because the chain state wouldn't match.
 */
object RatchetStateStore {

    private const val KEY_PREFIX = "ratchet/"

    /**
     * Save current ratchet state to storage.
     * Call on vault close or lock.
     */
    suspend fun saveState(ratchet: DoubleRatchet, localUserId: String) =
        withContext(Dispatchers.IO) {
            try {
                val state = ratchet.exportState()
                val json = JSONObject(state as Map<*, *>).toString()
                StorageManager.write("$KEY_PREFIX$localUserId", json.toByteArray())
            } catch (e: Exception) {
                // State save failed — ratchet will need re-init on next open
            }
        }

    /**
     * Load and restore ratchet state from storage.
     * Call after vault opens successfully.
     * Returns true if state was restored, false if fresh start needed.
     */
    suspend fun loadState(ratchet: DoubleRatchet, localUserId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val raw = StorageManager.read("$KEY_PREFIX$localUserId") ?: return@withContext false
                val json = JSONObject(String(raw))
                val map = json.keys().asSequence().associateWith { key ->
                    when (val v = json.get(key)) {
                        is Int -> v
                        is Long -> v
                        is String -> v
                        is org.json.JSONObject -> {
                            // Convert nested JSONObject (skipped keys map) to Map<String, String>
                            v.keys().asSequence().associateWith { k -> v.getString(k) }
                        }
                        else -> v.toString()
                    }
                }
                ratchet.importState(map)
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Clear persisted ratchet state (on vault destroy or account reset).
     */
    fun clearState(localUserId: String) {
        StorageManager.delete("$KEY_PREFIX$localUserId")
    }

    /**
     * Check if a saved ratchet state exists for this user.
     */
    fun hasState(localUserId: String): Boolean =
        StorageManager.exists("$KEY_PREFIX$localUserId")
}
