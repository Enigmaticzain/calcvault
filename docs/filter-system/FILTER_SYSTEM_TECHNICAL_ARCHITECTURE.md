# Calcvault AR Filter & Effects System - Technical Architecture

**Version:** 1.0  
**Date:** June 2026  
**Status:** Design Phase  

---

## Executive Summary

This document defines a production-grade AR Filter & Effects System for Calcvault that delivers Snapchat-level functionality while maintaining Calcvault's commitment to privacy, security, and performance. The system provides real-time face tracking, 2D/3D effect rendering, video call integration, and an extensible filter marketplace.

---

## 1. SYSTEM ARCHITECTURE OVERVIEW

### 1.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────┐
│            USER INTERFACE LAYER                          │
│  - Camera UI  - Call UI  - Filter Carousel  - Settings   │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│            FILTER ORCHESTRATION LAYER                    │
│  - Filter Manager  - Effect Compositor  - Lifecycle    │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│            PROCESSING PIPELINE LAYER                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │Face Tracking │  │Effect Render │  │Beauty Process│   │
│  │  Pipeline    │  │  Pipeline    │  │  Pipeline    │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│            CORE ENGINE LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │Face Mesh     │  │Landmark      │  │Head Pose     │   │
│  │Detector      │  │Refinement    │  │Estimator     │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │Eye Tracking  │  │Mouth Detect  │  │Hand Tracking │   │
│  │              │  │              │  │(Future)      │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│            RENDERING LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │2D Overlay    │  │3D Object     │  │Particle      │   │
│  │Renderer      │  │Renderer      │  │Renderer      │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│            PLATFORM INTEGRATION LAYER                    │
│  - Camera API  - Video Call Integration  - Storage      │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────┐
│            GPU/HARDWARE ACCELERATION                     │
│  - OpenGL ES 3.0  - Vulkan (future)  - NNAPI           │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Core Components

#### Face Tracking Pipeline
- **Input:** Camera frame (YUV_420)
- **Output:** Facial landmarks, head pose, per-face metadata
- **Engine:** MediaPipe Face Mesh (478 landmarks)
- **Features:**
  - Multi-face detection (up to 4 faces)
  - Head pose estimation (pitch, yaw, roll)
  - Eye gaze tracking
  - Mouth state detection
  - Iris tracking

#### Effect Rendering Pipeline
- **Input:** Video frame + facial landmarks
- **Output:** Rendered frame with effects applied
- **Process:**
  1. Frame preprocessing (color space conversion)
  2. Effect application (based on filter type)
  3. Compositing (layering multiple effects)
  4. Post-processing (color grading, tone mapping)
  5. Frame presentation

#### Asset Management Pipeline
- **Input:** Filter definition + asset references
- **Output:** Loaded assets in GPU memory
- **Process:**
  1. Validate filter manifest
  2. Download/load assets from cache
  3. Decompress and parse assets
  4. Upload to GPU
  5. Setup renderers

---

## 2. FACE TRACKING ENGINE DETAILED DESIGN

### 2.1 Multi-Step Face Landmark Detection

```
Input Frame
    ↓
[Frame Preprocessing]
  - Resize to 192x192
  - Normalize color space
  - Convert to tensor
    ↓
[Face Detection]
  - BlazeFace detector
  - Returns face bounding boxes
  - ~15ms per frame
    ↓
[Face Mesh Prediction]
  - MediaPipe Face Mesh model
  - 478 3D face landmarks
  - ~10ms per frame
    ↓
[Landmark Refinement]
  - Temporal smoothing (Kalman filter)
  - Outlier detection
  - Per-landmark confidence scoring
    ↓
[Pose Estimation]
  - Transform matrix calculation
  - Head rotation (pitch, yaw, roll)
  - Face position (x, y, z)
    ↓
[Metadata Extraction]
  - Eye openness
  - Mouth openness
  - Eyebrow raise detection
  - Smile detection
    ↓
Output: FaceData {
  landmarks: Array<3D Point>,
  headPose: {pitch, yaw, roll, position},
  eyeMetrics: {openness, gaze},
  mouthMetrics: {openness, smile},
  confidence: Float,
  timestamp: Long
}
```

