# Calcvault AR Filter System - Video Call & Camera Integration

## 1. VIDEO CALL INTEGRATION DESIGN

### 1.1 Integration Architecture

```
Outgoing Call (User's Camera → Filters → Encoding → Network)

┌──────────────────────────────────────────────────────────┐
│ Camera Input (YUV_420)                                   │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Face Detection & Tracking (Background Thread)            │
│ - MediaPipe Face Mesh                                    │
│ - Landmarks & metrics                                    │
│ - Output: FaceData                                       │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Filter Application (GL Thread)                           │
│ - Apply active filter effects                            │
│ - Render to filtered frame                               │
│ - Output: RGBA frame                                     │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Format Conversion (YUV_420)                              │
│ - Convert RGBA → YUV_420 for encoder                     │
│ - Optimize for H.264 encoding                            │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Video Encoding (MediaCodec)                              │
│ - H.264 @ 30 FPS, 1.5-3 Mbps                             │
│ - Quality adjusted for bandwidth                         │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Encryption (Calcvault E2EE)                              │
│ - End-to-end encryption applied                          │
│ - Frame data encrypted with call key                     │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Network Transmission                                     │
│ - Send to recipient via secure channel                   │
│ - Adaptive bitrate control                               │
└──────────────────────────────────────────────────────────┘

---

Incoming Call (Network → Decryption → Decoding → Display)

┌──────────────────────────────────────────────────────────┐
│ Network Reception                                        │
│ - Receive encrypted video packet                         │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Decryption (Calcvault E2EE)                              │
│ - Decrypt with call key                                  │
│ - Validate packet integrity                              │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Video Decoding (MediaCodec)                              │
│ - H.264 decode to YUV_420                                │
│ - Handle packet loss/retransmission                      │
└──────────────────┬───────────────────────────────────────┘
                   ↓
┌──────────────────────────────────────────────────────────┐
│ Display on Screen                                        │
│ - Render remote video with applied filters               │
│ - Show on video call UI                                  │
└──────────────────────────────────────────────────────────┘

NOTE: Remote user SEES the filters applied by the caller.
The receiving end just displays the pre-filtered video.
```

### 1.2 Call Filter Manager Implementation

