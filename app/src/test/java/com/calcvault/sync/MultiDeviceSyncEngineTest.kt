package com.calcvault.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiDeviceSyncEngineTest {

    @Test
    fun linkedDeviceDefaultsToActive() {
        val device = LinkedDevice(
            deviceId = "tablet-001",
            deviceName = "Tablet",
            deviceType = "tablet",
            publicKeyFingerprint = "aa:bb:cc",
            linkedAt = 100L,
            lastSeenAt = 200L
        )

        assertTrue(device.isActive)
        assertEquals("tablet-001", device.deviceId)
    }

    @Test
    fun linkedDeviceCanRepresentInactiveDevice() {
        val device = LinkedDevice(
            deviceId = "desktop-001",
            deviceName = "Desktop",
            deviceType = "desktop",
            publicKeyFingerprint = "dd:ee:ff",
            linkedAt = 100L,
            lastSeenAt = 200L,
            isActive = false
        )

        assertFalse(device.isActive)
    }

    @Test
    fun deviceSyncStateTracksContinuityFields() {
        val state = DeviceSyncState(
            deviceId = "phone-001",
            lastSyncedMessageId = 10L,
            lastScrollPosition = 7L,
            lastOpenedAt = 1_704_067_200_000L,
            unreadCount = 3
        )

        val updated = state.copy(lastScrollPosition = 11L, unreadCount = 0)

        assertEquals(10L, updated.lastSyncedMessageId)
        assertEquals(11L, updated.lastScrollPosition)
        assertEquals(0, updated.unreadCount)
    }

    @Test
    fun syncUpdateCarriesMessagePayloadForQueueing() {
        val payload = JSONObject().apply {
            put("id", 42L)
            put("content", "hello")
            put("timestamp", 123L)
            put("sender", "zain")
            put("delivered", false)
            put("read", false)
            put("type", 10)
            put("extra", "")
        }

        val update = MultiDeviceSyncEngine.SyncUpdate(
            type = "message",
            timestamp = 456L,
            data = payload
        )

        assertEquals("message", update.type)
        assertEquals(456L, update.timestamp)
        assertEquals("hello", update.data.getString("content"))
        assertEquals("zain", update.data.getString("sender"))
    }

    @Test
    fun syncedMessageMapsChatFieldsUsedByDeltaPipeline() {
        val message = SyncedMessage(
            id = 99L,
            content = "watch together?",
            timestamp = 1_704_067_200_000L,
            sender = "sanu",
            delivered = true,
            read = true,
            type = 10,
            extra = """{"theme":"warm"}"""
        )

        assertEquals(99L, message.id)
        assertEquals("watch together?", message.content)
        assertTrue(message.delivered)
        assertTrue(message.read)
        assertEquals("""{"theme":"warm"}""", message.extra)
    }
}
