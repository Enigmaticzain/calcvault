package com.calcvault.chat

import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.CallLogRecord
import com.calcvault.messaging.MessageRecord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTest {

    @Test
    fun messageRecordRoundTripsThroughJson() {
        val original = MessageRecord(
            id = 42L,
            type = AppendOnlyMessageDB.MSG_TEXT,
            from = "zain",
            to = "sanu",
            content = "Hello, partner!",
            timestamp = 1_704_067_200_000L,
            delivered = true,
            read = false,
            extra = """{"style":"warm"}""",
            isBackedUp = true
        )

        val restored = MessageRecord.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun messageDefaultsMatchAppendOnlyChatPipeline() {
        val message = MessageRecord(
            id = 7L,
            type = AppendOnlyMessageDB.MSG_TEXT,
            from = "local",
            to = "partner",
            content = "Test",
            timestamp = 123L
        )

        assertFalse(message.delivered)
        assertFalse(message.read)
        assertFalse(message.isBackedUp)
        assertEquals("", message.extra)
    }

    @Test
    fun mutableBackupFlagIsSerialized() {
        val message = MessageRecord(
            id = 8L,
            type = AppendOnlyMessageDB.MSG_FILE,
            from = "local",
            to = "partner",
            content = "[Media]",
            timestamp = 456L
        )

        message.isBackedUp = true

        assertTrue(MessageRecord.fromJson(message.toJson()).isBackedUp)
    }

    @Test
    fun callLogRecordSerializesAllPipelineFields() {
        val callLog = CallLogRecord(
            callId = "call-1",
            type = "video",
            initiator = "zain",
            receiver = "sanu",
            startTime = 1_704_067_200_000L,
            duration = 120_000L,
            ended = "local",
            recordingId = 99L
        )

        val json = JSONObject(callLog.toJson())

        assertEquals("call-1", json.getString("callId"))
        assertEquals("video", json.getString("type"))
        assertEquals("zain", json.getString("initiator"))
        assertEquals("sanu", json.getString("receiver"))
        assertEquals(1_704_067_200_000L, json.getLong("startTime"))
        assertEquals(120_000L, json.getLong("duration"))
        assertEquals("local", json.getString("ended"))
        assertEquals(99L, json.getLong("recId"))
    }

    @Test
    fun messageTypeConstantsStayDistinctForFeatureRouting() {
        val messageTypes = setOf(
            AppendOnlyMessageDB.MSG_TEXT,
            AppendOnlyMessageDB.MSG_IMAGE,
            AppendOnlyMessageDB.MSG_VIDEO,
            AppendOnlyMessageDB.MSG_AUDIO,
            AppendOnlyMessageDB.MSG_DELETED,
            AppendOnlyMessageDB.MSG_REACTION,
            AppendOnlyMessageDB.MSG_CALL_LOG,
            AppendOnlyMessageDB.MSG_MOOD,
            AppendOnlyMessageDB.MSG_PRESENCE,
            AppendOnlyMessageDB.MSG_THEME_SYNC,
            AppendOnlyMessageDB.MSG_FILE,
            AppendOnlyMessageDB.MSG_CARE_DROP
        )

        assertEquals(12, messageTypes.size)
        assertNotEquals(AppendOnlyMessageDB.MSG_TEXT, AppendOnlyMessageDB.MSG_FILE)
    }
}