### 2.2 Landmark Data Structure

```kotlin
data class FaceData(
    val faceId: Int,
    val timestamp: Long,
    
    // 478 3D landmarks (normalized to face space)
    val landmarks: Array<Vector3>,
    
    // Face mesh triangles for rendering
    val triangles: Array<TriangleIndex>,
    
    // Head pose (transform matrix)
    val headTransform: Matrix4x4,
    val headRotation: Quaternion,
    val headPosition: Vector3,
    
    // Euler angles
    val pitch: Float,  // Up/down
    val yaw: Float,    // Left/right
    val roll: Float,   // Tilt
    
    // Eye metrics
    val leftEyeOpenness: Float,    // 0.0-1.0
    val rightEyeOpenness: Float,   // 0.0-1.0
    val leftEyeGaze: Vector3,      // 3D gaze direction
    val rightEyeGaze: Vector3,     // 3D gaze direction
    
    // Mouth metrics
    val mouthOpenness: Float,      // 0.0-1.0
    val smileIntensity: Float,     // 0.0-1.0
    
    // Iris tracking
    val leftIrisDistance: Float,   // Normalized
    val rightIrisDistance: Float,  // Normalized
    
    // Confidence scores
    val overallConfidence: Float,
    val landmarkConfidences: FloatArray,
    
    // Bounding box
    val boundingBox: BoundingBox,
    
    // Texture coordinates for face mesh
    val textureCoords: Array<Vector2>
)

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float
)
```

### 2.3 Temporal Smoothing Strategy

```kotlin
// Kalman Filter for landmark smoothing
class LandmarkSmoother(
    private val alpha: Float = 0.8f,  // Smoothing factor
    private val threshold: Float = 0.1f  // Movement threshold
) {
    private var previousLandmarks: Array<Vector3>? = null
    private var velocities: Array<Vector3> = Array(478) { Vector3.ZERO }
    
    fun smooth(currentLandmarks: Array<Vector3>): Array<Vector3> {
        return if (previousLandmarks == null) {
            previousLandmarks = currentLandmarks
            currentLandmarks
        } else {
            val smoothed = Array(478) { i ->
                val prev = previousLandmarks!![i]
                val current = currentLandmarks[i]
                
                // Detect outliers
                val distance = prev.distance(current)
                if (distance > threshold) {
                    // Limit movement to prevent jumping
                    val clamped = prev.lerp(current, 0.5f)
                    velocities[i] = clamped.minus(prev)
                    clamped
                } else {
                    // Apply exponential moving average
                    prev.lerp(current, alpha)
                }
            }
            
            previousLandmarks = smoothed
            smoothed
        }
    }
}
```

---

## 3. EFFECT RENDERING PIPELINE

### 3.1 Rendering Architecture

```
Input: Camera Frame + Face Data
    ↓
[Frame Buffer Creation]
  - Create FBO for off-screen rendering
  - Size: Device screen size
  - Format: RGBA8888
    ↓
[Background Rendering]
  - Render camera frame as base
    ↓
[Effect Layer 1: Base Layer]
  - Beauty filters
  - Skin smoothing
  - Face deformation (if applicable)
    ↓
[Effect Layer 2: Overlays]
  - 2D PNG overlays
  - Position based on landmarks
  - Blending mode: OVER
    ↓
[Effect Layer 3: 3D Objects]
  - Load 3D models (GLB/GLTF)
  - Position at landmarks (e.g., glasses on nose)
  - Render with proper depth testing
    ↓
[Effect Layer 4: Particles]
  - Particle effects on triggers
  - Alpha blending
    ↓
[Effect Layer 5: Post-Processing]
  - Color grading
  - Bloom effect
  - Tone mapping
    ↓
Output: Rendered Frame
```

### 3.2 Effect Types

#### 3.2.1 Beauty Effects

