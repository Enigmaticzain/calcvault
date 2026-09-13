package com.calcvault.companion

import android.content.Context
import org.json.JSONObject
import java.util.Base64

object CompanionBridgeController {

    fun startUsbOnly(context: Context, autoApprove: Boolean = false) {
        CompanionBridgeService.start(context, bindLan = false, autoApprove = autoApprove)
    }

    fun startLanEnabled(context: Context, autoApprove: Boolean = false) {
        CompanionBridgeService.start(context, bindLan = true, autoApprove = autoApprove)
    }

    fun stop(context: Context) {
        CompanionBridgeService.stop(context)
    }

    fun currentPairingCode(): String? {
        val ticket = CompanionBridgeService.getLastPairingTicket() ?: return null
        if (System.currentTimeMillis() > ticket.expiresAt) return null
        return ticket.pairingCode
    }

    fun currentPairingUri(): String? {
        val ticket = CompanionBridgeService.getLastPairingTicket() ?: return null
        if (System.currentTimeMillis() > ticket.expiresAt) return null

        val payload = JSONObject()
            .put("version", CompanionBridgeConstants.PROTOCOL_VERSION)
            .put("pairingId", ticket.pairingId)
            .put("code", ticket.pairingCode)
            .put("transport", "usb")
            .put("endpointHint", "ws://127.0.0.1:${CompanionBridgeConstants.BRIDGE_PORT}${CompanionBridgeConstants.BRIDGE_PATH}")
            .put("expiresAt", ticket.expiresAt)

        val encoded = Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        return "calcvault://pair?data=$encoded"
    }
}
