package com.mailchat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mailchat.data.model.MessageEmbedding

@Dao
interface MessageEmbeddingDao {
    @Query("SELECT * FROM message_embeddings WHERE messageId IN (:ids)")
    suspend fun getByMessageIds(ids: List<Long>): List<MessageEmbedding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: MessageEmbedding)

    @Query("DELETE FROM message_embeddings WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: Long)

    @Query("DELETE FROM message_embeddings")
    suspend fun clear()
}
