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
    private val autoReplyService: AutoReplyService
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
        for (account in accounts) {
            try {
                val incoming = imapService.fetchNewMessages(account, periodStart)
                errorDetails.addAll(incoming.errors.map { "${account.emailAddress} $it" })
                for (fetchedMsg in incoming.messages) {
                    processFetchedMessage(account, fetchedMsg)
                }
                val sent = imapService.fetchSentMessages(account, periodStart)
                errorDetails.addAll(sent.errors.map { "${account.emailAddress} $it" })
                for (fetchedMsg in sent.messages) {
                    processFetchedMessage(account, fetchedMsg)
                }
                accountRepository.updateAccount(
                    account.copy(lastSyncTimestamp = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                failures++
                errorDetails.add("${account.emailAddress}: ${e.message}")
            }
        }
        return SyncResult(errorDetails, accounts.size, failures)
    }

    /**
     * Store a single fetched message: dedupe, create/update the chat, insert the message and its
     * attachments, post a notification for incoming mail and run the AI auto-processing.
     */
    suspend fun processFetchedMessage(
        account: EmailAccount,
        fetchedMsg: FetchedMessage
    ) {
        val msgId = fetchedMsg.message.messageId
        if (msgId.isNotBlank() && messageRepository.countByMessageId(msgId) > 0) return
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
            if (dupCount > 0) return
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
        if (fetchedMsg.isIncoming) {
            NotificationHelper.postNewEmail(
                context = context,
                chatId = chat.id,
                sender = fetchedMsg.message.from,
                subject = fetchedMsg.message.subject,
                preview = fetchedMsg.message.body.take(200)
            )
            if (SettingsUtils.getAiEnabled(context) && SettingsUtils.getAiAutoProcess(context)) {
                try {
                    com.mailchat.util.AiEngine.configureAndInitialize(context)
                    val updatedMsg = com.mailchat.util.AiMessageProcessor.processMessage(context, message)
                    messageRepository.updateMessage(updatedMsg.copy(id = dbMessageId))
                } catch (e: Exception) {
                    android.util.Log.w("MailchatSync", "ai auto-process failed", e)
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
            // Refresh the home-screen widget so it shows the new email.
            try {
                com.mailchat.widget.MailWidgetProvider.refreshAll(context)
            } catch (_: Exception) {
            }
        }
    }
}
