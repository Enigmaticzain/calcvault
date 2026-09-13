package com.calcvault.storage.ingestion

import android.util.Log
import java.io.File
import java.security.SecureRandom

class SecureFileDeleter {
    companion object {
        private const val TAG = "SecureFileDeleter"
        private const val OVERWRITE_PASSES = 3
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB
    }

    fun secureDelete(file: File): Boolean {
        if (!file.exists()) return true

        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { secureDelete(it) }
                file.delete()
            } else {
                secureDeleteFile(file)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error securely deleting ${file.absolutePath}", e)
            false
        }
    }

    private fun secureDeleteFile(file: File): Boolean {
        val size = file.length()
        if (size == 0L) return file.delete()

        val random = SecureRandom()
        val buffer = ByteArray(BUFFER_SIZE.coerceAtMost(size.toInt()))

        repeat(OVERWRITE_PASSES) {
            random.nextBytes(buffer)
            file.outputStream().use { output ->
                var remaining = size
                while (remaining > 0) {
                    val toWrite = buffer.size.coerceAtMost(remaining.toInt())
                    output.write(buffer, 0, toWrite)
                    remaining -= toWrite
                }
            }
        }

        return file.delete()
    }
}