```kotlin
data class BeautyEffect(
    val type: BeautyType,
    val intensity: Float,  // 0.0-1.0
    
    // Skin smoothing parameters
    val smoothingRadius: Float = 8f,
    val smoothingStrength: Float = 0.8f,
    
    // Teeth whitening
    val teethWhitening: Float = 0f,
    
    // Eye enhancement
    val eyeBrightness: Float = 0f,
    val eyeEnlargement: Float = 0f,
    
    // Face brightening
    val brightness: Float = 0f,
    
    // Face slimming
    val slimmingStrength: Float = 0f,
    
    // Jaw enhancement
    val jawlineStrength: Float = 0f,
    
    // Acne reduction
    val acneReductionStrength: Float = 0f
)

enum class BeautyType {
    NATURAL,      // Subtle enhancement
    MEDIUM,       // Moderate enhancement
    STRONG,       // Heavy enhancement
    CUSTOM        // User-defined
}
```

#### 3.2.2 Accessory Effects

```kotlin
data class AccessoryEffect(
    val assetPath: String,           // Path to 3D model
    val attachmentPoint: LandmarkGroup,  // Where to attach
    val scale: Vector3,
    val offset: Vector3,
    val rotation: Quaternion,
    val animated: Boolean = false,
    val animationPath: String? = null
)

enum class LandmarkGroup {
    NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    FOREHEAD,
    LEFT_CHEEK,
    RIGHT_CHEEK,
    MOUTH,
    CHIN,
    FULL_HEAD,
    LEFT_HAND,
    RIGHT_HAND,
    BACKGROUND
}
```

#### 3.2.3 Transform Effects

```kotlin
data class TransformEffect(
    val type: TransformType,
    val strength: Float,  // 0.0-1.0
    val targetLandmarks: Array<Vector3>,  // Deformation targets
    val deformationMesh: DeformationMesh
)

enum class TransformType {
    BIG_EYES,
    TINY_FACE,
    GIANT_HEAD,
    FACE_STRETCH,
    FACE_SQUEEZE,
    CUSTOM_DEFORMATION
}
```

#### 3.2.4 Reaction Effects

```kotlin
data class ReactionEffect(
    val trigger: FacialTrigger,
    val effects: List<Effect>,
    val duration: Long = 500  // milliseconds
)

enum class FacialTrigger {
    SMILE,          // Smile detected
    BLINK,          // Both eyes blink
    WINK,           // One eye blinks
    MOUTH_OPEN,     // Mouth opens wide
    EYEBROW_RAISE,  // Eyebrows raised
    HEAD_NOD,       // Head nods
    HEAD_SHAKE,     // Head shakes
    KISS_MOUTH      // Mouth shape for kiss
}
```

### 3.3 Rendering Quality Levels

```kotlin
enum class RenderingQuality {
    LOW {
        override val targetFPS = 24
        override val renderResolution = 0.75f  // 75% of screen
        override val effectComplexity = 0.5f
        override val particleCount = 50
    },
    MEDIUM {
        override val targetFPS = 30
        override val renderResolution = 0.9f
        override val effectComplexity = 0.8f
        override val particleCount = 150
    },
    HIGH {
        override val targetFPS = 60
        override val renderResolution = 1.0f
        override val effectComplexity = 1.0f
        override val particleCount = 300
    };
    
    abstract val targetFPS: Int
    abstract val renderResolution: Float
    abstract val effectComplexity: Float
    abstract val particleCount: Int
}
```

---

## 4. PERFORMANCE OPTIMIZATION STRATEGY

### 4.1 Frame Time Budget

```
Target: 30ms per frame (30 FPS minimum)

┌─────────────────────────────────────────────┐
│ Frame Time Budget (30ms)                    │
├─────────────────────────────────────────────┤
│ Camera frame capture:     1-2ms             │
│ Face detection:           3-5ms             │
│ Face mesh prediction:     8-12ms            │
│ Landmark smoothing:       1-2ms             │
│ Effect selection:         0.5-1ms           │
│ Rendering:                5-8ms             │
│ GPU sync/swap:            2-3ms             │
├─────────────────────────────────────────────┤
│ Total:                    ~28-32ms           │
│ Buffer:                   2-4ms              │
└─────────────────────────────────────────────┘

For 60 FPS (16.67ms):
- Reduce effect complexity
- Use half-resolution intermediate buffers
- Optimize GPU sync points
```

### 4.2 CPU Optimization

