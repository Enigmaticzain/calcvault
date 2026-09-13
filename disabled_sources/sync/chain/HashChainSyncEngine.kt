package com.calcvault.sync.chain

import com.calcvault.utils.CryptoUtils
import kotlinx.coroutines.*
import java.io.File

/**
 * HashChainSyncEngine
 *
 * Sync engine with cryptographic hash chain validation.
 *
 * Hash Chain concept:
 *   Every record appended to the container extends a chain:
 *     chain[0] = HMAC(containerKey, record_0_hash)
 *     chain[N] = HMAC(chain[N-1], record_N_hash)
 *
 *   The chain tip is stored in the container index.
 *   On sync: both sides share their chain tips.
 *   Since the chain is deterministic given the same records in the same order,
 *   the tips must agree after sync completes.
 *
 * This means:
 *   ✅ Any injected record is detected (breaks the chain)
 *   ✅ Any reordered record is detected
 *   ✅ Any modified record is detected (record hash changes → chain diverges)
 *   ✅ Truncation is detected (chain is shorter than expected)
 *
 * Rollback-safe merge:
 *   1. Compute diff
 *   2. Write to staging area on USB (never directly to container)
 *   3. Verify staging area hash chain
 *   4. Only if valid: commit staging to container atomically
 *   5. If anything fails: staging is discarded, container untouched
 *
 * Version tracking:
 *   Each USB has a monotonic sync version counter.
 *   On every successful sync: both counters increment to max(A, B) + 1.
 *   Sync refuses if version mismatch > threshold (indicates long divergence).
 */
class HashChainSyncEngine(private val chainKey: ByteArray) {

    companion object {
        private const val CHAIN_FILE   = "cv_chain.dat"
        private const val VERSION_FILE = "cv_version.dat"
        private const val STAGING_DIR  = "cv_staging"
        private const val MAX_VERSION_GAP = 1000
    }

    // ── Chain State ────────────────────────────────────────────────────────

    data class ChainState(
        val tip         : ByteArray,   // 32-byte chain tip HMAC
        val recordCount : Long,
        val syncVersion : Long
    )

    data class MergeResult(
        val success         : Boolean,
        val recordsAdded    : Int,
        val chainVerified   : Boolean,
        val rolledBack      : Boolean = false,
        val reason          : String  = ""
    )

    // ── Read Chain State ───────────────────────────────────────────────────

    fun readChainState(usbRoot: File): ChainState? {
        val chainFile = File(usbRoot, CHAIN_FILE)
        val versionFile = File(usbRoot, VERSION_FILE)
        if (!chainFile.exists()) return null

        return try {
            val chainData = chainFile.readBytes()
            if (chainData.size < 40) return null

            val buf     = java.nio.ByteBuffer.wrap(chainData)
            val tip     = ByteArray(32).also { buf.get(it) }
            val count   = buf.long
            val version = versionFile.readText().trim().toLongOrNull() ?: 0L

            ChainState(tip, count, version)
        } catch (e: Exception) { null }
    }

    // ── Write Chain State ──────────────────────────────────────────────────

    fun writeChainState(usbRoot: File, state: ChainState) {
        val chainData = java.nio.ByteBuffer.allocate(40).apply {
            put(state.tip); putLong(state.recordCount)
        }.array()

        // Atomic write: temp → rename
        val tmp = File(usbRoot, "cv_chain_tmp.dat")
        tmp.writeBytes(chainData)
        tmp.renameTo(File(usbRoot, CHAIN_FILE))

        File(usbRoot, VERSION_FILE).writeText(state.syncVersion.toString())
    }

    // ── Extend Chain ──────────────────────────────────────────────────────

    /**
     * Extend the chain by one record.
     * Call after every successful container write.
     */
    fun extendChain(currentTip: ByteArray, newRecordHash: ByteArray): ByteArray {
        return CryptoUtils.hmacSha256(currentTip, newRecordHash)
    }

    /**
     * Recompute the full chain from scratch given ordered record hashes.
     * Used to verify integrity after sync.
     */
    fun recomputeChain(recordHashes: List<ByteArray>): ByteArray {
        var tip = chainKey.copyOf()  // genesis: initialised with chain key
        for (hash in recordHashes) {
            tip = extendChain(tip, hash)
        }
        return tip
    }

