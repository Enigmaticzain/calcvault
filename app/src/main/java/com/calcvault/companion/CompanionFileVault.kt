package com.calcvault.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.calcvault.utils.CryptoUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private data class CompanionFileMeta(
    val fileId: String,
    val name: String,
    val mimeType: String,
    val plainSize: Long,
    val encSize: Long,
    val updatedAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fileId", fileId)
        .put("name", name)
        .put("mimeType", mimeType)
        .put("plainSize", plainSize)
        .put("encSize", encSize)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): CompanionFileMeta {
            return CompanionFileMeta(
                fileId = json.optString("fileId", ""),
                name = json.optString("name", "file.bin"),
                mimeType = json.optString("mimeType", "application/octet-stream"),
                plainSize = json.optLong("plainSize", 0L),
                encSize = json.optLong("encSize", 0L),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

class CompanionFileVault(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "cv_companion_file_key"
        private const val META_FILE_NAME = "index.json"
        private const val IV_LEN = 12
    }

    private val rootDir = File(context.filesDir, "companion_vault")
    private val filesDir = File(rootDir, "files")
    private val metadataFile = File(rootDir, META_FILE_NAME)
    private val lock = Any()
    private val rng = SecureRandom()

    init {
        if (!filesDir.exists()) filesDir.mkdirs()
        if (!metadataFile.exists()) {
            metadataFile.parentFile?.mkdirs()
            metadataFile.writeText("[]", StandardCharsets.UTF_8)
        }
    }

    fun listFilesJson(): JSONArray {
        synchronized(lock) {
            val metas = readAllMetadata()
            val result = JSONArray()
            metas.forEach { meta ->
                result.put(
                    JSONObject()
                        .put("fileId", meta.fileId)
                        .put("name", meta.name)
                        .put("size", meta.plainSize)
                        .put("modifiedAt", meta.updatedAt)
                        .put("mimeType", meta.mimeType)
                )
            }
            return result
        }
    }

    fun storePlainFile(tempPlainFile: File, fileName: String, mimeType: String): JSONObject {
        synchronized(lock) {
            val fileId = "f_" + CryptoUtils.randomHex(8)
            val target = File(filesDir, "$fileId.bin")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_LEN).also(rng::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))

            FileOutputStream(target).use { out ->
                out.write(iv)
                FileInputStream(tempPlainFile).use { input ->
                    val buffer = ByteArray(CompanionBridgeConstants.CHUNK_SIZE_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val update = cipher.update(buffer, 0, read)
                        if (update != null && update.isNotEmpty()) {
                            out.write(update)
                        }
                    }
                    val finalBlock = cipher.doFinal()
                    out.write(finalBlock)
                }
            }

            val meta = CompanionFileMeta(
                fileId = fileId,
                name = fileName.ifBlank { "$fileId.bin" },
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                plainSize = tempPlainFile.length(),
                encSize = target.length(),
                updatedAt = System.currentTimeMillis()
            )
            writeMetadata(meta)

            return JSONObject()
                .put("fileId", meta.fileId)
                .put("name", meta.name)
                .put("size", meta.plainSize)
                .put("storedAt", meta.updatedAt)
        }
    }

    fun streamFile(fileId: String, mode: String, emitChunk: (ByteArray) -> Unit): JSONObject {
        synchronized(lock) {
            val meta = readAllMetadata().firstOrNull { it.fileId == fileId }
                ?: throw IllegalArgumentException("file_not_found")
            val blob = File(filesDir, "$fileId.bin")
            if (!blob.exists()) throw IllegalStateException("file_blob_missing")

            if (mode == "encrypted") {
                FileInputStream(blob).use { input ->
                    val buffer = ByteArray(CompanionBridgeConstants.CHUNK_SIZE_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        emitChunk(buffer.copyOf(read))
                    }
                }
                return JSONObject()
                    .put("fileName", meta.name)
                    .put("size", blob.length())
                    .put("mode", "encrypted")
            }

            FileInputStream(blob).use { raw ->
                val iv = ByteArray(IV_LEN)
                val readIv = raw.read(iv)
                if (readIv != IV_LEN) {
                    throw IllegalStateException("encrypted_blob_corrupt")
                }

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                CipherInputStream(raw, cipher).use { decrypted ->
                    val buffer = ByteArray(CompanionBridgeConstants.CHUNK_SIZE_BYTES)
                    while (true) {
                        val read = decrypted.read(buffer)
                        if (read <= 0) break
                        emitChunk(buffer.copyOf(read))
                    }
                }
            }

            return JSONObject()
                .put("fileName", meta.name)
                .put("size", meta.plainSize)
                .put("mode", "decrypted")
        }
    }

    private fun readAllMetadata(): MutableList<CompanionFileMeta> {
        if (!metadataFile.exists()) return mutableListOf()
        val raw = metadataFile.readText(StandardCharsets.UTF_8)
        if (raw.isBlank()) return mutableListOf()

        val array = JSONArray(raw)
        val list = mutableListOf<CompanionFileMeta>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val meta = CompanionFileMeta.fromJson(item)
            if (meta.fileId.isNotBlank()) {
                list += meta
            }
        }
        return list
    }

    private fun writeMetadata(meta: CompanionFileMeta) {
        val list = readAllMetadata().filterNot { it.fileId == meta.fileId }.toMutableList()
        list += meta
        val array = JSONArray()
        list.sortedByDescending { it.updatedAt }.forEach { array.put(it.toJson()) }
        metadataFile.writeText(array.toString(), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
