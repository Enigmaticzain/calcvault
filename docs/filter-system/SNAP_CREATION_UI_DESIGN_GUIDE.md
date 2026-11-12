# Snap Creation UI/UX Design Guide

## Overview
This guide outlines the proper UI/UX implementation for the Snap creation feature in CalcVault, based on Snapchat's proven design patterns and the reference screenshot provided.

---

## 1. Screen Architecture

### Main Components:

```
┌─────────────────────────────────┐
│  Top Bar (Header)               │  ← Profile, Search, Notifications
├─────────────────────────────────┤
│                                 │
│      Camera Preview             │  ← Live camera feed (main content)
│      (with AR overlays)         │
│                                 │
├─────────────────────────────────┤
│  Bottom Bar (Controls)          │  ← Capture, Gallery, Recipients
├─────────────────────────────────┤
│  Quick Action Sidebar (Right)   │  ← Flash, Filters, Music, Stickers
└─────────────────────────────────┘
```

---

## 2. Top Bar Elements

### Left Section:
- **Profile Avatar** (32x32dp, circular)
  - Opens user profile menu
  - Shows user's story indicator
  
- **Search Icon** (24x24dp)
  - Opens search/discovery

### Right Section:
- **Friend Requests Badge** (32x32dp, circular with red notification badge)
  - Shows number of pending friend requests
  - Badge positioned top-right with red background (#FF0000)
  
- **Settings Menu** (24x24dp, hamburger or three-dots)
  - Accessibility options
  - Camera permissions
  - Storage settings

---

## 3. Camera Preview Area

### Requirements:
- **Full screen (minus top/bottom bars)**
- **Aspect Ratio:** 9:16 (vertical orientation)
- **FPS:** 30-60fps minimum
- **Preview Type:** Live camera feed with zero lag

### Overlay Elements:
- Face detection bounds (subtle outline when face detected)
- Lens name/indicator (top-left corner, small text)
- Recording indicator (red dot + "REC" when recording)
- Timer indicator (for video duration)

---

## 4. Right-Side Quick Actions Sidebar

Vertical stack of circular buttons (48x48dp each):

| Icon | Function | Behavior |
|------|----------|----------|
| ⚡ | Flash Control | Tap: Toggle flash modes (Off/On/Auto). Visual indicator shows current state |
| 📷 | Camera Switch | Tap: Switch between front/rear camera |
| 🎵 | Add Music | Tap: Open music picker modal |
| ➕ | Add Element | Tap: Add stickers, text, GIFs (opens editor) |
| ⚙️ | Lens/Filter | Long-press: Show lens gallery. Single tap: Apply default lens |

**Visual Design:**
- White icons on dark semi-transparent background
- Bottom margin: 16dp from bottom controls
- Icons scale slightly on tap (haptic feedback)

---

## 5. Bottom Control Bar

Horizontal layout with 5 sections:

```
[↙️ Gallery] [⭕ Capture] [👤 Recipients] [💬 Messages] [▶️ Stories]
```

### Detailed Layout:

**Left Section (Gallery):**
- Location icon or gallery thumbnail (40x40dp)
- Tap: Open camera roll / saved snaps
- Shows last captured media thumbnail

**Center (Capture Button):**
- **Circle (88x88dp)** with white background
- Inner circle (64x64dp) with hollow design
- **Tap behavior:**
  - Photo: Single tap → capture frame
  - Video: Press & hold → start recording, release → stop
- **Visual feedback:**
  - Scale animation on press
  - Recording progress ring (red) around button
  - Haptic feedback on capture

**Center-Right (Recipients):**
- Profile avatars of recent recipients (24x24dp, stacked)
- Tap: Open recipient picker
- Shows 1-3 recent contacts

**Right Sections (Messages, Stories):**
- Message icon with unread badge
- Stories icon
- Tap: Switch to respective screens

---

## 6. Snap Capture Workflow

### Photo Capture:
```
1. User taps center circle
2. Current frame freezes
3. Haptic feedback (light click)
4. Optional: 0.5s preview
5. Auto-transition to editing screen
```

### Video Capture:
```
1. User presses & holds center circle
2. Recording starts immediately
3. Timer shows elapsed time (00:01, 00:02, etc.)
4. Recording ring animates around capture button
5. Max duration: 60 seconds (with visual warning at 50s)
6. User releases → recording stops
7. Auto-transition to editing screen
```

---

## 7. Camera Initialization Checklist

When snap screen opens:

- ✅ Initialize Camera2 API (Android) / AVFoundation (iOS)
- ✅ Load FaceTracker (MediaPipe)
- ✅ Initialize FilterEngine
- ✅ Request camera & microphone permissions
- ✅ Start preview stream (30-60fps)
- ✅ Load default lens/filter (if any)
- ✅ Initialize motion sensors (gyro, accelerometer)
- ✅ Start background audio capture (ready mode)
- ✅ Initialize GPU rendering pipeline

---

## 8. AR/Face Tracking Integration

### Real-Time Processing:
```
Camera Frame (30-60fps)
    ↓
FaceTracker.detectFaces(frame)
    ↓
FilterEngine.processCameraFrame(frame)
    ↓
Apply active lens/filter
    ↓
Render to preview
    ↓
Display on screen
```

### Face Detection Indicators:
- **When face detected:** Subtle glow or outline around face
- **When no face detected:** Optional prompt: "Show your face"
- **Multiple faces:** All detected faces get tracking

---

## 9. Lens/Filter System

### Filter Selection Sidebar (Right):
- Default lens (no effect)
- 6-8 popular lenses shown
- Swipe or scroll to reveal more
- **Long-press:** Preview lens on camera
- **Single-press (tap):** Apply lens to current capture

### Example Lenses:
- Dog ears
- Face morphing
- Makeup (beauty filter)
- Cartoon effect
- Claymation
- B&W vintage

---

## 10. Snap Editing Screen

After capture (photo or video):

### Layout:
```
┌─────────────────────────────────┐
│  Media Preview                  │
│  (Photo/Video thumbnail)        │
├─────────────────────────────────┤
│  [Text] [Sticker] [Draw]        │  ← Edit buttons (horizontal tabs)
│  [Emoji] [GIF]    [Music]       │
├─────────────────────────────────┤
│  Edit Area (Canvas)             │
│  (Show preview with overlays)   │
├─────────────────────────────────┤
│  [❌ Delete] [⬅️ Retake] [✓ Send]│  ← Action buttons
└─────────────────────────────────┘
```

### Editing Tools:
1. **Text** - Add text with font/color/size options
2. **Sticker** - Browse and place stickers
3. **Drawing** - Free-hand drawing (pen tool)
4. **Emoji** - Quick emoji placement
5. **GIF** - Browse and place animated GIFs
6. **Music** - Add background music (Spotify/local)

---

## 11. Recipient Selection

Modal/Sheet that shows:

```
┌──────────────────────────┐
│ [Search friends...]      │
├──────────────────────────┤
│ ⭐ My Story              │
├──────────────────────────┤
│ [👤 Friend1]   [Select]  │
│ [👤 Friend2]   [Select]  │
│ [👤 Friend3]   [Select]  │
│ [👤 Friend4]   [Select]  │
│ ...                      │
├──────────────────────────┤
│ [Cancel]        [Send]   │
└──────────────────────────┘
```

### Features:
- Search to filter friends
- Multi-select capability
- "Send to Story" option
- Group selection presets
- Recent recipients at top

---

## 12. Performance Considerations

### FPS Targets:
- **Camera Preview:** 60fps (60Hz refresh)
- **Filter Processing:** 30fps minimum (acceptable for AR)
- **Recording:** 30fps (1080p)

### Memory Management:
- Stream frames without storing all in memory
- Use circular buffers for frame data
- Garbage collect unused filter resources
- Limit face landmarks storage (cache only current frame)

### Battery Optimization:
- Reduce FPS when idle (30fps)
- Disable unnecessary sensors
- Close camera on app backgrounding
- Hardware acceleration for encoding

---

## 13. Error Handling

### Camera Permission Denied:
```
┌──────────────────────────────┐
│ Camera Access Needed         │
│                              │
│ Allow CalcVault to access    │
│ your camera to create snaps. │
│                              │
│ [Settings]      [Cancel]     │
└──────────────────────────────┘
```

### Camera Unavailable:
- Show fallback UI (gallery picker)
- Prompt to restart app
- Graceful degradation

### Encoding Failures:
- Retry mechanism (up to 3x)
- User notification if capture fails
- Save draft option

---

## 14. Data Model: Snap Object

```kotlin
data class SnapCreation(
    val snapId: String,                    // UUID
    val mediaType: MediaType,              // PHOTO, VIDEO
    val originalMediaPath: String,         // Stored media file
    
    // Metadata
    val timestamp: Long,                   // Creation time
    val duration: Long,                    // Video duration (ms)
    val lensId: String?,                   // Applied lens
    val lensIntensity: Float = 1.0f,      // 0.0 - 1.0
    
    // Editing layers
    val textElements: List<TextElement>,
    val stickerElements: List<StickerElement>,
    val drawingElements: List<DrawingElement>,
    val emojiElements: List<EmojiElement>,
    val gifElements: List<GifElement>,
    val musicData: MusicData?,
    
    // Recipients & Distribution
    val recipients: List<String>,          // User IDs
    val isStory: Boolean = false,         // Story vs Direct
    val expirationTime: Long = 24 * 3600, // Expiry in seconds
    
    // Metadata for tracking
    val filterApplied: Boolean = false,
    val isEncrypted: Boolean = false,
    val compression: CompressionLevel = CompressionLevel.MEDIUM
)

enum class MediaType {
    PHOTO, VIDEO
}

enum class CompressionLevel {
    LOW, MEDIUM, HIGH
}

data class TextElement(
    val content: String,
    val x: Float,                  // Position (0-1)
    val y: Float,
    val fontSize: Int,
    val color: Int,                // ARGB color
    val fontFamily: String = "Roboto",
    val rotation: Float = 0f
)

data class StickerElement(
    val stickerId: String,
    val x: Float,
    val y: Float,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f
)

data class DrawingElement(
    val points: List<Pair<Float, Float>>,
    val color: Int,
    val strokeWidth: Float,
    val opacity: Float = 1.0f
)

data class MusicData(
    val musicId: String,
    val title: String,
    val artist: String,
    val startTime: Long,           // ms
    val duration: Long             // ms
)
```

---

## 15. Implementation Roadmap

### Phase 1: Core Camera (Priority: HIGH)
- [ ] Initialize Camera2/AVFoundation
- [ ] Implement live preview (60fps)
- [ ] Create main snap screen UI
- [ ] Implement capture button (photo mode)

### Phase 2: Video & Recording (Priority: HIGH)
- [ ] Add video recording capability
- [ ] Implement recording timer
- [ ] Add progress ring animation
- [ ] Hardware video encoding

### Phase 3: AR/Face Tracking (Priority: MEDIUM)
- [ ] Integrate MediaPipe face detection
- [ ] Apply face tracking overlays
- [ ] Implement lens system
- [ ] Real-time filter rendering

### Phase 4: Editing (Priority: MEDIUM)
- [ ] Build editing screen
- [ ] Implement text tool
- [ ] Implement sticker system
- [ ] Implement drawing tool
- [ ] Music integration

### Phase 5: Recipient & Send (Priority: MEDIUM)
- [ ] Build recipient picker
- [ ] Implement encryption
- [ ] Build send pipeline
- [ ] Track delivery status

### Phase 6: Optimization (Priority: LOW)
- [ ] Performance profiling
- [ ] Memory optimization
- [ ] Battery optimization
- [ ] Gesture smoothing

---

## 16. UI Components to Build

### Kotlin/Compose (Android):
- `CameraPreviewComposable`
- `SnapCaptureButtonComposable`
- `QuickActionsBarComposable`
- `SnapEditingScreenComposable`
- `RecipientPickerComposable`

### TypeScript/React (Web):
- `<SnapCamera />`
- `<CaptureControl />`
- `<QuickActions />`
- `<SnapEditor />`
- `<RecipientSelector />`

---

## 17. Testing Checklist

### Functional Tests:
- [ ] Camera initializes on screen open
- [ ] Photo capture works (frame freezes)
- [ ] Video recording works (timer increments)
- [ ] Face detection activates with face
- [ ] Lens/filter applies correctly
- [ ] Editing tools function properly
- [ ] Recipients can be selected
- [ ] Snaps are encrypted before send

### Performance Tests:
- [ ] Camera preview maintains 60fps
- [ ] Filter processing at 30fps minimum
- [ ] Encoding completes in <5 seconds
- [ ] Memory usage <200MB during capture

### Edge Cases:
- [ ] No camera permission
- [ ] Camera disconnected mid-recording
- [ ] Insufficient storage
- [ ] Large video file (60s @ 1080p)

---

## 18. UI Mockup Reference

```
┌─────────────────────────────────────┐
│ 👤   🔍         +2  ☰               │ ← Top Bar
├─────────────────────────────────────┤
│                                     │
│                                     │
│       📷 Camera Preview             │
│       (Live 30-60fps)               │ ← Main Content
│                                     │
│                                     │
├─────────────────────────────────────┤
│ ↙️ Location   ⭕ CAPTURE   👥 👯   ← Bottom Bar
│ Gallery     (Press & Hold) Recipients
└─────────────────────────────────────┘

        Right Sidebar:
        ⚡ Flash
        📷 Camera
        🎵 Music
        ➕ Add
        ⚙️ Lens
```

---

## 19. Known Issues to Avoid

1. **Camera Lag:** Don't process on main thread
2. **Battery Drain:** Disable sensors when not in use
3. **Memory Leaks:** Clean up frame buffers properly
4. **Encoding Failures:** Implement retry logic
5. **Permission Denials:** Graceful fallback to gallery
6. **Out-of-Memory:** Stream instead of loading all frames
7. **Gyroscope Jitter:** Smooth sensor input with filters
8. **Lens Artifacts:** Validate lens effects before rendering

---

## 20. Success Criteria

✅ Camera opens within 1 second
✅ Preview runs at 60fps with <100ms latency
✅ Capture completes in <500ms
✅ Encoding finishes in <5 seconds
✅ 8+ popular lenses available
✅ Full editing toolkit (text, stickers, draw, music)
✅ Recipient selection with search
✅ Encryption before sending
✅ Battery usage <10% per hour continuous use
✅ Smooth animations and gestures

---

## References

- Android Camera2 API: https://developer.android.com/reference/android/hardware/camera2
- iOS AVFoundation: https://developer.apple.com/av-foundation/
- MediaPipe: https://github.com/google/mediapipe
- Snapchat Design Philosophy: Real-time, expressive, private