```kotlin
// Threading strategy
class FilterProcessingThread(
    private val name: String = "FilterProcess"
) : Thread(name) {
    private val workQueue = ConcurrentLinkedQueue<FilterTask>()
    private val executor = Executors.newFixedThreadPool(2)
    
    // Run ML inference in background thread
    fun submitFaceDetection(frame: ByteBuffer): Future<FaceData> {
        return executor.submit {
            mediapipe.processFaceDetection(frame)
        }
    }
    
    // Handle rendering on GL thread
    fun submitRendering(faceData: FaceData, filter: Filter): Future<Bitmap> {
        return executor.submit {
            glThread.post {
                renderFilter(faceData, filter)
            }
        }
    }
}

// Avoid:
// - Allocating large objects in render loop
// - GC during frame rendering
// - Lock contention

// Do:
// - Use object pools for temporary allocations
// - Pre-allocate buffers
// - Use concurrent data structures
```

### 4.3 GPU Optimization

```glsl
// Vertex Shader Optimization
#version 300 es
precision highp float;

// Use specific precision where possible
layout(location = 0) in highp vec3 position;
layout(location = 1) in mediump vec2 texCoord;

uniform highp mat4 mvpMatrix;

out mediump vec2 v_texCoord;

void main() {
    gl_Position = mvpMatrix * vec4(position, 1.0);
    v_texCoord = texCoord;
}

// Fragment Shader Optimization
#version 300 es
precision mediump float;

in vec2 v_texCoord;
uniform sampler2D u_texture;
uniform float u_intensity;

out vec4 outColor;

void main() {
    vec4 color = texture(u_texture, v_texCoord);
    
    // Use mediump for intermediate calculations
    // Avoid expensive operations (pow, sqrt) when possible
    outColor = mix(color, color * u_intensity, 0.5);
}
```

### 4.4 Memory Optimization

```kotlin
// Object pooling
class FaceDataPool(private val poolSize: Int = 10) {
    private val pool = mutableListOf<FaceData>()
    
    init {
        repeat(poolSize) {
            pool.add(FaceData())
        }
    }
    
    fun acquire(): FaceData {
        return if (pool.isNotEmpty()) {
            pool.removeAt(0)
        } else {
            FaceData()
        }
    }
    
    fun release(data: FaceData) {
        data.reset()
        pool.add(data)
    }
}

// Buffer reuse
class FrameBufferCache {
    private val cache = mutableMapOf<String, FrameBuffer>()
    
    fun getOrCreate(key: String, width: Int, height: Int): FrameBuffer {
        return cache.getOrPut(key) {
            FrameBuffer(width, height)
        }
    }
}
```

### 4.5 Battery Optimization

```kotlin
class BatteryAwareFilter {
    private val batteryManager: BatteryManager = context.getSystemService()
    
    fun getOptimalQuality(): RenderingQuality {
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val temperature = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE)
        
        return when {
            temperature > 45 || batteryLevel < 20 -> RenderingQuality.LOW
            batteryLevel < 40 -> RenderingQuality.MEDIUM
            else -> RenderingQuality.HIGH
        }
    }
}
```

---

## 5. VIDEO CALL INTEGRATION

### 5.1 Real-Time Filter Pipeline in Calls

```
Outgoing Call Stream:

Camera Frame
    ↓
[Face Detection (local)]
  - Detect faces in camera frame
    ↓
[Apply Filters (local)]
  - Render effects to video frame
    ↓
[Encode (local)]
  - H.264/VP9 encoding
    ↓
[Encryption (Calcvault E2EE)]
  - End-to-end encryption
    ↓
Network Transmission
    ↓

Incoming Call Stream:

Network Reception
    ↓
[Decryption (Calcvault E2EE)]
  - End-to-end decryption
    ↓
[Decode]
  - H.264/VP9 decoding
    ↓
Display to User
  - Remote user sees filters applied
```

### 5.2 Filter Switching During Calls

