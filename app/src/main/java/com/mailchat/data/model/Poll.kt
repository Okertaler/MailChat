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
 * [multi] marks a multiple-choice poll (several options can be selected). [numberMode] marks a
 * numeric poll: instead of selecting options, each respondent enters a NUMBER per option and the
 * result is the SUM of those numbers per option (see [com.mailchat.data.model.PollVote.values]).
 *
 * [personMode] marks a person-related poll: the result is a TABLE where the persons (voters) form
 * the rows and the options form the columns, and the bottom row is the sum of the columns. The
 * answer format is unchanged (checkbox or number per option) — only the result view differs.
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
    val numberMode: Boolean = false,
    val personMode: Boolean = false,
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
