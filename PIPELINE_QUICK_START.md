# CalcVault Pipeline - Quick Start Guide

## What Has Been Set Up

✅ **Build System** - Gradle with testing, linting, automated builds  
✅ **Test Framework** - JUnit + Mockito with 20+ unit tests  
✅ **Linting** - ktlint code style enforcement  
✅ **CI/CD Pipeline** - GitHub Actions on every PR  
✅ **Health Checks** - System verification on startup  
✅ **Initialization Order** - Controlled component startup  

---

## For Developers

### 1. Build & Test Locally
```bash
# Check code style
./gradlew ktlintFormat

# Run tests
./gradlew testAll

# Build APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/Calculator-debug-1.0.apk
```

### 2. Common Gradle Tasks
| Task | Purpose |
|------|---------|
| `assembleDebug` | Build debug APK |
| `testDebugUnitTest` | Run unit tests |
| `ktlintCheck` | Check code style |
| `ktlintFormat` | Auto-format code |
| `lint` | Full lint check |
| `testAll` | Run all tests |

### 3. Before Committing
```bash
# Format your code
./gradlew ktlintFormat

# Run tests to verify nothing breaks
./gradlew testAll

# Build to ensure compilation succeeds
./gradlew assembleDebug
```

### 4. Push to GitHub
- Create a branch: `git checkout -b feature/my-feature`
- Commit changes: `git commit -am "Add my feature"`
- Push: `git push origin feature/my-feature`
- GitHub Actions automatically runs:
  - ✅ Lint checks
  - ✅ Build compilation
  - ✅ Unit tests
  - ✅ Code analysis

---

## For Feature Integration

### Adding a New Feature Module

1. **Create the feature package**
```bash
mkdir -p app/src/main/java/com/calcvault/myfeature
```

2. **Implement your feature code**
```kotlin
// app/src/main/java/com/calcvault/myfeature/MyFeature.kt
package com.calcvault.myfeature

class MyFeature {
    fun doSomething() {
        // Your code here
    }
}
```

3. **Add unit tests**
```bash
mkdir -p app/src/test/java/com/calcvault/myfeature
# Create MyFeatureTest.kt with test cases
```

4. **Run tests**
```bash
./gradlew testDebugUnitTest
```

5. **Format and commit**
```bash
./gradlew ktlintFormat
git add .
git commit -m "Add MyFeature"
git push
```

---

## Integrating with Core Systems

### 1. Integrate with E2E Encryption
```kotlin
import com.calcvault.crypto.E2EKeyManager

// In your feature
val keyManager = E2EKeyManager()
val encrypted = keyManager.encrypt(plaintext, publicKey)
val decrypted = keyManager.decrypt(encrypted, privateKey)
```

### 2. Integrate with Notifications
```kotlin
import com.calcvault.notifications.CVNotificationManager

val notificationManager = CVNotificationManager(context)
notificationManager.sendNotification(
    title = "Feature Update",
    message = "Something happened",
    importance = NotificationImportance.HIGH
)
```

### 3. Integrate with Themes
```kotlin
import com.calcvault.emotional.ThemeEngine

val themeEngine = ThemeEngine(context)
themeEngine.applyTheme(activity, ThemeType.COUPLE)
```

### 4. Integrate with Multi-Device Sync
```kotlin
import com.calcvault.sync.MultiDeviceSyncEngine

val syncEngine = MultiDeviceSyncEngine()
syncEngine.syncData(deviceId, mapOf("key" to "value"))

// Receive synced data
val data = syncEngine.getData(deviceId, "key")
```

---

## Understanding Initialization Order

When the app starts:
```
AppInitializer.initializeApp()
  ↓
1. Crypto (E2EKeyManager)
  ↓
2. Storage (Database, Cache, USB)
  ↓
3. Sync (MultiDeviceSync, CompanionBridge)
  ↓
4. Notifications (CVNotificationManager)
  ↓
5. Features (Chat, Call, Filters, etc. - in parallel)
  ↓
6. UI (Themes, Appearance)
  ↓
HealthCheckManager.runHealthChecks()
  ↓
App ready! ✅
```

Each phase waits for previous phase to complete (30s timeout).

---

## Monitoring & Debugging

### Check System Health
```kotlin
val healthManager = HealthCheckManager(context)
val status = healthManager.runHealthChecks()
healthManager.printHealthReport(status)
```

