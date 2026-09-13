package com.calcvault.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.storage.MediaChunkManager
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Registered as a private share target for saving shared media into the vault.
 */
class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var mediaManager: MediaChunkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        mediaManager = MediaChunkManager(storageEngine, messageDB)

        if (storageEngine.state != USBStorageEngine.State.UNLOCKED) {
            toast("Vault locked — unlock first")
            finish()
            return
        }

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent)
            else -> finish()
        }
    }

    private fun handleSingleShare(intent: Intent) {
        val type = intent.type ?: run {
            finish()
            return
        }

        when {
            type.startsWith("image/") -> saveStreamExtra(intent, MediaChunkManager.MediaType.IMAGE, type)
            type.startsWith("video/") -> saveStreamExtra(intent, MediaChunkManager.MediaType.VIDEO, type)
            type.startsWith("audio/") -> saveStreamExtra(intent, MediaChunkManager.MediaType.AUDIO, type)
            type == "text/plain" -> saveTextNote(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            else -> {
                toast("File type not supported")
                finish()
            }
        }
    }

    private fun handleMultipleShare(intent: Intent) {
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        if (uris.isNullOrEmpty()) {
            finish()
            return
        }

        val type = intent.type ?: "application/octet-stream"
        val mediaType = when {
            type.startsWith("image/") -> MediaChunkManager.MediaType.IMAGE
            type.startsWith("video/") -> MediaChunkManager.MediaType.VIDEO
            type.startsWith("audio/") -> MediaChunkManager.MediaType.AUDIO
            else -> MediaChunkManager.MediaType.IMAGE
        }

        showProgress("Saving ${uris.size} files...")
        lifecycleScope.launch {
            var saved = 0
            for (uri in uris) {
                readUri(uri)?.let { bytes ->
                    mediaManager.storeMedia(bytes, mediaType, type)
                    saved++
                }
            }
            withContext(Dispatchers.Main) {
                toast("$saved file(s) saved to vault")
                finish()
            }
        }
    }

    private fun saveStreamExtra(intent: Intent, type: MediaChunkManager.MediaType, mimeType: String) {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) {
            finish()
        } else {
            saveMediaUri(uri, type, mimeType)
        }
    }

    private fun saveMediaUri(uri: Uri, type: MediaChunkManager.MediaType, mimeType: String) {
        showProgress("Encrypting and saving...")
        lifecycleScope.launch {
            val bytes = readUri(uri)
            if (bytes == null) {
                withContext(Dispatchers.Main) {
                    toast("Could not read file")
                    finish()
                }
                return@launch
            }

            mediaManager.storeMedia(bytes, type, mimeType)
            withContext(Dispatchers.Main) {
                toast("Saved to vault ✓")
                finish()
            }
        }
    }

    private fun saveTextNote(text: String) {
        if (text.isBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            messageDB.sendTextMessage("ME", "VAULT_NOTE", text)
            withContext(Dispatchers.Main) {
                toast("Note saved to vault ✓")
                finish()
            }
        }
    }

    private suspend fun readUri(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun showProgress(msg: String) {
        val tv = TextView(this).apply {
            text = msg
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
