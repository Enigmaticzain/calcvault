# Multi-Device Sync - Quick Start & Examples

## ⚡ 5-Minute Quick Start

### Step 1: Copy Files (1 minute)
```bash
cp MultiDeviceSyncEngine.kt app/src/main/java/com/calcvault/sync/
cp MultiDeviceSyncService.kt app/src/main/java/com/calcvault/sync/
cp DeviceLinkingActivity.kt app/src/main/java/com/calcvault/ui/sync/
```

### Step 2: Update ChatActivity (2 minutes)
```kotlin
// Add imports
import com.calcvault.sync.MultiDeviceSyncService
import com.calcvault.sync.ContinuityManager

// Add properties
private var syncService: MultiDeviceSyncService? = null
private var continuityManager: ContinuityManager? = null

// In onCreate()
val syncIntent = Intent(this, MultiDeviceSyncService::class.java)
startService(syncIntent)
bindService(syncIntent, syncServiceConnection, Context.BIND_AUTO_CREATE)

// In onResume()
continuityManager?.onAppResumed()
restoreScrollPosition()

// In onPause()
continuityManager?.onAppPaused()
```

### Step 3: Update Manifest (1 minute)
```xml
<service android:name="com.calcvault.sync.MultiDeviceSyncService" />
<activity android:name="com.calcvault.ui.sync.DeviceLinkingActivity" />
```

### Step 4: Build & Test (1 minute)
```bash
./gradlew clean build
./gradlew installDebug
```

**Done!** Multi-device sync is now active. ✅

---

## 🎮 Usage Examples

### Example 1: Link a Device

**On Primary Device (Phone):**
```kotlin
val syncEngine = MultiDeviceSyncEngine(context, messageDB, storageEngine)
val code = syncEngine.generateLinkingCode()
// Display code to user: "123 456"
```

**On Secondary Device (Tablet):**
```kotlin
val syncEngine = MultiDeviceSyncEngine(context, messageDB, storageEngine)
val success = syncEngine.validateAndLinkDevice("123456", deviceId)
if (success) {
    Toast.makeText(context, "Device linked!", Toast.LENGTH_SHORT).show()
}
```

### Example 2: Send Message with Sync

```kotlin
// Send message
val message = messageDB.sendTextMessage(localUserId, partnerId, "Hello!")

// Sync to other devices
lifecycleScope.launch {
    syncService?.onMessageSent(message)
}
```

### Example 3: Track Scroll Position

```kotlin
// When user scrolls
binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        if (lastVisiblePosition >= 0) {
            val messageId = messages[lastVisiblePosition].id
            continuityManager?.onScrollPositionChanged(messageId)
        }
    }
})
```

### Example 4: Restore Scroll Position

```kotlin
// On app resume
override fun onResume() {
    super.onResume()
    continuityManager?.onAppResumed()
    
    if (continuityManager?.shouldAutoScroll() == true) {
        val position = continuityManager?.getRestoredScrollPosition() ?: return
        val index = continuityManager?.getScrollIndex(position) ?: return
        if (index >= 0) {
            binding.rvMessages.scrollToPosition(index)
        }
    }
}
```

### Example 5: Get Linked Devices

```kotlin
val devices = syncService?.getLinkedDevices() ?: emptyList()
devices.forEach { device ->
    println("${device.deviceType}: ${device.deviceName}")
    println("Linked: ${device.linkedAt}")
    println("Last seen: ${device.lastSeenAt}")
}
```

### Example 6: Unlink Device

```kotlin
val devices = syncService?.getLinkedDevices() ?: emptyList()
val tabletDevice = devices.find { it.deviceType == "tablet" }
if (tabletDevice != null) {
    syncService?.unlinkDevice(tabletDevice.deviceId)
    Toast.makeText(context, "Device unlinked", Toast.LENGTH_SHORT).show()
}
```

### Example 7: Force Logout Remote Device

```kotlin
val devices = syncService?.getLinkedDevices() ?: emptyList()
devices.forEach { device ->
    syncService?.forceLogoutRemote(device.deviceId)
}
```

### Example 8: Get Delta Updates

```kotlin
lifecycleScope.launch {
    val state = syncEngine.getDeviceSyncState(deviceId)
    if (state != null) {
        val delta = syncEngine.getDeltaUpdates(deviceId, state.lastSyncedMessageId)
        println("New messages: ${delta.size}")
    }
}
```

### Example 9: Handle Offline Updates

```kotlin
// When device comes online
lifecycleScope.launch {
    val pending = syncEngine.flushPendingUpdates(deviceId)
    println("Flushed ${pending.size} pending updates")
}
```

### Example 10: Sync Read Status

```kotlin
// When message is read
lifecycleScope.launch {
    syncService?.onMessageRead(messageId, localUserId)
}
```

---

## 🔗 Device Linking Flow

### Generate Code
```kotlin
val code = syncEngine.generateLinkingCode()
// Returns: "123456"
// Code expires in 5 minutes
```

### Validate Code
```kotlin
val success = syncEngine.validateAndLinkDevice(code, remoteDeviceId)
// Returns: true if valid, false if expired or incorrect
```

### Get Linked Devices
```kotlin
val devices = syncEngine.getLinkedDevices()
// Returns: List<LinkedDevice>
```

### Unlink Device
```kotlin
syncEngine.unlinkDevice(deviceId)
// Removes device from linked list
```

---

## 📊 Data Flow Examples

