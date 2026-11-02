package com.mailchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.Chat
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.model.Message
import com.mailchat.data.repository.AccountRepository
import com.mailchat.data.repository.ChatRepository
import com.mailchat.data.repository.MessageRepository
import com.mailchat.network.ImapConnectionException
import com.mailchat.network.ImapService
import com.mailchat.network.SmtpService
import com.mailchat.service.Contact
import com.mailchat.service.ContactService
import com.mailchat.util.EmailGrouping
import com.mailchat.util.NotificationHelper
import com.mailchat.util.SettingsUtils
import com.mailchat.util.sanitizeFileName
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

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val hiddenChats: List<Chat> = emptyList(),
    val importantChats: List<Chat> = emptyList(),
    val accounts: List<EmailAccount> = emptyList(),
    val isSyncing: Boolean = false,
    val error: String? = null,
    val selectionMode: Boolean = false,
    val selectedChatIds: Set<Long> = emptySet(),
    val contacts: List<Contact> = emptyList(),
    val isSendingNewEmail: Boolean = false,
    val newEmailError: String? = null,
    val encryptionErrorDialog: String? = null,
    val messageCounts: Map<Long, Int> = emptyMap(),
    val todoCounts: Map<Long, Int> = emptyMap(),
    val processedCounts: Map<Long, Int> = emptyMap(),
    val contactPhotos: Map<String, String?> = emptyMap(),
    val contactNames: Map<String, String> = emptyMap(),
    val aiSummarizedChatIds: Set<Long> = emptySet(),
    val isAiProcessing: Boolean = false,
    val spamCategories: Map<Long, String> = emptyMap(),
    val spamProbabilities: Map<Long, Int> = emptyMap(),
    val chatCustomTags: Map<Long, String> = emptyMap()
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val accountRepository: AccountRepository,
    private val messageRepository: MessageRepository,
    private val imapService: ImapService,
    private val smtpService: SmtpService,
    private val contactService: ContactService,
    private val syncStateDao: com.mailchat.data.dao.SyncStateDao,
    private val autoReplyService: com.mailchat.service.AutoReplyService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private data class NewEmailParams(
        val to: String,
        val subject: String,
        val body: String,
        val groupName: String,
        val accountId: Long,
        val attachments: List<File> = emptyList(),
        val cc: String = "",
        val bcc: String = ""
    )

    private var lastNewEmailParams: NewEmailParams? = null

    // Job of the running AI batch processing (so the "AI in use" banner can stop it).
    private var aiProcessingJob: kotlinx.coroutines.Job? = null

    /** Stops the running AI batch processing (if any) and hides the "AI in use" banner. */
    fun stopAiProcessing() {
        aiProcessingJob?.cancel()
        aiProcessingJob = null
        _uiState.update { it.copy(isAiProcessing = false) }
    }

    init {
        viewModelScope.launch { repairAttachmentPaths() }
        viewModelScope.launch { cleanupNoReplyNamePrefix() }
        viewModelScope.launch { repairChatPreviews() }
        viewModelScope.launch {
            accountRepository.getAllAccounts().collectLatest { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
        viewModelScope.launch {
            chatRepository.getAllChatsWithCount().collectLatest { items ->
                val chats = items.map { it.chat }
                _uiState.update {
                    it.copy(
                        chats = chats,
                        messageCounts = items.associate { it.chat.id to it.messageCount },
                        todoCounts = items.filter { i -> i.todoCount > 0 }.associate { it.chat.id to it.todoCount },
                        processedCounts = items.filter { i -> i.processedCount > 0 }.associate { it.chat.id to it.processedCount }
                    )
                }
                val photos = contactService.getPhotoUrisByEmails(chats.map { it.participantEmail }).toMutableMap()
                if (chats.any { it.participantEmail == "florescu@web.de" }) {
                    photos["florescu@web.de"] = "android.resource://${appContext.packageName}/drawable/ic_launcher_foreground"
                }
                val names = contactService.getNamesByEmails(chats.map { it.participantEmail })
                _uiState.update { it.copy(contactPhotos = photos, contactNames = names) }
            }
        }
        viewModelScope.launch {
            chatRepository.getHiddenChats().collectLatest { hidden ->
                _uiState.update { it.copy(hiddenChats = hidden) }
            }
        }
        viewModelScope.launch {
            chatRepository.getImportantChats().collectLatest { important ->
                _uiState.update { it.copy(importantChats = important) }
            }
        }
        viewModelScope.launch {
            messageRepository.getSpamCategories().collectLatest { cats ->
                _uiState.update { it.copy(spamCategories = cats) }
            }
        }
        viewModelScope.launch {
            messageRepository.getCustomTags().collectLatest { tags ->
                _uiState.update { it.copy(chatCustomTags = tags) }
            }
        }
        com.mailchat.service.SyncWorker.schedule(
            appContext,
            SettingsUtils.getSyncIntervalMinutes(appContext)
        )
    }



    fun loadAiSummaries() {
        viewModelScope.launch {
            val summarizedIds = messageRepository.getChatIdsWithSummary().toSet()
            android.util.Log.d("MailchatAI", "loadAiSummaries: ${summarizedIds.size} chats with summaries")
            _uiState.update { it.copy(aiSummarizedChatIds = summarizedIds) }
        }
    }

    fun processAllWithAi() {
        if (!SettingsUtils.getAiEnabled(appContext)) {
            _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_not_enabled)) }
            return
        }
        aiProcessingJob = viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            var processed = 0
            var failed = 0
            var firstError = ""
            try {
                val initOk = com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!initOk) {
                    val state = com.mailchat.ai.GeminiService.downloadState.value
                    _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_init_failed, state.toString())) }
                    _uiState.update { it.copy(isAiProcessing = false) }
                    return@launch
                }
                val chats = chatRepository.getAllChats().first()
                for (chat in chats) {
                    if (coroutineContext[kotlinx.coroutines.Job]?.isActive == false) return@launch
                    val messages = messageRepository.getMessagesByChatSync(chat.id)
                    for (message in messages) {
                        if (message.bodyFull.isBlank()) continue
                        try {
                            messageRepository.updateMessage(
                                com.mailchat.util.AiMessageProcessor.processMessage(appContext, message)
                            )
                            processed++
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed++
                            if (firstError.isBlank()) firstError = e.message ?: "unknown"
                        }
                    }
                }
                loadAiSummaries()
                val msg = if (failed > 0) appContext.getString(com.mailchat.R.string.ai_batch_result, processed, failed, firstError)
                else appContext.getString(com.mailchat.R.string.ai_batch_done, processed)
                _uiState.update { it.copy(error = msg) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stopped by the user — no error message.
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_summary_error, e.message ?: "")) }
            }
            _uiState.update { it.copy(isAiProcessing = false) }
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            val accounts = accountRepository.getAllAccounts().first()
            val periodDays = SettingsUtils.getSyncPeriodDays(appContext)
            val periodStart = System.currentTimeMillis() - periodDays * 24L * 60L * 60L * 1000L
            val syncErrors = mutableListOf<String>()
            for (account in accounts) {
                try {
                    val since = periodStart
                    val incoming = imapService.fetchNewMessages(account, since)
                    syncErrors.addAll(incoming.errors.map { "${account.emailAddress} $it" })
                    for (fetchedMsg in incoming.messages) {
                        processFetchedMessage(account, fetchedMsg)
                    }
                    val sent = imapService.fetchSentMessages(account, since)
                    syncErrors.addAll(sent.errors.map { "${account.emailAddress} $it" })
                    for (fetchedMsg in sent.messages) {
                        processFetchedMessage(account, fetchedMsg)
                    }
                    accountRepository.updateAccount(
                        account.copy(lastSyncTimestamp = System.currentTimeMillis())
                    )
                } catch (e: ImapConnectionException) {
                    syncErrors.add("${account.emailAddress}: ${e.message}")
                }
            }
            _uiState.update { state ->
                state.copy(
                    isSyncing = false,
                    error = if (syncErrors.isEmpty()) null
                    else syncErrors.take(2).joinToString("; ").take(300)
                )
            }
        }
    }

    private suspend fun processFetchedMessage(
        account: EmailAccount,
        fetchedMsg: com.mailchat.network.FetchedMessage
    ) {
        val msgId = fetchedMsg.message.messageId
        if (msgId.isNotBlank()) {
            if (messageRepository.countByMessageId(msgId) > 0) return
            if (SettingsUtils.getDeletedMessageIds(appContext).contains(msgId)) return
        }
        val chat = getOrCreateChat(fetchedMsg.participantEmail, account, fetchedMsg)
        if (msgId.isBlank()) {
            val dupCount = messageRepository.countByFromSubjectTimestamp(
                chat.id,
                fetchedMsg.message.from,
                fetchedMsg.message.subject,
                fetchedMsg.message.timestamp
            )
            if (dupCount > 0) return
        }
        var msgBody = fetchedMsg.message.body
        var msgBodyFull = fetchedMsg.message.bodyFull
        var isEncryptedMsg = false
        val sourceBody = msgBodyFull.ifBlank { msgBody }
        if (fetchedMsg.isIncoming &&
            com.mailchat.crypto.OpenPgpKeyService.isEncryptedContent(sourceBody) &&
            com.mailchat.crypto.OpenPgpKeyService.hasIdentity(appContext)
        ) {
            isEncryptedMsg = true
            try {
                val decrypted = withContext(Dispatchers.IO) {
                    com.mailchat.crypto.OpenPgpKeyService.decrypt(appContext, sourceBody)
                }
                msgBody = decrypted
                msgBodyFull = decrypted
            } catch (_: Exception) {
            }
        } else if (fetchedMsg.isIncoming && sourceBody.startsWith("MAILCHAT_ENC:")) {
            isEncryptedMsg = true
            try {
                val encryptedPart = sourceBody.removePrefix("MAILCHAT_ENC:")
                val decrypted = com.mailchat.crypto.CryptoService.decryptFromSender(encryptedPart, appContext, "")
                msgBody = decrypted
                msgBodyFull = decrypted
            } catch (_: Exception) {
            }
        }
        val message = fetchedMsg.message.copy(
            chatId = chat.id,
            accountId = account.id,
            body = msgBody,
            bodyFull = msgBodyFull,
            isEncrypted = isEncryptedMsg
        )
        val dbMessageId = messageRepository.insertMessage(message)
        for (attachment in fetchedMsg.attachments) {
            messageRepository.insertAttachment(
                attachment.copy(messageId = dbMessageId)
            )
        }
        if (fetchedMsg.isIncoming) {
            NotificationHelper.postNewEmail(
                context = appContext,
                chatId = chat.id,
                sender = fetchedMsg.message.from,
                subject = fetchedMsg.message.subject,
                preview = fetchedMsg.message.body.take(200)
            )
        }
        if (SettingsUtils.getAiEnabled(appContext) && SettingsUtils.getAiAutoProcess(appContext) && fetchedMsg.isIncoming) {
            processMessageWithAi(message.copy(id = dbMessageId))
        }
        // Automatic "Wo bist Du?" reply: if this contact has the auto-location-reply enabled and
        // the message asks "Wo bist Du?", reply with the current location (+ optional weather).
        if (fetchedMsg.isIncoming) {
            autoReplyService.maybeAutoReply(account, message.copy(id = dbMessageId))
        }
        // Decrypted copy in the inbox: replace the encrypted incoming message on the server
        // with its decrypted version (same Message-ID, date and headers). Background + retry;
        // no-op if the server copy is no longer encrypted.
        if (fetchedMsg.isIncoming && msgId.isNotBlank() &&
            fetchedMsg.message.isEncrypted &&
            com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackInboxEnabled(appContext)
        ) {
            val acc = account
            val mid = msgId
            viewModelScope.launch {
                for (attempt in 1..3) {
                    val ok = runCatching { imapService.replaceInboxMessageWithDecrypted(acc, mid) }
                        .getOrDefault(false)
                    if (ok) break
                    kotlinx.coroutines.delay(10000)
                }
            }
        }
    }

    private fun processMessageWithAi(message: Message) {
        viewModelScope.launch {
            try {
                com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                val updated = com.mailchat.util.AiMessageProcessor.processMessage(appContext, message)
                messageRepository.updateMessage(updated)
            } catch (e: Exception) {
                android.util.Log.e("MailchatAI", "processMessageWithAi failed: ${e.message}")
            }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            val contacts = contactService.getAllContacts()
            _uiState.update { it.copy(contacts = contacts) }
        }
    }

    fun sendNewEmail(
        to: String,
        subject: String,
        body: String,
        groupName: String = "",
        accountId: Long = 0L,
        forceUnencrypted: Boolean = false,
        attachments: List<File> = emptyList(),
        cc: String = "",
        bcc: String = ""
    ) {
        viewModelScope.launch {
            lastNewEmailParams = NewEmailParams(to, subject, body, groupName, accountId, attachments, cc, bcc)
            val account = if (accountId > 0) {
                accountRepository.getAccountById(accountId)
            } else {
                accountRepository.getAllAccounts().first().firstOrNull()
            }
            if (account == null) {
                _uiState.update { it.copy(newEmailError = "No account configured") }
                return@launch
            }
            if (to.isBlank() || (body.isBlank())) {
                return@launch
            }
            _uiState.update { it.copy(isSendingNewEmail = true, newEmailError = null) }
            try {
                if (account.passwordEncrypted.isBlank()) {
                    _uiState.update { it.copy(isSendingNewEmail = false, newEmailError = "Password not set for account ${account.emailAddress}. Please re-enter your password in Settings.") }
                    return@launch
                }
                val recipients = to.split(",", ";")
                    .map { EmailGrouping.normalize(it.trim()) }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (recipients.isEmpty()) return@launch
                val signature = SettingsUtils.getSignature(appContext)
                val fullBody = if (signature.isNotBlank()) "$body\n\n$signature" else body
                // Keep the original (unencrypted) files for the local message copy.
                val originalAttachments = attachments
                // Mutable copy (the parameter itself cannot be reassigned inside the
                // encryption lambdas below).
                var sendAttachments = attachments

                // --- Encryption: OpenPGP (primary) + legacy MAILCHAT_ENC fallback, same as the
                // reply/send flow. Encrypts when the first recipient has a public key. ---
                val firstRecipient = recipients.firstOrNull()?.lowercase() ?: ""
                var finalBody = fullBody
                var isEncryptedMsg = false
                var encryptionAttempted = false
                val extraHeaders = mutableMapOf<String, String>()
                val hasId = com.mailchat.crypto.OpenPgpKeyService.hasIdentity(appContext)
                val bcOk = com.mailchat.crypto.OpenPgpKeyService.ensureProviderRegistered()
                com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: to=$firstRecipient hasIdentity=$hasId bcProvider=$bcOk forceUnencrypted=$forceUnencrypted")
                // When auto-lookup is enabled the key freshness is checked on
                // keys.openpgp.org before every send (see currentPeerKey).
                if (!forceUnencrypted && hasId) {
                    // Returns an error string when encryption was attempted (peer key found)
                    // but failed; null otherwise. withContext is not inline, so the error is
                    // returned instead of using a non-local return.
                    val encError: String? = withContext(Dispatchers.IO) {
                        try {
                            val pubKey = com.mailchat.crypto.OpenPgpKeyService
                                .currentPeerKey(appContext, firstRecipient)
                            com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: peer key found=${!pubKey.isNullOrBlank()}")
                            if (!pubKey.isNullOrBlank()) {
                                encryptionAttempted = true
                                // Optionally append the key signature (own public key or a
                                // link to it) so the recipient can reply encrypted. Only part
                                // of the sent (encrypted) body, not the local display copy.
                                val keySig = com.mailchat.util.SettingsUtils.getEncryptedKeySignatureText(appContext)
                                val bodyToEncrypt = if (keySig.isNotBlank()) "$fullBody\n\n$keySig" else fullBody
                                finalBody = com.mailchat.crypto.OpenPgpKeyService.encrypt(bodyToEncrypt, pubKey)
                                isEncryptedMsg = true
                                extraHeaders["X-MailChat-Encryption"] = "OpenPGP"
                                com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: encrypted with OpenPGP (keySig=${keySig.isNotBlank()})")
                                if (sendAttachments.isNotEmpty()) {
                                    val encDir = java.io.File(appContext.cacheDir, "enc_attachments").apply { mkdirs() }
                                    sendAttachments = sendAttachments.map { file ->
                                        val encFile = java.io.File(encDir, "${file.name}.gpg")
                                        com.mailchat.crypto.OpenPgpKeyService.encryptFile(file, encFile, pubKey)
                                        encFile
                                    }
                                }
                                null
                            } else {
                                com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: no public key for $firstRecipient -> plaintext")
                                null
                            }
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: OpenPGP encryption failed: ${e.message}")
                            if (encryptionAttempted) "OpenPGP: ${e.message}" else null
                        }
                    }
                    if (encError != null) {
                        _uiState.update { it.copy(isSendingNewEmail = false, encryptionErrorDialog = encError) }
                        return@launch
                    }
                }
                if (!isEncryptedMsg && !forceUnencrypted) {
                    val peer = com.mailchat.crypto.CryptoService.getPeers(appContext)
                        .firstOrNull { it.email.lowercase() == firstRecipient && it.pinVerified }
                    if (peer != null) {
                        try {
                            encryptionAttempted = true
                            val encrypted = com.mailchat.crypto.CryptoService.encryptForRecipient(fullBody, peer.publicKeyArmored)
                            finalBody = "MAILCHAT_ENC:$encrypted"
                            isEncryptedMsg = true
                            extraHeaders["X-MailChat-Encryption"] = "AES-256-GCM"
                            extraHeaders["X-MailChat-Fingerprint"] = peer.fingerprint
                            com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: encrypted with legacy MAILCHAT_ENC")
                            if (sendAttachments.isNotEmpty()) {
                                val encDir = java.io.File(appContext.cacheDir, "enc_attachments").apply { mkdirs() }
                                sendAttachments = sendAttachments.map { file ->
                                    val encFile = java.io.File(encDir, "${file.name}.enc")
                                    com.mailchat.crypto.CryptoService.encryptFile(file, encFile, peer.publicKeyArmored)
                                    encFile
                                }
                            }
                        } catch (e: Exception) {
                            com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: legacy encryption failed: ${e.message}")
                            if (encryptionAttempted) {
                                _uiState.update { it.copy(isSendingNewEmail = false, encryptionErrorDialog = "Legacy: ${e.message}") }
                                return@launch
                            }
                        }
                    }
                }
                com.mailchat.ai.AppLogger.log(appContext, "sendNewEmail: final isEncrypted=$isEncryptedMsg")

                val sentMessageId = smtpService.sendEmail(
                    account = account,
                    to = recipients.joinToString(","),
                    subject = subject,
                    body = finalBody,
                    attachments = sendAttachments,
                    extraHeaders = extraHeaders,
                    cc = cc,
                    bcc = bcc
                )
                val chat = getOrCreateChatForNewEmail(recipients, account, subject, body, groupName)
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
                val newMessageId = messageRepository.insertMessage(message)
                // Save the original (unencrypted) attachments to the local message so they
                // show up in the chat view (same as the reply/forward flow).
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
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageTimestamp = System.currentTimeMillis(),
                        lastMessageBody = body,
                        lastMessageSubject = subject
                    )
                )
                // Encrypted new email: replace the copy in the Sent folder with the decrypted
                // version so all email clients can read it (recipient got the encrypted one).
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
                _uiState.update { it.copy(isSendingNewEmail = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSendingNewEmail = false, newEmailError = e.message) }
            }
        }
    }

    fun retrySendNewEmailUnencrypted() {
        lastNewEmailParams?.let { p ->
            sendNewEmail(
                p.to, p.subject, p.body, p.groupName, p.accountId,
                forceUnencrypted = true,
                attachments = p.attachments,
                cc = p.cc,
                bcc = p.bcc
            )
        }
    }

    fun dismissEncryptionErrorDialog() {
        _uiState.update { it.copy(encryptionErrorDialog = null) }
    }

    private suspend fun getOrCreateChatForNewEmail(
        recipients: List<String>,
        account: EmailAccount,
        @Suppress("UNUSED_PARAMETER") subject: String,
        body: String,
        groupName: String = ""
    ): Chat {
        val isGroup = recipients.size > 1
        val members = (listOf(EmailGrouping.normalize(account.emailAddress)) + recipients).distinct()
        val chatEmail = if (isGroup) members.sorted().joinToString(";") else recipients.first()
        val chatName = if (isGroup) {
            if (groupName.isNotBlank()) groupName else EmailGrouping.groupDisplayName(members)
        } else {
            EmailGrouping.displayName("", recipients.first())
        }
        val existing = chatRepository.findByEmailAndAccount(chatEmail, account.id)
        if (existing != null) {
            if (isGroup && groupName.isNotBlank() && existing.participantName != groupName) {
                chatRepository.updateChat(existing.copy(participantName = groupName))
                return existing.copy(participantName = groupName)
            }
            return existing
        }
        val newChat = Chat(
            participantEmail = chatEmail,
            participantName = chatName,
            accountId = account.id,
            lastMessageTimestamp = System.currentTimeMillis(),
            lastMessageBody = body,
            unreadCount = 0,
            backgroundImageUri = null
        )
        val id = chatRepository.insertChat(newChat)
        return newChat.copy(id = id)
    }

    fun renameChat(chatId: Long, newName: String) {
        viewModelScope.launch {
            val chat = chatRepository.getChatById(chatId) ?: return@launch
            chatRepository.updateChat(chat.copy(participantName = newName))
        }
    }

    fun enterSelectionMode() = _uiState.update { it.copy(selectionMode = true, selectedChatIds = emptySet()) }

    fun exitSelectionMode() = _uiState.update { it.copy(selectionMode = false, selectedChatIds = emptySet()) }

    fun selectAllChats() {
        val allIds = _uiState.value.chats.map { it.id }.toSet()
        _uiState.update { it.copy(selectedChatIds = allIds) }
    }

    fun toggleSelectAllChats() {
        val allIds = _uiState.value.chats.map { it.id }.toSet()
        _uiState.update { state ->
            val allSelected = allIds.isNotEmpty() && state.selectedChatIds.containsAll(allIds)
            state.copy(selectedChatIds = if (allSelected) emptySet() else allIds)
        }
    }

    fun reverseChatSelection() {
        val allIds = _uiState.value.chats.map { it.id }.toSet()
        _uiState.update { state ->
            state.copy(selectedChatIds = allIds - state.selectedChatIds)
        }
    }

    /**
     * Selects the most recent chat (by last message timestamp) and deselects all others.
     */
    fun selectLastChat() {
        _uiState.update { state ->
            val lastChat = state.chats.maxByOrNull { it.lastMessageTimestamp }
                ?: return@update state
            state.copy(selectedChatIds = setOf(lastChat.id))
        }
    }

    fun selectChat(chatId: Long) {
        _uiState.update { state ->
            state.copy(selectedChatIds = state.selectedChatIds + chatId)
        }
    }

    fun deselectChat(chatId: Long) {
        _uiState.update { state ->
            state.copy(selectedChatIds = state.selectedChatIds - chatId)
        }
    }

    fun toggleNoReplyOnSelected() {
        viewModelScope.launch {
            val state = _uiState.value
            val selected = state.chats.filter { it.id in state.selectedChatIds }
            if (selected.isEmpty()) return@launch
            val allNoReply = selected.all { EmailGrouping.isNoReply(it) }
            val newValue = if (allNoReply) EmailGrouping.NO_REPLY_ALLOW else EmailGrouping.NO_REPLY_PROHIBIT
            for (chat in selected) {
                chatRepository.updateChat(chat.copy(noReply = newValue))
            }
            exitSelectionMode()
        }
    }

    fun processSelectedWithAi() {
        if (!SettingsUtils.getAiEnabled(appContext)) {
            _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_not_enabled)) }
            return
        }
        aiProcessingJob = viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            var processed = 0
            var failed = 0
            var firstError = ""
            try {
                val initOk = com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!initOk) {
                    val state = com.mailchat.ai.GeminiService.downloadState.value
                    _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_init_failed, state.toString())) }
                    _uiState.update { it.copy(isAiProcessing = false) }
                    return@launch
                }
                val state = _uiState.value
                val selected = state.chats.filter { it.id in state.selectedChatIds }
                for (chat in selected) {
                    if (coroutineContext[kotlinx.coroutines.Job]?.isActive == false) return@launch
                    val messages = messageRepository.getMessagesByChatSync(chat.id)
                    for (message in messages) {
                        if (message.bodyFull.isBlank()) continue
                        try {
                            messageRepository.updateMessage(
                                com.mailchat.util.AiMessageProcessor.processMessage(appContext, message)
                            )
                            processed++
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed++
                            if (firstError.isBlank()) firstError = e.message ?: "unknown"
                        }
                    }
                }
                loadAiSummaries()
                val msg = if (failed > 0) appContext.getString(com.mailchat.R.string.ai_batch_result, processed, failed, firstError)
                else appContext.getString(com.mailchat.R.string.ai_batch_done, processed)
                _uiState.update { it.copy(error = msg) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stopped by the user — no error message.
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_summary_error, e.message ?: "")) }
            }
            _uiState.update { it.copy(isAiProcessing = false) }
        }
    }

    fun extractRealNamesForSelected() {
        if (!SettingsUtils.getAiEnabled(appContext)) {
            _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_not_enabled)) }
            return
        }
        aiProcessingJob = viewModelScope.launch {
            _uiState.update { it.copy(isAiProcessing = true) }
            var done = 0
            try {
                val initOk = com.mailchat.util.AiEngine.configureAndInitialize(appContext)
                if (!initOk) {
                    val state = com.mailchat.ai.GeminiService.downloadState.value
                    _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_init_failed, state.toString())) }
                    _uiState.update { it.copy(isAiProcessing = false) }
                    return@launch
                }
                val state = _uiState.value
                val selected = state.chats.filter { it.id in state.selectedChatIds }
                val sysNames = state.contactNames
                for (chat in selected) {
                    if (coroutineContext[kotlinx.coroutines.Job]?.isActive == false) return@launch
                    if (chat.participantName != chat.participantEmail) continue
                    val email = chat.participantEmail
                    val sysName = sysNames[email.lowercase()]
                    if (!sysName.isNullOrBlank()) continue
                    try {
                        val base = com.mailchat.ai.GeminiService.extractDisplayName(email)
                        if (base.isBlank()) continue
                        chatRepository.updateChat(chat.copy(participantName = base))
                        done++
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Einzelfehler ueberspringen
                    }
                }
                _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.real_names_done, done)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Stopped by the user — no error message.
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.ai_summary_error, e.message ?: "")) }
            }
            _uiState.update { it.copy(isAiProcessing = false) }
            exitSelectionMode()
        }
    }

    fun markSelectedAsRead() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedChatIds
            for (id in ids) {
                messageRepository.markAllRead(id)
                chatRepository.getChatById(id)?.let { chat ->
                    chatRepository.updateChat(chat.copy(unreadCount = 0))
                }
            }
            exitSelectionMode()
        }
    }

    fun markChatAsRead(chat: Chat) {
        viewModelScope.launch {
            messageRepository.markAllRead(chat.id)
            chatRepository.updateChat(chat.copy(unreadCount = 0))
        }
    }

    fun toggleChatSelection(chatId: Long) {
        _uiState.update { state ->
            val selected = state.selectedChatIds.toMutableSet()
            if (selected.contains(chatId)) selected.remove(chatId) else selected.add(chatId)
            state.copy(selectedChatIds = selected)
        }
    }

    fun setCustomTagOnSelected(tag: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedChats = state.chats.filter { it.id in state.selectedChatIds }
            for (chat in selectedChats) {
                chatRepository.updateChat(chat.copy(customTag = tag))
            }
        }
    }

    fun mergeSelectedChats(photoEmail: String? = null) {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedChats = state.chats.filter { it.id in state.selectedChatIds }
            if (selectedChats.size < 2) {
                exitSelectionMode()
                return@launch
            }
            val target = selectedChats.first()
            try {
                for (chat in selectedChats.drop(1)) {
                    messageRepository.moveMessagesToChat(chat.id, target.id)
                    chatRepository.deleteChat(chat)
                }
                val latest = selectedChats.maxOfOrNull { it.lastMessageTimestamp } ?: System.currentTimeMillis()
                var updatedChat = target.copy(lastMessageTimestamp = latest)
                if (photoEmail != null && photoEmail != target.participantEmail) {
                    val photo = contactService.getPhotoUriByEmail(photoEmail)
                    val name = contactService.getNamesByEmails(listOf(photoEmail))[photoEmail.lowercase()]
                    if (photo != null) {
                        contactService.setCustomPhoto(target.participantEmail, android.net.Uri.parse(photo))
                    }
                    if (name != null) {
                        updatedChat = updatedChat.copy(participantName = name)
                    }
                }
                chatRepository.updateChat(updatedChat)
                _uiState.update { it.copy(contactPhotos = contactService.getPhotoUrisByEmails(state.chats.map { c -> c.participantEmail })) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
            exitSelectionMode()
        }
    }

    private suspend fun repairAttachmentPaths() {
        val broken = messageRepository.getBrokenUriAttachments()
        for (att in broken) {
            val oldFile = java.io.File(att.uri)
            if (!oldFile.exists()) continue
            val cleanName = att.uri.substringAfterLast('/').sanitizeFileName()
            if (cleanName == oldFile.name) continue
            val newFile = java.io.File(oldFile.parentFile, cleanName)
            if (newFile.exists() || !oldFile.renameTo(newFile)) continue
            messageRepository.updateAttachmentUri(att.id, newFile.absolutePath)
            chatRepository.replaceBackgroundImageUri(oldFile.absolutePath, newFile.absolutePath)
        }
    }

    private suspend fun cleanupNoReplyNamePrefix() {
        val chats = chatRepository.getAllChats().first()
        for (chat in chats) {
            if (chat.participantName.startsWith("🚫")) {
                val cleaned = chat.participantName.removePrefix("🚫").trimStart()
                if (cleaned.isNotBlank()) {
                    chatRepository.updateChat(chat.copy(participantName = cleaned))
                }
            }
        }
    }

    /**
     * One-time repair: backfills the chat list preview (subject, timestamp, body) from the
     * latest message of each chat. Older app versions did not update lastMessageSubject when
     * messages were fetched via background sync (SyncEngine), so some chats showed an empty
     * or stale subject on the overview page.
     */
    private suspend fun repairChatPreviews() {
        if (SettingsUtils.getChatPreviewsRepaired(appContext)) return
        try {
            val chats = chatRepository.getAllChats().first()
            for (chat in chats) {
                val latest = messageRepository.getLatestMessageByChat(chat.id) ?: continue
                if (chat.lastMessageSubject == latest.subject &&
                    chat.lastMessageTimestamp >= latest.timestamp
                ) continue
                chatRepository.updateChat(
                    chat.copy(
                        lastMessageSubject = latest.subject,
                        lastMessageBody = chat.lastMessageBody.ifBlank { latest.body },
                        lastMessageTimestamp = maxOf(chat.lastMessageTimestamp, latest.timestamp)
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MailchatList", "repairChatPreviews failed", e)
        }
        SettingsUtils.setChatPreviewsRepaired(appContext)
    }

    private suspend fun deleteChatInternal(chat: Chat): String? {
        return try {
            val messages = messageRepository.getMessagesByChatSync(chat.id)
            val account = accountRepository.getAccountById(chat.accountId)
            if (account != null) {
                val incomingIds = messages.filter { it.isIncoming && it.messageId.isNotBlank() }.map { it.messageId }
                val outgoingIds = messages.filter { !it.isIncoming && it.messageId.isNotBlank() }.map { it.messageId }
                try {
                    if (incomingIds.isNotEmpty()) {
                        imapService.deleteMessagesFromServer(account, incomingIds, isIncoming = true)
                    }
                    if (outgoingIds.isNotEmpty()) {
                        imapService.deleteMessagesFromServer(account, outgoingIds, isIncoming = false)
                    }
                } catch (_: Exception) {}
            }
            val deletedIds = messages.map { it.messageId }.filter { it.isNotBlank() }
            for (message in messages) {
                messageRepository.deleteAttachmentsByMessage(message.id)
            }
            messageRepository.deleteMessagesByChat(chat.id)
            chatRepository.deleteChat(chat)
            if (deletedIds.isNotEmpty()) {
                SettingsUtils.addDeletedMessageIds(appContext, deletedIds)
            }
            // Revoke the auto "Wo bist Du?" location/weather reply for this contact
            // (1:1 chats only) so a deleted chat doesn't keep triggering auto-replies.
            if (!chat.participantEmail.contains(";")) {
                val email = chat.participantEmail.trim().lowercase()
                if (email.isNotBlank()) {
                    SettingsUtils.setAutoLocationReply(appContext, email, false)
                    SettingsUtils.setAutoWeather(appContext, email, false)
                }
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }

    fun deleteSelectedChats() {
        viewModelScope.launch {
            val state = _uiState.value
            for (id in state.selectedChatIds) {
                val chat = state.chats.find { it.id == id } ?: continue
                deleteChatInternal(chat)?.let { error ->
                    _uiState.update { it.copy(error = error) }
                }
            }
            exitSelectionMode()
        }
    }

    fun deleteAccount(account: EmailAccount) {
        viewModelScope.launch {
            try {
                val attachments = messageRepository.getAttachmentsByAccount(account.id)
                for (attachment in attachments) {
                    try {
                        File(attachment.uri).delete()
                    } catch (_: Exception) {
                    }
                }
                val chats = chatRepository.getChatsByAccountSync(account.id)
                for (chat in chats) {
                    val messages = messageRepository.getMessagesByChatSync(chat.id)
                    for (message in messages) {
                        messageRepository.deleteAttachmentsByMessage(message.id)
                    }
                    messageRepository.deleteMessagesByChat(chat.id)
                    chatRepository.deleteChat(chat)
                }
                accountRepository.deleteAccountById(account.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun renewAccountEmailsForId(accountId: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            renewAccountEmails(account)
        }
    }

    fun renewAccountEmails(account: EmailAccount) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            try {
                val attachments = messageRepository.getAttachmentsByAccount(account.id)
                for (attachment in attachments) {
                    try { File(attachment.uri).delete() } catch (_: Exception) {}
                }
                val chats = chatRepository.getChatsByAccountSync(account.id)
                for (chat in chats) {
                    val messages = messageRepository.getMessagesByChatSync(chat.id)
                    for (message in messages) {
                        messageRepository.deleteAttachmentsByMessage(message.id)
                    }
                    messageRepository.deleteMessagesByChat(chat.id)
                    chatRepository.deleteChat(chat)
                }
                syncStateDao.deleteByAccount(account.id)
                accountRepository.updateAccount(account.copy(lastSyncTimestamp = 0))
                // Bei "Erneuern" alle E-Mails laden, nicht nur Sync-Periode
                val periodStart = 0L
                val incoming = imapService.fetchNewMessages(account, periodStart)
                com.mailchat.ai.AppLogger.log(appContext, "RENEW: fetched ${incoming.messages.size} incoming, ${incoming.errors.size} errors")
                for (fetchedMsg in incoming.messages) {
                    processFetchedMessage(account, fetchedMsg)
                }
                val sent = imapService.fetchSentMessages(account, periodStart)
                com.mailchat.ai.AppLogger.log(appContext, "RENEW: fetched ${sent.messages.size} sent, ${sent.errors.size} errors")
                for (fetchedMsg in sent.messages) {
                    processFetchedMessage(account, fetchedMsg)
                }
                val folderErrors = incoming.errors + sent.errors
                if (folderErrors.isNotEmpty()) {
                    _uiState.update { it.copy(error = folderErrors.joinToString("; ").take(300)) }
                }
                accountRepository.updateAccount(
                    account.copy(lastSyncTimestamp = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    private suspend fun getOrCreateChat(
        email: String,
        account: EmailAccount,
        fetchedMsg: com.mailchat.network.FetchedMessage
    ): Chat {
        val participants = fetchedMsg.participants
        val isGroup = participants.size > 2
        val chatEmail = if (isGroup) participants.sorted().joinToString(";") else email
        val chatName = if (isGroup) {
            EmailGrouping.groupDisplayName(participants)
        } else {
            EmailGrouping.displayName(
                fetchedMsg.message.from.substringBefore('@'),
                email
            )
        }
        val existing = chatRepository.findByEmailAndAccount(chatEmail, account.id)
        if (existing != null) {
            val updated = existing.copy(
                lastMessageTimestamp = fetchedMsg.message.timestamp,
                lastMessageBody = fetchedMsg.message.body,
                lastMessageSubject = fetchedMsg.message.subject,
                unreadCount = if (fetchedMsg.isIncoming) existing.unreadCount + 1 else existing.unreadCount,
                backgroundImageUri = existing.backgroundImageUri ?: fetchedMsg.firstImageUri
            )
            chatRepository.updateChat(updated)
            return updated
        }
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
        return newChat.copy(id = id)
    }

    fun hideSelectedChats() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedChatIds
            for (id in ids) {
                chatRepository.setChatHidden(id, true)
            }
            exitSelectionMode()
        }
    }

    fun setSpamCategoryOnSelected(category: String) {
        viewModelScope.launch {
            val ids = _uiState.value.selectedChatIds
            for (id in ids) {
                val messages = messageRepository.getMessagesByChatSync(id)
                for (msg in messages) {
                    val isSpam = category != "" && category != "ham"
                    messageRepository.updateMessage(msg.copy(isSpam = isSpam, spamCategory = category))
                }
            }
            exitSelectionMode()
        }
    }

    fun showHiddenChat(chat: Chat) {
        viewModelScope.launch {
            chatRepository.setChatHidden(chat.id, false)
        }
    }

    fun unimportantChat(chat: Chat) {
        viewModelScope.launch {
            chatRepository.setChatImportant(chat.id, false)
        }
    }

    fun toggleImportantChat(chatId: Long) {
        viewModelScope.launch {
            val fresh = chatRepository.getChatById(chatId) ?: return@launch
            chatRepository.setChatImportant(chatId, !fresh.important)
        }
    }

    fun deleteSingleChat(chat: Chat) {
        viewModelScope.launch {
            deleteChatInternal(chat)?.let { error ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun favoriteSelectedChats() {
        val state = _uiState.value
        val ids = state.selectedChatIds
        viewModelScope.launch {
            val currentFavorites = chatRepository.getFavoriteCount()
            val newFavorites = ids.count { id ->
                state.chats.find { it.id == id }?.favorite == false
            }
            if (currentFavorites + newFavorites > 5) {
                _uiState.update { it.copy(error = appContext.getString(com.mailchat.R.string.favorites_limit)) }
                return@launch
            }
            for (id in ids) {
                val chat = state.chats.find { it.id == id }
                val isFavorite = chat?.favorite == true
                chatRepository.setChatFavorite(id, !isFavorite)
            }
            exitSelectionMode()
        }
    }

    fun importantSelectedChats() {
        val state = _uiState.value
        val ids = state.selectedChatIds
        viewModelScope.launch {
            for (id in ids) {
                val chat = state.chats.find { it.id == id }
                val isImportant = chat?.important == true
                chatRepository.setChatImportant(id, !isImportant)
            }
            exitSelectionMode()
        }
    }

    fun updateAccountColor(account: EmailAccount, color: Int) {
        viewModelScope.launch {
            accountRepository.updateAccount(account.copy(avatarColor = color))
        }
    }

    fun openDeveloperChat(onResult: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.getAllAccounts().first()
                val account = accounts.firstOrNull() ?: return@launch
                val devEmail = "florescu@web.de"
                var chat = chatRepository.findByEmailAndAccount(devEmail, account.id)
                if (chat == null) {
                    val newChat = Chat(
                        participantEmail = devEmail,
                        participantName = "Developer MailChat",
                        accountId = account.id,
                        lastMessageTimestamp = System.currentTimeMillis()
                    )
                    val id = chatRepository.insertChat(newChat)
                    chat = newChat.copy(id = id)
                } else if (chat.participantName != "Developer MailChat") {
                    chatRepository.updateChat(chat.copy(participantName = "Developer MailChat"))
                    chat = chat.copy(participantName = "Developer MailChat")
                }
                onResult(chat.id)
            } catch (_: Exception) {}
        }
    }
}