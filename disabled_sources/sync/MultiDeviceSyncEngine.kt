package com.calcvault.sync

import android.content.Context
import android.util.Base64
import com.calcvault.crypto.E2EKeyManager
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.provider.StorageManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-Device Sync System
 * 
 * Enables seamless conversation continuity across phone + tablet
 * with end-to-end encryption and real-time synchronization.
 */

data class LinkedDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,  // "phone", "tablet", "desktop"
    val publicKeyFingerprint: String,
    val linkedAt: Long,
    val lastSeenAt: Long,
    val isActive: Boolean = true
)

data class SyncedMessage(
    val id: Long,
    val content: String,
    val timestamp: Long,
    val sender: String,
    val delivered: Boolean,
    val read: Boolean,
    val type: Int,
    val extra: String = ""
)

data class DeviceSyncState(
    val deviceId: String,
    val lastSyncedMessageId: Long,
    val lastScrollPosition: Long,
    val lastOpenedAt: Long,
    val unreadCount: Int
)

class MultiDeviceSyncEngine(
    private val context: Context,
    private val messageDB: AppendOnlyMessageDB,
    private val storageEngine: USBStorageEngine
) {
    private val keyManager = E2EKeyManager.getInstance()
    private val linkedDevices = ConcurrentHashMap<String, LinkedDevice>()
    private val syncState = ConcurrentHashMap<String, DeviceSyncState>()
    private val pendingUpdates = ConcurrentHashMap<String, MutableList<SyncUpdate>>()
    private var localDeviceId = ""
    private var syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val DEVICES_KEY = "sync/linked_devices"
        private const val SYNC_STATE_KEY = "sync/device_state"
        private const val PENDING_UPDATES_KEY = "sync/pending_updates"
        private const val LOCAL_DEVICE_ID_KEY = "sync/local_device_id"
        private const val LINKING_CODE_LENGTH = 6
        private const val LINKING_CODE_EXPIRY_MS = 5 * 60 * 1000L  // 5 minutes
    }

    data class SyncUpdate(
        val type: String,  // "message", "read", "scroll", "state"
        val timestamp: Long,
        val data: JSONObject
    )

    init {
        loadLocalDeviceId()
        loadLinkedDevices()
        loadSyncState()
    }

    // ── Device Linking ─────────────────────────────────────────────────────

    fun generateLinkingCode(): String {
        val code = (100000..999999).random().toString()
        val expiry = System.currentTimeMillis() + LINKING_CODE_EXPIRY_MS
        val data = JSONObject().apply {
            put("code", code)
            put("expiry", expiry)
            put("deviceId", localDeviceId)
            put("deviceName", android.os.Build.MODEL)
            put("deviceType", getDeviceType())
            put("publicKey", keyManager.getKeyFingerprint())
        }
        StorageManager.write("sync/linking_code", data.toString().toByteArray())
        return code
    }

    fun validateAndLinkDevice(linkingCode: String, remoteDeviceId: String): Boolean {
        val data = StorageManager.read("sync/linking_code")?.let { String(it) }
            ?.let { JSONObject(it) } ?: return false

        val code = data.optString("code")
        val expiry = data.optLong("expiry")

        if (code != linkingCode || System.currentTimeMillis() > expiry) {
            return false
        }

        val linkedDevice = LinkedDevice(
            deviceId = remoteDeviceId,
            deviceName = data.optString("deviceName", "Unknown Device"),
            deviceType = data.optString("deviceType", "unknown"),
            publicKeyFingerprint = data.optString("publicKey", ""),
            linkedAt = System.currentTimeMillis(),
            lastSeenAt = System.currentTimeMillis()
        )

        linkedDevices[remoteDeviceId] = linkedDevice
        saveLinkedDevices()
        initializeSyncState(remoteDeviceId)
        
        StorageManager.delete("sync/linking_code")
        return true
    }

    fun getLinkedDevices(): List<LinkedDevice> {
        return linkedDevices.values.toList()
    }

    fun unlinkDevice(deviceId: String): Boolean {
        linkedDevices.remove(deviceId)
        syncState.remove(deviceId)
        pendingUpdates.remove(deviceId)
        saveLinkedDevices()
        return true
    }

    fun forceLogoutRemote(deviceId: String): Boolean {
        val logoutSignal = JSONObject().apply {
            put("type", "force_logout")
            put("timestamp", System.currentTimeMillis())
            put("fromDevice", localDeviceId)
        }
        queueSyncUpdate(deviceId, SyncUpdate("logout", System.currentTimeMillis(), logoutSignal))
        return true
    }

    // ── Message Sync ───────────────────────────────────────────────────────

    suspend fun syncMessageToDevices(message: MessageRecord) {
        linkedDevices.keys.forEach { deviceId ->
            val syncMsg = SyncUpdate(
                type = "message",
                timestamp = System.currentTimeMillis(),
                data = JSONObject().apply {
                    put("id", message.id)
                    put("content", message.content)
                    put("timestamp", message.timestamp)
                    put("sender", message.from)
                    put("delivered", message.delivered)
                    put("read", message.read)
                    put("type", message.type)
                    put("extra", message.extra)
                }
            )
            queueSyncUpdate(deviceId, syncMsg)
        }
    }

    suspend fun syncReadStatus(messageId: Long, readBy: String) {
        linkedDevices.keys.forEach { deviceId ->
            val update = SyncUpdate(
                type = "read",
                timestamp = System.currentTimeMillis(),
                data = JSONObject().apply {
                    put("messageId", messageId)
                    put("readBy", readBy)
                }
            )
            queueSyncUpdate(deviceId, update)
        }
    }

    suspend fun syncScrollPosition(messageId: Long) {
        val state = syncState[localDeviceId] ?: return
        val updated = state.copy(lastScrollPosition = messageId, lastOpenedAt = System.currentTimeMillis())
        syncState[localDeviceId] = updated

        linkedDevices.keys.forEach { deviceId ->
            val update = SyncUpdate(
                type = "scroll",
                timestamp = System.currentTimeMillis(),
                data = JSONObject().apply {
                    put("lastMessageId", messageId)
                    put("timestamp", System.currentTimeMillis())
                }
            )
            queueSyncUpdate(deviceId, update)
        }
    }

    // ── Continuity ─────────────────────────────────────────────────────────

    fun getLastScrollPosition(): Long {
        return syncState[localDeviceId]?.lastScrollPosition ?: 0L
    }

    fun updateLastScrollPosition(messageId: Long) {
        val state = syncState[localDeviceId] ?: DeviceSyncState(
            localDeviceId, 0L, messageId, System.currentTimeMillis(), 0
        )
        syncState[localDeviceId] = state.copy(lastScrollPosition = messageId)
        saveSyncState()
    }

    fun getDeviceSyncState(deviceId: String): DeviceSyncState? {
        return syncState[deviceId]
    }

    fun updateDeviceSyncState(deviceId: String, state: DeviceSyncState) {
        syncState[deviceId] = state
        saveSyncState()
    }

    // ── Offline Support ────────────────────────────────────────────────────

    private fun queueSyncUpdate(deviceId: String, update: SyncUpdate) {
        val queue = pendingUpdates.getOrPut(deviceId) { mutableListOf() }
        queue.add(update)
        savePendingUpdates()
    }

    suspend fun flushPendingUpdates(deviceId: String): List<SyncUpdate> {
        val updates = pendingUpdates[deviceId]?.toList() ?: emptyList()
        pendingUpdates[deviceId]?.clear()
        savePendingUpdates()
        return updates
    }

    suspend fun processSyncUpdates(updates: List<SyncUpdate>) {
        updates.forEach { update ->
            when (update.type) {
                "message" -> {
                    val data = update.data
                    val msg = MessageRecord(
                        id = data.getLong("id"),
                        type = data.getInt("type"),
                        from = data.getString("sender"),
                        to = "local",
                        content = data.getString("content"),
                        timestamp = data.getLong("timestamp"),
                        delivered = data.getBoolean("delivered"),
                        read = data.getBoolean("read"),
                        extra = data.optString("extra", "")
                    )
                    messageDB.saveReceivedMessage(msg)
                }
                "read" -> {
                    val messageId = update.data.getLong("messageId")
                    messageDB.markDelivered(messageId)
                }
                "scroll" -> {
                    val messageId = update.data.getLong("lastMessageId")
                    updateLastScrollPosition(messageId)
                }
                "logout" -> {
                    // Handle force logout
                }
            }
        }
    }

    // ── Delta Sync (Efficient) ─────────────────────────────────────────────

    suspend fun getDeltaUpdates(deviceId: String, sinceMessageId: Long): List<SyncUpdate> {
        val allMessages = messageDB.getChatMessages()
        val newMessages = allMessages.filter { it.id > sinceMessageId }

        return newMessages.map { msg ->
            SyncUpdate(
                type = "message",
                timestamp = msg.timestamp,
                data = JSONObject().apply {
                    put("id", msg.id)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                    put("sender", msg.from)
                    put("delivered", msg.delivered)
                    put("read", msg.read)
                    put("type", msg.type)
                    put("extra", msg.extra)
                }
            )
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private fun loadLocalDeviceId() {
        localDeviceId = StorageManager.read(LOCAL_DEVICE_ID_KEY)?.let { String(it) }
            ?: run {
                val id = keyManager.getDeviceId()
                StorageManager.write(LOCAL_DEVICE_ID_KEY, id.toByteArray())
                id
            }
    }

    private fun loadLinkedDevices() {
        val data = StorageManager.read(DEVICES_KEY)?.let { String(it) }
            ?.let { JSONArray(it) } ?: return

        for (i in 0 until data.length()) {
            val obj = data.getJSONObject(i)
            val device = LinkedDevice(
                deviceId = obj.getString("deviceId"),
                deviceName = obj.getString("deviceName"),
                deviceType = obj.getString("deviceType"),
                publicKeyFingerprint = obj.getString("publicKeyFingerprint"),
                linkedAt = obj.getLong("linkedAt"),
                lastSeenAt = obj.getLong("lastSeenAt"),
                isActive = obj.optBoolean("isActive", true)
            )
            linkedDevices[device.deviceId] = device
        }
    }

    private fun saveLinkedDevices() {
        val array = JSONArray()
        linkedDevices.values.forEach { device ->
            array.put(JSONObject().apply {
                put("deviceId", device.deviceId)
                put("deviceName", device.deviceName)
                put("deviceType", device.deviceType)
                put("publicKeyFingerprint", device.publicKeyFingerprint)
                put("linkedAt", device.linkedAt)
                put("lastSeenAt", device.lastSeenAt)
                put("isActive", device.isActive)
            })
        }
        StorageManager.write(DEVICES_KEY, array.toString().toByteArray())
    }

    private fun loadSyncState() {
        val data = StorageManager.read(SYNC_STATE_KEY)?.let { String(it) }
            ?.let { JSONArray(it) } ?: return

        for (i in 0 until data.length()) {
            val obj = data.getJSONObject(i)
            val state = DeviceSyncState(
                deviceId = obj.getString("deviceId"),
                lastSyncedMessageId = obj.getLong("lastSyncedMessageId"),
                lastScrollPosition = obj.getLong("lastScrollPosition"),
                lastOpenedAt = obj.getLong("lastOpenedAt"),
                unreadCount = obj.getInt("unreadCount")
            )
            syncState[state.deviceId] = state
        }
    }

    private fun saveSyncState() {
        val array = JSONArray()
        syncState.values.forEach { state ->
            array.put(JSONObject().apply {
                put("deviceId", state.deviceId)
                put("lastSyncedMessageId", state.lastSyncedMessageId)
                put("lastScrollPosition", state.lastScrollPosition)
                put("lastOpenedAt", state.lastOpenedAt)
                put("unreadCount", state.unreadCount)
            })
        }
        StorageManager.write(SYNC_STATE_KEY, array.toString().toByteArray())
    }

    private fun savePendingUpdates() {
        val data = JSONObject()
        pendingUpdates.forEach { (deviceId, updates) ->
            val array = JSONArray()
            updates.forEach { update ->
                array.put(JSONObject().apply {
                    put("type", update.type)
                    put("timestamp", update.timestamp)
                    put("data", update.data)
                })
            }
            data.put(deviceId, array)
        }
        StorageManager.write(PENDING_UPDATES_KEY, data.toString().toByteArray())
    }

    private fun initializeSyncState(deviceId: String) {
        syncState[deviceId] = DeviceSyncState(
            deviceId = deviceId,
            lastSyncedMessageId = 0L,
            lastScrollPosition = 0L,
            lastOpenedAt = System.currentTimeMillis(),
            unreadCount = 0
        )
        saveSyncState()
    }

    private fun getDeviceType(): String {
        val metrics = context.resources.displayMetrics
        val screenSize = kotlin.math.sqrt(
            (metrics.widthPixels / metrics.xdpi).toDouble().pow(2) +
            (metrics.heightPixels / metrics.ydpi).toDouble().pow(2)
        )
        return if (screenSize > 6.5) "tablet" else "phone"
    }

    fun cleanup() {
        syncScope.cancel()
    }
}

// Extension for power calculation
private fun Double.pow(exponent: Int): Double {
    return Math.pow(this, exponent.toDouble())
}
