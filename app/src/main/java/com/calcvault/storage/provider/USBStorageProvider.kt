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
            keyToIdMap[key] = recordId
            idToKeyMap[recordId] = key
            // Update container seal after every write
            seal.updateSeal(containerFile)
        }

        data.fill(0)
        return success
    }

    override fun readData(key: String): ByteArray? {
        val recordId = keyToIdMap[key] ?: return null
        return engine.readRecord(recordId)
    }

    override fun deleteData(key: String): Boolean {
        // USB storage is append-only — write tombstone
        val tombstoneData = "DELETED:$key".toByteArray()
        return writeData("tombstone/$key", tombstoneData)
    }

    override fun listKeys(prefix: String): List<String> {
        return keyToIdMap.keys.filter { it.startsWith(prefix) }.sorted()
    }

    override fun exists(key: String): Boolean = keyToIdMap.containsKey(key)

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
        // Read encrypted index from seal's index file
        val entries = seal.readIndex(derivedIndexKey())
        for ((id, entry) in entries) {
            if (id in skipIds) continue
            val keyStr = entry.key
            idToKeyMap[id] = keyStr
            keyToIdMap[keyStr] = id
            if (id >= nextId) nextId = id + 1
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
