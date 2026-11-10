package com.mailchat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.Chat
import com.mailchat.data.model.Message
import com.mailchat.data.repository.AccountRepository
import com.mailchat.data.repository.ChatRepository
import com.mailchat.data.repository.MessageRepository
import com.mailchat.network.ImapService
import com.mailchat.network.SmtpService
import com.mailchat.service.ContactService
import com.mailchat.service.Contact
import com.mailchat.util.EmailGrouping
import com.mailchat.util.normalizeWhitespace
import com.mailchat.util.stripHtml
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    val draftSubject: String? = null,
    val selectedRecipient: String? = null,
    val pendingQuote: String? = null,
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
            }
        }
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

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            try {
                val chat = _uiState.value.chat
                val account = chat?.let { accountRepository.getAccountById(it.accountId) }
                if (account != null && message.messageId.isNotBlank()) {
                    try {
                        imapService.deleteMessagesFromServer(
                            account = account,
                            messageIds = listOf(message.messageId),
                            isIncoming = message.isIncoming
                        )
                    } catch (_: Exception) {}
                }
                messageRepository.deleteAttachmentsByMessage(message.id)
                messageRepository.deleteMessage(message)
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
                if (account != null) {
                    try {
                        imapService.deleteMessagesFromServer(
                            account = account,
                            messageIds = messages.filter { it.messageId.isNotBlank() }.map { it.messageId },
                            isIncoming = messages.firstOrNull()?.isIncoming ?: true
                        )
                    } catch (_: Exception) {}
                }
                for (msg in messages) {
                    messageRepository.deleteAttachmentsByMessage(msg.id)
                    messageRepository.deleteMessage(msg)
                }
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
        _uiState.update {
            it.copy(
                pendingQuote = "— $dateStr, ${message.from}:\n$quoted",
                selectedRecipient = null
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
        _uiState.update {
            it.copy(
                pendingQuote = "— $dateStr, ${message.from}:\n$quoted",
                selectedRecipient = if (message.isIncoming) message.from else null
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
        _uiState.update { it.copy(quoteEnabled = !it.quoteEnabled) }
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
                    try {
                        com.mailchat.ai.GeminiService.suggestReply(lastMessages, prevWords, language, subject, senderName, recipientName)
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
        if (quotedSource.isBlank()) {
            _uiState.update { it.copy(pendingQuote = null) }
            return
        }
        val dateStr = java.text.SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(message.timestamp))
        val quote = quotedSource.lineSequence().joinToString("\n") { "> $it" }
        _uiState.update {
            it.copy(
                pendingQuote = "— $dateStr, ${message.from}:\n$quote",
                selectedRecipient = if (message.isIncoming) message.from else null,
                replyTargetMessageId = message.id
            )
        }
    }

    fun replyAll(message: Message) {
        val quotedSource = message.bodyFull.ifBlank { message.body }
            .stripHtml()
            .normalizeWhitespace()
            .take(1500)
        if (quotedSource.isBlank()) return
        val dateStr = java.text.SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(message.timestamp))
        val quote = quotedSource.lineSequence().joinToString("\n") { "> $it" }
        val ownEmail = _uiState.value.accountEmail
        val ccList = message.cc.split(";").filter { it.isNotBlank() && !it.equals(ownEmail, ignoreCase = true) }
        _uiState.update {
            it.copy(
                pendingQuote = "— $dateStr, ${message.from}:\n$quote",
                selectedRecipient = if (message.isIncoming) message.from else null,
                composeCc = ccList.joinToString(";"),
                replyTargetMessageId = message.id
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

    fun clearPendingQuote() {
        _uiState.update { it.copy(pendingQuote = null) }
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
            val forwardBody = buildString {
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
            val signature = com.mailchat.util.SettingsUtils.getSignature(appContext)
            val body = if (signature.isNotBlank()) "$forwardBody\n\n$signature" else forwardBody
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val originalAttachments = messageRepository.getAttachmentsByMessageSync(message.id)
                    .map { java.io.File(it.uri) }
                    .filter { it.exists() }
                var forwardAttachments = originalAttachments
                var finalBody = body
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
                    subject = fwdSubject,
                    body = finalBody,
                    attachments = forwardAttachments,
                    extraHeaders = extraHeaders
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
                !baseSubject.startsWith("Aw:", ignoreCase = true)
            ) "Re: $baseSubject" else baseSubject
            val extraHeaders = mutableMapOf<String, String>()
            if (latestIncoming != null && latestIncoming.messageId.isNotBlank()) {
                extraHeaders["In-Reply-To"] = latestIncoming.messageId
                extraHeaders["References"] = latestIncoming.messageId
            }
            val pendingQuote = _uiState.value.pendingQuote
            val bodyWithQuote = if (pendingQuote != null && rawBody.isNotBlank()) {
                "$rawBody\n\n$pendingQuote"
            } else if (_uiState.value.quoteEnabled && rawBody.isNotBlank() && latestIncoming != null) {
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
                    "$rawBody\n\n— $dateStr, ${latestIncoming.from}:\n$quote"
                } else rawBody
            } else rawBody
            val signature = com.mailchat.util.SettingsUtils.getSignature(appContext)
            // Group chats get an additional notice so recipients know to reply to all.
            val groupNotice = if (isGroupChat) "\n\n" + appContext.getString(com.mailchat.R.string.group_chat_notice) else ""
            val body = when {
                signature.isNotBlank() -> "$bodyWithQuote\n\n$signature$groupNotice"
                groupNotice.isNotBlank() -> "$bodyWithQuote$groupNotice"
                else -> bodyWithQuote
            }
            if (recipient.isBlank() || (rawBody.isBlank() && state.composeAttachments.isEmpty())) {
                return@launch
            }
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                val recipients = recipient.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                val firstRecipient = recipients.firstOrNull() ?: ""
                var finalBody = body
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
                    subject = replySubject,
                    body = finalBody,
                    attachments = finalAttachments,
                    extraHeaders = extraHeaders,
                    cc = _uiState.value.composeCc,
                    bcc = _uiState.value.composeBcc
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
                        pendingQuote = null,
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