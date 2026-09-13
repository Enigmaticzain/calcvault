# 🔧 Enable All Disabled Sources - Complete Integration Guide

## Overview

The following features are currently disabled in `disabled_sources/` directory:

1. **Browser** - Private browser activity
2. **Chat** - Character chat system
3. **Filters** - Video filter engine
4. **House** - Dual companion house
5. **Keyboard** - Keyboard theme system
6. **Mood** - Mood activity
7. **Notifications** - Dual notification system
8. **Settings** - Settings and screen share
9. **Sync** - Multi-device sync engine
10. **WatchTogether** - Watch together activity

---

## Step 1: Move Files Back to Source

### 1.1 Browser Module
```bash
# Move PrivateBrowserActivity.kt
cp disabled_sources/browser/PrivateBrowserActivity.kt \
   app/src/main/java/com/calcvault/browser/
```

### 1.2 Chat Module
```bash
# Move character chat files
cp disabled_sources/chat/*.kt \
   app/src/main/java/com/calcvault/chat/
```

### 1.3 Filters Module
```bash
# Move video filter files
cp disabled_sources/filters/*.kt \
   app/src/main/java/com/calcvault/filters/
```

### 1.4 House Module
```bash
# Move house files
cp disabled_sources/house/*.kt \
   app/src/main/java/com/calcvault/house/
```

### 1.5 Keyboard Module
```bash
# Move keyboard files
cp disabled_sources/keyboard/*.kt \
   app/src/main/java/com/calcvault/keyboard/
```

### 1.6 Mood Module
```bash
# Move mood files
cp disabled_sources/mood/*.kt \
   app/src/main/java/com/calcvault/mood/
```

### 1.7 Notifications Module
```bash
# Move notification files
cp disabled_sources/notifications/*.kt \
   app/src/main/java/com/calcvault/notifications/
```

### 1.8 Settings Module
```bash
# Move settings files
cp disabled_sources/settings/*.kt \
   app/src/main/java/com/calcvault/settings/
```

### 1.9 Sync Module
```bash
# Move sync files
cp -r disabled_sources/sync/* \
   app/src/main/java/com/calcvault/sync/
```

### 1.10 WatchTogether Module
```bash
# Move watch together files
cp disabled_sources/watchtogether/*.kt \
   app/src/main/java/com/calcvault/watchtogether/
```

---

## Step 2: Update AndroidManifest.xml

Uncomment the following activities in `app/src/main/AndroidManifest.xml`:

### 2.1 Call Activity
```xml
<activity android:name=".ui.call.CallActivity"
    android:exported="false" android:screenOrientation="portrait"
    android:turnScreenOn="true" android:keepScreenOn="true" />
```

### 2.2 Mood Activity
```xml
<activity android:name=".ui.mood.MoodActivity"
    android:exported="false" android:screenOrientation="portrait" />
```

### 2.3 Browser Activity
```xml
<activity android:name=".ui.browser.PrivateBrowserActivity"
    android:exported="false" android:screenOrientation="portrait" />
```

### 2.4 Settings Activity
```xml
<activity android:name=".ui.settings.SettingsActivity"
    android:exported="false" android:screenOrientation="portrait"
    android:windowSoftInputMode="adjustResize" />
```

### 2.5 Sync Activity (if exists)
```xml
<activity android:name=".ui.sync.SyncActivity"
    android:exported="false" android:screenOrientation="portrait" />
```

---

## Step 3: Update build.gradle

Ensure all dependencies are included:

```gradle
dependencies {
    // ... existing dependencies ...
    
    // For browser
    implementation 'androidx.webkit:webkit:1.8.0'
    
    // For video filters
    implementation 'org.opencv:opencv-android:4.8.0'
    
    // For notifications
    implementation 'androidx.core:core:1.13.1'
    
    // For sync
    implementation 'androidx.work:work-runtime-ktx:2.9.1'
}
```

---

## Step 4: Enable Features in Code

### 4.1 Browser Module
Create `app/src/main/java/com/calcvault/ui/browser/PrivateBrowserActivity.kt`:
- Implement private browsing
- Add certificate pinning
- Implement cookie management

### 4.2 Chat Module
Create `app/src/main/java/com/calcvault/ui/chat/` files:
- Character chat template
- Character message handler
- Chat theme system

### 4.3 Filters Module
Create `app/src/main/java/com/calcvault/filters/` files:
- Video filter engine
- Filter control panel
- Filter settings activity

### 4.4 House Module
Create `app/src/main/java/com/calcvault/house/` files:
- Dual companion house
- Character behavior engine
- Drag-drop interactions

### 4.5 Keyboard Module
Create `app/src/main/java/com/calcvault/keyboard/` files:
- Keyboard theme system
- Theme synchronization

### 4.6 Mood Module
Create `app/src/main/java/com/calcvault/ui/mood/MoodActivity.kt`:
- Mood selection UI
- Mood persistence
- Mood-based animations

### 4.7 Notifications Module
Create `app/src/main/java/com/calcvault/notifications/` files:
- Dual notification system
- Notification manager

### 4.8 Settings Module
Create `app/src/main/java/com/calcvault/ui/settings/` files:
- Settings activity
- Screen share activity
- Screen share engine

### 4.9 Sync Module
Create `app/src/main/java/com/calcvault/sync/` files:
- Multi-device sync engine
- Hash chain sync
- Watch session manager