```kotlin
class CallFilterManager(
    private val callSession: CallSession,
    private val filterEngine: FilterEngine
) {
    private var currentFilter: Filter? = null
    
    // Smooth filter transition
    fun switchFilter(newFilter: Filter) {
        // Fade out current filter
        animateFilterOpacity(currentFilter, 0f, duration = 200)
        
        // Load new filter in background
        thread(start = true) {
            val loaded = filterEngine.loadFilter(newFilter)
            
            // Switch to new filter
            runOnGLThread {
                currentFilter = loaded
                // Fade in new filter
                animateFilterOpacity(loaded, 1f, duration = 200)
            }
        }
    }
    
    // Disable filter instantly
    fun disableFilter() {
        currentFilter = null
        filterEngine.clearEffects()
    }
    
    // Apply to call stream
    fun processCallFrame(frame: VideoFrame): VideoFrame {
        return if (currentFilter != null) {
            filterEngine.applyFilter(frame, currentFilter!!)
        } else {
            frame
        }
    }
}
```

### 5.3 Video Call Quality Adaptation

```kotlin
class CallQualityAdaptor(
    private val callSession: CallSession
) {
    fun selectQuality(
        networkBandwidth: Float,
        deviceCapability: DeviceCapability,
        batteryLevel: Int
    ): RenderingQuality {
        return when {
            // Poor network conditions
            networkBandwidth < 2.0f -> RenderingQuality.LOW
            
            // Low-end device
            deviceCapability == DeviceCapability.LOW_END -> RenderingQuality.LOW
            deviceCapability == DeviceCapability.MID_RANGE -> RenderingQuality.MEDIUM
            
            // Battery critical
            batteryLevel < 15 -> RenderingQuality.LOW
            
            else -> RenderingQuality.HIGH
        }
    }
}
```

---

## 6. SECURITY ARCHITECTURE

### 6.1 Privacy-First Design

```
KEY PRINCIPLE: Filters operate locally whenever possible

Face Detection Data:
- Processed locally on device
- Not sent to servers
- Not stored persistently
- Only temporary buffers during processing
- Cleared after frame processing

Compliance:
- GDPR compliant
- CCPA compliant
- No biometric data collection
- Optional analytics only
- User consent-based
```

### 6.2 Secure Asset Validation

```kotlin
class AssetValidator {
    fun validateFilterPackage(filterPath: String): Boolean {
        // 1. Check file signature
        val signature = loadSignature("$filterPath/SIGNATURE")
        if (!verifySignature(signature)) return false
        
        // 2. Check manifest integrity
        val manifest = loadManifest("$filterPath/manifest.json")
        val expectedHash = manifest.fileHashes
        
        // 3. Validate all asset files
        for ((file, hash) in expectedHash) {
            val actualHash = calculateSHA256("$filterPath/$file")
            if (actualHash != hash) return false
        }
        
        // 4. Check file types (whitelist only PNG, GLB, GLTF, WebP)
        for (file in listFilesRecursive(filterPath)) {
            val ext = file.extension.lowercase()
            if (ext !in listOf("png", "webp", "glb", "gltf", "json")) {
                return false
            }
        }
        
        return true
    }
}
```

### 6.3 Memory Protection

```kotlin
// Sensitive data handling
class SecureBuffer(size: Int) {
    private val buffer = ByteArray(size)
    
    fun fillWithSensitiveData(data: ByteArray) {
        System.arraycopy(data, 0, buffer, 0, data.size)
    }
    
    fun clear() {
        // Overwrite with random data
        Random.nextBytes(buffer)
        // Clear multiple times
        repeat(3) {
            Random.nextBytes(buffer)
        }
    }
    
    fun secureRead(block: (ByteArray) -> Unit) {
        try {
            block(buffer)
        } finally {
            clear()
        }
    }
}
```

---

## 7. ASSET MANAGEMENT SYSTEM

### 7.1 Asset Storage Strategy

```
Device Storage:

/data/local/tmp/calcvault/filters/
├── cache/
│   ├── beauty_natural/
│   │   ├── manifest.json
│   │   ├── shader.glsl
│   │   └── config.json
│   └── dog_filter/
│       ├── manifest.json
│       ├── model.glb
│       └── animation.json
├── favorites/
│   └── (symlinks or references)
└── metadata.db
    (SQLite - filter metadata, usage stats, ratings)

Cloud Storage (for filter store):

https://filters.calcvault.io/
├── v1/
│   ├── filters/
│   │   ├── beauty/
│   │   ├── animals/
│   │   └── fun/
│   └── metadata.json
```

