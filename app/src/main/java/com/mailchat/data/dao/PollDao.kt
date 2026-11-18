package com.mailchat.data.dao

import androidx.room.*
import com.mailchat.data.model.Poll
import com.mailchat.data.model.PollVote
import kotlinx.coroutines.flow.Flow

@Dao
interface PollDao {
    // --- Polls ---

    @Query("SELECT * FROM polls WHERE id = :pollId AND accountId = :accountId")
    suspend fun getPoll(pollId: String, accountId: Long): Poll?

    /** Look up a poll by ID across ALL accounts (for Thunderbird replies where the quoted
     *  poll carries a per-recipient poll ID that only exists in the recipient's account). */
    @Query("SELECT * FROM polls WHERE id = :pollId LIMIT 1")
    suspend fun getPollAnyAccount(pollId: String): Poll?

    @Query("SELECT * FROM polls WHERE chatId = :chatId AND accountId = :accountId ORDER BY createdAt DESC")
    fun getPollsByChat(chatId: Long, accountId: Long): Flow<List<Poll>>

    @Query("SELECT * FROM polls WHERE chatId = :chatId AND accountId = :accountId ORDER BY createdAt DESC")
    suspend fun getPollsByChatSync(chatId: Long, accountId: Long): List<Poll>

    // All per-recipient poll-IDs of one logical poll (the creator's group), in creation order.
    @Query("SELECT * FROM polls WHERE groupId = :groupId AND accountId = :accountId ORDER BY createdAt ASC")
    fun getPollsByGroup(groupId: String, accountId: Long): Flow<List<Poll>>

    @Query("SELECT * FROM polls WHERE groupId = :groupId AND accountId = :accountId ORDER BY createdAt ASC")
    suspend fun getPollsByGroupSync(groupId: String, accountId: Long): List<Poll>

    @Query("SELECT * FROM polls WHERE accountId = :accountId AND status = 'open'")
    suspend fun getOpenPolls(accountId: Long): List<Poll>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoll(poll: Poll)

    @Update
    suspend fun updatePoll(poll: Poll)

    @Query("DELETE FROM polls WHERE id = :pollId AND accountId = :accountId")
    suspend fun deletePoll(pollId: String, accountId: Long)

    @Query("DELETE FROM polls WHERE chatId = :chatId")
    suspend fun deletePollsByChat(chatId: Long)

    @Query("DELETE FROM polls WHERE accountId = :accountId")
    suspend fun deletePollsByAccount(accountId: Long)

    // --- Votes ---

    @Query("SELECT * FROM poll_votes WHERE pollId = :pollId AND accountId = :accountId")
    fun getVotes(pollId: String, accountId: Long): Flow<List<PollVote>>

    @Query("SELECT * FROM poll_votes WHERE pollId = :pollId AND accountId = :accountId")
    suspend fun getVotesSync(pollId: String, accountId: Long): List<PollVote>

    // Votes for a set of poll-IDs across ALL accounts (the creator's aggregated view: the same
    // vote can be recorded in several accounts, so the caller dedupes by voterEmail).
    @Query("SELECT * FROM poll_votes WHERE pollId IN (:pollIds)")
    suspend fun getVotesForPollsAllAccounts(pollIds: List<String>): List<PollVote>

    // Reactive version (re-emits when a vote is inserted/updated), so the poll view updates
    // live without reopening the email.
    @Query("SELECT * FROM poll_votes WHERE pollId IN (:pollIds)")
    fun getVotesForPollsAllAccountsFlow(pollIds: List<String>): Flow<List<PollVote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: PollVote)

    @Query("DELETE FROM poll_votes WHERE pollId = :pollId AND accountId = :accountId")
    suspend fun deleteVotes(pollId: String, accountId: Long)

    @Query("DELETE FROM poll_votes WHERE accountId = :accountId")
    suspend fun deleteVotesByAccount(accountId: Long)
}
