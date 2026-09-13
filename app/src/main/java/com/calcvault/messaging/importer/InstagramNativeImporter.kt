package com.calcvault.messaging.importer

import android.content.Context
import android.util.Log
import android.util.Xml
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import com.calcvault.storage.MediaChunkManager
import com.calcvault.storage.provider.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.PriorityQueue
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

/**
 * InstagramNativeImporter
 *
 * Imports Instagram XHTML export pages into native CalcVault message records.
 * - Streaming XML pull parsing (no full file loads)
 * - External chunk sort by epoch timestamp
 * - Native media storage via MediaChunkManager
 */
class InstagramNativeImporter(
    private val context: Context,
    private val messageDB: AppendOnlyMessageDB,
    private val mediaManager: MediaChunkManager
) {

    data class ImportOptions(
        val sourceDir: File,
        val localUserId: String,
        val partnerUserId: String,
        val localDisplayName: String = "",
        val partnerDisplayName: String = "",
        val zainAliases: List<String> = listOf("zain"),
        val lavenderAliases: List<String> = listOf("lavender"),
        val replaceExistingConversation: Boolean = true,
        val forceReimport: Boolean = false,
        val maxRecordsPerChunk: Int = 2_500
    )

    data class ImportSummary(
        val imported: Boolean,
        val skippedAsUpToDate: Boolean,
        val textMessagesImported: Int,
        val mediaMessagesImported: Int,
        val missingMediaFiles: Int,
        val malformedEntries: Int,
        val zainUserId: String,
        val lavenderUserId: String,
        val error: String? = null
    )

    private data class PendingMessage(
        val rootDepth: Int,
        var senderRaw: String = "",
        var timestampRaw: String = "",
        val textParts: MutableList<String> = mutableListOf(),
        val mediaByPath: LinkedHashMap<String, MediaCandidate> = linkedMapOf(),
        var reactionDepth: Int = -1,
        var callDurationRaw: String = ""
    ) {
        fun addText(raw: String) {
            val cleaned = raw
                .replace('\u00A0', ' ')
                .trim()
            if (cleaned.isBlank()) return
            if (cleaned.equals("Click for audio", ignoreCase = true)) return
            textParts += cleaned
        }

        fun addBreak() {
            if (textParts.isNotEmpty() && textParts.last() != "\n") {
                textParts += "\n"
            }
        }

        fun addMedia(candidate: MediaCandidate) {
            mediaByPath.putIfAbsent(candidate.relativePath, candidate)
        }

        fun buildText(): String {
            val out = StringBuilder()
            for (part in textParts) {
                if (part == "\n") {
                    if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                    continue
                }
                if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                out.append(part)
            }
            return out.toString()
        }

        fun isInIgnoredTextDepth(currentDepth: Int): Boolean {
            if (reactionDepth != -1 && currentDepth > reactionDepth) return true
            return false
        }
    }

    private data class MediaCandidate(
        val relativePath: String,
        val messageType: Int,
        val mediaType: MediaChunkManager.MediaType,
        val mimeType: String,
        val fileName: String
    )

    private data class StagedRecord(
        val sequence: Long,
        val timestamp: Long,
        val fromUserId: String,
        val toUserId: String,
        val messageType: Int,
        val text: String = "",
        val mediaPath: String = "",
        val mediaMime: String = "",
        val mediaFileName: String = ""
    ) {
        fun toJsonLine(): String {
            return JSONObject().apply {
                put("seq", sequence)
                put("ts", timestamp)
                put("from", fromUserId)
                put("to", toUserId)
                put("type", messageType)
                put("text", text)
                put("mediaPath", mediaPath)
                put("mediaMime", mediaMime)
                put("mediaFileName", mediaFileName)
            }.toString()
        }

        fun isMedia(): Boolean = mediaPath.isNotBlank()

        companion object {
            fun fromJsonLine(line: String): StagedRecord {
                val json = JSONObject(line)
                return StagedRecord(
                    sequence = json.getLong("seq"),
                    timestamp = json.getLong("ts"),
                    fromUserId = json.getString("from"),
                    toUserId = json.getString("to"),
                    messageType = json.getInt("type"),
                    text = json.optString("text"),
                    mediaPath = json.optString("mediaPath"),
                    mediaMime = json.optString("mediaMime"),
                    mediaFileName = json.optString("mediaFileName")
                )
            }
        }
    }

    private data class ChunkCursor(
        val reader: BufferedReader,
        var record: StagedRecord
    )

    private data class MutableImportCounters(
        var textMessagesImported: Int = 0,
        var mediaMessagesImported: Int = 0,
        var missingMediaFiles: Int = 0,
        var malformedEntries: Int = 0
    )

    private class ParticipantMapper(
        localUserId: String,
        partnerUserId: String,
        localDisplayName: String,
        partnerDisplayName: String,
        zainAliases: List<String>,
        lavenderAliases: List<String>
    ) {
        private val localId = localUserId
        private val partnerId = partnerUserId
        private val localNorm = normalize(localDisplayName.ifBlank { localUserId })
        private val partnerNorm = normalize(partnerDisplayName.ifBlank { partnerUserId })
        private val zainAliasNorms = (zainAliases + "zain").mapNotNull { normalizeAlias(it) }.distinct()
        private val lavenderAliasNorms = (lavenderAliases + "lavender").mapNotNull { normalizeAlias(it) }.distinct()
        private val unknownSenderMap = mutableMapOf<String, String>()

        val zainUserId: String
        val lavenderUserId: String

        init {
            val localLooksZain = matchesAny(localNorm, zainAliasNorms)
            val localLooksLavender = matchesAny(localNorm, lavenderAliasNorms)
            val partnerLooksZain = matchesAny(partnerNorm, zainAliasNorms)
            val partnerLooksLavender = matchesAny(partnerNorm, lavenderAliasNorms)

            val defaultByRole = when {
                localLooksZain -> Pair(localId, partnerId)
                localLooksLavender -> Pair(partnerId, localId)
                partnerLooksZain -> Pair(partnerId, localId)
                partnerLooksLavender -> Pair(localId, partnerId)
                localId.equals("USER_A", ignoreCase = true) && partnerId.equals("USER_B", ignoreCase = true) ->
                    Pair("USER_A", "USER_B")
                localId.equals("USER_B", ignoreCase = true) && partnerId.equals("USER_A", ignoreCase = true) ->
                    Pair("USER_A", "USER_B")
                else -> Pair(localId, partnerId)
            }

            zainUserId = defaultByRole.first
            lavenderUserId = defaultByRole.second
        }

        fun mapSender(rawSender: String): String {
            val senderNorm = normalize(rawSender)
            if (matchesAny(senderNorm, zainAliasNorms)) return zainUserId
            if (matchesAny(senderNorm, lavenderAliasNorms)) return lavenderUserId

            if (matchesAny(senderNorm, listOf(localNorm))) return localId
            if (matchesAny(senderNorm, listOf(partnerNorm))) return partnerId

            if (senderNorm.isEmpty()) return zainUserId
            return unknownSenderMap.getOrPut(senderNorm) {
                if (unknownSenderMap.isEmpty()) zainUserId else lavenderUserId
            }
        }

        fun otherUserId(fromUserId: String): String {
            return when (fromUserId) {
                zainUserId -> lavenderUserId
                lavenderUserId -> zainUserId
                localId -> partnerId
                else -> localId
            }
        }

        private fun matchesAny(value: String, aliases: List<String>): Boolean {
            if (value.isBlank()) return false
            return aliases.any { alias ->
                alias.isNotBlank() &&
                    (value.contains(alias) || (alias.length >= 3 && alias.contains(value)))
            }
        }

        private fun normalizeAlias(alias: String): String? {
            val normalized = normalize(alias)
            return normalized.takeIf { it.isNotBlank() }
        }
    }

    private val timestampParsers = listOf(
        SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.US),
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
    ).onEach {
        it.isLenient = false
        it.timeZone = TimeZone.getDefault()
    }

    suspend fun import(options: ImportOptions): ImportSummary = withContext(Dispatchers.IO) {
        val sourceDir = options.sourceDir
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return@withContext ImportSummary(
                imported = false,
                skippedAsUpToDate = false,
                textMessagesImported = 0,
                mediaMessagesImported = 0,
                missingMediaFiles = 0,
                malformedEntries = 0,
                zainUserId = options.localUserId,
                lavenderUserId = options.partnerUserId,
                error = "Source directory not found: ${sourceDir.absolutePath}"
            )
        }

        val pages = listMessagePages(sourceDir)
        if (pages.isEmpty()) {
            return@withContext ImportSummary(
                imported = false,
                skippedAsUpToDate = false,
                textMessagesImported = 0,
                mediaMessagesImported = 0,
                missingMediaFiles = 0,
                malformedEntries = 0,
                zainUserId = options.localUserId,
                lavenderUserId = options.partnerUserId,
                error = "No message_*.html files found in ${sourceDir.absolutePath}"
            )
        }

        val mapper = ParticipantMapper(
            localUserId = options.localUserId,
            partnerUserId = options.partnerUserId,
            localDisplayName = options.localDisplayName,
            partnerDisplayName = options.partnerDisplayName,
            zainAliases = options.zainAliases,
            lavenderAliases = options.lavenderAliases
        )

        val signature = buildSourceSignature(pages)
        val importStateKey = "imports/instagram/${sourceDir.name}/state"
        if (!options.forceReimport && isUpToDate(importStateKey, signature)) {
            return@withContext ImportSummary(
                imported = false,
                skippedAsUpToDate = true,
                textMessagesImported = 0,
                mediaMessagesImported = 0,
                missingMediaFiles = 0,
                malformedEntries = 0,
                zainUserId = mapper.zainUserId,
                lavenderUserId = mapper.lavenderUserId
            )
        }

        val tempDir = File(
            context.cacheDir,
            "ig_native_import_${sourceDir.name}_${System.currentTimeMillis()}"
        ).apply { mkdirs() }

        val chunkFiles = mutableListOf<File>()
        val stagedBuffer = mutableListOf<StagedRecord>()
        val counters = MutableImportCounters()
        var sequenceCounter = 0L

        try {
            for (page in pages) {
                parsePage(
                    page = page,
                    sourceDirName = sourceDir.name,
                    mapper = mapper,
                    onRecord = { staged ->
                        stagedBuffer += staged.copy(sequence = sequenceCounter++)
                        if (stagedBuffer.size >= options.maxRecordsPerChunk) {
                            flushChunk(stagedBuffer, chunkFiles, tempDir)
                        }
                    }
                )
            }
            flushChunk(stagedBuffer, chunkFiles, tempDir)

            if (options.replaceExistingConversation) {
                clearConversationData()
            }

            val nextMessageId = AtomicLong((messageDB.getAllRecordIds().maxOrNull() ?: 0L) + 1L)
            mergeChunkFiles(chunkFiles) { record ->
                persistRecord(
                    record = record,
                    sourceDir = sourceDir,
                    nextMessageId = nextMessageId,
                    counters = counters
                )
            }

            val importedAnything = counters.textMessagesImported > 0 || counters.mediaMessagesImported > 0
            if (importedAnything) {
                writeImportState(
                    key = importStateKey,
                    signature = signature,
                    textCount = counters.textMessagesImported,
                    mediaCount = counters.mediaMessagesImported
                )
            }

            ImportSummary(
                imported = importedAnything,
                skippedAsUpToDate = false,
                textMessagesImported = counters.textMessagesImported,
                mediaMessagesImported = counters.mediaMessagesImported,
                missingMediaFiles = counters.missingMediaFiles,
                malformedEntries = counters.malformedEntries,
                zainUserId = mapper.zainUserId,
                lavenderUserId = mapper.lavenderUserId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Instagram import failed", e)
            ImportSummary(
                imported = false,
                skippedAsUpToDate = false,
                textMessagesImported = counters.textMessagesImported,
                mediaMessagesImported = counters.mediaMessagesImported,
                missingMediaFiles = counters.missingMediaFiles,
                malformedEntries = counters.malformedEntries + 1,
                zainUserId = mapper.zainUserId,
                lavenderUserId = mapper.lavenderUserId,
                error = e.message ?: "Unknown import error"
            )
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private fun parsePage(
        page: File,
        sourceDirName: String,
        mapper: ParticipantMapper,
        onRecord: (StagedRecord) -> Unit
    ) {
        FileInputStream(page).use { stream ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(InputStreamReader(stream, Charsets.UTF_8))

            var current: PendingMessage? = null
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.orEmpty()
                        val classAttr = parser.getAttributeValue(null, "class").orEmpty()

                        val pending = current
                        if (pending == null) {
                            if (tag.equals("div", ignoreCase = true) && isMessageRoot(classAttr)) {
                                current = PendingMessage(rootDepth = parser.depth)
                            }
                        } else {
                            when {
                                tag.equals("h2", ignoreCase = true) && hasClass(classAttr, "_a6-h") -> {
                                    pending.senderRaw = parser.nextText().trim()
                                }
                                tag.equals("div", ignoreCase = true) && hasClass(classAttr, "_a6-o") -> {
                                    pending.timestampRaw = parser.nextText().trim()
                                }
                                tag.equals("ul", ignoreCase = true) && hasClass(classAttr, "_a6-q") -> {
                                    pending.reactionDepth = parser.depth
                                }
                                tag.equals("span", ignoreCase = true) && hasClass(classAttr, "_idm") -> {
                                    pending.callDurationRaw = parser.nextText().trim()
                                }
                                tag.equals("br", ignoreCase = true) -> {
                                    pending.addBreak()
                                }
                                else -> {
                                    when {
                                        tag.equals("audio", ignoreCase = true) -> {
                                            val src = parser.getAttributeValue(null, "src").orEmpty()
                                            mediaCandidateFromPath(src, sourceDirName)?.let { pending.addMedia(it) }
                                        }
                                        tag.equals("video", ignoreCase = true) -> {
                                            val src = parser.getAttributeValue(null, "src").orEmpty()
                                            mediaCandidateFromPath(src, sourceDirName)?.let { pending.addMedia(it) }
                                        }
                                        tag.equals("source", ignoreCase = true) -> {
                                            val src = parser.getAttributeValue(null, "src").orEmpty()
                                            mediaCandidateFromPath(src, sourceDirName)?.let { pending.addMedia(it) }
                                        }
                                        tag.equals("img", ignoreCase = true) -> {
                                            val src = parser.getAttributeValue(null, "src").orEmpty()
                                            mediaCandidateFromPath(src, sourceDirName)?.let { pending.addMedia(it) }
                                        }
                                        tag.equals("a", ignoreCase = true) -> {
                                            val href = parser.getAttributeValue(null, "href").orEmpty()
                                            mediaCandidateFromPath(href, sourceDirName)?.let { pending.addMedia(it) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val pending = current
                        if (pending != null && !pending.isInIgnoredTextDepth(parser.depth)) {
                            pending.addText(parser.text.orEmpty())
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val pending = current
                        if (pending != null) {
                            val tag = parser.name.orEmpty()
                            if (tag.equals("ul", ignoreCase = true) && parser.depth == pending.reactionDepth) {
                                pending.reactionDepth = -1
                            }
                            if (tag.equals("div", ignoreCase = true) && parser.depth == pending.rootDepth) {
                                emitPendingMessage(
                                    pending = pending,
                                    mapper = mapper,
                                    sourceDirName = sourceDirName,
                                    onRecord = onRecord
                                )
                                current = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
    }

    private fun emitPendingMessage(
        pending: PendingMessage,
        mapper: ParticipantMapper,
        sourceDirName: String,
        onRecord: (StagedRecord) -> Unit
    ) {
        if (pending.senderRaw.isBlank()) return
        val epoch = parseTimestampToEpoch(pending.timestampRaw) ?: return

        val fromUserId = mapper.mapSender(pending.senderRaw)
        val toUserId = mapper.otherUserId(fromUserId)

        val normalizedText = normalizeText(pending.buildText())
        val callLogJson = maybeBuildCallLogJson(
            text = normalizedText,
            durationRaw = pending.callDurationRaw,
            fromUserId = fromUserId,
            toUserId = toUserId,
            timestamp = epoch
        )
        val media = pending.mediaByPath.values.toList()

        if (media.isEmpty()) {
            if (callLogJson != null) {
                onRecord(
                    StagedRecord(
                        sequence = 0L,
                        timestamp = epoch,
                        fromUserId = fromUserId,
                        toUserId = toUserId,
                        messageType = AppendOnlyMessageDB.MSG_CALL_LOG,
                        text = callLogJson
                    )
                )
            } else if (normalizedText.isNotBlank()) {
                onRecord(
                    StagedRecord(
                        sequence = 0L,
                        timestamp = epoch,
                        fromUserId = fromUserId,
                        toUserId = toUserId,
                        messageType = AppendOnlyMessageDB.MSG_TEXT,
                        text = normalizedText
                    )
                )
            }
            return
        }

        val captionText = stripAttachmentPlaceholder(normalizedText, pending.senderRaw)
        if (captionText.isNotBlank()) {
            onRecord(
                StagedRecord(
                    sequence = 0L,
                    timestamp = epoch,
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    messageType = AppendOnlyMessageDB.MSG_TEXT,
                    text = captionText
                )
            )
        }

        for (mediaCandidate in media) {
            val normalizedPath = normalizeMediaPath(mediaCandidate.relativePath, sourceDirName) ?: continue
            onRecord(
                StagedRecord(
                    sequence = 0L,
                    timestamp = epoch,
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    messageType = mediaCandidate.messageType,
                    mediaPath = normalizedPath,
                    mediaMime = mediaCandidate.mimeType,
                    mediaFileName = mediaCandidate.fileName
                )
            )
        }
    }

    private fun flushChunk(
        stagedBuffer: MutableList<StagedRecord>,
        chunkFiles: MutableList<File>,
        tempDir: File
    ) {
        if (stagedBuffer.isEmpty()) return
        val sorted = stagedBuffer.sortedWith(
            compareBy<StagedRecord> { it.timestamp }.thenByDescending { it.sequence }
        )
        val chunkFile = File(tempDir, "chunk_${chunkFiles.size}.jsonl")
        chunkFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            for (record in sorted) {
                writer.write(record.toJsonLine())
                writer.newLine()
            }
        }
        chunkFiles += chunkFile
        stagedBuffer.clear()
    }

    private suspend fun mergeChunkFiles(
        chunkFiles: List<File>,
        consumer: suspend (StagedRecord) -> Unit
    ) {
        if (chunkFiles.isEmpty()) return

        val queue = PriorityQueue<ChunkCursor>(
            compareBy<ChunkCursor> { it.record.timestamp }.thenByDescending { it.record.sequence }
        )
        val openReaders = mutableListOf<BufferedReader>()

        try {
            for (chunk in chunkFiles) {
                val reader = chunk.bufferedReader(Charsets.UTF_8)
                openReaders += reader
                val firstLine = reader.readLine()
                if (firstLine != null) {
                    queue += ChunkCursor(reader = reader, record = StagedRecord.fromJsonLine(firstLine))
                }
            }

            while (queue.isNotEmpty()) {
                val cursor = queue.poll()
                consumer(cursor.record)
                val next = cursor.reader.readLine()
                if (next != null) {
                    cursor.record = StagedRecord.fromJsonLine(next)
                    queue += cursor
                }
            }
        } finally {
            openReaders.forEach { runCatching { it.close() } }
        }
    }

    private suspend fun persistRecord(
        record: StagedRecord,
        sourceDir: File,
        nextMessageId: AtomicLong,
        counters: MutableImportCounters
    ) {
        if (!record.isMedia()) {
            val text = record.text.trim()
            if (text.isBlank()) return
            val extra = JSONObject().apply {
                put(AppendOnlyMessageDB.EXTRA_RETENTION_EXEMPT, true)
            }.toString()

            val message = MessageRecord(
                id = nextMessageId.getAndIncrement(),
                type = record.messageType,
                from = record.fromUserId,
                to = record.toUserId,
                content = text,
                timestamp = record.timestamp,
                delivered = true,
                read = true,
                extra = extra
            )
            messageDB.persist(message)
            counters.textMessagesImported += 1
            return
        }

        val mediaFile = File(sourceDir, record.mediaPath)
        if (!mediaFile.exists() || !mediaFile.isFile) {
            counters.missingMediaFiles += 1
            return
        }

        try {
            val mediaType = messageTypeToMediaType(record.messageType)
            val mimeType = record.mediaMime.ifBlank { guessMime(mediaFile.name, mediaType) }
            val fileName = record.mediaFileName.ifBlank { mediaFile.name }
            val mediaRef = FileInputStream(mediaFile).use { stream ->
                mediaManager.storeMediaStream(
                    inputStream = stream,
                    type = mediaType,
                    mimeType = mimeType,
                    fileName = fileName,
                    totalBytesHint = mediaFile.length()
                )
            }

            val extra = JSONObject().apply {
                put("ref", mediaRef.mediaId.toString())
                if (mediaRef.thumbHash.isNotBlank()) put("thumb", mediaRef.thumbHash)
                if (mimeType.isNotBlank()) put("mime", mimeType)
                if (fileName.isNotBlank()) put("name", fileName)
                put(AppendOnlyMessageDB.EXTRA_RETENTION_EXEMPT, true)
            }.toString()

            val message = MessageRecord(
                id = nextMessageId.getAndIncrement(),
                type = record.messageType,
                from = record.fromUserId,
                to = record.toUserId,
                content = "[Media]",
                timestamp = record.timestamp,
                delivered = true,
                read = true,
                extra = extra
            )
            messageDB.persist(message)
            counters.mediaMessagesImported += 1
        } catch (e: Exception) {
            counters.malformedEntries += 1
            Log.w(TAG, "Failed to persist media message for ${record.mediaPath}", e)
        }
    }

    private fun clearConversationData() {
        StorageManager.list("msg/").forEach { key -> StorageManager.delete(key) }
        StorageManager.list("reactions/").forEach { key -> StorageManager.delete(key) }
        StorageManager.list("tombstone/msg/").forEach { key -> StorageManager.delete(key) }
    }

    private fun listMessagePages(sourceDir: File): List<File> {
        val regex = Regex("""message_(\d+)\.html""", RegexOption.IGNORE_CASE)
        return sourceDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                val num = regex.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()
                if (num != null) file to num else null
            }
            ?.sortedBy { it.second }
            ?.map { it.first }
            ?.toList()
            .orEmpty()
    }

    private fun buildSourceSignature(pages: List<File>): String {
        val totalBytes = pages.sumOf { it.length() }
        val newestModified = pages.maxOfOrNull { it.lastModified() } ?: 0L
        val pageFingerprint = pages.joinToString("|") {
            "${it.name}:${it.length()}:${it.lastModified()}"
        }
        return "$totalBytes:$newestModified:$pageFingerprint"
    }

    private fun isUpToDate(key: String, expectedSignature: String): Boolean {
        val existing = StorageManager.read(key) ?: return false
        val json = runCatching { JSONObject(String(existing, Charsets.UTF_8)) }.getOrNull() ?: return false
        if (json.optString("signature") != expectedSignature) return false
        return StorageManager.list("msg/").isNotEmpty()
    }

    private fun writeImportState(
        key: String,
        signature: String,
        textCount: Int,
        mediaCount: Int
    ) {
        val payload = JSONObject().apply {
            put("signature", signature)
            put("textCount", textCount)
            put("mediaCount", mediaCount)
            put("importedAt", System.currentTimeMillis())
        }.toString().toByteArray(Charsets.UTF_8)
        StorageManager.write(key, payload)
    }

    private fun parseTimestampToEpoch(raw: String): Long? {
        val normalized = raw.trim()
        if (normalized.isBlank()) return null
        for (format in timestampParsers) {
            val parsed = runCatching { format.parse(normalized) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    private fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        val lines = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Click for audio", ignoreCase = true) }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun stripAttachmentPlaceholder(text: String, senderRaw: String): String {
        if (text.isBlank()) return ""
        val sender = senderRaw.trim()
        val filtered = text
            .split('\n')
            .map { it.trim() }
            .filter { line ->
                if (line.isBlank()) return@filter false
                val lower = line.lowercase(Locale.US)
                val generic = lower.endsWith("sent an attachment.") || lower == "you sent an attachment."
                val senderSpecific = sender.isNotBlank() &&
                    lower == "${sender.lowercase(Locale.US)} sent an attachment."
                !generic && !senderSpecific
            }
        return filtered.joinToString("\n").trim()
    }

    private fun maybeBuildCallLogJson(
        text: String,
        durationRaw: String,
        fromUserId: String,
        toUserId: String,
        timestamp: Long
    ): String? {
        val normalized = text.trim().lowercase(Locale.US)
        val isAudioEnded = normalized == "audio call ended"
        val isVideoEnded = normalized == "video chat ended"
        if (!isAudioEnded && !isVideoEnded) return null

        val durationMs = parseDurationToMs(durationRaw)
        val startedAt = (timestamp - durationMs).coerceAtLeast(0L)
        return JSONObject().apply {
            put("callId", "ig_${timestamp}_${fromUserId}_$toUserId")
            put("type", if (isVideoEnded) "VIDEO" else "VOICE")
            put("initiator", fromUserId)
            put("receiver", toUserId)
            put("startTime", startedAt)
            put("duration", durationMs)
            put("ended", "NORMAL")
        }.toString()
    }

    private fun parseDurationToMs(raw: String): Long {
        val normalized = raw
            .trim()
            .removePrefix("Duration:")
            .removePrefix("duration:")
            .trim()
            .lowercase(Locale.US)
        if (normalized.isBlank()) return 0L

        fun part(unitRegex: Regex): Long {
            val value = unitRegex.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            return value
        }

        val hours = part(Regex("""(\d+)\s*hour"""))
        val minutes = part(Regex("""(\d+)\s*minute"""))
        val seconds = part(Regex("""(\d+)\s*second"""))

        val total = hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L
        return if (total > 0L) total else 0L
    }

    private fun mediaCandidateFromPath(rawPath: String, sourceDirName: String): MediaCandidate? {
        val normalized = normalizeMediaPath(rawPath, sourceDirName) ?: return null
        val lowerPath = normalized.lowercase(Locale.US)
        if (lowerPath.startsWith("http://") || lowerPath.startsWith("https://")) return null

        val fileName = File(normalized).name
        val messageType: Int
        val mediaType: MediaChunkManager.MediaType
        when {
            lowerPath.startsWith("audio/") || hasAudioExtension(lowerPath) -> {
                messageType = AppendOnlyMessageDB.MSG_AUDIO
                mediaType = MediaChunkManager.MediaType.AUDIO
            }
            lowerPath.startsWith("videos/") || hasVideoExtension(lowerPath) -> {
                messageType = AppendOnlyMessageDB.MSG_VIDEO
                mediaType = MediaChunkManager.MediaType.VIDEO
            }
            lowerPath.startsWith("photos/") || lowerPath.startsWith("gifs/") || hasImageExtension(lowerPath) -> {
                messageType = AppendOnlyMessageDB.MSG_IMAGE
                mediaType = MediaChunkManager.MediaType.IMAGE
            }
            else -> {
                messageType = AppendOnlyMessageDB.MSG_FILE
                mediaType = MediaChunkManager.MediaType.FILE
            }
        }

        val mimeType = guessMime(fileName, mediaType)
        return MediaCandidate(
            relativePath = normalized,
            messageType = messageType,
            mediaType = mediaType,
            mimeType = mimeType,
            fileName = fileName
        )
    }

    private fun normalizeMediaPath(rawPath: String, sourceDirName: String): String? {
        var cleaned = rawPath.trim()
        if (cleaned.isBlank()) return null
        cleaned = cleaned.substringBefore('?').substringBefore('#')
        cleaned = cleaned.replace('\\', '/')
        cleaned = cleaned.removePrefix("./").removePrefix("/")
        cleaned = cleaned.removePrefix("your_instagram_activity/messages/inbox/")
        cleaned = cleaned.removePrefix("messages/inbox/")
        cleaned = cleaned.removePrefix("$sourceDirName/")

        val marker = "/$sourceDirName/"
        val markerIndex = cleaned.indexOf(marker)
        if (markerIndex >= 0) {
            cleaned = cleaned.substring(markerIndex + marker.length)
        }

        if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            return null
        }
        if (cleaned.isBlank()) return null
        return cleaned
    }

    private fun hasClass(classAttr: String, token: String): Boolean {
        if (classAttr.isBlank()) return false
        return classAttr.split(' ').any { it.trim() == token }
    }

    private fun isMessageRoot(classAttr: String): Boolean {
        if (classAttr.isBlank()) return false
        val classes = classAttr.split(' ').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        return classes.contains("pam") &&
            classes.contains("_a6-g") &&
            classes.contains("uiBoxWhite") &&
            classes.contains("noborder")
    }

    private fun messageTypeToMediaType(type: Int): MediaChunkManager.MediaType {
        return when (type) {
            AppendOnlyMessageDB.MSG_IMAGE -> MediaChunkManager.MediaType.IMAGE
            AppendOnlyMessageDB.MSG_VIDEO -> MediaChunkManager.MediaType.VIDEO
            AppendOnlyMessageDB.MSG_AUDIO -> MediaChunkManager.MediaType.AUDIO
            else -> MediaChunkManager.MediaType.FILE
        }
    }

    private fun guessMime(fileName: String, mediaType: MediaChunkManager.MediaType): String {
        val lower = fileName.lowercase(Locale.US)
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") && mediaType == MediaChunkManager.MediaType.AUDIO -> "audio/mp4"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".ogg") -> "audio/ogg"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".webm") -> "video/webm"
            mediaType == MediaChunkManager.MediaType.IMAGE -> "image/jpeg"
            mediaType == MediaChunkManager.MediaType.VIDEO -> "video/mp4"
            mediaType == MediaChunkManager.MediaType.AUDIO -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun hasImageExtension(path: String): Boolean {
        return path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".png") ||
            path.endsWith(".gif") ||
            path.endsWith(".webp")
    }

    private fun hasVideoExtension(path: String): Boolean {
        return path.endsWith(".mp4") ||
            path.endsWith(".mov") ||
            path.endsWith(".webm") ||
            path.endsWith(".mkv")
    }

    private fun hasAudioExtension(path: String): Boolean {
        return path.endsWith(".m4a") ||
            path.endsWith(".ogg") ||
            path.endsWith(".mp3") ||
            path.endsWith(".wav") ||
            path.endsWith(".aac")
    }

    companion object {
        private const val TAG = "InstagramNativeImporter"

        private fun normalize(value: String): String {
            if (value.isBlank()) return ""
            return value.lowercase(Locale.US).filter { it.isLetterOrDigit() }
        }
    }
}
