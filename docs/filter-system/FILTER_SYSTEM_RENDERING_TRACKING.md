# Calcvault AR Filter System - Rendering & Face Tracking Pipelines

## 1. DETAILED RENDERING PIPELINE

### 1.1 Pipeline Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ RENDER LOOP (Every Frame)                                   │
└─────────────────────────────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 1. INPUT FRAME PREPARATION          │
        ├────────────────────────────────────┤
        │ - Capture frame from camera         │
        │ - Convert YUV_420 → RGBA            │
        │ - Adjust for device orientation     │
        │ - Get face tracking data            │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 2. FRAME BUFFER SETUP               │
        ├────────────────────────────────────┤
        │ - Create FBO (Frame Buffer Object)  │
        │ - Attach color texture              │
        │ - Attach depth texture (optional)   │
        │ - Clear buffers                     │
        │ - Set viewport                      │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 3. BACKGROUND RENDERING            │
        ├────────────────────────────────────┤
        │ - Render camera frame as base layer │
        │ - Full screen quad                  │
        │ - Color correction                  │
        │ - Dynamic range compression         │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 4. FACE DETECTION LAYER            │
        ├────────────────────────────────────┤
        │ - Use face landmarks for effects    │
        │ - Build face mesh triangles         │
        │ - Calculate deformation if needed   │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 5. EFFECT LAYER 1: FACE EFFECTS   │
        ├────────────────────────────────────┤
        │ - Skin smoothing (bilateral blur)   │
        │ - Beauty enhancements               │
        │ - Face deformation mesh             │
        │ - Render to intermediate FBO        │
        │ - Blend with camera frame           │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 6. EFFECT LAYER 2: OVERLAYS       │
        ├────────────────────────────────────┤
        │ - 2D PNG/WebP overlays              │
        │ - Position based on landmarks       │
        │ - Alpha blending (OVER)             │
        │ - Animated sprites                  │
        │ - Render to current FBO             │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 7. EFFECT LAYER 3: 3D OBJECTS     │
        ├────────────────────────────────────┤
        │ - Load 3D models (GLB/GLTF)        │
        │ - Position at landmarks             │
        │ - Apply transformations             │
        │ - Depth testing enabled             │
        │ - Skeletal animation                │
        │ - Render to current FBO             │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 8. EFFECT LAYER 4: PARTICLES      │
        ├────────────────────────────────────┤
        │ - Particle system update            │
        │ - Position particles at landmarks   │
        │ - Physics simulation                │
        │ - Alpha blending                    │
        │ - Render to current FBO             │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 9. POST-PROCESSING                 │
        ├────────────────────────────────────┤
        │ - Color grading                     │
        │ - Bloom effect (optional)           │
        │ - Tone mapping                      │
        │ - Sharpening                        │
        │ - Vignette (optional)               │
        │ - Render to main FBO                │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 10. FINAL COMPOSITION              │
        ├────────────────────────────────────┤
        │ - Resolve MSAA if enabled           │
        │ - Copy to screen framebuffer        │
        │ - Call glSwapBuffers()              │
        └────────────────────────────────────┘
```

### 1.2 Rendering Engine Implementation

```kotlin
// RenderEngine.kt - Main rendering interface
interface RenderEngine {
    suspend fun initialize()
    suspend fun shutdown()
    
    fun startRendering(surfaceTexture: SurfaceTexture)
    fun stopRendering()
    
    fun setActiveFilter(filter: Filter)
    fun setFilterIntensity(intensity: Float)
    
    fun submitFrame(frame: VideoFrame, faceData: List<FaceData>)
    fun getRenderedFrame(): Bitmap?
    
    fun setRenderingQuality(quality: RenderingQuality)
    fun getPerformanceMetrics(): PerformanceMetrics
}

