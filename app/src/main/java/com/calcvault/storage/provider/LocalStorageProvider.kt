package com.calcvault.storage.provider

import android.content.Context
import android.os.Environment
import com.calcvault.utils.CryptoUtils
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LocalStorageProvider
 *
 * Stores encrypted data in a persistent directory that survives app uninstalls.
 * Uses a hidden folder in the shared storage area if accessible.
 */
class LocalStorageProvider(
    private val context: Context,
    private val masterKey: ByteArray // 32-byte AES key derived from passphrase
) : StorageProvider {

    companion object {
        private const val VAULT_DIR = "local_vault"
        private const val INDEX_FILE = ".index"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
        private const val HASH_LEN = 32
        private val APPEND_ONLY_PREFIXES = listOf("msg/", "media/", "call/")

        // Persistent path that survives uninstalls
        private const val PERSISTENT_FOLDER_NAME = ".cv_persistent_data"
    }

    private val vaultDir by lazy {
        val externalRoot = Environment.getExternalStorageDirectory()
        val persistentDir = File(externalRoot, PERSISTENT_FOLDER_NAME)

        // Attempt to use persistent storage, fallback to app-private storage if needed
        if (persistentDir.exists() || persistentDir.mkdirs()) {
            val finalVault = File(persistentDir, VAULT_DIR)
            if (!finalVault.exists()) finalVault.mkdirs()
            finalVault
        } else {
            val fallback = File(context.filesDir, VAULT_DIR)
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    private val rng = SecureRandom()
    private val keyIndex = mutableMapOf<String, String>()

    override val mode = StorageMode.LOCAL
    override val isAvailable get() = vaultDir.exists()
    override val usedBytes get() = if (vaultDir.exists()) vaultDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    override val statusMessage = "⚠️ Local mode — USB not connected. Data stays on phone even if app is deleted."

    override fun open(): Boolean {
        return try {
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }
            loadIndex()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        keyIndex.clear()
    }

    override fun destroy(): Boolean {
        return try {
            if (vaultDir.exists()) {
                vaultDir.walkBottomUp().forEach { file ->
                    if (file.isFile) {
                        val size = file.length().toInt()
                        if (size > 0) {
                            repeat(3) {
                                file.writeBytes(ByteArray(size).also { rng.nextBytes(it) })
                            }
                        }
                        file.delete()
                    }
                }
                vaultDir.delete()
            }
            // Also try to remove the parent persistent folder if empty
            vaultDir.parentFile?.let { if (it.list()?.isEmpty() == true) it.delete() }
            true
        } catch (e: Exception) { false }
    }

    override fun writeData(key: String, data: ByteArray): Boolean {
        return try {
            if (!vaultDir.exists()) vaultDir.mkdirs()

            val isAppendOnly = APPEND_ONLY_PREFIXES.any { key.startsWith(it) }
            val filename = if (isAppendOnly) {
                "${keyToFilename(key)}_${System.nanoTime()}"
            } else {
                keyToFilename(key)
            }

            val file = File(vaultDir, filename)

            val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(masterKey, "AES"),
                GCMParameterSpec(TAG_BITS, iv)
            )
            val ct = cipher.doFinal(data)

            val hash = CryptoUtils.sha256(iv + ct)
            file.writeBytes(iv + ct + hash)

            keyIndex[key] = filename
            if (isAppendOnly) {
                appendToIndex(key, filename)
            } else {
                updateIndex(key, filename)
            }

            true
        } catch (e: Exception) { false }
    }

    override fun readData(key: String): ByteArray? {
        return try {
            val filename = keyIndex[key] ?: return null
            val file = File(vaultDir, filename)
            if (!file.exists()) return null

            val raw = file.readBytes()
            if (raw.size < IV_LEN + HASH_LEN) return null

            val iv = raw.copyOfRange(0, IV_LEN)
            val hashStart = raw.size - HASH_LEN
            val ct = raw.copyOfRange(IV_LEN, hashStart)
            val stored = raw.copyOfRange(hashStart, raw.size)

            val expected = CryptoUtils.sha256(iv + ct)
            if (!CryptoUtils.constantTimeEquals(stored, expected)) return null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(masterKey, "AES"),
                GCMParameterSpec(TAG_BITS, iv)
            )
            cipher.doFinal(ct)
        } catch (e: Exception) { null }
    }

    override fun deleteData(key: String): Boolean {
        return try {
            val isAppendOnly = APPEND_ONLY_PREFIXES.any { key.startsWith(it) }
            if (isAppendOnly) {
                writeData("tombstone/$key", "DELETED:$key".toByteArray())
            } else {
                val filename = keyIndex[key] ?: return true
                val file = File(vaultDir, filename)
                if (file.exists()) {
                    file.delete()
                }
                keyIndex.remove(key)
                removeFromIndex(key)
            }
            true
        } catch (e: Exception) { false }
    }

    override fun listKeys(prefix: String): List<String> {
        return keyIndex.keys.filter { it.startsWith(prefix) }.sorted()
    }

    override fun exists(key: String): Boolean = keyIndex.containsKey(key)

    private fun keyToFilename(key: String): String {
        return CryptoUtils.sha256hex(key.toByteArray()).take(16)
    }

    private fun loadIndex() {
        val indexFile = File(vaultDir, INDEX_FILE)
        if (!indexFile.exists()) return
        try {
            keyIndex.clear()
            indexFile.readLines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) keyIndex[parts[0]] = parts[1]
            }
        } catch (e: Exception) {}
    }

    private fun updateIndex(key: String, filename: String) {
        keyIndex[key] = filename
        flushIndex()
    }

    private fun appendToIndex(key: String, filename: String) {
        try {
            val indexFile = File(vaultDir, INDEX_FILE)
            indexFile.appendText("$key=$filename\n")
        } catch (e: Exception) {}
    }

    private fun removeFromIndex(key: String) {
        keyIndex.remove(key)
        flushIndex()
    }

    private fun flushIndex() {
        try {
            val indexFile = File(vaultDir, INDEX_FILE)
            val content = keyIndex.entries.joinToString("\n") { "${it.key}=${it.value}" }
            indexFile.writeText(content)
        } catch (e: Exception) {}
    }
}
