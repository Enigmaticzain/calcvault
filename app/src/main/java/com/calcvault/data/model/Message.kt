package com.calcvault.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val id: String, // UUID
    val conversationId: String, // Which chat this belongs to
    val senderId: String, // Who sent it
    val recipientId: String, // Who receives it
    val encryptedContent: ByteArray, // ENCRYPTED message body
    val contentType: String = "text", // text, image, file
    val timestamp: Long, // When sent
    val status: MessageStatus = MessageStatus.PENDING,
    val isIncoming: Boolean // Sent or received?
) {
    enum class MessageStatus {
        PENDING, // Not yet sent
        SENT, // Sent to recipient's server
        DELIVERED, // Recipient's server acknowledged
        READ, // Recipient opened it
        FAILED // Failed to send
    }
}
