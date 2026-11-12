package com.calcvault.storage.provider

import android.content.Context
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.journal.WriteJournal
import com.calcvault.storage.seal.ContainerSeal
import com.calcvault.storage.seal.IndexEntry
import com.calcvault.utils.CryptoUtils
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * USBStorageProvider
 *
 * Wraps USBStorageEngine + EncryptedContainer into the StorageProvider interface.
 * This is the secure production mode — all data lives on the USB drive.
 */
class USBStorageProvider(
    private val context: Context,
    private val usbRoot: File,
    private val masterKey: ByteArray, // 32-byte container master key
    macKey: ByteArray // 32-byte HMAC key (derived from master)
) : StorageProvider {

    companion object {
        private const val CONTAINER_FILE = "container.enc"
    }

    private val containerFile = File(usbRoot, CONTAINER_FILE)
    private val journal = WriteJournal(usbRoot)
    private val seal = ContainerSeal(usbRoot, macKey)
    private val engine = USBStorageEngine.getInstance(context)

    // In-memory index rebuilt on open
    private val keyToIdMap = mutableMapOf<String, Long>() // key → record ID
    private val idToKeyMap = mutableMapOf<Long, String>() // record ID → key
    private var nextId = System.currentTimeMillis()

    override val mode = StorageMode.USB
    override val isAvailable get() = usbRoot.exists() && containerFile.exists()
    override val usedBytes get() = containerFile.length()
    override val statusMessage = "🔒 USB mode — fully secure."

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun open(): Boolean {
        // 1. Verify container seal (anti-tamper)
        val sealResult = seal.verifySeal(containerFile)
        if (sealResult == ContainerSeal.SealResult.TAMPER_DETECTED) return false

        // 2. Recover any incomplete writes from last session
        val incomplete = journal.open()

        // 3. Rebuild index from engine
        rebuildIndex(incomplete.toSet())

        return true
    }

    override fun close() {
        // Flush index to encrypted index file
        seal.writeIndex(
            buildIndexEntries(),
            derivedIndexKey()
        )
        journal.logCheckpoint("CLOSE")
        keyToIdMap.clear(); idToKeyMap.clear()
        engine.lock()
    }

    override fun destroy(): Boolean {
        val engine = USBStorageEngine.getInstance(context)
        var ok = false
        runBlocking {
            ok = engine.destroyContainer()
        }
        return ok
    }

    // ── Core operations ─────────────────────────────────────────────────────

    override fun writeData(key: String, data: ByteArray): Boolean {
        val recordId = generateId()
        val segType = keyToSegmentType(key)

        val success = runBlocking {
            engine.appendRecord(segType, recordId, data)
        }

        if (success) {
            val replacedId = keyToIdMap.put(key, recordId)
            if (replacedId != null) idToKeyMap.remove(replacedId)
            idToKeyMap[recordId] = key
            // Update container seal after every write
            seal.updateSeal(containerFile)
        }

        data.fill(0)
        return success
    }

    override fun readData(key: String): ByteArray? {
        if (key.startsWith("tombstone/")) return null
        val recordId = keyToIdMap[key] ?: return null
        return engine.readRecord(recordId)
    }

    override fun deleteData(key: String): Boolean {
        val existingId = keyToIdMap[key] ?: return true
        if (key.startsWith("tombstone/")) return false

        // USB storage is append-only. Persist an encrypted tombstone, then remove the key
        // from the active in-memory index so the deleted value cannot be read this session.
        val tombstoneData = "DELETED:$key".toByteArray()
        val tombstoneKey = "tombstone/${CryptoUtils.sha256hex(key.toByteArray()).take(32)}_${generateId()}"
        val success = writeData(tombstoneKey, tombstoneData)
        if (success) {
            keyToIdMap.remove(key)
            idToKeyMap.remove(existingId)
        }
        return success
    }

    override fun listKeys(prefix: String): List<String> {
        return keyToIdMap.keys.filter { !it.startsWith("tombstone/") && it.startsWith(prefix) }.sorted()
    }

    override fun exists(key: String): Boolean = !key.startsWith("tombstone/") && keyToIdMap.containsKey(key)

    // ── Private ────────────────────────────────────────────────────────────

    private fun keyToSegmentType(key: String): Int {
        return when {
            key.startsWith("msg/") -> 0x02
            key.startsWith("media/") -> 0x03
            key.startsWith("tombstone/") -> 0x06
            key.startsWith("ratchet/") -> 0x01
            key == "prefs" -> 0x01
            key == "attempts" -> 0x01
            key == "identity" -> 0x01
            else -> 0x05
        }
    }

    private fun generateId(): Long = nextId++

    private fun rebuildIndex(skipIds: Set<Long>) {
        keyToIdMap.clear(); idToKeyMap.clear()
        // Replay the sealed append-only index in record order so replacements and
        // tombstones survive detach/restart instead of resurrecting deleted keys.
        val activeKeys = linkedMapOf<String, Long>()
        val entries = seal.readIndex(derivedIndexKey()).toSortedMap()
        for ((id, entry) in entries) {
            if (id in skipIds) continue
            if (id >= nextId) nextId = id + 1
            val key = entry.key
            if (key.startsWith("tombstone/")) {
                val record = engine.readRecord(id)?.toString(Charsets.UTF_8) ?: continue
                if (record.startsWith("DELETED:")) activeKeys.remove(record.removePrefix("DELETED:"))
            } else {
                activeKeys[key] = id
            }
        }
        activeKeys.forEach { (key, id) ->
            keyToIdMap[key] = id
            idToKeyMap[id] = key
        }
    }

    private fun buildIndexEntries(): Map<Long, IndexEntry> {
        val container = engine.container
        return idToKeyMap.entries.associate { (id, key) ->
            val location = container?.getRecordLocation(id)
            id to IndexEntry(
                id = id,
                offset = location?.first ?: 0L, len = location?.second ?: 0,
                type = keyToSegmentType(key),
                hash = CryptoUtils.sha256hex(key.toByteArray()),
                key = key
            )
        }
    }

    private fun derivedIndexKey(): ByteArray {
        return CryptoUtils.hmacSha256(masterKey, "cv-index-key-v1".toByteArray()).copyOf(32)
    }
}
