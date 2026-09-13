package com.calcvault.storage.seal

import com.calcvault.utils.CryptoUtils
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ContainerSeal
 *
 * Full authenticated encryption layer over the USB container.
 *
 * Anti-tamper system:
 *  container.enc  ← encrypted records
 *  container.seal ← HMAC-SHA256 of entire container.enc + nonce (32+16 bytes)
 *  index.enc      ← AES-256-GCM encrypted index file
 *
 * Hash chain:
 *  Each record includes: HMAC(macKey, prevRecordHash || thisPayload)
 *  Modifying any record breaks all subsequent hashes → detected on verify.
 *
 * On every open: verify seal. Mismatch = TAMPER_DETECTED = refuse to open.
 * On every write: recompute and update seal.
 */
class ContainerSeal(
    private val usbRoot: File,
    private val macKey: ByteArray // 32-byte key derived from master key
) {
    companion object {
        private const val SEAL_FILE = "container.seal"
        private const val INDEX_FILE = "index.enc"
        private const val NONCE_LEN = 16
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }

    private val sealFile = File(usbRoot, SEAL_FILE)
    private val indexFile = File(usbRoot, INDEX_FILE)
    private var prevHash = ByteArray(32)
    private val rng = SecureRandom()

    enum class SealResult { VALID, SEAL_MISSING, TAMPER_DETECTED }

    // ── Seal verify/update ─────────────────────────────────────────────────

    fun verifySeal(containerFile: File): SealResult {
        if (!sealFile.exists()) return SealResult.SEAL_MISSING
        if (!containerFile.exists()) return SealResult.TAMPER_DETECTED
        return try {
            val seal = sealFile.readBytes()
            if (seal.size < NONCE_LEN + 32) return SealResult.TAMPER_DETECTED
            val nonce = seal.copyOfRange(0, NONCE_LEN)
            val stored = seal.copyOfRange(NONCE_LEN, NONCE_LEN + 32)
            val computed = hmac(macKey, nonce + containerFile.readBytes())
            if (CryptoUtils.constantTimeEquals(stored, computed)) {
                SealResult.VALID
            } else {
                SealResult.TAMPER_DETECTED
            }
        } catch (e: Exception) { SealResult.TAMPER_DETECTED }
    }

    fun updateSeal(containerFile: File) {
        try {
            val nonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
            val h = hmac(macKey, nonce + containerFile.readBytes())
            sealFile.writeBytes(nonce + h)
        } catch (e: Exception) {}
    }

    // ── Hash chain ─────────────────────────────────────────────────────────

    /** Returns hash chain entry for a new record. Updates internal prevHash. */
    fun chainHash(payload: ByteArray): ByteArray {
        val h = hmac(macKey, prevHash + payload)
        prevHash = h.copyOf()
        return h
    }

    /**
     * Walk all records and verify hash chain is unbroken.
     * Returns index of first broken link, or -1 if intact.
     */
    fun verifyChain(containerFile: File, offsets: List<Pair<Long, Int>>): Int {
        var prev = ByteArray(32)
        for ((idx, item) in offsets.withIndex()) {
            try {
                val bytes = ByteArray(item.second)
                RandomAccessFile(containerFile, "r").use { it.seek(item.first); it.readFully(bytes) }
                // Chain hash is stored at bytes.size-52..(bytes.size-20)
                val chainStart = bytes.size - 52
                if (chainStart < 0) return idx
                val stored = bytes.copyOfRange(chainStart, chainStart + 32)
                val payload = bytes.copyOfRange(0, chainStart)
                val expected = hmac(macKey, prev + payload)
                if (!CryptoUtils.constantTimeEquals(stored, expected)) return idx
                prev = stored
            } catch (e: Exception) { return idx }
        }
        return -1
    }

    // ── Encrypted index ────────────────────────────────────────────────────

    fun writeIndex(entries: Map<Long, IndexEntry>, indexKey: ByteArray) {
        val plain = entries.values.joinToString("\n") {
            "${it.id}|${it.offset}|${it.len}|${it.type}|${it.hash}|${it.key}"
        }.toByteArray()
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val c = gcm(Cipher.ENCRYPT_MODE, indexKey, iv)
        val ct = c.doFinal(plain); plain.fill(0)
        indexFile.writeBytes(iv + ct)
    }

    fun readIndex(indexKey: ByteArray): Map<Long, IndexEntry> {
        if (!indexFile.exists()) return emptyMap()
        return try {
            val raw = indexFile.readBytes()
            val iv = raw.copyOfRange(0, IV_LEN)
            val ct = raw.copyOfRange(IV_LEN, raw.size)
            val c = gcm(Cipher.DECRYPT_MODE, indexKey, iv)
            val plain = c.doFinal(ct)
            String(plain).also { plain.fill(0) }.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val p = line.split("|"); if (p.size < 6) return@mapNotNull null
                    val id = p[0].toLongOrNull() ?: return@mapNotNull null
                    id to IndexEntry(id, p[1].toLong(), p[2].toInt(), p[3].toInt(), p[4], p[5])
                }.toMap()
        } catch (e: Exception) { emptyMap() }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        CryptoUtils.hmacSha256(key, data)

    private fun gcm(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return c
    }
}

data class IndexEntry(val id: Long, val offset: Long, val len: Int, val type: Int, val hash: String, val key: String)