```kotlin
// CallFilterManager.kt
class CallFilterManager(
    private val callSession: CallSession,
    private val filterEngine: FilterEngine,
    private val context: Context
) {
    private var currentFilter: Filter? = null
    private var filterIntensity: Float = 1.0f
    private var isFilteringEnabled: Boolean = true
    
    private val filterSwitchScope = CoroutineScope(
        Dispatchers.Main + Job() + SupervisorJob()
    )
    
    private var filterCache = mutableMapOf<String, Filter>()
    
    // Initialize filter system for call
    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            filterEngine.initialize()
            
            // Preload favorite filters for quick switching
            preloadFavoriteFilters()
            
            Log.d("CallFilterManager", "Filter system initialized for call")
        } catch (e: Exception) {
            Log.e("CallFilterManager", "Initialization failed", e)
            throw e
        }
    }
    
    // Switch to a different filter during call
    fun switchFilter(filterId: String) {
        filterSwitchScope.launch {
            try {
                // Load filter (from cache if available)
                val filter = filterCache[filterId] ?: run {
                    filterEngine.loadFilter(filterId).also {
                        filterCache[filterId] = it
                    }
                }
                
                // Animate fade out of current filter
                if (currentFilter != null) {
                    animateFilterFadeOut(duration = 150)
                }
                
                // Switch filter
                currentFilter = filter
                
                // Animate fade in of new filter
                animateFilterFadeIn(duration = 150)
                
                Log.d("CallFilterManager", "Switched to filter: $filterId")
            } catch (e: Exception) {
                Log.e("CallFilterManager", "Filter switch failed", e)
            }
        }
    }
    
    // Disable filters
    fun disableFilters() {
        currentFilter = null
        isFilteringEnabled = false
        filterEngine.clearEffects()
    }
    
    // Enable filters
    fun enableFilters() {
        isFilteringEnabled = true
    }
    
    // Set filter intensity (0.0-1.0)
    fun setFilterIntensity(intensity: Float) {
        filterIntensity = intensity.coerceIn(0f, 1f)
        filterEngine.setFilterIntensity(filterIntensity)
    }
    
    // Add to favorites
    fun addToFavorites(filterId: String) {
        filterSwitchScope.launch {
            try {
                // Update database
                val repository = FilterRepository.getInstance(context)
                repository.addToFavorites(filterId)
            } catch (e: Exception) {
                Log.e("CallFilterManager", "Failed to add favorite", e)
            }
        }
    }
    
    // Remove from favorites
    fun removeFromFavorites(filterId: String) {
        filterSwitchScope.launch {
            try {
                val repository = FilterRepository.getInstance(context)
                repository.removeFromFavorites(filterId)
            } catch (e: Exception) {
                Log.e("CallFilterManager", "Failed to remove favorite", e)
            }
        }
    }
    
    // Process outgoing call frame
    fun processOutgoingFrame(frame: VideoFrame): VideoFrame {
        return if (isFilteringEnabled && currentFilter != null) {
            try {
                filterEngine.processCallFrame(frame, outgoing = true)
            } catch (e: Exception) {
                Log.e("CallFilterManager", "Frame processing failed", e)
                frame  // Return unfiltered on error
            }
        } else {
            frame
        }
    }
    
    // Get current filter info
    fun getCurrentFilter(): Filter? = currentFilter
    
    // Get performance metrics
    fun getMetrics(): CallFilterMetrics {
        return CallFilterMetrics(
            filteringEnabled = isFilteringEnabled,
            currentFilter = currentFilter?.id,
            filterIntensity = filterIntensity,
            processingTimeMs = filterEngine.getPerformanceMetrics().frameTimeMs,
            averageFPS = filterEngine.getPerformanceMetrics().averageFPS
        )
    }
    
    // Cleanup
    suspend fun shutdown() {
        filterSwitchScope.coroutineContext.job.cancelChildren()
        filterEngine.shutdown()
    }
    
    private suspend fun preloadFavoriteFilters() {
        try {
            val repository = FilterRepository.getInstance(context)
            val favorites = repository.getFavoriteFilters()
            
            for (favorite in favorites.take(5)) {  // Preload top 5 favorites
                try {
                    val filter = filterEngine.loadFilter(favorite.id)
                    filterCache[favorite.id] = filter
                } catch (e: Exception) {
                    Log.e("CallFilterManager", "Failed to preload filter: ${favorite.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CallFilterManager", "Failed to preload favorites", e)
        }
    }
    
    private fun animateFilterFadeOut(duration: Long) {
        // Animate filter intensity to 0
        ObjectAnimator.ofFloat(this, "filterIntensity", filterIntensity, 0f)
            .setDuration(duration)
            .start()
    }
    
    private fun animateFilterFadeIn(duration: Long) {
        // Animate filter intensity from 0 to 1
        ObjectAnimator.ofFloat(this, "filterIntensity", 0f, 1f)
            .setDuration(duration)
            .start()
    }
}

data class CallFilterMetrics(
    val filteringEnabled: Boolean,
    val currentFilter: String?,
    val filterIntensity: Float,
    val processingTimeMs: Float,
    val averageFPS: Float
)
```

### 1.3 Call Integration Point

