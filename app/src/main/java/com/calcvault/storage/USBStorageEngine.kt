package com.calcvault.storage

import android.content.Context
import com.calcvault.storage.container.EncryptedContainer
import com.calcvault.storage.journal.WriteJournal
import com.calcvault.crypto.E2EKeyManager
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * USBStorageEngine (improved)
 *
 * Core of CalcVault — orchestrates the EncryptedContainer and lifecycle.
 * Manages the connection to the physical USB drive.
 */
class USBStorageEngine private constructor(private val context: Context) {

    companion object {
        private const val CONTAINER_FILE = "container.enc"

        const val SEG_KEY_VAULT = EncryptedContainer.SEG_KEY_VAULT
        const val SEG_APPEND_DB = EncryptedContainer.SEG_MESSAGE
        const val SEG_MEDIA_CHUNK = EncryptedContainer.SEG_MEDIA_CHUNK
        const val SEG_INDEX = EncryptedContainer.SEG_INDEX
        const val SEG_INTEGRITY = EncryptedContainer.SEG_INTEGRITY
        const val SEG_TOMBSTONE = EncryptedContainer.SEG_TOMBSTONE
        const val SEG_PREFS = EncryptedContainer.SEG_PREFS

        const val MEDIA_CHUNK_SCHEME_LEGACY = 1
        const val MEDIA_CHUNK_SCHEME_STRIDED = 2
        const val MEDIA_CHUNK_RECORD_STRIDE = 1_048_576L

        @Volatile private var instance: USBStorageEngine? = null

        fun getInstance(context: Context): USBStorageEngine {
            return instance ?: synchronized(this) {
                instance ?: USBStorageEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private var usbRootPath: File? = null
    var container: EncryptedContainer? = null
        private set

    enum class State { DETACHED, ATTACHED_LOCKED, UNLOCKED, DESTROYED }
    var state: State = State.DETACHED
        private set

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── USB Lifecycle ────────────────────────────────────────────────────────

    fun onUsbAttached(usbRootDir: File) {
        usbRootPath = usbRootDir
        state = State.ATTACHED_LOCKED

        val containerFile = File(usbRootDir, CONTAINER_FILE)
        val journal = WriteJournal(usbRootDir)
        container = EncryptedContainer(containerFile, journal, E2EKeyManager.getInstance())
    }

    fun onUsbDetached() {
        engineScope.launch {
            lock()
            state = State.DETACHED
            container = null
            usbRootPath = null
        }
    }

    // ─── Unlock / Decrypt ─────────────────────────────────────────────────────

    suspend fun unlock(passphrase: CharArray, salt: ByteArray? = null): Boolean = withContext(Dispatchers.IO) {
        val root = usbRootPath ?: return@withContext false
        val c = container ?: return@withContext false

        try {
            // Salt should ideally be read from the container header
            val finalSalt = salt ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
            val spec = javax.crypto.spec.PBEKeySpec(passphrase, finalSalt, 100_000, 256)
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            val masterKey = SecretKeySpec(keyBytes, "AES")
            spec.clearPassword()

            val success = if (File(root, CONTAINER_FILE).exists()) {
                c.open(masterKey)
            } else {
                c.create(masterKey, finalSalt)
            }

            if (success) state = State.UNLOCKED
            success
        } catch (e: Exception) {
            false
        } finally {
            passphrase.fill('\u0000')
        }
    }

    // ─── Data Operations ──────────────────────────────────────────────────────

    suspend fun appendRecord(segmentType: Int, recordId: Long, payload: ByteArray): Boolean = withContext(Dispatchers.IO) {
        container?.appendRecord(segmentType, recordId, payload) ?: false
    }

    fun readRecord(recordId: Long): ByteArray? {
        return container?.readRecord(recordId)
    }

    fun readRecordByIdAndType(recordId: Long, segmentType: Int): ByteArray? {
        return container?.readRecordByIdAndType(recordId, segmentType)
    }

    fun getRecordIdsByType(segmentType: Int): List<Long> {
        return container?.getRecordIdsByType(segmentType) ?: emptyList()
    }

    suspend fun writeMediaChunk(
        mediaId: Long,
        chunkIndex: Int,
        data: ByteArray,
        schemeVersion: Int = MEDIA_CHUNK_SCHEME_STRIDED
    ): Boolean {
        val recordId = mediaChunkRecordId(mediaId, chunkIndex, schemeVersion) ?: return false
        val wrapped = ByteBuffer.allocate(4 + 4 + data.size).apply {
            putInt(chunkIndex)
            putInt(data.size)
            put(data)
        }.array()
        return appendRecord(SEG_MEDIA_CHUNK, recordId, wrapped)
    }

    fun readMediaChunk(
        mediaId: Long,
        chunkIndex: Int,
        schemeVersion: Int = MEDIA_CHUNK_SCHEME_STRIDED
    ): ByteArray? {
        val recordId = mediaChunkRecordId(mediaId, chunkIndex, schemeVersion) ?: return null
        val direct = readRecord(recordId)?.let { unwrapMediaChunk(it, chunkIndex) }
        if (direct != null) return direct

        val typed = readRecordByIdAndType(recordId, SEG_MEDIA_CHUNK) ?: return null
        return unwrapMediaChunk(typed, chunkIndex)
    }

    private fun mediaChunkRecordId(mediaId: Long, chunkIndex: Int, schemeVersion: Int): Long? {
        if (chunkIndex < 0) return null
        return try {
            when (schemeVersion) {
                MEDIA_CHUNK_SCHEME_STRIDED -> {
                    if (chunkIndex >= MEDIA_CHUNK_RECORD_STRIDE) return null
                    Math.addExact(
                        Math.multiplyExact(mediaId, MEDIA_CHUNK_RECORD_STRIDE),
                        chunkIndex.toLong()
                    )
                }
                else -> Math.addExact(mediaId, chunkIndex.toLong())
            }
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun unwrapMediaChunk(wrapped: ByteArray, expectedChunkIndex: Int): ByteArray? {
        return try {
            val buf = ByteBuffer.wrap(wrapped)
            val storedChunkIndex = buf.int
            val size = buf.int
            if (storedChunkIndex != expectedChunkIndex || size < 0 || size > buf.remaining()) {
                return null
            }
            val data = ByteArray(size)
            buf.get(data)
            if (buf.hasRemaining()) {
                null
            } else {
                data
            }
        } catch (e: Exception) { null }
    }

    // ─── Encryption ───────────────────────────────────────────────────────────

    fun encrypt(data: ByteArray): ByteArray {
        return try {
            E2EKeyManager.getInstance().encrypt(data)
        } catch (e: IllegalStateException) {
            // Fallback so UI actions (chat/mood) don't crash if session key isn't ready yet.
            data
        }
    }

    fun decrypt(blob: ByteArray): ByteArray {
        return try {
            E2EKeyManager.getInstance().decrypt(blob)
        } catch (e: IllegalStateException) {
            blob
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    fun lock() {
        container?.close()
        state = State.ATTACHED_LOCKED
    }

    fun hasAttachedEncryptedContainer(): Boolean {
        val root = usbRootPath ?: return false
        return File(root, CONTAINER_FILE).exists()
    }

    fun getAttachedContainerFile(): File? {
        val root = usbRootPath ?: return null
        val file = File(root, CONTAINER_FILE)
        return if (file.exists()) file else null
    }

    fun getBaseStoragePath(): File {
        return usbRootPath ?: context.filesDir
    }

    suspend fun restoreContainerFromBackup(
        stagedBackupFile: File,
        expectedSha256Hex: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val root = usbRootPath ?: return@withContext false
        if (!stagedBackupFile.exists()) return@withContext false

        val digest = MessageDigest.getInstance("SHA-256")
        stagedBackupFile.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
        if (!expectedSha256Hex.isNullOrBlank() && !actualDigest.equals(expectedSha256Hex, ignoreCase = true)) {
            return@withContext false
        }

        val target = File(root, CONTAINER_FILE)
        val temp = File(root, "$CONTAINER_FILE.restore.tmp")
        val backup = File(root, "$CONTAINER_FILE.restore.bak")

        if (state == State.UNLOCKED) {
            lock()
        }

        runCatching {
            stagedBackupFile.copyTo(temp, overwrite = true)
            if (target.exists()) {
                target.copyTo(backup, overwrite = true)
            }
            if (target.exists() && !target.delete()) {
                throw IllegalStateException("failed_to_delete_old_container")
            }
            if (!temp.renameTo(target)) {
                throw IllegalStateException("failed_to_promote_restored_container")
            }

            val journal = WriteJournal(root)
            container = EncryptedContainer(target, journal, E2EKeyManager.getInstance())
            state = State.ATTACHED_LOCKED
            backup.delete()
            true
        }.getOrElse {
            if (temp.exists()) temp.delete()
            if (!target.exists() && backup.exists()) {
                backup.renameTo(target)
            }
            false
        }
    }

    suspend fun destroyContainer(): Boolean = withContext(Dispatchers.IO) {
        val result = container?.destroy() ?: false
        if (result) {
            state = State.DESTROYED
            container = null
        }
        result
    }
}
