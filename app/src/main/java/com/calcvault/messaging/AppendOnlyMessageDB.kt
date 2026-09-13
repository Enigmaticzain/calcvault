package com.calcvault.messaging

import com.calcvault.crypto.ratchet.DoubleRatchet
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.provider.StorageManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

data class MessageRecord(
    val id: Long,
    val type: Int,
    val from: String,
    val to: String,
    val content: String,
    val timestamp: Long,
    val delivered: Boolean = false,
    val read: Boolean = false,
    val extra: String = "",
    var isBackedUp: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("type", type); put("from", from); put("to", to)
        put("content", content); put("ts", timestamp)
        put("delivered", delivered); put("read", read); put("extra", extra)
        put("backedUp", isBackedUp)
    }.toString()

    companion object {
        fun fromJson(json: String) = JSONObject(json).let { j ->
            MessageRecord(
                j.getLong("id"), j.getInt("type"), j.getString("from"),
                j.getString("to"), j.getString("content"), j.getLong("ts"),
                j.optBoolean("delivered"), j.optBoolean("read"), j.optString("extra"),
                j.optBoolean("backedUp")
            )
        }
    }
}

data class CallLogRecord(
    val callId: String,
    val type: String,
    val initiator: String,
    val receiver: String,
    val startTime: Long,
    val duration: Long,
    val ended: String,
    val recordingId: Long? = null
) {
    fun toJson() = JSONObject().apply {
        put("callId", callId)
        put("type", type)
        put("initiator", initiator)
        put("receiver", receiver)
        put("startTime", startTime)
        put("duration", duration)
        put("ended", ended)
        if (recordingId != null) put("recId", recordingId)
    }.toString()
}

/**
 * AppendOnlyMessageDB
 *
 * Handles persistence of chat messages and logs.
 * Enforces 48-hour local retention policy.
 */
class AppendOnlyMessageDB(private val engine: USBStorageEngine? = null, private val ratchet: DoubleRatchet? = null) {

    companion object {
        const val MSG_TEXT = 10; const val MSG_IMAGE = 11
        const val MSG_VIDEO = 12; const val MSG_AUDIO = 13
        const val MSG_DELETED = 14; const val MSG_REACTION = 15
        const val MSG_CALL_LOG = 16; const val MSG_MOOD = 17
        const val MSG_PRESENCE = 18
        const val MSG_THEME_SYNC = 19
        const val MSG_FILE = 20
        const val MSG_CARE_DROP = 21
        const val EXTRA_RETENTION_EXEMPT = "retentionExempt"

        private const val RETENTION_MS = 48 * 60 * 60 * 1000L
    }

    // ── Write ──────────────────────────────────────────────────────────────

    suspend fun sendTextMessage(
        from: String,
        to: String,
        content: String,
        type: Int = MSG_TEXT,
        extra: String = ""
    ): MessageRecord =
        withContext(Dispatchers.IO) {
            val msg = build(type, from, to, content, extra)
            persist(msg); cleanupOldMessages(); msg
        }

    suspend fun sendMediaMessage(
        from: String,
        to: String,
        type: Int,
        mediaRef: String,
        thumbHash: String? = null,
        mimeType: String? = null,
        fileName: String? = null
    ): MessageRecord =
        withContext(Dispatchers.IO) {
            val extra = JSONObject().apply {
                put("ref", mediaRef)
                thumbHash?.let { put("thumb", it) }
                mimeType?.takeIf { it.isNotBlank() }?.let { put("mime", it) }
                fileName?.takeIf { it.isNotBlank() }?.let { put("name", it) }
            }.toString()
            val msg = build(type, from, to, "[Media]", extra)
            persist(msg); cleanupOldMessages(); msg
        }

    suspend fun saveReceivedMessage(record: MessageRecord) = withContext(Dispatchers.IO) {
        persist(record)
        cleanupOldMessages()
    }

    suspend fun saveMessage(record: MessageRecord) = withContext(Dispatchers.IO) {
        persist(record)
        cleanupOldMessages()
    }

    suspend fun saveCallLog(callLog: CallLogRecord) = withContext(Dispatchers.IO) {
        val msg = build(MSG_CALL_LOG, callLog.initiator, callLog.receiver, callLog.toJson())
        persist(msg)
        cleanupOldMessages()
    }

    suspend fun saveMood(userId: String, mood: String) = withContext(Dispatchers.IO) {
        StorageManager.write("mood/$userId", mood.toByteArray())
        val msg = build(MSG_MOOD, userId, "SYSTEM", mood)
        persist(msg)
        cleanupOldMessages()
    }

    suspend fun markBackedUp(messageId: Long) = withContext(Dispatchers.IO) {
        val record = readRecord("msg/$messageId") ?: return@withContext
        record.isBackedUp = true
        persist(record)
    }

