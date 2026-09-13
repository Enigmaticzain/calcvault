package com.calcvault.crypto.ratchet

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.*
import javax.crypto.spec.*

/**
 * DoubleRatchet — Signal Protocol Double Ratchet
 *
 * Security properties:
 *  ✅ Forward secrecy      — past messages safe if current key leaks
 *  ✅ Break-in recovery    — future messages safe after compromise
 *  ✅ Per-message keys     — every single message has unique AES-256 key
 *  ✅ Key rotation         — sending chain key advances after every send
 *  ✅ Out-of-order delivery — skipped message keys cached up to MAX_SKIP
 */
class DoubleRatchet {

    companion object {
        private const val CURVE = "secp256r1"
        private const val MAX_SKIP = 1000
        private const val MSG_KEY_SEED: Byte = 0x01
        private const val CHAIN_SEED: Byte = 0x02
        private const val INFO_ROOT = "cv-root-v1"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private val RNG = SecureRandom()
    }

    // ── State ──────────────────────────────────────────────────────────────
    private var rootKey = ByteArray(32)
    private var sendChainKey = ByteArray(32)
    private var recvChainKey = ByteArray(32)
    private var localKey: KeyPair? = null
    private var remoteKey: PublicKey? = null
    private var sendCount = 0
    private var recvCount = 0
    private var prevSendLen = 0
    private val skipped = mutableMapOf<Pair<String, Int>, ByteArray>()
    private var initialized = false

    // ── Init ───────────────────────────────────────────────────────────────

    fun initSender(sharedSecret: ByteArray, partnerRatchetPub: PublicKey) {
        localKey = genKeypair()
        remoteKey = partnerRatchetPub
        rootKey = sharedSecret.copyOf(32)
        val (newRoot, sendChain) = dhStep(rootKey, localKey!!.private, remoteKey!!)
        rootKey = newRoot
        sendChainKey = sendChain
        initialized = true
        sharedSecret.fill(0)
    }

    fun initReceiver(sharedSecret: ByteArray, localKeypair: KeyPair) {
        localKey = localKeypair
        rootKey = sharedSecret.copyOf(32)
        initialized = true
        sharedSecret.fill(0)
    }

    // ── Encrypt ────────────────────────────────────────────────────────────

    fun encrypt(plaintext: ByteArray): RatchetMessage {
        check(initialized)
        val msgKey = hmac(sendChainKey, byteArrayOf(MSG_KEY_SEED))
        sendChainKey = hmac(sendChainKey, byteArrayOf(CHAIN_SEED))
        val ctr = sendCount++
        val iv = ByteArray(GCM_IV_LEN).also { RNG.nextBytes(it) }
        val ct = gcmEncrypt(plaintext, msgKey, iv)
        plaintext.fill(0); msgKey.fill(0)
        return RatchetMessage(
            sendCount = ctr,
            prevChainLen = prevSendLen,
            senderRatchetKey = localKey!!.public.encoded,
            iv = iv,
            ciphertext = ct
        )
    }

    // ── Decrypt ────────────────────────────────────────────────────────────

    fun decrypt(msg: RatchetMessage): ByteArray? {
        check(initialized)
        val senderPub = decodeKey(msg.senderRatchetKey) ?: return null
        val senderB64 = b64(senderPub.encoded)
        val remoteB64 = remoteKey?.let { b64(it.encoded) }

        val cacheKey = Pair(senderB64, msg.sendCount)
        skipped[cacheKey]?.let { cachedKey ->
            val plain = gcmDecrypt(msg.ciphertext, cachedKey, msg.iv)
            skipped.remove(cacheKey); cachedKey.fill(0)
            return plain
        }

        if (senderB64 != remoteB64) {
            skipKeys(msg.prevChainLen, senderB64)
            val (newRoot1, newRecvChain) = dhStep(rootKey, localKey!!.private, senderPub)
            rootKey = newRoot1
            recvChainKey = newRecvChain
            remoteKey = senderPub
            recvCount = 0
            prevSendLen = sendCount; sendCount = 0
            val newLocal = genKeypair()
            val (newRoot2, newSendChain) = dhStep(newRoot1, newLocal.private, senderPub)
            rootKey = newRoot2; sendChainKey = newSendChain; localKey = newLocal
        }

        skipKeys(msg.sendCount, senderB64)

        val msgKey = hmac(recvChainKey, byteArrayOf(MSG_KEY_SEED))
        recvChainKey = hmac(recvChainKey, byteArrayOf(CHAIN_SEED))
        recvCount++
        val plain = gcmDecrypt(msg.ciphertext, msgKey, msg.iv)
        msgKey.fill(0)
        return plain
    }

    // ── State export/import for USB persistence ────────────────────────────

    fun exportState(): Map<String, Any> = mapOf(
        "rk" to b64(rootKey),
        "sk" to b64(sendChainKey),
        "ck" to b64(recvChainKey),
        "lpk" to (localKey?.public?.encoded?.let { b64(it) } ?: ""),
        "lsk" to (localKey?.private?.encoded?.let { b64(it) } ?: ""),
        "rpk" to (remoteKey?.encoded?.let { b64(it) } ?: ""),
        "sc" to sendCount,
        "rc" to recvCount,
        "psl" to prevSendLen,
        "sk_cache" to skipped.entries.associate {
            "${it.key.first}|${it.key.second}" to b64(it.value)
        }
    )

