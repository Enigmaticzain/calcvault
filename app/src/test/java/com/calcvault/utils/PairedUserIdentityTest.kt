package com.calcvault.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairedUserIdentityTest {
    @Test
    fun roleNamesAndLegacyIdsMapToStablePairIds() {
        assertEquals(PairedUserIdentity.USER_A, PairedUserIdentity.stableId("Zain"))
        assertEquals(PairedUserIdentity.USER_A, PairedUserIdentity.stableId("user_a"))
        assertEquals(PairedUserIdentity.USER_B, PairedUserIdentity.stableId("Sanu"))
        assertEquals(PairedUserIdentity.USER_B, PairedUserIdentity.stableId("USER_B"))
        assertNull(PairedUserIdentity.stableId("unknown"))
    }

    @Test
    fun partnerAndDisplayNamesStayConsistentAcrossThePair() {
        assertEquals(PairedUserIdentity.USER_B, PairedUserIdentity.partnerId("Zain"))
        assertEquals(PairedUserIdentity.USER_A, PairedUserIdentity.partnerId("Sanu"))
        assertEquals("Zain", PairedUserIdentity.displayName(PairedUserIdentity.USER_A))
        assertEquals("Sanu", PairedUserIdentity.displayName(PairedUserIdentity.USER_B))
    }
}
