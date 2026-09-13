package com.calcvault.companion

import android.util.Log
import com.calcvault.utils.SessionManager

class CompanionOperationAuthorizer(
    private val allowDebugAutoApprove: Boolean
) {

    fun authorize(op: String, requestId: String): AuthorizationResult {
        if (!CompanionBridgeConstants.SUPPORTED_OPS.contains(op)) {
            return AuthorizationResult.Denied("unsupported_operation")
        }

        if (!SessionManager.isVaultOpen) {
            return AuthorizationResult.Denied("vault_locked")
        }

        if (allowDebugAutoApprove) {
            Log.i("CompanionAuth", "Debug auto-approval granted for $op requestId=$requestId")
            return AuthorizationResult.Approved
        }

        // Explicit approval UI should be connected before enabling release approvals.
        return AuthorizationResult.Denied("approval_ui_required")
    }
}

sealed class AuthorizationResult {
    data object Approved : AuthorizationResult()
    data class Denied(val reason: String) : AuthorizationResult()
}
