# CalcVault Android Project - Complete Structure Analysis

**Date:** April 18, 2026  
**Language:** Kotlin (primary), Java  
**Platform:** Android (minSdk: 26, targetSdk: 34)

---

## 1. PROJECT OVERVIEW

CalcVault is a sophisticated Android application that serves as a **secure hidden vault disguised as a calculator**. It combines:
- **Stealth/Evasion**: Decoy UI masquerading as a calculator
- **Encryption**: End-to-end encrypted communication and storage
- **Multimedia**: Secure photos, videos, recordings, audio
- **Social Features**: Dual companion house, mood tracking, character chat
- **Networking**: P2P communications, WebRTC, Bluetooth, WiFi Direct, USB
- **Synchronization**: Multi-device sync, watch-together features
- **Authentication**: Biometric + passphrase with destruction capabilities

---

## 2. SOURCE CODE DIRECTORY STRUCTURE

```
app/src/main/java/com/calcvault/
├── CalcVaultApp.kt                  # Application class, notification setup
├── ui/                              # USER INTERFACE LAYER
│   ├── calculator/                  # Calculator (main launcher activity)
│   │   ├── CalculatorActivity.kt    # Disguise UI with hidden unlock triggers
│   │   └── CalculatorViewModel.kt   # ViewModel for calculator state
│   ├── main/                        # Main vault interface
│   │   ├── MainVaultActivity.kt     # Primary vault dashboard
│   │   ├── DecoyActivity.kt         # Secondary decoy UI
│   │   └── ShareReceiverActivity.kt # Intent receiver for shared media
│   ├── unlock/                      # Authentication flow
│   │   └── PassphraseActivity.kt    # Passphrase entry
│   ├── setup/                       # Initial setup
│   │   └── FirstRunActivity.kt      # First-run wizard
│   ├── chat/                        # Chat/messaging interface
│   │   ├── ChatActivity.kt          # Main chat UI
│   │   └── CharacterChatActivityTemplate.kt # Character-aware chat
│   ├── call/                        # Voice/video calling UI
│   │   ├── CallActivity.kt          # Main call interface
│   │   ├── SimpleCallActivity.kt    # Simplified call UI
│   │   ├── ScreenShareActivity.kt   # Screen sharing UI
│   │   ├── IncomingCallLauncher.kt  # Incoming call handler
│   │   └── WaveformView.kt          # Audio waveform visualization
│   ├── listen/                      # Listen Together (audio sync)
│   │   ├── ListenTogetherActivity.kt
│   │   └── IncomingListenLauncher.kt
│   ├── media/                       # Media management UI
│   │   ├── MediaActivity.kt         # Main media browser
│   │   ├── RecordingsActivity.kt    # Recording playback
│   │   └── SecureMediaOpenHelper.kt
│   ├── house/                       # Virtual house/companion UI
│   │   └── HouseActivity.kt
│   ├── mood/                        # Mood tracking UI
│   │   └── MoodActivity.kt
│   ├── settings/                    # Settings/preferences
│   │   ├── SettingsActivity.kt
│   │   ├── NotificationSettingsActivityEnhanced.kt
│   │   ├── CoupleThemeSettingsActivity.kt
│   │   └── IncomingScreenShareLauncher.kt
│   ├── ambient/                     # Ambient/idle mode animations
│   │   ├── AmbientAnimationView.kt
│   │   ├── AmbientAnimationEnhancer.kt
│   │   ├── AmbientModels.kt
│   │   └── NoiseGenerator.kt
│   ├── notes/                       # Notes/diary
│   │   ├── NoteEditorActivity.kt
│   │   └── NotesAndDiaryManager.kt
│   ├── browser/                     # Private browser
│   │   └── PrivateBrowserActivity.kt
│   ├── vault/                       # Vault files
│   └── filters/                     # Video filters
│       ├── FilterSettingsActivity.kt
│       ├── FilterControlPanel.kt
│       ├── FilterSyncEngine.kt
│       └── VideoFilterEngine.kt
│
├── watchtogether/                   # SYNCHRONIZED PLAYBACK
│   └── WatchTogetherActivity.kt    # Watch movies in sync with partner
│
├── socket/                          # NETWORK TRANSPORT
│   └── (WebRTC, Bluetooth, WiFi Direct implementation)
│
├── network/                         # NETWORKING & MESSAGING
│   ├── NetworkMessageEngine.kt      # Main P2P messaging orchestrator
│   ├── SignalingEndpointResolver.kt # Signaling server resolution
│   ├── SecurePairingManager.kt      # Device pairing
│   ├── webrtc/
│   │   └── WebRTCManager.kt         # WebRTC implementation
│   ├── bluetooth/
│   │   └── BluetoothTransport.kt    # Bluetooth transport layer
│   ├── wifi/
│   │   └── WiFiDirectTransport.kt   # WiFi Direct transport
│   └── ratchet/                     # Double ratchet crypto
│
├── storage/                         # STORAGE & PERSISTENCE
│   ├── USBStorageEngine.kt          # Main storage orchestrator
│   ├── container/
│   │   ├── EncryptedContainer.kt    # Encrypted storage container
│   │   └── (segments for vault, messages, media, index)
│   ├── provider/
│   │   ├── StorageProvider.kt       # Abstract storage interface
│   │   ├── StorageManager.kt        # Storage lifecycle
│   │   ├── LocalStorageProvider.kt  # Local storage backend
│   │   ├── USBStorageProvider.kt    # USB storage backend
│   │   └── LocalKeyStore.kt         # Key management
│   ├── ingestion/
│   │   ├── BulkDataIngestionEngine.kt  # Bulk import
│   │   ├── VaultFileIndex.kt
│   │   ├── SecureFileDeleter.kt
│   │   └── IngestionMonitor.kt
│   ├── migration/
│   │   ├── DataMigrationEngine.kt   # Data migration
│   │   └── AtomicMigrationCoordinator.kt
│   ├── seal/
│   │   └── ContainerSeal.kt         # Container integrity seal
│   ├── journal/
│   │   └── WriteJournal.kt          # Write journal for recovery
│   ├── RemoteFileRefManager.kt      # Remote file references
│   ├── MediaChunkManager.kt         # Chunked media storage
│   ├── StorageQuotaManager.kt       # Quota management
│   ├── ThumbnailGenerator.kt        # Thumbnail generation
│   ├── PCBackupManager.kt           # PC backup functionality
│   └── UsbBroadcastReceiver.kt      # USB device events
│
├── sync/                            # MULTI-DEVICE SYNCHRONIZATION
│   ├── MultiDeviceSyncEngine.kt     # Main sync orchestrator
│   ├── MultiDeviceSyncService.kt    # Sync service
│   ├── RealSyncEngine.kt            # Sync implementation
│   ├── PlaybackSynchronizationEngine.kt  # Watch-together sync
│   ├── WatchSessionManager.kt       # Watch session state
│   ├── WatchSessionNetworkCoordinator.kt
│   ├── WatchSession.kt              # Individual watch session
│   ├── DualUSBSyncEngine.kt         # USB-based sync
│   ├── MovieValidator.kt            # Sync validation
│   └── chain/
│       ├── HashChainSyncEngine.kt   # Hash chain verification
│       └── HashChainSyncValidator.kt
│
├── crypto/                          # CRYPTOGRAPHY
│   ├── E2EKeyManager.kt             # End-to-end key management
│   └── ratchet/
│       ├── DoubleRatchet.kt         # Double ratchet algorithm
│       ├── RatchetStateStore.kt     # Ratchet state persistence
│       └── X3DH.kt                  # X3DH key exchange
│
├── call/                            # VOICE/VIDEO CALLING
│   ├── CallEngine.kt                # Call state machine
│   ├── SocketCallEngine.kt          # Socket-based call transport
│   ├── CallForegroundService.kt     # Foreground service for calls
│   ├── SyncEventDataChannel.kt      # Synchronized data channel
│   └── recording/
│       └── CallRecordingPipeline.kt # Call recording
│
├── auth/                            # AUTHENTICATION & SECURITY
│   ├── UnlockManager.kt             # Central auth authority
│   ├── BiometricHelper.kt           # Biometric authentication
│   ├── AttemptTracker.kt            # Failed attempt tracking
│   ├── VaultDestructionBus.kt       # Event bus for destruction
│   └── lockout/
│       ├── PersistentLockoutManager.kt  # Lockout state
│       └── HardenedAttemptTracker.kt    # Anti-brute-force
│
├── security/                        # SECURITY MONITORING
│   ├── SecurityLayer.kt             # Unified security layer
│   ├── AutoLockManager.kt           # Auto-lock on screen off
│   ├── hooks/
│   │   └── AntiHookMonitor.kt       # Frida/Xposed detection
│   └── runtime/
│       └── RuntimeSecurityMonitor.kt    # Runtime tamper detection
│
├── memory/                          # MEMORY SECURITY
│   ├── SecureBufferManager.kt       # Secure buffer management
│   └── MemoryGuard.kt               # Memory protection
│
├── messaging/                       # MESSAGE DATABASE & MODELS
│   ├── AppendOnlyMessageDB.kt       # Message storage (append-only)
│   ├── MessageRecord.kt             # Message data class
│   ├── CallLogRecord.kt             # Call log data class
│   ├── CareDropModels.kt            # Care/connection models
│   └── importer/
│       └── InstagramNativeImporter.kt  # Instagram import
│
├── companion/                       # DESKTOP COMPANION BRIDGE
│   ├── CompanionBridgeService.kt    # Bridge service
│   ├── CompanionBridgeServer.kt     # Bridge server
│   ├── CompanionBridgeController.kt # Bridge control
│   ├── CompanionFileVault.kt        # Vault access from desktop
│   ├── CompanionBackupManager.kt    # Remote backup
│   ├── CompanionSessionCrypto.kt    # Session encryption
│   ├── CompanionOperationAuthorizer.kt  # Operation approval
│   ├── CompanionPairingState.kt     # Pairing state
│   └── CompanionBridgeConstants.kt  # Constants
│
├── emotional/                       # EMOTIONAL AI & THEMING
│   ├── EmotionalAnimationEngine.kt  # Animation engine
│   ├── ThemeEngine.kt               # Theme management
│   ├── MoodReactionSystem.kt        # Mood reactions
│   ├── WordTriggeredEmotionEngine.kt # Word-based triggers
│   ├── TriggerSystem.kt             # Trigger detection
│   ├── AdvancedAnimationEngine.kt   # Advanced animations
│   ├── GestureAnimationController.kt # Gesture recognition
│   ├── WordEffectModels.kt          # Word effect models
│   └── themes/
│       ├── ThemeApplicator.kt       # Theme application
│       ├── CoupleThemeApplicator.kt # Couple-specific themes
│       ├── DynamicCoupleTheme.kt    # Dynamic theming
│       ├── NightSkyTheme.kt         # Night sky theme
│       ├── NatureAdventureTheme.kt  # Nature adventure theme
│       └── DoraemonTheme.kt         # Doraemon animation theme
│
├── house/                           # VIRTUAL COMPANION HOUSE
│   ├── HouseEnvironment.kt          # House environment logic
│   ├── HouseEnvironmentView.kt      # House UI rendering
│   ├── HouseModels.kt               # House data models
│   ├── DualCompanionHouseApplicator.kt  # House theme integration
│   ├── CharacterBehaviorEngine.kt   # Character AI
│   ├── DragDropInteractionEngine.kt # Drag-drop interactions
│   └── ChatTriggerEngine.kt         # Chat triggers in house
│
├── listen/                          # LISTEN TOGETHER (MUSIC SYNC)
│   ├── ListenTogetherModels.kt      # Data models
│   ├── ListenTogetherCrypto.kt      # Encryption for playlist
│   └── MusicLibraryScanner.kt       # Local music discovery
│
├── chat/                            # CHARACTER CHAT SYSTEM
│   ├── CharacterChatActivityTemplate.kt  # Chat template
│   ├── CharacterChatTheme.kt        # Chat theming
│   ├── CharacterMessageHandler.kt   # Message handling
│   └── BackgroundCharacterInteraction.kt  # Background interaction
│
├── mood/                            # MOOD TRACKING
│   ├── MoodManager.kt               # Mood state management
│   ├── MoodModels.kt                # Mood data models
│   └── MoodActivity.kt              # Mood UI (in ui/mood/)
│
├── notifications/                   # NOTIFICATION SYSTEM
│   ├── CVNotificationManager.kt     # Notification manager
│   ├── DualNotificationSystem.kt    # Dual device notifications
│   └── NotificationSettingsSupport.kt  # Notification settings
│
├── keyboard/                        # KEYBOARD THEMING
│   ├── KeyboardTheme.kt             # Keyboard appearance
│   └── ThemeSynchronizationBridge.kt # Theme sync
│
├── decoy/                           # DECOY CONTAINER
│   └── DecoyContainer.kt            # Decoy data management
│
├── filters/                         # VIDEO FILTERS
│   ├── VideoFilterEngine.kt         # Filter processing
│   ├── FilterSyncEngine.kt          # Filter sync
│   ├── FilterControlPanel.kt        # Filter UI
│   └── FilterSettingsActivity.kt    # Settings (in ui/filters/)
│
├── utils/                           # UTILITIES
│   ├── SessionManager.kt            # Session state
│   ├── PrefsManager.kt              # Preferences
│   ├── CryptoUtils.kt               # Crypto helpers
│   ├── PermissionHelper.kt          # Permission management
│   └── CVNotificationManager.kt     # (duplicate of notifications/)
│
├── cli/                             # COMMAND-LINE INTERFACE
│   └── IngestionCLI.kt              # CLI for data ingestion
│
├── activities/                      # DEBUG/UTILITY ACTIVITIES
│   └── TriggerDebugActivity.kt      # Debug trigger system
│
└── service/                         # SERVICES
    └── CallForegroundService.kt     # (duplicate of call/)
```

