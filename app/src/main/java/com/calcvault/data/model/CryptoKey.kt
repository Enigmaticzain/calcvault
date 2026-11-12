package com.calcvault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crypto_keys")
data class CryptoKey(
    @PrimaryKey
    val id: String,
    val keyType: String,        // "identity", "signed_prekey", "one_time_prekey"
    val publicKey: ByteArray,
    val privateKey: ByteArray,  // Encrypted with master password before storage
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
