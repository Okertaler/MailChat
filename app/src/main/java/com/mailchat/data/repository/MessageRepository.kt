package com.mailchat.data.repository

import com.mailchat.data.dao.AttachmentDao
import com.mailchat.data.dao.MessageDao
import com.mailchat.data.dao.MessageEmbeddingDao
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.Message
import com.mailchat.data.model.MessageEmbedding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val messageEmbeddingDao: MessageEmbeddingDao
) {
    fun getMessagesByChat(chatId: Long): Flow<List<Message>> = messageDao.getMessagesByChat(chatId)

    suspend fun getMessagesByChatSync(chatId: Long): List<Message> = messageDao.getMessagesByChatSync(chatId)

    suspend fun getLatestMessageByChat(chatId: Long): Message? = messageDao.getLatestMessageByChat(chatId)

    suspend fun getMessageById(id: Long): Message? = messageDao.getMessageById(id)

    suspend fun markAllRead(chatId: Long) = messageDao.markAllRead(chatId)

    suspend fun getChatIdsWithSummary(): List<Long> = messageDao.getChatIdsWithSummary()

    fun getSpamCategories(): Flow<Map<Long, String>> = messageDao.getSpamCategories().map { list ->
        list.groupBy { it.chatId }.mapValues { (_, entries) ->
            entries.map { it.spamCategory }.maxByOrNull { cat ->
                when (cat) {
                    "phishing" -> 3
                    "unwanted_ad" -> 2
                    "newsletter" -> 1
                    else -> 0
                }
            } ?: ""
        }
    }

    fun getCustomTags(): Flow<Map<Long, String>> = messageDao.getCustomTags().map { list ->
        list.associate { it.chatId to it.customTag }
    }

    suspend fun countByMessageId(messageId: String): Int = messageDao.countByMessageId(messageId)

    suspend fun getBrokenCidMessages(accountId: Long): List<Message> = messageDao.getBrokenCidMessages(accountId)

    suspend fun searchAll(q: String, since: Long, limit: Int): List<Message> = messageDao.searchAll(q, since, limit)

    suspend fun getFlaggedMessages(since: Long, limit: Int): List<Message> = messageDao.getFlaggedMessages(since, limit)

    suspend fun getEmbeddingsByMessageIds(ids: List<Long>): List<MessageEmbedding> =
        messageEmbeddingDao.getByMessageIds(ids)

    suspend fun upsertEmbedding(embedding: MessageEmbedding) = messageEmbeddingDao.upsert(embedding)

    suspend fun deleteEmbeddingForMessage(messageId: Long) = messageEmbeddingDao.deleteForMessage(messageId)

    suspend fun getMessagesNeedingEmbedding(since: Long, limit: Int): List<Message> =
        messageDao.getMessagesNeedingEmbedding(since, limit)

    suspend fun countByFromSubjectTimestamp(chatId: Long, from: String, subject: String, timestamp: Long): Int =
        messageDao.countByFromSubjectTimestamp(chatId, from, subject, timestamp)

    suspend fun insertMessage(message: Message): Long = messageDao.insertMessage(message)

    suspend fun updateMessage(message: Message) = messageDao.updateMessage(message)

    suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)

    suspend fun deleteMessagesByChat(chatId: Long) = messageDao.deleteMessagesByChat(chatId)

    suspend fun moveMessagesToChat(oldChatId: Long, newChatId: Long) =
        messageDao.moveMessagesToChat(oldChatId, newChatId)

    suspend fun moveIncomingByFromToChat(oldChatId: Long, newChatId: Long, from: String): Int =
        messageDao.moveIncomingByFromToChat(oldChatId, newChatId, from)

    fun getAttachmentsByMessage(messageId: Long): Flow<List<Attachment>> =
        attachmentDao.getAttachmentsByMessage(messageId)

    suspend fun getAttachmentsByMessageSync(messageId: Long): List<Attachment> =
        attachmentDao.getAttachmentsByMessageSync(messageId)

    suspend fun getAttachmentsByChatSync(chatId: Long): List<Attachment> =
        attachmentDao.getAttachmentsByChatSync(chatId)

    suspend fun insertAttachment(attachment: Attachment): Long =
        attachmentDao.insertAttachment(attachment)

    suspend fun deleteAttachmentsByMessage(messageId: Long) =
        attachmentDao.deleteAttachmentsByMessage(messageId)

    suspend fun getAttachmentsByAccount(accountId: Long): List<Attachment> =
        attachmentDao.getAttachmentsByAccount(accountId)

    suspend fun getBrokenUriAttachments(): List<Attachment> =
        attachmentDao.getBrokenUriAttachments()

    suspend fun updateAttachmentUri(id: Long, uri: String) =
        attachmentDao.updateUri(id, uri)
}