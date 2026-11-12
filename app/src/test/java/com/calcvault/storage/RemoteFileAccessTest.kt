package com.calcvault.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileAccessTest {
    @Test
    fun stablePairIdsAndLegacyNamesAuthorizeTheSameOwner() {
        val zainFile = UserOwnershipManager.RemoteFileRef(
            id = "file-z",
            name = "large.bin",
            size = 2_000_000_000L,
            owner = "Zain",
            accessState = UserOwnershipManager.AccessState.AVAILABLE
        )
        assertTrue(zainFile.canAccess("USER_A"))
        assertFalse(zainFile.canAccess("USER_B"))

        val sanuFile = zainFile.copy(id = "file-s", owner = "USER_B")
        assertTrue(sanuFile.canAccess("Sanu"))
        assertTrue(sanuFile.canAccess("USER_A"))
    }

    @Test
    fun explicitGrantIsRequiredAndHonorsExpiryAndRevocation() {
        val granted = UserOwnershipManager.RemoteFileRef(
            id = "file-g",
            name = "private.bin",
            size = 10,
            owner = "USER_A",
            accessState = UserOwnershipManager.AccessState.AVAILABLE,
            userGrantExpiriesMs = mapOf("Sanu" to 500L)
        )
        assertTrue(granted.canAccess("USER_B", nowMs = 499L))
        assertFalse(granted.canAccess("USER_B", nowMs = 500L))
        assertFalse(granted.copy(revoked = true).canAccess("Sanu", nowMs = 400L))
        assertFalse(granted.copy(accessState = UserOwnershipManager.AccessState.LOCKED).canAccess("Sanu", nowMs = 400L))
    }
}
