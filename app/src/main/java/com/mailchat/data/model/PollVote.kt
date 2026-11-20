package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.Index

/**
 * A single vote in a poll. The same vote is recorded in every account that sees the answer email,
 * so the primary key is (pollId, accountId, voterEmail). [optionIndices] is a JSON array of the
 * selected option indices (0-based) — used for checkbox polls. [values] is a JSON array of numbers
 * aligned to the options (values[i] = the number entered for option i) — used for numeric polls.
 */
@Entity(
    tableName = "poll_votes",
    primaryKeys = ["pollId", "accountId", "voterEmail"],
    indices = [Index(value = ["pollId"]), Index(value = ["accountId"])]
)
data class PollVote(
    val pollId: String,
    val accountId: Long,
    val voterEmail: String,
    val optionIndices: String = "[]",
    val values: String = "[]",
    val messageId: String = "",
    val answeredAt: Long = 0
)
