package com.calcvault.listen

import android.util.Base64
import com.calcvault.crypto.E2EKeyManager
import org.json.JSONObject

/**
 * Lightweight payload wrapper for listen-control signals.
 * Only control metadata is transmitted, never the music file itself.
 */
object ListenTogetherCrypto {

    fun wrap(payload: JSONObject): JSONObject {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        return try {
            val encrypted = E2EKeyManager.getInstance().encrypt(bytes)
            JSONObject().apply {
                put("enc", true)
                put("blob", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                put("fallback", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
        } catch (_: Exception) {
            JSONObject().apply {
                put("enc", false)
                put("blob", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
        }
    }

    fun unwrap(container: JSONObject?): JSONObject? {
        if (container == null) return null
        val blob = container.optString("blob")
        if (blob.isBlank()) return null

        return try {
            val raw = Base64.decode(blob, Base64.DEFAULT)
            val decoded = if (container.optBoolean("enc", false)) {
                E2EKeyManager.getInstance().decrypt(raw)
            } else {
                raw
            }
            JSONObject(decoded.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            val fallback = container.optString("fallback")
            if (fallback.isBlank()) return null
            return try {
                JSONObject(Base64.decode(fallback, Base64.DEFAULT).toString(Charsets.UTF_8))
            } catch (_: Exception) {
                null
            }
        }
    }
}
