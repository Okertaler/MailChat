package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEmbedding(
    @PrimaryKey
    val messageId: Long,
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is MessageEmbedding && other.messageId == messageId
    override fun hashCode(): Int = messageId.hashCode()
}
