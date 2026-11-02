package com.mailchat.data.model

import androidx.room.Entity

@Entity(tableName = "sync_state", primaryKeys = ["accountId", "folder"])
data class SyncState(
    val accountId: Long,
    val folder: String,
    val uidValidity: Long,
    val lastUid: Long,
    val periodStart: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