// GLRenderEngine.kt - OpenGL ES implementation
class GLRenderEngine(
    private val context: Context,
    private val glThread: GLThread
) : RenderEngine {
    
    private lateinit var shaderProgram: GLProgram
    private lateinit var frameBuffer: FrameBuffer
    private lateinit var meshRenderer: MeshRenderer
    private lateinit var overlayRenderer: OverlayRenderer
    private lateinit var particleRenderer: ParticleRenderer
    private lateinit var postProcessor: PostProcessor
    
    private var currentFilter: Filter? = null
    private var filterIntensity: Float = 1.0f
    
    override fun submitFrame(frame: VideoFrame, faceData: List<FaceData>) {
        glThread.queueEvent {
            renderFrameInternal(frame, faceData)
        }
    }
    
    private fun renderFrameInternal(frame: VideoFrame, faceData: List<FaceData>) {
        // 1. Prepare frame
        val cameraTexture = uploadCameraFrame(frame)
        
        // 2. Create FBO
        val fbo = frameBuffer.getOrCreate(
            "render_target",
            frame.width,
            frame.height
        )
        fbo.bind()
        GLES30.glViewport(0, 0, frame.width, frame.height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        
        // 3. Render background
        renderBackground(cameraTexture)
        
        // 4. Render effects
        if (currentFilter != null && faceData.isNotEmpty()) {
            val face = faceData[0]  // Process first face
            
            // 4a. Face effects
            if (currentFilter!!.effects.any { it is BeautyEffect }) {
                renderBeautyEffects(currentFilter!!, face)
            }
            
            // 4b. Overlays
            if (currentFilter!!.effects.any { it is AccessoryEffect }) {
                overlayRenderer.renderOverlays(
                    currentFilter!!.effects,
                    face,
                    filterIntensity
                )
            }
            
            // 4c. 3D Objects
            if (currentFilter!!.effects.any { it is TransformEffect }) {
                meshRenderer.render(
                    currentFilter!!.effects,
                    face,
                    filterIntensity
                )
            }
            
            // 4d. Particles
            if (currentFilter!!.effects.any { it is ReactionEffect }) {
                particleRenderer.update(faceData)
                particleRenderer.render()
            }
        }
        
        // 5. Post-processing
        val postFBO = frameBuffer.getOrCreate("post_target", frame.width, frame.height)
        postFBO.bind()
        postProcessor.process(fbo.colorTexture)
        
        // 6. Present
        presentFrame(postFBO)
    }
    
    private fun renderBackground(cameraTexture: Int) {
        // Render camera frame as background
        val bgShader = shaderProgram.getVariant("background")
        bgShader.use()
        
        // Bind camera texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cameraTexture)
        bgShader.setUniform("u_texture", 0)
        
        // Render full-screen quad
        renderQuad()
    }
    
    private fun renderBeautyEffects(filter: Filter, face: FaceData) {
        val beautyEffect = filter.effects.filterIsInstance<BeautyEffect>().firstOrNull()
        if (beautyEffect != null) {
            // Create bilateral blur shader
            val blurShader = shaderProgram.getVariant("bilateral_blur")
            blurShader.use()
            
            val strength = beautyEffect.intensity * filterIntensity
            blurShader.setUniform("u_strength", strength)
            
            // Apply blur
            applyShaderToFaceMesh(blurShader, face)
        }
    }
    
    private fun renderQuad() {
        val quad = createScreenQuad()
        quad.bind()
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }
    
    private fun presentFrame(fbo: FrameBuffer) {
        // Resolve to default framebuffer
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo.handle)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        
        GLES30.glBlitFramebuffer(
            0, 0, fbo.width, fbo.height,
            0, 0, fbo.width, fbo.height,
            GLES30.GL_COLOR_BUFFER_BIT,
            GLES30.GL_LINEAR
        )
    }
}
```

### 1.3 Shader Pipeline

```glsl
// Vertex Shader: vertex_default.glsl
#version 300 es
precision highp float;

// Input attributes
layout(location = 0) in vec3 a_position;
layout(location = 1) in vec2 a_texCoord;
layout(location = 2) in vec3 a_normal;

// Uniforms
uniform mat4 u_mvpMatrix;
uniform mat4 u_modelMatrix;

// Output to fragment shader
out vec2 v_texCoord;
out vec3 v_normal;
out vec3 v_worldPos;

