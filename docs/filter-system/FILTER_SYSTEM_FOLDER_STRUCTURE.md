# Calcvault AR Filter System - Folder Structure & Module Organization

## Complete Folder Structure

```
calcvault/
├── app/src/main/
│   ├── kotlin/com/calcvault/
│   │   ├── filter/                          # Root filter system package
│   │   │   ├── common/                      # Shared utilities
│   │   │   │   ├── data/
│   │   │   │   │   ├── FaceData.kt          # Data classes for face tracking
│   │   │   │   │   ├── FilterData.kt        # Filter definition classes
│   │   │   │   │   ├── EffectData.kt        # Effect specifications
│   │   │   │   │   ├── PerformanceMetrics.kt
│   │   │   │   │   └── AssetData.kt
│   │   │   │   ├── utils/
│   │   │   │   │   ├── VectorMath.kt        # Vector/matrix operations
│   │   │   │   │   ├── ConversionUtils.kt   # Color space, format conversions
│   │   │   │   │   ├── ThreadingUtils.kt    # Thread pool, coroutine helpers
│   │   │   │   │   ├── LogUtils.kt
│   │   │   │   │   └── PerformanceUtils.kt
│   │   │   │   ├── constants/
│   │   │   │   │   ├── FilterConstants.kt
│   │   │   │   │   ├── PerformanceConstants.kt
│   │   │   │   │   ├── GLConstants.kt
│   │   │   │   │   └── AssetConstants.kt
│   │   │   │   └── extensions/
│   │   │   │       ├── BitmapExtensions.kt
│   │   │   │       ├── ByteBufferExtensions.kt
│   │   │   │       ├── CoroutineExtensions.kt
│   │   │   │       └── MathExtensions.kt
│   │   │   │
│   │   │   ├── engine/                      # Core filter engine
│   │   │   │   ├── FilterEngine.kt          # Main engine interface
│   │   │   │   ├── FilterEngineImpl.kt       # Implementation
│   │   │   │   ├── FrameProcessor.kt        # Frame processing pipeline
│   │   │   │   ├── FilterOrchestrator.kt    # Lifecycle management
│   │   │   │   └── QualityAdaptor.kt        # Performance adaptation
│   │   │   │
│   │   │   ├── tracking/                    # Face tracking engine
│   │   │   │   ├── FaceTracker.kt           # Main tracking interface
│   │   │   │   ├── MediaPipeFaceTracker.kt  # MediaPipe implementation
│   │   │   │   ├── LandmarkDetector.kt      # Landmark detection
│   │   │   │   ├── HeadPoseEstimator.kt     # Head pose estimation
│   │   │   │   ├── EyeTracker.kt            # Eye tracking
│   │   │   │   ├── MouthDetector.kt         # Mouth detection
│   │   │   │   ├── LandmarkSmoother.kt      # Temporal smoothing
│   │   │   │   ├── FaceMeshBuilder.kt       # Face mesh construction
│   │   │   │   └── TrackerCache.kt          # Caching layer
│   │   │   │
│   │   │   ├── rendering/                   # Rendering pipeline
│   │   │   │   ├── RenderEngine.kt          # Main rendering interface
│   │   │   │   ├── GLRenderEngine.kt        # OpenGL ES implementation
│   │   │   │   ├── ShaderCompiler.kt        # Shader compilation
│   │   │   │   ├── FrameBufferManager.kt    # FBO management
│   │   │   │   ├── TextureManager.kt        # Texture management
│   │   │   │   ├── MeshRenderer.kt          # Mesh rendering
│   │   │   │   ├── ParticleRenderer.kt      # Particle effects
│   │   │   │   ├── OverlayRenderer.kt       # 2D overlay rendering
│   │   │   │   ├── PostProcessor.kt         # Post-processing effects
│   │   │   │   └── EffectCompositor.kt      # Effect layering
│   │   │   │
│   │   │   ├── effects/                     # Effect implementations
│   │   │   │   ├── core/
│   │   │   │   │   ├── Effect.kt            # Base effect interface
│   │   │   │   │   ├── EffectRenderer.kt    # Effect rendering interface
│   │   │   │   │   └── EffectConfig.kt      # Effect configuration
│   │   │   │   ├── beauty/
│   │   │   │   │   ├── BeautyEffect.kt      # Beauty effect implementation
│   │   │   │   │   ├── SkinSmoothingEffect.kt
│   │   │   │   │   ├── FaceAcneReducer.kt
│   │   │   │   │   ├── TeethWhiteningEffect.kt
│   │   │   │   │   ├── EyeBrighteningEffect.kt
│   │   │   │   │   ├── EyeEnlargementEffect.kt
│   │   │   │   │   ├── FaceSlimmingEffect.kt
│   │   │   │   │   ├── JawlineEnhancementEffect.kt
│   │   │   │   │   └── MakeupEffect.kt
│   │   │   │   ├── accessory/
│   │   │   │   │   ├── AccessoryEffect.kt   # Base accessory effect
│   │   │   │   │   ├── GlassesEffect.kt
│   │   │   │   │   ├── HatEffect.kt
│   │   │   │   │   ├── CrownEffect.kt
│   │   │   │   │   ├── MaskEffect.kt
│   │   │   │   │   ├── EarringsEffect.kt
│   │   │   │   │   └── ChainEffect.kt
│   │   │   │   ├── animal/
│   │   │   │   │   ├── AnimalEffect.kt      # Base animal effect
│   │   │   │   │   ├── DogFilterEffect.kt
│   │   │   │   │   ├── CatFilterEffect.kt
│   │   │   │   │   ├── LionFilterEffect.kt
│   │   │   │   │   ├── TigerFilterEffect.kt
│   │   │   │   │   ├── PandaFilterEffect.kt
│   │   │   │   │   └── RabbitFilterEffect.kt
│   │   │   │   ├── transform/
│   │   │   │   │   ├── TransformEffect.kt   # Face deformation effects
│   │   │   │   │   ├── BigEyesEffect.kt
│   │   │   │   │   ├── TinyFaceEffect.kt
│   │   │   │   │   ├── GiantHeadEffect.kt
│   │   │   │   │   └── CartoonEffect.kt
│   │   │   │   ├── fun/
│   │   │   │   │   ├── FunEffect.kt
│   │   │   │   │   ├── EmojiEffect.kt
│   │   │   │   │   └── ComicEffect.kt
│   │   │   │   ├── anime/
│   │   │   │   │   ├── AnimeEffect.kt
│   │   │   │   │   ├── AnimeEyesEffect.kt
│   │   │   │   │   ├── AnimeFaceEffect.kt
│   │   │   │   │   └── ChibiModeEffect.kt
│   │   │   │   ├── background/
│   │   │   │   │   ├── BackgroundEffect.kt  # Base background effect
│   │   │   │   │   ├── BackgroundBlurEffect.kt
│   │   │   │   │   ├── BackgroundReplaceEffect.kt
│   │   │   │   │   ├── VirtualRoomEffect.kt
│   │   │   │   │   └── AIBackgroundEffect.kt
│   │   │   │   ├── professional/
│   │   │   │   │   ├── ProfessionalEffect.kt
│   │   │   │   │   ├── StudioLightingEffect.kt
│   │   │   │   │   └── WebcamEnhancementEffect.kt
│   │   │   │   └── reaction/
│   │   │   │       ├── ReactionEffect.kt    # Reaction trigger system
│   │   │   │       ├── SmileReactionEffect.kt
│   │   │   │       ├── BlinkReactionEffect.kt
│   │   │   │       └── MouthReactionEffect.kt
│   │   │   │
│   │   │   ├── assets/                      # Asset management
│   │   │   │   ├── AssetManager.kt          # Main asset manager
│   │   │   │   ├── AssetLoader.kt           # Asset loading logic
│   │   │   │   ├── AssetValidator.kt        # Asset validation/security
│   │   │   │   ├── AssetCompressor.kt       # Asset compression
│   │   │   │   ├── AssetCache.kt            # Memory cache
│   │   │   │   ├── AssetDatabase.kt         # Database persistence
│   │   │   │   ├── GLTFLoader.kt            # GLTF/GLB loading
│   │   │   │   ├── PNGAssetLoader.kt        # PNG overlay loading
│   │   │   │   ├── WebPAssetLoader.kt       # WebP asset loading
│   │   │   │   └── AnimationLoader.kt       # Animation loading
│   │   │   │
│   │   │   ├── filters/                     # Filter definitions & management
│   │   │   │   ├── FilterManager.kt         # Filter lifecycle management
│   │   │   │   ├── FilterLoader.kt          # Filter loading
│   │   │   │   ├── FilterRepository.kt      # Database repository
│   │   │   │   ├── FilterStore.kt           # Cloud filter store client
│   │   │   │   ├── FilterCategories.kt      # Filter categorization
│   │   │   │   ├── FilterFavorites.kt       # Favorites management
│   │   │   │   └── FilterCustomization.kt   # User customization storage
│   │   │   │
│   │   │   ├── camera/                      # Camera integration
│   │   │   │   ├── CameraController.kt      # Camera control interface
│   │   │   │   ├── Camera2Controller.kt     # Camera2 API implementation
│   │   │   │   ├── CameraPermissions.kt     # Permission handling
│   │   │   │   ├── FrameCapture.kt          # Frame capture from camera
│   │   │   │   ├── VideoRecorder.kt         # Video recording
│   │   │   │   ├── CameraPreview.kt         # Preview management
│   │   │   │   └── CameraQuality.kt         # Quality management
│   │   │   │
│   │   │   ├── videocall/                   # Video call integration
│   │   │   │   ├── CallFilterManager.kt     # Filter control in calls
│   │   │   │   ├── CallFrameProcessor.kt    # Call frame processing
│   │   │   │   ├── CallQualityAdaptor.kt    # Quality adaptation for calls
│   │   │   │   ├── CallEncodingOptimizer.kt # Encoding optimization
│   │   │   │   └── CallIntegration.kt       # Integration with call system
│   │   │   │
│   │   │   ├── ui/                          # UI components
│   │   │   │   ├── screens/
│   │   │   │   │   ├── CameraScreen.kt      # Camera capture UI
│   │   │   │   │   ├── FilterCarouselUI.kt  # Filter browsing UI
│   │   │   │   │   ├── FilterStoreUI.kt     # Filter marketplace UI
│   │   │   │   │   ├── SettingsUI.kt        # Filter settings
│   │   │   │   │   └── EffectEditorUI.kt    # Effect customization
│   │   │   │   ├── components/
│   │   │   │   │   ├── FilterCarouselView.kt
│   │   │   │   │   ├── CaptureButtonView.kt
│   │   │   │   │   ├── FilterPreviewView.kt
│   │   │   │   │   ├── EffectSliderView.kt
│   │   │   │   │   └── SettingsPanel.kt
│   │   │   │   ├── viewmodels/
│   │   │   │   │   ├── CameraViewModel.kt
│   │   │   │   │   ├── FilterCarouselViewModel.kt
│   │   │   │   │   ├── FilterStoreViewModel.kt
│   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   └── animations/
│   │   │   │       ├── FilterTransitionAnimation.kt
│   │   │   │       ├── CaptureAnimation.kt
│   │   │   │       └── TransitionAnimations.kt
│   │   │   │
│   │   │   ├── database/                    # Local database
│   │   │   │   ├── FilterDatabase.kt        # Room database
│   │   │   │   ├── dao/
│   │   │   │   │   ├── FilterDao.kt
│   │   │   │   │   ├── FilterUsageDao.kt
│   │   │   │   │   ├── FilterCustomizationDao.kt
│   │   │   │   │   ├── DownloadedAssetDao.kt
│   │   │   │   │   └── LandmarkCacheDao.kt
│   │   │   │   └── entities/
│   │   │   │       ├── FilterEntity.kt
│   │   │   │       ├── FilterUsageEntity.kt
│   │   │   │       ├── FilterCustomizationEntity.kt
│   │   │   │       ├── DownloadedAssetEntity.kt
│   │   │   │       └── LandmarkCacheEntity.kt
│   │   │   │
│   │   │   ├── network/                     # Network & API
│   │   │   │   ├── FilterStoreService.kt    # Retrofit service
│   │   │   │   ├── FilterRepository.kt      # Data repository layer
│   │   │   │   ├── NetworkModels.kt         # API request/response models
│   │   │   │   ├── NetworkInterceptor.kt
│   │   │   │   └── DownloadManager.kt       # Filter download management
│   │   │   │
│   │   │   ├── performance/                 # Performance optimization
│   │   │   │   ├── PerformanceMonitor.kt    # FPS, timing metrics
│   │   │   │   ├── MemoryOptimizer.kt       # Memory management
│   │   │   │   ├── CPUProfiler.kt           # CPU profiling
│   │   │   │   ├── GPUProfiler.kt           # GPU profiling
│   │   │   │   ├── BatteryOptimizer.kt      # Battery optimization
│   │   │   │   ├── ThermalController.kt     # Thermal protection
│   │   │   │   └── AdaptiveQuality.kt       # Dynamic quality scaling
│   │   │   │
│   │   │   ├── security/                    # Security & privacy
│   │   │   │   ├── AssetSecurity.kt         # Asset validation/encryption
│   │   │   │   ├── SecureBuffer.kt          # Secure memory handling
│   │   │   │   ├── PermissionManager.kt     # Permission management
│   │   │   │   ├── PrivacyConfig.kt         # Privacy settings
│   │   │   │   └── DataCleanup.kt           # Cleanup of sensitive data
│   │   │   │
│   │   │   ├── analytics/                   # Analytics & telemetry
│   │   │   │   ├── FilterAnalytics.kt       # Usage analytics
│   │   │   │   ├── CrashReporter.kt         # Error reporting
│   │   │   │   ├── PerformanceReporter.kt   # Performance telemetry
│   │   │   │   └── EventLogger.kt           # Event logging
│   │   │   │
│   │   │   ├── ai/                          # AI features (future)
│   │   │   │   ├── AIEffectModule.kt        # Base AI module interface
│   │   │   │   ├── AIBeautyEnhancer.kt      # AI beauty effects
│   │   │   │   ├── AIFaceStylizer.kt        # AI stylization
│   │   │   │   ├── AIBackgroundGenerator.kt # AI background generation
│   │   │   │   ├── ModelManager.kt          # ML model management
│   │   │   │   └── TFLiteHelper.kt          # TensorFlow Lite utilities
│   │   │   │
│   │   │   └── integration/                 # Integration with Calcvault
│   │   │       ├── FilterIntegration.kt     # Main integration point
│   │   │       ├── CallIntegration.kt       # Call system integration
│   │   │       ├── CameraIntegration.kt     # Camera system integration
│   │   │       ├── NotificationIntegration.kt
│   │   │       └── SettingsIntegration.kt   # Settings integration
│   │   │
│   │   └── ... (other Calcvault packages)
│   │
│   ├── res/
│   │   ├── drawable/
│   │   │   ├── ic_camera.xml
│   │   │   ├── ic_filter.xml
│   │   │   ├── ic_gallery.xml
│   │   │   └── ic_settings.xml
│   │   ├── layout/
│   │   │   ├── fragment_camera.xml
│   │   │   ├── fragment_filter_carousel.xml
│   │   │   ├── fragment_filter_store.xml
│   │   │   ├── view_capture_button.xml
│   │   │   └── view_effect_slider.xml
│   │   ├── values/
│   │   │   ├── dimens.xml
│   │   │   ├── colors_filter.xml
│   │   │   └── strings_filter.xml
│   │   ├── anim/
│   │   │   ├── filter_fade_in.xml
│   │   │   ├── filter_fade_out.xml
│   │   │   ├── filter_slide_left.xml
│   │   │   └── capture_pulse.xml
│   │   └── raw/
│   │       ├── shaders/
│   │       │   ├── vertex_default.glsl
│   │       │   ├── fragment_beauty.glsl
│   │       │   ├── fragment_overlay.glsl
│   │       │   ├── fragment_blur.glsl
│   │       │   └── fragment_particle.glsl
│   │       └── filters/
│   │           ├── filter_manifest_template.json
│   │           └── default_filters.json
│   │
│   └── assets/
│       ├── filters/                         # Built-in filters
│       │   ├── beauty_natural/
│       │   ├── beauty_medium/
│       │   ├── beauty_strong/
│       │   ├── dog_filter/
│       │   ├── cat_filter/
│       │   ├── glasses_filter/
│       │   └── ... (other built-in filters)
│       ├── models/
│       │   ├── mediapipe_face_mesh.tflite
│       │   ├── mediapipe_face_detection.tflite
│       │   └── beauty_enhancement_model.tflite
│       └── shaders/
│           ├── default.vert
│           ├── default.frag
│           └── post_process.frag
│
├── dependencies/
│   ├── filter_dependencies.gradle          # Filter system dependencies
│   └── versions.gradle
│
├── scripts/
│   ├── build_filters.sh                    # Filter building script
│   ├── optimize_assets.sh                  # Asset optimization
│   ├── generate_docs.sh                    # Documentation generation
│   └── test_filters.sh                     # Filter testing
│
├── docs/
│   ├── FILTER_SYSTEM_TECHNICAL_ARCHITECTURE.md
│   ├── FILTER_IMPLEMENTATION_GUIDE.md
│   ├── FILTER_API_REFERENCE.md
│   ├── SHADER_DEVELOPMENT_GUIDE.md
│   ├── ASSET_FORMAT_SPECIFICATION.md
│   ├── PERFORMANCE_TUNING_GUIDE.md
│   ├── SECURITY_GUIDELINES.md
│   ├── FILTER_DEVELOPMENT_EXAMPLES.md
│   └── TROUBLESHOOTING.md
│
└── tests/
    ├── unit/
    │   ├── FilterEngineTest.kt
    │   ├── FaceTrackerTest.kt
    │   ├── RenderEngineTest.kt
    │   ├── AssetManagerTest.kt
    │   └── EffectTest.kt
    ├── integration/
    │   ├── CameraIntegrationTest.kt
    │   ├── CallIntegrationTest.kt
    │   └── FilterPipelineTest.kt
    └── performance/
        ├── PerformanceBenchmarks.kt
        └── MemoryLeakTests.kt
```

