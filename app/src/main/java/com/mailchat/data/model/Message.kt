package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EmailAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["accountId"]),
        Index(value = ["timestamp"]),
        Index(value = ["messageId"])
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long = 0,
    val accountId: Long = 0,
    val from: String,
    val subject: String,
    val body: String,
    val bodyFull: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isIncoming: Boolean = true,
    val messageId: String = "",
    val isSpam: Boolean = false,
    val spamCategory: String = "",
    val summary: String = "",
    val summaryLanguage: String = "",
    val isProcessed: Boolean = false,
    val isTodo: Boolean = false,
    val isStarred: Boolean = false,
    val cc: String = "",
    val bcc: String = "",
    val customTag: String = "",
    val isEncrypted: Boolean = false
)