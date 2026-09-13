package com.calcvault.test.utils

import org.mockito.Mockito
import org.mockito.verification.VerificationMode

object TestUtils {
    inline fun <reified T> spyOn(actual: T): T = Mockito.spy(actual)

    inline fun <reified T> mockOf(): T = Mockito.mock(T::class.java)

    fun <T> verifyCall(mock: T, mode: VerificationMode = Mockito.times(1)) {
        Mockito.verify(mock, mode)
    }

    fun <T> resetMock(mock: T) {
        Mockito.reset(mock)
    }
}

object TestDataBuilder {
    fun generateMessageId(): String = "msg-${System.currentTimeMillis()}"

    fun generateUserId(): String = "user-${(1000..9999).random()}"

    fun generateDeviceId(): String = "device-${(1000..9999).random()}"

    fun generateChatMessage(
        senderId: String = generateUserId(),
        content: String = "Test message",
        timestamp: Long = System.currentTimeMillis(),
        encrypted: Boolean = false
    ): Map<String, Any> = mapOf(
        "id" to generateMessageId(),
        "senderId" to senderId,
        "content" to content,
        "timestamp" to timestamp,
        "encrypted" to encrypted,
        "isRead" to false
    )

    fun generateUser(
        id: String = generateUserId(),
        name: String = "Test User",
        status: String = "online"
    ): Map<String, String> = mapOf(
        "id" to id,
        "name" to name,
        "status" to status,
        "lastSeen" to System.currentTimeMillis().toString()
    )

    fun generateSyncData(
        key: String = "test-key",
        value: String = "test-value",
        version: Long = 1
    ): Map<String, Any> = mapOf(
        "key" to key,
        "value" to value,
        "version" to version,
        "timestamp" to System.currentTimeMillis()
    )
}

object CalcVaultAssertions {
    fun assertEncrypted(value: String) {
        assert(!value.contains(Regex("[a-zA-Z0-9]{20,}"))) {
            "Value appears to be plaintext, not encrypted: $value"
        }
    }

    fun assertValidDeviceId(deviceId: String) {
        assert(deviceId.matches(Regex("^[a-z0-9-]{10,}$"))) {
            "Invalid device ID format: $deviceId"
        }
    }

    fun assertValidMessageId(messageId: String) {
        assert(messageId.matches(Regex("^msg-[0-9]{13}$"))) {
            "Invalid message ID format: $messageId"
        }
    }

    fun assertSyncedAcrossDevices(
        deviceData: Map<String, Any>,
        value: Any,
        key: String
    ) {
        assert(deviceData[key] == value) {
            "Data not synced: expected $value but got ${deviceData[key]}"
        }
    }
}

class TestFixture {
    companion object {
        fun createTestContext() = Mockito.mock(android.content.Context::class.java)

        fun createMultipleUsers(count: Int = 3): List<Map<String, String>> {
            return (1..count).map { index ->
                TestDataBuilder.generateUser(
                    id = "user-$index",
                    name = "Test User $index"
                )
            }
        }

        fun createChatHistory(messageCount: Int = 10): List<Map<String, Any>> {
            val users = createMultipleUsers(2)
            return (1..messageCount).map { index ->
                TestDataBuilder.generateChatMessage(
                    senderId = users[index % 2]["id"]!!,
                    content = "Message $index",
                    timestamp = System.currentTimeMillis() + (index * 1000)
                )
            }
        }

        fun createSyncScenario(deviceCount: Int = 2): Pair<List<String>, Map<String, Map<String, Any>>> {
            val devices = (1..deviceCount).map { "device-$it" }
            val syncData = mutableMapOf<String, Map<String, Any>>()
            devices.forEach { deviceId ->
                syncData[deviceId] = TestDataBuilder.generateSyncData(
                    key = deviceId,
                    value = "synced-$deviceId"
                )
            }
            return Pair(devices, syncData)
        }
    }
}
