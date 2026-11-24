package com.mailchat.data.model

import androidx.room.Embedded

data class ChatWithCount(
    @Embedded val chat: Chat,
    val messageCount: Int,
    val todoCount: Int = 0,
    val processedCount: Int = 0,
    val stickyNoteCount: Int = 0
)

/** The number of sticky notes of a single [color] in one chat (for the color filter). */
data class StickyNoteColorCount(
    val chatId: Long,
    val color: String,
    val count: Int
)
