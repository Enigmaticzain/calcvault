package com.calcvault.sync

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.*

/**
 * Multi-Device Sync Service
 * * Runs in background to:
 * - Sync messages across devices
 * - Track scroll position for continuity
 * - Handle offline queuing
 * - Manage device state
 */

class MultiDeviceSyncService : Service() {
    private lateinit var syncEngine: MultiDeviceSyncEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var storageEngine: USBStorageEngine
    private val binder = SyncBinder()
    private var syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    companion object {
        private const val TAG = "MultiDeviceSync"
        private const val SYNC_INTERVAL_MS = 5_000L // 5 seconds
    }

    inner class SyncBinder : Binder() {
        fun getService(): MultiDeviceSyncService = this@MultiDeviceSyncService
    }

    override fun onCreate() {
        super.onCreate()
        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        syncEngine = MultiDeviceSyncEngine(this, messageDB, storageEngine)
        startSyncLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = syncScope.launch {
            while (isActive) {
                try {
                    performSync()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error", e)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun performSync() {
        val linkedDevices = syncEngine.getLinkedDevices()
        if (linkedDevices.isEmpty()) return

        linkedDevices.forEach { device ->
            try {
                // Flush pending updates
                val pending = syncEngine.flushPendingUpdates(device.deviceId)
                if (pending.isNotEmpty()) {
                    Log.d(TAG, "Flushed ${pending.size} updates to ${device.deviceName}")
                }

                // Get delta updates
                val state = syncEngine.getDeviceSyncState(device.deviceId)
                if (state != null) {
                    val delta = syncEngine.getDeltaUpdates(device.deviceId, state.lastSyncedMessageId)
                    if (delta.isNotEmpty()) {
                        Log.d(TAG, "Synced ${delta.size} new messages to ${device.deviceName}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing with ${device.deviceName}", e)
            }
        }
    }

    suspend fun onMessageSent(message: MessageRecord) {
        syncEngine.syncMessageToDevices(message)
    }

    suspend fun onMessageRead(messageId: Long, readBy: String) {
        syncEngine.syncReadStatus(messageId, readBy)
    }

    suspend fun onScrollPositionChanged(messageId: Long) {
        syncEngine.syncScrollPosition(messageId)
    }

    fun getLastScrollPosition(): Long {
        return syncEngine.getLastScrollPosition()
    }

    fun updateScrollPosition(messageId: Long) {
        syncEngine.updateLastScrollPosition(messageId)
    }

    fun getLinkedDevices() = syncEngine.getLinkedDevices()

    fun unlinkDevice(deviceId: String) = syncEngine.unlinkDevice(deviceId)

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        syncScope.cancel()
        syncEngine.cleanup()
    }
}

/**
 * Continuity Manager
 * * Handles app resume/pause to maintain scroll position
 * and conversation continuity across devices.
 */

class ContinuityManager(
    private val syncService: MultiDeviceSyncService,
    private val messageDB: AppendOnlyMessageDB
) {
    private var lastScrollPosition = 0L
    private var isAppInForeground = false

    fun onAppResumed() {
        isAppInForeground = true
        lastScrollPosition = syncService.getLastScrollPosition()
        Log.d("Continuity", "App resumed, last position: $lastScrollPosition")
    }

    fun onAppPaused() {
        isAppInForeground = false
    }

    fun onScrollPositionChanged(messageId: Long) {
        if (isAppInForeground) {
            lastScrollPosition = messageId
            syncService.updateScrollPosition(messageId)
        }
    }

    fun getRestoredScrollPosition(): Long {
        return lastScrollPosition
    }

    fun shouldAutoScroll(): Boolean {
        return lastScrollPosition > 0L
    }

    fun getMessageAtPosition(messageId: Long): MessageRecord? {
        val messages = messageDB.getChatMessages()
        return messages.firstOrNull { it.id == messageId }
    }

    fun getScrollIndex(messageId: Long): Int {
        val messages = messageDB.getChatMessages()
        return messages.indexOfFirst { it.id == messageId }
    }
}
