package com.calcvault.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.calcvault.data.model.Message

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>)

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessagesForConversation(convId: String): LiveData<List<Message>>

    @Query("SELECT * FROM messages WHERE status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<Message>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: Message.MessageStatus)

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteConversation(convId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
