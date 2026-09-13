package com.calcvault.call.recording

import com.calcvault.crypto.E2EKeyManager
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.storage.container.EncryptedContainer
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * CallRecordingPipeline
 *
 * Chunk-based call recording pipeline with full USB storage integration.
 *
 * Architecture:
 *   PCM audio arrives from mic/network in small buffers (~20ms each)
 *   Buffers accumulate in RAM ring buffer (max 5MB)
 *   When ring buffer hits threshold OR 30s timer fires → flush chunk to USB
 *   Each chunk is encrypted with current session key before writing
 *   Chunk pointer written to call metadata DB
 *   On playback: chunks read from USB, decrypted, reassembled
 *
 * Chunk format (binary):
 *   [8B: callId]  [4B: chunkIndex] [4B: chunkCount_hint] [8B: timestamp]
 *   [4B: pcmRate] [4B: channels]   [4B: pcmLen]
 *   [N:  raw PCM payload]
 *
 * Ownership targets:
 *   MINE:    write to local USB only
 *   PARTNER: write to partner's USB on next sync
 *   BOTH:    write to local USB + flag for sync to partner
 *
 * Recording is never stored in plaintext.
 * If USB is removed mid-recording: remaining RAM buffer is wiped, not written.
 */
class CallRecordingPipeline(
    private val container: EncryptedContainer,
    private val messageDB: AppendOnlyMessageDB,
    private val keyManager: E2EKeyManager
) {
    enum class RecordingTarget { MINE, PARTNER, BOTH }

    companion object {
        private const val MAX_RAM_BUFFER_BYTES = 5 * 1024 * 1024 // 5MB max in RAM
        private const val FLUSH_INTERVAL_MS = 30_000L // flush every 30s
        private const val CHUNK_HEADER_LEN = 8 + 4 + 4 + 8 + 4 + 4 + 4 // 40 bytes
        private const val PCM_SAMPLE_RATE = 48_000
        private const val PCM_CHANNELS = 1 // mono for efficiency
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null
    private var isRecording = false
    private var currentCallId = 0L
    private var chunkIndex = 0
    private var target = RecordingTarget.BOTH
    private val chunkPointers = mutableListOf<Long>() // record IDs of written chunks

    // RAM ring buffer — audio bytes accumulate here before flush
    private val ramBuffer = mutableListOf<ByteArray>()
    private val ramBufferBytes = AtomicLong(0L)
    private val bufferLock = java.util.concurrent.locks.ReentrantLock()

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Start recording a call.
     * @param callId   unique call identifier
     * @param target   where to save the recording
     */
    fun startRecording(callId: Long, target: RecordingTarget) {
        this.currentCallId = callId
        this.target = target
        this.chunkIndex = 0
        this.isRecording = true
        chunkPointers.clear()
        ramBuffer.clear()
        ramBufferBytes.set(0L)

        // Periodic flush job
        flushJob = scope.launch {
            while (isActive && isRecording) {
                delay(FLUSH_INTERVAL_MS)
                flushBufferToUSB()
            }
        }
    }

    /**
     * Feed audio data into the recording pipeline.
     * Call this with every PCM audio buffer received from mic or network.
     * @param pcm   raw PCM audio bytes
     * @param local true if this is the local mic, false if remote audio
     */
    fun feedAudio(pcm: ByteArray, local: Boolean) {
        if (!isRecording) return

        // Check ownership — skip if target doesn't include this source
        if (local && target == RecordingTarget.PARTNER) return
        // Partner audio always buffered when target includes PARTNER or BOTH

        bufferLock.lock()
        try {
            ramBuffer.add(pcm.copyOf())
            ramBufferBytes.addAndGet(pcm.size.toLong())
        } finally {
            bufferLock.unlock()
        }

        // Auto-flush when buffer reaches max size
        if (ramBufferBytes.get() >= MAX_RAM_BUFFER_BYTES) {
            scope.launch { flushBufferToUSB() }
        }
    }

    /**
     * Stop recording and flush remaining buffer.
     * Returns metadata about the recording for saving to the DB.
     */
    suspend fun stopRecording(): RecordingMeta = withContext(Dispatchers.IO) {
        isRecording = false
        flushJob?.cancel()

        // Flush remaining buffer
        flushBufferToUSB()

        // Wipe RAM buffer
        bufferLock.lock()
        try {
            ramBuffer.forEach { it.fill(0) }
            ramBuffer.clear()
            ramBufferBytes.set(0L)
        } finally {
            bufferLock.unlock()
        }

        RecordingMeta(
            callId = currentCallId,
            chunkCount = chunkIndex,
            chunkPointers = chunkPointers.toList(),
            target = target,
            pcmSampleRate = PCM_SAMPLE_RATE,
            channels = PCM_CHANNELS
        )
    }

    /**
     * Emergency wipe — call when USB is removed mid-recording.
     * Wipes RAM buffer without writing to USB.
     */
    fun emergencyWipe() {
        isRecording = false
        flushJob?.cancel()
        bufferLock.lock()
        try {
            ramBuffer.forEach { it.fill(0) }
            ramBuffer.clear()
            ramBufferBytes.set(0L)
        } finally {
            bufferLock.unlock()
        }
        chunkPointers.clear()
    }

    // ── Chunk Assembly + Write ─────────────────────────────────────────────

    private suspend fun flushBufferToUSB() = withContext(Dispatchers.IO) {
        if (!isRecording && ramBufferBytes.get() == 0L) return@withContext

        // Drain buffer under lock
        val drainedChunks: List<ByteArray>
        bufferLock.lock()
        try {
            drainedChunks = ramBuffer.toList()
            ramBuffer.clear()
            ramBufferBytes.set(0L)
        } finally {
            bufferLock.unlock()
        }

        if (drainedChunks.isEmpty()) return@withContext

        // Assemble PCM payload
        val pcmPayload = drainedChunks.fold(ByteArray(0)) { acc, b -> acc + b }
        drainedChunks.forEach { it.fill(0) } // wipe source arrays

        // Build chunk header
        val header = ByteBuffer.allocate(CHUNK_HEADER_LEN).apply {
            putLong(currentCallId)
            putInt(chunkIndex)
            putInt(-1) // chunkCount_hint: unknown at write time
            putLong(System.currentTimeMillis())
            putInt(PCM_SAMPLE_RATE)
            putInt(PCM_CHANNELS)
            putInt(pcmPayload.size)
        }.array()

        val chunkData = header + pcmPayload

        // Encrypt with current session key
        val encrypted = try {
            keyManager.encrypt(chunkData)
        } catch (e: Exception) {
            pcmPayload.fill(0); chunkData.fill(0)
            return@withContext // session key gone (USB removed?) — abort
        } finally {
            pcmPayload.fill(0); chunkData.fill(0)
        }

        // Generate record ID for this chunk
        val recordId = System.nanoTime() + chunkIndex

        // Write encrypted chunk to USB container
        val ok = container.appendRecord(
            segmentType = EncryptedContainer.SEG_MEDIA_CHUNK,
            recordId = recordId,
            plaintext = encrypted
        )

        if (ok) {
            chunkPointers.add(recordId)
            chunkIndex++
        }

        encrypted.fill(0)
    }

    // ── Playback ──────────────────────────────────────────────────────────

    /**
     * Read and reassemble a recording from USB.
     * Returns PCM audio bytes in correct chunk order.
     * Never writes decrypted data to disk.
     */
    suspend fun readRecording(chunkIds: List<Long>): ByteArray? = withContext(Dispatchers.IO) {
        val chunks = mutableListOf<Pair<Int, ByteArray>>()

        for (id in chunkIds) {
            val encrypted = container.readRecord(id) ?: continue
            val chunkData = try {
                keyManager.decrypt(encrypted)
            } catch (e: Exception) { continue } finally { encrypted.fill(0) }

            // Parse header to get chunk index and PCM
            if (chunkData.size < CHUNK_HEADER_LEN) { chunkData.fill(0); continue }
            val buf = ByteBuffer.wrap(chunkData)
            buf.getLong() // callId
            val idx = buf.int
            buf.position(buf.position() + 4 + 8 + 4 + 4) // skip remaining header
            val pcmLen = buf.int
            val pcm = ByteArray(pcmLen)
            buf.get(pcm)
            chunkData.fill(0)

            chunks.add(Pair(idx, pcm))
        }

        // Sort by chunk index and concatenate
        val sorted = chunks.sortedBy { it.first }
        val result = sorted.fold(ByteArray(0)) { acc, (_, pcm) -> acc + pcm }
        sorted.forEach { (_, pcm) -> pcm.fill(0) }
        result
    }
}

// ── Data Models ────────────────────────────────────────────────────────────

data class RecordingMeta(
    val callId: Long,
    val chunkCount: Int,
    val chunkPointers: List<Long>,
    val target: CallRecordingPipeline.RecordingTarget,
    val pcmSampleRate: Int,
    val channels: Int
) {
    fun toJson(): String = org.json.JSONObject().apply {
        put("callId", callId)
        put("chunkCount", chunkCount)
        put("chunkPointers", org.json.JSONArray(chunkPointers))
        put("target", target.name)
        put("sampleRate", pcmSampleRate)
        put("channels", channels)
    }.toString()
}