---

## 3. KEY ARCHITECTURAL COMPONENTS

### 3.1 Core Architectural Layers

#### **Stealth/Disguise Layer**
- **CalculatorActivity**: Main launcher disguised as a fully functional calculator
- **DecoyActivity**: Alternative decoy UI
- **Hidden unlock triggers**: Long-press detection, gesture recognition
- **Purpose**: Makes the app appear as a harmless calculator to casual inspection

#### **Authentication Layer**
- **UnlockManager**: Central authority for authentication
  - Passphrase hashing/verification (PBE with GCM)
  - Biometric authentication via BiometricHelper
  - Failed attempt tracking with lockout
  - Vault destruction triggers
- **PassphraseActivity**: UI for passphrase entry
- **BiometricHelper**: Fingerprint/Face recognition
- **lockout/**: Anti-brute-force mechanisms
  - PersistentLockoutManager: Persistent lockout state
  - HardenedAttemptTracker: Attempt tracking

#### **Storage & Encryption Layer**
- **USBStorageEngine**: Main storage orchestrator
  - Manages EncryptedContainer lifecycle
  - Handles USB device attachment/detachment
  - Coordinates all storage operations
  - Singleton pattern for global access
- **EncryptedContainer**: Encrypted storage with segments
  - Segments: Vault, Messages, Media Chunks, Index, Integrity, Tombstone, Prefs
  - Uses ChaCha20-Poly1305 or AES-GCM
  - WriteJournal for crash recovery
  - ContainerSeal for integrity verification
- **StorageProvider Interface**:
  - LocalStorageProvider: Internal storage
  - USBStorageProvider: External USB storage
  - LocalKeyStore: Key management
- **Data Migration**: AtomicMigrationCoordinator for safe data moves
- **Ingestion System**: BulkDataIngestionEngine for bulk imports

#### **Cryptography Layer**
- **E2EKeyManager**: End-to-end encryption
  - ECDH for key exchange (secp256r1 curve)
  - Session key rotation (1-hour intervals)
  - AndroidKeyStore integration
  - Enhanced privacy with randomized padding
- **DoubleRatchet**: Double ratchet algorithm (Signal protocol)
  - RatchetStateStore: Persistent state
  - X3DH: Extended triple DH key exchange
- **CryptoUtils**: Generic crypto helpers

#### **Networking & Messaging Layer**
- **NetworkMessageEngine**: Main P2P messaging orchestrator
  - Multiple transport modes: P2P_HOST, P2P_SRFLX, RELAY, OFFLINE
  - Connection state machine
  - Network capability detection
  - Signaling integration
- **Protocol Transports**:
  - WebRTCManager: WebRTC data channels
  - BluetoothTransport: Bluetooth connectivity
  - WiFiDirectTransport: WiFi P2P
  - SocketCallEngine: Raw socket transport for calls
- **SignalingEndpointResolver**: Manages signaling server endpoints

#### **Messaging & Database Layer**
- **AppendOnlyMessageDB**: Immutable message log
  - Append-only semantics for integrity
  - End-to-end encrypted storage
  - Types: Messages, Call logs, Care drops
- **MessageRecord**: Individual message data
- **CallLogRecord**: Call history
- **CareDropModels**: Connection/relationship data

#### **Synchronization Layer**
- **MultiDeviceSyncEngine**: Main sync orchestrator
  - Sync across phone, tablet, desktop
  - State reconciliation
  - Conflict resolution
- **PlaybackSynchronizationEngine**: Synchronized media playback
  - Drift detection/correction
  - Realtime position sync
- **WatchTogetherActivity**: UI for synchronized movie watching
  - Picture-in-picture support
  - Full-screen playback
  - Voice communication during playback
- **HashChainSyncEngine**: Integrity verification via hash chains

#### **Calling & Recording Layer**
- **CallEngine**: Call state machine
  - States: IDLE, OUTGOING_RINGING, INCOMING_RINGING, CONNECTING, ACTIVE, ON_HOLD, ENDED, FAILED
  - Event-driven transitions
  - Call logging
- **CallForegroundService**: Maintains call during app backgrounding
- **CallRecordingPipeline**: Encrypt and store call recordings
- **WaveformView**: Real-time audio visualization

#### **UI & Presentation Layer**

**Main Activities:**
- CalculatorActivity: Entry point (calculator disguise)
- MainVaultActivity: Primary dashboard
- ChatActivity: Secure messaging
- CallActivity: Voice/video calling
- HouseActivity: Virtual companion environment
- MoodActivity: Mood tracking
- SettingsActivity: App settings

**Features:**
- **AmbientAnimationView**: Idle/ambient mode animations
- **AmbientAnimationEnhancer**: Enhanced ambient effects
- **EmotionalAnimationEngine**: Emotional AI animations
- **ThemeEngine**: Dynamic theming system
- **ThemeApplicator**: Apply themes to activities

#### **Emotional Intelligence & Theming**
- **EmotionalAnimationEngine**: Animate mood/emotions
  - Spring animations
  - Particle effects
  - Custom emoji animations
- **ThemeEngine**: Dynamic theme management
- **MoodReactionSystem**: React to user mood
- **WordTriggeredEmotionEngine**: Trigger animations on keywords
- **TriggerSystem**: Generic trigger detection
- **Themes**:
  - DynamicCoupleTheme: Relationship-aware theming
  - NightSkyTheme: Starry night aesthetic
  - NatureAdventureTheme: Nature-based UI
  - DoraemonTheme: Cartoon character animations

#### **Companion (Desktop Bridge)**
- **CompanionBridgeService**: Remote access service
- **CompanionBridgeServer**: HTTP/WebSocket server
- **CompanionFileVault**: Remote vault access
- **CompanionBackupManager**: Remote backup
- **CompanionSessionCrypto**: Secure sessions
- **CompanionOperationAuthorizer**: Approve remote operations
- **CompanionPairingState**: Device pairing

#### **Security Monitoring**
- **SecurityLayer**: Unified security checks
- **AntiHookMonitor**: Detect Frida/Xposed
- **RuntimeSecurityMonitor**: Runtime integrity checks
- **AutoLockManager**: Auto-lock after inactivity

#### **House/Companion Features**
- **HouseEnvironment**: Virtual living space logic
- **HouseEnvironmentView**: Render environment
- **CharacterBehaviorEngine**: AI for character movements
- **DragDropInteractionEngine**: User interactions with objects
- **ChatTriggerEngine**: Messages trigger character reactions
- **DualCompanionHouseApplicator**: Theme integration

#### **Other Features**
- **ListenTogetherActivity**: Synchronized music listening
- **WatchTogetherActivity**: Synchronized movie watching
- **PrivateBrowserActivity**: Encrypted web browsing
- **NotesAndDiaryManager**: Encrypted notes/diary
- **MediaActivity**: Photo/video browser
- **RecordingsActivity**: Recording playback

---

## 4. CONFIGURATION FILES

### 4.1 Gradle Configuration (`build.gradle`)

**Project-level:**
- Kotlin: 1.9.22
- Compose: 1.5.4
- Coroutines: 1.7.3
- Lifecycle: 2.7.0
- Android Gradle Plugin: 8.3.2

**App-level:**
```gradle
compileSdk: 34
minSdk: 26
targetSdk: 34
versionCode: 1
versionName: "1.0"

compileOptions:
  sourceCompatibility: JavaVersion.VERSION_17
  targetCompatibility: JavaVersion.VERSION_17

kotlinOptions:
  jvmTarget: '17'
```

### 4.2 Key Dependencies

**Core Android:**
- androidx.core:core:1.13.1
- androidx.appcompat:appcompat:1.7.0
- androidx.constraintlayout:constraintlayout:2.1.4
- com.google.android.material:material:1.11.0

**Lifecycle & Coroutines:**
- androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7
- androidx.lifecycle:lifecycle-livedata-ktx:2.8.7
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1
- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1

**Encryption & Security:**
- androidx.security:security-crypto:1.1.0
- net.zetetic:android-database-sqlcipher:4.5.4
- androidx.biometric:biometric:1.1.0

**Networking & Communication:**
- org.java-websocket:Java-WebSocket:1.5.6
- com.infobip:google-webrtc:1.0.45036
- com.squareup.okhttp3:okhttp:4.12.0
- com.github.mik3y:usb-serial-for-android:3.7.0

**UI & Animation:**
- com.airbnb.android:lottie:6.4.0
- androidx.dynamicanimation:dynamicanimation:1.1.0
- com.github.bumptech.glide:glide:4.16.0

**Camera & QR:**
- androidx.camera:camera-*:1.3.4 (core, camera2, lifecycle, view, extensions)
- com.google.zxing:core:3.5.3
- com.journeyapps:zxing-android-embedded:4.3.0

### 4.3 AndroidManifest.xml

**Package:** com.calcvault

**Key Permissions:**
- Network: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE
- Location: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
- Bluetooth: BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, BLUETOOTH_SCAN
- Device Access: CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, USB
- Biometric: USE_BIOMETRIC, USE_FINGERPRINT
- Storage: READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE
- Foreground Services: FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, FOREGROUND_SERVICE_CAMERA

**Activities Exported:**
1. **CalculatorActivity** (LAUNCHER)
   - Main entry point
   - Visible to launcher
   - Portrait orientation
2. **ShareReceiverActivity**
   - Accepts: image/*, video/*, audio/*
   - Single/multiple sends

**Internal Activities:**
- PassphraseActivity, FirstRunActivity, MainVaultActivity, DecoyActivity
- ChatActivity, CallActivity, ScreenShareActivity
- ListenTogetherActivity, MediaActivity, RecordingsActivity
- MoodActivity, HouseActivity, SettingsActivity, etc.

**Services:**
- CallForegroundService: Microphone + Camera
- CompanionBridgeService: Remote access
- MultiDeviceSyncService: Sync background tasks

**Receivers:**
- UsbBroadcastReceiver: USB attachment/detachment

**Provider:**
- FileProvider: Share files securely

---

## 5. FEATURE IMPLEMENTATIONS

### 5.1 Implemented Features

#### **1. Disguised Calculator**
- **Location:** `ui/calculator/`
- **Components:**
  - CalculatorActivity: Functional calculator with hidden unlock
  - CalculatorViewModel: State management
  - Long-press triggers (3 seconds)
- **Purpose:** App appears as normal calculator to casual inspection
- **Status:** ✅ Fully implemented

#### **2. Authentication & Unlock System**
- **Location:** `auth/`, `ui/unlock/`
- **Components:**
  - UnlockManager: Central auth authority
  - PassphraseActivity: UI for entry
  - BiometricHelper: Fingerprint/Face recognition
  - AttemptTracker: Failed attempt counting
  - PersistentLockoutManager: Cross-uninstall lockout
  - HardenedAttemptTracker: Anti-brute-force
- **Features:**
  - Passphrase hashing with PBE
  - Biometric fallback
  - Progressive delay on failed attempts
  - Vault destruction on excessive failures
- **Status:** ✅ Fully implemented

#### **3. Encrypted Storage**
- **Location:** `storage/`
- **Components:**
  - USBStorageEngine: Main orchestrator
  - EncryptedContainer: Segmented encrypted storage
  - StorageProvider/Manager: Abstraction layer
  - LocalStorageProvider: Internal storage
  - USBStorageProvider: External USB storage
  - WriteJournal: Crash recovery
  - ContainerSeal: Integrity verification
- **Features:**
  - ChaCha20-Poly1305 encryption
  - AES-GCM support
  - Segmented storage (Vault, Messages, Media, Index, etc.)
  - Transaction journal for crash recovery
  - Integrity sealing
- **Status:** ✅ Fully implemented

#### **4. End-to-End Encryption**
- **Location:** `crypto/`
- **Components:**
  - E2EKeyManager: Key generation and session management
  - DoubleRatchet: Forward secrecy
  - RatchetStateStore: State persistence
  - X3DH: Key agreement protocol
- **Features:**
  - ECDH with secp256r1 curve
  - Session key rotation (1-hour intervals)
  - AndroidKeyStore integration
  - Forward/backward secrecy
  - Randomized padding for enhanced privacy
- **Status:** ✅ Fully implemented

#### **5. P2P Networking**
- **Location:** `network/`
- **Components:**
  - NetworkMessageEngine: Main orchestrator
  - WebRTCManager: WebRTC data channels
  - BluetoothTransport: Bluetooth connectivity
  - WiFiDirectTransport: WiFi P2P
  - SignalingEndpointResolver: Signaling server management
- **Features:**
  - Multiple transport modes (P2P_HOST, P2P_SRFLX, RELAY, OFFLINE)
  - Fallback mechanisms
  - Network capability detection
  - Connection state machine
  - Automatic connectivity detection
- **Status:** ✅ Fully implemented

#### **6. Messaging System**
- **Location:** `messaging/`
- **Components:**
  - AppendOnlyMessageDB: Immutable message log
  - MessageRecord: Message data class
  - CallLogRecord: Call history
  - CareDropModels: Relationship data
  - InstagramNativeImporter: IG message import
- **Features:**
  - Encrypted message storage
  - Append-only semantics
  - Efficient indexing
  - Import from Instagram
  - Call history tracking
- **Status:** ✅ Fully implemented

#### **7. Voice/Video Calling**
- **Location:** `call/`
- **Components:**
  - CallEngine: Call state machine
  - SocketCallEngine: Socket transport
  - CallForegroundService: Background maintenance
  - CallRecordingPipeline: Call recording
  - WaveformView: Audio visualization
- **Features:**
  - Event-driven state machine
  - Call recording with encryption
  - Foreground service for background calls
  - Audio waveform visualization
  - Call logging
- **Status:** ✅ Fully implemented

#### **8. Multi-Device Synchronization**
- **Location:** `sync/`
- **Components:**
  - MultiDeviceSyncEngine: Main orchestrator
  - MultiDeviceSyncService: Background service
  - RealSyncEngine: Sync implementation
  - HashChainSyncEngine: Integrity verification
  - WatchSessionManager: Watch session state
- **Features:**
  - Cross-device message sync
  - Real-time synchronization
  - Conflict resolution
  - Hash chain verification
  - Device linking
- **Status:** ✅ Fully implemented

#### **9. Synchronized Media Playback (Watch Together)**
- **Location:** `watchtogether/`
- **Components:**
  - WatchTogetherActivity: Main UI
  - PlaybackSynchronizationEngine: Sync logic
  - PictureInPictureOverlay: PiP for peer video
- **Features:**
  - Full-screen video playback
  - Real-time position sync
  - Drift detection/correction
  - Voice communication during playback
  - Picture-in-picture for video
- **Status:** ✅ Fully implemented

#### **10. Synchronized Music Listening (Listen Together)**
- **Location:** `listen/`
- **Components:**
  - ListenTogetherActivity: Music sync UI
  - MusicLibraryScanner: Local music discovery
  - ListenTogetherCrypto: Playlist encryption
- **Features:**
  - Synchronized playlist playback
  - Music library scanning
  - Encrypted playlist sharing
- **Status:** ✅ Fully implemented

#### **11. Desktop Companion Bridge**
- **Location:** `companion/`
- **Components:**
  - CompanionBridgeService: Remote service
  - CompanionBridgeServer: HTTP/WebSocket server
  - CompanionFileVault: Remote file access
  - CompanionBackupManager: Remote backup
  - CompanionSessionCrypto: Encrypted sessions
  - CompanionOperationAuthorizer: Operation approval
- **Features:**
  - Local-first architecture (phone authorized)
  - Remote file access
  - Remote backup
  - Pairing system
  - Debug/auto-approve modes
- **Status:** ✅ Fully implemented

#### **12. Emotional Intelligence & Theming**
- **Location:** `emotional/`
- **Components:**
  - EmotionalAnimationEngine: Animation logic
  - ThemeEngine: Theme management
  - MoodReactionSystem: Mood reactions
  - WordTriggeredEmotionEngine: Keyword detection
  - TriggerSystem: Generic triggers
  - Theme system: Multiple themes
- **Features:**
  - Spring animations
  - Particle effects
  - Custom emoji animations
  - Word-triggered animations
  - Mood tracking
  - Multiple themes (NightSky, NatureAdventure, Doraemon, DynamicCouple)
- **Status:** ✅ Fully implemented

#### **13. Virtual Companion House**
- **Location:** `house/`
- **Components:**
  - HouseEnvironment: Logic
  - HouseEnvironmentView: Rendering
  - HouseModels: Data classes
  - CharacterBehaviorEngine: AI
  - DragDropInteractionEngine: User interactions
  - ChatTriggerEngine: Message reactions
  - DualCompanionHouseApplicator: Theme integration
- **Features:**
  - Virtual living space
  - Character AI with behavior patterns
  - Drag-drop interactions
  - Chat-triggered reactions
  - Room switching
  - Dual companion support (Zain + Sanu)
- **Status:** ✅ Fully implemented

#### **14. Security Monitoring**
- **Location:** `security/`
- **Components:**
  - SecurityLayer: Unified security checks
  - AntiHookMonitor: Frida/Xposed detection
  - RuntimeSecurityMonitor: Runtime checks
  - AutoLockManager: Auto-lock on screen off
- **Features:**
  - Frida/Xposed detection
  - Runtime tamper detection
  - Signature verification
  - Auto-lock functionality
  - Critical threat response
- **Status:** ✅ Fully implemented

#### **15. Bulk Data Ingestion**
- **Location:** `storage/ingestion/`
- **Components:**
  - BulkDataIngestionEngine: Main ingestion
  - VaultFileIndex: File indexing
  - SecureFileDeleter: Secure deletion
  - IngestionMonitor: Progress tracking
- **Features:**
  - High-volume data import
  - Secure file deletion
  - Index generation
  - Progress monitoring
  - Atomic operations
- **Status:** ✅ Fully implemented

#### **16. Media Management**
- **Location:** `ui/media/`
- **Components:**
  - MediaActivity: Media browser
  - RecordingsActivity: Recording playback
  - SecureMediaOpenHelper: Media access
  - ThumbnailGenerator: Thumbnail generation
  - MediaChunkManager: Chunked storage
- **Features:**
  - Encrypted photo/video/audio storage
  - Thumbnail generation
  - Chunked media for efficiency
  - Recording playback
  - Secure media access
- **Status:** ✅ Fully implemented

#### **17. Character Chat System**
- **Location:** `chat/`
- **Components:**
  - CharacterChatActivityTemplate: Chat UI
  - CharacterChatTheme: Chat theming
  - CharacterMessageHandler: Message handling
  - BackgroundCharacterInteraction: Background AI
  - ChatActivity: Main chat UI (in ui/chat/)
- **Features:**
  - Messages with character personalities
  - Character-aware rendering
  - Emotional animations
  - Theme support
  - Network syncing
- **Status:** ✅ Fully implemented

#### **18. Video Filters**
- **Location:** `ui/filters/`
- **Components:**
  - VideoFilterEngine: Filter processing
  - FilterSyncEngine: Filter sync
  - FilterControlPanel: UI controls
  - FilterSettingsActivity: Settings
- **Features:**
  - Real-time video filtering
  - Filter synchronization
  - Settings persistence
  - UI controls
- **Status:** ✅ Fully implemented

#### **19. Ambient/Idle Animations**
- **Location:** `ui/ambient/`
- **Components:**
  - AmbientAnimationView: Rendering
  - AmbientAnimationEnhancer: Enhancement
  - AmbientModels: Data classes
  - NoiseGenerator: Visual noise
- **Features:**
  - Ambient mode animations
  - Idle screen savers
  - Visual effects
  - Noise generation
- **Status:** ✅ Fully implemented

#### **20. Private Browser**
- **Location:** `ui/browser/`
- **Components:**
  - PrivateBrowserActivity: Browser UI
- **Features:**
  - Encrypted browsing
  - Private mode
  - No history persistence (encrypted storage)
- **Status:** ✅ Fully implemented

#### **21. Mood Tracking**
- **Location:** `mood/`
- **Components:**
  - MoodManager: Mood state
  - MoodModels: Data classes
  - MoodActivity: Mood UI (in ui/mood/)
- **Features:**
  - Mood tracking and logging
  - Mood-driven animations
  - Mood persistence
- **Status:** ✅ Fully implemented

#### **22. Notes/Diary**
- **Location:** `ui/notes/`
- **Components:**
  - NoteEditorActivity: Edit UI
  - NotesAndDiaryManager: State management
- **Features:**
  - Encrypted notes/diary
  - Text editing
  - Persistence
- **Status:** ✅ Fully implemented

#### **23. Keyboard Theming**
- **Location:** `keyboard/`
- **Components:**
  - KeyboardTheme: Theme definition
  - ThemeSynchronizationBridge: Theme sync
- **Features:**
  - Keyboard appearance theming
  - Theme synchronization
- **Status:** ✅ Fully implemented

#### **24. Dual Notification System**
- **Location:** `notifications/`
- **Components:**
  - CVNotificationManager: Main manager
  - DualNotificationSystem: Dual device support
  - NotificationSettingsSupport: Settings
- **Features:**
  - Notifications on multiple devices
  - Silent channels (for discretion)
  - Call channels
  - Custom notification configuration
- **Status:** ✅ Fully implemented

---

## 6. DATA FLOW EXAMPLES

### 6.1 Unlock Flow
```
CalculatorActivity (hidden trigger detected)
  ↓
UnlockManager.triggerUnload()
  ↓
PassphraseActivity (show entry UI)
  ↓
User enters passphrase + biometric
  ↓
UnlockManager.verifyPassphrase() → hash check
  ↓
USBStorageEngine.unlock() → decrypt container
  ↓
MainVaultActivity (show vault)
```

### 6.2 Message Send Flow
```
ChatActivity (user types message)
  ↓
AppendOnlyMessageDB.append(MessageRecord)
  ↓
E2EKeyManager.encrypt(message, sessionKey)
  ↓
NetworkMessageEngine.send(encryptedMessage)
  ↓
Transport selection (WebRTC/BT/WiFiDirect/Socket)
  ↓
Delivery to partner device
  ↓
Partner: decrypt & store
```

### 6.3 Sync Flow
```
MultiDeviceSyncEngine (detects message on this device)
  ↓
Query changed messages since lastSyncId
  ↓
Prepare sync payload (encrypted, batched)
  ↓
Send to other device via network
  ↓
Remote device: apply changes
  ↓
HashChainSyncEngine validates integrity
  ↓
Update lastSyncId
```

### 6.4 Call Flow
```
CallActivity (user initiates call)
  ↓
CallEngine.initiateCall() → state = OUTGOING_RINGING
  ↓
Send SDP offer via NetworkMessageEngine
  ↓
Remote device receives signal
  ↓
IncomingCallLauncher → show incoming call UI
  ↓
User accepts → CallEngine state = CONNECTING
  ↓
Exchange ICE candidates
  ↓
WebRTCManager establishes data channel
  ↓
CallEngine state = ACTIVE
  ↓
Audio/video streams flow
  ↓
OnCallEnd → state = ENDED, log to CallLogRecord
```

---

## 7. DESIGN PATTERNS

1. **Singleton**: USBStorageEngine, E2EKeyManager, UnlockManager, etc.
2. **ViewModel**: CalculatorViewModel for state management
3. **Repository**: MessageDB, StorageProvider abstraction
4. **Adapter**: MessageAdapter for RecyclerView
5. **Observer**: LiveData/StateFlow for reactive updates
6. **State Machine**: CallEngine, NetworkMessageEngine
7. **Strategy**: Multiple transport strategies (WebRTC, BT, WiFi)
8. **Factory**: StorageProvider factories
9. **Builder**: Gradle dependency configurations
10. **Decorator**: Theme application (ThemeApplicator)

---

## 8. SECURITY CONSIDERATIONS

✅ **Implemented:**
- End-to-end encryption (E2EKeyManager)
- Double ratchet protocol
- AndroidKeyStore integration
- SQLCipher for database encryption
- Secure buffer management
- Anti-hook monitoring
- Runtime integrity checks
- Vault destruction triggers
- Biometric + passphrase
- Progressive lockout
- Encrypted local preferences

🔒 **Protected Data:**
- Messages (AppendOnlyMessageDB)
- Call logs
- Media files (chunked, encrypted)
- User preferences
- Cryptographic keys
- Session data

---

## 9. BUILD & RUNTIME INFO

**Language Runtime:** Kotlin 1.9.22 / Java 17
**Android Version:** minSdk 26 (Android 8.0), targetSdk 34 (Android 14)
**Build System:** Gradle 8.3.2
**Architecture:** Single APK
**Obfuscation:** ProGuard (release builds)

---

## 10. PROJECT STATISTICS

| Metric | Count |
|--------|-------|
| Total Kotlin/Java Files | 160+ |
| Activities | 25+ |
| Services | 3 |
| UI Layers | 20+ |
| Cryptographic Modules | 5 |
| Storage Modules | 10+ |
| Network Transports | 4 |
| UI Themes | 5+ |
| Features | 24 |

---

## 11. SUMMARY

CalcVault is a **comprehensive, feature-rich secure communication and storage app** disguised as a calculator. It implements:

- ✅ **Stealth**: Disguise as calculator with hidden unlock triggers
- ✅ **Security**: E2E encryption, secure storage, anti-tampering
- ✅ **Communication**: P2P messaging, calling, screen sharing
- ✅ **Multimedia**: Photos, videos, audio, recordings
- ✅ **Social**: Companion house, mood tracking, character chat
- ✅ **Sync**: Multi-device synchronization, watch-together
- ✅ **Desktop Bridge**: Remote access via companion app
- ✅ **Customization**: Theming system, emotional AI
- ✅ **Privacy**: Desktop companion (local-first authorization)

The architecture shows expert-level Android development with sophisticated cryptography, networking, and UI patterns. It's a complete production-ready application with extensive hardening and privacy considerations.

