package com.calcvault.ui.media

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.content.FileProvider
import com.calcvault.messaging.MessageRecord
import com.calcvault.storage.MediaChunkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object SecureMediaOpenHelper {

    data class MaterializedMedia(
        val file: File,
        val mimeType: String,
        val displayName: String,
        val ref: MediaChunkManager.MediaRef
    )

    suspend fun materialize(
        activity: Activity,
        mediaManager: MediaChunkManager,
        message: MessageRecord
    ): MaterializedMedia? = withContext(Dispatchers.IO) {
        val extra = runCatching { JSONObject(message.extra) }.getOrNull() ?: return@withContext null
        val mediaId = extra.optString("ref").toLongOrNull() ?: return@withContext null
        val ref = mediaManager.getMediaRef(mediaId) ?: return@withContext null

        val bytes = try {
            mediaManager.retrieveMedia(ref) ?: return@withContext null
        } catch (_: Exception) {
            return@withContext null
        }

        if (bytes.size.toLong() != ref.totalBytes) {
            bytes.fill(0)
            return@withContext null
        }

        try {
            val extension = extensionFor(ref.mimeType, ref.type)
            val cacheDir = File(activity.cacheDir, "secure_media").apply { mkdirs() }
            cleanupOldFiles(cacheDir)

            val file = try {
                File.createTempFile("cv_media_", extension, cacheDir)
            } catch (_: Exception) {
                bytes.fill(0)
                return@withContext null
            }

            try {
                file.writeBytes(bytes)
            } catch (_: Exception) {
                file.delete()
                bytes.fill(0)
                return@withContext null
            }

            bytes.fill(0)

            MaterializedMedia(
                file = file,
                mimeType = ref.mimeType.ifBlank { fallbackMime(ref.type) },
                displayName = ref.fileName.ifBlank {
                    extra.optString("name").ifBlank { "CalcVault$extension" }
                },
                ref = ref
            )
        } catch (_: Exception) {
            bytes.fill(0)
            null
        }
    }

    fun open(activity: Activity, media: MaterializedMedia) {
        when (media.ref.type) {
            MediaChunkManager.MediaType.IMAGE -> showImage(activity, media)
            MediaChunkManager.MediaType.VIDEO -> showVideo(activity, media)
            MediaChunkManager.MediaType.AUDIO -> showAudio(activity, media)
            MediaChunkManager.MediaType.FILE -> {
                // Check if file type has a built-in viewer
                if (canPreviewFile(media.mimeType)) {
                    showDocument(activity, media)
                } else {
                    showFileNotSupportedDialog(activity, media)
                }
            }
        }
    }

    private fun showPlaybackError(activity: Activity, dialog: Dialog, errorMessage: String) {
        android.util.Log.e("MediaPlayback", errorMessage)
        Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show()
        try {
            dialog.dismiss()
        } catch (_: Exception) {}
    }

    private fun showImage(activity: Activity, media: MaterializedMedia) {
        try {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            val imageBitmap = try {
                BitmapFactory.decodeFile(media.file.absolutePath)
            } catch (e: Exception) {
                android.util.Log.e("ImageViewer", "Failed to decode image", e)
                Toast.makeText(activity, "Failed to decode image: ${e.message}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return
            }

            if (imageBitmap == null) {
                android.util.Log.e("ImageViewer", "Image bitmap is null")
                Toast.makeText(activity, "Failed to load image", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return
            }

            val imageView = ImageView(activity).apply {
                setBackgroundColor(0xFF000000.toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(imageBitmap)
                setOnClickListener {
                    dialog.dismiss()
                }
            }

            dialog.setContentView(imageView)
            dialog.setOnDismissListener {
                try {
                    imageBitmap.recycle()
                } catch (_: Exception) {}
                try {
                    media.file.delete()
                } catch (_: Exception) {}
                android.util.Log.i("ImageViewer", "Image viewer closed and cleaned up")
            }
            dialog.show()
            android.util.Log.i("ImageViewer", "Image displayed successfully")
        } catch (e: Exception) {
            android.util.Log.e("ImageViewer", "Fatal error in showImage", e)
            Toast.makeText(activity, "Image viewer failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVideo(activity: Activity, media: MaterializedMedia) {
        try {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.window?.apply {
                setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            val container = android.widget.FrameLayout(activity).apply {
                setBackgroundColor(0xFF000000.toInt())
            }

            val videoView = VideoView(activity).apply {
                setBackgroundColor(0xFF000000.toInt())
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )

                val mediaController = MediaController(activity).apply {
                    setAnchorView(this@apply)
                }
                setMediaController(mediaController)

                var isInitialized = false
                var retryCount = 0
                val maxRetries = 3

                setOnPreparedListener { player ->
                    if (!isInitialized) {
                        isInitialized = true
                        player.isLooping = false
                        try {
                            start()
                            android.util.Log.i("MediaPlayback", "Video playback started successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("MediaPlayback", "Error starting video playback", e)
                            showPlaybackError(activity, dialog, "Failed to start playback: ${e.message}")
                        }
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    if (retryCount < maxRetries && what == android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                        retryCount++
                        android.util.Log.w("MediaPlayback", "Retry $retryCount/$maxRetries after server error")
                        try {
                            resume()
                        } catch (e: Exception) {
                            android.util.Log.e("MediaPlayback", "Retry failed", e)
                        }
                        false // Continue handling
                    } else {
                        val errorMsg = when (what) {
                            android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown playback error"
                            android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Server error (codec crashed)"
                            android.media.MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> "Format not supported for streaming"
                            -1 -> "Playback error: $extra"
                            else -> "Playback error code $what"
                        }
                        showPlaybackError(activity, dialog, errorMsg)
                        true
                    }
                }

                setOnCompletionListener {
                    android.util.Log.i("MediaPlayback", "Video playback completed")
                }
            }

            container.addView(videoView)
            dialog.setContentView(container)

            try {
                val uri = Uri.fromFile(media.file)
                videoView.setVideoURI(uri)
                android.util.Log.i("MediaPlayback", "Video URI set: ${media.file.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayback", "Failed to set video URI", e)
                showPlaybackError(activity, dialog, "Failed to load video: ${e.message}")
            }

            dialog.setOnDismissListener {
                try {
                    videoView.stopPlayback()
                    android.util.Log.i("MediaPlayback", "Video stopped and cleaned up")
                } catch (_: Exception) {}
                try {
                    media.file.delete()
                } catch (_: Exception) {}
            }
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("MediaPlayback", "Fatal error in showVideo", e)
            Toast.makeText(activity, "Video playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAudio(activity: Activity, media: MaterializedMedia) {
        try {
            val dialog = Dialog(activity)
            dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            val player = MediaPlayer()
            var progressThread: Thread? = null
            var isPlayerReady = false
            var isPlayerPlaying = false
            var retryCount = 0
            val maxRetries = 3

            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                setBackgroundColor(0xFF222222.toInt())
            }

            val title = TextView(activity).apply {
                text = media.displayName
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, 24)
            }
            root.addView(title)

            val seekBar = SeekBar(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            root.addView(seekBar)

            val timeDisplay = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 12 }
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvCurrentTime = TextView(activity).apply {
                text = "0:00"
                textSize = 12f
                setTextColor(0xFFAAAAAA.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            timeDisplay.addView(tvCurrentTime)

            val tvDuration = TextView(activity).apply {
                text = "0:00"
                textSize = 12f
                setTextColor(0xFFAAAAAA.toInt())
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            timeDisplay.addView(tvDuration)
            root.addView(timeDisplay)

            val controls = LinearLayout(activity).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 24 }
            }

            val toggle = Button(activity).apply {
                text = "Play"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.rightMargin = 12 }
            }
            controls.addView(toggle)

            val btnClose = Button(activity).apply {
                text = "Close"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.leftMargin = 12 }
            }
            controls.addView(btnClose)
            root.addView(controls)

            dialog.setContentView(root)

            fun formatTime(ms: Int): String {
                val totalSeconds = ms / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                return String.format("%d:%02d", minutes, seconds)
            }

            // Prepare audio player with async callback
            try {
                player.setDataSource(media.file.absolutePath)
                player.setOnPreparedListener { preparedPlayer ->
                    isPlayerReady = true
                    val duration = preparedPlayer.duration
                    seekBar.max = duration
                    tvDuration.text = formatTime(duration)
                    toggle.isEnabled = true
                    android.util.Log.i("AudioPlayback", "Audio prepared: $duration ms")
                }
                player.setOnErrorListener { _, what, extra ->
                    if (retryCount < maxRetries && what == android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                        retryCount++
                        android.util.Log.w("AudioPlayback", "Retry $retryCount/$maxRetries")
                        try {
                            player.prepareAsync()
                        } catch (e: Exception) {
                            android.util.Log.e("AudioPlayback", "Retry failed", e)
                        }
                        false
                    } else {
                        val msg = "Error: $what"
                        android.util.Log.e("AudioPlayback", msg)
                        Toast.makeText(activity, "Playback error: $msg", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                player.prepareAsync()
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayback", "Failed to load audio", e)
                Toast.makeText(activity, "Cannot load audio: ${e.message}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return
            }

            toggle.isEnabled = false

            toggle.setOnClickListener {
                if (!isPlayerReady) return@setOnClickListener
                try {
                    if (player.isPlaying) {
                        player.pause()
                        isPlayerPlaying = false
                        toggle.text = "Play"
                        android.util.Log.i("AudioPlayback", "Paused")
                    } else {
                        player.start()
                        isPlayerPlaying = true
                        toggle.text = "Pause"
                        android.util.Log.i("AudioPlayback", "Started")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayback", "Playback toggle error", e)
                    Toast.makeText(activity, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && isPlayerReady) {
                        try {
                            player.seekTo(progress)
                            tvCurrentTime.text = formatTime(progress)
                            android.util.Log.d("AudioPlayback", "Seeked to $progress")
                        } catch (e: Exception) {
                            android.util.Log.e("AudioPlayback", "Seek error", e)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })

            player.setOnCompletionListener {
                isPlayerPlaying = false
                toggle.text = "Play"
                seekBar.progress = seekBar.max
                tvCurrentTime.text = formatTime(seekBar.max)
                android.util.Log.i("AudioPlayback", "Playback completed")
            }

            dialog.setOnShowListener {
                progressThread = Thread {
                    try {
                        while (!Thread.currentThread().isInterrupted && isPlayerReady) {
                            if (isPlayerPlaying) {
                                try {
                                    val currentPos = player.currentPosition
                                    seekBar.post {
                                        seekBar.progress = currentPos
                                        tvCurrentTime.text = formatTime(currentPos)
                                    }
                                } catch (e: Exception) {
                                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                                }
                            }
                            Thread.sleep(250)
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }.also { it.start() }
            }

            dialog.setOnDismissListener {
                progressThread?.interrupt()
                try {
                    progressThread?.join(1000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }

                try {
                    if (player.isPlaying) player.stop()
                } catch (_: Exception) {}

                try {
                    player.release()
                } catch (_: Exception) {}

                try {
                    media.file.delete()
                } catch (_: Exception) {}

                android.util.Log.i("AudioPlayback", "Dialog closed and cleaned up")
            }

            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayback", "Fatal error in showAudio", e)
            Toast.makeText(activity, "Audio playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFile(activity: Activity, media: MaterializedMedia) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            media.file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, media.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            activity.startActivity(Intent.createChooser(intent, media.displayName))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "No compatible app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupOldFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun extensionFor(mimeType: String, type: MediaChunkManager.MediaType): String {
        val lowercaseMime = mimeType.lowercase()

        // Exact MIME type matching with proper extensions
        return when {
            // Images
            lowercaseMime.contains("jpeg") || lowercaseMime.contains("jpg") -> ".jpg"
            lowercaseMime.contains("png") -> ".png"
            lowercaseMime.contains("gif") -> ".gif"
            lowercaseMime.contains("webp") -> ".webp"
            lowercaseMime.contains("bmp") -> ".bmp"

            // Videos
            lowercaseMime.contains("mp4") -> ".mp4"
            lowercaseMime.contains("webm") -> ".webm"
            lowercaseMime.contains("mpeg") && lowercaseMime.contains("video") -> ".mpeg"
            lowercaseMime.contains("quicktime") || lowercaseMime.contains("mov") -> ".mov"
            lowercaseMime.contains("matroska") || lowercaseMime.contains("mkv") -> ".mkv"
            lowercaseMime.contains("3gpp") -> ".3gp"

            // Audio
            lowercaseMime.contains("mpeg") && lowercaseMime.contains("audio") -> ".mp3"
            lowercaseMime.contains("mp4") && lowercaseMime.contains("audio") -> ".m4a"
            lowercaseMime.contains("ogg") -> ".ogg"
            lowercaseMime.contains("wav") -> ".wav"
            lowercaseMime.contains("flac") -> ".flac"
            lowercaseMime.contains("aac") -> ".aac"
            lowercaseMime.contains("opus") -> ".opus"

            // Documents
            lowercaseMime.contains("pdf") -> ".pdf"
            lowercaseMime.contains("word") || lowercaseMime.contains("document") -> ".docx"
            lowercaseMime.contains("sheet") -> ".xlsx"
            lowercaseMime.contains("presentation") -> ".pptx"
            lowercaseMime.contains("plain") -> ".txt"
            lowercaseMime.contains("json") -> ".json"
            lowercaseMime.contains("xml") -> ".xml"
            lowercaseMime.contains("zip") || lowercaseMime.contains("compressed") -> ".zip"

            // Fallback by type
            type == MediaChunkManager.MediaType.IMAGE -> ".jpg"
            type == MediaChunkManager.MediaType.VIDEO -> ".mp4"
            type == MediaChunkManager.MediaType.AUDIO -> ".m4a"

            // Generic fallback
            else -> ".bin"
        }
    }

    private fun fallbackMime(type: MediaChunkManager.MediaType): String {
        return when (type) {
            MediaChunkManager.MediaType.IMAGE -> "image/jpeg"
            MediaChunkManager.MediaType.VIDEO -> "video/mp4"
            MediaChunkManager.MediaType.AUDIO -> "audio/mp4"
            MediaChunkManager.MediaType.FILE -> "application/octet-stream"
        }
    }

    private fun canPreviewFile(mimeType: String): Boolean {
        val lowercaseMime = mimeType.lowercase()
        // PDF and text files can be previewed
        return lowercaseMime.contains("pdf") || 
               lowercaseMime.contains("text") || 
               lowercaseMime.contains("plain")
    }

    private fun showDocument(activity: Activity, media: MaterializedMedia) {
        try {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            if (media.mimeType.lowercase().contains("pdf")) {
                // For PDF, show a message that PDF viewer is not available
                val text = TextView(activity).apply {
                    text = "PDF Preview Not Available\n\nThis file type cannot be previewed in the app.\n\nFile: ${media.displayName}"
                    textSize = 16f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(48, 48, 48, 48)
                    setBackgroundColor(0xFF000000.toInt())
                }
                dialog.setContentView(text)
            } else {
                // For text files, show content
                val content = media.file.readText()
                val textView = TextView(activity).apply {
                    text = content
                    textSize = 14f
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF1A1A1A.toInt())
                    setPadding(24, 24, 24, 24)
                    isVerticalScrollBarEnabled = true
                }
                val scroll = ScrollView(activity).apply {
                    setBackgroundColor(0xFF000000.toInt())
                    addView(textView)
                }
                dialog.setContentView(scroll)
            }

            dialog.setOnDismissListener {
                try {
                    media.file.delete()
                } catch (_: Exception) {}
            }
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("DocumentViewer", "Failed to show document", e)
            Toast.makeText(activity, "Failed to open document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileNotSupportedDialog(activity: Activity, media: MaterializedMedia) {
        try {
            android.app.AlertDialog.Builder(activity)
                .setTitle("File Cannot Be Previewed")
                .setMessage("The file type '${media.mimeType}' is not supported for preview in CalcVault.\n\nFile: ${media.displayName}")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    try {
                        media.file.delete()
                    } catch (_: Exception) {}
                }
                .show()
        } catch (e: Exception) {
            android.util.Log.e("FileNotSupported", "Failed to show dialog", e)
            Toast.makeText(activity, "File type not supported for preview", Toast.LENGTH_SHORT).show()
        }
    }
}

