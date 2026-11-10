package com.mailchat.network

import com.mailchat.R
import com.mailchat.data.model.Attachment
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.model.Message
import com.mailchat.util.EmailGrouping
import com.mailchat.util.sanitizeFileName
import com.mailchat.util.stripHtml
import com.mailchat.util.truncateToLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Properties
import javax.mail.BodyPart
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message as JMessage
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.Store
import javax.mail.search.MessageIDTerm
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

data class FetchedMessage(
    val message: Message,
    val attachments: List<Attachment>,
    val firstImageUri: String?,
    val participantEmail: String,
    val isIncoming: Boolean,
    val participants: List<String> = emptyList()
)

data class FetchResult(
    val messages: List<FetchedMessage>,
    val errors: List<String> = emptyList()
)

@Singleton
class ImapService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncStateDao: com.mailchat.data.dao.SyncStateDao
) {

    private val properties: Properties
        get() = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "30000")
            put("mail.imaps.writetimeout", "30000")
        }

    private fun connectStore(account: EmailAccount): Store {
        return try {
            val session = Session.getInstance(properties)
            val store = session.getStore("imaps")
            store.connect(
                account.imapHost,
                account.imapPort,
                account.username,
                account.passwordEncrypted
            )
            store
        } catch (e: Exception) {
            throw ImapConnectionException(context.getString(R.string.connection_failed, e.message), e)
        }
    }

    suspend fun listFolders(account: EmailAccount): List<String> = withContext(Dispatchers.IO) {
        val store = connectStore(account)
        try {
            val defaultFolder = store.getDefaultFolder()
            val folders = defaultFolder.list("*")
            val names = folders.map { it.name }.filter { it.isNotBlank() }.toMutableSet()
            // INBOX is a special folder that some servers do not return in LIST *; always include it.
            names.add("INBOX")
            names.toList()
        } finally {
            store.close()
        }
    }

    fun isOutgoingFolder(folderName: String): Boolean {
        val lower = folderName.lowercase()
        return lower.contains("sent") || lower.contains("gesendet") ||
            lower.contains("sent items") || lower.contains("abgesendet")
    }

    suspend fun fetchNewMessages(
        account: EmailAccount,
        sinceTimestamp: Long,
        maxMessages: Int = 100_000
    ): FetchResult = withContext(Dispatchers.IO) {
        val store = connectStore(account)
        try {
            val folders = getSyncFolders(account)
            val result = mutableListOf<FetchedMessage>()
            val errors = mutableListOf<String>()
            for (folderName in folders) {
                if (isOutgoingFolder(folderName)) continue
                try {
                    val folder = store.getFolder(folderName)
                    if (!folder.exists()) continue
                    result.addAll(fetchFromOpenFolder(store, account, folderName, sinceTimestamp, maxMessages, isIncoming = true))
                } catch (e: Exception) {
                    android.util.Log.w("MailchatSync", "fetch $folderName failed", e)
                    errors.add("$folderName: ${e.message}")
                }
            }
            FetchResult(result, errors)
        } finally {
            store.close()
        }
    }

    suspend fun fetchMessageByMessageId(account: EmailAccount, messageId: String): FetchedMessage? =
        withContext(Dispatchers.IO) {
            if (messageId.isBlank()) return@withContext null
            val store = connectStore(account)
            try {
                val folders = getSyncFolders(account).filter { !isOutgoingFolder(it) }
                for (folderName in folders) {
                    try {
                        val folder = store.getFolder(folderName)
                        if (!folder.exists()) continue
                        folder.open(Folder.READ_ONLY)
                        try {
                            // 1) server-side SEARCH (kann von manchen Providern abgelehnt werden)
                            try {
                                val found = folder.search(javax.mail.search.HeaderTerm("Message-ID", messageId))
                                if (found.isNotEmpty()) {
                                    val parsed = parseMessage(found.first(), true)
                                    if (parsed != null) return@withContext parsed
                                }
                            } catch (_: Exception) {
                            }
                            // 2) Fallback: Header der neuesten Nachrichten scannen
                            val messages = folder.messages
                            var checked = 0
                            for (i in messages.indices.reversed()) {
                                if (checked >= 300) break
                                checked++
                                try {
                                    val mid = messages[i].getHeader("Message-ID")?.firstOrNull()
                                    if (mid != null && mid.trim() == messageId.trim()) {
                                        val parsed = parseMessage(messages[i], true)
                                        if (parsed != null) return@withContext parsed
                                        break
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        } finally {
                            folder.close(false)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MailchatSync", "repair search $folderName failed", e)
                    }
                }
                null
            } finally {
                store.close()
            }
        }

    suspend fun fetchRawMessage(account: EmailAccount, messageId: String): String? =
        withContext(Dispatchers.IO) {
            if (messageId.isBlank()) return@withContext null
            val store = connectStore(account)
            try {
                for (folderName in getSyncFolders(account)) {
                    try {
                        val folder = store.getFolder(folderName)
                        if (!folder.exists()) continue
                        folder.open(Folder.READ_ONLY)
                        try {
                            var found: javax.mail.Message? = null
                            try {
                                val res = folder.search(javax.mail.search.HeaderTerm("Message-ID", messageId))
                                if (res.isNotEmpty()) found = res.first()
                            } catch (_: Exception) {
                            }
                            if (found == null) {
                                val messages = folder.messages
                                var checked = 0
                                for (i in messages.indices.reversed()) {
                                    if (checked >= 300) break
                                    checked++
                                    try {
                                        val mid = messages[i].getHeader("Message-ID")?.firstOrNull()
                                        if (mid != null && mid.trim() == messageId.trim()) {
                                            found = messages[i]
                                            break
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            if (found != null) {
                                val out = java.io.ByteArrayOutputStream()
                                try {
                                    found.writeTo(out)
                                } catch (_: Exception) {
                                }
                                val raw = out.toString(Charsets.UTF_8.name())
                                if (raw.isNotBlank()) return@withContext raw
                            }
                        } finally {
                            folder.close(false)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MailchatSync", "raw search $folderName failed", e)
                    }
                }
                null
            } finally {
                store.close()
            }
        }

    suspend fun fetchSentMessages(
        account: EmailAccount,
        sinceTimestamp: Long,
        maxMessages: Int = 100_000
    ): FetchResult = withContext(Dispatchers.IO) {        val store = connectStore(account)
        try {
            val folders = getSyncFolders(account).filter { isOutgoingFolder(it) }
            val result = mutableListOf<FetchedMessage>()
            val errors = mutableListOf<String>()
            for (folderName in folders) {
                try {
                    val folder = store.getFolder(folderName)
                    if (!folder.exists()) continue
                    result.addAll(fetchFromOpenFolder(store, account, folderName, sinceTimestamp, maxMessages, isIncoming = false))
                } catch (e: Exception) {
                    android.util.Log.w("MailchatSync", "fetch sent $folderName failed", e)
                    errors.add("$folderName: ${e.message}")
                }
            }
            FetchResult(result, errors)
        } finally {
            store.close()
        }
    }

    private fun getSyncFolders(account: EmailAccount): List<String> {
        val custom = account.syncFolders
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (custom.isNotEmpty()) return custom
        return listOf("INBOX", "Sent", "Sent Messages", "INBOX.Sent", "INBOX/Sent", "Sent Items", "Gesendet", "Abgesendet")
    }

    suspend fun deleteMessagesFromServer(
        account: EmailAccount,
        messageIds: List<String>,
        isIncoming: Boolean
    ) = withContext(Dispatchers.IO) {
        if (messageIds.isEmpty()) return@withContext
        val store = connectStore(account)
        try {
            val validIds = messageIds.filter { it.isNotBlank() }
            if (validIds.isEmpty()) return@withContext
            val terms = validIds.map { MessageIDTerm(it) }
            val search = if (terms.size == 1) terms[0]
                else javax.mail.search.OrTerm(terms.toTypedArray())

            val foldersToDelete = if (isIncoming) {
                getSyncFolders(account).filter { !isOutgoingFolder(it) }
            } else {
                getSyncFolders(account).filter { isOutgoingFolder(it) }
            }

            for (folderName in foldersToDelete) {
                try {
                    val folder = store.getFolder(folderName)
                    if (!folder.exists()) continue
                    folder.open(Folder.READ_WRITE)
                    try {
                        val found = folder.search(search)
                        for (msg in found) {
                            msg.setFlag(Flags.Flag.DELETED, true)
                        }
                        if (found.isNotEmpty()) folder.expunge()
                    } finally {
                        folder.close(false)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MailchatSync", "server delete in $folderName failed", e)
                }
            }
        } finally {
            store.close()
        }
    }

    private fun findSentFolder(store: Store): String? {
        val sentFolders = listOf("Sent", "Sent Messages", "INBOX.Sent", "INBOX/Sent", "Sent Items")
        for (name in sentFolders) {
            try {
                if (store.getFolder(name).exists()) return name
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Replace the message with [messageId] in the Sent folder with a decrypted version:
     * the PGP-encrypted body is decrypted with the stored identity and .gpg attachments
     * are decrypted to their original files. The replacement keeps the original Message-ID,
     * date and headers, so threading and other email clients keep working.
     *
     * Returns true if the message was found and replaced, false otherwise (message not in
     * the Sent folder yet, not PGP-encrypted, or no identity available).
     */
    suspend fun replaceSentMessageWithDecrypted(account: EmailAccount, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (messageId.isBlank()) return@withContext false
            if (!com.mailchat.crypto.OpenPgpKeyService.hasIdentity(context)) return@withContext false
            val store = connectStore(account)
            try {
                val sentFolderName = findSentFolder(store) ?: return@withContext false
                replaceMessageInFolder(store, sentFolderName, messageId, "Sent")
            } finally {
                store.close()
            }
        }

    /**
     * Same as [replaceSentMessageWithDecrypted] but for the INBOX: the incoming
     * PGP-encrypted message is replaced by its decrypted version (same Message-ID,
     * date and headers), so the decrypted copy is visible in the inbox as well.
     * The original encrypted message is removed from the inbox.
     */
    suspend fun replaceInboxMessageWithDecrypted(account: EmailAccount, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (messageId.isBlank()) return@withContext false
            if (!com.mailchat.crypto.OpenPgpKeyService.hasIdentity(context)) return@withContext false
            val store = connectStore(account)
            try {
                replaceMessageInFolder(store, "INBOX", messageId, "Inbox")
            } finally {
                store.close()
            }
        }

    /**
     * Shared implementation of the write-back: finds the message with [messageId] in
     * [folderName], checks that it is still PGP-encrypted, and replaces it with the
     * decrypted version (keeping Message-ID, date and headers so threading keeps working).
     * Returns true if the message was found and replaced, false otherwise.
     */
    private fun replaceMessageInFolder(
        store: Store,
        folderName: String,
        messageId: String,
        label: String
    ): Boolean {
        return try {
            val folder = store.getFolder(folderName).apply { open(Folder.READ_WRITE) }
            try {
                val found = folder.search(javax.mail.search.HeaderTerm("Message-ID", messageId))
                if (found.size != 1) return false
                val original = folder.getMessage(found[0].messageNumber) as? javax.mail.internet.MimeMessage
                    ?: return false
                if (!com.mailchat.crypto.OpenPgpKeyService.isEncryptedContent(extractTextContent(original))) {
                    return false
                }

                val decryptedContent = buildDecryptedContent(original) ?: return false

                val replacement = javax.mail.internet.MimeMessage(Session.getInstance(Properties())).apply {
                    // Copy all headers except the content-related ones (rebuilt below)
                    for (h in original.allHeaders) {
                        val n = h.name
                        if (n.equals("Content-Type", true) || n.equals("Content-Transfer-Encoding", true) ||
                            n.equals("Content-Disposition", true) || n.equals("Content-ID", true) ||
                            n.equals("Content-Description", true) || n.equals("MIME-Version", true) ||
                            n.equals("Message-ID", true) || n.equals("Date", true)
                        ) continue
                        addHeader(n, h.value)
                    }
                    original.from?.firstOrNull()?.let { setFrom(it) }
                    original.getRecipients(JMessage.RecipientType.TO)?.let { setRecipients(JMessage.RecipientType.TO, it) }
                    original.getRecipients(JMessage.RecipientType.CC)?.let { setRecipients(JMessage.RecipientType.CC, it) }
                    setSubject(original.subject)
                    setSentDate(original.sentDate)
                    when (val c = decryptedContent) {
                        is String -> setContent(c, "text/plain; charset=utf-8")
                        is Multipart -> setContent(c)
                        else -> setContent(c.toString(), "text/plain; charset=utf-8")
                    }
                    setHeader("Message-ID", messageId)
                    saveChanges()
                }

                val originalFlags = original.flags
                folder.appendMessages(arrayOf(replacement))
                original.setFlag(Flags.Flag.DELETED, true)
                folder.expunge()
                val newFound = folder.search(javax.mail.search.HeaderTerm("Message-ID", messageId))
                if (newFound.size == 1) {
                    folder.getMessage(newFound[0].messageNumber).setFlags(originalFlags, true)
                }
                com.mailchat.ai.AppLogger.log(context, "Write-back: decrypted copy saved in $label")
                true
            } finally {
                folder.close(false)
            }
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(context, "Write-back failed: ${e.message}")
            false
        }
    }

    /** Extract the text content of a message (plain body, or the first text part of a multipart). */
    private fun extractTextContent(message: javax.mail.internet.MimeMessage): String {
        return try {
            when (val content = message.content) {
                is String -> content
                is Multipart -> {
                    for (i in 0 until content.count) {
                        val part = content.getBodyPart(i)
                        if ((part.contentType ?: "").startsWith("text/")) {
                            return part.content as? String ?: ""
                        }
                    }
                    ""
                }
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Rebuild the message content with the PGP-encrypted parts decrypted: the text part
     * (armored PGP block) becomes plaintext, .gpg attachments become their original files.
     * Returns null if the content cannot be decrypted.
     */
    private fun buildDecryptedContent(original: javax.mail.internet.MimeMessage): Any? {
        return try {
            val content = original.content
            if (content is Multipart) {
                val newMultipart = javax.mail.internet.MimeMultipart()
                for (i in 0 until content.count) {
                    val part = content.getBodyPart(i)
                    newMultipart.addBodyPart(replaceEncryptedPart(part) ?: part)
                }
                newMultipart
            } else if (content is String && com.mailchat.crypto.OpenPgpKeyService.isEncryptedContent(content)) {
                com.mailchat.crypto.OpenPgpKeyService.decrypt(context, content)
            } else {
                content
            }
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(context, "Write-back: decrypt content failed: ${e.message}")
            null
        }
    }

    /**
     * Decrypt a single multipart part if it is PGP-encrypted (text part with an armored PGP
     * block, or a .gpg attachment). Returns the replacement part, or null to keep the original.
     */
    private fun replaceEncryptedPart(part: BodyPart): BodyPart? {
        return try {
            val contentType = (part.contentType ?: "").lowercase()
            if (contentType.startsWith("text/")) {
                val text = part.content as? String
                if (text != null && com.mailchat.crypto.OpenPgpKeyService.isEncryptedContent(text)) {
                    val newPart = javax.mail.internet.MimeBodyPart()
                    newPart.setText(com.mailchat.crypto.OpenPgpKeyService.decrypt(context, text), "UTF-8")
                    return newPart
                }
            }
            val fileName = part.fileName ?: ""
            if (fileName.endsWith(".gpg", ignoreCase = true)) {
                val dir = File(context.cacheDir, "writeback").apply { mkdirs() }
                val stamp = System.nanoTime()
                val encFile = File(dir, "enc_$stamp")
                part.inputStream.use { input -> encFile.outputStream().use { o -> input.copyTo(o) } }
                val decFile = File(dir, "dec_$stamp")
                val originalName = com.mailchat.crypto.OpenPgpKeyService.decryptFile(encFile, decFile, context)
                encFile.delete()
                val newPart = javax.mail.internet.MimeBodyPart()
                newPart.attachFile(decFile)
                newPart.setFileName(originalName.ifBlank { fileName.removeSuffix(".gpg") })
                part.getHeader("Content-ID")?.firstOrNull()?.let { newPart.setHeader("Content-ID", it) }
                decFile.delete()
                return newPart
            }
            null
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(context, "Write-back: part decrypt failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchFromOpenFolder(
        store: Store,
        account: EmailAccount,
        folderName: String,
        sinceTimestamp: Long,
        maxMessages: Int,
        isIncoming: Boolean
    ): List<FetchedMessage> {
        val folder = store.getFolder(folderName).apply { open(Folder.READ_ONLY) }
        try {
            val uidFolder = folder as? javax.mail.UIDFolder
            val uidValidity = if (uidFolder != null) {
                runCatching { uidFolder.uidValidity }.getOrDefault(0L)
            } else 0L
            val state = if (uidFolder != null) syncStateDao.get(account.id, folderName) else null

            // The download period was increased since the last sync -> we must (re)fetch the extended range.
            // Also force a full fetch if periodStart was never stored (legacy state).
            val storedPeriodStart = state?.periodStart ?: 0L
            val periodExtended = state != null && (storedPeriodStart == 0L || sinceTimestamp < storedPeriodStart)

            val incremental = uidFolder != null && state != null &&
                state.uidValidity == uidValidity && state.lastUid > 0 && !periodExtended

            val messages: Array<JMessage> = if (incremental) {
                uidFolder!!.getMessagesByUID(state!!.lastUid + 1, javax.mail.UIDFolder.LASTUID)
            } else {
                // Full or extended fetch: a server-side SINCE search reliably respects the period
                // (the old client-side date break assumed perfectly date-sorted folders).
                val searchResult = runCatching {
                    folder.search(
                        javax.mail.search.ReceivedDateTerm(
                            javax.mail.search.ComparisonTerm.GE,
                            Date(sinceTimestamp)
                        )
                    )
                }
                val msgs = if (searchResult.isSuccess) searchResult.getOrNull() ?: folder.messages else folder.messages
                com.mailchat.ai.AppLogger.log(context, "IMAP $folderName: incremental=$incremental searchOk=${searchResult.isSuccess} count=${msgs.size} since=$sinceTimestamp")
                msgs
            }

            var newLastUid = state?.lastUid ?: 0L
            val result = mutableListOf<FetchedMessage>()
            var count = 0
            for (mail in messages) {
                if (count >= maxMessages) break
                if (uidFolder != null) {
                    runCatching { uidFolder.getUID(mail) }.getOrNull()?.let { uid ->
                        if (uid > newLastUid) newLastUid = uid
                    }
                }
                val fetched = parseMessage(mail, isIncoming)
                if (fetched != null) {
                    result.add(fetched)
                    count++
                }
            }
            result.reverse()

            // Keep the earliest period we have ever fetched, so decreasing the period does not
            // make us forget (and re-hide) older mail that is already stored.
            val newPeriodStart = if (storedPeriodStart > 0L && storedPeriodStart < sinceTimestamp) storedPeriodStart else sinceTimestamp

            val hasNewMessages = newLastUid > (state?.lastUid ?: 0L)
            if (uidFolder != null && uidValidity > 0L && newLastUid > 0 && (hasNewMessages || periodExtended)) {
                syncStateDao.save(
                    com.mailchat.data.model.SyncState(
                        accountId = account.id,
                        folder = folderName,
                        uidValidity = uidValidity,
                        lastUid = newLastUid,
                        periodStart = newPeriodStart
                    )
                )
            }
            return result
        } finally {
            folder.close(false)
        }
    }

    private fun messageDate(mail: JMessage): Date {
        return try {
            mail.receivedDate ?: mail.sentDate ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    private fun bodyPartsOf(part: javax.mail.Part): List<BodyPart> {
        return try {
            if (part.isMimeType("multipart/*")) {
                val multipart = part.content as? Multipart ?: return emptyList()
                val count = multipart.count
                (0 until count).map { multipart.getBodyPart(it) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Whether [part] (or any nested part) is a PGP/MIME encrypted message, i.e. it contains a
     * multipart/encrypted structure or an "encrypted.asc"/"pgp-signed.asc" payload part.
     *
     * Thunderbird and other OpenPGP clients send encrypted mail this way: the real content
     * (body, and possibly subject/attachments) is inside the encrypted payload, NOT in the
     * outer message. The outer message then looks empty with a single "encrypted" file.
     */
    private fun isPgpMimeEncrypted(part: javax.mail.Part): Boolean {
        return try {
            if (part.isMimeType("multipart/encrypted")) return true
            if (part.isMimeType("multipart/*")) {
                return bodyPartsOf(part).any { isPgpMimeEncrypted(it) }
            }
            val fn = part.fileName?.lowercase()
            fn == "encrypted.asc" || fn == "pgp-signed.asc"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Finds the PGP payload part of a PGP/MIME message — the part holding the actual encrypted
     * data (usually the application/octet-stream part of a multipart/encrypted, named
     * "encrypted.asc"). Returns null if none is found.
     */
    private fun findPgpMimePayload(part: javax.mail.Part): javax.mail.Part? {
        return try {
            if (part.isMimeType("multipart/encrypted")) {
                val subParts = bodyPartsOf(part)
                return subParts.firstOrNull {
                    it.fileName?.lowercase()?.let { n -> n == "encrypted.asc" || n == "pgp-signed.asc" } == true
                } ?: subParts.firstOrNull { it.isMimeType("application/octet-stream") }
                    ?: subParts.lastOrNull()
            }
            if (part.isMimeType("multipart/*")) {
                for (subPart in bodyPartsOf(part)) {
                    val found = findPgpMimePayload(subPart)
                    if (found != null) return found
                }
                return null
            }
            val fn = part.fileName?.lowercase()
            if (fn == "encrypted.asc" || fn == "pgp-signed.asc") return part
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts a PGP/MIME message: extracts the encrypted payload, decrypts it with the stored
     * identity, and parses the result as a MIME message. The decrypted MIME message holds the
     * real body (and attachments). Returns the decrypted [JMessage], or null if the message is
     * not PGP/MIME, no identity exists, or decryption/parsing fails.
     */
    private fun tryDecryptPgpMime(mail: JMessage): JMessage? {
        return try {
            val payload = findPgpMimePayload(mail) ?: return null
            val data = payload.inputStream.use { it.readBytes() }
            val (decryptedBytes, _) = com.mailchat.crypto.OpenPgpKeyService.decryptData(
                data,
                com.mailchat.crypto.OpenPgpKeyService.getSecretKeyArmored(context),
                com.mailchat.crypto.OpenPgpKeyService.getPassphrase(context).toCharArray()
            )
            val decrypted = javax.mail.internet.MimeMessage(
                Session.getInstance(Properties()),
                java.io.ByteArrayInputStream(decryptedBytes)
            )
            com.mailchat.ai.AppLogger.log(context, "PGP/MIME decrypted: subject=${decrypted.subject?.take(40)}")
            decrypted
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(context, "PGP/MIME decrypt failed: ${e.message}")
            null
        }
    }

    private fun parseMessage(mail: JMessage, isIncoming: Boolean): FetchedMessage? {
        return try {
            val fromAddress = mail.from?.firstOrNull()?.let {
                if (it is javax.mail.internet.InternetAddress) it.address else null
            } ?: return null

            // For sent messages the chat participant is the recipient (To address)
            val participantAddress = if (isIncoming) {
                fromAddress
            } else {
                mail.getRecipients(javax.mail.Message.RecipientType.TO)?.firstOrNull()?.let {
                    if (it is javax.mail.internet.InternetAddress) it.address else null
                } ?: fromAddress
            }

            // PGP/MIME (Thunderbird & co.): the real content is encrypted inside an
            // "encrypted.asc" part of a multipart/encrypted structure. Decrypt it and use the
            // decrypted MIME message as the source for subject, body and attachments. Without
            // this the message shows up empty with a single "encrypted" file attachment.
            var sourceMail: JMessage = mail
            var pgpMimeDecrypted = false
            if (isIncoming && isPgpMimeEncrypted(mail)
                && com.mailchat.crypto.OpenPgpKeyService.hasIdentity(context)
            ) {
                val decrypted = tryDecryptPgpMime(mail)
                if (decrypted != null) {
                    sourceMail = decrypted
                    pgpMimeDecrypted = true
                }
            }

            // The subject may live in the outer header (standard PGP/MIME) or, if the outer one
            // is empty, in the decrypted message. Prefer the decrypted one, fall back to outer.
            val subject = (sourceMail.subject ?: "").ifBlank { mail.subject ?: "" }
            var bodyFull = extractTextBody(sourceMail)
            // Signature status of the incoming message ("" = not checked / not encrypted).
            var signatureStatus = ""
            // Decrypt incoming OpenPGP-encrypted bodies (inline armored PGP, not PGP/MIME) with
            // the stored private key so the chat shows plaintext instead of the raw armored PGP
            // block, and verify the sender's signature. Sent messages are handled by the
            // write-back path, so only incoming messages need this here.
            if (isIncoming && com.mailchat.crypto.OpenPgpKeyService.isEncryptedContent(bodyFull)
                && com.mailchat.crypto.OpenPgpKeyService.hasIdentity(context)
            ) {
                try {
                    // Look up the sender's public key: local peer first, then keys.openpgp.org
                    // (short timeouts so a slow network never blocks the fetch).
                    val senderPubKey = com.mailchat.crypto.OpenPgpKeyService.getPeer(context, fromAddress)?.publicKeyArmored
                        ?: com.mailchat.crypto.OpenPgpKeyService.fetchKeyByEmail(fromAddress, 5000, 8000)
                    val result = com.mailchat.crypto.OpenPgpKeyService.decryptAndVerify(context, bodyFull, senderPubKey)
                    bodyFull = result.plaintext
                    signatureStatus = result.signatureStatus.name
                    com.mailchat.ai.AppLogger.log(
                        context,
                        "Decrypted incoming email body: subject=${subject.take(40)} sig=${signatureStatus} signer=${result.signerFingerprint}"
                    )
                } catch (e: Exception) {
                    com.mailchat.ai.AppLogger.log(context, "Incoming email body decrypt failed: ${e.message}")
                }
            }
            val body = bodyFull.stripHtml().truncateToLines(5)
            val timestamp = messageDate(mail).time

            val attachments = mutableListOf<Attachment>()
            var firstImageUri: String? = null
            var sortOrder = 0

            fun collectAttachments(part: javax.mail.Part) {
                if (part.isMimeType("multipart/*")) {
                    for (subPart in bodyPartsOf(part)) {
                        collectAttachments(subPart)
                    }
                } else {
                    val mime = part.contentType?.substringBefore(";")?.trim()?.lowercase() ?: "?"
                    if (mime.startsWith("image/") || part.getHeader("Content-ID") != null) {
                        com.mailchat.ai.AppLogger.log(context, "collectAtt: mime=$mime cid=${part.getHeader("Content-ID")?.firstOrNull()} fn=${part.fileName}")
                    }
                    val att = saveAttachment(part, sortOrder++)
                    if (att != null) {
                        attachments.add(att)
                        if (firstImageUri == null && att.isImage) firstImageUri = att.uri
                    }
                }
            }
            collectAttachments(sourceMail)
            com.mailchat.ai.AppLogger.log(context, "collectAtt done: ${attachments.size} attachments for subject=${subject.take(40)}")

            val normalizedParticipant = EmailGrouping.normalize(participantAddress)
            val normalizedFrom = EmailGrouping.normalize(fromAddress)

            val messageId = try {
                mail.getHeader("Message-ID")?.firstOrNull() ?: ""
            } catch (e: Exception) {
                ""
            }

            // Encrypted if our app tagged it (X-MailChat-Encryption, kept even after the
            // Sent-folder write-back) or the content is an armored PGP block (incoming mail).
            val encHeader = try {
                mail.getHeader("X-MailChat-Encryption")?.firstOrNull() ?: ""
            } catch (e: Exception) {
                ""
            }
            val isEncryptedMsg = pgpMimeDecrypted || encHeader.isNotBlank() ||
                com.mailchat.crypto.OpenPgpKeyService.isEncryptedContent(bodyFull)

            val cc = parseAddressListHeader(mail, "Cc")
            val bcc = parseAddressListHeader(mail, "Bcc")

            val message = Message(
                from = normalizedFrom,
                subject = subject,
                body = body,
                bodyFull = bodyFull,
                timestamp = timestamp,
                isIncoming = isIncoming,
                messageId = messageId,
                cc = cc,
                bcc = bcc,
                isEncrypted = isEncryptedMsg,
                signatureStatus = signatureStatus
            )

            val toRecipients = (mail.getRecipients(javax.mail.Message.RecipientType.TO) ?: emptyArray())
                .mapNotNull { (it as? javax.mail.internet.InternetAddress)?.address?.let { a -> EmailGrouping.normalize(a) } }
            val ccRecipients = (mail.getRecipients(javax.mail.Message.RecipientType.CC) ?: emptyArray())
                .mapNotNull { (it as? javax.mail.internet.InternetAddress)?.address?.let { a -> EmailGrouping.normalize(a) } }
            val participants = (listOf(normalizedFrom) + toRecipients + ccRecipients).distinct()

            FetchedMessage(message, attachments, firstImageUri, normalizedParticipant, isIncoming, participants)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAddressListHeader(mail: JMessage, headerName: String): String {
        return try {
            val headerValue = mail.getHeader(headerName)?.firstOrNull() ?: return ""
            if (headerValue.isBlank()) return ""
            javax.mail.internet.InternetAddress.parse(headerValue)
                .mapNotNull { it.address }
                .joinToString(";")
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractTextBody(mail: JMessage): String {
        return try {
            extractBodyRecursive(mail)
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractBodyRecursive(part: javax.mail.Part): String {
        return try {
            if (part.isMimeType("text/plain")) {
                part.content.toString()
            } else if (part.isMimeType("text/html")) {
                part.content.toString()
            } else if (part.isMimeType("multipart/*")) {
                var htmlBody: String? = null
                var plainBody: String? = null
                for (subPart in bodyPartsOf(part)) {
                    if (htmlBody != null && plainBody != null) break
                    if (subPart.isMimeType("text/plain") && plainBody == null) {
                        plainBody = subPart.content.toString()
                    } else if (subPart.isMimeType("text/html") && htmlBody == null) {
                        htmlBody = subPart.content.toString()
                    } else if (subPart.isMimeType("multipart/*")) {
                        val nested = extractBodyRecursive(subPart)
                        if (nested.isNotBlank()) {
                            if (subPart.contentType?.contains("html") == true && htmlBody == null) {
                                htmlBody = nested
                            } else if (plainBody == null) {
                                plainBody = nested
                            } else if (htmlBody == null) {
                                htmlBody = nested
                            }
                        }
                    }
                }
                htmlBody ?: plainBody ?: ""
            } else {
                part.content.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun saveAttachment(part: javax.mail.Part, sortOrder: Int): Attachment? {
        return try {
            val mimeType = part.contentType?.substringBefore(";")?.trim()?.lowercase() ?: "application/octet-stream"
            val contentId = part.getHeader("Content-ID")?.firstOrNull()
                ?.trim()?.removeSurrounding("<", ">") ?: ""
            val partFileName = part.fileName

            if (mimeType.startsWith("image/")) {
                com.mailchat.ai.AppLogger.log(context, "saveAttachment: image part, mime=$mimeType, contentId=$contentId, partFileName=$partFileName")
            }

            // part.fileName kann "inline"/"attachment" (Disposition-Wert) statt echten Namen liefern
            val validPartFileName = if (partFileName != null &&
                partFileName.isNotBlank() &&
                partFileName.lowercase() != "inline" &&
                partFileName.lowercase() != "attachment" &&
                partFileName.contains('.')
            ) partFileName else null

            val rawFileName = validPartFileName
                ?: if (mimeType.startsWith("image/") && contentId.isNotBlank()) contentId
                else return null
            val isEncryptedExt = com.mailchat.crypto.CryptoService.isEncryptedFile(rawFileName) // .enc/.asc/.gpg
            val fileName = when {
                rawFileName.endsWith(".gpg", ignoreCase = true) -> rawFileName.removeSuffix(".gpg")
                rawFileName.endsWith(".asc", ignoreCase = true) -> rawFileName.removeSuffix(".asc")
                isEncryptedExt -> com.mailchat.crypto.CryptoService.getOriginalFileName(rawFileName)
                else -> rawFileName
            }
            val safeName = fileName.sanitizeFileName()

            val isImage = mimeType.startsWith("image/")

            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val tempFile = File(dir, "${System.currentTimeMillis()}_$safeName")
            part.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            // Detect OpenPGP-encrypted content (armored PGP block) by reading the file head.
            // This catches .asc/.gpg files that are real OpenPGP messages, not just by extension.
            val isPgpContent = try {
                tempFile.inputStream().bufferedReader().use { reader ->
                    val buf = CharArray(500)
                    val read = reader.read(buf)
                    val head = if (read > 0) String(buf, 0, read) else ""
                    head.contains("BEGIN PGP MESSAGE") || head.contains("BEGIN PGP ARMORED")
                }
            } catch (_: Exception) { false }

            val finalFile = when {
                isPgpContent && com.mailchat.crypto.OpenPgpKeyService.hasIdentity(context) -> {
                    // OpenPGP-encrypted file: decrypt with the stored private key.
                    try {
                        val decryptedFile = File(dir, "${System.currentTimeMillis()}_dec_${safeName}")
                        com.mailchat.crypto.OpenPgpKeyService.decryptFile(tempFile, decryptedFile, context)
                        tempFile.delete()
                        decryptedFile
                    } catch (e: Exception) {
                        com.mailchat.ai.AppLogger.log(context, "OpenPGP attachment decrypt failed: ${e.message}")
                        tempFile
                    }
                }
                isPgpContent -> {
                    // OpenPGP file but no identity available: keep the encrypted file as-is.
                    tempFile
                }
                isEncryptedExt -> {
                    // Legacy encrypted file: decrypt with CryptoService.
                    try {
                        val decryptedFile = File(dir, "${System.currentTimeMillis()}_dec_${safeName}")
                        com.mailchat.crypto.CryptoService.decryptFile(tempFile, decryptedFile, context)
                        tempFile.delete()
                        decryptedFile
                    } catch (e: Exception) {
                        com.mailchat.ai.AppLogger.log(context, "Attachment decrypt failed: ${e.message}")
                        tempFile
                    }
                }
                else -> tempFile
            }

            if (contentId.isNotBlank()) {
                com.mailchat.ai.AppLogger.log(context, "CID image saved: $contentId -> ${finalFile.name} (${finalFile.length()} bytes)")
            }
            if (isPgpContent || isEncryptedExt) {
                com.mailchat.ai.AppLogger.log(context, "Encrypted attachment processed: $fileName (${finalFile.length()} bytes) pgp=$isPgpContent")
            }

            Attachment(
                fileName = fileName,
                mimeType = mimeType,
                uri = finalFile.absolutePath,
                isImage = isImage,
                sizeBytes = finalFile.length(),
                sortOrder = sortOrder,
                contentId = contentId
            )
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(context, "saveAttachment error: ${e.message}")
            null
        }
    }
}

class ImapConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)