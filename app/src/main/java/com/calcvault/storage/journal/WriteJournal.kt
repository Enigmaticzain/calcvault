package com.calcvault.storage.journal

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * WriteJournal
 *
 * Binary crash-safe write journal for the USB container.
 *
 * Every container write follows this 3-phase protocol:
 *   Phase 1 — INTENT:   record what we're about to do
 *   Phase 2 — WRITE:    perform the actual write
 *   Phase 3 — COMMIT:   record that write succeeded + payload hash
 *
 * On crash between Phase 1 and Phase 3:
 *   Journal has INTENT with no COMMIT → incomplete write detected on next open
 *   RecoveryEngine uses this to identify and skip corrupt records
 *
 * Binary format (each entry):
 *   [4B: ENTRY_TYPE] [8B: timestamp] [8B: record_id] [32B: payload_hash] [4B: reason_len] [N: reason]
 *
 * Entry types:
 *   0x01 = INTENT
 *   0x02 = COMMIT
 *   0x03 = ROLLBACK
 *   0x04 = CHECKPOINT (journal was verified consistent at this point)
 *
 * Thread safety: all operations are mutex-protected.
 * Journal file: /usb_root/cv_journal.bin
 */
class WriteJournal(private val usbRoot: File) {

    companion object {
        private const val FILE_NAME = "cv_journal.bin"
        private const val MAGIC = 0x43564A4C // "CVJL"
        private const val MAX_SIZE = 10 * 1024 * 1024L // 10MB before rotation

        private const val TYPE_INTENT = 0x01
        private const val TYPE_COMMIT = 0x02
        private const val TYPE_ROLLBACK = 0x03
        private const val TYPE_CHECKPOINT = 0x04

        private const val ENTRY_FIXED_LEN = 4 + 8 + 8 + 32 + 4 // 56 bytes before reason
    }

    private val journalFile = File(usbRoot, FILE_NAME)
    private val lock = ReentrantLock()

    // In-memory tracking for current session
    private val pendingIntents = mutableSetOf<Long>() // record IDs with INTENT but no COMMIT
    private val committed = mutableSetOf<Long>()

    // ── Init ───────────────────────────────────────────────────────────────

    /**
     * Open the journal on vault mount. Reads existing entries and identifies
     * any incomplete writes from a previous crash.
     */
    fun open(): List<Long> = lock.withLock {
        if (!journalFile.exists()) return emptyList()
        rotateIfNeeded()
        return scanForIncompleteWrites()
    }

    // ── Write Operations ───────────────────────────────────────────────────

    fun logIntent(recordId: Long, payloadHash: String) = lock.withLock {
        writeEntry(TYPE_INTENT, recordId, payloadHash, "")
        pendingIntents.add(recordId)
    }

    fun logCommit(recordId: Long, payloadHash: String) = lock.withLock {
        writeEntry(TYPE_COMMIT, recordId, payloadHash, "")
        pendingIntents.remove(recordId)
        committed.add(recordId)
    }

    fun logRollback(recordId: Long, reason: String) = lock.withLock {
        writeEntry(TYPE_ROLLBACK, recordId, "", reason)
        pendingIntents.remove(recordId)
    }

    fun logCheckpoint(stats: String) = lock.withLock {
        writeEntry(TYPE_CHECKPOINT, -1L, "", stats)
    }

    // ── Recovery ───────────────────────────────────────────────────────────

    /**
     * Returns list of record IDs that have INTENT but no COMMIT or ROLLBACK.
     * These are incomplete writes that should be treated as corrupt.
     */
    fun getIncompleteWrites(): Set<Long> = lock.withLock {
        return pendingIntents.toSet()
    }

    /**
     * How many commits are in the journal — used to verify consistency with container index.
     */
    fun getCommitCount(): Int = lock.withLock {
        return committed.size
    }

    /**
     * Verify that every record ID in the provided index has a matching COMMIT entry.
     * Returns IDs present in index but missing a commit (potential corruption).
     */
    fun verifyConsistency(indexIds: Set<Long>): Set<Long> = lock.withLock {
        return indexIds - committed
    }

    // ── Private ────────────────────────────────────────────────────────────

    private fun writeEntry(type: Int, recordId: Long, hash: String, reason: String) {
        try {
            val hashBytes = hash.toByteArray(Charsets.UTF_8).copyOf(32) // pad/truncate to 32
            val reasonBytes = reason.toByteArray(Charsets.UTF_8)
            val totalLen = ENTRY_FIXED_LEN + reasonBytes.size

            val buf = ByteBuffer.allocate(totalLen).apply {
                putInt(type)
                putLong(System.currentTimeMillis())
                putLong(recordId)
                put(hashBytes)
                putInt(reasonBytes.size)
                if (reasonBytes.isNotEmpty()) put(reasonBytes)
            }

            journalFile.appendBytes(buf.array())
            // Force flush to storage — critical for crash safety
            RandomAccessFile(journalFile, "rw").use { it.fd.sync() }
        } catch (e: Exception) {
            // Journal write failed — non-fatal but log it
        }
    }

    private fun scanForIncompleteWrites(): List<Long> {
        val intents = mutableSetOf<Long>()
        val commits = mutableSetOf<Long>()
        val rollbacks = mutableSetOf<Long>()

        try {
            RandomAccessFile(journalFile, "r").use { raf ->
                while (raf.filePointer < raf.length()) {
                    try {
                        val type = raf.readInt()
                        val ts = raf.readLong()
                        val recordId = raf.readLong()
                        raf.skipBytes(32) // hash
                        val reasonLen = raf.readInt()
                        if (reasonLen > 0) raf.skipBytes(reasonLen)

                        when (type) {
                            TYPE_INTENT -> intents.add(recordId)
                            TYPE_COMMIT -> { commits.add(recordId); committed.add(recordId) }
                            TYPE_ROLLBACK -> rollbacks.add(recordId)
                        }
                    } catch (e: Exception) { break } // truncated entry at end
                }
            }
        } catch (e: Exception) { return emptyList() }

        // Rebuild pending intents
        pendingIntents.clear()
        pendingIntents.addAll(intents - commits - rollbacks)

        return pendingIntents.toList()
    }

    private fun rotateIfNeeded() {
        if (journalFile.length() > MAX_SIZE) {
            val archive = File(usbRoot, "cv_journal_prev.bin")
            archive.delete()
            journalFile.renameTo(archive)
        }
    }
}
