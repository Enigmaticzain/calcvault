package com.calcvault.storage.container

import com.calcvault.crypto.E2EKeyManager
import com.calcvault.storage.journal.WriteJournal
import com.calcvault.utils.CryptoUtils
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EncryptedContainer
 *
 * True virtual encrypted filesystem backed by a single .enc file on USB.
 */
class EncryptedContainer(
    private val containerFile: File,
    private val journal: WriteJournal,
    private val keyManager: E2EKeyManager
) {
    companion object {
        val MAGIC = "CALCVAULT_V2".toByteArray()
        const val VERSION = 2
        const val SALT_LEN = 32
        const val IV_LEN = 12
        const val TAG_LEN = 16
        const val HASH_LEN = 32

        // Segment types
        const val SEG_KEY_VAULT = 0x01
        const val SEG_MESSAGE = 0x02
        const val SEG_MEDIA_CHUNK = 0x03
        const val SEG_INDEX = 0x04
        const val SEG_INTEGRITY = 0x05
        const val SEG_TOMBSTONE = 0x06
        const val SEG_PREFS = 0x07

        // Offsets
        const val OFF_MAGIC = 0L
        const val OFF_VERSION = 12L
        const val OFF_SALT = 16L
        const val OFF_HEADER_IV = 48L
        const val OFF_HEADER_TAG = 60L
        const val OFF_HEADER_ENC = 76L
        const val HEADER_ENC_LEN = 512
        const val DATA_START = OFF_HEADER_ENC + HEADER_ENC_LEN // 588L
    }

    // In-memory index: record_id → (fileOffset, byteLength)
    private val index = mutableMapOf<Long, Pair<Long, Int>>()

    // Container master key — lives in RAM only
    private var masterKey: SecretKey? = null
    private val rng = SecureRandom()

    data class SecretKey(val raw: ByteArray) {
        fun toJceKey() = SecretKeySpec(raw, "AES")
        fun wipe() { raw.fill(0) }
    }

    // ── Metadata ───────────────────────────────────────────────────────────

    fun getRecordLocation(recordId: Long): Pair<Long, Int>? = index[recordId]

    // ── Init / Create ──────────────────────────────────────────────────────

    /**
     * Create a brand-new container file.
     */
    fun create(passphraseKey: javax.crypto.SecretKey, salt: ByteArray): Boolean {
        return try {
            journal.logIntent(-1L, "CREATE_CONTAINER")

            val rawMasterKey = ByteArray(32).also { rng.nextBytes(it) }
            masterKey = SecretKey(rawMasterKey)

            val headerPlain = buildKeyVaultBlock(rawMasterKey)
            val headerIV = ByteArray(IV_LEN).also { rng.nextBytes(it) }
            val headerEnc = aesGcmEncrypt(headerPlain, passphraseKey, headerIV)

            RandomAccessFile(containerFile, "rw").use { raf ->
                raf.seek(OFF_MAGIC); raf.write(MAGIC)
                raf.seek(OFF_VERSION); raf.writeInt(VERSION)
                raf.seek(OFF_SALT); raf.write(salt)
                raf.seek(OFF_HEADER_IV); raf.write(headerIV)
                raf.seek(OFF_HEADER_TAG); raf.write(headerEnc.copyOf(TAG_LEN))
                raf.seek(OFF_HEADER_ENC); raf.write(headerEnc.copyOfRange(TAG_LEN, headerEnc.size).copyOf(HEADER_ENC_LEN))
            }

            rawMasterKey.fill(0)
            headerPlain.fill(0)

            journal.logCommit(-1L, CryptoUtils.sha256hex(MAGIC))
            true
        } catch (e: Exception) {
            journal.logRollback(-1L, e.message ?: "CREATE_FAILED")
            false
        }
    }

    /**
     * Open and verify an existing container.
     */
    fun open(passphraseKey: javax.crypto.SecretKey): Boolean {
        return try {
            if (!verifyMagic()) return false

            RandomAccessFile(containerFile, "r").use { raf ->
                val headerIV = ByteArray(IV_LEN).also { raf.seek(OFF_HEADER_IV); raf.readFully(it) }
                val headerTag = ByteArray(TAG_LEN).also { raf.seek(OFF_HEADER_TAG); raf.readFully(it) }
                val headerEnc = ByteArray(HEADER_ENC_LEN).also { raf.seek(OFF_HEADER_ENC); raf.readFully(it) }

                val fullEnc = headerTag + headerEnc
                val headerPlain = aesGcmDecrypt(fullEnc, passphraseKey, headerIV) ?: return false

                val rawMasterKey = parseKeyVaultBlock(headerPlain)
                masterKey = SecretKey(rawMasterKey)
                headerPlain.fill(0)

                rebuildIndex(raf)
            }
            true
        } catch (e: Exception) { false }
    }

    // ── Write ──────────────────────────────────────────────────────────────

    fun appendRecord(
        segmentType: Int,
        recordId: Long,
        plaintext: ByteArray
    ): Boolean {
        val key = masterKey ?: return false
        val hash = CryptoUtils.sha256hex(plaintext)
        journal.logIntent(recordId, hash)

        return try {
            val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
            val ciphertext = aesGcmEncrypt(plaintext, key.toJceKey(), iv)

            val footer = ByteBuffer.allocate(4 + 8 + 8).apply {
                putInt(segmentType)
                putLong(recordId)
                putLong(System.currentTimeMillis())
            }.array()

            val recordHash = CryptoUtils.sha256(ciphertext + footer)
            val totalLen = IV_LEN + ciphertext.size + HASH_LEN + footer.size
            val lenBytes = ByteBuffer.allocate(4).putInt(totalLen).array()

            val recordBytes = lenBytes + iv + ciphertext + recordHash + footer

            val tmpFile = File(containerFile.parent, "cv_tmp_${System.nanoTime()}.part")
            tmpFile.writeBytes(recordBytes)

            if (!tmpFile.readBytes().contentEquals(recordBytes)) {
                tmpFile.delete()
                journal.logRollback(recordId, "VERIFY_FAIL")
                return false
            }

            val offset = containerFile.length()
            FileOutputStream(containerFile, true).use { it.write(recordBytes) }
            tmpFile.delete()

            index[recordId] = Pair(offset, recordBytes.size)
            plaintext.fill(0)

            journal.logCommit(recordId, hash)
            true
        } catch (e: Exception) {
            journal.logRollback(recordId, e.message ?: "WRITE_ERROR")
            false
        }
    }

    /**
     * Read a specific record by ID.
     */
    fun readRecord(recordId: Long): ByteArray? {
        val key = masterKey ?: return null
        val location = index[recordId] ?: return null
        val (offset, length) = location

        return try {
            readRecordAt(offset, length, key)
        } catch (e: Exception) { null }
    }

    fun readRecordByIdAndType(recordId: Long, segmentType: Int): ByteArray? {
        val key = masterKey ?: return null
        return try {
            RandomAccessFile(containerFile, "r").use { raf ->
                var pos = DATA_START
                var foundOffset = -1L
                var foundLength = 0
                while (pos < raf.length()) {
                    raf.seek(pos)
                    val totalLen = raf.readInt()
                    if (totalLen <= 0 || pos + 4L + totalLen > raf.length()) break

                    val footerOffset = pos + 4L + totalLen - 20L
                    raf.seek(footerOffset)
                    val currentType = raf.readInt()
                    val currentRecordId = raf.readLong()
                    if (currentType == segmentType && currentRecordId == recordId) {
                        foundOffset = pos
                        foundLength = 4 + totalLen
                    }
                    pos += 4L + totalLen
                }

                if (foundOffset < 0L || foundLength <= 0) {
                    null
                } else {
                    readRecordAt(foundOffset, foundLength, key)
                }
            }
        } catch (e: Exception) { null }
    }

    /**
     * Read the raw bytes (including IV, ciphertext, hash, footer) for a record.
     * Used for syncing between USBs without decryption.
     */
    fun readRecordRaw(recordId: Long): ByteArray? {
        val location = index[recordId] ?: return null
        val (offset, length) = location
        return try {
            val recordBytes = ByteArray(length)
            RandomAccessFile(containerFile, "r").use { raf ->
                raf.seek(offset)
                raf.readFully(recordBytes)
            }
            recordBytes
        } catch (e: Exception) { null }
    }

    fun getRecordIdsByType(segmentType: Int): List<Long> {
        val ids = mutableListOf<Long>()
        for ((id, location) in index) {
            try {
                val (offset, length) = location
                val footerOffset = offset + length - 20
                val footer = ByteArray(20)
                RandomAccessFile(containerFile, "r").use { raf ->
                    raf.seek(footerOffset); raf.readFully(footer)
                }
                val type = ByteBuffer.wrap(footer, 0, 4).int
                if (type == segmentType) ids.add(id)
            } catch (e: Exception) { continue }
        }
        return ids
    }

    // ── Integrity Verification ─────────────────────────────────────────────

    fun verifyIntegrity(): List<Long> {
        val corrupt = mutableListOf<Long>()
        for ((id, _) in index) {
            if (readRecord(id) == null) corrupt.add(id)
        }
        return corrupt
    }

    fun verifyMagic(): Boolean {
        return try {
            val magic = ByteArray(MAGIC.size)
            RandomAccessFile(containerFile, "r").use { raf ->
                raf.seek(OFF_MAGIC); raf.readFully(magic)
            }
            magic.contentEquals(MAGIC)
        } catch (e: Exception) { false }
    }

    // ── Wipe / Destroy ─────────────────────────────────────────────────────

    fun destroy(): Boolean {
        return try {
            val size = containerFile.length()
            RandomAccessFile(containerFile, "rw").use { raf ->
                repeat(3) { pass ->
                    raf.seek(0)
                    val pattern = if (pass % 2 == 0) {
                        ByteArray(4096) { 0xFF.toByte() }
                    } else {
                        ByteArray(4096) { 0x00 }
                    }
                    var written = 0L
                    while (written < size) {
                        val chunk = minOf(4096L, size - written).toInt()
                        raf.write(pattern, 0, chunk)
                        written += chunk
                    }
                    raf.fd.sync()
                }
            }
            containerFile.delete()
            masterKey?.wipe()
            masterKey = null
            true
        } catch (e: Exception) { false }
    }

    // ── Close / Lock ────────────────────────────────────────────────────────

    fun close() {
        masterKey?.wipe()
        masterKey = null
        index.clear()
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    private fun buildKeyVaultBlock(rawMasterKey: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_ENC_LEN)
        buf.putInt(rawMasterKey.size)
        buf.put(rawMasterKey)
        buf.putLong(System.currentTimeMillis())
        val pad = ByteArray(buf.remaining()).also { rng.nextBytes(it) }
        buf.put(pad)
        return buf.array()
    }

    private fun parseKeyVaultBlock(block: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(block)
        val keyLen = buf.int
        return ByteArray(keyLen).also { buf.get(it) }
    }

    private fun rebuildIndex(raf: RandomAccessFile) {
        index.clear()
        var pos = DATA_START
        while (pos < raf.length()) {
            try {
                raf.seek(pos)
                val lenBytes = ByteArray(4)
                raf.readFully(lenBytes)
                val totalLen = ByteBuffer.wrap(lenBytes).int
                if (totalLen <= 0 || pos + 4 + totalLen > raf.length()) break

                val footerOffset = pos + 4 + totalLen - 20
                raf.seek(footerOffset + 4)
                val recordId = raf.readLong()
                index[recordId] = Pair(pos, 4 + totalLen)
                pos += 4 + totalLen
            } catch (e: Exception) { break }
        }
    }

    private fun readRecordAt(offset: Long, length: Int, key: SecretKey): ByteArray? {
        val recordBytes = ByteArray(length)
        RandomAccessFile(containerFile, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(recordBytes)
        }

        val iv = recordBytes.copyOfRange(4, 4 + IV_LEN)
        val footerStart = recordBytes.size - 20
        val hashStart = footerStart - HASH_LEN
        val ciphertext = recordBytes.copyOfRange(4 + IV_LEN, hashStart)
        val storedHash = recordBytes.copyOfRange(hashStart, footerStart)
        val footer = recordBytes.copyOfRange(footerStart, recordBytes.size)

        val computedHash = CryptoUtils.sha256(ciphertext + footer)
        if (!storedHash.contentEquals(computedHash)) return null

        return aesGcmDecrypt(ciphertext, key.toJceKey(), iv)
    }

    private fun aesGcmEncrypt(plain: ByteArray, key: javax.crypto.SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(plain)
    }

    private fun aesGcmDecrypt(ct: ByteArray, key: javax.crypto.SecretKey, iv: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (e: Exception) { null }
    }
}
