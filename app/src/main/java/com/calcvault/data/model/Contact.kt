package com.calcvault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val id: String, // Unique user ID
    val displayName: String,
    val publicKey: ByteArray, // Their public key for encryption
    val onionAddress: String? = null, // Their .onion address (if using Tor)
    val lastSeen: Long = 0,
    val isVerified: Boolean = false, // Have you verified keys in person?
    val createdAt: Long = System.currentTimeMillis()
)
