# ⚡ QUICK REFERENCE - Enable All Disabled Sources

## One-Command Enablement

Copy and paste these commands to enable all disabled sources:

---

## 1. Copy All Files Back

```bash
# Create directories
mkdir -p app/src/main/java/com/calcvault/ui/browser
mkdir -p app/src/main/java/com/calcvault/ui/house
mkdir -p app/src/main/java/com/calcvault/ui/keyboard
mkdir -p app/src/main/java/com/calcvault/ui/filters
mkdir -p app/src/main/java/com/calcvault/notifications
mkdir -p app/src/main/java/com/calcvault/watchtogether
mkdir -p app/src/main/java/com/calcvault/sync/chain

# Copy all files
cp disabled_sources/browser/*.kt app/src/main/java/com/calcvault/ui/browser/
cp disabled_sources/chat/*.kt app/src/main/java/com/calcvault/ui/chat/
cp disabled_sources/filters/*.kt app/src/main/java/com/calcvault/ui/filters/
cp disabled_sources/house/*.kt app/src/main/java/com/calcvault/ui/house/
cp disabled_sources/keyboard/*.kt app/src/main/java/com/calcvault/ui/keyboard/
cp disabled_sources/mood/*.kt app/src/main/java/com/calcvault/ui/mood/
cp disabled_sources/notifications/*.kt app/src/main/java/com/calcvault/notifications/
cp disabled_sources/settings/*.kt app/src/main/java/com/calcvault/ui/settings/
cp -r disabled_sources/sync/* app/src/main/java/com/calcvault/sync/
cp disabled_sources/watchtogether/*.kt app/src/main/java/com/calcvault/watchtogether/
```

---

## 2. Update Manifest

```bash
# Replace with enabled manifest
cp app/src/main/AndroidManifest_ENABLED.xml app/src/main/AndroidManifest.xml
```

---

## 3. Build

```bash
# Clean and build
./gradlew clean
./gradlew build

# Or just build
./gradlew assembleDebug
```

---

## 4. Test

```bash
# Run on device
./gradlew installDebug

# Or build release
./gradlew assembleRelease
```

---

## Files Enabled

### Browser (1)
- PrivateBrowserActivity.kt

### Chat (4)
- BackgroundCharacterInteraction.kt
- CharacterChatActivityTemplate.kt
- CharacterChatTheme.kt
- CharacterMessageHandler.kt

### Filters (4)
- FilterControlPanel.kt
- FilterSettingsActivity.kt
- FilterSyncEngine.kt
- VideoFilterEngine.kt

### House (7)
- CharacterBehaviorEngine.kt
- ChatTriggerEngine.kt
- DragDropInteractionEngine.kt
- DualCompanionHouseApplicator.kt
- HouseEnvironment.kt
- HouseEnvironmentView.kt
- HouseModels.kt

### Keyboard (2)
- KeyboardTheme.kt
- ThemeSynchronizationBridge.kt

### Mood (1)
- MoodActivity.kt

### Notifications (2)
- CVNotificationManager.kt
- DualNotificationSystem.kt

### Settings (6)
- CoupleThemeSettingsActivity.kt
- IncomingScreenShareLauncher.kt
- ScreenShareActivity.kt
- ScreenShareEngine.kt
- ScreenShareSession.kt
- SettingsActivity.kt

### Sync (10)
- HashChainSyncEngine.kt
- HashChainSyncValidator.kt
- DualUSBSyncEngine.kt
- MovieValidator.kt
- MultiDeviceSyncEngine.kt
- MultiDeviceSyncService.kt
- PlaybackSynchronizationEngine.kt
- RealSyncEngine.kt
- WatchSession.kt
- WatchSessionManager.kt
- WatchSessionNetworkCoordinator.kt

### WatchTogether (2)
- PictureInPictureOverlay.kt
- WatchTogetherActivity.kt

---

## Activities Enabled in Manifest

```xml
<!-- Call Activity -->
<activity android:name=".ui.call.CallActivity" ... />

<!-- Mood Activity -->
<activity android:name=".ui.mood.MoodActivity" ... />

<!-- Browser Activity -->
<activity android:name=".ui.browser.PrivateBrowserActivity" ... />

<!-- Settings Activity -->
<activity android:name=".ui.settings.SettingsActivity" ... />

<!-- Couple Theme Settings -->
<activity android:name=".ui.settings.CoupleThemeSettingsActivity" ... />

<!-- Screen Share Launcher -->
<activity android:name=".ui.settings.IncomingScreenShareLauncher" ... />

<!-- Watch Together -->
<activity android:name=".watchtogether.WatchTogetherActivity" ... />

<!-- Filter Settings -->
<activity android:name=".ui.filters.FilterSettingsActivity" ... />

<!-- Multi-Device Sync Service -->
<service android:name=".sync.MultiDeviceSyncService" ... />
```

---

## Dependencies to Add

Add to `app/build.gradle`:

```gradle
// Browser
implementation 'androidx.webkit:webkit:1.8.0'

// Video Filters
implementation 'org.opencv:opencv-android:4.8.0'

// Notifications
implementation 'androidx.core:core:1.13.1'

// Sync
implementation 'androidx.work:work-runtime-ktx:2.9.1'

// Screen Share
implementation 'androidx.mediarouter:mediarouter:1.7.0'
```

---

## Verification

After enabling, verify:

- [ ] All files copied
- [ ] Manifest updated
- [ ] Build succeeds
- [ ] No compilation errors
- [ ] App runs without crashes
- [ ] All activities accessible
- [ ] All features functional

---

## Troubleshooting

### Build fails
```bash
./gradlew clean
./gradlew build --stacktrace
```

### Missing files
```bash
# Check if files exist
ls -la app/src/main/java/com/calcvault/ui/browser/
ls -la app/src/main/java/com/calcvault/ui/mood/
```

### Manifest errors
```bash
# Validate manifest
./gradlew validateSigningRelease
```

---

## Summary

**39 files enabled across 10 modules**

✅ Browser
✅ Chat
✅ Filters
✅ House
✅ Keyboard
✅ Mood
✅ Notifications
✅ Settings
✅ Sync
✅ WatchTogether

**All features ready to use!** 🚀