```kotlin
// CallIntegration.kt - Integration with Calcvault's call system
class CallIntegration(
    private val context: Context,
    private val callSession: CallSession
) {
    private lateinit var filterManager: CallFilterManager
    private var frameProcessingJob: Job? = null
    
    suspend fun setupFilteringForCall() {
        filterManager = CallFilterManager(callSession, FilterEngine.getInstance(), context)
        filterManager.initialize()
        
        // Hook into call's video frame processing
        callSession.setOutgoingFrameProcessor { frame ->
            filterManager.processOutgoingFrame(frame)
        }
    }
    
    fun onFilterCarouselRequest() {
        // User requested filter carousel during call
        // Show filter picker UI overlay
        showFilterPickerOverlay()
    }
    
    fun onFilterSelected(filterId: String) {
        filterManager.switchFilter(filterId)
    }
    
    fun onFilterIntensityChanged(intensity: Float) {
        filterManager.setFilterIntensity(intensity)
    }
    
    fun onFilterFavorited(filterId: String) {
        filterManager.addToFavorites(filterId)
    }
    
    suspend fun teardownFiltering() {
        filterManager.shutdown()
    }
    
    private fun showFilterPickerOverlay() {
        // Show bottom sheet with filter carousel
        // Allows instant switching between filters
    }
}
```

---

## 2. CAMERA INTEGRATION DESIGN

### 2.1 Camera API Integration

```
┌─────────────────────────────────────────────────────────┐
│ Calcvault Camera Module                                 │
└────────────────────┬────────────────────────────────────┘
                     ↓
         ┌───────────────────────────┐
         │ Camera2 API Integration    │
         │ - Opens camera session     │
         │ - Configures capture      │
         │ - Manages preview         │
         └────────┬────────────────┘
                  ↓
    ┌─────────────────────────────────┐
    │ Preview + Capture Targets       │
    │ - Surface for preview           │
    │ - ImageReader for capture       │
    │ - Filter processing pipeline    │
    └────────┬────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ Filter Engine Processing        │
    │ - Real-time preview filtering   │
    │ - Face detection on frames      │
    │ - Effect rendering              │
    └────────┬────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ Output Processing               │
    │ - Capture filtered photo        │
    │ - Record filtered video         │
    │ - Display in preview            │
    └─────────────────────────────────┘
```

### 2.2 Camera Controller Implementation

