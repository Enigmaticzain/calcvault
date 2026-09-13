package com.calcvault.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import com.calcvault.storage.provider.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * MediaChunkManager
 *
 * Stores media chunks in the encrypted container while keeping metadata in a
 * separate namespace so media chunk 0 can never collide with metadata again.
 */
class MediaChunkManager(
    private val engine: USBStorageEngine,
    private val messageDB: AppendOnlyMessageDB
) {
    companion object {
        private const val MAX_CHUNK_BYTES = 2 * 1024 * 1024
        private const val THUMB_WIDTH = 320
        private const val THUMB_HEIGHT = 240
        private const val JPEG_QUALITY = 80
        private const val META_KEY_PREFIX = "media/meta/"
        private const val META_SCHEMA_VERSION = 2
        private const val MAX_RECOVERY_SCAN_CHUNKS = 4_096
        private val NEXT_MEDIA_ID = AtomicLong(0L)
    }

    enum class MediaOwnership { MINE, PARTNER, BOTH }
    enum class MediaType { IMAGE, VIDEO, AUDIO, FILE }

    data class MediaRef(
        val mediaId: Long,
        val type: MediaType,
        val chunkCount: Int,
        val totalBytes: Long,
        val mimeType: String,
        val ownership: MediaOwnership,
        val thumbHash: String,
        val createdAt: Long,
        val fileName: String = "",
        val chunkScheme: Int = USBStorageEngine.MEDIA_CHUNK_SCHEME_LEGACY
    )

    private data class MessageMetadataHint(
        val type: MediaType,
        val mimeType: String,
        val thumbHash: String,
        val createdAt: Long,
        val fileName: String
    )

    private data class ChunkScanResult(
        val chunkCount: Int,
        val totalBytes: Long
    )

    suspend fun storeMedia(
        rawBytes: ByteArray,
        type: MediaType,
        mimeType: String,
        ownership: MediaOwnership = MediaOwnership.BOTH,
        fileName: String = ""
    ): MediaRef = withContext(Dispatchers.IO) {
        val mediaId = generateMediaId()
        val createdAt = System.currentTimeMillis()
        val chunkScheme = USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED
        val chunks = rawBytes.toChunks(MAX_CHUNK_BYTES)
        if (chunks.isEmpty()) {
            throw IllegalArgumentException("Cannot store empty media payload")
        }

        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val wrote = engine.writeMediaChunk(
                mediaId = mediaId,
                chunkIndex = chunkIndex,
                data = chunk,
                schemeVersion = chunkScheme
            )
            if (!wrote) {
                throw IllegalStateException("Failed to persist media chunk $chunkIndex")
            }
        }

        if (!verifyStoredChunks(mediaId, chunkScheme, chunks)) {
            throw IllegalStateException("Stored media chunks failed verification")
        }

        val thumbHash = if (type == MediaType.IMAGE) {
            generateThumbnailHash(rawBytes)
        } else {
            ""
        }

        val ref = MediaRef(
            mediaId = mediaId,
            type = type,
            chunkCount = chunks.size,
            totalBytes = rawBytes.size.toLong(),
            mimeType = mimeType,
            ownership = ownership,
            thumbHash = thumbHash,
            createdAt = createdAt,
            fileName = fileName,
            chunkScheme = chunkScheme
        )

        if (!storeCanonicalMetadata(ref) || !verifyStoredMetadata(ref)) {
            throw IllegalStateException("Failed to persist media metadata")
        }

        ref
    }

    suspend fun storeMediaStream(
        inputStream: InputStream,
        type: MediaType,
        mimeType: String,
        ownership: MediaOwnership = MediaOwnership.BOTH,
        fileName: String = "",
        totalBytesHint: Long = -1L
    ): MediaRef = withContext(Dispatchers.IO) {
        val mediaId = generateMediaId()
        val createdAt = System.currentTimeMillis()
        val chunkScheme = USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED
        val buffered = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream)

        val readBuffer = ByteArray(MAX_CHUNK_BYTES)
        val chunkDigests = mutableListOf<ByteArray>()
        var chunkCount = 0
        var totalBytes = 0L

        try {
            while (true) {
                val read = buffered.read(readBuffer)
                if (read <= 0) break

                val chunk = readBuffer.copyOf(read)
                val wrote = engine.writeMediaChunk(
                    mediaId = mediaId,
                    chunkIndex = chunkCount,
                    data = chunk,
                    schemeVersion = chunkScheme
                )
                if (!wrote) {
                    chunk.fill(0)
                    throw IllegalStateException("Failed to persist media chunk $chunkCount")
                }

                chunkDigests += sha256(chunk)
                totalBytes += read.toLong()
                chunk.fill(0)
                chunkCount += 1
            }
        } finally {
            readBuffer.fill(0)
        }

        if (chunkCount == 0) {
            throw IllegalArgumentException("Cannot store empty media payload")
        }

        if (!verifyStoredChunkDigests(mediaId, chunkScheme, chunkDigests)) {
            throw IllegalStateException("Stored media chunks failed verification")
        }

        chunkDigests.forEach { it.fill(0) }

        val resolvedTotalBytes = if (totalBytesHint > 0L && totalBytesHint == totalBytes) {
            totalBytesHint
        } else {
            totalBytes
        }

        val ref = MediaRef(
            mediaId = mediaId,
            type = type,
            chunkCount = chunkCount,
            totalBytes = resolvedTotalBytes,
            mimeType = mimeType,
            ownership = ownership,
            thumbHash = "",
            createdAt = createdAt,
            fileName = fileName,
            chunkScheme = chunkScheme
        )

        if (!storeCanonicalMetadata(ref) || !verifyStoredMetadata(ref)) {
            throw IllegalStateException("Failed to persist media metadata")
        }

        ref
    }

    suspend fun storeImage(
        bitmap: Bitmap,
        ownership: MediaOwnership = MediaOwnership.BOTH
    ): MediaRef {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        return storeMedia(baos.toByteArray(), MediaType.IMAGE, "image/jpeg", ownership)
    }

    suspend fun retrieveMedia(ref: MediaRef): ByteArray? = withContext(Dispatchers.IO) {
        readAllMediaBytes(ref)
    }

    suspend fun retrieveBitmap(ref: MediaRef): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = retrieveMedia(ref) ?: return@withContext null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bytes.fill(0)
        bitmap
    }

    fun decodeThumbnail(thumbHash: String): Bitmap? {
        if (thumbHash.isBlank()) return null
        return try {
            val bytes = android.util.Base64.decode(thumbHash, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun getMediaRef(mediaId: Long): MediaRef? {
        readCanonicalMetadata(mediaId)?.let { canonical ->
            normalizeMetadata(mediaId, canonical)?.let { return it }
        }

        val hadLegacyCollision = hasLegacyRecordIdCollision(mediaId)
        readLegacyMetadata(mediaId)?.let { legacy ->
            normalizeMetadata(mediaId, legacy)?.let { normalized ->
                if (hadLegacyCollision || readCanonicalMetadata(mediaId) == null) {
                    storeCanonicalMetadata(normalized)
                }
                return normalized
            }
        }

        val recovered = recoverMediaRef(mediaId) ?: return null
        storeCanonicalMetadata(recovered)
        return recovered
    }

    private fun ByteArray.toChunks(chunkSize: Int): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < size) {
            val end = minOf(offset + chunkSize, size)
            chunks += copyOfRange(offset, end)
            offset = end
        }
        return chunks
    }

    private fun readAllMediaBytes(ref: MediaRef): ByteArray? {
        val resolvedScheme = resolveChunkScheme(ref) ?: return null
        val out = ByteArrayOutputStream()
        return try {
            for (chunkIndex in 0 until ref.chunkCount) {
                val chunk = engine.readMediaChunk(ref.mediaId, chunkIndex, resolvedScheme) ?: return null
                out.write(chunk)
                chunk.fill(0)
            }
            val result = out.toByteArray()
            if (result.size.toLong() != ref.totalBytes) {
                null
            } else {
                result
            }
        } catch (_: Exception) {
            null
        } finally {
            out.close()
        }
    }

    private fun generateThumbnailHash(imageBytes: ByteArray): String {
        return try {
            val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return ""
            val thumb = Bitmap.createScaledBitmap(original, THUMB_WIDTH, THUMB_HEIGHT, true)
            val baos = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT)
        } catch (_: Exception) {
            ""
        }
    }

    private fun MediaRef.toBytes(): ByteArray {
        return JSONObject().apply {
            put("schema", META_SCHEMA_VERSION)
            put("mediaId", mediaId)
            put("type", type.name)
            put("chunkCount", chunkCount)
            put("totalBytes", totalBytes)
            put("mimeType", mimeType)
            put("ownership", ownership.name)
            put("thumbHash", thumbHash)
            put("createdAt", createdAt)
            put("fileName", fileName)
            put("chunkScheme", chunkScheme)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    private fun fromBytes(bytes: ByteArray): MediaRef? {
        return try {
            val raw = String(bytes, Charsets.UTF_8).trim()
            when {
                raw.startsWith("{") -> {
                    val json = JSONObject(raw)
                    MediaRef(
                        mediaId = json.getLong("mediaId"),
                        type = MediaType.valueOf(json.getString("type")),
                        chunkCount = json.getInt("chunkCount"),
                        totalBytes = json.getLong("totalBytes"),
                        mimeType = json.optString("mimeType"),
                        ownership = MediaOwnership.valueOf(
                            json.optString("ownership", MediaOwnership.BOTH.name)
                        ),
                        thumbHash = json.optString("thumbHash"),
                        createdAt = json.optLong("createdAt"),
                        fileName = json.optString("fileName"),
                        chunkScheme = json.optInt(
                            "chunkScheme",
                            USBStorageEngine.MEDIA_CHUNK_SCHEME_LEGACY
                        )
                    )
                }
                else -> {
                    val parts = raw.split("|")
                    MediaRef(
                        mediaId = parts[0].toLong(),
                        type = MediaType.valueOf(parts[1]),
                        chunkCount = parts[2].toInt(),
                        totalBytes = parts[3].toLong(),
                        mimeType = parts[4],
                        ownership = MediaOwnership.valueOf(parts[5]),
                        thumbHash = parts[6],
                        createdAt = parts[7].toLong(),
                        fileName = parts.getOrElse(8) { "" },
                        chunkScheme = parts.getOrNull(9)?.toIntOrNull()
                            ?: USBStorageEngine.MEDIA_CHUNK_SCHEME_LEGACY
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun storeCanonicalMetadata(ref: MediaRef): Boolean {
        val refBytes = ref.toBytes()
        if (StorageManager.isReady()) {
            val wrote = StorageManager.write("$META_KEY_PREFIX${ref.mediaId}", refBytes.copyOf())
            if (wrote) return true
        }

        return runBlocking {
            engine.appendRecord(
                segmentType = USBStorageEngine.SEG_INDEX,
                recordId = metadataRecordId(ref.mediaId),
                payload = refBytes.copyOf()
            )
        }
    }

    private fun readCanonicalMetadata(mediaId: Long): MediaRef? {
        if (StorageManager.isReady()) {
            StorageManager.read("$META_KEY_PREFIX$mediaId")
                ?.let { fromBytes(it)?.let { ref -> return ref } }
        }

        engine.readRecord(metadataRecordId(mediaId))
            ?.let { fromBytes(it)?.let { ref -> return ref } }

        return null
    }

    private fun readLegacyMetadata(mediaId: Long): MediaRef? {
        engine.readRecordByIdAndType(mediaId, USBStorageEngine.SEG_INDEX)
            ?.let { fromBytes(it)?.let { ref -> return ref } }

        engine.readRecord(mediaId)
            ?.let { fromBytes(it)?.let { ref -> return ref } }

        return null
    }

    private fun verifyStoredChunks(
        mediaId: Long,
        chunkScheme: Int,
        expectedChunks: List<ByteArray>
    ): Boolean {
        for ((chunkIndex, expected) in expectedChunks.withIndex()) {
            val actual = engine.readMediaChunk(mediaId, chunkIndex, chunkScheme) ?: return false
            val matches = actual.contentEquals(expected)
            actual.fill(0)
            if (!matches) return false
        }
        return true
    }

    private fun verifyStoredChunkDigests(
        mediaId: Long,
        chunkScheme: Int,
        expectedDigests: List<ByteArray>
    ): Boolean {
        for ((chunkIndex, expectedDigest) in expectedDigests.withIndex()) {
            val actualChunk = engine.readMediaChunk(mediaId, chunkIndex, chunkScheme) ?: return false
            val actualDigest = sha256(actualChunk)
            actualChunk.fill(0)
            val matches = actualDigest.contentEquals(expectedDigest)
            actualDigest.fill(0)
            if (!matches) return false
        }
        return true
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun verifyStoredMetadata(ref: MediaRef): Boolean {
        return readCanonicalMetadata(ref.mediaId) == ref
    }

    private fun normalizeMetadata(mediaId: Long, ref: MediaRef): MediaRef? {
        if (ref.mediaId != mediaId) return null
        if (ref.chunkCount <= 0 || ref.totalBytes <= 0L) return null

        val resolvedScheme = resolveChunkScheme(ref) ?: return null
        return ref.copy(
            mimeType = ref.mimeType.ifBlank { defaultMimeType(ref.type) },
            chunkScheme = resolvedScheme
        )
    }

    private fun resolveChunkScheme(ref: MediaRef): Int? {
        return resolveChunkScheme(ref.mediaId, ref.chunkScheme)
    }

    private fun resolveChunkScheme(mediaId: Long, preferredScheme: Int): Int? {
        if (engine.readMediaChunk(mediaId, 0, preferredScheme) != null) {
            return preferredScheme
        }

        val alternateScheme = if (preferredScheme == USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED) {
            USBStorageEngine.MEDIA_CHUNK_SCHEME_LEGACY
        } else {
            USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED
        }
        if (engine.readMediaChunk(mediaId, 0, alternateScheme) != null) {
            return alternateScheme
        }

        return detectChunkSchemeFromContainerScan(mediaId)
    }

    private fun detectChunkSchemeFromContainerScan(mediaId: Long): Int? {
        val recordIds = runCatching {
            engine.getRecordIdsByType(USBStorageEngine.SEG_MEDIA_CHUNK)
        }.getOrDefault(emptyList())
        if (recordIds.isEmpty()) return null

        val legacyIndices = recordIds.mapNotNullTo(mutableSetOf()) { recordId ->
            decodeLegacyChunkIndex(mediaId, recordId)
        }
        val stridedIndices = recordIds.mapNotNullTo(mutableSetOf()) { recordId ->
            decodeStridedChunkIndex(mediaId, recordId)
        }

        return when {
            0 in stridedIndices && stridedIndices.size >= legacyIndices.size ->
                USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED
            0 in legacyIndices ->
                USBStorageEngine.MEDIA_CHUNK_SCHEME_LEGACY
            stridedIndices.isNotEmpty() ->
                USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED
            legacyIndices.isNotEmpty() ->
                USBStorageEngine.MEDIA_CHUNK_SCHEME_LEGACY
            else -> null
        }
    }

    private fun decodeLegacyChunkIndex(mediaId: Long, recordId: Long): Int? {
        val diff = recordId - mediaId
        if (diff < 0L || diff > MAX_RECOVERY_SCAN_CHUNKS) return null
        return diff.toInt()
    }

    private fun decodeStridedChunkIndex(mediaId: Long, recordId: Long): Int? {
        val baseId = try {
            Math.multiplyExact(mediaId, USBStorageEngine.MEDIA_CHUNK_RECORD_STRIDE)
        } catch (_: ArithmeticException) {
            return null
        }
        val diff = recordId - baseId
        if (diff < 0L || diff >= USBStorageEngine.MEDIA_CHUNK_RECORD_STRIDE) return null
        if (diff > MAX_RECOVERY_SCAN_CHUNKS) return null
        return diff.toInt()
    }

    private fun recoverMediaRef(mediaId: Long): MediaRef? {
        val messageHint = loadMessageMetadataHint(mediaId)
        val preferredScheme = USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED
        val scheme = resolveChunkScheme(mediaId, preferredScheme) ?: return null
        val chunkScan = scanChunks(mediaId, scheme) ?: return null
        val recoveredType = messageHint?.type ?: MediaType.FILE
        val recoveredMimeType = messageHint?.mimeType
            ?.takeIf { it.isNotBlank() }
            ?: defaultMimeType(recoveredType)

        var recovered = MediaRef(
            mediaId = mediaId,
            type = recoveredType,
            chunkCount = chunkScan.chunkCount,
            totalBytes = chunkScan.totalBytes,
            mimeType = recoveredMimeType,
            ownership = MediaOwnership.BOTH,
            thumbHash = messageHint?.thumbHash.orEmpty(),
            createdAt = messageHint?.createdAt ?: System.currentTimeMillis(),
            fileName = messageHint?.fileName.orEmpty(),
            chunkScheme = scheme
        )

        val bytes = readAllMediaBytes(recovered) ?: return null
        try {
            if (recovered.type == MediaType.IMAGE && recovered.thumbHash.isBlank()) {
                recovered = recovered.copy(thumbHash = generateThumbnailHash(bytes))
            }
            recovered = recovered.copy(totalBytes = bytes.size.toLong())
        } finally {
            bytes.fill(0)
        }
        return recovered
    }

    private fun scanChunks(mediaId: Long, scheme: Int): ChunkScanResult? {
        var chunkCount = 0
        var totalBytes = 0L
        while (chunkCount < MAX_RECOVERY_SCAN_CHUNKS) {
            val chunk = engine.readMediaChunk(mediaId, chunkCount, scheme) ?: break
            totalBytes += chunk.size.toLong()
            chunk.fill(0)
            chunkCount += 1
        }
        return if (chunkCount == 0) null else ChunkScanResult(chunkCount, totalBytes)
    }

    private fun hasLegacyRecordIdCollision(mediaId: Long): Boolean {
        val legacyMetadata = engine.readRecordByIdAndType(mediaId, USBStorageEngine.SEG_INDEX)
        val legacyChunkZero = engine.readRecordByIdAndType(mediaId, USBStorageEngine.SEG_MEDIA_CHUNK)
        return legacyMetadata != null && legacyChunkZero != null
    }

    private fun loadMessageMetadataHint(mediaId: Long): MessageMetadataHint? {
        val messages = runCatching { messageDB.getAllMessages() }.getOrElse { emptyList() }
        return messages.asSequence()
            .mapNotNull { it.toMediaHint(mediaId) }
            .maxByOrNull { it.createdAt }
    }

    private fun MessageRecord.toMediaHint(mediaId: Long): MessageMetadataHint? {
        val mediaType = when (type) {
            AppendOnlyMessageDB.MSG_IMAGE -> MediaType.IMAGE
            AppendOnlyMessageDB.MSG_VIDEO -> MediaType.VIDEO
            AppendOnlyMessageDB.MSG_AUDIO -> MediaType.AUDIO
            AppendOnlyMessageDB.MSG_FILE -> MediaType.FILE
            else -> return null
        }
        val extraJson = runCatching { JSONObject(extra) }.getOrNull() ?: return null
        val refId = extraJson.optString("ref").toLongOrNull() ?: return null
        if (refId != mediaId) return null

        return MessageMetadataHint(
            type = mediaType,
            mimeType = extraJson.optString("mime").ifBlank { defaultMimeType(mediaType) },
            thumbHash = extraJson.optString("thumb"),
            createdAt = timestamp,
            fileName = extraJson.optString("name")
        )
    }

    private fun generateMediaId(): Long {
        while (true) {
            val now = System.currentTimeMillis()
            val candidate = NEXT_MEDIA_ID.updateAndGet { current ->
                if (current < now) now else current + 1L
            }
            if (isMediaIdAvailable(candidate)) {
                return candidate
            }
        }
    }

    private fun isMediaIdAvailable(mediaId: Long): Boolean {
        if (StorageManager.isReady() && StorageManager.exists("$META_KEY_PREFIX$mediaId")) {
            return false
        }
        if (engine.readRecord(metadataRecordId(mediaId)) != null) return false
        if (engine.readRecordByIdAndType(mediaId, USBStorageEngine.SEG_INDEX) != null) return false
        if (engine.readMediaChunk(mediaId, 0, USBStorageEngine.MEDIA_CHUNK_SCHEME_LEGACY) != null) {
            return false
        }
        if (engine.readMediaChunk(mediaId, 0, USBStorageEngine.MEDIA_CHUNK_SCHEME_STRIDED) != null) {
            return false
        }
        return true
    }

    private fun defaultMimeType(type: MediaType): String {
        return when (type) {
            MediaType.IMAGE -> "image/jpeg"
            MediaType.VIDEO -> "video/mp4"
            MediaType.AUDIO -> "audio/mp4"
            MediaType.FILE -> "application/octet-stream"
        }
    }

    private fun metadataRecordId(mediaId: Long): Long {
        return mediaId xor Long.MIN_VALUE
    }
}
