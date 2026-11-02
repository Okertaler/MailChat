package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "drafts",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Draft(
    @PrimaryKey val chatId: Long,
    val subject: String = "",
    val body: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
