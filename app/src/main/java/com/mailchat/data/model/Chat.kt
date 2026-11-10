package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    foreignKeys = [
        ForeignKey(
            entity = EmailAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["participantEmail", "accountId"], unique = true),
        Index(value = ["accountId"])
    ]
)
data class Chat(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val participantEmail: String,
    val participantName: String,
    val accountId: Long,
    val lastMessageTimestamp: Long = 0,
    val lastMessageBody: String = "",
    val lastMessageSubject: String = "",
    val unreadCount: Int = 0,
    val backgroundImageUri: String? = null,
    val hidden: Boolean = false,
    val favorite: Boolean = false,
    val important: Boolean = false,
    val noReply: Int = 0,
    val customTag: String = "",
    /** For group chats: when true, outgoing emails are encrypted for all members that have a key. */
    val isEncrypted: Boolean = false
)