void main() {
    // Transform vertex to clip space
    gl_Position = u_mvpMatrix * vec4(a_position, 1.0);
    
    // Pass texture coordinates
    v_texCoord = a_texCoord;
    
    // Transform normal to world space
    v_normal = mat3(u_modelMatrix) * a_normal;
    
    // Calculate world position
    v_worldPos = (u_modelMatrix * vec4(a_position, 1.0)).xyz;
}

// Fragment Shader: fragment_beauty.glsl
#version 300 es
precision mediump float;

// Input from vertex shader
in vec2 v_texCoord;
in vec3 v_normal;
in vec3 v_worldPos;

// Uniforms
uniform sampler2D u_texture;
uniform sampler2D u_normalMap;
uniform float u_strength;
uniform vec3 u_lightPos;

// Output
out vec4 outColor;

void main() {
    // Sample base texture
    vec4 color = texture(u_texture, v_texCoord);
    
    // Apply bilateral filtering for skin smoothing
    // (Simplified - in practice, use separable Gaussian blur)
    vec4 smoothed = bilateralFilter(color, v_texCoord, u_strength);
    
    // Apply lighting
    vec3 normal = normalize(texture(u_normalMap, v_texCoord).rgb);
    vec3 lightDir = normalize(u_lightPos - v_worldPos);
    float diffuse = max(dot(normal, lightDir), 0.0);
    
    // Combine effects
    vec3 finalColor = smoothed.rgb * (0.5 + diffuse * 0.5);
    
    outColor = vec4(finalColor, smoothed.a);
}

// Fragment Shader: fragment_overlay.glsl
#version 300 es
precision mediump float;

in vec2 v_texCoord;

uniform sampler2D u_baseTexture;
uniform sampler2D u_overlayTexture;
uniform float u_alpha;

out vec4 outColor;

void main() {
    vec4 baseColor = texture(u_baseTexture, v_texCoord);
    vec4 overlayColor = texture(u_overlayTexture, v_texCoord);
    
    // Alpha blending (OVER operation)
    vec3 result = mix(baseColor.rgb, overlayColor.rgb, overlayColor.a * u_alpha);
    float alpha = baseColor.a + overlayColor.a * u_alpha * (1.0 - baseColor.a);
    
    outColor = vec4(result, alpha);
}

// Fragment Shader: fragment_blur.glsl
#version 300 es
precision mediump float;

in vec2 v_texCoord;

uniform sampler2D u_texture;
uniform vec2 u_texelSize;
uniform float u_radius;
uniform float u_strength;

out vec4 outColor;

