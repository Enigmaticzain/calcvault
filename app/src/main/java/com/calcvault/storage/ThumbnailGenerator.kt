package com.calcvault.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ThumbnailGenerator
 *
 * Generates thumbnails for media files for UI display.
 * Supports:
 * - Video: First frame
 * - Images: Compressed preview
 * - Audio: Waveform or icon
 */
class ThumbnailGenerator(private val context: Context) {

    companion object {
        private const val TAG = "ThumbnailGenerator"

        // Thumbnail dimensions
        const val THUMBNAIL_WIDTH = 200
        const val THUMBNAIL_HEIGHT = 200

        // Quality (0-100)
        const val THUMBNAIL_QUALITY = 80

        // File type extensions
        const val AUDIO_EXTS = "mp3,wav,aac,m4a,flac,opus"
        const val VIDEO_EXTS = "mp4,mkv,avi,mov,webm,flv"
        const val IMAGE_EXTS = "jpg,jpeg,png,webp,gif,bmp"
    }

    /**
     * Generate thumbnail for a file
     */
    suspend fun generateThumbnail(
        sourceFile: File,
        outputFile: File? = null
    ): File? = withContext(Dispatchers.Default) {
        try {
            val fileType = getFileType(sourceFile.extension)

            val thumbnail = when (fileType) {
                FileType.VIDEO -> generateVideoThumbnail(sourceFile)
                FileType.IMAGE -> generateImageThumbnail(sourceFile)
                FileType.AUDIO -> generateAudioThumbnail(sourceFile)
                else -> generateGenericThumbnail(sourceFile)
            }

            if (thumbnail != null && outputFile != null) {
                saveThumbnail(thumbnail, outputFile)
            }

            thumbnail?.recycle()
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error generating thumbnail for ${sourceFile.name}", e)
            null
        }
    }

    /**
     * Generate thumbnail from video file (first frame)
     */
    private fun generateVideoThumbnail(videoFile: File): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.frameAtTime
            retriever.release()

            bitmap?.let { resizeBitmap(it, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) }
        } catch (e: Exception) {
            Log.w(TAG, "Error generating video thumbnail", e)
            generatePlayIconBitmap()
        }
    }

    /**
     * Generate thumbnail from image file (compressed preview)
     */
    private fun generateImageThumbnail(imageFile: File): Bitmap? {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
            bitmap?.let { resizeBitmap(it, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) }
        } catch (e: Exception) {
            Log.w(TAG, "Error generating image thumbnail", e)
            null
        }
    }

    /**
     * Generate thumbnail for audio file (waveform or icon)
     */
    private fun generateAudioThumbnail(audioFile: File): Bitmap? {
        return try {
            // For now, generate a generic audio icon with waveform pattern
            generateAudioWaveformBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Error generating audio thumbnail", e)
            generateAudioIconBitmap()
        }
    }

    /**
     * Generate generic thumbnail for unknown file types
     */
    private fun generateGenericThumbnail(file: File): Bitmap {
        val ext = file.extension.uppercase()
        return createTextBitmap(ext, Color.GRAY)
    }

    // ─── Bitmap Utilities ──────────────────────────────────────────────────

    /**
     * Resize bitmap to target dimensions
     */
    private fun resizeBitmap(source: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * Create play icon bitmap (for videos)
     */
    private fun generatePlayIconBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.argb(200, 0, 0, 0))

        // Play button triangle
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        val cx = THUMBNAIL_WIDTH / 2f
        val cy = THUMBNAIL_HEIGHT / 2f
        val size = 40f

        val path = android.graphics.Path().apply {
            moveTo(cx - size, cy - size)
            lineTo(cx - size, cy + size)
            lineTo(cx + size, cy)
            close()
        }
        canvas.drawPath(path, paint)

        return bitmap
    }

    /**
     * Create audio icon bitmap
     */
    private fun generateAudioIconBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient-like color
        canvas.drawColor(Color.argb(200, 63, 81, 181))

        // Music icon
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        val cx = THUMBNAIL_WIDTH / 2f
        val cy = THUMBNAIL_HEIGHT / 2f

        // Two rounded rectangles for speaker
        canvas.drawRect(cx - 30f, cy - 30f, cx - 10f, cy + 30f, paint)
        canvas.drawRect(cx + 10f, cy - 30f, cx + 30f, cy + 30f, paint)

        // Lines for sound
        val linePaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawArc(cx - 50f, cy - 50f, cx + 50f, cy + 50f, 45f, 45f, false, linePaint)

        return bitmap
    }

    /**
     * Create audio waveform bitmap
     */
    private fun generateAudioWaveformBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.argb(200, 76, 175, 80))

        // Waveform bars
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }

        val barWidth = 6
        val bars = THUMBNAIL_WIDTH / (barWidth + 2)
        val centerY = THUMBNAIL_HEIGHT / 2f

        for (i in 0 until bars) {
            val x = (barWidth + 2) * i + barWidth / 2f
            val heightVariation = (Math.sin(i * 0.3) * 30 + 40).toFloat()
            val y1 = centerY - heightVariation
            val y2 = centerY + heightVariation

            canvas.drawLine(x, y1, x, y2, paint)
        }

        return bitmap
    }

    /**
     * Create text bitmap for file type
     */
    private fun createTextBitmap(text: String, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(color)

        // Text
        val paint = Paint().apply {
            this.color = Color.WHITE
            isAntiAlias = true
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(
            text.take(3),
            THUMBNAIL_WIDTH / 2f,
            (THUMBNAIL_HEIGHT / 2f) + 8,
            paint
        )

        return bitmap
    }

    /**
     * Save bitmap to file
     */
    private fun saveThumbnail(bitmap: Bitmap, file: File): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, output)
            }
            Log.d(TAG, "Thumbnail saved: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving thumbnail", e)
            false
        }
    }

    // ─── File Type Detection ───────────────────────────────────────────────

    enum class FileType {
        AUDIO, VIDEO, IMAGE, DOCUMENT, ARCHIVE, UNKNOWN
    }

    private fun getFileType(extension: String): FileType {
        val ext = extension.lowercase()

        return when {
            AUDIO_EXTS.contains(ext) -> FileType.AUDIO
            VIDEO_EXTS.contains(ext) -> FileType.VIDEO
            IMAGE_EXTS.contains(ext) -> FileType.IMAGE
            listOf("pdf", "doc", "docx", "txt", "xls").contains(ext) -> FileType.DOCUMENT
            listOf("zip", "rar", "7z", "tar", "gz").contains(ext) -> FileType.ARCHIVE
            else -> FileType.UNKNOWN
        }
    }

    /**
     * Check if file type supports thumbnails
     */
    fun supportsThumbnail(file: File): Boolean {
        return getFileType(file.extension) in listOf(
            FileType.AUDIO,
            FileType.VIDEO,
            FileType.IMAGE
        )
    }

    /**
     * Get MIME type for file
     */
    fun getMimeType(file: File): String {
        return when (getFileType(file.extension)) {
            FileType.AUDIO -> "audio/*"
            FileType.VIDEO -> "video/*"
            FileType.IMAGE -> "image/*"
            FileType.DOCUMENT -> "application/*"
            FileType.ARCHIVE -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
