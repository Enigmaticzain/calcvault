package com.calcvault.utils

/** Stable identities for CalcVault's private two-person pair. */
object PairedUserIdentity {
    const val USER_A = "USER_A"
    const val USER_B = "USER_B"
    const val ZAIN_ROLE = "zain"
    const val SANU_ROLE = "sanu"

    /** Normalize the current and older display-name/role identifiers to stable pair IDs. */
    fun stableId(value: String?): String? = when (value?.trim()?.lowercase()) {
        "user_a", "a", ZAIN_ROLE -> USER_A
        "user_b", "b", SANU_ROLE -> USER_B
        else -> null
    }

    fun roleFor(value: String?): String? = when (stableId(value)) {
        USER_A -> ZAIN_ROLE
        USER_B -> SANU_ROLE
        else -> null
    }

    fun displayName(value: String?): String? = when (stableId(value)) {
        USER_A -> "Zain"
        USER_B -> "Sanu"
        else -> null
    }

    fun partnerId(value: String?): String? = when (stableId(value)) {
        USER_A -> USER_B
        USER_B -> USER_A
        else -> null
    }

    fun sameUser(left: String?, right: String?): Boolean {
        val leftId = stableId(left)
        val rightId = stableId(right)
        if (leftId != null || rightId != null) return leftId != null && leftId == rightId
        return !left.isNullOrBlank() && !right.isNullOrBlank() && left.trim().equals(right.trim(), ignoreCase = true)
    }
}