    suspend fun markDelivered(messageId: Long) = withContext(Dispatchers.IO) {
        val record = readRecord("msg/$messageId") ?: return@withContext
        val updated = record.copy(delivered = true)
        persist(updated)
    }

    suspend fun addReaction(messageId: Long, userId: String, emoji: String) = withContext(Dispatchers.IO) {
        val key = "reactions/$messageId"
        val existing = StorageManager.read(key)?.let { String(it) }
        val array = if (existing != null) JSONArray(existing) else JSONArray()

        // Simple logic: one reaction per user
        var found = false
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("u") == userId) {
                obj.put("e", emoji)
                found = true
                break
            }
        }
        if (!found) {
            array.put(JSONObject().apply { put("u", userId); put("e", emoji) })
        }

        StorageManager.write(key, array.toString().toByteArray())
    }

    suspend fun softDeleteMessage(messageId: Long, userId: String) = withContext(Dispatchers.IO) {
        val tombstone = JSONObject().apply {
            put("id", messageId)
            put("by", userId)
            put("ts", System.currentTimeMillis())
        }.toString()
        StorageManager.write("tombstone/msg/$messageId", tombstone.toByteArray())
    }

    // ── Retention ─────────────────────────────────────────────────────────

    private fun cleanupOldMessages() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        StorageManager.list("msg/").forEach { key ->
            val record = readRecord(key)
            if (record != null && record.timestamp < cutoff && !isRetentionExempt(record)) {
                StorageManager.delete(key)
            }
        }
    }

    private fun isRetentionExempt(record: MessageRecord): Boolean {
        if (record.extra.isBlank()) return false
        return runCatching {
            JSONObject(record.extra).optBoolean(EXTRA_RETENTION_EXEMPT, false)
        }.getOrDefault(false)
    }

    // ── Read ───────────────────────────────────────────────────────────────

    fun getAllMessages(): List<MessageRecord> {
        val tombstones = StorageManager.list("tombstone/msg/")
            .map { it.removePrefix("tombstone/msg/") }.toSet()
        return StorageManager.list("msg/")
            .filter { it.removePrefix("msg/") !in tombstones }
            .mapNotNull { key -> readRecord(key) }
            .filter { it.type != MSG_DELETED }
            .sortedBy { it.timestamp }
    }

    fun getMessages(type: Int): List<MessageRecord> {
        return getAllMessages().filter { it.type == type }
    }

    fun getAllRecordIds(): Set<Long> {
        return StorageManager.list("msg/")
            .mapNotNull { it.removePrefix("msg/").toLongOrNull() }
            .toSet()
    }

    fun getChatMessages(): List<MessageRecord> {
        val displayTypes = listOf(
            MSG_TEXT,
            MSG_IMAGE,
            MSG_VIDEO,
            MSG_AUDIO,
            MSG_CALL_LOG,
            MSG_MOOD,
            MSG_FILE,
            MSG_CARE_DROP
        )
        return getAllMessages().filter { it.type in displayTypes }
    }

    fun getAllReactions(): Map<Long, List<String>> {
        val result = mutableMapOf<Long, List<String>>()
        StorageManager.list("reactions/").forEach { key ->
            val mid = key.removePrefix("reactions/").toLongOrNull() ?: return@forEach
            val raw = StorageManager.read(key) ?: return@forEach
            val array = JSONArray(String(raw))
            val emojis = mutableListOf<String>()
            for (i in 0 until array.length()) {
                emojis.add(array.getJSONObject(i).getString("e"))
            }
            result[mid] = emojis
        }
        return result
    }

    fun getLatestMood(userId: String): String? =
        StorageManager.read("mood/$userId")?.let { String(it) }

    suspend fun persist(record: MessageRecord) {
        val json = record.toJson().toByteArray()
        val payload = if (ratchet != null) {
            try { ratchet.encrypt(json).toBytes() } catch (e: Exception) { json }
        } else {
            json
        }

        withContext(Dispatchers.IO) {
            StorageManager.write("msg/${record.id}", payload)
        }
    }

    private fun readRecord(key: String): MessageRecord? {
        val raw = StorageManager.read(key) ?: return null
        return try {
            val ratchetMsg = com.calcvault.crypto.ratchet.RatchetMessage.fromBytes(raw)
            val plain = if (ratchetMsg != null && ratchet != null) {
                ratchet.decrypt(ratchetMsg) ?: raw
            } else {
                raw
            }
            MessageRecord.fromJson(String(plain))
        } catch (e: Exception) {
            try { MessageRecord.fromJson(String(raw)) } catch (e2: Exception) { null }
        }
    }

    private fun build(
        type: Int,
        from: String,
        to: String,
        content: String,
        extra: String = ""
    ) = MessageRecord(genId(), type, from, to, content, System.currentTimeMillis(), extra = extra)

    private fun genId() = System.currentTimeMillis() * 1000 + (Math.random() * 999).toLong()
}
