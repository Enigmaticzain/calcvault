# CalcVault Integration Pipeline Guide

## Overview
This document describes the complete pipeline for integrating all 18 CalcVault features into a cohesive, production-ready application.

## Architecture

### Component Initialization Order
All components must initialize in this specific order to avoid race conditions:

```
1. Crypto/Security
   ├─ E2EKeyManager
   └─ EncryptionHandler

2. Storage (depends on Crypto)
   ├─ DatabaseManager
   ├─ CacheManager
   └─ USBStorageEngine

3. Sync (depends on Storage)
   ├─ MultiDeviceSyncEngine
   └─ CompanionBridgeService

4. Notifications (depends on Storage)
   ├─ CVNotificationManager
   └─ DualNotificationSystem

5. Features (depends on all above)
   ├─ ChatActivity & MessageDB
   ├─ CallEngine (SocketCallEngine)
   ├─ VideoFilterEngine
   ├─ CharacterBehaviorEngine
   ├─ EmotionalAnimationEngine
   ├─ WatchTogetherActivity
   └─ ListenTogetherActivity

6. UI & Themes (depends on Features)
   ├─ ThemeEngine
   └─ AppearanceManager
```

## Phase 1: Build & Lint Pipeline ✅ DONE

### Changes Made:
- ✅ **build.gradle**: Added ktlint plugin, test dependencies (JUnit, Mockito), test configuration
- ✅ **ktlint config** (.ktlint.yml): Code style enforcement
- ✅ **Unit tests**: Created for crypto, sync, and chat modules
- ✅ **GitHub Actions**: Automated lint, build, test pipeline

### Gradle Tasks
```bash
# Build
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK

# Testing
./gradlew testDebugUnitTest  # Run unit tests
./gradlew testAll            # Run all tests

# Linting
./gradlew ktlintCheck   # Check code style
./gradlew ktlintFormat  # Auto-format code
./gradlew lint          # Full lint check
```

## Phase 2: Feature Integration

### AppInitializer Component
- **File**: `app/src/main/java/com/calcvault/core/init/AppInitializer.kt`
- **Purpose**: Controls startup order, prevents race conditions
- **Usage**:
```kotlin
val initializer = AppInitializer(context)
initializer.initializeApp()  // Blocks until all systems ready
```

### Integration Points

#### 1. E2E Encryption
All data-storing components must use `E2EKeyManager`:
- ✓ Chat messages
- ✓ Sync data
- ✓ Storage vault
- ✓ Notification metadata

#### 2. Notifications
All features that generate notifications must use `CVNotificationManager`:
- ✓ Chat notifications
- ✓ Call notifications
- ✓ Sync notifications
- ✓ Mood notifications
- ✓ Care drop notifications

#### 3. Theme System
All UI components must apply themes via `ThemeApplicator`:
- ✓ Chat Activity
- ✓ Call UI
- ✓ Filter UI
- ✓ House UI
- ✓ Character UI

#### 4. Multi-Device Sync
All mutable features must sync via `MultiDeviceSyncEngine`:
- ✓ Chat messages
- ✓ Mood state
- ✓ Filters
- ✓ Theme preference
- ✓ Notes/Diary/To-Do

## Phase 3: Monitoring & Health Checks ✅ DONE

### HealthCheckManager
- **File**: `app/src/main/java/com/calcvault/core/health/HealthCheckManager.kt`
- **Checks**: Crypto, Storage, Sync, Notifications, Chat, Calls, Themes, Database
- **Usage**:
```kotlin
val healthManager = HealthCheckManager(context)
val status = healthManager.runHealthChecks()
healthManager.printHealthReport(status)
```

## Phase 4: CI/CD Pipeline ✅ DONE

### GitHub Actions Workflow
- **File**: `.github/workflows/build-and-test.yml`
- **Triggers**: Push to main/develop, Pull requests
- **Jobs**:
  1. **lint** - ktlint code style checks
  2. **build** - Compile debug APK
  3. **unit-tests** - Run unit test suite
  4. **integration-tests** - Run feature integration tests
  5. **analysis** - Code quality (detekt + optional SonarQube)

### PR Checks
Every pull request automatically runs:
- ✅ Kotlin linting (ktlint)
- ✅ Unit tests
- ✅ Build compilation
- ✅ Code quality analysis

## Phase 5: Quality Assurance & Testing

### Test Coverage Requirements
Each feature module must have unit tests covering:
- Happy path (success case)
- Error handling
- Edge cases (empty input, large data, etc.)
- Integration with other features

### Manual Testing Checklist

