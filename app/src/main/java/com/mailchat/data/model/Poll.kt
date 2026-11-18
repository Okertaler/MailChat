package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.Index

/**
 * A poll (Umfrage) that was created in a chat. The same poll [id] can exist in several accounts
 * (the creator's account and every recipient's account), so the primary key is (id, accountId).
 *
 * The poll is stored as a plain-text email (see [com.mailchat.util.PollFormat]) so it also works
 * with regular email programs. The [options] field is a JSON array of the option texts.
 *
 * The creator sends ONE poll email per recipient, each with its own [id] (poll-ID). All of those
 * per-recipient poll-IDs belong to the same logical poll, linked by [groupId]. [recipientEmail]
 * records who a per-recipient poll-ID was sent to (only set in the creator's records). The
 * creator manages the whole group (aggregated results, auto-close, result emails) until it is
 * closed. Recipient-side copies have an empty [groupId] (they are standalone single polls).
 */
@Entity(
    tableName = "polls",
    primaryKeys = ["id", "accountId"],
    indices = [Index(value = ["chatId"]), Index(value = ["accountId"]), Index(value = ["groupId"])]
)
data class Poll(
    val id: String,
    val accountId: Long,
    val chatId: Long = 0,
    val groupId: String = "",
    val recipientEmail: String = "",
    val title: String = "",
    val question: String = "",
    val options: String = "[]",
    val multi: Boolean = false,
    val creatorEmail: String = "",
    val pollMessageId: String = "",
    val status: String = STATUS_OPEN,
    val closedBy: String = "",
    val createdAt: Long = 0,
    val closedAt: Long = 0
) {
    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_CLOSED = "closed"
        const val CLOSED_BY_CREATOR = "creator"
        const val CLOSED_BY_ALL_ANSWERED = "all_answered"
    }
}
