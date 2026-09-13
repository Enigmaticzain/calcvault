package com.calcvault.ui.media

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.calcvault.ui.filters.VideoFilterEngine
import com.calcvault.ui.filters.FilterControlPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SnapCameraActivity - In-app camera with real-time AR filters
 *
 * Features:
 * - Live camera preview with CameraX
 * - Real-time emoji showers and visual effects
 * - Photo capture with applied filters
 * - Filter control panel
 * - Flashlight support
 */
class SnapCameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var filterControlPanel: FilterControlPanel
    private lateinit var overlayTexture: TextureView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var filterEngine: VideoFilterEngine
    
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isFlashOn = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Build UI
        buildUI()
        
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize filter engine with TextureView overlay
        overlayTexture = TextureView(this)
        filterEngine = VideoFilterEngine(this, overlayTexture)

        // Request permissions and start camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)

        // Camera preview
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        // Overlay for filter effects (TextureView for VideoFilterEngine)
        overlayTexture = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(overlayTexture)

        // Control panel at bottom
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
            }
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            setPadding(16, 16, 16, 16)
        }
        root.addView(bottomPanel)

        // Filter control panel
        filterControlPanel = FilterControlPanel(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            onEmojiTrigger = { emoji ->
                filterEngine.triggerEmojiShower(emoji, 1.5f)
            }
            
            onFilterSelected = { filterType, intensity ->
                filterEngine.applyOverlayFilter(filterType, 0, intensity)
            }
            
            onClearEffects = {
                filterEngine.clearAllEffects()
            }
        }
        bottomPanel.addView(filterControlPanel)

        // Action buttons
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        bottomPanel.addView(buttonRow)

        // Flash button
        val flashBtn = Button(this).apply {
            text = "💡"
            layoutParams = LinearLayout.LayoutParams(64, 64).apply { marginEnd = 8 }
            setBackgroundColor(Color.argb(150, 255, 255, 255))
            setTextColor(Color.BLACK)
            setOnClickListener { toggleFlash() }
        }
        buttonRow.addView(flashBtn)

        // Capture button (large center button)
        val captureBtn = Button(this).apply {
            text = "📷"
            layoutParams = LinearLayout.LayoutParams(
                120,
                120
            )
            setBackgroundColor(Color.argb(220, 100, 200, 255))
            setTextColor(Color.WHITE)
            textSize = 28f
            setOnClickListener { capturePhoto() }
        }
        buttonRow.addView(captureBtn)

        // Close button
        val closeBtn = Button(this).apply {
            text = "✕"
            layoutParams = LinearLayout.LayoutParams(64, 64).apply { marginStart = 8 }
            setBackgroundColor(Color.argb(150, 255, 100, 100))
            setTextColor(Color.WHITE)
            textSize = 24f
            setOnClickListener { finish() }
        }
        buttonRow.addView(closeBtn)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "snap_$name.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@SnapCameraActivity, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@SnapCameraActivity, "✓ Snap saved!", Toast.LENGTH_SHORT).show()
                    
                    activityScope.launch {
                        Thread.sleep(500)
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("snap_uri", output.savedUri.toString())
                        })
                        finish()
                    }
                }
            }
        )
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        imageCapture?.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        Toast.makeText(this, if (isFlashOn) "💡 Flash ON" else "💡 Flash OFF", Toast.LENGTH_SHORT).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        filterEngine.destroy()
    }
}
