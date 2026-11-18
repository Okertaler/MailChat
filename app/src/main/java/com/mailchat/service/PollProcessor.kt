package com.mailchat.service

import android.content.Context
import com.mailchat.data.model.Chat
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.model.Message
import com.mailchat.data.model.Poll
import com.mailchat.data.model.PollVote
import com.mailchat.data.repository.AccountRepository
import com.mailchat.data.repository.ChatRepository
import com.mailchat.data.repository.MessageRepository
import com.mailchat.data.repository.PollRepository
import com.mailchat.network.SmtpService
import com.mailchat.util.EmailGrouping
import com.mailchat.util.PollFormat
import com.mailchat.util.SettingsUtils
import com.mailchat.util.stripHtml
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects polls and poll answers in incoming/sent messages, records votes, renders the results
 * and handles the automatic close ("all have answered") plus the result email.
 *
 * Detection (see [PollFormat]):
 *  1. `[[poll-answer:<id>:<indices>]]`  → a MailChat answer.
 *  2. `[[poll:<id>:<m|s>]]`             → the original poll (creates the Poll record) or a
 *     reference to an existing poll (a regular-email answer when it also contains X marks).
 *  3. No markers, but a reply in a chat with a single open poll + X marks → a markerless
 *     regular-email answer.
 */
