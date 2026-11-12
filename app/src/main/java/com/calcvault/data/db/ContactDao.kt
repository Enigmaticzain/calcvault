package com.calcvault.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.calcvault.data.model.Contact

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact)

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContact(contactId: String): Contact?

    @Delete
    suspend fun delete(contact: Contact)
}
