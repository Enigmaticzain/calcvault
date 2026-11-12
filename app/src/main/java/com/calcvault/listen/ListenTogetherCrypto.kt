package com.calcvault.listen

import android.util.Base64
import com.calcvault.crypto.E2EKeyManager
import org.json.JSONObject

/**
 * Legacy envelope helper retained for compatibility. It fails closed when local crypto fails and
 * never includes a plaintext fallback. Its existence does not establish peer key agreement,
 * authenticated identity, or a verified end-to-end protocol; the current Listen Together path
 * deliberately sends only consented control metadata over HTTPS instead.
 */
@Deprecated("Not a verified peer E2EE protocol; use the consent-gated control transport")
object ListenTogetherCrypto {
    fun wrap(payload: JSONObject): JSONObject? = runCatching {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val encrypted = E2EKeyManager.getInstance().encrypt(bytes)
        require(encrypted.isNotEmpty())
        JSONObject().apply {
            put("enc", true)
            put("blob", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }.getOrNull()

    fun unwrap(container: JSONObject?): JSONObject? {
        if (container == null || !container.optBoolean("enc", false)) return null
        val blob = container.optString("blob")
        if (blob.isBlank()) return null
        return runCatching {
            val encrypted = Base64.decode(blob, Base64.DEFAULT)
            val decoded = E2EKeyManager.getInstance().decrypt(encrypted)
            JSONObject(decoded.toString(Charsets.UTF_8))
        }.getOrNull()
    }
}
