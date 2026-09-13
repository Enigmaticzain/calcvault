package com.calcvault.companion

import com.calcvault.utils.CryptoUtils
import java.util.Locale

private const val PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

data class CompanionPairingTicket(
    val pairingId: String,
    val pairingCode: String,
    val codeHashBase64: String,
    val createdAt: Long,
    val expiresAt: Long
)

class CompanionPairingState {

    @Volatile
    private var ticket: CompanionPairingTicket? = null

    fun issue(now: Long = System.currentTimeMillis()): CompanionPairingTicket {
        val code = generatePairingCode(CompanionBridgeConstants.PAIRING_CODE_LENGTH)
        val normalized = code.uppercase(Locale.US)
        val pairingId = CryptoUtils.randomHex(12)
        val createdAt = now
        val expiresAt = now + CompanionBridgeConstants.PAIRING_TTL_MS
        val digest = CryptoUtils.sha256(normalized.toByteArray(Charsets.UTF_8))
        val next = CompanionPairingTicket(
            pairingId = pairingId,
            pairingCode = normalized,
            codeHashBase64 = CryptoUtils.toBase64(digest),
            createdAt = createdAt,
            expiresAt = expiresAt
        )
        ticket = next
        return next
    }

    fun current(now: Long = System.currentTimeMillis()): CompanionPairingTicket? {
        val active = ticket ?: return null
        if (now > active.expiresAt) {
            ticket = null
            return null
        }
        return active
    }

    fun invalidate() {
        ticket = null
    }

    private fun generatePairingCode(length: Int): String {
        val source = PAIRING_ALPHABET.toCharArray()
        val raw = CryptoUtils.randomBytes(length)
        val out = StringBuilder(length)
        for (i in 0 until length) {
            val idx = raw[i].toInt() and 0xff
            out.append(source[idx % source.size])
        }
        return out.toString()
    }
}
