package com.mailchat.data.model

import androidx.room.Embedded

data class ChatWithCount(
    @Embedded val chat: Chat,
    val messageCount: Int,
    val todoCount: Int = 0,
    val processedCount: Int = 0
)