Logs output like:
```
✅ Crypto: E2E encryption active
✅ Storage: Storage ready (USB + Database)
✅ Sync: Multi-device sync ready
✅ Notifications: Dual notification system ready
✅ Chat: Chat system ready
✅ Calls: Call engine (socket audio) ready
✅ Themes: Theme engine ready (4+ themes)
✅ Database: Database accessible

Summary: 8/8 systems healthy ✅
```

### View Logs
```bash
adb logcat | grep CalcVault
```

---

## GitHub Actions Status

Every PR automatically shows:
- ✅ Lint results (ktlint)
- ✅ Build status (compilation)
- ✅ Test results (unit tests)
- ✅ Code quality (detekt)

Check PR status:
1. Go to Pull Request
2. Scroll to "Checks" section
3. See status of each job
4. Click details for full output

---

## Common Issues & Solutions

### "ktlint check failed"
**Solution**: Auto-format your code
```bash
./gradlew ktlintFormat
```

### "Build failed"
**Solution**: Clean and rebuild
```bash
./gradlew clean assembleDebug
```

### "Tests failed"
**Solution**: Run tests locally
```bash
./gradlew testDebugUnitTest --info
```
Then fix the failing tests.

### "Gradle wrapper not found"
**Solution**: Make it executable
```bash
chmod +x gradlew
```

### "Android SDK not found"
**Solution**: Install Android SDK
```bash
echo "export ANDROID_HOME=$HOME/android-sdk" >> ~/.bashrc
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

---

## Test Examples

### Running Specific Tests
```bash
# Run only crypto tests
./gradlew testDebugUnitTest --tests "*.E2EKeyManagerTest"

# Run only one test
./gradlew testDebugUnitTest --tests "E2EKeyManagerTest.testKeyGenerationSuccessful"

# Verbose output
./gradlew testDebugUnitTest --info
```

### Writing New Tests
Use the existing tests as templates:
- `E2EKeyManagerTest.kt` - Crypto tests
- `MultiDeviceSyncEngineTest.kt` - Sync tests
- `ChatMessageTest.kt` - Chat tests
- `TestUtils.kt` - Helper utilities

---

## Release Checklist

Before releasing a new version:

- [ ] All tests pass locally
  ```bash
  ./gradlew testAll
  ```
- [ ] Code formatted
  ```bash
  ./gradlew ktlintFormat
  ```
- [ ] Build succeeds
  ```bash
  ./gradlew assembleRelease
  ```
- [ ] Manual testing of critical features
- [ ] Update version in build.gradle
  ```gradle
  versionCode 2
  versionName "1.1"
  ```
- [ ] Push to main branch
  - GitHub Actions runs full pipeline
  - All checks must pass

---

## File Reference

| File | Purpose |
|------|---------|
| `app/build.gradle` | Build config, dependencies, tasks |
| `.ktlint.yml` | Code style rules |
| `.github/workflows/build-and-test.yml` | CI/CD pipeline |
| `AppInitializer.kt` | Component initialization |
| `HealthCheckManager.kt` | System health verification |
| `E2EKeyManagerTest.kt` | Crypto unit tests |
| `MultiDeviceSyncEngineTest.kt` | Sync unit tests |
| `ChatMessageTest.kt` | Chat unit tests |
| `TestUtils.kt` | Test helpers and fixtures |

---

## Quick Commands Cheat Sheet

```bash
# Local development
./gradlew clean                    # Clean build
./gradlew ktlintFormat             # Format code
./gradlew testAll                  # Run tests
./gradlew assembleDebug            # Build APK
adb install app.apk                # Install

# Code quality
./gradlew ktlintCheck              # Check style
./gradlew lint                     # Full lint

# CI/CD pipeline (GitHub)
git push origin feature/branch      # Trigger pipeline
# (Wait for checks to complete)

# Debugging
adb logcat | grep CalcVault        # View logs
./gradlew testDebugUnitTest --info # Verbose tests
```

---

## Resources

- 📖 **INTEGRATION_PIPELINE_GUIDE.md** - Detailed integration documentation
- 📊 **PIPELINE_IMPLEMENTATION_STATUS.md** - Project status report
- 🧪 **Test Files** - E2EKeyManagerTest.kt, MultiDeviceSyncEngineTest.kt, ChatMessageTest.kt
- ⚙️ **AppInitializer.kt** - Startup orchestration
- 🏥 **HealthCheckManager.kt** - Health verification

---

**Status**: ✅ Ready for feature integration  
**Last Updated**: May 31, 2026
