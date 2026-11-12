package com.calcvault.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileGrantTest {
    @Test
    fun requiresExplicitRecipientGrantEvenWhenReferenceIsMarkedAvailable() {
        val ref = UserOwnershipManager.RemoteFileRef(
            id = "opaque-file-id",
            name = "private name",
            size = 42L,
            owner = "owner",
            accessState = UserOwnershipManager.AccessState.AVAILABLE
        )

        assertFalse(ref.canAccess("recipient", nowMs = 100L))
        assertTrue(ref.canAccess("owner", nowMs = 100L))
    }

    @Test
    fun recipientsCanHaveIndependentExpiryAndRevocationStates() {
        val ref = UserOwnershipManager.RemoteFileRef(
            id = "opaque-file-id",
            name = "private name",
            size = 42L,
            owner = "owner",
            accessState = UserOwnershipManager.AccessState.AVAILABLE,
            userGrantExpiriesMs = mapOf("first" to 200L, "second" to 0L)
        )

        assertFalse(ref.canAccess("first", nowMs = 200L))
        assertTrue(ref.canAccess("second", nowMs = 200L))
        assertFalse(ref.copy(userGrantExpiriesMs = mapOf("first" to 200L)).canAccess("second", nowMs = 100L))
    }

    @Test
    fun anExplicitGrantCanExpireOrBeRevoked() {
        val granted = UserOwnershipManager.RemoteFileRef(
            id = "opaque-file-id",
            name = "private name",
            size = 42L,
            owner = "owner",
            accessState = UserOwnershipManager.AccessState.AVAILABLE,
            allowedUsers = listOf("recipient"),
            expiresAtMs = 200L
        )

        assertTrue(granted.canAccess("recipient", nowMs = 199L))
        assertFalse(granted.canAccess("recipient", nowMs = 200L))
        assertTrue(granted.isGrantExpired(nowMs = 200L))
        assertFalse(granted.copy(revoked = true).canAccess("recipient", nowMs = 199L))
    }
}
