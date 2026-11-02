package com.mailchat.data.dao

import androidx.room.*
import com.mailchat.data.model.Attachment
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY sortOrder ASC")
    fun getAttachmentsByMessage(messageId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getAttachmentsByMessageSync(messageId: Long): List<Attachment>

    @Query(
        "SELECT * FROM attachments " +
            "WHERE uri LIKE '%:%' AND uri NOT LIKE 'content://%' AND uri NOT LIKE 'file://%'"
    )
    suspend fun getBrokenUriAttachments(): List<Attachment>

    @Query("UPDATE attachments SET uri = :uri WHERE id = :id")
    suspend fun updateUri(id: Long, uri: String)

    @Query(
        "SELECT a.* FROM attachments a " +
            "INNER JOIN messages m ON a.messageId = m.id " +
            "WHERE m.chatId = :chatId ORDER BY a.sortOrder ASC"
    )
    suspend fun getAttachmentsByChatSync(chatId: Long): List<Attachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsByMessage(messageId: Long)

    @Query("SELECT * FROM attachments WHERE messageId IN (SELECT id FROM messages WHERE accountId = :accountId)")
    suspend fun getAttachmentsByAccount(accountId: Long): List<Attachment>
}