### 4.10 WatchTogether Module
Create `app/src/main/java/com/calcvault/watchtogether/` files:
- Watch together activity
- Picture-in-picture overlay

---

## Step 5: Integration Points

### 5.1 MainActivity Integration
Add navigation to disabled features:

```kotlin
// In MainVaultActivity.kt
private fun setupNavigationToDisabledFeatures() {
    // Browser
    findViewById<Button>(R.id.btn_browser)?.setOnClickListener {
        startActivity(Intent(this, PrivateBrowserActivity::class.java))
    }
    
    // Mood
    findViewById<Button>(R.id.btn_mood)?.setOnClickListener {
        startActivity(Intent(this, MoodActivity::class.java))
    }
    
    // Settings
    findViewById<Button>(R.id.btn_settings)?.setOnClickListener {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
    
    // Watch Together
    findViewById<Button>(R.id.btn_watch)?.setOnClickListener {
        startActivity(Intent(this, WatchTogetherActivity::class.java))
    }
}
```

### 5.2 Messaging Integration
Add mood updates to messaging:

```kotlin
// In NetworkMessageEngine.kt
suspend fun sendMoodUpdate(mood: String) {
    val payload = mood.toByteArray()
    sendEncryptedPayload(
        id = System.currentTimeMillis(),
        data = payload,
        type = AppendOnlyMessageDB.MSG_MOOD
    )
}
```

### 5.3 Sync Integration
Add multi-device sync:

```kotlin
// In MainVaultActivity.kt
private fun initializeMultiDeviceSync() {
    val syncEngine = MultiDeviceSyncEngine(this)
    syncEngine.initialize(
        localDeviceId = getDeviceId(),
        partnerDeviceId = getPartnerDeviceId()
    )
}
```

### 5.4 Notification Integration
Add dual notifications:

```kotlin
// In CalcVaultApp.kt
private fun setupNotifications() {
    val notificationManager = DualNotificationSystem(this)
    notificationManager.initialize()
}
```

---

## Step 6: Permissions

Ensure all permissions are in AndroidManifest.xml:

```xml
<!-- Browser -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Video Filters -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Screen Share -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAPTURE_AUDIO_OUTPUT" />

<!-- Sync -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
```

---

## Step 7: Build and Test

### 7.1 Clean Build
```bash
./gradlew clean
./gradlew build
```

### 7.2 Test Each Feature
- [ ] Browser - Open private browser
- [ ] Chat - Send character chat message
- [ ] Filters - Apply video filter
- [ ] House - Open dual companion house
- [ ] Keyboard - Change keyboard theme
- [ ] Mood - Set mood
- [ ] Notifications - Receive notification
- [ ] Settings - Open settings
- [ ] Sync - Sync with partner device
- [ ] WatchTogether - Watch video together

---

## Step 8: Feature Activation

### 8.1 Browser
- Private browsing mode
- Certificate pinning
- Cookie management
- History clearing

### 8.2 Chat
- Character chat template
- Message handling
- Theme integration
- Background interactions

### 8.3 Filters
- Video filter engine
- Real-time filters
- Filter settings
- Filter sync

### 8.4 House
- Dual companion house
- Character behavior
- Drag-drop interactions
- Environment customization

### 8.5 Keyboard
- Keyboard themes
- Theme synchronization
- Custom layouts

### 8.6 Mood
- Mood selection
- Mood persistence
- Mood-based animations
- Mood sharing

### 8.7 Notifications
- Dual notifications
- Notification manager
- Custom notifications
- Notification sync

### 8.8 Settings
- App settings
- Screen share settings
- Theme settings
- Privacy settings

### 8.9 Sync
- Multi-device sync
- Hash chain validation
- Watch session management
- Playback synchronization

### 8.10 WatchTogether
- Watch together activity
- Picture-in-picture
- Synchronized playback
- Chat during watch

---

## Troubleshooting

### Build Errors
```bash
# Clear cache
./gradlew clean

# Rebuild
./gradlew build

# Check for missing dependencies
./gradlew dependencies
```

### Runtime Errors
- Check AndroidManifest.xml for activity declarations
- Verify all imports are correct
- Check for missing permissions
- Verify layout files exist

### Feature Not Working
- Check if activity is declared in manifest
- Verify permissions are granted
- Check logs for errors
- Test with mock data

---

## Verification Checklist

- [ ] All files moved from disabled_sources
- [ ] AndroidManifest.xml updated
- [ ] build.gradle dependencies added
- [ ] All activities declared
- [ ] All permissions added
- [ ] Navigation integrated
- [ ] Build succeeds
- [ ] App runs without crashes
- [ ] All features accessible
- [ ] All features functional

---

## Summary

**All disabled sources will be enabled with:**
- ✅ File restoration
- ✅ Manifest updates
- ✅ Dependency management
- ✅ Integration points
- ✅ Permission setup
- ✅ Feature activation
- ✅ Testing verification

**No code removed - only enabled!**

---

## Next Steps

1. Execute Step 1: Move files back
2. Execute Step 2: Update manifest
3. Execute Step 3: Update build.gradle
4. Execute Step 4: Enable features
5. Execute Step 5: Add integrations
6. Execute Step 6: Verify permissions
7. Execute Step 7: Build and test
8. Execute Step 8: Activate features
9. Verify all features work
10. Deploy updated app

---

**All disabled features will be fully functional!** 🚀