```kotlin
// CameraController.kt - Main camera interface
interface CameraController {
    suspend fun initialize()
    suspend fun shutdown()
    
    suspend fun startPreview(surfaceTexture: SurfaceTexture)
    suspend fun stopPreview()
    
    suspend fun takePicture(): Bitmap
    suspend fun startRecording(outputPath: String): Recording
    suspend fun stopRecording()
    
    fun switchCamera(cameraId: String)
    fun setFlashMode(mode: FlashMode)
    fun setFocusMode(mode: FocusMode)
    
    fun getAvailableCameras(): List<CameraInfo>
    fun getCameraInfo(cameraId: String): CameraInfo?
}

// Camera2ControllerImpl.kt - Camera2 API implementation
class Camera2ControllerImpl(
    private val context: Context,
    private val filterEngine: FilterEngine
) : CameraController {
    
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentCameraId: String = "0"  // Default to front camera
    
    private lateinit var previewSize: Size
    private lateinit var previewSurface: SurfaceTexture
    
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    
    override suspend fun initialize() = withContext(Dispatchers.Main) {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        // Check permissions
        if (!hasPermissions()) {
            throw SecurityException("Camera permissions not granted")
        }
        
        // Find best camera
        currentCameraId = findBestCamera()
    }
    
    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        withContext(Dispatchers.Main) {
            previewSurface = surfaceTexture
            
            // Open camera
            val cameraDevice = openCamera(currentCameraId)
            this@Camera2ControllerImpl.cameraDevice = cameraDevice
            
            // Create capture session
            val surface = Surface(surfaceTexture)
            createCaptureSession(cameraDevice, surface)
        }
    }
    
    override suspend fun takePicture(): Bitmap {
        return withContext(Dispatchers.Default) {
            val imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,
                1
            )
            
            val captureRequest = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE
            )
            captureRequest.addTarget(imageReader.surface)
            
            var capturedFrame: Image? = null
            var result = CompletableFuture<Bitmap>()
            
            imageReader.setOnImageAvailableListener({
                capturedFrame = imageReader.acquireLatestImage()
                if (capturedFrame != null) {
                    result.complete(
                        convertYUVtoBitmap(capturedFrame!!)
                    )
                }
            }, Handler(Looper.getMainLooper()))
            
            // Submit capture request
            captureSession!!.capture(captureRequest.build(), null, null)
            
            try {
                result.get(5, TimeUnit.SECONDS)
            } finally {
                capturedFrame?.close()
                imageReader.close()
            }
        }
    }
    
    override suspend fun startRecording(outputPath: String): Recording {
        return withContext(Dispatchers.Default) {
            val mediaRecorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(previewSize.width, previewSize.height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(5000000)  // 5 Mbps
                setOutputFile(outputPath)
                prepare()
                start()
            }
            
            Recording(mediaRecorder, outputPath)
        }
    }
    
    override suspend fun stopRecording() {
        // Handled by Recording class
    }
    
    override fun switchCamera(cameraId: String) {
        mainScope.launch {
            stopPreview()
            currentCameraId = cameraId
            cameraDevice?.close()
            startPreview(previewSurface)
        }
    }
    
    override fun setFlashMode(mode: FlashMode) {
        // Set flash mode via capture request
        val characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
        val flashAvailable = characteristics.get(
            CameraCharacteristics.FLASH_INFO_AVAILABLE
        ) ?: false
        
        if (flashAvailable) {
            // Update capture request with flash mode
        }
    }
    
    private suspend fun openCamera(cameraId: String): CameraDevice {
        return suspendCancellableCoroutine { continuation ->
            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        continuation.resume(device)
                    }
                    
                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        continuation.resumeWithException(
                            IOException("Camera disconnected")
                        )
                    }
                    
                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        continuation.resumeWithException(
                            IOException("Camera error: $error")
                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }
    }
    
    private suspend fun createCaptureSession(
        device: CameraDevice,
        surface: Surface
    ) {
        return suspendCancellableCoroutine { continuation ->
            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        
                        // Create preview request
                        val previewRequest = device.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW
                        ).apply {
                            addTarget(surface)
                            set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                            )
                        }
                        
                        // Start preview
                        session.setRepeatingRequest(previewRequest.build(), null, null)
                        continuation.resume(Unit)
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        continuation.resumeWithException(
                            IOException("Failed to configure capture session")
                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }
    }
    
    private fun findBestCamera(): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        // Prefer front camera for selfies
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId
            }
        }
        
        return cameraManager.cameraIdList[0]
    }
    
    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun convertYUVtoBitmap(image: Image): Bitmap {
        // Convert YUV_420_888 to Bitmap
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val paddedRowSize = planes[0].rowPadding + planes[0].pixelStride * image.width
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        
        // Copy Y plane
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        // TODO: Proper YUV to RGB conversion using libyuv or similar
        
        return bitmap
    }
    
    override suspend fun shutdown() {
        withContext(Dispatchers.Main) {
            captureSession?.close()
            cameraDevice?.close()
            mainScope.coroutineContext.job.cancelChildren()
        }
    }
    
    override fun getAvailableCameras(): List<CameraInfo> {
        return cameraManager.cameraIdList.map { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: 0
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            
            CameraInfo(
                id = id,
                name = if (facing == 0) "Back" else "Front",
                facing = CameraFacing.values()[facing],
                sensorOrientation = sensorOrientation
            )
        }
    }
    
    override fun getCameraInfo(cameraId: String): CameraInfo? {
        return getAvailableCameras().find { it.id == cameraId }
    }
}

data class CameraInfo(
    val id: String,
    val name: String,
    val facing: CameraFacing,
    val sensorOrientation: Int
)

enum class CameraFacing {
    BACK, FRONT
}

enum class FlashMode {
    OFF, ON, AUTO, RED_EYE
}

enum class FocusMode {
    AUTO, CONTINUOUS, MACRO
}

class Recording(
    private val mediaRecorder: MediaRecorder,
    val outputPath: String
) : Closeable {
    fun stop() {
        try {
            mediaRecorder.stop()
            mediaRecorder.reset()
        } catch (e: Exception) {
            Log.e("Recording", "Failed to stop recording", e)
        }
    }
    
    override fun close() {
        stop()
        mediaRecorder.release()
    }
}
```