## Module Dependency Graph

```
UI Layer
  ↓
CameraScreen → FilterCarouselUI → SettingsUI
  ↓ (depends on)
CameraViewModel → FilterCarouselViewModel → SettingsViewModel
  ↓ (depends on)
FilterIntegration ←→ FilterEngine ←→ FilterManager
  ↓                   ↓
CameraController      FaceTracker → RenderEngine
  ↓                   ↓
Camera2API        MediaPipeFaceTracker
  ↓
Platform APIs

FilterStore ←→ FilterStoreService ←→ Network
  ↓
AssetManager ←→ AssetDatabase
  ↓
FileSystem / SharedPreferences

CallFilterManager ↔ CallSystem ↔ FilterEngine
```

## Key Design Principles

1. **Separation of Concerns**: Each package handles a specific responsibility
2. **Layered Architecture**: Clear dependency flow from UI → Business Logic → Platform APIs
3. **Interface-Based Design**: Implementations can be swapped (e.g., GLRenderEngine vs Vulkan)
4. **Resource Management**: Proper lifecycle management for GPU/CPU resources
5. **Testability**: Clear seams for unit and integration testing
6. **Extensibility**: Easy to add new effects, filters, and features

## File Naming Conventions

- **Interfaces**: `SomethingManager.kt`, `SomethingEngine.kt`, `SomethingTracker.kt`
- **Implementations**: `SomethingManagerImpl.kt`, `GLRenderEngine.kt`
- **Data Classes**: `SomethingData.kt`, entities follow database table names
- **Effects**: `SomethingEffect.kt` (e.g., `DogFilterEffect.kt`)
- **Utilities**: `SomethingUtils.kt`, `SomethingExtensions.kt`
- **Tests**: `SomethingTest.kt` (unit), `SomethingIntegrationTest.kt`
