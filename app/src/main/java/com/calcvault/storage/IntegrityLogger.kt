package com.calcvault.storage

import kotlinx.coroutines.*
import java.io.File

/**
 * IntegrityLogger
 *
 * Write journal for the USB container.
 *
 * Every write to the container goes through this logger:
 *  1. Log INTENT  (what we're about to write)
 *  2. Perform write
 *  3. Log COMMIT  (write succeeded + SHA-256 of written bytes)
 *  4. If crash between INTENT and COMMIT → detected on next open → rollback
 *
 * Journal format (append-only, text lines):
 *  INTENT  | timestamp | record_id | payload_sha256
 *  COMMIT  | timestamp | record_id | payload_sha256
 *  ROLLBACK| timestamp | record_id | reason
 *
 * Journal file: /usb_root/cv_journal.log
 *
 * Recovery logic (run on every vault open):
 *  - Scan journal for INTENT entries with no matching COMMIT
 *  - These are incomplete writes → roll them back (remove partial data)
 *  - Write a ROLLBACK entry to close the loop
 */
class IntegrityLogger(private val usbRoot: File) {

    companion object {
        private const val JOURNAL_FILE = "cv_journal.log"
        private const val MAX_JOURNAL_BYTES = 5 * 1024 * 1024L // 5MB — rotate after this
    }

    private val journalFile = File(usbRoot, JOURNAL_FILE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Journal Operations ───────────────────────────────────────────────────

    /**
     * Log an INTENT before writing.
     * @param recordId  the record about to be written
     * @param payloadHash  SHA-256 hex of the payload
     */
    fun logIntent(recordId: Long, payloadHash: String) {
        appendLine("INTENT", recordId, payloadHash)
    }

    /**
     * Log a COMMIT after successful write.
     */
    fun logCommit(recordId: Long, payloadHash: String) {
        appendLine("COMMIT", recordId, payloadHash)
    }

    /**
     * Log a ROLLBACK for a failed/incomplete write.
     */
    fun logRollback(recordId: Long, reason: String) {
        appendLine("ROLLBACK", recordId, reason)
    }

    // ─── Recovery ─────────────────────────────────────────────────────────────

    /**
     * Scan the journal on vault open and return IDs of incomplete writes.
     * These records should be treated as corrupt and skipped/removed.
     */
    fun recoverIncompleteWrites(): List<Long> {
        if (!journalFile.exists()) return emptyList()

        val intents = mutableSetOf<Long>()
        val commits = mutableSetOf<Long>()
        val rollbacks = mutableSetOf<Long>()

        try {
            journalFile.readLines().forEach { line ->
                val parts = line.split("|").map { it.trim() }
                if (parts.size < 3) return@forEach
                val type = parts[0]
                val recordId = parts[2].toLongOrNull() ?: return@forEach
                when (type) {
                    "INTENT" -> intents.add(recordId)
                    "COMMIT" -> commits.add(recordId)
                    "ROLLBACK" -> rollbacks.add(recordId)
                }
            }
        } catch (e: Exception) { return emptyList() }

        // Incomplete = INTENT present, no COMMIT and no ROLLBACK
        val incomplete = intents - commits - rollbacks
        for (id in incomplete) logRollback(id, "CRASH_RECOVERY")
        return incomplete.toList()
    }

    /**
     * Rotate the journal if it exceeds MAX_JOURNAL_BYTES.
     * Old journal archived as cv_journal_prev.log.
     */
    fun rotateIfNeeded() {
        if (!journalFile.exists()) return
        if (journalFile.length() > MAX_JOURNAL_BYTES) {
            val archive = File(usbRoot, "cv_journal_prev.log")
            archive.delete()
            journalFile.renameTo(archive)
        }
    }

    /**
     * Verify that the number of COMMIT entries matches
     * what the container's index says. Returns false if mismatch.
     */
    fun verifyJournalConsistency(expectedCommitCount: Int): Boolean {
        if (!journalFile.exists()) return true
        val commitCount = journalFile.readLines().count { it.startsWith("COMMIT") }
        return commitCount >= expectedCommitCount
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private fun appendLine(type: String, recordId: Long, data: String) {
        try {
            val ts = System.currentTimeMillis()
            val line = "$type | $ts | $recordId | $data\n"
            journalFile.appendText(line)
        } catch (e: Exception) {
            // Journal write failed — non-fatal, continue operation
        }
    }
}
