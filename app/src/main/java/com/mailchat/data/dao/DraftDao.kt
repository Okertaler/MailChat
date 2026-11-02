package com.mailchat.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mailchat.data.model.Draft

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE chatId = :chatId")
    suspend fun getDraft(chatId: Long): Draft?

    @Upsert
    suspend fun saveDraft(draft: Draft)

    @Query("DELETE FROM drafts WHERE chatId = :chatId")
    suspend fun deleteDraft(chatId: Long)
}
