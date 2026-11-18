package com.mailchat.service

import android.content.Context
import com.mailchat.data.model.Chat
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.repository.AccountRepository
import com.mailchat.data.repository.ChatRepository
import com.mailchat.data.repository.MessageRepository
import com.mailchat.network.FetchedMessage
import com.mailchat.network.ImapService
import com.mailchat.util.EmailGrouping
import com.mailchat.util.NotificationHelper
import com.mailchat.util.SettingsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared sync engine used by both the periodic [SyncWorker] and the IMAP push service.
 *
 * The push service runs the sync directly (instead of via WorkManager) because WorkManager jobs
 * are not reliably executed in the background (Doze / battery optimizations can defer them). The
 * push service is a foreground service holding a wake lock, so it can fetch and store incoming
 * mail reliably regardless of which screen is visible or whether the app is in the background.
 */
@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imapService: ImapService,
    private val accountRepository: AccountRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val autoReplyService: AutoReplyService,
    private val pollProcessor: PollProcessor
) {

    // Background scope for the inbox write-back (must not block the sync loop).
    private val writeBackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class SyncResult(
        val errorDetails: List<String>,
        val totalAccounts: Int,
        val failedAccounts: Int
    )

    /**
     * Fetch and process new (and sent) messages for all accounts.
     *
     * @return the error details plus account/failure counts (used by the worker to decide on retry).
     */
    suspend fun syncAll(): SyncResult {
        val accounts = accountRepository.getAllAccounts().first()
        if (accounts.isEmpty()) return SyncResult(emptyList(), 0, 0)
        val periodDays = SettingsUtils.getSyncPeriodDays(context)
        val periodStart = System.currentTimeMillis() - periodDays * 24L * 60L * 60L * 1000L
        val errorDetails = mutableListOf<String>()
        var failures = 0
        // Collect all NEW incoming emails of this sync run and post a single notification (or a
        // summary with a count) at the end, instead of one notification per email.
        val newEmails = mutableListOf<NotificationHelper.NewEmailInfo>()
        for (account in accounts) {
            try {
                val incoming = imapService.fetchNewMessages(account, periodStart)
                errorDetails.addAll(incoming.errors.map { "${account.emailAddress} $it" })
                for (fetchedMsg in incoming.messages) {
                    processFetchedMessage(account, fetchedMsg)?.let { newEmails.add(it) }
                }
                val sent = imapService.fetchSentMessages(account, periodStart)
                errorDetails.addAll(sent.errors.map { "${account.emailAddress} $it" })
                for (fetchedMsg in sent.messages) {
                    processFetchedMessage(account, fetchedMsg)?.let { newEmails.add(it) }
                }
                accountRepository.updateAccount(
                    account.copy(lastSyncTimestamp = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                failures++
                errorDetails.add("${account.emailAddress}: ${e.message}")
            }
        }
        // One notification (or a summary with the count) for everything fetched in this run.
        NotificationHelper.postNewEmailNotifications(context, newEmails)
        // Refresh the home-screen widget once (instead of per email) so it shows the new emails.
        if (newEmails.isNotEmpty()) {
            try {
                com.mailchat.widget.MailWidgetProvider.refreshAll(context)
            } catch (_: Exception) {
            }
        }
        return SyncResult(errorDetails, accounts.size, failures)
    }

    /**
     * Store a single fetched message: dedupe, create/update the chat, insert the message and its
     * attachments and run the AI auto-processing. Returns a [NotificationHelper.NewEmailInfo]
     * when a NEW incoming email was inserted (so the caller can batch the notifications and post
     * a single one — or a summary with a count — at the end of the sync), or null otherwise.
     */
    suspend fun processFetchedMessage(
        account: EmailAccount,
        fetchedMsg: FetchedMessage
    ): NotificationHelper.NewEmailInfo? {
        val msgId = fetchedMsg.message.messageId
        if (msgId.isNotBlank() && messageRepository.countByMessageId(msgId) > 0) return null
        val participants = fetchedMsg.participants
        val isGroup = participants.size > 2
        val chatEmail = if (isGroup) participants.sorted().joinToString(";") else fetchedMsg.participantEmail
        val chatName = if (isGroup) {
            EmailGrouping.groupDisplayName(participants)
        } else {
            EmailGrouping.displayName(
                fetchedMsg.message.from.substringBefore('@'),
                fetchedMsg.participantEmail
            )
        }
        val existing = chatRepository.findByEmailAndAccount(chatEmail, account.id)
        val chat = if (existing != null) {
            // Only update the "last message" fields when this message is newer than the current
            // last one, so the chat overview always shows the NEWEST message's subject — regardless
            // of the order in which the IMAP server returns the messages.
            val isNewer = fetchedMsg.message.timestamp >= existing.lastMessageTimestamp
            val updated = existing.copy(
                lastMessageTimestamp = if (isNewer) fetchedMsg.message.timestamp else existing.lastMessageTimestamp,
                lastMessageBody = if (isNewer) fetchedMsg.message.body else existing.lastMessageBody,
                lastMessageSubject = if (isNewer) fetchedMsg.message.subject else existing.lastMessageSubject,
                unreadCount = if (fetchedMsg.isIncoming) existing.unreadCount + 1 else existing.unreadCount,
                backgroundImageUri = existing.backgroundImageUri ?: fetchedMsg.firstImageUri
            )
            chatRepository.updateChat(updated)
            updated
        } else {
            val newChat = Chat(
                participantEmail = chatEmail,
                participantName = chatName,
                accountId = account.id,
                lastMessageTimestamp = fetchedMsg.message.timestamp,
                lastMessageBody = fetchedMsg.message.body,
                lastMessageSubject = fetchedMsg.message.subject,
                unreadCount = if (fetchedMsg.isIncoming) 1 else 0,
                backgroundImageUri = fetchedMsg.firstImageUri
            )
            val id = chatRepository.insertChat(newChat)
            newChat.copy(id = id)
        }
        if (msgId.isBlank()) {
            val dupCount = messageRepository.countByFromSubjectTimestamp(
                chat.id,
                fetchedMsg.message.from,
                fetchedMsg.message.subject,
                fetchedMsg.message.timestamp
            )
            if (dupCount > 0) return null
        }
        // Phishing blacklist: a blacklisted sender is marked as phishing immediately (persists
        // in SharedPreferences, survives chat deletion).
        val blacklisted = fetchedMsg.isIncoming &&
            SettingsUtils.isPhishingBlacklisted(context, fetchedMsg.message.from)
        val message = fetchedMsg.message.copy(
            chatId = chat.id,
            accountId = account.id,
            isSpam = if (blacklisted) true else fetchedMsg.message.isSpam,
            spamCategory = if (blacklisted) "phishing" else fetchedMsg.message.spamCategory
        )
        val dbMessageId = messageRepository.insertMessage(message)
        for (attachment in fetchedMsg.attachments) {
            messageRepository.insertAttachment(attachment.copy(messageId = dbMessageId))
        }
        // Polls (Umfragen): detect a poll or a poll answer and record it (works for incoming and
        // outgoing mail, so the creator's sent poll is registered too). Poll answers are
        // suppressed in the chat, so they must not trigger a notification either.
        var isPollAnswerMsg = false
        var isPollMsg = false
        try {
            val processed = pollProcessor.processMessageForPolls(message.copy(id = dbMessageId), chat)
            isPollAnswerMsg = processed.isPollAnswer
            isPollMsg = processed.pollId.isNotBlank()
        } catch (e: Exception) {
            android.util.Log.w("MailchatSync", "poll processing failed", e)
        }
        if (fetchedMsg.isIncoming) {
            // Polls (and poll answers) get no AI tagging/spam detection — at most the
            // encryption symbol is shown (driven by isEncrypted, not by the AI).
            if (SettingsUtils.getAiEnabled(context) && SettingsUtils.getAiAutoProcess(context) && !isPollMsg) {
                try {
                    com.mailchat.util.AiEngine.configureAndInitialize(context)
                    val updatedMsg = com.mailchat.util.AiMessageProcessor.processMessage(context, message)
                    messageRepository.updateMessage(updatedMsg.copy(id = dbMessageId))
                } catch (e: Exception) {
                    android.util.Log.w("MailchatSync", "ai auto-process failed", e)
                }
            }
            // Automatically create a display name (Klarnamen) for a new sender (controlled by a
            // separate switch, so it also runs when auto-summarize is off).
            if (SettingsUtils.getAiEnabled(context) && SettingsUtils.getAiAutoRealNames(context) &&
                chat.participantName == chat.participantEmail
            ) {
                writeBackScope.launch {
                    try {
                        com.mailchat.util.AiEngine.configureAndInitialize(context)
                        com.mailchat.util.AiMessageProcessor.ensureDisplayName(chat) { updated ->
                            chatRepository.updateChat(updated)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MailchatSync", "auto real-name failed", e)
                    }
                }
            }
            // Automatic "Wo bist Du?" reply (see AutoReplyService).
            try {
                autoReplyService.maybeAutoReply(account, message.copy(id = dbMessageId))
            } catch (e: Exception) {
                android.util.Log.w("MailchatSync", "auto-reply failed", e)
            }
            // Decrypted copy in the inbox: replace the encrypted incoming message on the
            // server with its decrypted version (same Message-ID, date and headers). Runs in
            // the background so the sync loop is not blocked; the replacement is a no-op if
            // the server copy is no longer encrypted (e.g. already written back).
            if (fetchedMsg.message.isEncrypted &&
                com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackInboxEnabled(context)
            ) {
                val acc = account
                val mid = msgId
                writeBackScope.launch {
                    for (attempt in 1..3) {
                        val ok = runCatching { imapService.replaceInboxMessageWithDecrypted(acc, mid) }
                            .getOrDefault(false)
                        if (ok) break
                        kotlinx.coroutines.delay(10000)
                    }
                }
            }
        }
        // Return the new-email info so the caller can batch the notification (and the widget
        // refresh) at the end of the sync instead of doing it per email. Poll answers are
        // suppressed in the chat → no notification for them.
        return if (fetchedMsg.isIncoming && !isPollAnswerMsg) {
            NotificationHelper.NewEmailInfo(
                chatId = chat.id,
                sender = fetchedMsg.message.from,
                subject = fetchedMsg.message.subject,
                preview = fetchedMsg.message.body.take(200),
                timestamp = fetchedMsg.message.timestamp
            )
        } else null
    }
}