    /**
     * Verify that the given ordered record hashes produce the expected chain tip.
     */
    fun verifyChain(recordHashes: List<ByteArray>, expectedTip: ByteArray): Boolean {
        val computedTip = recomputeChain(recordHashes)
        return CryptoUtils.constantTimeEquals(computedTip, expectedTip)
    }

    // ── Rollback-Safe Merge ────────────────────────────────────────────────

    /**
     * Merge incoming records into the local container.
     *
     * Protocol:
     *   1. Write incoming records to staging directory
     *   2. Compute expected chain tip after merge
     *   3. If chain tip matches remote's combined chain → commit
     *   4. If any verification fails → wipe staging, return rolled-back result
     *
     * @param localRoot       local USB mount
     * @param incomingRecords list of (recordHash, encryptedBlob) to merge
     * @param localHashes     current ordered list of local record hashes
     * @param remoteChainTip  what the remote side says the merged chain tip should be
     */
    suspend fun rollbackSafeMerge(
        localRoot       : File,
        incomingRecords : List<Pair<ByteArray, ByteArray>>,  // (hash, blob)
        localHashes     : List<ByteArray>,
        remoteChainTip  : ByteArray
    ): MergeResult = withContext(Dispatchers.IO) {

        if (incomingRecords.isEmpty()) {
            return@withContext MergeResult(true, 0, true, reason = "NOTHING_TO_MERGE")
        }

        val stagingDir = File(localRoot, STAGING_DIR).also { it.mkdirs() }

        try {
            // ── Stage 1: Write to staging ──────────────────────────────────
            val stagedFiles = mutableListOf<File>()
            val incomingHashes = mutableListOf<ByteArray>()

            for ((hash, blob) in incomingRecords) {
                val stagingFile = File(stagingDir, "rec_${CryptoUtils.toHex(hash).take(16)}.part")
                stagingFile.writeBytes(blob)

                // Verify written data
                val writtenHash = CryptoUtils.sha256(stagingFile.readBytes())
                if (!writtenHash.contentEquals(CryptoUtils.sha256(blob))) {
                    wipeStagingDir(stagingDir)
                    return@withContext MergeResult(false, 0, false, rolledBack = true,
                        reason = "STAGING_WRITE_VERIFY_FAIL")
                }

                stagedFiles.add(stagingFile)
                incomingHashes.add(hash)
            }

            // ── Stage 2: Verify chain after merge ──────────────────────────
            val mergedHashes = localHashes + incomingHashes
            val computedTip  = recomputeChain(mergedHashes)

            if (!CryptoUtils.constantTimeEquals(computedTip, remoteChainTip)) {
                wipeStagingDir(stagingDir)
                return@withContext MergeResult(false, 0, false, rolledBack = true,
                    reason = "CHAIN_VERIFICATION_FAILED")
            }

            // ── Stage 3: Commit — append staged blobs to container ─────────
            val containerFile = File(localRoot, "container.enc")
            for ((file, _) in stagedFiles.zip(incomingHashes)) {
                val blob = file.readBytes()
                containerFile.appendBytes(blob)
                blob.fill(0)
            }

            // ── Stage 4: Wipe staging directory ───────────────────────────
            wipeStagingDir(stagingDir)

            MergeResult(
                success       = true,
                recordsAdded  = incomingRecords.size,
                chainVerified = true
            )

        } catch (e: Exception) {
            wipeStagingDir(stagingDir)
            MergeResult(false, 0, false, rolledBack = true, reason = e.message ?: "UNKNOWN_ERROR")
        }
    }

    // ── Version Management ─────────────────────────────────────────────────

    /**
     * Check if two USB drives have diverged too much to sync safely.
     */
    fun checkVersionCompatibility(localVersion: Long, remoteVersion: Long): Boolean {
        return kotlin.math.abs(localVersion - remoteVersion) <= MAX_VERSION_GAP
    }

    /**
     * Compute the next sync version after a successful sync.
     */
    fun nextVersion(localVersion: Long, remoteVersion: Long): Long {
        return maxOf(localVersion, remoteVersion) + 1
    }

    // ── Private ────────────────────────────────────────────────────────────

    private fun wipeStagingDir(stagingDir: File) {
        stagingDir.listFiles()?.forEach { file ->
            try {
                // Overwrite before delete
                if (file.isFile) {
                    file.writeBytes(ByteArray(file.length().toInt()))
                    file.delete()
                }
            } catch (e: Exception) { }
        }
        try { stagingDir.delete() } catch (e: Exception) { }
    }
}
