package com.mailchat.data.repository

import com.mailchat.data.dao.ChatDao
import com.mailchat.data.model.Chat
import com.mailchat.data.model.ChatWithCount
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    suspend fun getChatsByAccountSync(accountId: Long): List<Chat> = chatDao.getChatsByAccountSync(accountId)

    fun getAllChats(): Flow<List<Chat>> = chatDao.getAllChats()

    fun getAllChatsWithCount(): Flow<List<ChatWithCount>> = chatDao.getAllChatsWithCount()

    fun getHiddenChats(): Flow<List<Chat>> = chatDao.getHiddenChats()

    fun getImportantChats(): Flow<List<Chat>> = chatDao.getImportantChats()

    suspend fun getChatById(id: Long): Chat? = chatDao.getChatById(id)

    suspend fun findByEmailAndAccount(email: String, accountId: Long): Chat? =
        chatDao.findByEmailAndAccount(email, accountId)

    suspend fun getFavoriteCount(): Int = chatDao.getFavoriteCount()

    suspend fun insertChat(chat: Chat): Long = chatDao.insertChat(chat)

    suspend fun updateChat(chat: Chat) = chatDao.updateChat(chat)

    suspend fun setChatHidden(chatId: Long, hidden: Boolean) = chatDao.setChatHidden(chatId, hidden)

    suspend fun setChatFavorite(chatId: Long, favorite: Boolean) = chatDao.setChatFavorite(chatId, favorite)

    suspend fun setChatImportant(chatId: Long, important: Boolean) = chatDao.setChatImportant(chatId, important)

    suspend fun replaceBackgroundImageUri(oldUri: String, newUri: String) =
        chatDao.replaceBackgroundImageUri(oldUri, newUri)

    suspend fun deleteChat(chat: Chat) = chatDao.deleteChat(chat)
}