### 7.2 Asset Loading Pipeline

```kotlin
class AssetManager {
    private val cache = mutableMapOf<String, Asset>()
    private val loadingScope = CoroutineScope(
        Dispatchers.Default + Job() + ExceptionHandler { _, _ -> }
    )
    
    suspend fun loadFilterAssets(filterId: String): Filter {
        // 1. Check local cache
        val cached = cache[filterId]
        if (cached != null) return cached as Filter
        
        // 2. Download if not cached
        val filterPath = downloadFilterIfNeeded(filterId)
        
        // 3. Validate
        if (!validateFilter(filterPath)) {
            throw SecurityException("Filter validation failed")
        }
        
        // 4. Decompress
        val decompressed = decompressFilter(filterPath)
        
        // 5. Parse manifest
        val manifest = parseManifest("$decompressed/manifest.json")
        
        // 6. Load and prepare assets
        val assets = loadAssets(manifest)
        
        // 7. Compile shaders
        val shaders = compileShaders(manifest)
        
        // 8. Create filter object
        val filter = Filter(
            id = filterId,
            manifest = manifest,
            assets = assets,
            shaders = shaders
        )
        
        // 9. Cache in memory
        cache[filterId] = filter
        
        return filter
    }
    
    suspend fun preloadFilter(filterId: String) {
        loadingScope.launch {
            loadFilterAssets(filterId)
        }
    }
}
```

---

## 8. DATABASE SCHEMA

### 8.1 SQLite Schema

```sql
-- Filters Table
CREATE TABLE filters (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL,
    version INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Storage info
    local_path TEXT,
    download_url TEXT,
    file_size INTEGER,
    compressed_size INTEGER,
    checksum TEXT,
    
    -- Metadata
    rating REAL DEFAULT 0.0,
    download_count INTEGER DEFAULT 0,
    usage_count INTEGER DEFAULT 0,
    is_favorite INTEGER DEFAULT 0,
    is_downloaded INTEGER DEFAULT 0,
    is_premium INTEGER DEFAULT 0,
    
    -- Compatibility
    min_api_level INTEGER DEFAULT 21,
    min_ram_mb INTEGER DEFAULT 512,
    supported_devices TEXT,
    
    -- Manifest
    manifest_json TEXT,
    
    INDEX idx_category (category),
    INDEX idx_favorite (is_favorite),
    INDEX idx_downloaded (is_downloaded)
);

-- Filter Usage Statistics
CREATE TABLE filter_usage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    context TEXT,  -- 'camera', 'call', 'edit'
    duration_ms INTEGER,
    fps_avg REAL,
    
    FOREIGN KEY (filter_id) REFERENCES filters(id),
    INDEX idx_filter_id (filter_id),
    INDEX idx_used_at (used_at)
);

-- Filter Effects Customization
CREATE TABLE filter_customizations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    filter_id TEXT NOT NULL,
    customization_data TEXT,  -- JSON
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(user_id, filter_id),
    FOREIGN KEY (filter_id) REFERENCES filters(id)
);

-- Downloaded Assets Cache
CREATE TABLE downloaded_assets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    asset_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size INTEGER,
    checksum TEXT,
    downloaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    
    UNIQUE(filter_id, asset_name),
    FOREIGN KEY (filter_id) REFERENCES filters(id),
    INDEX idx_expires_at (expires_at)
);

-- Face Detection Landmarks (for caching/optimization)
CREATE TABLE landmark_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    timestamp LONG NOT NULL,
    face_data BLOB,  -- Serialized FaceData
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at)
);
```

---

## 9. API SCHEMA

### 9.1 Internal APIs

