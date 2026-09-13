package com.calcvault.storage.provider

/**
 * StorageProvider
 *
 * Core abstraction for CalcVault's storage layer.
 *
 * Two implementations:
 *  - LocalStorageProvider  → encrypted files in app's internal storage (temp/dev mode)
 *  - USBStorageProvider    → encrypted container on USB drive (secure/production mode)
 *
 * Everything in the app talks to StorageProvider.
 * Nothing talks to USBStorageEngine or internal files directly.
 *
 * Key naming convention:
 *  "msg/{id}"          → message record
 *  "media/{id}/{chunk}"→ media chunk
 *  "prefs"             → preferences blob
 *  "ratchet/{userId}"  → ratchet state for a user
 *  "attempts"          → lockout attempt counter
 *  "index"             → record index
 *  "identity"          → identity record
 */
interface StorageProvider {

    // ── Core operations ────────────────────────────────────────────────────

    /**
     * Write encrypted data for a given key.
     * Append-only keys (prefixed with "msg/", "media/") must not be overwritten.
     * Mutable keys ("prefs", "ratchet/", "attempts") can be overwritten.
     */
    fun writeData(key: String, data: ByteArray): Boolean

    /**
     * Read data for a given key. Returns null if not found.
     */
    fun readData(key: String): ByteArray?

    /**
     * Soft-delete a record (writes a tombstone).
     * Append-only records are never truly deleted — tombstone appended instead.
     */
    fun deleteData(key: String): Boolean

    /**
     * List all keys, optionally filtered by prefix.
     */
    fun listKeys(prefix: String = ""): List<String>

    /**
     * Check if a key exists.
     */
    fun exists(key: String): Boolean = readData(key) != null

    // ── Batch operations ───────────────────────────────────────────────────

    /**
     * Write multiple key/value pairs atomically.
     * All succeed or all fail — no partial writes.
     */
    fun writeBatch(entries: Map<String, ByteArray>): Boolean {
        return entries.all { (k, v) -> writeData(k, v) }
    }

    /**
     * Read multiple keys in one operation.
     */
    fun readBatch(keys: List<String>): Map<String, ByteArray?> {
        return keys.associateWith { readData(it) }
    }

    // ── Metadata ────────────────────────────────────────────────────────────

    /**
     * Mode this provider is operating in.
     */
    val mode: StorageMode

    /**
     * True if the storage backend is currently available.
     * For USB: true when USB is mounted. For local: always true.
     */
    val isAvailable: Boolean

    /**
     * Approximate total storage used in bytes.
     */
    val usedBytes: Long

    /**
     * Human-readable status for the in-app warning banner.
     */
    val statusMessage: String

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Open/mount the storage backend. Called after authentication.
     */
    fun open(): Boolean

    /**
     * Flush and close. Called on lock, USB removal, or app minimized.
     */
    fun close()

    /**
     * Secure wipe of all storage. Called on vault destruction (≥20 attempts).
     */
    fun destroy(): Boolean
}

enum class StorageMode {
    LOCAL, // phone internal storage (less secure, no USB required)
    USB // USB drive (secure mode, requires physical USB)
}
