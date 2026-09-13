package com.calcvault.storage.ingestion

import android.content.Context
import android.util.Log
import com.calcvault.crypto.E2EKeyManager
import com.calcvault.storage.ThumbnailGenerator
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BulkDataIngestionEngine(
    private val context: Context,
    private val storageEngine: USBStorageEngine,
    private val index: VaultFileIndex,
    private val thumbnailGen: ThumbnailGenerator
) {
    companion object {
        private const val TAG = "BulkDataIngestion"
        private const val CHUNK_SIZE = 4 * 1024 * 1024L // 4MB chunks
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_LEN = 128
    }

    data class IngestionProgress(
        val totalFiles: Int,
        val processedFiles: Int,
        val currentFile: String,
        val currentFileProgress: Float,
        val totalBytesProcessed: Long,
        val totalBytes: Long,
        val estimatedTimeRemaining: Long
    )

    var onProgress: ((IngestionProgress) -> Unit)? = null
    var onError: ((String, Exception) -> Unit)? = null

    suspend fun ingestDirectory(sourceDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!sourceDir.isDirectory) return@withContext false

        val files = sourceDir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return@withContext true

        val totalBytes = files.sumOf { it.length() }
        var processedBytes = 0L
        var processedCount = 0
        val startTime = System.currentTimeMillis()

        for (file in files) {
            try {
                val success = ingestFile(file)
                if (success) {
                    processedBytes += file.length()
                    processedCount++

                    val elapsed = System.currentTimeMillis() - startTime
                    val rate = if (elapsed > 0) processedBytes / elapsed else 0L
                    val remaining = if (rate > 0) (totalBytes - processedBytes) / rate else 0L

                    onProgress?.invoke(
                        IngestionProgress(
                            totalFiles = files.size,
                            processedFiles = processedCount,
                            currentFile = file.name,
                            currentFileProgress = 1f,
                            totalBytesProcessed = processedBytes,
                            totalBytes = totalBytes,
                            estimatedTimeRemaining = remaining
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error ingesting ${file.name}", e)
                onError?.invoke(file.name, e)
            }
        }

        true
    }

    suspend fun ingestFile(sourceFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.isFile) return@withContext false

        try {
            val fileId = UUID.randomUUID().toString()
            val fileHash = computeHash(sourceFile)

            // Check for duplicates
            if (index.fileExists(fileHash)) {
                Log.d(TAG, "File already exists in vault: ${sourceFile.name}")
                SecureFileDeleter().secureDelete(sourceFile)
                return@withContext true
            }

            val encryptedFile = File(context.filesDir, "encrypted/$fileId.enc")
            encryptedFile.parentFile?.mkdirs()

            // Stream encrypt the file
            val success = streamEncryptFile(sourceFile, encryptedFile)
            if (!success) {
                encryptedFile.delete()
                return@withContext false
            }

            // Generate thumbnail if applicable
            val thumbPath = if (thumbnailGen.supportsThumbnail(sourceFile)) {
                val thumbFile = File(context.filesDir, "thumbnails/$fileId.jpg")
                thumbFile.parentFile?.mkdirs()
                thumbnailGen.generateThumbnail(sourceFile, thumbFile)?.absolutePath
            } else {
                null
            }

            // Create index entry
            val vaultFile = VaultFile(
                id = fileId,
                name = sourceFile.name,
                size = sourceFile.length(),
                type = getFileType(sourceFile),
                encryptedPath = encryptedFile.absolutePath,
                thumbnailPath = thumbPath,
                createdAt = sourceFile.lastModified(),
                modifiedAt = sourceFile.lastModified(),
                hash = fileHash,
                mimeType = getMimeType(sourceFile)
            )

            val indexed = index.addFile(vaultFile)
            if (!indexed) {
                encryptedFile.delete()
                thumbPath?.let { File(it).delete() }
                return@withContext false
            }

            // Securely delete original
            SecureFileDeleter().secureDelete(sourceFile)
            Log.d(TAG, "Successfully ingested: ${sourceFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error ingesting file", e)
            onError?.invoke(sourceFile.name, e)
            false
        }
    }

    private suspend fun streamEncryptFile(sourceFile: File, encryptedFile: File): Boolean {
        return try {
            val keyManager = E2EKeyManager.getInstance()
            if (!keyManager.hasActiveSession()) {
                Log.e(TAG, "No active encryption session")
                return false
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
            val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
            val spec = GCMParameterSpec(GCM_TAG_LEN, iv)

            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            sourceFile.inputStream().use { input ->
                encryptedFile.outputStream().use { output ->
                    // Write IV
                    output.write(iv)

                    val buffer = ByteArray(CHUNK_SIZE.toInt())
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } > 0) {
                        val encrypted = cipher.update(buffer, 0, bytesRead)
                        if (encrypted != null) {
                            output.write(encrypted)
                        }
                    }

                    // Write final block with tag
                    val finalBlock = cipher.doFinal()
                    output.write(finalBlock)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting file", e)
            false
        }
    }

    suspend fun decryptFile(vaultFile: VaultFile, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(vaultFile.encryptedPath)
            if (!encryptedFile.exists()) return@withContext false

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")

            encryptedFile.inputStream().use { input ->
                // Read IV
                val iv = ByteArray(GCM_IV_LEN)
                if (input.read(iv) != GCM_IV_LEN) return@withContext false

                val spec = GCMParameterSpec(GCM_TAG_LEN, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)

                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(CHUNK_SIZE.toInt())
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } > 0) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        if (decrypted != null) {
                            output.write(decrypted)
                        }
                    }

                    val finalBlock = cipher.doFinal()
                    output.write(finalBlock)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting file", e)
            false
        }
    }

    private suspend fun computeHash(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE.toInt())
        var bytesRead: Int

        file.inputStream().use { input ->
            while (input.read(buffer).also { bytesRead = it } > 0) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getFileType(file: File): String {
        val ext = file.extension.lowercase()
        return when {
            ext in listOf("mp3", "wav", "aac", "m4a", "flac", "opus") -> "AUDIO"
            ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv") -> "VIDEO"
            ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> "IMAGE"
            ext in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx") -> "DOCUMENT"
            ext in listOf("zip", "rar", "7z", "tar", "gz") -> "ARCHIVE"
            else -> "OTHER"
        }
    }

    private fun getMimeType(file: File): String {
        return when (getFileType(file)) {
            "AUDIO" -> "audio/*"
            "VIDEO" -> "video/*"
            "IMAGE" -> "image/*"
            "DOCUMENT" -> "application/*"
            "ARCHIVE" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