```kotlin
// Filter Engine API
interface FilterEngine {
    // Lifecycle
    suspend fun initialize()
    suspend fun shutdown()
    
    // Filter Management
    suspend fun loadFilter(filterId: String): Filter
    suspend fun unloadFilter(filterId: String)
    suspend fun switchFilter(filterId: String)
    suspend fun setFilterIntensity(intensity: Float)
    suspend fun applyCustomization(customization: FilterCustomization)
    
    // Processing
    suspend fun processCameraFrame(frame: VideoFrame): VideoFrame
    suspend fun processCallFrame(frame: VideoFrame, outgoing: Boolean): VideoFrame
    suspend fun applyBeautyEffect(frame: VideoFrame, effect: BeautyEffect): VideoFrame
    
    // Face Tracking
    fun getFaceData(): List<FaceData>
    fun getFaceCount(): Int
    fun isFaceDetected(): Boolean
    
    // Rendering Quality
    fun setRenderingQuality(quality: RenderingQuality)
    fun getRenderingQuality(): RenderingQuality
    fun setTargetFPS(fps: Int)
    
    // Statistics
    fun getPerformanceMetrics(): PerformanceMetrics
    fun getUsageStatistics(): UsageStatistics
}

data class PerformanceMetrics(
    val averageFPS: Float,
    val frameTimeMs: Float,
    val cpuUsage: Float,
    val gpuUsage: Float,
    val memoryUsageMB: Float,
    val faceDetectionTimeMs: Float,
    val renderingTimeMs: Float
)

data class UsageStatistics(
    val totalFiltersUsed: Int,
    val mostUsedFilter: String,
    val totalProcessingTime: Long,
    val averageSessionDuration: Long,
    val contextUsage: Map<String, Int>
)
```

### 9.2 Network APIs

```kotlin
// Filter Store API
interface FilterStoreAPI {
    @GET("filters/featured")
    suspend fun getFeaturedFilters(): List<FilterMetadata>
    
    @GET("filters/categories")
    suspend fun getCategories(): List<FilterCategory>
    
    @GET("filters/search")
    suspend fun searchFilters(
        @Query("q") query: String,
        @Query("category") category: String?,
        @Query("page") page: Int
    ): PaginatedResponse<FilterMetadata>
    
    @GET("filters/{id}")
    suspend fun getFilterDetails(
        @Path("id") filterId: String
    ): FilterMetadata
    
    @GET("filters/{id}/download")
    suspend fun downloadFilter(
        @Path("id") filterId: String
    ): ResponseBody
    
    @GET("filters/{id}/reviews")
    suspend fun getFilterReviews(
        @Path("id") filterId: String,
        @Query("page") page: Int
    ): PaginatedResponse<FilterReview>
    
    @POST("filters/{id}/rate")
    suspend fun rateFilter(
        @Path("id") filterId: String,
        @Body rating: FilterRating
    ): Response
    
    @POST("analytics/filter-usage")
    suspend fun reportFilterUsage(
        @Body usage: FilterUsageReport
    ): Response
}
```

---

## 10. FILTER CATEGORIES & SPECIFICATIONS

### 10.1 Beauty Filter Specifications

```kotlin
enum class BeautyLevel {
    NATURAL {
        override val smoothingStrength = 0.3f
        override val eyeEnlargement = 0.05f
        override val brightening = 0.1f
    },
    MEDIUM {
        override val smoothingStrength = 0.6f
        override val eyeEnlargement = 0.15f
        override val brightening = 0.25f
    },
    STRONG {
        override val smoothingStrength = 0.9f
        override val eyeEnlargement = 0.3f
        override val brightening = 0.4f
    };
    
    abstract val smoothingStrength: Float
    abstract val eyeEnlargement: Float
    abstract val brightening: Float
}

// Skin smoothing using bilateral filter
class SkinSmoothingEffect(
    private val strength: Float
) {
    fun apply(image: Bitmap): Bitmap {
        // Use bilateral filter to preserve edges while smoothing
        return bilateralFilter(
            image,
            sigmaSpace = 8f * strength,
            sigmaDomain = 5f * strength
        )
    }
}
```

### 10.2 Accessory Filter Specifications

```kotlin
data class AccessoryFilterSpec(
    val assetType: AssetType,
    val attachmentLandmarks: List<Int>,
    val scale: Vector3,
    val rotationAxis: Vector3,
    val physicsEnabled: Boolean = false
)

enum class AssetType {
    GLASSES,      // Attached to nose + eyes
    SUNGLASSES,
    AVIATORS,
    CROWN,        // Attached to forehead
    HAT,          // Attached to head
    HELMET,
    MASK,         // Full face coverage
    EARRINGS,     // Attached to ears
    CHAIN,        // Attached to neck
    CUSTOM_3D
}
```