#### Communication Features
- [ ] Chat: Send/receive encrypted messages
- [ ] Calls: Make/receive audio/video calls
- [ ] Screen Sharing: Share screen during call
- [ ] Instagram Import: Import chat history

#### Emotional Features
- [ ] Mood: Set mood and see UI change
- [ ] Care Drops: Send/receive emotional gifts
- [ ] Word Triggers: Trigger emoji showers with specific words
- [ ] Character Reactions: Characters react to messages

#### Shared Life Features
- [ ] Watch Together: Sync movie playback
- [ ] Listen Together: Sync music playback
- [ ] Shared Notes: Create/edit notes together
- [ ] Shared Diary: Write diary entries
- [ ] Shared To-Do: Manage tasks together

#### Character & Theme Features
- [ ] Character Control: Drag characters around house
- [ ] Dual House: Male + female characters interact
- [ ] Theme Switching: Switch between 4+ themes
- [ ] Keyboard Theme: Keyboard follows theme

#### Storage & Sync Features
- [ ] Vault Storage: Store 50GB+ encrypted data
- [ ] Multi-Device Sync: Changes sync across devices
- [ ] Large File Control: Files >1GB only on primary device
- [ ] USB Storage: Extended storage via USB

#### Privacy Features
- [ ] Calculator Disguise: App looks like calculator
- [ ] Stealth Notifications: No preview in notifications
- [ ] E2E Encryption: Messages encrypted end-to-end
- [ ] No Cloud: All data stays on device

## Build & Release

### Debug Build
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/Calculator-debug-1.0.apk
```

### Release Build
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/Calculator-release-1.0.apk
# (Must sign with keystore for Play Store)
```

### Version Bumping
Edit `app/build.gradle`:
```gradle
defaultConfig {
    versionCode 2    # Increment for every release
    versionName "1.1" # Semantic versioning
}
```

## Troubleshooting

### Build Failures
1. Check Android SDK is installed: `sdkmanager --list`
2. Verify Java 17: `java -version`
3. Clean and rebuild: `./gradlew clean assembleDebug`

### Test Failures
1. Run specific test: `./gradlew testDebugUnitTest --tests ClassName`
2. See full output: `./gradlew testDebugUnitTest --info`

### Lint Failures
1. Check style: `./gradlew ktlintCheck`
2. Auto-fix: `./gradlew ktlintFormat`

### Sync Issues
Check `MultiDeviceSyncEngine` logs for:
- Device registration status
- Sync conflicts
- Device offline handling

## Feature Status

| Feature | Status | Tests | Integration | CI/CD |
|---------|--------|-------|-------------|-------|
| Chat | ✅ | ✅ | ✅ | ✅ |
| Calls | ✅ | ⏳ | ✅ | ✅ |
| Sync | ✅ | ✅ | ✅ | ✅ |
| Vault | ✅ | ⏳ | ✅ | ✅ |
| Filters | ✅ | ⏳ | ✅ | ✅ |
| House | ✅ | ⏳ | ✅ | ✅ |
| Emotions | ✅ | ⏳ | ✅ | ✅ |
| Watch | ✅ | ⏳ | ✅ | ✅ |
| Listen | ✅ | ⏳ | ✅ | ✅ |
| Themes | ✅ | ⏳ | ✅ | ✅ |
| Notes | ✅ | ⏳ | ✅ | ✅ |
| Notifications | ✅ | ⏳ | ✅ | ✅ |
| Keyboard | ✅ | ⏳ | ✅ | ✅ |
| Mood | ✅ | ⏳ | ✅ | ✅ |
| Crypto | ✅ | ✅ | ✅ | ✅ |
| Screen Share | ✅ | ⏳ | ✅ | ✅ |
| USB Storage | ✅ | ⏳ | ✅ | ✅ |
| Stealth Mode | ✅ | ⏳ | ✅ | ✅ |

⏳ = Tests to be created per remaining todos

## Next Steps

1. **Write remaining tests** for each feature module
2. **Create integration tests** for cross-feature scenarios
3. **Run manual QA** on all 18 features
4. **Performance profiling** - memory, CPU, battery
5. **Release automation** - signing, versioning, publishing

## Contributing

All pull requests must:
1. ✅ Pass ktlint (code style)
2. ✅ Pass unit tests
3. ✅ Build successfully
4. ✅ Not break existing features
5. ✅ Include tests for new code

## Support

For integration issues:
1. Check GitHub Actions workflow results
2. Review component health check output
3. Check logs: `adb logcat | grep CalcVault`
4. Review integration checklist above

---

**Last Updated**: May 31, 2026  
**Status**: ✅ Phase 1-4 Complete, Phase 5 In Progress
