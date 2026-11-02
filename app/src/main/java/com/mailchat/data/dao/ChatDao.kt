package com.mailchat.data.dao

import androidx.room.*
import com.mailchat.data.model.Chat
import com.mailchat.data.model.ChatWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE accountId = :accountId")
    suspend fun getChatsByAccountSync(accountId: Long): List<Chat>

    @Query("SELECT * FROM chats WHERE hidden = 0 ORDER BY favorite DESC, lastMessageTimestamp DESC")
    fun getAllChats(): Flow<List<Chat>>

    @Query(
        "SELECT c.*, COUNT(m.id) AS messageCount, " +
            "COALESCE(SUM(m.isTodo), 0) AS todoCount, " +
            "COALESCE(SUM(m.isProcessed), 0) AS processedCount " +
            "FROM chats c LEFT JOIN messages m ON m.chatId = c.id " +
            "WHERE c.hidden = 0 GROUP BY c.id " +
            "ORDER BY c.favorite DESC, c.lastMessageTimestamp DESC"
    )
    fun getAllChatsWithCount(): Flow<List<ChatWithCount>>

    @Query("SELECT * FROM chats WHERE hidden = 1 ORDER BY lastMessageTimestamp DESC")
    fun getHiddenChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE important = 1 AND hidden = 0 ORDER BY lastMessageTimestamp DESC")
    fun getImportantChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChatById(id: Long): Chat?

    @Query("SELECT * FROM chats WHERE participantEmail = :email AND accountId = :accountId LIMIT 1")
    suspend fun findByEmailAndAccount(email: String, accountId: Long): Chat?

    @Query("SELECT COUNT(*) FROM chats WHERE favorite = 1")
    suspend fun getFavoriteCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat): Long

    @Update
    suspend fun updateChat(chat: Chat)

    @Query("UPDATE chats SET hidden = :hidden WHERE id = :chatId")
    suspend fun setChatHidden(chatId: Long, hidden: Boolean)

    @Query("UPDATE chats SET favorite = :favorite WHERE id = :chatId")
    suspend fun setChatFavorite(chatId: Long, favorite: Boolean)

    @Query("UPDATE chats SET important = :important WHERE id = :chatId")
    suspend fun setChatImportant(chatId: Long, important: Boolean)

    @Query("UPDATE chats SET backgroundImageUri = :newUri WHERE backgroundImageUri = :oldUri")
    suspend fun replaceBackgroundImageUri(oldUri: String, newUri: String)

    @Delete
    suspend fun deleteChat(chat: Chat)
}