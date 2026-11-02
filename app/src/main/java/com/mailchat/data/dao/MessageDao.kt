package com.mailchat.data.dao

import androidx.room.*
import com.mailchat.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChat(chatId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId")
    suspend fun getMessagesByChatSync(chatId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessageByChat(chatId: Long): Message?

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): Message?

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND isRead = 0")
    suspend fun markAllRead(chatId: Long)

    @Query("UPDATE messages SET isRead = 0 WHERE chatId = :chatId AND isRead = 1")
    suspend fun markAllUnread(chatId: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0")
    suspend fun getUnreadCount(chatId: Long): Int

    @Query("SELECT DISTINCT chatId FROM messages WHERE summary != ''")
    suspend fun getChatIdsWithSummary(): List<Long>

    data class SpamCategoryEntry(val chatId: Long, val spamCategory: String)

    @Query("SELECT DISTINCT chatId, spamCategory FROM messages WHERE isSpam = 1 AND spamCategory != ''")
    fun getSpamCategories(): Flow<List<SpamCategoryEntry>>

    data class CustomTagEntry(val chatId: Long, val customTag: String)

    @Query("SELECT DISTINCT chatId, customTag FROM messages WHERE customTag != ''")
    fun getCustomTags(): Flow<List<CustomTagEntry>>

    @Query("SELECT COUNT(*) FROM messages WHERE messageId = :messageId AND messageId != ''")
    suspend fun countByMessageId(messageId: String): Int

    @Query(
        "SELECT * FROM messages WHERE accountId = :accountId AND isIncoming = 1 AND messageId != '' " +
            "AND bodyFull LIKE '%cid:%' " +
            "AND NOT EXISTS (SELECT 1 FROM attachments a WHERE a.messageId = messages.id AND a.contentId != '') " +
            "ORDER BY timestamp DESC LIMIT 30"
    )
    suspend fun getBrokenCidMessages(accountId: Long): List<Message>

    @Query(
        "SELECT * FROM messages WHERE timestamp >= :since AND (subject LIKE '%' || :q || '%' OR bodyFull LIKE '%' || :q || '%' OR `from` LIKE '%' || :q || '%') " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun searchAll(q: String, since: Long, limit: Int): List<Message>

    @Query(
        "SELECT * FROM messages WHERE timestamp >= :since AND (isSpam = 1 OR isTodo = 1) " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getFlaggedMessages(since: Long, limit: Int): List<Message>

    @Query(
        "SELECT m.* FROM messages m LEFT JOIN message_embeddings e ON e.messageId = m.id " +
            "WHERE e.messageId IS NULL AND m.timestamp >= :since ORDER BY m.timestamp DESC LIMIT :limit"
    )
    suspend fun getMessagesNeedingEmbedding(since: Long, limit: Int): List<Message>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND `from` = :from AND subject = :subject AND timestamp = :timestamp")
    suspend fun countByFromSubjectTimestamp(chatId: Long, from: String, subject: String, timestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Update
    suspend fun updateMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChat(chatId: Long)

    @Query("UPDATE messages SET chatId = :newChatId WHERE chatId = :oldChatId")
    suspend fun moveMessagesToChat(oldChatId: Long, newChatId: Long)

    /**
     * Moves all INCOMING messages from [from] in [oldChatId] to [newChatId]. Used to split a
     * previously merged chat back apart: the messages of one sender are moved to their own chat.
     * Returns the number of moved messages.
     */
    @Query(
        "UPDATE messages SET chatId = :newChatId " +
            "WHERE chatId = :oldChatId AND isIncoming = 1 AND LOWER(`from`) = LOWER(:from)"
    )
    suspend fun moveIncomingByFromToChat(oldChatId: Long, newChatId: Long, from: String): Int
}