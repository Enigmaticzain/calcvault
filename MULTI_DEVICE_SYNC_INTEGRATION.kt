// ChatActivity Integration for Multi-Device Sync
// Add these imports to ChatActivity.kt

import com.calcvault.sync.MultiDeviceSyncService
import com.calcvault.sync.ContinuityManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

// Add these properties to ChatActivity class

private var syncService: MultiDeviceSyncService? = null
private var continuityManager: ContinuityManager? = null
private var isSyncServiceBound = false

private val syncServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as MultiDeviceSyncService.SyncBinder
        syncService = binder.getService()
        isSyncServiceBound = true
        continuityManager = ContinuityManager(syncService!!, messageDB)
        restoreScrollPosition()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        isSyncServiceBound = false
        syncService = null
    }
}

// Add to onCreate() method

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing code ...
    
    // Start sync service
    val syncIntent = Intent(this, MultiDeviceSyncService::class.java)
    startService(syncIntent)
    bindService(syncIntent, syncServiceConnection, Context.BIND_AUTO_CREATE)
}

// Add to onResume() method

override fun onResume() {
    super.onResume()
    continuityManager?.onAppResumed()
    restoreScrollPosition()
}

// Add to onPause() method

override fun onPause() {
    super.onPause()
    continuityManager?.onAppPaused()
    val lastVisiblePosition = (binding.rvMessages.layoutManager as? LinearLayoutManager)?.findLastVisibleItemPosition()
    if (lastVisiblePosition != null && lastVisiblePosition >= 0 && messages.isNotEmpty()) {
        val messageId = messages[lastVisiblePosition].id
        continuityManager?.onScrollPositionChanged(messageId)
    }
}

// Add to onDestroy() method

override fun onDestroy() {
    super.onDestroy()
    if (isSyncServiceBound) {
        unbindService(syncServiceConnection)
    }
    // ... existing cleanup ...
}

// Add this method to restore scroll position

private fun restoreScrollPosition() {
    if (continuityManager?.shouldAutoScroll() == true) {
        val position = continuityManager?.getRestoredScrollPosition() ?: return
        val index = continuityManager?.getScrollIndex(position) ?: return
        if (index >= 0 && index < messages.size) {
            binding.rvMessages.scrollToPosition(index)
        }
    }
}

// Hook message sending to sync

private fun sendText() {
    val text = binding.etInput.text?.toString()?.trim() ?: return
    if (text.isBlank()) return
    lifecycleScope.launch {
        try {
            val msg = messageDB.sendTextMessage(localUserId, partnerUserId, text)
            val payload = runCatching {
                storageEngine.encrypt(text.toByteArray())
            }.getOrElse { text.toByteArray() }
            
            networkEngine.sendEncryptedPayload(msg.id, payload, msg.type)

            withContext(Dispatchers.Main) {
                binding.etInput.text?.clear()
                adapter.addMessage(msg)
                scrollBottom()
                animEngine.checkMessageTriggers(text)
                
                // Sync to other devices
                lifecycleScope.launch {
                    syncService?.onMessageSent(msg)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ChatActivity, "Message failed to send", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// Hook message read status

private fun markMessageAsRead(messageId: Long) {
    lifecycleScope.launch {
        messageDB.markDelivered(messageId)
        syncService?.onMessageRead(messageId, localUserId)
    }
}

// Hook scroll position tracking

private fun setupScrollListener() {
    binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            if (lastVisiblePosition >= 0 && lastVisiblePosition < messages.size) {
                val messageId = messages[lastVisiblePosition].id
                continuityManager?.onScrollPositionChanged(messageId)
            }
        }
    })
}

// Add device linking button to settings

private fun showSettingsMenu() {
    val options = arrayOf(
        "Link Device",
        "Linked Devices",
        "Settings",
        "Cancel"
    )
    AlertDialog.Builder(this)
        .setTitle("Options")
        .setItems(options) { _, which ->
            when (which) {
                0 -> startActivity(Intent(this, DeviceLinkingActivity::class.java))
                1 -> showLinkedDevices()
                2 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        .show()
}

private fun showLinkedDevices() {
    val devices = syncService?.getLinkedDevices() ?: emptyList()
    if (devices.isEmpty()) {
        Toast.makeText(this, "No devices linked", Toast.LENGTH_SHORT).show()
        return
    }

    val deviceNames = devices.map { "${it.deviceType.uppercase()} - ${it.deviceName}" }.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle("Linked Devices")
        .setItems(deviceNames) { _, which ->
            val device = devices[which]
            AlertDialog.Builder(this)
                .setTitle("Unlink Device?")
                .setMessage("${device.deviceName} will no longer sync with this account.")
                .setPositiveButton("Unlink") { _, _ ->
                    syncService?.unlinkDevice(device.deviceId)
                    Toast.makeText(this, "Device unlinked", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        .show()
}

// Add to AndroidManifest.xml

<service
    android:name="com.calcvault.sync.MultiDeviceSyncService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />

<activity
    android:name="com.calcvault.ui.sync.DeviceLinkingActivity"
    android:exported="false"
    android:screenOrientation="portrait" />

// Add permissions to AndroidManifest.xml

<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
