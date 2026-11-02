package com.mailchat.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mailchat.data.model.SyncState

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE accountId = :accountId AND folder = :folder")
    suspend fun get(accountId: Long, folder: String): SyncState?

    @Upsert
    suspend fun save(state: SyncState)

    @Query("DELETE FROM sync_state WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long)

    @Query("DELETE FROM sync_state")
    suspend fun deleteAll()
}
