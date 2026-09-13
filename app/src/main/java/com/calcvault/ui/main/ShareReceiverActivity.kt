package com.calcvault.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.storage.MediaChunkManager
import com.calcvault.storage.USBStorageEngine
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ShareReceiverActivity
 *
 * Handles files shared from other Android apps via the share sheet.
 * e.g. user opens a photo in Gallery, taps Share, selects CalcVault.
 *
 * Flow:
 *  1. Check vault is open (session active)
 *  2. If not → show "Unlock first" toast → exit
 *  3. Read shared file from URI into RAM (never touch disk directly)
 *  4. Encrypt → store in USB via MediaChunkManager
 *  5. Show "Saved ✓" toast
 *
 * Privacy: shared content goes DIRECTLY to encrypted USB.
 * Never written to phone storage in plaintext.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check vault is open
        if (!SessionManager.isVaultOpen) {
            toast(getString(com.calcvault.R.string.share_vault_locked))
            finish()
            return
        }

        val action = intent?.action
        val type = intent?.type ?: ""

        when {
            action == Intent.ACTION_SEND && type.startsWith("image/") ->
                handleSingleImage(intent)

            action == Intent.ACTION_SEND && type.startsWith("video/") ->
                handleSingleVideo(intent)

            action == Intent.ACTION_SEND && type.startsWith("audio/") ->
                handleSingleAudio(intent)

            action == Intent.ACTION_SEND_MULTIPLE ->
                handleMultiple(intent)

            action == Intent.ACTION_SEND ->
                handleGenericFile(intent)

            else -> {
                toast(getString(com.calcvault.R.string.share_unsupported))
                finish()
            }
        }
    }

    private fun handleSingleImage(intent: Intent?) {
        val uri = intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: run {
            finish(); return
        }
        saveToVault(uri, MediaChunkManager.MediaType.IMAGE)
    }

    private fun handleSingleVideo(intent: Intent?) {
        val uri = intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: run {
            finish(); return
        }
        saveToVault(uri, MediaChunkManager.MediaType.VIDEO)
    }

    private fun handleSingleAudio(intent: Intent?) {
        val uri = intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: run {
            finish(); return
        }
        saveToVault(uri, MediaChunkManager.MediaType.AUDIO)
    }

    private fun handleMultiple(intent: Intent?) {
        val uris = intent?.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: run {
            finish(); return
        }
        for (uri in uris) saveToVault(uri, guessType(intent.type ?: ""))
    }

    private fun handleGenericFile(intent: Intent?) {
        val uri = intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: run {
            finish(); return
        }
        saveToVault(uri, MediaChunkManager.MediaType.FILE)
    }

    private fun saveToVault(uri: Uri, type: MediaChunkManager.MediaType) {
        toast(getString(com.calcvault.R.string.share_encrypting))

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val storageEngine = USBStorageEngine.getInstance(this@ShareReceiverActivity)
                    val messageDB = AppendOnlyMessageDB(storageEngine)
                    val mediaManager = MediaChunkManager(storageEngine, messageDB)

                    // Read file bytes into RAM via ContentResolver
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: return@withContext false

                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val fileName = resolveDisplayName(uri)

                    // Encrypt + store on USB
                    val ref = mediaManager.storeMedia(
                        rawBytes = bytes,
                        type = type,
                        mimeType = mimeType,
                        ownership = MediaChunkManager.MediaOwnership.MINE,
                        fileName = fileName
                    )

                    // Save message record pointing to the media
                    messageDB.sendMediaMessage(
                        from = SessionManager.localUserId,
                        to = SessionManager.partnerUserId,
                        type = when (type) {
                            MediaChunkManager.MediaType.IMAGE -> AppendOnlyMessageDB.MSG_IMAGE
                            MediaChunkManager.MediaType.VIDEO -> AppendOnlyMessageDB.MSG_VIDEO
                            MediaChunkManager.MediaType.AUDIO -> AppendOnlyMessageDB.MSG_AUDIO
                            MediaChunkManager.MediaType.FILE -> AppendOnlyMessageDB.MSG_FILE
                        },
                        mediaRef = ref.mediaId.toString(),
                        thumbHash = ref.thumbHash,
                        mimeType = mimeType,
                        fileName = fileName
                    )

                    // Secure wipe of bytes from RAM
                    bytes.fill(0)
                    true
                } catch (e: Exception) {
                    false
                }
            }

            toast(
                if (success) {
                    getString(com.calcvault.R.string.share_saved)
                } else {
                    getString(com.calcvault.R.string.error_usb_not_found)
                }
            )
            finish()
        }
    }

    private fun guessType(mimeType: String) = when {
        mimeType.startsWith("image/") -> MediaChunkManager.MediaType.IMAGE
        mimeType.startsWith("video/") -> MediaChunkManager.MediaType.VIDEO
        mimeType.startsWith("audio/") -> MediaChunkManager.MediaType.AUDIO
        else -> MediaChunkManager.MediaType.FILE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun resolveDisplayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault(uri.lastPathSegment.orEmpty())
    }
}