@Singleton
class PollProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pollRepository: PollRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val accountRepository: AccountRepository,
    private val smtpService: SmtpService
) {
    // Background scope for sending the result email (must not block the sync loop).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Inspects a newly inserted [message] and, when it is a poll or a poll answer, records it and
     * updates the message (pollId / isPollAnswer). Returns the (possibly updated) message.
     */
    suspend fun processMessageForPolls(message: Message, chat: Chat): Message {
        val rawBody = message.bodyFull.ifBlank { message.body }
        if (rawBody.isBlank()) return message
        // The stored body may be HTML (multipart/alternative prefers the HTML part). The poll
        // format is line-based, so strip HTML before parsing — this also decodes the &gt; quote
        // prefixes used by regular email programs (e.g. Thunderbird) so the checkbox lines and
        // the quoted poll marker are recognized.
        val body = if (rawBody.contains("<")) rawBody.stripHtml() else rawBody

        // 0. Result email (sent when a poll closes): mark it as a poll message so it gets no
        // AI tagging/spam detection, but don't create a poll or record a vote. The marker carries
        // the per-recipient poll-ID, so the recipient's local copy of the poll is closed too
        // (they can't vote anymore).
        PollFormat.parseResultMarker(body)?.let { resultId ->
            val poll = pollRepository.getPoll(resultId, message.accountId)
            if (poll != null && poll.status == Poll.STATUS_OPEN) {
                pollRepository.updatePoll(
                    poll.copy(
                        status = Poll.STATUS_CLOSED,
                        closedBy = Poll.CLOSED_BY_ALL_ANSWERED,
                        closedAt = System.currentTimeMillis()
                    )
                )
            }
            // Update the recipient's poll message to show the result (the bubble then displays
            // it). The ASCII result is the email body minus the result-marker line.
            val asciiResult = body.lineSequence()
                .filter { !it.trim().startsWith("[[poll-result:") }
                .joinToString("\n")
                .trim()
            if (asciiResult.isNotBlank()) updatePollMessageBody(resultId, asciiResult)
            return updateMessageForPoll(message, resultId, isAnswer = false)
        }

        // 1. MailChat answer (explicit answer marker).
        PollFormat.parseAnswerMarker(body)?.let { answer ->
            return recordVote(message, chat, answer.pollId, answer.optionIndices)
        }

        // 2. Poll marker present.
        PollFormat.parsePollMarker(body)?.let { (pollId, multi) ->
            // Try the current account first, then all accounts (Thunderbird replies carry the
            // recipient's per-recipient poll ID, which only exists in the recipient's account).
            val existing = pollRepository.getPoll(pollId, message.accountId)
                ?: pollRepository.getPollAnyAccount(pollId)
            return if (existing == null) {
                createPoll(message, chat, pollId, multi, body)
            } else {
                // A reference to an existing poll: a regular-email answer when it has X marks.
                handleExistingPollReference(message, chat, existing, body)
            }
        }

        // 3. No markers: a markerless regular-email answer (X marks + the chat's single open poll).
        return handleMarkerlessAnswer(message, chat, body)
    }

    // --- Poll creation ---

    private suspend fun createPoll(
        message: Message,
        chat: Chat,
        pollId: String,
        multi: Boolean,
        body: String
    ): Message {
        val data = PollFormat.parsePollBody(body)
        val options = data?.options ?: emptyList()
        val poll = Poll(
            id = pollId,
            accountId = message.accountId,
            chatId = chat.id,
            title = data?.title ?: message.subject,
            question = data?.question ?: "",
            options = optionsToJson(options),
            multi = multi,
            creatorEmail = if (message.isIncoming) message.from else accountEmailFor(message.accountId),
            pollMessageId = message.messageId,
            status = Poll.STATUS_OPEN,
            createdAt = message.timestamp
        )
        pollRepository.insertPoll(poll)
        return updateMessageForPoll(message, pollId, isAnswer = false)
    }

    // --- Vote recording ---

    private suspend fun recordVote(
        message: Message,
        chat: Chat,
        pollId: String,
        optionIndices: List<Int>
    ): Message {
        val poll = pollRepository.getPoll(pollId, message.accountId) ?: return message
        if (optionIndices.isEmpty()) return message
        val votePollId = resolveVotePollId(poll, chat)
        val vote = PollVote(
            pollId = votePollId,
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = indicesToJson(optionIndices),
            messageId = message.messageId,
            answeredAt = message.timestamp
        )
        pollRepository.insertVote(vote)
        val updated = updateMessageForPoll(message, pollId, isAnswer = true)
        checkAutoClose(poll, chat)
        return updated
    }

    /**
     * Resolves the poll ID to use for a vote. For recipient-side single polls (groupId empty),
     * finds the corresponding group poll in the creator's account so the vote is recorded with a
     * poll ID the auto-close check can find across all accounts.
     */
    private suspend fun resolveVotePollId(poll: Poll, chat: Chat): String {
        if (poll.groupId.isNotBlank()) return poll.id
        val creatorAccount = findAccountByEmail(poll.creatorEmail) ?: return poll.id
        return pollRepository.getPollsByChatSync(chat.id, creatorAccount.id)
            .firstOrNull { it.recipientEmail.trim().lowercase() == poll.recipientEmail.trim().lowercase() }
            ?.id ?: poll.id
    }

    /** A body that references an existing poll (quoted marker). An answer when it has X marks. */
    private suspend fun handleExistingPollReference(
        message: Message,
        chat: Chat,
        poll: Poll,
        body: String
    ): Message {
        val marked = PollFormat.parseXMarkedOptions(body)
        if (marked.isEmpty()) return message // just a reference (e.g. a forwarded poll), no vote
        val options = parseOptions(poll.options)
        val indices = PollFormat.matchOptionsToIndices(marked, options)
        if (indices.isEmpty()) return message
        val votePollId = resolveVotePollId(poll, chat)
        val vote = PollVote(
            pollId = votePollId,
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = indicesToJson(indices),
            messageId = message.messageId,
            answeredAt = message.timestamp
        )
        pollRepository.insertVote(vote)
        val updated = updateMessageForPoll(message, poll.id, isAnswer = true)
        checkAutoClose(poll, chat)
        return updated
    }

    /** No markers at all: match X marks against the chat's single open poll. */
    private suspend fun handleMarkerlessAnswer(message: Message, chat: Chat, body: String): Message {
        if (!message.isIncoming) return message
        val marked = PollFormat.parseXMarkedOptions(body)
        if (marked.isEmpty()) return message
        val openPolls = pollRepository.getPollsByChatSync(chat.id, message.accountId)
            .filter { it.status == Poll.STATUS_OPEN }
        if (openPolls.size != 1) return message
        val poll = openPolls[0]
        val options = parseOptions(poll.options)
        val indices = PollFormat.matchOptionsToIndices(marked, options)
        if (indices.isEmpty()) return message
        val vote = PollVote(
            pollId = resolveVotePollId(poll, chat),
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = indicesToJson(indices),
            messageId = message.messageId,
            answeredAt = message.timestamp
        )
        pollRepository.insertVote(vote)
        val updated = updateMessageForPoll(message, poll.id, isAnswer = true)
        checkAutoClose(poll, chat)
        return updated
    }

    // --- Auto close + result ---

    /**
     * Checks whether a poll (or the group it belongs to) can be auto-closed because everyone has
     * answered. The poll is always managed from the CREATOR's account (the "source of truth"), so
     * the result email is sent from there — regardless of which account the triggering vote
     * arrived in (multi-account setup: the creator reads several accounts).
     */
    private suspend fun checkAutoClose(poll: Poll, chat: Chat) {
        if (poll.status != Poll.STATUS_OPEN) return
        val creatorAccount = findAccountByEmail(poll.creatorEmail) ?: return
        // The "source of truth" is the poll in the creator's account. For recipient-side polls
        // (groupId is empty, single-poll copies), the poll ID doesn't exist in the creator's
        // account — find the group poll by matching recipientEmail + chatId instead.
        // For single polls created from Thunderbird (no groupId, empty recipientEmail), fall
        // back to looking up the poll by ID in the creator's account.
        val sourcePoll = (if (poll.groupId.isNotBlank()) {
            pollRepository.getPoll(poll.id, creatorAccount.id)
        } else {
            pollRepository.getPollsByChatSync(chat.id, creatorAccount.id)
                .firstOrNull { it.recipientEmail.trim().lowercase() == poll.recipientEmail.trim().lowercase() }
                ?: pollRepository.getPoll(poll.id, creatorAccount.id)
        }) ?: return
        if (sourcePoll.status != Poll.STATUS_OPEN) return
        if (sourcePoll.groupId.isNotBlank()) {
            checkAutoCloseGroup(sourcePoll, chat, creatorAccount)
        } else {
            checkAutoCloseSingle(sourcePoll, chat)
        }
    }

    /** Group (creator's logical poll): auto-close when every recipient has answered. */
    private suspend fun checkAutoCloseGroup(poll: Poll, chat: Chat, creatorAccount: EmailAccount) {
        val groupPolls = pollRepository.getPollsByGroupSync(poll.groupId, creatorAccount.id)
        if (groupPolls.isEmpty()) {
            return
        }
        // Everyone (all recipients + the creator) must have voted — across ALL accounts.
        val allVotes = pollRepository.getVotesForPollsAllAccounts(groupPolls.map { it.id })
        val voted = allVotes.map { it.voterEmail.trim().lowercase() }.toSet()
        val required = groupPolls.map { it.recipientEmail.trim().lowercase() }
            .filter { it.isNotBlank() }.toMutableSet()
        if (poll.creatorEmail.isNotBlank()) required.add(poll.creatorEmail.trim().lowercase())
        if (required.isNotEmpty() && required.all { it in voted }) {
            closePollGroup(groupPolls, chat, Poll.CLOSED_BY_ALL_ANSWERED)
        }
    }

    /** Single poll (legacy / recipient's copy): auto-close when every required voter answered. */
    private suspend fun checkAutoCloseSingle(poll: Poll, chat: Chat) {
        val allVotes = pollRepository.getVotesForPollsAllAccounts(listOf(poll.id))
        val voted = allVotes.map { it.voterEmail.trim().lowercase() }.toSet()
        val required = requiredVoters(chat, poll.creatorEmail)
        if (required.isNotEmpty() && required.all { it in voted }) {
            closeAndSendResult(poll, chat, Poll.CLOSED_BY_ALL_ANSWERED)
        }
    }

    /**
     * Closes an open poll (manually by the creator) and sends the result email. Returns true when
     * the poll was open and has now been closed.
     */
    suspend fun closePollByCreator(poll: Poll, chat: Chat): Boolean {
        if (poll.status != Poll.STATUS_OPEN) return false
        return closeAndSendResult(poll, chat, Poll.CLOSED_BY_CREATOR)
    }

    /**
     * Closes a whole poll group (all per-recipient poll-IDs) and sends one result email per
     * recipient (each carrying that recipient's poll-ID, which also closes their local copy).
     * Returns true when at least one poll was open and has now been closed.
     */
    suspend fun closePollGroup(polls: List<Poll>, chat: Chat, closedBy: String = Poll.CLOSED_BY_CREATOR): Boolean {
        var anyClosed = false
        for (poll in polls) {
            val current = pollRepository.getPoll(poll.id, poll.accountId) ?: continue
            if (current.status == Poll.STATUS_OPEN) {
                pollRepository.updatePoll(
                    current.copy(
                        status = Poll.STATUS_CLOSED,
                        closedBy = closedBy,
                        closedAt = System.currentTimeMillis()
                    )
                )
                anyClosed = true
            }
        }
        if (!anyClosed) return false
        scope.launch { sendGroupResultEmails(polls, chat) }
        return true
    }

    private suspend fun closeAndSendResult(poll: Poll, chat: Chat, closedBy: String): Boolean {
        // Re-read to avoid a double close (two votes arriving in the same sync).
        val current = pollRepository.getPoll(poll.id, poll.accountId) ?: return false
        if (current.status != Poll.STATUS_OPEN) return false
        val closed = current.copy(
            status = Poll.STATUS_CLOSED,
            closedBy = closedBy,
            closedAt = System.currentTimeMillis()
        )
        pollRepository.updatePoll(closed)
        scope.launch { sendResultEmail(closed, chat) }
        return true
    }

    private suspend fun sendResultEmail(poll: Poll, chat: Chat) {
        val account = accountRepository.getAccountById(poll.accountId) ?: return
        val options = parseOptions(poll.options)
        val votes = pollRepository.getVotesSync(poll.id, poll.accountId)
        val counts = IntArray(options.size)
        var totalVotes = 0
        for (vote in votes) {
            val indices = parseIndices(vote.optionIndices)
            for (i in indices) if (i in options.indices) counts[i]++
            totalVotes += indices.size
        }
        val isGerman = SettingsUtils.isGermanUi(context)
        val body = PollFormat.buildAsciiResult(
            title = poll.title,
            options = options,
            counts = counts.toList(),
            totalVotes = totalVotes,
            resultLabel = if (isGerman) "Ergebnis" else "Result",
            votesLabel = if (isGerman) "Stimmen" else "votes"
        )
        // Update the poll message in the chat to show the result (the bubble then displays it).
        // The poll message carries pollId = poll.id.
        updatePollMessageBody(poll.id, body)
        // The result marker keeps the email part of the poll flow (no AI tagging/spam) in
        // MailChat; regular email programs just see a short line at the end.
        val bodyWithMarker = body + "\n[[poll-result:" + poll.id + "]]"
        val subject = "📊 ${poll.title} — " + (if (isGerman) "Ergebnis" else "Result")
        val recipient = if (EmailGrouping.isGroupKey(chat.participantEmail)) {
            chat.participantEmail.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != EmailGrouping.normalize(account.emailAddress) }
                .joinToString(",")
                .ifBlank { chat.participantEmail }
        } else {
            chat.participantEmail
        }
        try {
            // Encrypt the result email like a regular message (OpenPGP / legacy fallback).
            val recipients = recipient.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
            val enc = com.mailchat.crypto.EncryptionHelper.encryptForRecipients(
                context, bodyWithMarker, recipients, isGroupChat, chat.isEncrypted
            )
            val extraHeaders = mutableMapOf("X-MailChat-Poll-Result" to poll.id)
            extraHeaders.putAll(enc.headers)
            smtpService.sendEmail(
                account = account,
                to = recipient,
                subject = subject,
                body = enc.body,
                extraHeaders = extraHeaders,
                bodyFormat = com.mailchat.network.BodyFormat.PLAIN
            )
        } catch (e: com.mailchat.crypto.EncryptionHelper.EncryptionFailed) {
            // No UI here (background send): respect the encryption rules and skip the send.
            android.util.Log.w("MailchatPoll", "send result email skipped (encryption failed)", e)
        } catch (e: Exception) {
            android.util.Log.w("MailchatPoll", "send result email failed", e)
        }
    }

    /**
     * Sends the aggregated result of a poll group: ONE result email per recipient, each carrying
     * that recipient's poll-ID (so their local copy of the poll is closed too). The counts are
     * aggregated across ALL per-recipient poll-IDs and ALL accounts (all votes counted, not
     * deduped).
     */
    private suspend fun sendGroupResultEmails(polls: List<Poll>, chat: Chat) {
        val account = accountRepository.getAccountById(polls.first().accountId) ?: return
        val options = parseOptions(polls.first().options)
        // Aggregate votes across all accounts for all per-recipient poll-IDs.
        // Counts: sum ALL votes from all respondents (not deduped).
        // Display: deduped votes show the last answer per voter.
        val allVotes = pollRepository.getVotesForPollsAllAccounts(polls.map { it.id })
        val counts = IntArray(options.size)
        var totalVotes = 0
        for (vote in allVotes) {
            val indices = parseIndices(vote.optionIndices)
            for (i in indices) if (i in options.indices) counts[i]++
            totalVotes += indices.size
        }
        val isGerman = SettingsUtils.isGermanUi(context)
        val body = PollFormat.buildAsciiResult(
            title = polls.first().title,
            options = options,
            counts = counts.toList(),
            totalVotes = totalVotes,
            resultLabel = if (isGerman) "Ergebnis" else "Result",
            votesLabel = if (isGerman) "Stimmen" else "votes"
        )
        val subject = "📊 ${polls.first().title} — " + (if (isGerman) "Ergebnis" else "Result")
        // Update the poll message(s) in the chat to show the result (the bubble then displays it).
        // The creator's poll messages carry pollId = groupId.
        updatePollMessageBody(polls.first().groupId, body)
        for (poll in polls) {
            val recipient = poll.recipientEmail
            if (recipient.isBlank()) continue
            // Skip the creator's own entry (recipientEmail == creatorEmail, no poll email was
            // ever sent for it): the creator already sees the result in the poll view and their
            // local copy is closed by closePollGroup. Sending a result email to the creator's
            // own address would create a stray message in the wrong chat.
            if (poll.creatorEmail.isNotBlank() &&
                recipient.trim().lowercase() == poll.creatorEmail.trim().lowercase()
            ) continue
            // The result marker carries the per-recipient poll-ID (closes the recipient's copy).
            val bodyWithMarker = body + "\n[[poll-result:" + poll.id + "]]"
            try {
                val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
                val enc = com.mailchat.crypto.EncryptionHelper.encryptForRecipients(
                    context, bodyWithMarker, listOf(recipient), isGroupChat, chat.isEncrypted
                )
                val extraHeaders = mutableMapOf("X-MailChat-Poll-Result" to poll.id)
                extraHeaders.putAll(enc.headers)
                smtpService.sendEmail(
                    account = account,
                    to = recipient,
                    subject = subject,
                    body = enc.body,
                    extraHeaders = extraHeaders,
                    bodyFormat = com.mailchat.network.BodyFormat.PLAIN
                )
            } catch (e: com.mailchat.crypto.EncryptionHelper.EncryptionFailed) {
                android.util.Log.w("MailchatPoll", "send result email skipped (encryption failed)", e)
            } catch (e: Exception) {
                android.util.Log.w("MailchatPoll", "send result email failed", e)
            }
        }
    }

    // --- Helpers ---

    private suspend fun updateMessageForPoll(message: Message, pollId: String, isAnswer: Boolean): Message {
        if (message.id <= 0) return message
        val updated = message.copy(pollId = pollId, isPollAnswer = isAnswer)
        messageRepository.updateMessage(updated)
        return updated
    }

    /**
     * Replaces the body of the poll message(s) in the chat with the aggregated ASCII result, so
     * the chat bubble shows the result directly (instead of the original question/options). The
     * message keeps its pollId (still recognized as a poll → tapping opens the interactive view
     * with the bar chart), and the result marker is NOT added (so it's not treated as a result
     * email). Poll answers and result emails are left untouched.
     */
    private suspend fun updatePollMessageBody(pollId: String, asciiResult: String) {
        if (pollId.isBlank()) return
        val messages = messageRepository.getMessagesByPollId(pollId)
        for (msg in messages) {
            if (msg.isPollAnswer) continue
            if (PollFormat.parseResultMarker(msg.bodyFull.ifBlank { msg.body }) != null) continue
            messageRepository.updateMessage(msg.copy(body = asciiResult, bodyFull = asciiResult))
        }
    }

    private suspend fun accountEmailFor(accountId: Long): String =
        accountRepository.getAccountById(accountId)?.emailAddress ?: ""

    /** Finds the local account that belongs to [email] (the creator's account), or null. */
    private suspend fun findAccountByEmail(email: String): EmailAccount? {
        if (email.isBlank()) return null
        val target = email.trim().lowercase()
        val accounts = accountRepository.getAllAccounts().first()
        return accounts.firstOrNull { it.emailAddress.trim().lowercase() == target }
    }

    /** All participants that must vote (including the creator), excluding no-reply addresses. */
    private fun requiredVoters(chat: Chat, creatorEmail: String): Set<String> {
        val isGroup = EmailGrouping.isGroupKey(chat.participantEmail)
        val members = if (isGroup) {
            chat.participantEmail.split(";").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        } else {
            listOf(chat.participantEmail.trim().lowercase()).filter { it.isNotBlank() }
        }
        val all = members.toMutableSet()
        if (creatorEmail.isNotBlank()) all.add(creatorEmail.trim().lowercase())
        return all.filter { !EmailGrouping.isNoReplyEmail(it) }.toSet()
    }

    private fun parseOptions(optionsJson: String): List<String> = PollFormat.optionsFromJson(optionsJson)

    private fun parseIndices(indicesJson: String): List<Int> = PollFormat.indicesFromJson(indicesJson)

    private fun optionsToJson(options: List<String>): String = PollFormat.optionsToJson(options)

    private fun indicesToJson(indices: List<Int>): String = PollFormat.indicesToJson(indices)
}
