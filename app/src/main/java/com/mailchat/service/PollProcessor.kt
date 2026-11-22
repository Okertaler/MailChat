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
import com.mailchat.util.AppointmentFormat
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

        // 0a. Appointment result email (sent when an appointment closes): same handling as the poll
        // result — close the local copy and show the result in the chat bubble.
        AppointmentFormat.parseApptResultMarker(body)?.let { resultId ->
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
            val asciiResult = body.lineSequence()
                .filter { !it.trim().startsWith("[[appt-result:") }
                .joinToString("\n")
                .trim()
            if (asciiResult.isNotBlank()) updatePollMessageBody(resultId, asciiResult)
            return updateMessageForPoll(message, resultId, isAnswer = false)
        }

        // 1a. MailChat numeric answer (explicit numeric answer marker). Checked FIRST: the literal
        // "poll-answer:" is a prefix of "poll-answer-num:", so the numeric marker must win.
        PollFormat.parseAnswerNumMarker(body)?.let { answer ->
            return recordVote(message, chat, answer.pollId, answer.optionIndices, answer.values)
        }

        // 1b. MailChat appointment answer (explicit appointment answer marker, with comment).
        AppointmentFormat.parseApptAnswerMarker(body)?.let { answer ->
            return recordApptVote(message, chat, answer.apptId, answer.choice, answer.comment)
        }

        // 1. MailChat answer (explicit answer marker).
        PollFormat.parseAnswerMarker(body)?.let { answer ->
            return recordVote(message, chat, answer.pollId, answer.optionIndices)
        }

        // 2. Poll marker present.
        PollFormat.parsePollMarker(body)?.let { marker ->
            // Try the current account first, then all accounts (Thunderbird replies carry the
            // recipient's per-recipient poll ID, which only exists in the recipient's account).
            val existing = pollRepository.getPoll(marker.pollId, message.accountId)
                ?: pollRepository.getPollAnyAccount(marker.pollId)
            return if (existing == null) {
                createPoll(message, chat, marker.pollId, marker.multi, marker.numberMode, marker.personMode, body)
            } else {
                // A reference to an existing poll: a regular-email answer when it has X marks.
                handleExistingPollReference(message, chat, existing, body)
            }
        }

        // 2a. Appointment marker present.
        AppointmentFormat.parseApptMarker(body)?.let { marker ->
            val existing = pollRepository.getPoll(marker.apptId, message.accountId)
                ?: pollRepository.getPollAnyAccount(marker.apptId)
            return if (existing == null) {
                createAppointment(message, chat, marker, body)
            } else {
                // A reference to an existing appointment: a regular-email answer when it has X marks.
                handleExistingApptReference(message, chat, existing, body)
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
        numberMode: Boolean,
        personMode: Boolean,
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
            numberMode = numberMode,
            personMode = personMode,
            creatorEmail = if (message.isIncoming) message.from else accountEmailFor(message.accountId),
            pollMessageId = message.messageId,
            status = Poll.STATUS_OPEN,
            createdAt = message.timestamp
        )
        pollRepository.insertPoll(poll)
        return updateMessageForPoll(message, pollId, isAnswer = false)
    }

    // --- Appointment (Termin) creation ---

    /**
     * Creates the local appointment poll record when the original appointment email is received.
     * The dates come from the marker; the title/question/location/options from the body.
     */
    private suspend fun createAppointment(
        message: Message,
        chat: Chat,
        marker: AppointmentFormat.ApptMarker,
        body: String
    ): Message {
        val data = AppointmentFormat.parseApptBody(body)
        val options = PollFormat.parseAllCheckboxOptions(body)
        val poll = Poll(
            id = marker.apptId,
            accountId = message.accountId,
            chatId = chat.id,
            title = data?.title ?: message.subject,
            question = data?.question ?: "",
            options = optionsToJson(options),
            multi = false,
            numberMode = false,
            personMode = false,
            appointmentMode = true,
            startDate = data?.startDate ?: "",
            startTime = data?.startTime ?: "",
            endDate = data?.endDate ?: "",
            endTime = data?.endTime ?: "",
            allDay = data?.allDay ?: marker.allDay,
            location = data?.location ?: "",
            creatorEmail = if (message.isIncoming) message.from else accountEmailFor(message.accountId),
            pollMessageId = message.messageId,
            status = Poll.STATUS_OPEN,
            createdAt = message.timestamp
        )
        pollRepository.insertPoll(poll)
        return updateMessageForPoll(message, marker.apptId, isAnswer = false)
    }

    /**
     * Records an appointment answer (choice 0/1/2 + optional comment). [choice] is the 0-based
     * index of the selected option (Ja/Nein/Vielleicht).
     */
    private suspend fun recordApptVote(
        message: Message,
        chat: Chat,
        apptId: String,
        choice: Int,
        comment: String
    ): Message {
        val poll = pollRepository.getPoll(apptId, message.accountId) ?: return message
        if (choice !in 0..2) return message
        val votePollId = resolveVotePollId(poll, chat)
        val vote = PollVote(
            pollId = votePollId,
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = indicesToJson(listOf(choice)),
            values = "[]",
            comment = comment,
            messageId = message.messageId,
            answeredAt = message.timestamp
        )
        pollRepository.insertVote(vote)
        val updated = updateMessageForPoll(message, apptId, isAnswer = true)
        checkAutoClose(poll, chat)
        return updated
    }

    /**
     * A body that references an existing appointment (quoted marker). An answer when it has an X
     * mark on one of the Ja/Nein/Vielleicht options; the optional comment is read from a
     * "Kommentar:" line.
     */
    private suspend fun handleExistingApptReference(
        message: Message,
        chat: Chat,
        poll: Poll,
        body: String
    ): Message {
        val marked = PollFormat.parseXMarkedOptions(body)
        if (marked.isEmpty()) return message // just a reference (e.g. a forwarded appointment)
        val options = parseOptions(poll.options)
        val choice = PollFormat.matchOptionsToIndices(marked, options).firstOrNull() ?: return message
        val comment = AppointmentFormat.parseApptComment(body)
        val votePollId = resolveVotePollId(poll, chat)
        val vote = PollVote(
            pollId = votePollId,
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = indicesToJson(listOf(choice)),
            values = "[]",
            comment = comment,
            messageId = message.messageId,
            answeredAt = message.timestamp
        )
        pollRepository.insertVote(vote)
        val updated = updateMessageForPoll(message, poll.id, isAnswer = true)
        checkAutoClose(poll, chat)
        return updated
    }

    // --- Vote recording ---

    private suspend fun recordVote(
        message: Message,
        chat: Chat,
        pollId: String,
        optionIndices: List<Int>,
        values: List<Double> = emptyList()
    ): Message {
        val poll = pollRepository.getPoll(pollId, message.accountId) ?: return message
        if (optionIndices.isEmpty() && values.isEmpty()) return message
        val votePollId = resolveVotePollId(poll, chat)
        val vote = PollVote(
            pollId = votePollId,
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = indicesToJson(optionIndices),
            values = valuesToJson(values),
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

    /**
     * A body that references an existing poll (quoted marker). An answer when it has X marks
     * (checkbox poll) or numbers next to the options (numeric poll).
     */
    private suspend fun handleExistingPollReference(
        message: Message,
        chat: Chat,
        poll: Poll,
        body: String
    ): Message {
        // Numeric poll: parse the numbers from the reply (e.g. "Option = 5", "Option (5)", "Option 5").
        if (poll.numberMode) {
            return recordNumberVoteFromBody(message, chat, poll, body) ?: message
        }
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

    /**
     * No markers at all: match the reply against the chat's single open poll — X marks (checkbox
     * poll) or numbers next to the options (numeric poll).
     */
    private suspend fun handleMarkerlessAnswer(message: Message, chat: Chat, body: String): Message {
        if (!message.isIncoming) return message
        val openPolls = pollRepository.getPollsByChatSync(chat.id, message.accountId)
            .filter { it.status == Poll.STATUS_OPEN }
        if (openPolls.size != 1) return message
        val poll = openPolls[0]
        // Appointment (Termin): X mark on Ja/Nein/Vielleicht + optional "Kommentar:" line.
        if (poll.appointmentMode) {
            return handleMarkerlessApptAnswer(message, chat, poll, body)
        }
        // Numeric poll: parse the numbers from the reply.
        if (poll.numberMode) {
            return recordNumberVoteFromBody(message, chat, poll, body) ?: message
        }
        val marked = PollFormat.parseXMarkedOptions(body)
        if (marked.isEmpty()) return message
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

    /**
     * Records a numeric vote parsed from a regular email reply body (numbers next to the options).
     * Returns the updated message when a vote was recorded, or null when no numbers were found
     * (the body is then just a reference to the poll, not an answer).
     */
    private suspend fun recordNumberVoteFromBody(
        message: Message,
        chat: Chat,
        poll: Poll,
        body: String
    ): Message? {
        val options = parseOptions(poll.options)
        val values = PollFormat.parseNumberAnswer(body, options)
        if (values.all { it == 0.0 }) return null
        val vote = PollVote(
            pollId = resolveVotePollId(poll, chat),
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = "[]",
            values = valuesToJson(values),
            messageId = message.messageId,
            answeredAt = message.timestamp
        )
        pollRepository.insertVote(vote)
        val updated = updateMessageForPoll(message, poll.id, isAnswer = true)
        checkAutoClose(poll, chat)
        return updated
    }

    /**
     * No markers at all: match the reply against the chat's single open APPOINTMENT — an X mark on
     * one of the Ja/Nein/Vielleicht options + an optional "Kommentar:" line.
     */
    private suspend fun handleMarkerlessApptAnswer(
        message: Message,
        chat: Chat,
        poll: Poll,
        body: String
    ): Message {
        val marked = PollFormat.parseXMarkedOptions(body)
        if (marked.isEmpty()) return message
        val options = parseOptions(poll.options)
        val choice = PollFormat.matchOptionsToIndices(marked, options).firstOrNull() ?: return message
        val comment = AppointmentFormat.parseApptComment(body)
        val vote = PollVote(
            pollId = resolveVotePollId(poll, chat),
            accountId = message.accountId,
            voterEmail = message.from.trim().lowercase(),
            optionIndices = indicesToJson(listOf(choice)),
            values = "[]",
            comment = comment,
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
        val votes = pollRepository.getVotesSync(poll.id, poll.accountId)
        val isGerman = SettingsUtils.isGermanUi(context)
        val body = buildResultBody(poll, votes, isGerman)
        // Update the poll message in the chat to show the result (the bubble then displays it).
        // The poll message carries pollId = poll.id.
        updatePollMessageBody(poll.id, body)
        // The result marker keeps the email part of the poll flow (no AI tagging/spam) in
        // MailChat; regular email programs just see a short line at the end. The marker also
        // carries the structured result so the recipient's result view matches the creator's.
        val marker = buildResultMarkerFor(poll, votes, requiredVoters(chat, poll.creatorEmail).toList(), isGerman)
        // The "do not reply" hint is added to the EMAIL only (not the in-app chat bubble).
        val bodyWithMarker = body + "\n" + noReplyHint(isGerman) + "\n" + marker
        val subject = (if (poll.appointmentMode) "📅" else "📊") + " ${poll.title} — " + (if (isGerman) "Ergebnis" else "Result")
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
        // Aggregate votes across all accounts for all per-recipient poll-IDs.
        // Bar chart: sum ALL votes from all respondents (not deduped).
        // Person table: deduped votes show the last answer per voter (one row per person).
        val allVotes = pollRepository.getVotesForPollsAllAccounts(polls.map { it.id })
        val isGerman = SettingsUtils.isGermanUi(context)
        val body = buildResultBody(polls.first(), allVotes, isGerman)
        val subject = (if (polls.first().appointmentMode) "📅" else "📊") + " ${polls.first().title} — " + (if (isGerman) "Ergebnis" else "Result")
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
            // The result marker carries the per-recipient poll-ID (closes the recipient's copy)
            // plus the structured result (so the recipient's result view matches the creator's).
            // The "do not reply" hint is added to the EMAIL only (not the in-app chat bubble).
            val marker = buildResultMarkerFor(poll, allVotes, polls.map { it.recipientEmail }, isGerman)
            val bodyWithMarker = body + "\n" + noReplyHint(isGerman) + "\n" + marker
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

    private fun parseValues(valuesJson: String): List<Double> = PollFormat.valuesFromJson(valuesJson)

    private fun optionsToJson(options: List<String>): String = PollFormat.optionsToJson(options)

    private fun indicesToJson(indices: List<Int>): String = PollFormat.indicesToJson(indices)

    private fun valuesToJson(values: List<Double>): String = PollFormat.valuesToJson(values)

    /**
     * Builds the result body for a closed poll:
     *  - Person-related poll: an ASCII TABLE (persons × options, bottom row = column sums), using
     *    the DEDUPED votes (one row per person, last answer wins).
     *  - Otherwise: the ASCII bar chart (counts sum ALL votes, not deduped).
     */
    /**
     * The "do not reply" hint appended to result emails (for foreign email systems such as
     * Thunderbird, where a recipient could otherwise accidentally reply to the result).
     */
    private fun noReplyHint(isGerman: Boolean): String =
        if (isGerman) "⚠️ Nicht antworten!" else "⚠️ Do not reply!"

    private fun buildResultBody(poll: Poll, votes: List<PollVote>, isGerman: Boolean): String {
        val options = parseOptions(poll.options)
        val resultLabel = if (isGerman) "Ergebnis" else "Result"
        if (poll.appointmentMode) {
            val deduped = dedupeVotesByVoter(votes)
            val responses = deduped.map { vote ->
                val choice = parseIndices(vote.optionIndices).firstOrNull() ?: -1
                AppointmentFormat.ApptResponse(vote.voterEmail, choice, vote.comment)
            }
            return AppointmentFormat.buildApptAsciiResult(
                title = poll.title,
                question = poll.question,
                dateLabel = apptDateLabel(poll, isGerman),
                location = poll.location,
                responses = responses,
                optionLabels = options,
                resultLabel = resultLabel,
                personLabel = "Person",
                answerLabel = if (isGerman) "Antwort" else "Answer",
                commentLabel = if (isGerman) "Kommentar" else "Comment"
            )
        }
        if (poll.personMode) {
            val deduped = dedupeVotesByVoter(votes)
            val header = listOf(if (isGerman) "Person" else "Person") + options
            val rows = PollFormat.personTableRows(options, deduped, poll.numberMode)
            val sums = PollFormat.personColumnSums(options, deduped, poll.numberMode)
            val footer = listOf(if (isGerman) "Summe" else "Sum") + sums.map { it.toString() }
            return PollFormat.buildAsciiTable(poll.title, poll.question, header, rows, footer, resultLabel)
        }
        val (counts, totalVotes) = computeCounts(options, votes, poll.numberMode)
        return PollFormat.buildAsciiResult(
            title = poll.title,
            question = poll.question,
            options = options,
            counts = counts,
            totalVotes = totalVotes,
            resultLabel = resultLabel,
            votesLabel = if (poll.numberMode) (if (isGerman) "Summe" else "Total")
                else (if (isGerman) "Stimmen" else "votes")
        )
    }

    /** The human-readable date label for an appointment poll (for the result email). */
    private fun apptDateLabel(poll: Poll, isGerman: Boolean): String =
        AppointmentFormat.dateLabel(poll.startDate, poll.startTime, poll.endDate, poll.endTime, poll.allDay, isGerman)

    /**
     * Builds the result marker for a result email, choosing the appointment or poll format. For
     * appointments the marker carries the structured per-person responses (choice + comment) so a
     * recipient renders the same result view as the creator.
     */
    private fun buildResultMarkerFor(
        poll: Poll,
        votes: List<PollVote>,
        recipientEmails: List<String>,
        isGerman: Boolean
    ): String {
        if (poll.appointmentMode) {
            val deduped = dedupeVotesByVoter(votes)
            val responses = deduped.map { vote ->
                val choice = parseIndices(vote.optionIndices).firstOrNull() ?: -1
                AppointmentFormat.ApptResponse(vote.voterEmail, choice, vote.comment)
            }
            return AppointmentFormat.buildApptResultMarker(poll.id, AppointmentFormat.ApptResultData(responses))
        }
        val data = buildResultData(poll, votes, recipientEmails)
        return PollFormat.buildResultMarker(poll.id, data)
    }

    /**
     * Builds the structured result carried in the result email's marker, so a recipient can
     * render the same result view as the creator (aggregated counts or person-table rows, plus
     * who has voted).
     */
    private fun buildResultData(poll: Poll, votes: List<PollVote>, recipientEmails: List<String>): PollFormat.PollResultData {
        val options = parseOptions(poll.options)
        val recipients = recipientEmails
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { email -> email to votes.any { it.voterEmail.trim().lowercase() == email } }
        return if (poll.personMode) {
            val deduped = dedupeVotesByVoter(votes)
            PollFormat.PollResultData(
                recipients = recipients,
                rows = PollFormat.personTableRows(options, deduped, poll.numberMode),
                sums = PollFormat.personColumnSums(options, deduped, poll.numberMode)
            )
        } else {
            val (counts, total) = computeCounts(options, votes, poll.numberMode)
            PollFormat.PollResultData(counts = counts, total = total, recipients = recipients)
        }
    }

    /** Dedupes votes by voter (last answer wins, by answeredAt) — one entry per person. */
    private fun dedupeVotesByVoter(votes: List<PollVote>): List<PollVote> =
        votes.groupBy { it.voterEmail.trim().lowercase() }
            .values
            .mapNotNull { it.maxByOrNull { v -> v.answeredAt } }
            .toList()

    /**
     * Computes the per-option result and the total for a poll.
     *  - Checkbox poll: [counts][Int] = number of voters who selected each option; total = total
     *    selections (sum of the counts).
     *  - Numeric poll: [counts][Int] = SUM of the numbers entered per option; total = grand total
     *    of all numbers (so the percentage is each option's share of the total).
     * Returns (counts, total).
     */
    private fun computeCounts(options: List<String>, votes: List<PollVote>, numberMode: Boolean): Pair<List<Int>, Int> {
        val counts = IntArray(options.size)
        var total = 0
        for (vote in votes) {
            if (numberMode) {
                val values = parseValues(vote.values)
                for (i in options.indices) {
                    counts[i] += values.getOrElse(i) { 0.0 }.toInt()
                }
                total += values.sum().toInt()
            } else {
                val indices = parseIndices(vote.optionIndices)
                for (i in indices) if (i in options.indices) counts[i]++
                total += indices.size
            }
        }
        return counts.toList() to total
    }
}