### Message Sync Flow
```
Phone: sendText("Hello")
    ↓
messageDB.sendTextMessage()
    ↓
syncService.onMessageSent(message)
    ↓
syncEngine.syncMessageToDevices(message)
    ↓
Queue update for tablet
    ↓
Background service (every 5 seconds)
    ↓
Flush pending updates
    ↓
Tablet receives update
    ↓
messageDB.saveReceivedMessage()
    ↓
Message appears in chat
```

### Scroll Position Flow
```
User scrolls to message #250
    ↓
onScrolled() callback
    ↓
continuityManager.onScrollPositionChanged(250)
    ↓
syncService.updateScrollPosition(250)
    ↓
Saved to storage
    ↓
User opens tablet
    ↓
onAppResumed()
    ↓
restoreScrollPosition()
    ↓
Auto-scroll to message #250
```

### Offline Sync Flow
```
Tablet offline
    ↓
Send message on phone
    ↓
syncEngine.syncMessageToDevices()
    ↓
Queue update for tablet
    ↓
Tablet comes online
    ↓
Background service detects online
    ↓
Flush pending updates
    ↓
Message appears on tablet
```

---

## 🧪 Testing Examples

### Test Device Linking
```kotlin
// Generate code on phone
val code = syncEngine.generateLinkingCode()

// Validate on tablet
val success = syncEngine.validateAndLinkDevice(code, tabletDeviceId)
assert(success) { "Device linking failed" }

// Verify linked
val devices = syncEngine.getLinkedDevices()
assert(devices.any { it.deviceId == tabletDeviceId }) { "Device not linked" }
```

### Test Message Sync
```kotlin
// Send message on phone
val message = messageDB.sendTextMessage("user1", "user2", "Hello")
syncService?.onMessageSent(message)

// Wait for sync
Thread.sleep(6000)  // Wait for background service

// Verify on tablet
val messages = messageDB.getChatMessages()
assert(messages.any { it.id == message.id }) { "Message not synced" }
```

### Test Scroll Restoration
```kotlin
// Scroll to position on phone
continuityManager?.onScrollPositionChanged(250)

// Close app
continuityManager?.onAppPaused()

// Open app on tablet
continuityManager?.onAppResumed()

// Verify position restored
val position = continuityManager?.getRestoredScrollPosition()
assert(position == 250L) { "Position not restored" }
```

---

## 🔐 Security Examples

### Device Fingerprinting
```kotlin
val fingerprint = keyManager.getKeyFingerprint()
// Returns: "a1b2:c3d4:e5f6:g7h8"
```

### Secure Key Exchange
```kotlin
// On primary device
val ephemeralPubKey = keyManager.startEphemeralExchange()

// Send to secondary device
// Secondary device receives and validates

// On secondary device
keyManager.completeEphemeralExchange(ephemeralPubKey)
// Session established
```

### Encrypt Message Before Sync
```kotlin
val plaintext = message.content.toByteArray()
val encrypted = keyManager.encrypt(plaintext)
// Send encrypted to other device

// On receiving device
val decrypted = keyManager.decrypt(encrypted)
val content = String(decrypted)
```

---

## 🎯 Common Patterns

### Pattern 1: Initialize Sync on App Start
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Start sync service
    val syncIntent = Intent(this, MultiDeviceSyncService::class.java)
    startService(syncIntent)
    bindService(syncIntent, syncServiceConnection, Context.BIND_AUTO_CREATE)
}
```

### Pattern 2: Restore State on Resume
```kotlin
override fun onResume() {
    super.onResume()
    continuityManager?.onAppResumed()
    restoreScrollPosition()
}
```

### Pattern 3: Save State on Pause
```kotlin
override fun onPause() {
    super.onPause()
    continuityManager?.onAppPaused()
    val lastPosition = getLastVisiblePosition()
    continuityManager?.onScrollPositionChanged(lastPosition)
}
```

### Pattern 4: Sync on Message Send
```kotlin
private fun sendMessage(text: String) {
    val message = messageDB.sendTextMessage(localUserId, partnerId, text)
    lifecycleScope.launch {
        syncService?.onMessageSent(message)
    }
}
```

### Pattern 5: Handle Device Linking
```kotlin
private fun linkDevice() {
    startActivity(Intent(this, DeviceLinkingActivity::class.java))
}
```

---

## 📋 Integration Checklist

- [ ] Copy 3 core files
- [ ] Add imports to ChatActivity
- [ ] Add properties to ChatActivity
- [ ] Add service binding
- [ ] Add continuity manager
- [ ] Hook onCreate()
- [ ] Hook onResume()
- [ ] Hook onPause()
- [ ] Hook onDestroy()
- [ ] Hook message sending
- [ ] Hook scroll tracking
- [ ] Update AndroidManifest.xml
- [ ] Add service declaration
- [ ] Add activity declaration
- [ ] Build project
- [ ] Test on device
- [ ] Verify all features work

---

## 🚀 Deployment

### Build
```bash
./gradlew clean build
```

### Test
```bash
./gradlew installDebug
```

### Release
```bash
./gradlew assembleRelease
```

---

## 📞 Quick Reference

| Task | Code |
|------|------|
| Generate code | `syncEngine.generateLinkingCode()` |
| Link device | `syncEngine.validateAndLinkDevice(code, id)` |
| Get devices | `syncEngine.getLinkedDevices()` |
| Unlink device | `syncEngine.unlinkDevice(id)` |
| Sync message | `syncService?.onMessageSent(msg)` |
| Sync read | `syncService?.onMessageRead(id, user)` |
| Track scroll | `continuityManager?.onScrollPositionChanged(id)` |
| Restore scroll | `continuityManager?.getRestoredScrollPosition()` |

---

**Multi-Device Sync is ready to use!** 👫✨