    @Suppress("UNCHECKED_CAST")
    fun importState(s: Map<String, Any>) {
        rootKey = dec64(s["rk"] as String)
        sendChainKey = dec64(s["sk"] as String)
        recvChainKey = dec64(s["ck"] as String)
        sendCount = (s["sc"] as Int)
        recvCount = (s["rc"] as Int)
        prevSendLen = (s["psl"] as Int)
        val lpk = s["lpk"] as? String; val lsk = s["lsk"] as? String
        if (!lpk.isNullOrBlank() && !lsk.isNullOrBlank()) {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(dec64(lpk)))
            val priv = KeyFactory.getInstance("EC").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(dec64(lsk)))
            localKey = KeyPair(pub, priv)
        }
        val rpk = s["rpk"] as? String
        if (!rpk.isNullOrBlank()) {
            remoteKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(dec64(rpk)))
        }
        skipped.clear()
        (s["sk_cache"] as? Map<String, String>)?.forEach { (k, v) ->
            val p = k.split("|"); if (p.size == 2) {
                skipped[Pair(p[0], p[1].toInt())] = dec64(v)
            }
        }
        initialized = true
    }

    fun wipe() {
        rootKey.fill(0); sendChainKey.fill(0); recvChainKey.fill(0)
        skipped.values.forEach { it.fill(0) }; skipped.clear()
        localKey = null; remoteKey = null; initialized = false
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun dhStep(rk: ByteArray, priv: PrivateKey, pub: PublicKey): Pair<ByteArray, ByteArray> {
        val dh = KeyAgreement.getInstance("ECDH").also { it.init(priv); it.doPhase(pub, true) }
        val ikm = dh.generateSecret()
        val out = hkdf(rk, ikm, INFO_ROOT, 64)
        ikm.fill(0)
        return Pair(out.copyOf(32), out.copyOfRange(32, 64))
    }

    private fun skipKeys(until: Int, senderB64: String) {
        var chain = recvChainKey.copyOf(); var ctr = recvCount
        while (ctr < until && skipped.size < MAX_SKIP) {
            skipped[Pair(senderB64, ctr)] = hmac(chain, byteArrayOf(MSG_KEY_SEED))
            chain = hmac(chain, byteArrayOf(CHAIN_SEED)); ctr++
        }
        recvChainKey = chain; recvCount = ctr
    }

    private fun hkdf(salt: ByteArray, ikm: ByteArray, info: String, len: Int): ByteArray {
        val prk = hmac(salt, ikm)
        val out = ByteArray(len); val ib = info.toByteArray(); var t = ByteArray(0); var off = 0; var c = 1
        while (off < len) {
            t = hmac(prk, t + ib + byteArrayOf(c.toByte()))
            val n = minOf(t.size, len - off); System.arraycopy(t, 0, out, off, n); off += n; c++
        }
        prk.fill(0); return out
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").also { it.init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private fun gcmEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return c.doFinal(plain)
    }

    private fun gcmDecrypt(ct: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        c.doFinal(ct)
    } catch (e: Exception) { null }

    private fun genKeypair(): KeyPair =
        KeyPairGenerator.getInstance("EC").also { it.initialize(ECGenParameterSpec(CURVE), RNG) }.generateKeyPair()

    private fun decodeKey(b: ByteArray): PublicKey? = try {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(b))
    } catch (e: Exception) { null }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun dec64(s: String) = Base64.getDecoder().decode(s)
}

data class RatchetMessage(
    val sendCount: Int,
    val prevChainLen: Int,
    val senderRatchetKey: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    fun toBytes(): ByteArray {
        val b = java.nio.ByteBuffer.allocate(4 + 4 + 4 + senderRatchetKey.size + 12 + ciphertext.size)
        b.putInt(sendCount); b.putInt(prevChainLen)
        b.putInt(senderRatchetKey.size); b.put(senderRatchetKey)
        b.put(iv); b.put(ciphertext); return b.array()
    }
    companion object {
        fun fromBytes(bytes: ByteArray): RatchetMessage? {
            try {
                if (bytes.size < 24) return null
                // Quick check: if it looks like JSON, it's NOT a RatchetMessage
                if (bytes[0].toInt() == '{'.toInt()) return null

                val b = java.nio.ByteBuffer.wrap(bytes)
                val sc = b.int; val pc = b.int; val kl = b.int

                // Safety check on key length to prevent OOM
                if (kl <= 0 || kl > 2048 || bytes.size < (12 + kl + 12)) return null

                val key = ByteArray(kl).also { b.get(it) }
                val iv = ByteArray(12).also { b.get(it) }
                val ct = ByteArray(b.remaining()).also { b.get(it) }
                return RatchetMessage(sc, pc, key, iv, ct)
            } catch (e: Exception) {
                return null
            }
        }
    }
}
