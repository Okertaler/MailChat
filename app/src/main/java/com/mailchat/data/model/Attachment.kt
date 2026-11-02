package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"])
    ]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long = 0,
    val fileName: String,
    val mimeType: String,
    val uri: String,
    val isImage: Boolean,
    val sizeBytes: Long,
    val sortOrder: Int = 0,
    val contentId: String = ""
)