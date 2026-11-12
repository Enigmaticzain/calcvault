package com.calcvault.storage

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoUserOwnershipTest {
    @Test
    fun bothStableUserIdsCanUseSharedFilesButPartnerCannotAccessZainsLargeFiles() {
        val ownership = UserOwnershipManager(File("unused-test-root"))
        assertTrue(ownership.canAccessDirectly("USER_A", UserOwnershipManager.FileOwner.SHARED))
        assertTrue(ownership.canAccessDirectly("USER_B", UserOwnershipManager.FileOwner.SHARED))
        assertTrue(ownership.canAccessDirectly("Sanu", UserOwnershipManager.FileOwner.SANU))
        assertTrue(ownership.canAccessDirectly("Zain", UserOwnershipManager.FileOwner.ZAIN))
        assertFalse(ownership.canAccessDirectly("USER_B", UserOwnershipManager.FileOwner.ZAIN))
        assertFalse(ownership.canAccessDirectly("unknown", UserOwnershipManager.FileOwner.SHARED))
    }
}
