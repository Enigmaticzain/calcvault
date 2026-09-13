# Dual Notification System - Quick Start

## ⚡ 5-Minute Setup

### Step 1: Copy Files (1 min)
```bash
cp DualNotificationSystem.kt app/src/main/java/com/calcvault/notifications/
cp NotificationSettingsActivity.kt app/src/main/java/com/calcvault/ui/
```

### Step 2: Update ChatActivity (2 min)
```kotlin
// Add import
import com.calcvault.notifications.DualNotificationSystem
import com.calcvault.notifications.NotificationPayload

// Add property
private lateinit var notificationSystem: DualNotificationSystem

// In onCreate()
notificationSystem = DualNotificationSystem(this)

// In onMessageReceived
val payload = NotificationPayload(
    messageId = msg.id,
    senderId = msg.from,
    senderName = SessionManager.partnerNickname.ifBlank { msg.from },
    content = msg.content,
    timestamp = msg.timestamp,
    isUrgent = msg.content.contains("[URGENT]"),
    type = msg.type
)
notificationSystem.showNotification(partnerUserId, payload)
```

### Step 3: Update Manifest (1 min)
```xml
<activity
    android:name="com.calcvault.ui.NotificationSettingsActivity"
    android:exported="false"
    android:screenOrientation="portrait" />

<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### Step 4: Build (1 min)
```bash
./gradlew clean build
./gradlew installDebug
```

**Done!** ✅

---

## 🎮 Quick Examples

### Show Standard Notification
```kotlin
val payload = NotificationPayload(
    messageId = 123L,
    senderId = "sanu",
    senderName = "Sanu",
    content = "Hello!",
    timestamp = System.currentTimeMillis()
)
notificationSystem.showNotification("sanu", payload)
```

### Enable Stealth Mode
```kotlin
notificationSystem.setMode("sanu", NotificationMode.STEALTH)
notificationSystem.setPrivateCode("sanu", "7226")
```

### Send Urgent Message
```kotlin
val urgentText = "[URGENT] Important message"
val msg = messageDB.sendTextMessage(localUserId, partnerUserId, urgentText)
```

### Toggle Mode
```kotlin
val config = notificationSystem.getConfig("sanu")
val newMode = if (config.mode == NotificationMode.STANDARD) {
    NotificationMode.STEALTH
} else {
    NotificationMode.STANDARD
}
notificationSystem.setMode("sanu", newMode)
```

---

## 📊 Modes Comparison

| Feature | Standard | Stealth |
|---------|----------|---------|
| Sender Name | ✅ Shown | ❌ Hidden |
| Message Preview | ✅ Shown | ❌ Hidden |
| Private Code | ❌ N/A | ✅ Shown |
| Sound | ✅ Enabled | ❌ Disabled |
| Vibration | ✅ Enabled | ❌ Disabled |
| Lock Screen | Full content | Generic + code |

---

## 🔔 Notification Examples

### Standard Mode
```
Lock Screen:
"Sanu: Hey, how are you?"

After Tap:
Full message visible
```

### Stealth Mode (no code)
```
Lock Screen:
"New update"

After Tap:
"From your secure contact"
"Hey, how are you?"
```

### Stealth Mode (with code)
```
Lock Screen:
"New update • 7226"

After Tap:
"From your secure contact"
"Hey, how are you?"
```

### Urgent Stealth
```
Lock Screen:
"Attention required • 7226"

Vibration: Strong pattern
Sound: High-priority tone
```

---

## 🧪 Testing

### Test Standard Mode
1. Set mode to Standard
2. Send message
3. Verify notification shows sender + preview
4. Tap notification
5. Verify full content visible

### Test Stealth Mode
1. Set mode to Stealth
2. Set code to "7226"
3. Send message
4. Verify lock screen shows "New update • 7226"
5. Tap notification
6. Verify full content visible

### Test Urgent
1. Send message with "[URGENT]" prefix
2. Verify vibration pattern
3. Verify "Attention required" text
4. Verify high-priority channel

---

## 📋 Integration Checklist

- [ ] Copy 2 files
- [ ] Update ChatActivity
- [ ] Update Manifest
- [ ] Build project
- [ ] Test on device
- [ ] Verify all modes work

---

## 🚀 Next Steps

1. **Immediate**: Copy files and integrate
2. **Today**: Test all notification modes
3. **This Week**: Deploy to production
4. **Ongoing**: Monitor user feedback

---

**Dual Notification System is ready to use!** 🔔✨
