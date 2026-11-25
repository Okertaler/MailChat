package com.mailchat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.Chat
import com.mailchat.data.model.Message
import com.mailchat.data.model.Poll
import com.mailchat.data.model.PollVote
import com.mailchat.data.repository.AccountRepository
import com.mailchat.data.repository.ChatRepository
import com.mailchat.data.repository.MessageRepository
import com.mailchat.network.ImapService
import com.mailchat.network.SmtpService
import com.mailchat.service.ContactService
import com.mailchat.service.Contact
import com.mailchat.util.AppointmentFormat
import com.mailchat.util.EmailGrouping
import com.mailchat.util.PollFormat
import com.mailchat.util.SettingsUtils
import com.mailchat.util.normalizeWhitespace
import com.mailchat.util.stripHtml
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ChatDetailUiState(
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val attachmentsByMessage: Map<Long, List<Attachment>> = emptyMap(),
    val isLoading: Boolean = true,
    val composeText: String = "",
    val composeAttachments: List<File> = emptyList(),
    val isSending: Boolean = false,
    val isSyncing: Boolean = false,
    val isAiProcessing: Boolean = false,
    val currentSummarizingMessageId: Long? = null,
    val sendError: String? = null,
    val encryptionErrorDialog: String? = null,
    val contactPhotoUri: String? = null,
    val contactName: String? = null,
    val contactPhotos: Map<String, String?> = emptyMap(),
    val contactNames: Map<String, String> = emptyMap(),
    val contacts: List<Contact> = emptyList(),
    val accountEmail: String? = null,
    val accountColor: Int = 0xFF128C7E.toInt(),
    val quoteEnabled: Boolean = false,
    val aiSuggestion: String = "",
    val aiError: String = "",
    val isGeneratingSuggestion: Boolean = false,
    val isTranslating: Boolean = false,
    val translationText: String = "",
    val translationError: String = "",
    val isSuggestingSubject: Boolean = false,
    val aiSuggestedSubject: String = "",
    val quickReplies: List<String> = emptyList(),
    val isGeneratingQuickReplies: Boolean = false,
    val draftSubject: String? = null,
    val selectedRecipient: String? = null,
    val replyQuote: String? = null,
    val composeCc: String = "",
    val composeBcc: String = "",
    val fullComposeOpen: Boolean = false,
    val smtpAccounts: List<com.mailchat.data.model.EmailAccount> = emptyList(),
    val composeFromAccountId: Long = 0,
    val rawSource: String? = null,
    val rawSourceLoading: Boolean = false,
    val replyTargetMessageId: Long? = null
)

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val accountRepository: AccountRepository,
    private val smtpService: SmtpService,
    private val imapService: ImapService,
    private val contactService: ContactService,
    private val draftDao: com.mailchat.data.dao.DraftDao,
    private val pollRepository: com.mailchat.data.repository.PollRepository,
    private val pollProcessor: com.mailchat.service.PollProcessor,
    private val syncEngine: com.mailchat.service.SyncEngine,
    private val mapImageService: com.mailchat.network.MapImageService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val chatId: Long = savedStateHandle.get<Long>("chatId") ?: 0L

    private var currentSubject: String = ""
    // Subject of the last send attempt; used so a retry (e.g. after the encryption error
    // dialog) keeps the subject even if the UI already cleared its local subject state.
    private var pendingSendSubject: String? = null
    // Retry action for the encryption error dialog (send or forward flow).
    private var encryptionErrorRetry: (() -> Unit)? = null
    private data class ForwardParams(
        val recipient: String,
        val message: Message,
        val customText: String,
        val contactName: String
    )
    private var lastForwardParams: ForwardParams? = null
    private var draftJob: kotlinx.coroutines.Job? = null
    // Job of the running AI processing (so the "AI in use" banner can stop it).
    private var aiProcessingJob: kotlinx.coroutines.Job? = null

    /** Stops the running AI processing (if any) and hides the "AI in use" indicator. */
    fun stopAiProcessing() {
        aiProcessingJob?.cancel()
        aiProcessingJob = null
        _uiState.update { it.copy(isAiProcessing = false, currentSummarizingMessageId = null) }
    }
    // Message-IDs for which a PGP/MIME re-fetch/repair was already attempted (avoids repeated
    // network calls for messages that genuinely have no decryptable content).
    private val repairedPgpMessageIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    // Local message IDs for which a missing-attachment re-fetch was already attempted in this
    // session. Keyed by local id (not Message-ID) so messages with an empty stored Message-ID are
    // also guarded.
    private val repairedAttachLocalIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getChatById(chatId)?.let { chat ->
                val account = accountRepository.getAccountById(chat.accountId)
                _uiState.update {
                    it.copy(
                        chat = chat,
                        accountEmail = account?.emailAddress,
                        accountColor = account?.avatarColor ?: 0xFF128C7E.toInt()
                    )
                }
                loadContactPhoto()
            }
            val draft = draftDao.getDraft(chatId)
            if (draft != null && (draft.body.isNotBlank() || draft.subject.isNotBlank())) {
                _uiState.update {
                    it.copy(
                        composeText = if (it.composeText.isBlank()) draft.body else it.composeText,
                        draftSubject = draft.subject.ifBlank { null }
                    )
                }
            }
            loadContacts()
            val accounts = accountRepository.getAllAccounts().first()
            _uiState.update { it.copy(smtpAccounts = accounts.filter { a -> a.smtpHost.isNotBlank() }) }
        }
        viewModelScope.launch {
            messageRepository.getMessagesByChat(chatId).collectLatest { messages ->
                _uiState.update { state ->
                    state.copy(messages = messages, isLoading = false)
                }
                loadAttachments(messages)
                repairUndecryptedPgpMime(messages)
                repairMissingAttachments(messages)
            }
        }
        // Fetch new emails for this chat from the server when the chat is opened. The chat detail
        // view only reads the local DB, so without this new emails (e.g. poll answers) would only
        // appear after the next background sync (chat overview). Dedupe by Message-ID makes a
        // re-fetch of already-stored mail a no-op.
        viewModelScope.launch {
            val chat = chatRepository.getChatById(chatId) ?: return@launch
            val account = accountRepository.getAccountById(chat.accountId) ?: return@launch
            try {
                val periodStart = if (chat.lastMessageTimestamp > 0) {
                    chat.lastMessageTimestamp - 60_000
                } else {
                    System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                }
                val incoming = imapService.fetchNewMessages(account, periodStart)
                for (fetchedMsg in incoming.messages) {
                    syncEngine.processFetchedMessage(account, fetchedMsg)
                }
            } catch (e: Exception) {
                android.util.Log.w("MailchatChat", "fetch-on-open failed", e)
            }
        }
    }

    /**
     * Manually sync this chat: fetch new messages from the server and store them. Triggered by
     * the sync button in the chat detail top bar (which spins while this runs) and by the poll
     * view (so new votes / the result email show up immediately). Reuses the same fetch window
     * as the fetch-on-open logic; dedupe by Message-ID makes a re-fetch a no-op.
     *
     * [periodStartOverride] starts the fetch window at a specific point in time (the poll view
     * uses the poll message's timestamp, so poll answers are covered even when the chat has
     * newer messages).
     */
    fun syncChat(periodStartOverride: Long? = null) {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            val chat = chatRepository.getChatById(chatId) ?: return@launch
            val account = accountRepository.getAccountById(chat.accountId) ?: return@launch
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val periodStart = periodStartOverride
                    ?: if (chat.lastMessageTimestamp > 0) {
                        chat.lastMessageTimestamp - 60_000
                    } else {
                        System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                    }
                val incoming = imapService.fetchNewMessages(account, periodStart)
                for (fetchedMsg in incoming.messages) {
                    syncEngine.processFetchedMessage(account, fetchedMsg)
                }
            } catch (e: Exception) {
                android.util.Log.w("MailchatChat", "manual sync fetch failed", e)
            }
            // Also repair this chat's attachments whose file is missing on disk (e.g. after a
            // restore that did not include the attachment files). Runs even if the fetch above
            // failed, because the local re-link step needs no network.
            try {
                repairChatAttachments(chat, account)
            } catch (e: Exception) {
                android.util.Log.w("MailchatChat", "manual sync attach repair failed", e)
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    /**
     * Repairs this chat's attachment rows whose file is missing on disk. First tries a local
     * re-link (a previous sync may have already downloaded the file as an orphan), then re-fetches
     * the message by its Message-ID for the rest (this is what recovers inline/CID images, which
     * have no orphan file). Capped to keep the IMAP load reasonable.
     */
    private suspend fun repairChatAttachments(
        chat: Chat,
        account: com.mailchat.data.model.EmailAccount
    ) {
        val attachments = messageRepository.getAttachmentsByChatSync(chatId)
        if (attachments.isEmpty()) return
        val attDir = File(appContext.filesDir, "attachments")
        val orphanFiles = if (attDir.exists()) attDir.listFiles()?.toList() ?: emptyList() else emptyList()

        val refetchMessageIds = linkedSetOf<Long>()
        var localRelinked = 0
        for (att in attachments) {
            if (File(att.uri).exists()) continue
            val matched = findOrphanFile(att, orphanFiles)
            if (matched != null) {
                messageRepository.updateAttachmentUri(att.id, matched.absolutePath)
                localRelinked++
            } else {
                refetchMessageIds.add(att.messageId)
            }
        }
        if (localRelinked > 0) {
            com.mailchat.ai.AppLogger.log(appContext, "ATTACH repair (chat): $localRelinked re-linked locally")
        }
        if (refetchMessageIds.isEmpty()) return

        var refetched = 0
        for (messageId in refetchMessageIds.take(20)) {
            val message = messageRepository.getMessageById(messageId) ?: continue
            if (message.messageId.isBlank()) continue
            try {
                val fetched = imapService.fetchMessageByMessageId(account, message.messageId) ?: continue
                if (fetched.attachments.isEmpty()) continue
                val storedAtts = messageRepository.getAttachmentsByMessageSync(message.id)
                val storedCids = storedAtts.map { it.contentId }.filter { it.isNotBlank() }.toSet()
                val storedNames = storedAtts.map { it.fileName.lowercase() }.toSet()
                for (stored in storedAtts) {
                    if (File(stored.uri).exists()) continue
                    val match = fetched.attachments.firstOrNull { f ->
                        (f.contentId.isNotBlank() && f.contentId == stored.contentId) ||
                            f.fileName.equals(stored.fileName, ignoreCase = true)
                    }
                    if (match != null) {
                        messageRepository.updateAttachmentUri(stored.id, match.uri)
                    }
                }
                // Insert fetched attachments that have no stored counterpart yet.
                for (f in fetched.attachments) {
                    val dup = (f.contentId.isNotBlank() && f.contentId in storedCids) ||
                        (f.fileName.lowercase() in storedNames)
                    if (!dup) {
                        messageRepository.insertAttachment(f.copy(messageId = message.id))
                    }
                }
                refetched++
            } catch (e: Exception) {
                android.util.Log.w("MailchatChat", "attach refetch failed for ${message.messageId}", e)
            }
        }
        if (refetched > 0) {
            com.mailchat.ai.AppLogger.log(appContext, "ATTACH repair (chat): $refetched messages re-fetched")
        }
    }

    /**
     * Finds an orphan file in the attachments directory that matches [att] by file name (or
     * content ID as a last resort). Prefers a size match, then the most recently modified file.
     */
    private fun findOrphanFile(att: Attachment, orphanFiles: List<File>): File? {
        val searchName = att.fileName.ifBlank { att.contentId }
        if (searchName.isBlank()) return null
        val matches = orphanFiles.filter { it.name.endsWith(searchName, ignoreCase = true) }
        if (matches.isEmpty()) return null
        return matches.sortedWith(
            compareByDescending<File> { it.length() == att.sizeBytes }
                .thenByDescending { it.lastModified() }
        ).first()
    }

    /** Global eingestelltes Absender-Konto verwenden, sonst Konto des Chats. */
    private suspend fun senderAccountFor(chat: Chat): com.mailchat.data.model.EmailAccount? {
        val chatAccount = accountRepository.getAccountById(chat.accountId) ?: return null
        val overrideId = _uiState.value.composeFromAccountId
        if (overrideId != 0L) {
            accountRepository.getAccountById(overrideId)?.let { return it }
        }
        val globalId = com.mailchat.util.SettingsUtils.getSenderAccountId(appContext)
        if (globalId != 0L && globalId != chat.accountId) {
            return accountRepository.getAccountById(globalId) ?: chatAccount
        }
        return chatAccount
    }

    private suspend fun loadAttachments(messages: List<Message>) {
        if (messages.isEmpty()) {
            _uiState.update { it.copy(attachmentsByMessage = emptyMap()) }
            return
        }
        val all = messageRepository.getAttachmentsByChatSync(chatId)
        val attachmentsMap = all.groupBy { it.messageId }
        _uiState.update { it.copy(attachmentsByMessage = attachmentsMap) }
    }

    /**
     * Repairs incoming messages that were cached before PGP/MIME support: they show up with an
     * empty body and a single "encrypted" file attachment (the raw decrypted MIME blob). Re-fetches
     * the raw message from the server, re-parses it (which now decrypts PGP/MIME into a proper
     * body + attachments) and updates the stored message. Only runs when an OpenPGP identity
     * exists, and each message is attempted at most once.
     */
    private fun repairUndecryptedPgpMime(messages: List<Message>) {
        if (!com.mailchat.crypto.OpenPgpKeyService.hasIdentity(appContext)) return
        val candidates = messages.filter { msg ->
            msg.isIncoming &&
                msg.bodyFull.isBlank() &&
                msg.messageId.isNotBlank() &&
                msg.messageId !in repairedPgpMessageIds
        }
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val account = accountRepository.getAccountById(chat.accountId) ?: return@launch
            for (msg in candidates) {
                if (!repairedPgpMessageIds.add(msg.messageId)) continue
                try {
                    val fetched = imapService.fetchMessageByMessageId(account, msg.messageId) ?: continue
                    val oldNames = messageRepository.getAttachmentsByMessageSync(msg.id).map { it.fileName }.toSet()
                    val newNames = fetched.attachments.map { it.fileName }.toSet()
                    val bodyChanged = fetched.message.bodyFull.isNotBlank() && fetched.message.bodyFull != msg.bodyFull
                    val attachmentsChanged = newNames != oldNames
                    if (!bodyChanged && !attachmentsChanged) continue
                    val updated = fetched.message.copy(
                        id = msg.id,
                        chatId = msg.chatId,
                        accountId = msg.accountId,
                        isRead = msg.isRead,
                        isSpam = msg.isSpam,
                        spamCategory = msg.spamCategory,
                        summary = msg.summary,
                        summaryLanguage = msg.summaryLanguage,
                        isProcessed = msg.isProcessed,
                        isTodo = msg.isTodo,
                        isStarred = msg.isStarred,
                        customTag = msg.customTag
                    )
                    messageRepository.updateMessage(updated)
                    messageRepository.deleteAttachmentsByMessage(msg.id)
                    for (att in fetched.attachments) {
                        messageRepository.insertAttachment(att.copy(messageId = msg.id))
                    }
                    // Keep the chat list preview in sync if this is the most recent message.
                    if (msg.timestamp >= chat.lastMessageTimestamp) {
                        chatRepository.updateChat(
                            chat.copy(
                                lastMessageBody = updated.body,
                                lastMessageSubject = updated.subject
                            )
                        )
                    }
                    com.mailchat.ai.AppLogger.log(appContext, "Repaired PGP/MIME message: subject=${updated.subject.take(40)}")
                } catch (e: Exception) {
                    com.mailchat.ai.AppLogger.log(appContext, "PGP/MIME repair failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Repairs messages that were cached before the inline-attachment fix (e.g. a PDF sent with
     * `Content-Disposition: inline` and no file name was skipped and never stored). Such messages
     * show up with zero attachments even though the server copy has them. Re-fetches the message
     * by its Message-ID and adds any attachments found.
     *
     * Only messages with zero local attachments are candidates. Messages whose server copy also
     * has no attachments (genuinely text-only) are remembered in a persistent set so they are not
     * re-fetched on every chat open forever. Capped per session to keep the IMAP load reasonable.
     */
    private fun repairMissingAttachments(messages: List<Message>) {
        val attachmentsMap = _uiState.value.attachmentsByMessage
        val failedIds = com.mailchat.util.SettingsUtils.getAttachRepairFailedMessageIds(appContext)
        val candidates = messages
            .filter { msg ->
                val atts = attachmentsMap[msg.id] ?: emptyList()
                // Candidate if it has no attachments, OR only image attachments (a file attachment
                // such as an inline PDF may have been skipped before the fix while the CID images
                // were stored). Messages that already have a non-image (file) attachment are left
                // alone.
                val needsRepair = atts.isEmpty() || atts.all { it.isImage }
                needsRepair &&
                    !repairedAttachLocalIds.contains(msg.id) &&
                    // Skip only if we already know the server copy has nothing new (needs a Message-ID).
                    (msg.messageId.isBlank() || msg.messageId !in failedIds)
            }
            .sortedByDescending { it.timestamp }
            .take(50)
        if (candidates.isEmpty()) return
        com.mailchat.ai.AppLogger.log(
            appContext,
            "ATTACH repair (chat): ${candidates.size} candidate(s): " +
                candidates.joinToString(", ") { "'${it.subject.take(25)}' atts=${attachmentsMap[it.id]?.size ?: 0} mid=${if (it.messageId.isBlank()) "<EMPTY>" else it.messageId.take(25)}" }
        )
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val account = accountRepository.getAccountById(chat.accountId) ?: return@launch
            for (msg in candidates) {
                if (!repairedAttachLocalIds.add(msg.id)) continue
                try {
                    // includeOutgoing=true: a reply ("AW: …") with an inline attachment is in the
                    // Sent folder, not the INBOX.
                    var fetched = if (msg.messageId.isNotBlank()) {
                        imapService.fetchMessageByMessageId(account, msg.messageId, includeOutgoing = true)
                    } else {
                        null
                    }
                    // Fallback: Message-ID missing or the server lookup failed -> match by subject+date.
                    if (fetched == null) {
                        com.mailchat.ai.AppLogger.log(
                            appContext,
                            "ATTACH repair (chat): Message-ID lookup failed/empty for '${msg.subject.take(40)}' (mid='${msg.messageId.take(30)}') -> trying subject+date"
                        )
                        fetched = imapService.fetchMessageBySubjectAndDate(
                            account, msg.subject, msg.timestamp, includeOutgoing = true
                        )
                    }
                    if (fetched == null) {
                        com.mailchat.ai.AppLogger.log(
                            appContext,
                            "ATTACH repair (chat): server copy NOT FOUND for '${msg.subject.take(40)}' (mid='${msg.messageId.take(30)}')"
                        )
                        continue
                    }
                    // Compare the server's attachments with the local ones by file name and add only
                    // the missing ones (avoids duplicating the CID images that are already stored).
                    val localNames = (attachmentsMap[msg.id] ?: emptyList())
                        .map { it.fileName.lowercase() }.toSet()
                    val newAttachments = fetched.attachments.filter { it.fileName.lowercase() !in localNames }
                    if (newAttachments.isEmpty()) {
                        // Nothing new on the server — remember it so it is not re-fetched on every
                        // chat open forever.
                        if (msg.messageId.isNotBlank()) {
                            com.mailchat.util.SettingsUtils.addAttachRepairFailedMessageId(appContext, msg.messageId)
                        }
                        com.mailchat.ai.AppLogger.log(
                            appContext,
                            "ATTACH repair (chat): no new attachments for '${msg.subject.take(40)}' (server=${fetched.attachments.size}, local=${localNames.size})"
                        )
                        continue
                    }
                    for (att in newAttachments) {
                        messageRepository.insertAttachment(att.copy(messageId = msg.id))
                    }
                    // Refresh the attachments map so the UI shows the newly added attachments.
                    loadAttachments(_uiState.value.messages)
                    com.mailchat.ai.AppLogger.log(
                        appContext,
                        "ATTACH repair (chat): added ${newAttachments.size} missing attachment(s) for '${msg.subject.take(40)}': ${newAttachments.joinToString { it.fileName }}"
                    )
                } catch (e: Exception) {
                    android.util.Log.w("MailchatChat", "attach repair failed for ${msg.messageId}", e)
                    com.mailchat.ai.AppLogger.log(appContext, "ATTACH repair (chat) error for '${msg.subject.take(40)}': ${e.message}")
                }
            }
        }
    }

    fun markAsRead() {
        viewModelScope.launch {
            messageRepository.markAllRead(chatId)
            chatRepository.getChatById(chatId)?.let { chat ->
                chatRepository.updateChat(chat.copy(unreadCount = 0))
            }
            // Refresh the home-screen widget so the now-read emails disappear from the
            // "unread" list, making room for other unread emails.
            com.mailchat.widget.MailWidgetProvider.refreshAll(appContext)
        }
    }

    /**
     * Returns all messages belonging to the poll of [message] (the per-recipient poll emails, the
     * answers and the result emails), or just [message] when it's not a poll message. Deleting a
     * poll removes the whole poll flow.
     */
    private suspend fun pollRelatedMessages(message: Message): List<Message> {
        val isPollMsg = message.pollId.isNotBlank() && !message.isPollAnswer &&
            PollFormat.parseResultMarker(message.bodyFull.ifBlank { message.body }) == null
        if (!isPollMsg) return listOf(message)
        // Group poll: the message carries the groupId. Collect the per-recipient poll IDs too so
        // the answers and result emails (which carry the per-recipient poll-ID) are included.
        val groupPolls = pollRepository.getPollsByGroupSync(message.pollId, message.accountId)
        val pollIds = if (groupPolls.isNotEmpty()) {
            (listOf(message.pollId) + groupPolls.map { it.id }).distinct()
        } else {
            listOf(message.pollId)
        }
        val related = mutableListOf<Message>()
        for (id in pollIds) {
            related.addAll(messageRepository.getMessagesByPollId(id))
        }
        if (related.none { it.id == message.id }) related.add(message)
        return related
    }

    /** Deletes the poll records and votes of the poll of [message] (no-op when not a poll). */
    private suspend fun cleanupPollData(message: Message) {
        val isPollMsg = message.pollId.isNotBlank() && !message.isPollAnswer &&
            PollFormat.parseResultMarker(message.bodyFull.ifBlank { message.body }) == null
        if (!isPollMsg) return
        val groupPolls = pollRepository.getPollsByGroupSync(message.pollId, message.accountId)
        val pollIds = if (groupPolls.isNotEmpty()) {
            groupPolls.map { it.id }
        } else {
            listOf(message.pollId)
        }
        for (id in pollIds) {
            pollRepository.deleteVotes(id, message.accountId)
            pollRepository.deletePoll(id, message.accountId)
        }
    }

    /**
     * After deleting messages, refresh the chat's cached "last message" fields (subject, body,
     * timestamp) so the chat overview shows the subject of the newest REMAINING message — or an
     * empty subject when the chat has no messages left. Without this, deleting the last email
     * would keep showing the deleted email's subject forever.
     */
    private suspend fun refreshChatLastMessage(chatId: Long) {
        try {
            val chat = chatRepository.getChatById(chatId) ?: return
            val messages = messageRepository.getMessagesByChatSync(chatId)
            val latest = messages.maxByOrNull { it.timestamp }
            val updated = chat.copy(
                lastMessageSubject = latest?.subject ?: "",
                lastMessageBody = latest?.body ?: "",
                lastMessageTimestamp = latest?.timestamp ?: 0L
            )
            chatRepository.updateChat(updated)
            _uiState.update { it.copy(chat = updated) }
        } catch (_: Exception) {
        }
    }

    /** Deletes [toDelete] from the IMAP server (grouped by incoming/outgoing) and locally. */
    private suspend fun deleteMessagesWithServer(account: com.mailchat.data.model.EmailAccount?, toDelete: List<Message>) {
        if (account != null) {
            try {
                // Sticky notes are local-only (never sent) — never touch the server for them.
                val incoming = toDelete.filter { it.isIncoming && !it.isStickyNote && it.messageId.isNotBlank() }.map { it.messageId }
                val outgoing = toDelete.filter { !it.isIncoming && !it.isStickyNote && it.messageId.isNotBlank() }.map { it.messageId }
                if (incoming.isNotEmpty()) {
                    imapService.deleteMessagesFromServer(account, incoming, isIncoming = true)
                }
                if (outgoing.isNotEmpty()) {
                    imapService.deleteMessagesFromServer(account, outgoing, isIncoming = false)
                }
            } catch (_: Exception) {}
        }
        for (msg in toDelete) {
            messageRepository.deleteAttachmentsByMessage(msg.id)
            messageRepository.deleteMessage(msg)
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            try {
                val chat = _uiState.value.chat
                val account = chat?.let { accountRepository.getAccountById(it.accountId) }
                // When deleting a poll, remove the whole poll flow (poll emails, answers, result
                // emails) from the server and locally, plus the poll records and votes.
                val toDelete = pollRelatedMessages(message)
                deleteMessagesWithServer(account, toDelete)
                cleanupPollData(message)
                refreshChatLastMessage(chatId)
            } catch (_: Exception) {
            }
        }
    }

    fun deleteMessages(messageIds: List<Long>) {
        viewModelScope.launch {
            try {
                val chat = _uiState.value.chat
                val account = chat?.let { accountRepository.getAccountById(it.accountId) }
                val messages = _uiState.value.messages.filter { it.id in messageIds }
                // Expand poll messages to the whole poll flow (poll emails, answers, result emails).
                val toDelete = mutableListOf<Message>()
                for (msg in messages) {
                    for (related in pollRelatedMessages(msg)) {
                        if (toDelete.none { it.id == related.id }) toDelete.add(related)
                    }
                }
                deleteMessagesWithServer(account, toDelete)
                for (msg in messages) {
                    cleanupPollData(msg)
                }
                refreshChatLastMessage(chatId)
                val remaining = messageRepository.getMessagesByChatSync(chatId)
                _uiState.update { it.copy(messages = remaining) }
            } catch (_: Exception) {
            }
        }
    }

    fun markMessagesAsRead(messageIds: List<Long>) {
        viewModelScope.launch {
            val messages = _uiState.value.messages.filter { it.id in messageIds }
            for (msg in messages) {
                if (!msg.isRead) {
                    messageRepository.updateMessage(msg.copy(isRead = true))
                }
            }
            val remaining = messageRepository.getMessagesByChatSync(chatId)
            _uiState.update { it.copy(messages = remaining) }
            // Refresh the home-screen widget so the now-read emails disappear from the
            // "unread" list, making room for other unread emails.
            com.mailchat.widget.MailWidgetProvider.refreshAll(appContext)
        }
    }

    fun summarizeMessages(messages: List<Message>) {
        aiProcessingJob = viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!com.mailchat.ai.GeminiService.isEngineReady()) return@launch
                for (msg in messages) {
                    if (coroutineContext[kotlinx.coroutines.Job]?.isActive == false) return@launch
                    if (msg.bodyFull.isBlank()) continue
                    try {
                        _uiState.update { it.copy(currentSummarizingMessageId = msg.id) }
                        // Full AI pipeline (same as the overview): spam classification +
                        // custom tags + summary. forceSummary=true because the user
                        // explicitly requested the summary for these messages.
                        val updatedMsg = com.mailchat.util.AiMessageProcessor.processMessage(
                            appContext, msg, forceSummary = true
                        )
                        messageRepository.updateMessage(updatedMsg)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // skip single-message errors
                    }
                }
                val remaining = messageRepository.getMessagesByChatSync(chatId)
                _uiState.update { it.copy(messages = remaining, currentSummarizingMessageId = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stopped by the user — no error message.
                throw e
            } finally {
                _uiState.update { it.copy(isAiProcessing = false, currentSummarizingMessageId = null) }
            }
        }
    }

    fun toggleSpamForMessages(messages: List<Message>) {
        viewModelScope.launch {
            for (msg in messages) {
                val newSpam = !msg.isSpam
                messageRepository.updateMessage(msg.copy(isSpam = newSpam, spamCategory = if (newSpam) "unwanted_ad" else ""))
            }
            val remaining = messageRepository.getMessagesByChatSync(chatId)
            _uiState.update { it.copy(messages = remaining) }
        }
    }

    fun setSpamCategoryForMessages(messages: List<Message>, category: String) {
        viewModelScope.launch {
            val isSpam = category != "ham"
            // Update the explicitly selected/target messages.
            for (msg in messages) {
                messageRepository.updateMessage(msg.copy(isSpam = isSpam, spamCategory = if (isSpam) category else ""))
            }
            // For phishing and "not phishing" (ham) the decision applies to the whole
            // sender/recipient combination: every INCOMING message from the same sender(s) in
            // this chat is marked (or unmarked) consistently, so the user doesn't end up with
            // some emails from a phishing sender flagged and others not. Only incoming messages
            // are relevant here (they come from the external sender to this account).
            if (category == "phishing" || category == "ham") {
                val chat = _uiState.value.chat
                if (chat != null) {
                    val senders = messages.filter { it.isIncoming }.map { it.from.lowercase() }.toSet()
                    if (senders.isNotEmpty()) {
                        // Persist the decision as a blacklist entry so it survives chat deletion
                        // and applies to future emails from the same sender.
                        if (category == "phishing") {
                            com.mailchat.util.SettingsUtils.addPhishingBlacklist(appContext, senders)
                        } else {
                            com.mailchat.util.SettingsUtils.removePhishingBlacklist(appContext, senders)
                        }
                        val allMessages = messageRepository.getMessagesByChatSync(chat.id)
                        for (msg in allMessages) {
                            if (msg.isIncoming && msg.from.lowercase() in senders) {
                                messageRepository.updateMessage(
                                    msg.copy(isSpam = isSpam, spamCategory = if (isSpam) category else "")
                                )
                            }
                        }
                    }
                }
            }
            val remaining = messageRepository.getMessagesByChatSync(chatId)
            _uiState.update { it.copy(messages = remaining) }
        }
    }

    fun setProcessedForMessages(messages: List<Message>, processed: Boolean) {
        viewModelScope.launch {
            for (msg in messages) {
                messageRepository.updateMessage(msg.copy(isProcessed = processed))
            }
            val remaining = messageRepository.getMessagesByChatSync(chatId)
            _uiState.update { it.copy(messages = remaining) }
        }
    }

    fun setTodoForMessages(messages: List<Message>, todo: Boolean) {
        viewModelScope.launch {
            for (msg in messages) {
                messageRepository.updateMessage(msg.copy(isTodo = todo))
            }
            val remaining = messageRepository.getMessagesByChatSync(chatId)
            _uiState.update { it.copy(messages = remaining) }
        }
    }

    fun toggleProcessed(message: Message) {
        viewModelScope.launch {
            val updated = message.copy(isProcessed = !message.isProcessed)
            messageRepository.updateMessage(updated)
            val chat = _uiState.value.chat ?: return@launch
            val messages = messageRepository.getMessagesByChatSync(chat.id)
            _uiState.update { it.copy(messages = messages) }
        }
    }

    fun toggleTodo(message: Message) {
        viewModelScope.launch {
            val updated = message.copy(isTodo = !message.isTodo)
            messageRepository.updateMessage(updated)
            val chat = _uiState.value.chat ?: return@launch
            val messages = messageRepository.getMessagesByChatSync(chat.id)
            _uiState.update { it.copy(messages = messages) }
        }
    }

    fun cycleMessageFlags(message: Message) {
        viewModelScope.launch {
            val fresh = messageRepository.getMessageById(message.id) ?: return@launch
            val updated = when {
                fresh.isTodo -> fresh.copy(isTodo = false, isProcessed = true)
                fresh.isProcessed -> fresh.copy(isProcessed = false)
                else -> fresh.copy(isTodo = true)
            }
            messageRepository.updateMessage(updated)
            val chat = _uiState.value.chat ?: return@launch
            val messages = messageRepository.getMessagesByChatSync(chat.id)
            _uiState.update { it.copy(messages = messages) }
        }
    }

    fun toggleSpam(message: Message) {
        viewModelScope.launch {
            val newSpam = !message.isSpam
            val updated = message.copy(isSpam = newSpam, spamCategory = if (newSpam) "unwanted_ad" else "")
            messageRepository.updateMessage(updated)
            if (!newSpam) {
                val chat = _uiState.value.chat
                if (chat != null) {
                    val allMessages = messageRepository.getMessagesByChatSync(chat.id)
                    for (msg in allMessages) {
                        if (msg.from.equals(message.from, ignoreCase = true) && msg.isSpam) {
                            messageRepository.updateMessage(msg.copy(isSpam = false, spamCategory = ""))
                        }
                    }
                }
            }
            val chat = _uiState.value.chat ?: return@launch
            val messages = messageRepository.getMessagesByChatSync(chat.id)
            _uiState.update { it.copy(messages = messages) }
        }
    }

    fun setCustomTag(message: Message, tag: String) {
        viewModelScope.launch {
            val updated = message.copy(customTag = tag)
            messageRepository.updateMessage(updated)
            val chat = _uiState.value.chat ?: return@launch
            val messages = messageRepository.getMessagesByChatSync(chat.id)
            _uiState.update { it.copy(messages = messages) }
        }
    }

    fun toggleStarred(message: Message) {
        viewModelScope.launch {
            val updated = message.copy(isStarred = !message.isStarred)
            messageRepository.updateMessage(updated)
            val chat = _uiState.value.chat ?: return@launch
            val messages = messageRepository.getMessagesByChatSync(chat.id)
            _uiState.update { it.copy(messages = messages) }
        }
    }

    fun getFiveLines(message: Message): String {
        val source = if (message.summary.isNotBlank()) message.summary
        else message.bodyFull.ifBlank { message.body }.stripHtml().normalizeWhitespace()
        val lines = source.lineSequence().toList()
        return lines.take(5).joinToString("\n")
    }

    fun copyFiveLines(message: Message) {
        val fiveLines = getFiveLines(message)
        val clipboard = appContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MailChat", fiveLines))
    }

    fun forwardFiveLines(message: Message) {
        val fiveLines = getFiveLines(message)
        val dateStr = java.text.SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(message.timestamp))
        val quoted = fiveLines.lineSequence().joinToString("\n") { "> $it" }
        // The quote goes into the message field (editable) instead of a hidden append on send.
        // Forward: always included, no toggle (replyQuote stays null).
        val quoteBlock = "— $dateStr, ${message.from}:\n$quoted"
        _uiState.update {
            it.copy(
                composeText = if (it.composeText.isBlank()) quoteBlock else it.composeText + "\n\n" + quoteBlock,
                selectedRecipient = null,
                replyQuote = null,
                quoteEnabled = false
            )
        }
    }

    fun replyWithFiveLinesQuote(message: Message) {
        val fiveLines = getFiveLines(message)
        val dateStr = java.text.SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(message.timestamp))
        val quoted = fiveLines.lineSequence().joinToString("\n") { "> $it" }
        // The quote goes into the message field (editable) and can be toggled off (quoteEnabled).
        val quoteBlock = "— $dateStr, ${message.from}:\n$quoted"
        _uiState.update {
            it.copy(
                composeText = appendQuoteBlock(it.composeText, quoteBlock),
                selectedRecipient = if (message.isIncoming) message.from else null,
                replyQuote = quoteBlock,
                quoteEnabled = true
            )
        }
    }

    fun onComposeTextChange(text: String) {
        _uiState.update { it.copy(composeText = text, sendError = null) }
        scheduleDraftSave()
    }

    fun onSubjectChange(subject: String) {
        currentSubject = subject
        scheduleDraftSave()
    }

    fun toggleQuote() {
        _uiState.update { state ->
            val quote = state.replyQuote
            if (quote == null) {
                // Manually composed message: the quote is appended at send time.
                state.copy(quoteEnabled = !state.quoteEnabled)
            } else if (state.quoteEnabled) {
                // Turn off: remove the quote from the message field.
                state.copy(composeText = removeQuoteBlock(state.composeText, quote), quoteEnabled = false)
            } else {
                // Turn on: put the quote back into the message field.
                state.copy(composeText = appendQuoteBlock(state.composeText, quote), quoteEnabled = true)
            }
        }
    }

    /**
     * Appends the quote block to the compose text in the format matching the text
     * (plain text stays plain; if the text is already HTML, the quote is converted to HTML).
     */
    private fun appendQuoteBlock(text: String, quote: String): String {
        return if (com.mailchat.network.SmtpService.looksLikeHtml(text)) {
            text + "<p>${com.mailchat.network.SmtpService.escapeHtml(quote).replace("\n", "<br>")}</p>"
        } else if (text.isBlank()) {
            quote
        } else {
            text + "\n\n" + quote
        }
    }

    /**
     * Removes the quote block from the compose text (best effort): first an exact match
     * (plain or HTML form), then a search for the quote's header line.
     */
    private fun removeQuoteBlock(text: String, quote: String): String {
        val trimmed = text.trimEnd()
        if (trimmed.endsWith(quote)) return trimmed.removeSuffix(quote).trimEnd()
        val htmlQuote = "<p>${com.mailchat.network.SmtpService.escapeHtml(quote).replace("\n", "<br>")}</p>"
        if (trimmed.endsWith(htmlQuote)) return trimmed.removeSuffix(htmlQuote).trimEnd()
        val header = quote.lineSequence().first()
        val idx = text.indexOf(header)
        if (idx >= 0) return text.substring(0, idx).trimEnd()
        return text
    }

    fun clearAiSuggestion() {
        _uiState.update { it.copy(aiSuggestion = "", aiError = "") }
    }

    fun generateReplySuggestion(subjectHint: String, tone: String = "informal") {
        viewModelScope.launch {
            _uiState.update { it.copy(aiError = "", aiSuggestion = "", isGeneratingSuggestion = true) }
            try {
                val engineOk = com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!engineOk) {
                    _uiState.update {
                        it.copy(
                            aiError = appContext.getString(com.mailchat.R.string.assistant_model_missing),
                            isGeneratingSuggestion = false
                        )
                    }
                    return@launch
                }
                val state = _uiState.value
                val chat = state.chat
                val language = com.mailchat.util.LanguageUtils.getLanguage(appContext)
                val subject = subjectHint.ifBlank { state.messages.lastOrNull()?.subject ?: "" }
                val account = chat?.let { accountRepository.getAccountById(it.accountId) }
                val senderName = account?.emailAddress ?: ""
                val recipientName = state.contactName
                    ?: chat?.participantName?.takeIf { it != chat.participantEmail }
                    ?: chat?.participantEmail
                    ?: ""

                val replyTarget = state.replyTargetMessageId?.let { id ->
                    state.messages.firstOrNull { it.id == id }
                }

                val result = if (replyTarget != null) {
                    val emailText = replyTarget.bodyFull.ifBlank { replyTarget.body }
                        .stripHtml().normalizeWhitespace().take(3000)
                    val draft = state.composeText
                    try {
                        com.mailchat.ai.GeminiService.suggestEmailReply(emailText, draft, tone, language, replyTarget.subject)
                    } catch (e: IllegalStateException) {
                        throw e
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    val lastMessages = state.messages.takeLast(3).joinToString("\n") {
                        "${if (it.isIncoming) "From" else "To"}: ${it.body.take(200)}"
                    }
                    val prevWords = com.mailchat.util.SettingsUtils.getPreviousComposeWords(appContext)
                    // The user's own text in the reply field (e.g. a negative reply) — for
                    // forwards, strip the forwarded-email block so only the user's note counts.
                    val draft = state.composeText
                        .substringBefore("---------- Weitergeleitete E-Mail ----------")
                        .trim()
                        .take(1000)
                    try {
                        com.mailchat.ai.GeminiService.suggestReply(lastMessages, prevWords, language, subject, senderName, recipientName, draft)
                    } catch (e: IllegalStateException) {
                        throw e
                    } catch (e: Exception) {
                        ""
                    }
                }
                _uiState.update {
                    if (result.isNotBlank()) it.copy(aiSuggestion = result, isGeneratingSuggestion = false)
                    else it.copy(aiError = appContext.getString(com.mailchat.R.string.assistant_model_missing), isGeneratingSuggestion = false)
                }
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(aiError = appContext.getString(com.mailchat.R.string.assistant_model_missing), isGeneratingSuggestion = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(aiError = e.message ?: "error", isGeneratingSuggestion = false) }
            }
        }
    }

    fun summarizeMessage(message: Message) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentSummarizingMessageId = message.id) }
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!com.mailchat.ai.GeminiService.isEngineReady()) {
                    throw IllegalStateException("No model")
                }
                // Explicit user request: force the summary even for short messages.
                val updatedMsg = com.mailchat.util.AiMessageProcessor.processMessage(appContext, message, forceSummary = true)
                messageRepository.updateMessage(updatedMsg)
                val updated = messageRepository.getMessagesByChatSync(chatId)
                _uiState.update { it.copy(messages = updated, currentSummarizingMessageId = null) }
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(currentSummarizingMessageId = null) }
                android.widget.Toast.makeText(
                    appContext,
                    appContext.getString(com.mailchat.R.string.assistant_model_missing),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("MailchatAI", "summarizeMessage failed: ${e.message}")
                _uiState.update { it.copy(currentSummarizingMessageId = null) }
                android.widget.Toast.makeText(
                    appContext,
                    appContext.getString(com.mailchat.R.string.ai_summary_error, e.message ?: ""),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Translates the given message into the app's UI language (on-device, one-tap). The result is
     * stored in [ChatDetailUiState.translationText] and shown in a dialog by the screen.
     */
    fun translateMessage(message: Message) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, translationText = "", translationError = "") }
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!com.mailchat.ai.GeminiService.isEngineReady()) {
                    throw IllegalStateException("No model")
                }
                val text = message.bodyFull.ifBlank { message.body }
                val lang = com.mailchat.util.LanguageUtils.getLanguage(appContext)
                val result = com.mailchat.ai.GeminiService.translate(text, lang)
                _uiState.update { it.copy(translationText = result, isTranslating = false) }
            } catch (e: IllegalStateException) {
                _uiState.update {
                    it.copy(isTranslating = false, translationError = appContext.getString(com.mailchat.R.string.assistant_model_missing))
                }
            } catch (e: Exception) {
                android.util.Log.e("MailchatAI", "translateMessage failed: ${e.message}")
                _uiState.update { it.copy(isTranslating = false, translationError = e.message ?: "error") }
            }
        }
    }

    /**
     * Suggests a subject line for the text currently being composed (on-device, one-tap). The
     * result is stored in [ChatDetailUiState.aiSuggestedSubject]; the screen observes it and fills
     * the subject field, then calls [clearSuggestedSubject].
     */
    fun suggestSubjectForCompose() {
        viewModelScope.launch {
            val body = _uiState.value.composeText
            if (body.isBlank()) return@launch
            _uiState.update { it.copy(isSuggestingSubject = true) }
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!com.mailchat.ai.GeminiService.isEngineReady()) {
                    throw IllegalStateException("No model")
                }
                val lang = com.mailchat.util.LanguageUtils.getLanguage(appContext)
                val result = com.mailchat.ai.GeminiService.suggestSubject(body, lang)
                _uiState.update { it.copy(aiSuggestedSubject = result, isSuggestingSubject = false) }
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(isSuggestingSubject = false) }
                android.widget.Toast.makeText(
                    appContext, appContext.getString(com.mailchat.R.string.assistant_model_missing),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("MailchatAI", "suggestSubjectForCompose failed: ${e.message}")
                _uiState.update { it.copy(isSuggestingSubject = false) }
            }
        }
    }

    fun clearSuggestedSubject() {
        _uiState.update { it.copy(aiSuggestedSubject = "") }
    }

    /**
     * Generates 2-3 one-tap quick replies for the latest incoming message (on-device). The result
     * is stored in [ChatDetailUiState.quickReplies] and shown as chips above the compose bar.
     */
    fun generateQuickReplies() {
        viewModelScope.launch {
            val latestIncoming = _uiState.value.messages.lastOrNull { it.isIncoming } ?: return@launch
            _uiState.update { it.copy(isGeneratingQuickReplies = true, quickReplies = emptyList()) }
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!com.mailchat.ai.GeminiService.isEngineReady()) {
                    throw IllegalStateException("No model")
                }
                val lang = com.mailchat.util.LanguageUtils.getLanguage(appContext)
                val text = latestIncoming.bodyFull.ifBlank { latestIncoming.body }
                val result = com.mailchat.ai.GeminiService.quickReplies(text, lang)
                _uiState.update { it.copy(quickReplies = result, isGeneratingQuickReplies = false) }
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(isGeneratingQuickReplies = false) }
                android.widget.Toast.makeText(
                    appContext, appContext.getString(com.mailchat.R.string.assistant_model_missing),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("MailchatAI", "generateQuickReplies failed: ${e.message}")
                _uiState.update { it.copy(isGeneratingQuickReplies = false) }
            }
        }
    }

    fun generateMessageReply(message: Message, tone: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSuggestion = true, aiError = "") }
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!com.mailchat.ai.GeminiService.isEngineReady()) {
                    throw IllegalStateException("No model")
                }
                val lang = com.mailchat.util.LanguageUtils.getLanguage(appContext)
                val emailText = message.bodyFull.ifBlank { message.body }
                    .stripHtml().normalizeWhitespace().take(3000)
                val draft = _uiState.value.composeText
                val result = com.mailchat.ai.GeminiService.suggestEmailReply(emailText, draft, tone, lang, message.subject)
                val prefix = if (lang == "de") "Aw: " else "Re: "
                val subject = result.substringAfter("SUBJECT:").substringBefore("\n").trim()
                    .ifBlank { prefix + message.subject }
                val body = result.substringAfter("BODY:").trim()
                if (body.isNotBlank()) {
                    onSubjectChange(subject)
                    onComposeTextChange(body)
                }
                _uiState.update { it.copy(isGeneratingSuggestion = false) }
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(isGeneratingSuggestion = false, aiError = appContext.getString(com.mailchat.R.string.assistant_model_missing)) }
            } catch (e: Exception) {
                android.util.Log.e("MailchatAI", "generateMessageReply failed: ${e.message}")
                _uiState.update { it.copy(isGeneratingSuggestion = false, aiError = e.message ?: "error") }
            }
        }
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            val state = _uiState.value
            if (state.composeText.isBlank() && currentSubject.isBlank()) {
                draftDao.deleteDraft(chatId)
            } else {
                draftDao.saveDraft(
                    com.mailchat.data.model.Draft(
                        chatId = chatId,
                        subject = currentSubject,
                        body = state.composeText,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun addComposeAttachment(file: File) =
        _uiState.update { it.copy(composeAttachments = it.composeAttachments + file) }

    /**
     * Generates an OpenStreetMap image (red marker at the given position) and attaches it to the
     * message being composed ("Mein Standort" from the "+" menu). Returns true on success.
     */
    suspend fun attachLocationMap(lat: Double, lon: Double): Boolean {
        val mapFile = mapImageService.generateMapImage(lat, lon) ?: return false
        // Move the map into the compose-attachments dir so it survives cache cleanup of the
        // generic cache dir and gets a stable, descriptive name.
        val attachDir = File(appContext.filesDir, "compose_attachments").apply { mkdirs() }
        val target = File(attachDir, "standort_${System.currentTimeMillis()}.png")
        mapFile.copyTo(target, overwrite = true)
        mapFile.delete()
        addComposeAttachment(target)
        return true
    }

    /**
     * Saves a local sticky note (Notizzettel) in this chat: it appears in the chat as a colored
     * bubble but is never sent as an email. [colorKey] is one of
     * [com.mailchat.util.StickyNoteColors] (default: yellow).
     */
    fun saveStickyNote(text: String, colorKey: String = com.mailchat.util.StickyNoteColors.YELLOW) {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val note = Message(
                chatId = chatId,
                accountId = chat.accountId,
                from = "Notizzettel",
                subject = "",
                body = text,
                bodyFull = text,
                timestamp = System.currentTimeMillis(),
                isRead = true,
                isIncoming = false,
                isProcessed = true,
                messageId = "sticky-${System.currentTimeMillis()}",
                isStickyNote = true,
                stickyNoteColor = colorKey
            )
            messageRepository.insertMessage(note)
        }
    }

    /**
     * Saves the SAME sticky note (text + color) under EACH of the given selected messages:
     * one note is inserted right after each selected message (timestamp = message timestamp + 1ms,
     * so the note appears directly below it in the chat). Used from the "Bearbeiten" menu when
     * several emails are selected.
     */
    fun saveStickyNoteUnderMessages(messages: List<Message>, text: String, colorKey: String = com.mailchat.util.StickyNoteColors.YELLOW) {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val sorted = messages.sortedBy { it.timestamp }
            sorted.forEachIndexed { index, msg ->
                val note = Message(
                    chatId = chatId,
                    accountId = chat.accountId,
                    from = "Notizzettel",
                    subject = "",
                    body = text,
                    bodyFull = text,
                    timestamp = msg.timestamp + 1 + index,
                    isRead = true,
                    isIncoming = false,
                    isProcessed = true,
                    messageId = "sticky-${System.currentTimeMillis()}-$index",
                    isStickyNote = true,
                    stickyNoteColor = colorKey
                )
                messageRepository.insertMessage(note)
            }
        }
    }

    /**
     * Updates the text and/or color of an existing sticky note (opened by clicking the note in
     * the chat). The note stays local-only and is never sent as an email.
     */
    fun updateStickyNote(message: Message, text: String, colorKey: String? = null) {
        viewModelScope.launch {
            val updated = message.copy(
                body = text,
                bodyFull = text,
                stickyNoteColor = colorKey ?: message.stickyNoteColor
            )
            messageRepository.updateMessage(updated)
            // Keep the chat list preview up to date when the note is the chat's last message.
            val chat = _uiState.value.chat ?: return@launch
            if (chat.lastMessageTimestamp <= message.timestamp) {
                chatRepository.updateChat(chat.copy(lastMessageBody = text))
            }
        }
    }

    fun removeComposeAttachment(file: File) =
        _uiState.update { it.copy(composeAttachments = it.composeAttachments - file) }

    fun removeAllComposeAttachments() =
        _uiState.update { it.copy(composeAttachments = emptyList()) }

    fun createCompressedAttachment(password: String) {
        viewModelScope.launch {
            try {
                val text = _uiState.value.composeText
                val attachments = _uiState.value.composeAttachments
                val attachDir = File(appContext.filesDir, "compose_attachments").apply { mkdirs() }
                val finalFile = File(attachDir, "mailchat_archive.zip")

                if (password.isNotBlank()) {
                    // Echte passwortgeschtzte ZIP (WinZip-kompatibel), damit externe Entpacker sie oeffnen koennen
                    val zipFile = net.lingala.zip4j.ZipFile(finalFile)
                    zipFile.setPassword(password.toCharArray())
                    val params = net.lingala.zip4j.model.ZipParameters().apply {
                        encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                        aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
                        compressionMethod = net.lingala.zip4j.model.enums.CompressionMethod.DEFLATE
                    }
                    var entryIndex = 0
                    if (text.isNotBlank()) {
                        val tmpTxt = File(appContext.cacheDir, "message.txt").apply {
                            mkdirs()
                            writeText(text)
                        }
                        params.setFileNameInZip("message.txt")
                        zipFile.addFile(tmpTxt, params)
                        tmpTxt.delete()
                        entryIndex++
                    }
                    for (file in attachments) {
                        if (!file.exists()) continue
                        params.setFileNameInZip(file.name.ifBlank { "attachment_$entryIndex" })
                        entryIndex++
                        zipFile.addFile(file, params)
                    }
                } else {
                    val dir = File(appContext.cacheDir, "compress_tmp").apply { mkdirs() }
                    val zipFilePlain = File(dir, "mailchat_archive.zip")
                    java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFilePlain)).use { zos ->
                        if (text.isNotBlank()) {
                            zos.putNextEntry(java.util.zip.ZipEntry("message.txt"))
                            zos.write(text.toByteArray())
                            zos.closeEntry()
                        }
                        for (file in attachments) {
                            zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                    zipFilePlain.copyTo(finalFile, overwrite = true)
                    zipFilePlain.delete()
                    dir.deleteRecursively()
                }

                _uiState.update {
                    it.copy(
                        composeAttachments = it.composeAttachments + finalFile,
                        composeText = ""
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MailchatCompress", "Error: ${e.message}")
            }
        }
    }

    fun compressImageAttachments() {
        viewModelScope.launch {
            val compressed = _uiState.value.composeAttachments.map { file ->
                if (isImageFile(file)) compressImage(file) else file
            }
            _uiState.update { it.copy(composeAttachments = compressed) }
        }
    }

    private fun isImageFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
    }

    private fun compressImage(file: File): File {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: return file
            val maxDim = 1920
            val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            } else 1f
            val scaled = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap
            val out = File(file.parent, "compressed_${file.name}")
            java.io.FileOutputStream(out).use { fos ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, fos)
            }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            file.delete()
            out
        } catch (e: Exception) {
            file
        }
    }

    fun loadContactPhoto() {
        val chat = _uiState.value.chat ?: return
        val email = chat.participantEmail
        viewModelScope.launch {
            val isGroup = com.mailchat.util.EmailGrouping.isGroupKey(email)
            val emails = if (isGroup) email.split(";").filter { it.isNotBlank() } else listOf(email)
            val photos = contactService.getPhotoUrisByEmails(emails)
            val names = contactService.getNamesByEmails(emails)
            val mainPhoto = photos[email.lowercase()]
            val mainName = names[email.lowercase()]
            if (mainName != null && chat.participantName == email) {
                chatRepository.updateChat(chat.copy(participantName = mainName))
                _uiState.update { it.copy(contactPhotoUri = mainPhoto, contactName = mainName, chat = chat.copy(participantName = mainName), contactPhotos = photos, contactNames = names) }
            } else {
                _uiState.update { it.copy(contactPhotoUri = mainPhoto, contactName = mainName, contactPhotos = photos, contactNames = names) }
            }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            val contacts = contactService.getAllContacts()
            _uiState.update { it.copy(contacts = contacts) }
        }
    }

    fun setContactPhoto(email: String, uri: android.net.Uri) {
        viewModelScope.launch {
            contactService.setCustomPhoto(email, uri)
            contactService.invalidateContactCache()
            loadContacts()
            loadContactPhoto()
        }
    }

    fun onRecipientSelected(recipient: String?) {
        _uiState.update { it.copy(selectedRecipient = recipient) }
    }

    fun getChatParticipants(): List<String> {
        val state = _uiState.value
        val chat = state.chat ?: return emptyList()
        val result = LinkedHashSet<String>()
        chat.participantEmail.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach { result.add(it) }
        state.messages.map { it.from.trim() }.filter { it.isNotBlank() }.forEach { result.add(it) }
        return result.toList()
    }

    fun renameChat(newName: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            val trimmed = newName.trim()
            if (trimmed.isBlank()) return@launch
            try {
                chatRepository.updateChat(chat.copy(participantName = trimmed))
                _uiState.update {
                    it.copy(chat = chat.copy(participantName = trimmed))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(sendError = e.message) }
            }
        }
    }

    fun updateGroupMembers(members: List<String>) {
        viewModelScope.launch {
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            val cleaned = members.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
            if (cleaned.size < 2) return@launch
            val newKey = cleaned.sorted().joinToString(";")
            val newName = com.mailchat.util.EmailGrouping.groupDisplayName(cleaned)
            try {
                chatRepository.updateChat(chat.copy(participantEmail = newKey, participantName = newName))
                _uiState.update {
                    it.copy(
                        chat = chat.copy(participantEmail = newKey, participantName = newName),
                        selectedRecipient = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(sendError = e.message) }
            }
        }
    }

    /**
     * Splits a previously merged chat back apart: moves all INCOMING messages from [participant]
     * out of the current chat into their own chat (creating it if needed). This undoes a merge
     * for one of the merged senders (e.g. when the wrong address was selected during a merge).
     *
     * Only incoming messages are moved: outgoing messages can't be attributed to a specific
     * sender without the original recipient address, so they stay in the current chat. The chat's
     * own (main) participant is never split out.
     */
    fun splitParticipantFromChat(participant: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            val targetEmail = participant.trim().lowercase()
            if (targetEmail.isBlank()) return@launch
            if (targetEmail == chat.participantEmail.trim().lowercase()) return@launch

            val messages = messageRepository.getMessagesByChatSync(chat.id)
            val toMove = messages.filter { it.isIncoming && it.from.trim().lowercase() == targetEmail }
            if (toMove.isEmpty()) {
                com.mailchat.ai.AppLogger.log(appContext, "Split: no incoming messages from $targetEmail")
                return@launch
            }

            try {
                // Find or create the target chat for this participant.
                val existing = chatRepository.findByEmailAndAccount(targetEmail, chat.accountId)
                val newChatId: Long
                if (existing != null) {
                    newChatId = existing.id
                } else {
                    val name = contactService.getNamesByEmails(listOf(targetEmail))[targetEmail]
                        ?: targetEmail.substringBefore('@')
                    val latest = toMove.maxOf { it.timestamp }
                    val lastBody = toMove.maxByOrNull { it.timestamp }?.body ?: ""
                    val newChat = com.mailchat.data.model.Chat(
                        participantEmail = targetEmail,
                        participantName = name,
                        accountId = chat.accountId,
                        lastMessageTimestamp = latest,
                        lastMessageBody = lastBody
                    )
                    newChatId = chatRepository.insertChat(newChat)
                }

                val moved = messageRepository.moveIncomingByFromToChat(chat.id, newChatId, targetEmail)

                // Refresh the current chat's last message preview.
                val remaining = messageRepository.getMessagesByChatSync(chat.id)
                if (remaining.isNotEmpty()) {
                    val latest = remaining.maxOf { it.timestamp }
                    val lastBody = remaining.maxByOrNull { it.timestamp }?.body ?: ""
                    chatRepository.updateChat(chat.copy(lastMessageTimestamp = latest, lastMessageBody = lastBody))
                }

                _uiState.update {
                    it.copy(
                        messages = remaining,
                        selectedRecipient = null
                    )
                }
                com.mailchat.ai.AppLogger.log(appContext, "Split: moved $moved messages from $targetEmail to chat $newChatId")
                android.widget.Toast.makeText(
                    appContext,
                    appContext.getString(com.mailchat.R.string.split_done, targetEmail),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                _uiState.update { it.copy(sendError = e.message) }
            }
        }
    }

    fun replyWithQuote(message: Message) {
        val quotedSource = message.bodyFull.ifBlank { message.body }
            .stripHtml()
            .normalizeWhitespace()
            .take(1500)
        val dateStr = java.text.SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(message.timestamp))
        val quote = quotedSource.lineSequence().joinToString("\n") { "> $it" }
        // The quote goes into the message field (editable) and is on by default; the user can
        // toggle it off (quoteEnabled). The send flow does not append a second quote for replies.
        val quoteBlock = if (quotedSource.isBlank()) null else "— $dateStr, ${message.from}:\n$quote"
        // Always set the recipient, even when the body is empty (e.g. image-only or
        // attachment-only mail) — previously an early return left the To field blank.
        _uiState.update {
            it.copy(
                composeText = quoteBlock?.let { q -> appendQuoteBlock(it.composeText, q) } ?: it.composeText,
                selectedRecipient = if (message.isIncoming) message.from else null,
                replyTargetMessageId = message.id,
                replyQuote = quoteBlock,
                quoteEnabled = quoteBlock != null
            )
        }
    }

    fun replyAll(message: Message) {
        val quotedSource = message.bodyFull.ifBlank { message.body }
            .stripHtml()
            .normalizeWhitespace()
            .take(1500)
        val dateStr = java.text.SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(message.timestamp))
        val quote = quotedSource.lineSequence().joinToString("\n") { "> $it" }
        val ownEmail = _uiState.value.accountEmail
        val ccList = message.cc.split(";").filter { it.isNotBlank() && !it.equals(ownEmail, ignoreCase = true) }
        // The quote goes into the message field (editable), on by default (see replyWithQuote).
        val quoteBlock = if (quotedSource.isBlank()) null else "— $dateStr, ${message.from}:\n$quote"
        // Always set the recipient, even when the body is empty (see replyWithQuote).
        _uiState.update {
            it.copy(
                composeText = quoteBlock?.let { q -> appendQuoteBlock(it.composeText, q) } ?: it.composeText,
                selectedRecipient = if (message.isIncoming) message.from else null,
                composeCc = ccList.joinToString(";"),
                replyTargetMessageId = message.id,
                replyQuote = quoteBlock,
                quoteEnabled = quoteBlock != null
            )
        }
    }

    fun clearComposeCc() {
        _uiState.update { it.copy(composeCc = "") }
    }

    fun setComposeCc(value: String) {
        _uiState.update { it.copy(composeCc = value) }
    }

    fun setComposeBcc(value: String) {
        _uiState.update { it.copy(composeBcc = value) }
    }

    fun setSelectedRecipient(value: String?) {
        _uiState.update { it.copy(selectedRecipient = value) }
    }

    fun openFullCompose() {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val sender = senderAccountFor(chat)
            _uiState.update {
                it.copy(
                    fullComposeOpen = true,
                    composeFromAccountId = 0L,
                    accountEmail = sender?.emailAddress ?: it.accountEmail
                )
            }
        }
    }

    fun closeFullCompose() {
        _uiState.update { it.copy(fullComposeOpen = false) }
    }

    fun setComposeFrom(accountId: Long) {
        _uiState.update { it.copy(composeFromAccountId = accountId) }
    }

    /** Rohquelltext per IMAP laden; Fallback: Header + gespeicherter Body. */
    fun loadRawSource(message: Message, toAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(rawSource = null, rawSourceLoading = true) }
            try {
                val account = message.accountId.takeIf { it != 0L }?.let { accountRepository.getAccountById(it) }
                var raw = if (account != null && message.messageId.isNotBlank()) {
                    imapService.fetchRawMessage(account, message.messageId)
                } else null
                if (raw.isNullOrBlank()) {
                    raw = buildString {
                        appendLine("From: ${message.from}")
                        appendLine("To: ${toAddress.ifBlank { "?" }}")
                        if (message.cc.isNotBlank()) appendLine("Cc: ${message.cc}")
                        appendLine("Subject: ${message.subject}")
                        appendLine("Date: ${java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm z", java.util.Locale.US).format(java.util.Date(message.timestamp))}")
                        if (message.messageId.isNotBlank()) appendLine("Message-ID: ${message.messageId}")
                        appendLine()
                        append(message.bodyFull)
                    }
                }
                _uiState.update { it.copy(rawSource = raw, rawSourceLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        rawSource = "Quelltext konnte nicht geladen werden: ${e.message}",
                        rawSourceLoading = false
                    )
                }
            }
        }
    }

    fun closeRawSource() {
        _uiState.update { it.copy(rawSource = null, rawSourceLoading = false) }
    }

    /**
     * Manuell: verschlüsselte gesendete Nachricht im Postausgang durch die entschlüsselte
     * Version ersetzen (damit sie in allen E-Mail-Clients lesbar ist).
     */
    fun saveDecryptedToSent(message: Message) {
        viewModelScope.launch {
            val account = message.accountId.takeIf { it != 0L }?.let { accountRepository.getAccountById(it) }
            val ok = if (account != null && message.messageId.isNotBlank()) {
                runCatching { imapService.replaceSentMessageWithDecrypted(account, message.messageId) }
                    .getOrDefault(false)
            } else false
            android.widget.Toast.makeText(
                appContext,
                appContext.getString(
                    if (ok) com.mailchat.R.string.secure_writeback_success
                    else com.mailchat.R.string.secure_writeback_failed
                ),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Prepares a forward in the unified full compose overlay (same UI as reply / reply-all).
     * The complete original text (as a "forwarded email" block) and the original attachments
     * are pre-selected; the recipient is left empty for the user to pick. The actual send goes
     * through the normal [send] path.
     */
    fun prepareForward(message: Message) {
        viewModelScope.launch {
            val quotedSource = message.bodyFull.ifBlank { message.body }
                .stripHtml()
                .normalizeWhitespace()
            val dateStr = java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date(message.timestamp))
            val forwardBlock = buildString {
                appendLine("---------- Weitergeleitete E-Mail ----------")
                appendLine("Von: ${message.from}")
                appendLine("Datum: $dateStr")
                if (message.subject.isNotBlank()) appendLine("Betreff: ${message.subject}")
                appendLine()
                append(quotedSource)
            }
            // Use the attachments already loaded for the detail view (same source the user
            // sees), falling back to a DB lookup. This is more reliable than a fresh DB read.
            val attEntities = _uiState.value.attachmentsByMessage[message.id]
                ?: messageRepository.getAttachmentsByMessageSync(message.id)
            val originalAttachments = attEntities
                .map { java.io.File(it.uri) }
                .filter { it.exists() }
            val chat = _uiState.value.chat ?: return@launch
            val sender = senderAccountFor(chat)
            _uiState.update {
                it.copy(
                    composeText = forwardBlock,
                    composeAttachments = originalAttachments,
                    selectedRecipient = null,
                    composeCc = "",
                    composeBcc = "",
                    quoteEnabled = false,
                    replyQuote = null,
                    replyTargetMessageId = null,
                    fullComposeOpen = true,
                    composeFromAccountId = 0L,
                    accountEmail = sender?.emailAddress ?: it.accountEmail
                )
            }
        }
    }

    fun forwardTo(recipient: String, message: Message, customText: String = "", contactName: String = "", forceUnencrypted: Boolean = false) {
        viewModelScope.launch {
            lastForwardParams = ForwardParams(recipient, message, customText, contactName)
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            val account = senderAccountFor(chat) ?: return@launch
            val to = recipient.trim()
            if (to.isBlank()) return@launch
            val fwdSubject = if (message.subject.startsWith("Fwd:", ignoreCase = true) ||
                message.subject.startsWith("Fw:", ignoreCase = true)
            ) message.subject else "Fwd: ${message.subject.ifBlank { message.from }}"
            val quotedSource = message.bodyFull.ifBlank { message.body }
                .stripHtml()
                .normalizeWhitespace()
                .take(2000)
            val dateStr = java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date(message.timestamp))
            val displayName = if (contactName.isNotBlank()) contactName else to
            // The custom text may be HTML (rich-text editor) or plain text; the forwarded
            // email block and the signature are rendered to match.
            val customTextIsHtml = com.mailchat.network.SmtpService.looksLikeHtml(customText)
            fun plainToHtmlBlock(t: String): String =
                com.mailchat.network.SmtpService.escapeHtml(t).replace("\n", "<br>")
            val forwardBody = buildString {
                if (customTextIsHtml) {
                    if (customText.isNotBlank()) {
                        append(customText)
                        append("<p></p>")
                    }
                    append("<p>---------- Weitergeleitete E-Mail ----------<br>Von: ${com.mailchat.network.SmtpService.escapeHtml(message.from)}<br>An: ${com.mailchat.network.SmtpService.escapeHtml(displayName)}<br>Datum: $dateStr")
                    if (message.subject.isNotBlank()) append("<br>Betreff: ${com.mailchat.network.SmtpService.escapeHtml(message.subject)}")
                    append("</p><p>${plainToHtmlBlock(quotedSource)}</p>")
                } else {
                    if (customText.isNotBlank()) {
                        append(customText)
                        appendLine()
                        appendLine()
                    }
                    appendLine("---------- Weitergeleitete E-Mail ----------")
                    appendLine("Von: ${message.from}")
                    appendLine("An: $displayName")
                    appendLine("Datum: $dateStr")
                    if (message.subject.isNotBlank()) appendLine("Betreff: ${message.subject}")
                    appendLine()
                    append(quotedSource)
                }
            }
            val signature = com.mailchat.util.SettingsUtils.getSignature(appContext)
            val body = if (signature.isNotBlank()) {
                if (customTextIsHtml) "$forwardBody<p>${plainToHtmlBlock(signature)}</p>"
                else "$forwardBody\n\n$signature"
            } else forwardBody
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val originalAttachments = messageRepository.getAttachmentsByMessageSync(message.id)
                    .map { java.io.File(it.uri) }
                    .filter { it.exists() }
                var forwardAttachments = originalAttachments
                var finalBody = body
                var finalSubject = fwdSubject
                var isEncryptedMsg = false
                var encryptionAttempted = false
                val extraHeaders = mutableMapOf<String, String>()
                val firstRecipient = to.split(",").firstOrNull()?.trim()?.lowercase() ?: ""
                val hasId = com.mailchat.crypto.OpenPgpKeyService.hasIdentity(appContext)
                com.mailchat.ai.AppLogger.log(appContext, "forwardTo: to=$firstRecipient hasIdentity=$hasId forceUnencrypted=$forceUnencrypted")

                // --- OpenPGP (primary): same as the send flow ---
                if (!forceUnencrypted && hasId) {
                    // Returns an error string when encryption was attempted (peer key found)
                    // but failed; null otherwise. withContext is not inline, so the error is
                    // returned instead of using a non-local return.
                    val encError: String? = withContext(Dispatchers.IO) {
                        try {
                            val pubKey = com.mailchat.crypto.OpenPgpKeyService
                                .currentPeerKey(appContext, firstRecipient)
                            com.mailchat.ai.AppLogger.log(appContext, "forwardTo: peer key found=${!pubKey.isNullOrBlank()}")
                            if (!pubKey.isNullOrBlank()) {
                                encryptionAttempted = true
                                // Optionally append the key signature (own public key or a
                                // link to it) so the recipient can reply encrypted. Only part
                                // of the sent (encrypted) body, not the local display copy.
                                val keySig = com.mailchat.util.SettingsUtils.getEncryptedKeySignatureText(appContext)
                                val bodyToEncrypt = if (keySig.isNotBlank()) "$body\n\n$keySig" else body
                                finalBody = com.mailchat.crypto.OpenPgpKeyService.signAndEncryptForRecipient(appContext, bodyToEncrypt, pubKey)
                                isEncryptedMsg = true
                                extraHeaders["X-MailChat-Encryption"] = "OpenPGP"
                                com.mailchat.ai.AppLogger.log(appContext, "forwardTo: signed+encrypted with OpenPGP (keySig=${keySig.isNotBlank()})")
                                // Optionally encrypt the subject as well (never fails the send).
                                if (com.mailchat.crypto.OpenPgpKeyService.isEncryptSubjectEnabled(appContext) && fwdSubject.isNotBlank()) {
                                    try {
                                        finalSubject = com.mailchat.crypto.OpenPgpKeyService.encryptSubject(appContext, fwdSubject, listOf(pubKey)) ?: fwdSubject
                                    } catch (e: Exception) {
                                        com.mailchat.ai.AppLogger.log(appContext, "forwardTo: subject encryption failed: ${e.message}")
                                    }
                                }
                                if (forwardAttachments.isNotEmpty()) {
                                    val encDir = java.io.File(appContext.cacheDir, "enc_attachments").apply { mkdirs() }
                                    forwardAttachments = forwardAttachments.map { file ->
                                        val encFile = java.io.File(encDir, "${file.name}.gpg")
                                        com.mailchat.crypto.OpenPgpKeyService.encryptFile(file, encFile, pubKey)
                                        encFile
                                    }
                                }
                                null
                            } else {
                                com.mailchat.ai.AppLogger.log(appContext, "forwardTo: no public key for $firstRecipient -> plaintext")
                                null
                            }
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "forwardTo: OpenPGP encryption failed: ${e.message}")
                            if (encryptionAttempted) "OpenPGP: ${e.message}" else null
                        }
                    }
                    if (encError != null) {
                        encryptionErrorRetry = { forwardToUnencryptedRetry() }
                        _uiState.update { it.copy(isSending = false, encryptionErrorDialog = encError) }
                        return@launch
                    }
                }

                // --- Legacy MAILCHAT_ENC fallback ---
                if (!isEncryptedMsg && !forceUnencrypted) {
                    val peer = com.mailchat.crypto.CryptoService.getPeers(appContext)
                        .firstOrNull { it.email.lowercase() == firstRecipient && it.pinVerified }
                    if (peer != null) {
                        try {
                            encryptionAttempted = true
                            val encrypted = com.mailchat.crypto.CryptoService.encryptForRecipient(body, peer.publicKeyArmored)
                            finalBody = "MAILCHAT_ENC:$encrypted"
                            isEncryptedMsg = true
                            extraHeaders["X-MailChat-Encryption"] = "AES-256-GCM"
                            extraHeaders["X-MailChat-Fingerprint"] = peer.fingerprint
                            com.mailchat.ai.AppLogger.log(appContext, "forwardTo: encrypted with legacy MAILCHAT_ENC")
                            if (forwardAttachments.isNotEmpty()) {
                                val encDir = java.io.File(appContext.cacheDir, "enc_attachments").apply { mkdirs() }
                                forwardAttachments = forwardAttachments.map { file ->
                                    val encFile = java.io.File(encDir, "${file.name}.enc")
                                    com.mailchat.crypto.CryptoService.encryptFile(file, encFile, peer.publicKeyArmored)
                                    encFile
                                }
                            }
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "forwardTo: legacy encryption failed: ${e.message}")
                            if (encryptionAttempted) {
                                encryptionErrorRetry = { forwardToUnencryptedRetry() }
                                _uiState.update { it.copy(isSending = false, encryptionErrorDialog = "Legacy: ${e.message}") }
                                return@launch
                            }
                        }
                    }
                }
                com.mailchat.ai.AppLogger.log(appContext, "forwardTo: final isEncrypted=$isEncryptedMsg")
                val sentMessageId = smtpService.sendEmail(
                    account = account,
                    to = to,
                    subject = finalSubject,
                    body = finalBody,
                    attachments = forwardAttachments,
                    extraHeaders = extraHeaders,
                    bodyFormat = if (isEncryptedMsg) com.mailchat.network.BodyFormat.PLAIN
                        else if (com.mailchat.network.SmtpService.looksLikeHtml(body)) com.mailchat.network.BodyFormat.HTML
                        else com.mailchat.network.BodyFormat.PLAIN_AS_HTML
                )
                val normalizedTo = EmailGrouping.normalize(to)
                val existingChat = chatRepository.findByEmailAndAccount(normalizedTo, account.id)
                val targetChat = if (existingChat != null) {
                    if (existingChat.hidden) {
                        chatRepository.setChatHidden(existingChat.id, false)
                    }
                    existingChat
                } else {
                    val newChat = Chat(
                        participantEmail = normalizedTo,
                        participantName = EmailGrouping.displayName("", normalizedTo),
                        accountId = account.id,
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = body,
                        lastMessageSubject = fwdSubject,
                        unreadCount = 0,
                        backgroundImageUri = null
                    )
                    val id = chatRepository.insertChat(newChat)
                    newChat.copy(id = id)
                }
                val newMessage = Message(
                    chatId = targetChat.id,
                    accountId = account.id,
                    from = account.emailAddress,
                    subject = fwdSubject,
                    body = body,
                    bodyFull = body,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    isIncoming = false,
                    messageId = sentMessageId,
                    isEncrypted = isEncryptedMsg
                )
                val newMessageId = messageRepository.insertMessage(newMessage)
                val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
                for ((index, file) in originalAttachments.withIndex()) {
                    val lowerName = file.name.lowercase()
                    val mime = when {
                        lowerName.endsWith(".png") -> "image/png"
                        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
                        lowerName.endsWith(".gif") -> "image/gif"
                        lowerName.endsWith(".webp") -> "image/webp"
                        lowerName.endsWith(".pdf") -> "application/pdf"
                        else -> "application/octet-stream"
                    }
                    messageRepository.insertAttachment(
                        Attachment(
                            messageId = newMessageId,
                            fileName = file.name,
                            mimeType = mime,
                            uri = file.absolutePath,
                            isImage = imageExtensions.any { lowerName.endsWith(it) },
                            sizeBytes = file.length(),
                            sortOrder = index
                        )
                    )
                }
                // Encrypted forwarded email: replace the copy in the Sent folder with the
                // decrypted version so all email clients can read it (recipient got the
                // encrypted one). The message may not be in the Sent folder yet, so retry.
                if (isEncryptedMsg && sentMessageId.isNotBlank() &&
                    com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackEnabled(appContext)
                ) {
                    val acc = account
                    val mid = sentMessageId
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(5000)
                        for (attempt in 1..4) {
                            val ok = runCatching { imapService.replaceSentMessageWithDecrypted(acc, mid) }
                                .getOrDefault(false)
                            if (ok) break
                            kotlinx.coroutines.delay(10000)
                        }
                    }
                }
                chatRepository.updateChat(
                    targetChat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = body,
                        lastMessageSubject = fwdSubject
                    )
                )
                _uiState.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message) }
            }
        }
    }

    fun send(to: String? = null, subject: String? = null, forceUnencrypted: Boolean = false) {
        viewModelScope.launch {
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            val account = senderAccountFor(chat) ?: return@launch
            val selected = state.selectedRecipient
            val recipient = to
                ?: selected?.takeIf { it.isNotBlank() }
                ?: if (EmailGrouping.isGroupKey(chat.participantEmail)) {
                    chat.participantEmail.split(";")
                        .filter { it != EmailGrouping.normalize(account.emailAddress) }
                        .joinToString(",")
                        .ifBlank { chat.participantEmail }
                } else {
                    chat.participantEmail
                }
            val rawBody = state.composeText
            if (rawBody.isNotBlank()) {
                val words = rawBody.split(Regex("[\\s,.;!?]+")).filter { it.length > 2 }
                com.mailchat.util.SettingsUtils.addComposeWords(appContext, words)
            }
            val latestIncoming = state.messages.lastOrNull { it.isIncoming }
            val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
            // Group chats always use the group name as the subject (no "Re:" prefix). This keeps
            // the subject stable so incoming emails can be filtered/identified by the group name.
            val baseSubject = if (isGroupChat) {
                chat.participantName
            } else {
                subject?.takeIf { it.isNotBlank() }
                    ?: (if (forceUnencrypted) pendingSendSubject?.takeIf { it.isNotBlank() } else null)
                    ?: currentSubject.takeIf { it.isNotBlank() }
                    ?: latestIncoming?.subject?.takeIf { it.isNotBlank() }
                    ?: chat.participantName
            }
            pendingSendSubject = baseSubject
            val replySubject = if (!isGroupChat && latestIncoming != null &&
                !baseSubject.startsWith("Re:", ignoreCase = true) &&
                !baseSubject.startsWith("Aw:", ignoreCase = true) &&
                !baseSubject.startsWith("Fwd:", ignoreCase = true) &&
                !baseSubject.startsWith("Fw:", ignoreCase = true)
            ) "Re: $baseSubject" else baseSubject
            val extraHeaders = mutableMapOf<String, String>()
            if (latestIncoming != null && latestIncoming.messageId.isNotBlank()) {
                extraHeaders["In-Reply-To"] = latestIncoming.messageId
                extraHeaders["References"] = latestIncoming.messageId
            }
            // The compose body may be HTML (rich-text editor) or plain text. Plain-text
            // sections (quote, group notice, signature) are converted to HTML only when the
            // body is already HTML, so the sections stay visually distinct in both cases.
            val rawBodyIsHtml = com.mailchat.network.SmtpService.looksLikeHtml(rawBody)
            fun plainToHtmlBlock(t: String): String =
                com.mailchat.network.SmtpService.escapeHtml(t).replace("\n", "<br>")
            // The quote for reply / reply-all / forward is part of the compose text itself
            // (editable in the message field). quoteEnabled only adds the latest incoming
            // message's quote for manually composed messages (the "Include quote" toggle).
            val bodyWithQuote = if (_uiState.value.quoteEnabled && _uiState.value.replyQuote == null && rawBody.isNotBlank() && latestIncoming != null) {
                val quotedSource = latestIncoming.bodyFull.ifBlank { latestIncoming.body }
                    .stripHtml()
                    .normalizeWhitespace()
                    .take(1500)
                if (quotedSource.isNotBlank()) {
                    val dateStr = java.text.SimpleDateFormat(
                        "dd.MM.yyyy HH:mm",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(latestIncoming.timestamp))
                    val quote = quotedSource.lineSequence().joinToString("\n") { "> $it" }
                    val quoteBlock = "— $dateStr, ${latestIncoming.from}:\n$quote"
                    if (rawBodyIsHtml) "$rawBody<p>${plainToHtmlBlock(quoteBlock)}</p>"
                    else "$rawBody\n\n$quoteBlock"
                } else rawBody
            } else rawBody
            val signature = com.mailchat.util.SettingsUtils.getSignature(appContext)
            // Group chats get an additional notice so recipients know to reply to all.
            val groupNoticeText = if (isGroupChat) appContext.getString(com.mailchat.R.string.group_chat_notice) else ""
            // Clean structure: message body → group notice (clearly marked) → signature (last,
            // separated by a blank line). The blank lines become paragraph breaks when the body
            // is rendered as HTML, so the sections stay visually distinct.
            val body = buildString {
                append(bodyWithQuote)
                if (groupNoticeText.isNotBlank()) {
                    if (rawBodyIsHtml) append("<p>💬 $groupNoticeText</p>")
                    else append("\n\n💬 $groupNoticeText")
                }
                if (signature.isNotBlank()) {
                    if (rawBodyIsHtml) append("<p>${plainToHtmlBlock(signature)}</p>")
                    else append("\n\n$signature")
                }
            }
            if (recipient.isBlank() || (rawBody.isBlank() && state.composeAttachments.isEmpty())) {
                return@launch
            }
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val recipients = recipient.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                val firstRecipient = recipients.firstOrNull() ?: ""
                var finalBody = body
                var finalSubject = replySubject
                var isEncryptedMsg = false
                var encryptionAttempted = false
                var finalAttachments = state.composeAttachments
                val hasId = com.mailchat.crypto.OpenPgpKeyService.hasIdentity(appContext)
                val bcOk = com.mailchat.crypto.OpenPgpKeyService.ensureProviderRegistered()
                com.mailchat.ai.AppLogger.log(appContext, "sendEmail: to=$firstRecipient hasIdentity=$hasId bcProvider=$bcOk forceUnencrypted=$forceUnencrypted")

                // --- OpenPGP (primary): current peer key; when auto-lookup is enabled the
                // key freshness is checked on keys.openpgp.org before every send ---
                // Group chats are only encrypted when they were created as encrypted; 1:1 chats
                // keep the automatic behavior (encrypt when the recipient has a key).
                val shouldEncrypt = if (isGroupChat) chat.isEncrypted else true
                if (!forceUnencrypted && hasId && shouldEncrypt) {
                    // Returns an error string when encryption was attempted (peer key found)
                    // but failed; null otherwise. withContext is not inline, so the error is
                    // returned instead of using a non-local return.
                    val encError: String? = withContext(Dispatchers.IO) {
                        try {
                            // Collect the public keys of ALL recipients who have a key. For group
                            // chats this enables multi-recipient encryption so every member with a
                            // key can decrypt the message (not just the first recipient).
                            val pubKeys = recipients.mapNotNull {
                                com.mailchat.crypto.OpenPgpKeyService.currentPeerKey(appContext, it)
                            }
                            com.mailchat.ai.AppLogger.log(appContext, "sendEmail: peer keys found=${pubKeys.size}/${recipients.size}")
                            if (pubKeys.isNotEmpty()) {
                                encryptionAttempted = true
                                // Optionally append the key signature (own public key or a
                                // link to it) so the recipient can reply encrypted. Only part
                                // of the sent (encrypted) body, not the local display copy.
                                val keySig = com.mailchat.util.SettingsUtils.getEncryptedKeySignatureText(appContext)
                                val bodyToEncrypt = if (keySig.isNotBlank()) "$body\n\n$keySig" else body
                                finalBody = if (pubKeys.size > 1) {
                                    com.mailchat.crypto.OpenPgpKeyService.signAndEncryptForRecipients(appContext, bodyToEncrypt, pubKeys)
                                } else {
                                    com.mailchat.crypto.OpenPgpKeyService.signAndEncryptForRecipient(appContext, bodyToEncrypt, pubKeys.first())
                                }
                                isEncryptedMsg = true
                                extraHeaders["X-MailChat-Encryption"] = "OpenPGP"
                                com.mailchat.ai.AppLogger.log(appContext, "sendEmail: signed+encrypted with OpenPGP for ${pubKeys.size} recipient(s) (keySig=${keySig.isNotBlank()})")
                                // Optionally encrypt the subject as well (never fails the send).
                                if (com.mailchat.crypto.OpenPgpKeyService.isEncryptSubjectEnabled(appContext) && replySubject.isNotBlank()) {
                                    try {
                                        finalSubject = com.mailchat.crypto.OpenPgpKeyService.encryptSubject(appContext, replySubject, pubKeys) ?: replySubject
                                    } catch (e: Exception) {
                                        com.mailchat.ai.AppLogger.log(appContext, "sendEmail: subject encryption failed: ${e.message}")
                                    }
                                }
                                if (state.composeAttachments.isNotEmpty()) {
                                    val encDir = java.io.File(appContext.cacheDir, "enc_attachments").apply { mkdirs() }
                                    finalAttachments = state.composeAttachments.map { file ->
                                        val encFile = java.io.File(encDir, "${file.name}.gpg")
                                        if (pubKeys.size > 1) {
                                            com.mailchat.crypto.OpenPgpKeyService.encryptFilesForRecipients(file, encFile, pubKeys)
                                        } else {
                                            com.mailchat.crypto.OpenPgpKeyService.encryptFile(file, encFile, pubKeys.first())
                                        }
                                        encFile
                                    }
                                }
                                null
                            } else {
                                com.mailchat.ai.AppLogger.log(appContext, "sendEmail: no public key for any recipient -> plaintext")
                                null
                            }
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "sendEmail: OpenPGP encryption failed: ${e.message}")
                            if (encryptionAttempted) "OpenPGP: ${e.message}" else null
                        }
                    }
                    if (encError != null) {
                        encryptionErrorRetry = { send(forceUnencrypted = true) }
                        _uiState.update { it.copy(isSending = false, encryptionErrorDialog = encError) }
                        return@launch
                    }
                }

                // --- Legacy MAILCHAT_ENC fallback ---
                if (!isEncryptedMsg && !forceUnencrypted) {
                    val peer = com.mailchat.crypto.CryptoService.getPeers(appContext)
                        .firstOrNull { it.email.lowercase() == firstRecipient && it.pinVerified }
                    if (peer != null) {
                        try {
                            encryptionAttempted = true
                            val encrypted = com.mailchat.crypto.CryptoService.encryptForRecipient(body, peer.publicKeyArmored)
                            finalBody = "MAILCHAT_ENC:$encrypted"
                            isEncryptedMsg = true
                            extraHeaders["X-MailChat-Encryption"] = "AES-256-GCM"
                            extraHeaders["X-MailChat-Fingerprint"] = peer.fingerprint
                            com.mailchat.ai.AppLogger.log(appContext, "sendEmail: encrypted with legacy MAILCHAT_ENC")
                            if (state.composeAttachments.isNotEmpty()) {
                                val encDir = java.io.File(appContext.cacheDir, "enc_attachments").apply { mkdirs() }
                                finalAttachments = state.composeAttachments.map { file ->
                                    val encFile = java.io.File(encDir, "${file.name}.enc")
                                    com.mailchat.crypto.CryptoService.encryptFile(file, encFile, peer.publicKeyArmored)
                                    encFile
                                }
                            }
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "sendEmail: legacy encryption failed: ${e.message}")
                            if (encryptionAttempted) {
                                encryptionErrorRetry = { send(forceUnencrypted = true) }
                                _uiState.update { it.copy(isSending = false, encryptionErrorDialog = "Legacy: ${e.message}") }
                                return@launch
                            }
                        }
                    }
                }
                com.mailchat.ai.AppLogger.log(appContext, "sendEmail: final isEncrypted=$isEncryptedMsg")
                val sentMessageId = smtpService.sendEmail(
                    account = account,
                    to = recipient,
                    subject = finalSubject,
                    body = finalBody,
                    attachments = finalAttachments,
                    extraHeaders = extraHeaders,
                    cc = _uiState.value.composeCc,
                    bcc = _uiState.value.composeBcc,
                    bodyFormat = if (isEncryptedMsg) com.mailchat.network.BodyFormat.PLAIN
                        else if (com.mailchat.network.SmtpService.looksLikeHtml(body)) com.mailchat.network.BodyFormat.HTML
                        else com.mailchat.network.BodyFormat.PLAIN_AS_HTML
                )
                val message = Message(
                    chatId = chat.id,
                    accountId = account.id,
                    from = account.emailAddress,
                    subject = replySubject,
                    body = body,
                    bodyFull = body,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    isIncoming = false,
                    messageId = sentMessageId,
                    isEncrypted = isEncryptedMsg
                )
                val messageId = messageRepository.insertMessage(message)
                val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
                for ((index, file) in state.composeAttachments.withIndex()) {
                    val lowerName = file.name.lowercase()
                    val mime = when {
                        lowerName.endsWith(".png") -> "image/png"
                        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
                        lowerName.endsWith(".gif") -> "image/gif"
                        lowerName.endsWith(".webp") -> "image/webp"
                        lowerName.endsWith(".pdf") -> "application/pdf"
                        else -> "application/octet-stream"
                    }
                    messageRepository.insertAttachment(
                        Attachment(
                            messageId = messageId,
                            fileName = file.name,
                            mimeType = mime,
                            uri = file.absolutePath,
                            isImage = imageExtensions.any { lowerName.endsWith(it) },
                            sizeBytes = file.length(),
                            sortOrder = index
                        )
                    )
                }
                // Encrypted sent email: replace the copy in the Sent folder with the
                // decrypted version so all email clients can read it (recipient got the
                // encrypted one). The message may not be in the Sent folder yet, so retry.
                if (isEncryptedMsg && sentMessageId.isNotBlank() &&
                    com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackEnabled(appContext)
                ) {
                    val acc = account
                    val mid = sentMessageId
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(5000)
                        for (attempt in 1..4) {
                            val ok = runCatching { imapService.replaceSentMessageWithDecrypted(acc, mid) }
                                .getOrDefault(false)
                            if (ok) break
                            kotlinx.coroutines.delay(10000)
                        }
                    }
                }
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = body
                    )
                )
                draftDao.deleteDraft(chatId)
                currentSubject = ""
                pendingSendSubject = null
                _uiState.update {
                    it.copy(
                        isSending = false,
                        composeText = "",
                        composeAttachments = emptyList(),
                        draftSubject = null,
                        replyQuote = null,
                        quoteEnabled = false,
                        composeCc = "",
                        composeBcc = "",
                        fullComposeOpen = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message) }
            }
        }
    }

    /**
     * Sends the "Wo bist Du?" location request: a standalone message with the exact subject
     * "Standortanfrage" and the body "Wo bist du?" + "Anfrage von <contactName>". It goes
     * through the same encryption + SMTP pipeline as a normal message (encrypted when the
     * recipient has a key) and is saved locally so it shows up in the chat. It does NOT use the
     * current compose draft (no attachments, cc/bcc or quote) and does NOT get a "Re:" prefix.
     */
    fun sendLocationRequest(contactName: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            val account = senderAccountFor(chat) ?: return@launch
            val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
            val recipient = if (isGroupChat) {
                chat.participantEmail.split(";")
                    .filter { it != EmailGrouping.normalize(account.emailAddress) }
                    .joinToString(",")
                    .ifBlank { chat.participantEmail }
            } else {
                chat.participantEmail
            }
            if (recipient.isBlank()) return@launch
            val body = "Wo bist du?\n\nAnfrage von $contactName"
            val subject = "Standortanfrage"
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val recipients = recipient.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                val firstRecipient = recipients.firstOrNull() ?: ""
                var finalBody = body
                var isEncryptedMsg = false
                val extraHeaders = mutableMapOf<String, String>()
                // OpenPGP (primary) — same as a normal send.
                if (com.mailchat.crypto.OpenPgpKeyService.hasIdentity(appContext)) {
                    withContext(Dispatchers.IO) {
                        try {
                            val pubKey = com.mailchat.crypto.OpenPgpKeyService.currentPeerKey(appContext, firstRecipient)
                            if (!pubKey.isNullOrBlank()) {
                                val keySig = com.mailchat.util.SettingsUtils.getEncryptedKeySignatureText(appContext)
                                val bodyToEncrypt = if (keySig.isNotBlank()) "$body\n\n$keySig" else body
                                finalBody = com.mailchat.crypto.OpenPgpKeyService.signAndEncryptForRecipient(appContext, bodyToEncrypt, pubKey)
                                isEncryptedMsg = true
                                extraHeaders["X-MailChat-Encryption"] = "OpenPGP"
                                com.mailchat.ai.AppLogger.log(appContext, "Location request: signed+encrypted with OpenPGP")
                            } else {
                                com.mailchat.ai.AppLogger.log(appContext, "Location request: no public key for $firstRecipient -> plaintext")
                            }
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "Location request: OpenPGP encryption failed: ${e.message}")
                        }
                    }
                }
                // Legacy MAILCHAT_ENC fallback.
                if (!isEncryptedMsg) {
                    val peer = com.mailchat.crypto.CryptoService.getPeers(appContext)
                        .firstOrNull { it.email.lowercase() == firstRecipient && it.pinVerified }
                    if (peer != null) {
                        try {
                            finalBody = "MAILCHAT_ENC:" + com.mailchat.crypto.CryptoService.encryptForRecipient(body, peer.publicKeyArmored)
                            isEncryptedMsg = true
                            extraHeaders["X-MailChat-Encryption"] = "AES-256-GCM"
                            extraHeaders["X-MailChat-Fingerprint"] = peer.fingerprint
                            com.mailchat.ai.AppLogger.log(appContext, "Location request: encrypted with legacy MAILCHAT_ENC")
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "Location request: legacy encryption failed: ${e.message}")
                        }
                    }
                }
                val sentMessageId = smtpService.sendEmail(
                    account = account,
                    to = recipient,
                    subject = subject,
                    body = finalBody,
                    extraHeaders = extraHeaders,
                    bodyFormat = if (isEncryptedMsg) com.mailchat.network.BodyFormat.PLAIN
                        else com.mailchat.network.BodyFormat.PLAIN_AS_HTML
                )
                val message = Message(
                    chatId = chat.id,
                    accountId = account.id,
                    from = account.emailAddress,
                    subject = subject,
                    body = body,
                    bodyFull = body,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    isIncoming = false,
                    messageId = sentMessageId,
                    isEncrypted = isEncryptedMsg
                )
                messageRepository.insertMessage(message)
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = body,
                        lastMessageSubject = subject
                    )
                )
                com.mailchat.ai.AppLogger.log(appContext, "Location request 'Standortanfrage' sent to $recipient (encrypted=$isEncryptedMsg)")
                _uiState.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                com.mailchat.ai.AppLogger.log(appContext, "Location request failed: ${e.message}")
                _uiState.update { it.copy(isSending = false, sendError = e.message) }
            }
        }
    }

    // --- Polls (Umfragen) ---

    data class PollResults(
        val poll: Poll,
        val votes: List<PollVote>,
        val counts: List<Int>,
        val totalVotes: Int,
        // The group's per-recipient polls (for the creator's "who has answered" list). Empty for
        // single polls (recipient's view / legacy polls).
        val recipients: List<Poll> = emptyList(),
        // The structured result from the result email (recipient's view: the same aggregated
        // data the creator sees). Null while the poll is open or for legacy result emails.
        val resultData: PollFormat.PollResultData? = null,
        // The structured appointment result (recipient's view: the same responses the creator
        // sees). Null while the appointment is open or for legacy result emails.
        val apptResultData: AppointmentFormat.ApptResultData? = null
    )

    /**
     * Observes a poll and its votes (for the bar chart). Emits null when the poll is gone.
     *
     * When [pollId] is a group (the creator's logical poll, linking several per-recipient
     * poll-IDs), the results are aggregated across ALL per-recipient poll-IDs and ALL accounts
     * (the same vote can be recorded in several accounts → deduped by voter, last answer wins).
     * This is what makes the multi-account setup work (the creator reads several accounts).
     */
    fun observePollResults(pollId: String): Flow<PollResults?> {
        val chat = _uiState.value.chat ?: return flowOf(null)
        val accountId = chat.accountId
        return flow {
            val groupPolls = pollRepository.getPollsByGroupSync(pollId, accountId)
            if (groupPolls.isNotEmpty()) {
                // Group (creator's logical poll): aggregate all per-recipient poll-IDs + votes
                // across all accounts. The votes query is REACTIVE (re-emits when a vote is
                // inserted/updated), so the view updates live without reopening the email.
                pollRepository.getPollsByGroup(pollId, accountId)
                    .flatMapLatest { polls ->
                        if (polls.isEmpty()) {
                            flowOf(null)
                        } else {
                            val pollIds = polls.map { it.id }
                            pollRepository.getVotesForPollsAllAccountsFlow(pollIds).map { votes ->
                                val deduped = dedupeVotesByVoter(votes)
                                val options = PollFormat.optionsFromJson(polls.first().options)
                                val (counts, total) = computePollCounts(options, votes, polls.first().numberMode)
                                // The group is open while ANY per-recipient poll is still open.
                                val status = if (polls.any { it.status == Poll.STATUS_OPEN }) Poll.STATUS_OPEN else Poll.STATUS_CLOSED
                                PollResults(polls.first().copy(status = status), deduped, counts, total, recipients = polls)
                            }
                        }
                    }.collect { emit(it) }
            } else {
                // Single poll (recipient's view or a legacy poll): per-account votes.
                val pollFlow = pollRepository.getPollsByChat(chat.id, accountId)
                    .map { list -> list.firstOrNull { it.id == pollId } }
                val votesFlow = pollRepository.getVotes(pollId, accountId)
                // Reactive message flow: re-emits when the result email is linked to the poll
                // (its pollId is set), so the structured result is loaded as soon as it arrives
                // (no need to reload the view).
                val messagesFlow = messageRepository.getMessagesByPollIdFlow(pollId)
                combine(pollFlow, votesFlow, messagesFlow) { poll, votes, messages ->
                    if (poll == null) null
                    else {
                        val options = PollFormat.optionsFromJson(poll.options)
                        val (counts, total) = computePollCounts(options, votes, poll.numberMode)
                        // Dedupe for display (one row per person in the person-table; the
                        // bar-chart counts above already sum ALL votes).
                        val deduped = dedupeVotesByVoter(votes)
                        // Closed poll: load the structured result from the result email, so the
                        // recipient sees the SAME aggregated result as the creator.
                        // For appointments the structured data carries the responses, so the
                        // recipient sees the graphical result (not the ASCII body).
                        val (resultData, apptResultData) = if (poll.status == Poll.STATUS_CLOSED) {
                            val bodies = messages.map { msg -> msg.bodyFull.ifBlank { msg.body } }
                            bodies.mapNotNull { PollFormat.parseResultData(it) }.firstOrNull() to
                                bodies.mapNotNull { AppointmentFormat.parseApptResultData(it) }.firstOrNull()
                        } else {
                            null to null
                        }
                        PollResults(poll, deduped, counts, total, resultData = resultData, apptResultData = apptResultData)
                    }
                }.collect { emit(it) }
            }
        }
    }

    /**
     * Dedupes votes by voter (the same person can answer in several accounts / re-answer).
     * The LAST answer wins (by answeredAt).
     */
    private fun dedupeVotesByVoter(votes: List<PollVote>): List<PollVote> =
        votes.groupBy { it.voterEmail.trim().lowercase() }
            .values
            .mapNotNull { it.maxByOrNull { v -> v.answeredAt } }
            .toList()

    /**
     * Resolves contact display names for the given emails (lowercase email → name). Emails
     * without a known contact are simply not in the map (the UI falls back to the email).
     */
    suspend fun resolveContactNames(emails: List<String>): Map<String, String> =
        contactService.getNamesByEmails(emails)

    /**
     * Computes the per-option result and the total for display.
     *  - Checkbox poll: counts = number of voters who selected each option; total = total selections.
     *  - Numeric poll: counts = SUM of the numbers entered per option; total = grand total of all
     *    numbers (so the percentage is each option's share of the total).
     * Returns (counts, total).
     */
    private fun computePollCounts(options: List<String>, votes: List<PollVote>, numberMode: Boolean): Pair<List<Int>, Int> {
        val counts = IntArray(options.size)
        var total = 0
        for (v in votes) {
            if (numberMode) {
                val values = PollFormat.valuesFromJson(v.values)
                for (i in options.indices) {
                    counts[i] += values.getOrElse(i) { 0.0 }.toInt()
                }
                total += values.sum().toInt()
            } else {
                val idx = PollFormat.indicesFromJson(v.optionIndices)
                for (i in idx) if (i in options.indices) counts[i]++
                total += idx.size
            }
        }
        return counts.toList() to total
    }

    /**
     * Creates and sends a poll to the current chat (1:1 or group). The creator sends ONE poll
     * email per recipient, each with its own poll-ID. All per-recipient poll-IDs are linked by a
     * shared [groupId] (the logical poll) and managed together until the poll is closed.
     */
    fun createPoll(
        title: String,
        question: String,
        options: List<String>,
        multi: Boolean,
        numberMode: Boolean = false,
        personMode: Boolean = false,
        forceUnencrypted: Boolean = false
    ) {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val account = senderAccountFor(chat) ?: return@launch
            val recipients = recipientEmailsForChat(chat, account)
            if (recipients.isEmpty()) {
                _uiState.update { it.copy(sendError = "No recipients") }
                return@launch
            }
            val isGerman = SettingsUtils.isGermanUi(appContext)
            val choiceLabel = when {
                numberMode -> if (isGerman) "Zahl pro Option eingeben (wird aufsummiert)" else "Enter a number per option (summed up)"
                multi -> if (isGerman) "mehrere Antworten möglich" else "multiple answers possible"
                else -> if (isGerman) "nur eine Antwort möglich" else "only one answer possible"
            }
            val instruction = when {
                numberMode -> if (isGerman) {
                    "Antwort: Zahl in die eckige Klammer schreiben, z. B. [5] Option 1, und " +
                        "diese E-Mail beantworten. Kommentare werden nicht angezeigt und verfallen."
                } else {
                    "Answer: write the number in the square brackets, e.g. [5] Option 1, and " +
                        "reply to this email. Comments are not displayed and will be discarded."
                }
                else -> if (isGerman) {
                    "Antwort: X in die Box(en) setzen und diese E-Mail beantworten. " +
                        "Kommentare werden nicht angezeigt und verfallen."
                } else {
                    "Answer: put an X in the box(es) and reply to this email. " +
                        "Comments are not displayed and will be discarded."
                }
            }
            val subject = "📊 $title"
            val groupId = PollFormat.newPollId()
            val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                // Prepare (build + encrypt) every recipient FIRST, so an encryption failure is
                // detected before anything is sent (a retry then resends cleanly, no duplicates).
                data class Prepared(val recipient: String, val pollId: String, val body: String, val enc: com.mailchat.crypto.EncryptionHelper.Result)
                val prepared = mutableListOf<Prepared>()
                for (recipient in recipients) {
                    val pollId = PollFormat.newPollId()
                    val body = PollFormat.buildPollBody(title, question, options, multi, pollId, choiceLabel, instruction, numberMode, personMode)
                    val enc = com.mailchat.crypto.EncryptionHelper.encryptForRecipients(
                        appContext, body, listOf(recipient.trim().lowercase()), isGroupChat, chat.isEncrypted, forceUnencrypted, subject
                    )
                    prepared.add(Prepared(recipient, pollId, body, enc))
                }
                // Send one poll email per recipient and register the per-recipient poll.
                var firstBody = ""
                for (p in prepared) {
                    if (firstBody.isEmpty()) firstBody = p.body
                    val extraHeaders = mutableMapOf("X-MailChat-Poll" to p.pollId)
                    extraHeaders.putAll(p.enc.headers)
                    // PLAIN (text/plain): the poll format is line-based and must stay plain text so
                    // the marker/options can be parsed reliably when fetched back.
                    val sentMessageId = smtpService.sendEmail(
                        account = account,
                        to = p.recipient,
                        subject = p.enc.subject ?: subject,
                        body = p.enc.body,
                        extraHeaders = extraHeaders,
                        bodyFormat = com.mailchat.network.BodyFormat.PLAIN
                    )
                    // Register the per-recipient poll in the creator's account (linked to the group).
                    val poll = Poll(
                        id = p.pollId,
                        accountId = account.id,
                        chatId = chat.id,
                        groupId = groupId,
                        recipientEmail = p.recipient.trim().lowercase(),
                        title = title,
                        question = question,
                        options = PollFormat.optionsToJson(options),
                        multi = multi,
                        numberMode = numberMode,
                        personMode = personMode,
                        creatorEmail = account.emailAddress,
                        pollMessageId = sentMessageId,
                        status = Poll.STATUS_OPEN,
                        createdAt = System.currentTimeMillis()
                    )
                    pollRepository.insertPoll(poll)
                    // One local message per recipient (real Message-ID → deduped when the sent
                    // email is fetched back). pollId = groupId so the chat shows the logical poll.
                    val message = Message(
                        chatId = chat.id,
                        accountId = account.id,
                        from = account.emailAddress,
                        subject = subject,
                        body = p.body,
                        bodyFull = p.body,
                        timestamp = System.currentTimeMillis(),
                        isRead = true,
                        isIncoming = false,
                        messageId = sentMessageId,
                        isEncrypted = p.enc.isEncrypted
                    )
                    val messageId = messageRepository.insertMessage(message)
                    messageRepository.updateMessage(message.copy(id = messageId, pollId = groupId))
                }
                // The creator can also vote: a dedicated per-recipient poll-ID for the creator
                // (NOT sent as an email — it only exists so the creator's "Answer" button works
                // and their vote is tracked like any other recipient's).
                pollRepository.insertPoll(
                    Poll(
                        id = PollFormat.newPollId(),
                        accountId = account.id,
                        chatId = chat.id,
                        groupId = groupId,
                        recipientEmail = account.emailAddress.trim().lowercase(),
                        title = title,
                        question = question,
                        options = PollFormat.optionsToJson(options),
                        multi = multi,
                        numberMode = numberMode,
                        personMode = personMode,
                        creatorEmail = account.emailAddress,
                        pollMessageId = "",
                        status = Poll.STATUS_OPEN,
                        createdAt = System.currentTimeMillis()
                    )
                )
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = firstBody,
                        lastMessageSubject = subject
                    )
                )
                _uiState.update { it.copy(isSending = false) }
            } catch (e: com.mailchat.crypto.EncryptionHelper.EncryptionFailed) {
                encryptionErrorRetry = { createPoll(title, question, options, multi, numberMode, personMode, forceUnencrypted = true) }
                _uiState.update { it.copy(isSending = false, encryptionErrorDialog = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message) }
            }
        }
    }

    /**
     * Creates a TERMIN (appointment) poll: sends one appointment email per recipient
     * (Ja/Nein/Vielleicht + comment) and registers the per-recipient poll (appointmentMode).
     * Mirrors [createPoll] but with the appointment details (start/end, all-day, location).
     *
     * [targetChatId] optionally overrides the chat the poll is sent to (e.g. when starting an
     * appointment poll from an AI-detected appointment and the user picks a different
     * recipient/group). When null, the current chat is used.
     */
    fun createAppointment(
        title: String,
        question: String,
        startDate: String,
        startTime: String,
        endDate: String,
        endTime: String,
        allDay: Boolean,
        location: String,
        targetChatId: Long? = null,
        targetEmail: String? = null,
        forceUnencrypted: Boolean = false
    ) {
        viewModelScope.launch {
            val chat = when {
                // A contact that is not in the chats (id = 0): use the current account and the
                // contact's email as the recipient.
                targetEmail != null -> {
                    val currentChat = _uiState.value.chat ?: return@launch
                    com.mailchat.data.model.Chat(
                        id = 0,
                        participantEmail = targetEmail,
                        participantName = targetEmail.substringBefore('@'),
                        accountId = currentChat.accountId
                    )
                }
                targetChatId != null -> {
                    chatRepository.getChatById(targetChatId) ?: _uiState.value.chat ?: return@launch
                }
                else -> {
                    _uiState.value.chat ?: return@launch
                }
            }
            val account = senderAccountFor(chat) ?: return@launch
            val recipients = recipientEmailsForChat(chat, account)
            if (recipients.isEmpty()) {
                _uiState.update { it.copy(sendError = "No recipients") }
                return@launch
            }
            val isGerman = SettingsUtils.isGermanUi(appContext)
            val options = if (isGerman) listOf("Ja", "Nein", "Vielleicht") else listOf("Yes", "No", "Maybe")
            val instruction = if (isGerman) {
                "Antwort: X in die Box setzen und diese E-Mail beantworten. " +
                    "Kommentar (optional): eigene Zeile \"Kommentar: ...\" schreiben."
            } else {
                "Answer: put an X in the box and reply to this email. " +
                    "Comment (optional): write your own line \"Comment: ...\"."
            }
            val startISO = if (allDay) startDate else "$startDate T$startTime"
            val endISO = if (allDay) endDate else "$endDate T$endTime"
            val dateLabel = AppointmentFormat.dateLabel(startDate, startTime, endDate, endTime, allDay, isGerman)
            val subject = "📅 $title"
            val groupId = PollFormat.newPollId()
            val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                // Prepare (build + encrypt) every recipient FIRST, so an encryption failure is
                // detected before anything is sent (a retry then resends cleanly, no duplicates).
                data class Prepared(val recipient: String, val apptId: String, val body: String, val enc: com.mailchat.crypto.EncryptionHelper.Result)
                val prepared = mutableListOf<Prepared>()
                for (recipient in recipients) {
                    val apptId = PollFormat.newPollId()
                    val body = AppointmentFormat.buildApptBody(
                        title, question, dateLabel, location, options, instruction,
                        apptId, startISO, endISO, allDay
                    )
                    val enc = com.mailchat.crypto.EncryptionHelper.encryptForRecipients(
                        appContext, body, listOf(recipient.trim().lowercase()), isGroupChat, chat.isEncrypted, forceUnencrypted, subject
                    )
                    prepared.add(Prepared(recipient, apptId, body, enc))
                }
                // Send one appointment email per recipient and register the per-recipient poll.
                var firstBody = ""
                for (p in prepared) {
                    if (firstBody.isEmpty()) firstBody = p.body
                    val extraHeaders = mutableMapOf("X-MailChat-Appt" to p.apptId)
                    extraHeaders.putAll(p.enc.headers)
                    // PLAIN (text/plain): the appointment format is line-based and must stay plain.
                    val sentMessageId = smtpService.sendEmail(
                        account = account,
                        to = p.recipient,
                        subject = p.enc.subject ?: subject,
                        body = p.enc.body,
                        extraHeaders = extraHeaders,
                        bodyFormat = com.mailchat.network.BodyFormat.PLAIN
                    )
                    val poll = Poll(
                        id = p.apptId,
                        accountId = account.id,
                        chatId = chat.id,
                        groupId = groupId,
                        recipientEmail = p.recipient.trim().lowercase(),
                        title = title,
                        question = question,
                        options = PollFormat.optionsToJson(options),
                        multi = false,
                        numberMode = false,
                        personMode = false,
                        appointmentMode = true,
                        startDate = startDate,
                        startTime = startTime,
                        endDate = endDate,
                        endTime = endTime,
                        allDay = allDay,
                        location = location,
                        creatorEmail = account.emailAddress,
                        pollMessageId = sentMessageId,
                        status = Poll.STATUS_OPEN,
                        createdAt = System.currentTimeMillis()
                    )
                    pollRepository.insertPoll(poll)
                    // One local message per recipient (real Message-ID → deduped when fetched back).
                    val message = Message(
                        chatId = chat.id,
                        accountId = account.id,
                        from = account.emailAddress,
                        subject = subject,
                        body = p.body,
                        bodyFull = p.body,
                        timestamp = System.currentTimeMillis(),
                        isRead = true,
                        isIncoming = false,
                        messageId = sentMessageId,
                        isEncrypted = p.enc.isEncrypted
                    )
                    val messageId = messageRepository.insertMessage(message)
                    messageRepository.updateMessage(message.copy(id = messageId, pollId = groupId))
                }
                // The creator can also answer: a dedicated per-recipient poll-ID for the creator
                // (NOT sent as an email — it only exists so the creator's answer is tracked).
                pollRepository.insertPoll(
                    Poll(
                        id = PollFormat.newPollId(),
                        accountId = account.id,
                        chatId = chat.id,
                        groupId = groupId,
                        recipientEmail = account.emailAddress.trim().lowercase(),
                        title = title,
                        question = question,
                        options = PollFormat.optionsToJson(options),
                        multi = false,
                        numberMode = false,
                        personMode = false,
                        appointmentMode = true,
                        startDate = startDate,
                        startTime = startTime,
                        endDate = endDate,
                        endTime = endTime,
                        allDay = allDay,
                        location = location,
                        creatorEmail = account.emailAddress,
                        pollMessageId = "",
                        status = Poll.STATUS_OPEN,
                        createdAt = System.currentTimeMillis()
                    )
                )
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = firstBody,
                        lastMessageSubject = subject
                    )
                )
                _uiState.update { it.copy(isSending = false) }
            } catch (e: com.mailchat.crypto.EncryptionHelper.EncryptionFailed) {
                encryptionErrorRetry = { createAppointment(title, question, startDate, startTime, endDate, endTime, allDay, location, targetChatId, forceUnencrypted = true) }
                _uiState.update { it.copy(isSending = false, encryptionErrorDialog = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message) }
            }
        }
    }

    /**
     * Answers a TERMIN (appointment) poll: sends the choice (Ja/Nein/Vielleicht) + optional comment
     * as a reply to the chat. Mirrors [answerPoll] but with the appointment answer marker.
     */
    fun answerAppointment(
        pollId: String,
        choice: Int,
        comment: String,
        forceUnencrypted: Boolean = false
    ) {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val account = senderAccountFor(chat) ?: return@launch
            // Resolve the actual poll-ID (group → the voter's own per-recipient poll-ID).
            val groupPolls = pollRepository.getPollsByGroupSync(pollId, account.id)
            val poll = if (groupPolls.isNotEmpty()) {
                val voterEmail = account.emailAddress.trim().lowercase()
                groupPolls.firstOrNull { it.recipientEmail.trim().lowercase() == voterEmail }
                    ?: groupPolls.first()
            } else {
                pollRepository.getPoll(pollId, account.id) ?: return@launch
            }
            val actualPollId = if (poll.groupId.isNotBlank()) {
                poll.id
            } else {
                val creatorAccount = accountRepository.getAllAccounts().first()
                    .firstOrNull { it.emailAddress.trim().lowercase() == poll.creatorEmail.trim().lowercase() }
                if (creatorAccount != null) {
                    pollRepository.getPollsByChatSync(chat.id, creatorAccount.id)
                        .firstOrNull { it.recipientEmail.trim().lowercase() == poll.recipientEmail.trim().lowercase() }
                        ?.id ?: poll.id
                } else {
                    poll.id
                }
            }
            val options = PollFormat.optionsFromJson(poll.options)
            val isGerman = SettingsUtils.isGermanUi(appContext)
            val noReply = if (isGerman) "⚠️ Nicht antworten!" else "⚠️ Do not reply!"
            val commentLabel = if (isGerman) "Kommentar" else "Comment"
            val body = AppointmentFormat.buildApptAnswerBody(options, choice, comment, actualPollId, noReply, commentLabel)
            val subject = if (EmailGrouping.isGroupKey(chat.participantEmail)) {
                chat.participantName
            } else {
                "Re: ${poll.title}"
            }
            // The creator answers for themselves; everyone else answers to the chat's other party.
            val isCreator = account.emailAddress.trim().lowercase() == poll.creatorEmail.trim().lowercase()
            val recipient = if (isCreator) account.emailAddress else recipientForChat(chat, account)
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val recipients = recipient.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
                val enc = com.mailchat.crypto.EncryptionHelper.encryptForRecipients(
                    appContext, body, recipients, isGroupChat, chat.isEncrypted, forceUnencrypted, subject
                )
                val extraHeaders = mutableMapOf("X-MailChat-Appt-Answer" to actualPollId)
                extraHeaders.putAll(enc.headers)
                val sentMessageId = smtpService.sendEmail(
                    account = account,
                    to = recipient,
                    subject = enc.subject ?: subject,
                    body = enc.body,
                    extraHeaders = extraHeaders,
                    bodyFormat = com.mailchat.network.BodyFormat.PLAIN
                )
                val message = Message(
                    chatId = chat.id,
                    accountId = account.id,
                    from = account.emailAddress,
                    subject = subject,
                    body = body,
                    bodyFull = body,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    isIncoming = false,
                    messageId = sentMessageId,
                    isEncrypted = enc.isEncrypted
                )
                val messageId = messageRepository.insertMessage(message)
                pollProcessor.processMessageForPolls(message.copy(id = messageId), chat)
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = body,
                        lastMessageSubject = subject
                    )
                )
                _uiState.update { it.copy(isSending = false) }
            } catch (e: com.mailchat.crypto.EncryptionHelper.EncryptionFailed) {
                encryptionErrorRetry = { answerAppointment(pollId, choice, comment, forceUnencrypted = true) }
                _uiState.update { it.copy(isSending = false, encryptionErrorDialog = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message) }
            }
        }
    }

    /**
     * Shares the appointment as an ICS file (via the Android share sheet) so the user can add it
     * to their own calendar ("In Kalender übernehmen").
     */
    fun shareAppointmentToCalendar(pollId: String) {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val account = senderAccountFor(chat) ?: return@launch
            val groupPolls = pollRepository.getPollsByGroupSync(pollId, account.id)
            val poll = if (groupPolls.isNotEmpty()) {
                groupPolls.first()
            } else {
                pollRepository.getPoll(pollId, account.id) ?: return@launch
            }
            val isGerman = SettingsUtils.isGermanUi(appContext)
            val dateLabel = AppointmentFormat.dateLabel(
                poll.startDate, poll.startTime, poll.endDate, poll.endTime, poll.allDay, isGerman
            )
            val description = buildString {
                if (poll.question.isNotBlank()) append(poll.question).append('\n')
                append("🗓 ").append(dateLabel)
                if (poll.location.isNotBlank()) append("\n📍 ").append(poll.location)
            }
            try {
                // Use ACTION_INSERT (like the AI "add to my calendar" flow) instead of ACTION_SEND
                // with an ICS file: the system calendar registers for ACTION_INSERT, so the
                // calendar app is actually offered in the chooser.
                val calendarIntent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                    data = android.net.Uri.parse("content://com.android.calendar/events")
                    putExtra("title", poll.title)
                    putExtra("description", description)
                    // The official extra is "eventLocation" (CalendarContract.Events.EVENT_LOCATION);
                    // "location" is set too for third-party calendar apps that read it.
                    if (poll.location.isNotBlank()) {
                        putExtra("eventLocation", poll.location)
                        putExtra("location", poll.location)
                    }
                    // appContext is the Application context (not an Activity), so starting an
                    // activity from it requires FLAG_ACTIVITY_NEW_TASK.
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (poll.allDay) {
                        // All-day event: beginTime/endTime are UTC-midnight timestamps; the end is
                        // exclusive (the day after the last day).
                        putExtra("allDay", true)
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            val start = sdf.parse(poll.startDate)
                            val end = sdf.parse(poll.endDate.ifBlank { poll.startDate })
                            if (start != null && end != null) {
                                putExtra("beginTime", start.time)
                                putExtra("endTime", end.time + 24L * 60L * 60L * 1000L)
                            }
                        } catch (_: Exception) { }
                    } else if (poll.startDate.isNotBlank()) {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            val beginTime = sdf.parse("${poll.startDate} ${poll.startTime.ifBlank { "00:00" }}")
                            if (beginTime != null) {
                                putExtra("beginTime", beginTime.time)
                                val endTime = if (poll.endDate.isNotBlank() && poll.endTime.isNotBlank()) {
                                    sdf.parse("${poll.endDate} ${poll.endTime}")
                                } else {
                                    null
                                }
                                putExtra("endTime", (endTime ?: beginTime).time)
                            }
                        } catch (_: Exception) { }
                    }
                }
                val chooser = android.content.Intent.createChooser(
                    calendarIntent, appContext.getString(com.mailchat.R.string.calendar_share_chooser)
                )
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(chooser)
            } catch (e: Exception) {
                _uiState.update { it.copy(sendError = e.message) }
            }
        }
    }

    /**
     * Lists the chats (conversations) of the current account, for the recipient/group picker when
     * starting an appointment poll from an AI-detected appointment.
     */
    suspend fun chatsForPicker(): List<com.mailchat.data.model.Chat> {
        val accountId = _uiState.value.chat?.accountId ?: return emptyList()
        val chats = chatRepository.getChatsByAccountSync(accountId)
        val chatEmails = chats.map { it.participantEmail.lowercase() }.toSet()
        // Add contacts that are not already in the chats (so the user can start an appointment
        // poll with any contact, not only the ones they're currently corresponding with).
        val contactChats = contactService.getAllContacts()
            .filter { it.email.lowercase() !in chatEmails }
            .map { contact ->
                com.mailchat.data.model.Chat(
                    id = 0,
                    participantEmail = contact.email,
                    participantName = contact.name,
                    accountId = accountId
                )
            }
        return chats + contactChats
    }

    /**
     * Answers a poll: sends the selected options (checkbox poll) or the entered numbers (numeric
     * poll) as a reply to the chat.
     */
    fun answerPoll(
        pollId: String,
        selectedIndices: List<Int>,
        values: List<Double> = emptyList(),
        forceUnencrypted: Boolean = false
    ) {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val account = senderAccountFor(chat) ?: return@launch
            // Resolve the actual poll-ID: when [pollId] is a group (the creator's logical poll,
            // shown in the chat), use the voter's own per-recipient poll-ID (the one whose
            // recipientEmail matches the voter). Without this the creator could not vote, because
            // the chat message carries the groupId, not a real poll-ID.
            val groupPolls = pollRepository.getPollsByGroupSync(pollId, account.id)
            val poll = if (groupPolls.isNotEmpty()) {
                val voterEmail = account.emailAddress.trim().lowercase()
                groupPolls.firstOrNull { it.recipientEmail.trim().lowercase() == voterEmail }
                    ?: groupPolls.first()
            } else {
                pollRepository.getPoll(pollId, account.id) ?: return@launch
            }
            // For recipient-side single polls (groupId empty), use the creator's per-recipient
            // poll ID so the answer email references a poll ID that exists in the creator's
            // account. This allows the creator's device to find the poll and record the vote
            // when the answer email is synced.
            val actualPollId = if (poll.groupId.isNotBlank()) {
                poll.id
            } else {
                val creatorAccount = accountRepository.getAllAccounts().first()
                    .firstOrNull { it.emailAddress.trim().lowercase() == poll.creatorEmail.trim().lowercase() }
                if (creatorAccount != null) {
                    pollRepository.getPollsByChatSync(chat.id, creatorAccount.id)
                        .firstOrNull { it.recipientEmail.trim().lowercase() == poll.recipientEmail.trim().lowercase() }
                        ?.id ?: poll.id
                } else {
                    poll.id
                }
            }
            val options = PollFormat.optionsFromJson(poll.options)
            // "Do not reply" hint in the answer email (for foreign email systems, so a reader of
            // this individual answer doesn't reply to it).
            val noReply = if (SettingsUtils.isGermanUi(appContext)) "⚠️ Nicht antworten!" else "⚠️ Do not reply!"
            val body = if (poll.numberMode) {
                PollFormat.buildNumberAnswerBody(options, values, actualPollId, noReply)
            } else {
                PollFormat.buildAnswerBody(options, selectedIndices, actualPollId, noReply)
            }
            val subject = if (EmailGrouping.isGroupKey(chat.participantEmail)) {
                chat.participantName
            } else {
                "Re: ${poll.title}"
            }
            // The creator votes for themselves: the answer must arrive in their own account (so
            // the vote is recorded there). Everyone else answers to the chat's other party.
            val isCreator = account.emailAddress.trim().lowercase() == poll.creatorEmail.trim().lowercase()
            val recipient = if (isCreator) account.emailAddress else recipientForChat(chat, account)
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                // Encrypt the answer like a regular message (OpenPGP / legacy fallback).
                val recipients = recipient.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                val isGroupChat = EmailGrouping.isGroupKey(chat.participantEmail)
                val enc = com.mailchat.crypto.EncryptionHelper.encryptForRecipients(
                    appContext, body, recipients, isGroupChat, chat.isEncrypted, forceUnencrypted, subject
                )
                val extraHeaders = mutableMapOf("X-MailChat-Poll-Answer" to actualPollId)
                extraHeaders.putAll(enc.headers)
                // PLAIN (text/plain): the answer format is line-based and must stay plain text.
                val sentMessageId = smtpService.sendEmail(
                    account = account,
                    to = recipient,
                    subject = enc.subject ?: subject,
                    body = enc.body,
                    extraHeaders = extraHeaders,
                    bodyFormat = com.mailchat.network.BodyFormat.PLAIN
                )
                // The local copy keeps the PLAINTEXT body (so the vote can be parsed);
                // isEncrypted drives the encryption symbol in the chat.
                val message = Message(
                    chatId = chat.id,
                    accountId = account.id,
                    from = account.emailAddress,
                    subject = subject,
                    body = body,
                    bodyFull = body,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    isIncoming = false,
                    messageId = sentMessageId,
                    isEncrypted = enc.isEncrypted
                )
                val messageId = messageRepository.insertMessage(message)
                pollProcessor.processMessageForPolls(message.copy(id = messageId), chat)
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = body,
                        lastMessageSubject = subject
                    )
                )
                _uiState.update { it.copy(isSending = false) }
            } catch (e: com.mailchat.crypto.EncryptionHelper.EncryptionFailed) {
                encryptionErrorRetry = { answerPoll(pollId, selectedIndices, values, forceUnencrypted = true) }
                _uiState.update { it.copy(isSending = false, encryptionErrorDialog = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message) }
            }
        }
    }

    /** Closes an open poll (by the creator) and sends the result email(s). */
    fun closePoll(pollId: String) {
        viewModelScope.launch {
            val chat = _uiState.value.chat ?: return@launch
            val accountId = chat.accountId
            val groupPolls = pollRepository.getPollsByGroupSync(pollId, accountId)
            if (groupPolls.isNotEmpty()) {
                // Group (creator's logical poll): close all per-recipient polls + send result emails.
                pollProcessor.closePollGroup(groupPolls, chat)
            } else {
                // Single poll: current behavior.
                val poll = pollRepository.getPoll(pollId, accountId) ?: return@launch
                pollProcessor.closePollByCreator(poll, chat)
            }
        }
    }

    /** The recipient emails for the current chat (1:1 or group, excluding the sender). */
    private fun recipientEmailsForChat(chat: Chat, account: com.mailchat.data.model.EmailAccount): List<String> {
        return if (EmailGrouping.isGroupKey(chat.participantEmail)) {
            chat.participantEmail.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != EmailGrouping.normalize(account.emailAddress) }
        } else {
            listOf(chat.participantEmail.trim()).filter { it.isNotBlank() }
        }
    }

    /** The recipient string for the current chat (1:1 or group, excluding the sender). */
    private fun recipientForChat(chat: Chat, account: com.mailchat.data.model.EmailAccount): String {
        return if (EmailGrouping.isGroupKey(chat.participantEmail)) {
            chat.participantEmail.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != EmailGrouping.normalize(account.emailAddress) }
                .joinToString(",")
                .ifBlank { chat.participantEmail }
        } else {
            chat.participantEmail
        }
    }

    fun dismissEncryptionErrorDialog() {
        _uiState.update { it.copy(encryptionErrorDialog = null) }
    }

    /** "Ja" in the encryption error dialog: resend the last attempt unencrypted (send or forward). */
    fun retryEncryptionErrorUnencrypted() {
        encryptionErrorRetry?.invoke()
    }

    private fun forwardToUnencryptedRetry() {
        lastForwardParams?.let { p ->
            forwardTo(p.recipient, p.message, p.customText, p.contactName, forceUnencrypted = true)
        }
    }

    fun refetchEmptyMessages() {
        viewModelScope.launch {
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            val account = accountRepository.getAccountById(chat.accountId) ?: return@launch
            val emptyMessages = state.messages.filter { it.body.isBlank() && it.bodyFull.isBlank() }
            if (emptyMessages.isEmpty()) return@launch
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val periodStart = emptyMessages.minOf { it.timestamp } - 1000L
                val incoming = imapService.fetchNewMessages(account, periodStart)
                for (fetchedMsg in incoming.messages) {
                    if (fetchedMsg.message.body.isNotBlank()) {
                        val existing = state.messages.find {
                            it.messageId == fetchedMsg.message.messageId
                        }
                        if (existing != null) {
                            messageRepository.updateMessage(
                                existing.copy(
                                    body = fetchedMsg.message.body,
                                    bodyFull = fetchedMsg.message.bodyFull
                                )
                            )
                        }
                    }
                }
                val updatedMessages = messageRepository.getMessagesByChatSync(chat.id)
                _uiState.update { it.copy(messages = updatedMessages, isSending = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = "Re-fetch: ${e.message}") }
            }
        }
    }

    fun summarizeAllMessages() {
        aiProcessingJob = viewModelScope.launch {
            val state = _uiState.value
            val chat = state.chat ?: return@launch
            _uiState.update { it.copy(isAiProcessing = true, sendError = null) }
            var summarizedCount = 0
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!com.mailchat.ai.GeminiService.isEngineReady()) {
                    throw IllegalStateException("No model")
                }
                val allMessages = state.messages.filter { it.body.isNotBlank() || it.bodyFull.isNotBlank() }
                android.util.Log.d("MailchatAI", "summarizeAllMessages: ${allMessages.size} messages with body")
                for (message in allMessages) {
                    if (coroutineContext[kotlinx.coroutines.Job]?.isActive == false) return@launch
                    try {
                        val originalFull = message.bodyFull.ifBlank { message.body }
                        if (originalFull.isBlank()) continue
                        _uiState.update { it.copy(currentSummarizingMessageId = message.id) }
                        val updatedMsg = com.mailchat.util.AiMessageProcessor.processMessage(appContext, message)
                        messageRepository.updateMessage(updatedMsg)
                        val updated = messageRepository.getMessagesByChatSync(chat.id)
                        _uiState.update { it.copy(messages = updated) }
                        if (updatedMsg.summary.isNotBlank() && updatedMsg.summary != message.summary) summarizedCount++
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("MailchatAI", "Summarize failed for msg ${message.id}: ${e.message}")
                    }
                }
                _uiState.update { it.copy(isAiProcessing = false, currentSummarizingMessageId = null) }
                if (summarizedCount > 0) {
                    android.widget.Toast.makeText(
                        appContext,
                        appContext.getString(com.mailchat.R.string.ai_summary_result, summarizedCount),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(appContext, appContext.getString(com.mailchat.R.string.ai_summary_none), android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stopped by the user — no error message.
                throw e
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(isAiProcessing = false, sendError = null) }
                android.widget.Toast.makeText(
                    appContext,
                    appContext.getString(com.mailchat.R.string.assistant_model_missing),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("MailchatAI", "summarizeAllMessages error: ${e.message}")
                _uiState.update { it.copy(isAiProcessing = false, sendError = "KI: ${e.message}") }
                android.widget.Toast.makeText(
                    appContext,
                    appContext.getString(com.mailchat.R.string.ai_summary_error, e.message ?: ""),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}