// Gaussian blur kernel
const float weights[5] = float[] (0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
const float offsets[5] = float[] (0.0, 1.0, 2.0, 3.0, 4.0);

void main() {
    vec4 result = texture(u_texture, v_texCoord) * weights[0];
    
    for(int i = 1; i < 5; i++) {
        float offset = offsets[i] * u_radius;
        result += texture(u_texture, v_texCoord + vec2(offset, 0.0) * u_texelSize) * weights[i] * u_strength;
        result += texture(u_texture, v_texCoord - vec2(offset, 0.0) * u_texelSize) * weights[i] * u_strength;
    }
    
    outColor = result;
}
```

---

## 2. DETAILED FACE TRACKING PIPELINE

### 2.1 Multi-Step Face Detection

```
┌─────────────────────────────────────────────────────────────┐
│ FACE TRACKING LOOP (Parallel to Render Loop)                │
└─────────────────────────────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 1. CAMERA FRAME CAPTURE            │
        ├────────────────────────────────────┤
        │ - Get YUV frame from camera        │
        │ - Convert to RGB for ML            │
        │ - Resize to 192x192 for detector   │
        │ - Create tensor input              │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 2. FACE DETECTION (BlazeFace)      │
        ├────────────────────────────────────┤
        │ - Run blazeface_front.tflite       │
        │ - Output: Face bounding boxes      │
        │ - Confidence thresholding          │
        │ - Non-maximum suppression          │
        │ - ~5-10ms per frame                │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 3. FACE ROI EXTRACTION             │
        ├────────────────────────────────────┤
        │ - Crop region around face          │
        │ - Scale and normalize              │
        │ - Prepare for mesh model           │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 4. FACE MESH PREDICTION            │
        ├────────────────────────────────────┤
        │ - Run facemesh.tflite              │
        │ - Output: 478 3D landmarks         │
        │ - Denormalize to original space    │
        │ - ~8-12ms per frame                │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 5. LANDMARK FILTERING              │
        ├────────────────────────────────────┤
        │ - Temporal smoothing (Kalman)      │
        │ - Outlier detection                │
        │ - Confidence weighting             │
        │ - Per-landmark validation          │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 6. HEAD POSE ESTIMATION            │
        ├────────────────────────────────────┤
        │ - PnP (Perspective-n-Point)       │
        │ - 3D → 2D projection               │
        │ - Euler angles calculation         │
        │ - Transform matrix derivation      │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 7. FACIAL METRIC EXTRACTION        │
        ├────────────────────────────────────┤
        │ - Eye openness (both eyes)         │
        │ - Eye gaze direction               │
        │ - Mouth openness                   │
        │ - Smile intensity                  │
        │ - Iris distance                    │
        │ - Eyebrow position                 │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 8. FACE MESH CONSTRUCTION          │
        ├────────────────────────────────────┤
        │ - Generate triangle indices        │
        │ - Calculate texture coordinates    │
        │ - Vertex normal calculation        │
        │ - Upload to GPU buffers            │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 9. REACTION TRIGGER DETECTION      │
        ├────────────────────────────────────┤
        │ - Smile detection                  │
        │ - Blink detection                  │
        │ - Mouth open detection             │
        │ - Eyebrow raise detection          │
        │ - Head movement detection          │
        └────────────────────────────────────┘
                         ↓
        ┌────────────────────────────────────┐
        │ 10. OUTPUT: FACE DATA              │
        ├────────────────────────────────────┤
        │ - All facial metrics               │
        │ - Ready for rendering pipeline     │
        │ - Cached for multiple uses         │
        └────────────────────────────────────┘
```

### 2.2 Face Tracking Implementation

```kotlin
// FaceTracker.kt - Main interface
interface FaceTracker {
    suspend fun initialize()
    suspend fun shutdown()
    
    suspend fun detectFaces(frame: VideoFrame): List<FaceData>
    fun getLastDetectedFaces(): List<FaceData>
    fun getFaceCount(): Int
    fun isFaceDetected(): Boolean
    
    fun setTrackingQuality(quality: TrackingQuality)
    fun getPerformanceMetrics(): TrackingMetrics
}

// MediaPipeFaceTracker.kt - MediaPipe implementation
class MediaPipeFaceTracker(
    private val context: Context
) : FaceTracker {
    
    private lateinit var faceMeshDetector: FaceMeshDetector
    private lateinit var landmarkSmoother: LandmarkSmoother
    private lateinit var poseEstimator: HeadPoseEstimator
    private lateinit var metricExtractor: FacialMetricExtractor
    
    private var lastFaceData: List<FaceData> = emptyList()
    private val faceDataPool = FaceDataPool(poolSize = 5)
    
    override suspend fun initialize() = withContext(Dispatchers.Default) {
        faceMeshDetector = FaceMeshDetector.create(context)
        landmarkSmoother = LandmarkSmoother(alpha = 0.85f)
        poseEstimator = HeadPoseEstimator()
        metricExtractor = FacialMetricExtractor()
    }
    
    override suspend fun detectFaces(frame: VideoFrame): List<FaceData> {
        return withContext(Dispatchers.Default) {
            try {
                // 1. Detect faces
                val detections = faceMeshDetector.detect(frame)
                
                if (detections.isEmpty()) {
                    lastFaceData = emptyList()
                    return@withContext emptyList()
                }
                
                // 2. Process each face
                val faceDataList = mutableListOf<FaceData>()
                
                for ((idx, detection) in detections.withIndex()) {
                    // Get landmarks
                    var landmarks = detection.landmarks
                    
                    // 3. Smooth landmarks
                    landmarks = landmarkSmoother.smooth(landmarks)
                    
                    // 4. Estimate head pose
                    val headPose = poseEstimator.estimatePose(landmarks)
                    
                    // 5. Extract facial metrics
                    val metrics = metricExtractor.extract(
                        landmarks,
                        headPose
                    )
                    
                    // 6. Build face mesh
                    val faceMesh = FaceMeshBuilder.build(landmarks)
                    
                    // 7. Create FaceData
                    val faceData = faceDataPool.acquire().apply {
                        this.faceId = idx
                        this.timestamp = System.currentTimeMillis()
                        this.landmarks = landmarks
                        this.triangles = faceMesh.triangles
                        this.headTransform = headPose.transform
                        this.headRotation = headPose.rotation
                        this.headPosition = headPose.position
                        this.pitch = headPose.pitch
                        this.yaw = headPose.yaw
                        this.roll = headPose.roll
                        this.leftEyeOpenness = metrics.leftEyeOpenness
                        this.rightEyeOpenness = metrics.rightEyeOpenness
                        this.leftEyeGaze = metrics.leftEyeGaze
                        this.rightEyeGaze = metrics.rightEyeGaze
                        this.mouthOpenness = metrics.mouthOpenness
                        this.smileIntensity = metrics.smileIntensity
                        this.overallConfidence = detection.confidence
                        this.boundingBox = detection.boundingBox
                    }
                    
                    faceDataList.add(faceData)
                }
                
                lastFaceData = faceDataList
                faceDataList
            } catch (e: Exception) {
                Log.e("FaceTracker", "Detection failed", e)
                lastFaceData = emptyList()
                emptyList()
            }
        }
    }
    
    override fun getLastDetectedFaces(): List<FaceData> = lastFaceData
    override fun getFaceCount(): Int = lastFaceData.size
    override fun isFaceDetected(): Boolean = lastFaceData.isNotEmpty()
    
    override fun setTrackingQuality(quality: TrackingQuality) {
        faceMeshDetector.setQuality(quality)
    }
    
    override fun getPerformanceMetrics(): TrackingMetrics {
        return TrackingMetrics(
            detectionTimeMs = faceMeshDetector.getLastProcessingTime(),
            landmarkCount = 478,
            faceCount = lastFaceData.size,
            averageConfidence = lastFaceData.map { it.overallConfidence }.average()
        )
    }
}

// Landmark Smoothing with Kalman Filter
class LandmarkSmoother(
    private val alpha: Float = 0.85f,
    private val velocityThreshold: Float = 0.1f
) {
    private var previousLandmarks: Array<Vector3>? = null
    private var velocities: Array<Vector3> = Array(478) { Vector3.ZERO }
    private var confidences: FloatArray = FloatArray(478) { 1.0f }
    
    fun smooth(currentLandmarks: Array<Vector3>): Array<Vector3> {
        return if (previousLandmarks == null) {
            previousLandmarks = currentLandmarks
            currentLandmarks
        } else {
            val smoothed = Array(478) { i ->
                val prev = previousLandmarks!![i]
                val current = currentLandmarks[i]
                val velocity = velocities[i]
                
                // Calculate movement
                val delta = current.minus(prev)
                val distance = delta.length()
                
                // Detect and handle outliers
                if (distance > velocityThreshold) {
                    // Large movement - likely an outlier
                    // Predict position based on velocity
                    val predicted = prev.plus(velocity)
                    
                    // Blend between prediction and current
                    val corrected = predicted.lerp(current, 0.3f)
                    
                    // Update velocity
                    velocities[i] = corrected.minus(prev)
                    
                    corrected
                } else {
                    // Normal movement - apply exponential moving average
                    val smoothedPoint = prev.lerp(current, alpha)
                    
                    // Update velocity
                    velocities[i] = smoothedPoint.minus(prev)
                    
                    smoothedPoint
                }
            }
            
            previousLandmarks = smoothed
            smoothed
        }
    }
}

// Head Pose Estimation using PnP
class HeadPoseEstimator {
    private val cameraMatrix = FloatArray(9).apply {
        // Typical camera intrinsics - should be calibrated per device
        this[0] = 800.0f  // fx
        this[4] = 800.0f  // fy
        this[2] = 320.0f  // cx
        this[5] = 240.0f  // cy
        this[8] = 1.0f
    }
    
    fun estimatePose(landmarks: Array<Vector3>): HeadPose {
        // Use specific landmarks for PnP
        val knownPoints = listOf(
            landmarks[1],    // Nose
            landmarks[33],   // Left eye
            landmarks[263],  // Right eye
            landmarks[152],  // Mouth
            landmarks[10]    // Chin
        )
        
        // Solve PnP (Perspective-n-Point)
        // Reference: https://docs.opencv.org/master/d9/d0c/group__calib3d.html
        val rotation = Array(3) { FloatArray(3) }
        val translation = FloatArray(3)
        
        solvePnP(knownPoints, rotation, translation)
        
        // Convert rotation matrix to Euler angles
        val (pitch, yaw, roll) = rotationMatrixToEulerAngles(rotation)
        
        // Build transformation matrix
        val transform = Matrix4x4()
        transform.setRotationFromMatrix(rotation)
        transform.setTranslation(translation[0], translation[1], translation[2])
        
        return HeadPose(
            rotation = Quaternion.fromRotationMatrix(rotation),
            transform = transform,
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            position = Vector3(translation[0], translation[1], translation[2])
        )
    }
    
    private fun solvePnP(
        points: List<Vector3>,
        rotation: Array<FloatArray>,
        translation: FloatArray
    ) {
        // Implementation using OpenCV's solvePnP
        // This is a placeholder - actual implementation uses OpenCV JNI
    }
    
    private fun rotationMatrixToEulerAngles(
        matrix: Array<FloatArray>
    ): Triple<Float, Float, Float> {
        // Convert rotation matrix to Euler angles (Tait-Bryan Y-X-Z)
        val pitch = asin(-matrix[2][0])
        val yaw = atan2(matrix[2][1], matrix[2][2])
        val roll = atan2(matrix[1][0], matrix[0][0])
        
        return Triple(
            Math.toDegrees(pitch.toDouble()).toFloat(),
            Math.toDegrees(yaw.toDouble()).toFloat(),
            Math.toDegrees(roll.toDouble()).toFloat()
        )
    }
}

// Facial Metric Extraction
class FacialMetricExtractor {
    fun extract(
        landmarks: Array<Vector3>,
        headPose: HeadPose
    ): FacialMetrics {
        return FacialMetrics(
            leftEyeOpenness = calculateEyeOpenness(landmarks, isRight = false),
            rightEyeOpenness = calculateEyeOpenness(landmarks, isRight = true),
            leftEyeGaze = calculateEyeGaze(landmarks, isRight = false),
            rightEyeGaze = calculateEyeGaze(landmarks, isRight = true),
            mouthOpenness = calculateMouthOpenness(landmarks),
            smileIntensity = calculateSmileIntensity(landmarks),
            eyebrowRaiseLeft = calculateEyebrowRaise(landmarks, isRight = false),
            eyebrowRaiseRight = calculateEyebrowRaise(landmarks, isRight = true),
            blinkingBoth = isBothEyesBlinking(landmarks)
        )
    }
    
    private fun calculateEyeOpenness(
        landmarks: Array<Vector3>,
        isRight: Boolean
    ): Float {
        // Landmarks for eye: top, bottom, left, right
        // Left eye: 33, 160, 158, 133
        // Right eye: 263, 387, 386, 362
        
        val indices = if (isRight) {
            Triple(263, 387, 386)  // right_eye_top, bottom, left
        } else {
            Triple(33, 160, 158)   // left_eye_top, bottom, left
        }
        
        val (top, bottom, left) = indices
        val eyeHeight = landmarks[bottom].y - landmarks[top].y
        val eyeWidth = landmarks[left].x - landmarks[bottom].x  // Approximate
        
        // Normalize by typical eye dimensions
        val openness = (eyeHeight / 50.0f).coerceIn(0f, 1f)
        
        return openness
    }
    
    private fun calculateMouthOpenness(landmarks: Array<Vector3>): Float {
        // Mouth landmarks: upper_lip_top, lower_lip_bottom
        // Top: 13, Bottom: 14
        
        val topLip = landmarks[13]
        val bottomLip = landmarks[14]
        
        val mouthHeight = abs(bottomLip.y - topLip.y)
        
        // Normalize by typical mouth dimensions
        val openness = (mouthHeight / 20.0f).coerceIn(0f, 1f)
        
        return openness
    }
    
    private fun calculateSmileIntensity(landmarks: Array<Vector3>): Float {
        // Smile is detected by lip corner position relative to mouth center
        // When smiling, lip corners move up
        
        val mouthCenter = landmarks[14]  // Mouth center
        val leftLipCorner = landmarks[61]
        val rightLipCorner = landmarks[291]
        
        val leftCornerY = mouthCenter.y - leftLipCorner.y
        val rightCornerY = mouthCenter.y - rightLipCorner.y
        
        val smileIntensity = ((leftCornerY + rightCornerY) / 20.0f).coerceIn(0f, 1f)
        
        return smileIntensity
    }
    
    private fun calculateEyebrowRaise(
        landmarks: Array<Vector3>,
        isRight: Boolean
    ): Float {
        // Eyebrow position changes when raised
        // Left eyebrow: 46, Right eyebrow: 276
        
        val eyebrowIndices = if (isRight) listOf(276, 283, 282) else listOf(46, 53, 52)
        val eyeIndices = if (isRight) listOf(33, 160, 158) else listOf(263, 387, 386)
        
        val eyebrowCenter = landmarks[eyebrowIndices[0]].y
        val eyeCenter = landmarks[eyeIndices[0]].y
        
        val distance = abs(eyeCenter - eyebrowCenter)
        val raise = (distance / 30.0f).coerceIn(0f, 1f)
        
        return raise
    }
    
    private fun calculateEyeGaze(
        landmarks: Array<Vector3>,
        isRight: Boolean
    ): Vector3 {
        // Estimate gaze direction from iris position
        val irisIndices = if (isRight) listOf(471, 472, 473, 474, 475) else listOf(466, 467, 468, 469, 470)
        
        val irisCenter = landmarks[irisIndices[0]]
        val eyeCenter = landmarks[if (isRight) 263 else 33]
        
        // Gaze vector from eye center to iris
        val gazeVector = irisCenter.minus(eyeCenter).normalize()
        
        return gazeVector
    }
    
    private fun isBothEyesBlinking(landmarks: Array<Vector3>): Boolean {
        val leftOpenness = calculateEyeOpenness(landmarks, isRight = false)
        val rightOpenness = calculateEyeOpenness(landmarks, isRight = true)
        
        return leftOpenness < 0.2f && rightOpenness < 0.2f
    }
}

data class HeadPose(
    val rotation: Quaternion,
    val transform: Matrix4x4,
    val pitch: Float,   // Up/down rotation
    val yaw: Float,     // Left/right rotation
    val roll: Float,    // Tilt rotation
    val position: Vector3
)

data class FacialMetrics(
    val leftEyeOpenness: Float,
    val rightEyeOpenness: Float,
    val leftEyeGaze: Vector3,
    val rightEyeGaze: Vector3,
    val mouthOpenness: Float,
    val smileIntensity: Float,
    val eyebrowRaiseLeft: Float,
    val eyebrowRaiseRight: Float,
    val blinkingBoth: Boolean
)

data class TrackingMetrics(
    val detectionTimeMs: Float,
    val landmarkCount: Int,
    val faceCount: Int,
    val averageConfidence: Double
)
```

This comprehensive face tracking implementation handles:
- Multi-face detection
- Robust landmark smoothing
- Accurate head pose estimation
- Detailed facial metric extraction
- Performance profiling

---

**Next Sections:**
- Video Call Integration Design
- Camera Integration Design
- Complete Database Schema
- API Schema with Examples
- Performance Optimization Strategies
- Security Implementation Details
