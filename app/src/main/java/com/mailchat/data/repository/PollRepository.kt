package com.mailchat.data.repository

import com.mailchat.data.dao.PollDao
import com.mailchat.data.model.Poll
import com.mailchat.data.model.PollVote
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PollRepository @Inject constructor(
    private val pollDao: PollDao
) {
    suspend fun getPoll(pollId: String, accountId: Long): Poll? = pollDao.getPoll(pollId, accountId)

    suspend fun getPollAnyAccount(pollId: String): Poll? = pollDao.getPollAnyAccount(pollId)

    fun getPollsByChat(chatId: Long, accountId: Long): Flow<List<Poll>> =
        pollDao.getPollsByChat(chatId, accountId)

    suspend fun getPollsByChatSync(chatId: Long, accountId: Long): List<Poll> =
        pollDao.getPollsByChatSync(chatId, accountId)

    fun getPollsByGroup(groupId: String, accountId: Long): Flow<List<Poll>> =
        pollDao.getPollsByGroup(groupId, accountId)

    suspend fun getPollsByGroupSync(groupId: String, accountId: Long): List<Poll> =
        pollDao.getPollsByGroupSync(groupId, accountId)

    suspend fun getOpenPolls(accountId: Long): List<Poll> = pollDao.getOpenPolls(accountId)

    suspend fun insertPoll(poll: Poll) = pollDao.insertPoll(poll)

    suspend fun updatePoll(poll: Poll) = pollDao.updatePoll(poll)

    suspend fun deletePoll(pollId: String, accountId: Long) = pollDao.deletePoll(pollId, accountId)

    suspend fun deletePollsByChat(chatId: Long) = pollDao.deletePollsByChat(chatId)

    suspend fun deletePollsByAccount(accountId: Long) = pollDao.deletePollsByAccount(accountId)

    fun getVotes(pollId: String, accountId: Long): Flow<List<PollVote>> =
        pollDao.getVotes(pollId, accountId)

    suspend fun getVotesSync(pollId: String, accountId: Long): List<PollVote> =
        pollDao.getVotesSync(pollId, accountId)

    suspend fun getVotesForPollsAllAccounts(pollIds: List<String>): List<PollVote> =
        pollDao.getVotesForPollsAllAccounts(pollIds)

    fun getVotesForPollsAllAccountsFlow(pollIds: List<String>): Flow<List<PollVote>> =
        pollDao.getVotesForPollsAllAccountsFlow(pollIds)

    suspend fun insertVote(vote: PollVote) = pollDao.insertVote(vote)

    suspend fun deleteVotes(pollId: String, accountId: Long) = pollDao.deleteVotes(pollId, accountId)

    suspend fun deleteVotesByAccount(accountId: Long) = pollDao.deleteVotesByAccount(accountId)
}
