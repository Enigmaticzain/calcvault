package com.calcvault.companion

import com.calcvault.storage.USBStorageEngine
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class CompanionBackupManager(private val storageEngine: USBStorageEngine) {

    fun streamEncryptedBackup(emitChunk: (ByteArray) -> Unit): JSONObject {
        val file = storageEngine.getAttachedContainerFile()
            ?: throw IllegalStateException("container_not_available")

        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(CompanionBridgeConstants.CHUNK_SIZE_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                totalBytes += read.toLong()
                emitChunk(buffer.copyOf(read))
            }
        }

        return JSONObject()
            .put("backupVersion", 1)
            .put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
            .put("size", totalBytes)
            .put("snapshotTs", System.currentTimeMillis())
    }

    fun createRestoreCollector(requestId: String, baseDir: File): CompanionRestoreCollector {
        if (!baseDir.exists()) baseDir.mkdirs()
        val file = File(baseDir, "restore_${requestId}_${System.currentTimeMillis()}.tmp")
        return CompanionRestoreCollector(file)
    }

    suspend fun finalizeRestore(
        collector: CompanionRestoreCollector,
        expectedSha256: String?
    ): JSONObject {
        val file = collector.finish()
        val restored = storageEngine.restoreContainerFromBackup(file, expectedSha256)
        val response = JSONObject()
            .put("restoredAt", System.currentTimeMillis())
            .put("bytes", collector.totalBytes)
            .put("digest", collector.sha256Hex)

        file.delete()

        if (!restored) {
            throw IllegalStateException("restore_validation_failed")
        }
        return response
    }
}

class CompanionRestoreCollector(private val tempFile: File) {

    private val digest = MessageDigest.getInstance("SHA-256")
    private val output = FileOutputStream(tempFile)
    private var finalizedDigestHex: String? = null
    var totalBytes: Long = 0L
        private set

    val sha256Hex: String
        get() = finalizedDigestHex ?: throw IllegalStateException("collector_not_finalized")

    fun appendChunk(data: ByteArray) {
        output.write(data)
        digest.update(data)
        totalBytes += data.size
    }

    fun finish(): File {
        output.flush()
        output.fd.sync()
        output.close()
        if (finalizedDigestHex == null) {
            finalizedDigestHex = digest.digest().joinToString("") { "%02x".format(it) }
        }
        return tempFile
    }

    fun abort() {
        runCatching { output.close() }
        runCatching { tempFile.delete() }
    }
}