---

## 11. FUTURE AI EXPANSION ARCHITECTURE

### 11.1 AI Module Integration Points

```kotlin
// Extension interface for AI features
interface AIEffectModule {
    suspend fun initialize()
    suspend fun processFrame(frame: VideoFrame, faceData: FaceData): VideoFrame
    fun getRequiredResources(): AIResourceRequirements
}

data class AIResourceRequirements(
    val modelSizeMB: Int,
    val minRAM: Int,
    val processingTimeMs: Int,
    val requiresInternet: Boolean,
    val requiresGPU: Boolean
)

// Planned AI modules
class AIBeautyEnhancement : AIEffectModule {
    // Real-time face beautification using neural networks
}

class AIFaceStylization : AIEffectModule {
    // Convert face to cartoon, anime, painting style
}

class AINoseEffects : AIEffectModule {
    // AI-powered background replacement/generation
}

class AIAvatarGeneration : AIEffectModule {
    // Generate personalized avatars
}
```

### 11.2 On-Device AI Strategy

```kotlin
class OnDeviceAIManager(
    context: Context,
    private val mlKit: MLKit = MLKit.getInstance(context)
) {
    // Use TensorFlow Lite for on-device ML
    private val tfliteInterpreter: Interpreter by lazy {
        val model = loadModelAsset("beauty_enhancement_model.tflite")
        Interpreter(model)
    }
    
    // NNAPI acceleration for better performance
    private val nnApiDelegate: NNAPIDelegate by lazy {
        NNAPIDelegate()
    }
    
    suspend fun applyAIBeautyEffect(
        frame: VideoFrame,
        faceData: FaceData
    ): VideoFrame = withContext(Dispatchers.Default) {
        val inputTensor = prepareInputTensor(frame, faceData)
        val outputTensor = FloatArray(inputTensor.size)
        
        tfliteInterpreter.run(inputTensor, outputTensor)
        
        reconstructFrameFromTensor(outputTensor)
    }
}
```

---

## 12. DEVELOPMENT ROADMAP

### Phase 1: Core Infrastructure (Weeks 1-4)
- [ ] Media Pipe Face Mesh integration
- [ ] OpenGL ES rendering pipeline setup
- [ ] Camera integration
- [ ] Basic frame processing

### Phase 2: Face Tracking & Detection (Weeks 5-8)
- [ ] Face landmark detection
- [ ] Head pose estimation
- [ ] Eye/mouth tracking
- [ ] Temporal smoothing
- [ ] Multi-face support

### Phase 3: Basic Effects (Weeks 9-12)
- [ ] 2D overlay rendering
- [ ] Beauty effect implementation
- [ ] Simple 3D object rendering
- [ ] Effect switching

### Phase 4: Advanced Effects (Weeks 13-16)
- [ ] Particle effects
- [ ] Reaction triggers
- [ ] Transform effects
- [ ] Post-processing

### Phase 5: Video Call Integration (Weeks 17-20)
- [ ] Real-time filter processing in calls
- [ ] Quality adaptation
- [ ] Performance optimization

### Phase 6: Filter Store & UI (Weeks 21-24)
- [ ] Filter carousel UI
- [ ] Download system
- [ ] Filter store backend
- [ ] Settings UI

### Phase 7: Optimization & Polish (Weeks 25-28)
- [ ] Performance tuning
- [ ] Memory optimization
- [ ] Battery optimization
- [ ] Testing on multiple devices

---

## 13. CONCLUSION

This architecture provides a scalable, secure, and performant foundation for Snapchat-level filter capabilities in Calcvault. The modular design allows for incremental implementation while maintaining compatibility with Calcvault's privacy and security standards.

The system prioritizes:
- **Privacy**: Local processing, minimal data retention
- **Security**: Asset validation, secure memory handling
- **Performance**: GPU acceleration, adaptive quality
- **Extensibility**: AI-ready architecture for future expansion
- **User Experience**: Real-time effects, smooth transitions

---

**Next Steps:**
1. Review and approve architecture
2. Proceed with folder structure and module implementation
3. Set up development environment
4. Begin Phase 1 implementation