### 2.3 Camera UI Integration

```kotlin
// CameraScreen.kt - Camera UI Fragment
class CameraScreen : Fragment() {
    
    private lateinit var filterManager: FilterEngine
    private lateinit var cameraController: CameraController
    private val viewModel: CameraViewModel by viewModels()
    
    private lateinit var cameraTexture: TextureView
    private lateinit var captureButton: ImageButton
    private lateinit var videoButton: ImageButton
    private lateinit var filterButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var cameraSwitch: ImageButton
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        setupListeners()
        
        lifecycleScope.launch {
            try {
                filterManager = FilterEngine.getInstance()
                cameraController = Camera2ControllerImpl(requireContext(), filterManager)
                
                filterManager.initialize()
                cameraController.initialize()
                cameraController.startPreview(cameraTexture.surfaceTexture!!)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Initialization failed", e)
                showError("Failed to initialize camera")
            }
        }
    }
    
    private fun setupListeners() {
        captureButton.setOnClickListener {
            lifecycleScope.launch {
                val photo = cameraController.takePicture()
                // Process and save photo
                savePhoto(photo)
            }
        }
        
        videoButton.setOnClickListener {
            // Start/stop video recording
        }
        
        filterButton.setOnClickListener {
            showFilterCarousel()
        }
        
        galleryButton.setOnClickListener {
            openGallery()
        }
        
        cameraSwitch.setOnClickListener {
            // Switch between front/back cameras
        }
    }
    
    private fun showFilterCarousel() {
        val carousel = FilterCarouselBottomSheet()
        carousel.onFilterSelected = { filterId ->
            lifecycleScope.launch {
                filterManager.switchFilter(filterId)
            }
        }
        carousel.show(parentFragmentManager, "filters")
    }
}
```

---

## 3. FRAME PROCESSING OPTIMIZATION

### 3.1 Real-Time Processing Strategy

```
┌─────────────────────────────────────────────────┐
│ CAMERA FRAME PROCESSING (30 FPS Target)         │
├─────────────────────────────────────────────────┤
│ Frame Interval: ~33ms per frame                  │
├─────────────────────────────────────────────────┤
│ Camera Thread:           5ms (frame capture)     │
│ Background Thread:      12ms (face detection)    │
│ GL Thread:              12ms (rendering)         │
│ GPU Operations:          3ms (memory copy)       │
│ Display:                 1ms (composition)       │
├─────────────────────────────────────────────────┤
│ Total:                 ~33ms (fits in budget)    │
└─────────────────────────────────────────────────┘

### Multi-Threading Model

┌─────────────────────────────────────────────────┐
│ MAIN THREAD                                     │
│ - UI updates                                    │
│ - Fragment lifecycle                            │
│ - Button interactions                           │
└──────────────────────┬──────────────────────────┘

┌──────────────────────┴──────────────────────────┐
│ CAMERA THREAD (Camera2 callbacks)               │
│ - Camera frame callbacks                        │
│ - Feed frames to processing pipeline            │
└──────────────────────┬──────────────────────────┘

┌──────────────────────┴──────────────────────────┐
│ ML INFERENCE THREAD (Coroutines Default)        │
│ - Face detection                                │
│ - Landmark extraction                           │
│ - Facial metrics                                │
└──────────────────────┬──────────────────────────┘

┌──────────────────────┴──────────────────────────┐
│ GL RENDER THREAD                                │
│ - Effect rendering                              │
│ - GPU operations                                │
│ - Frame composition                             │
└──────────────────────┬──────────────────────────┘

┌──────────────────────┴──────────────────────────┐
│ ENCODER THREAD (MediaCodec callback)            │
│ - Video encoding for recording                  │
│ - H.264 encoding                                │
└─────────────────────────────────────────────────┘
```

This architecture ensures:
- No blocking of UI thread
- Smooth camera preview
- Consistent filter rendering
- Efficient CPU/GPU resource utilization
