package com.mailchat.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.mailchat.data.model.Chat
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.repository.AccountRepository
import com.mailchat.data.repository.ChatRepository
import com.mailchat.data.repository.MessageRepository
import com.mailchat.data.repository.PollRepository
import com.mailchat.network.FetchedMessage
import com.mailchat.network.ImapService
import com.mailchat.util.EmailGrouping
import com.mailchat.util.NotificationHelper
import com.mailchat.util.SettingsUtils
import com.mailchat.util.normalizeWhitespace
import com.mailchat.util.stripHtml
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val imapService: ImapService,
    private val accountRepository: AccountRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val pollRepository: PollRepository,
    private val syncEngine: SyncEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        com.mailchat.ai.AppLogger.log(applicationContext, "SyncWorker doWork: started")
        val accounts = accountRepository.getAllAccounts().first()
        if (accounts.isEmpty()) return Result.success()
        // Skip the redundant full sync if a sync ran very recently (the IMAP push service polls
        // every 60s). Without this, the push service and this worker run syncAll in parallel —
        // double the IMAP connections, double the traffic, double the battery drain. If no
        // recent sync happened (push service stopped or stuck), this worker is the fallback and
        // runs the full sync.
        val lastSync = accounts.maxOfOrNull { it.lastSyncTimestamp } ?: 0L
        val sinceLastSync = System.currentTimeMillis() - lastSync
        val result = if (sinceLastSync in 0..RECENT_SYNC_THRESHOLD_MS) {
            com.mailchat.ai.AppLogger.log(
                applicationContext,
                "SyncWorker: recent sync ${sinceLastSync / 1000}s ago — skipping redundant syncAll (maintenance only)"
            )
            SyncEngine.SyncResult(emptyList(), 0, 0)
        } else {
            syncEngine.syncAll()
        }
        // Periodic-sync-only maintenance passes (CID image repair, missing-attachment repair,
        // embeddings).
        for (account in accounts) {
            try {
                repairCidImages(account)
            } catch (e: Exception) {
                android.util.Log.w("MailchatSync", "cid repair pass failed", e)
            }
            try {
                repairMissingAttachments(account)
            } catch (e: Exception) {
                android.util.Log.w("MailchatSync", "attach repair pass failed", e)
            }
        }
        pollReminderPass(accounts)
        embedPendingMessages()
        return if (result.failedAccounts > 0 && result.failedAccounts == result.totalAccounts) {
            Result.retry()
        } else {
            if (result.errorDetails.isNotEmpty()) {
                NotificationHelper.postSyncError(
                    context = applicationContext,
                    message = result.errorDetails.joinToString("; ").take(300)
                )
            }
            Result.success()
        }
    }

    private suspend fun repairCidImages(account: EmailAccount) {
        try {
            val broken = messageRepository.getBrokenCidMessages(account.id)
            // Skip messages that were already attempted and could not be fixed (the server copy
            // has no content-ID mapping either). Without this, the same messages would be
            // re-fetched from the server on EVERY periodic sync forever.
            val failedIds = SettingsUtils.getCidRepairFailedMessageIds(applicationContext)
            val toCheck = broken.filter { it.messageId !in failedIds }
            if (toCheck.isEmpty()) return
            com.mailchat.ai.AppLogger.log(applicationContext, "CID repair: ${toCheck.size} messages to check")
            for (msg in toCheck) {
                try {
                    val fetched = imapService.fetchMessageByMessageId(account, msg.messageId) ?: continue
                    if (fetched.attachments.none { it.contentId.isNotBlank() }) {
                        // The server copy cannot fix this message — remember it so it is not
                        // re-fetched on every sync.
                        SettingsUtils.addCidRepairFailedMessageId(applicationContext, msg.messageId)
                        continue
                    }
                    messageRepository.deleteAttachmentsByMessage(msg.id)
                    for (att in fetched.attachments) {
                        messageRepository.insertAttachment(att.copy(messageId = msg.id))
                    }
                    messageRepository.updateMessage(
                        msg.copy(bodyFull = fetched.message.bodyFull, body = fetched.message.body)
                    )
                    messageRepository.deleteEmbeddingForMessage(msg.id)
                    com.mailchat.ai.AppLogger.log(applicationContext, "CID repair OK: ${msg.subject}")
                } catch (e: Exception) {
                    android.util.Log.w("MailchatSync", "cid repair failed for ${msg.messageId}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MailchatSync", "cid repair pass failed", e)
        }
    }

    /**
     * Repairs attachment rows whose file is missing on disk. This happens after a backup restore
     * that did not include the attachment files (the DB rows survive, the files do not).
     *
     * Two strategies, in order:
     *  1. Local re-link: a previous sync already downloaded the file (orphan file in the
     *     attachments directory). Point the row at it — no network needed.
     *  2. Re-fetch: for the rest, fetch the message by its Message-ID and re-link the rows to
     *     the freshly downloaded files (this is what recovers inline/CID images, which have no
     *     orphan file). Capped per run to keep the IMAP load reasonable.
     */
    private suspend fun repairMissingAttachments(account: EmailAccount) {
        try {
            val attachments = messageRepository.getAttachmentsByAccount(account.id)
            if (attachments.isEmpty()) return
            val attDir = java.io.File(applicationContext.filesDir, "attachments")
            val orphanFiles = if (attDir.exists()) attDir.listFiles()?.toList() ?: emptyList() else emptyList()

            val refetchMessageIds = linkedSetOf<Long>()
            var localRelinked = 0
            for (att in attachments) {
                if (java.io.File(att.uri).exists()) continue
                val matched = findOrphanFile(att, orphanFiles)
                if (matched != null) {
                    messageRepository.updateAttachmentUri(att.id, matched.absolutePath)
                    localRelinked++
                } else {
                    refetchMessageIds.add(att.messageId)
                }
            }
            if (localRelinked > 0) {
                com.mailchat.ai.AppLogger.log(applicationContext, "ATTACH repair: $localRelinked re-linked locally")
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
                        if (java.io.File(stored.uri).exists()) continue
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
                    android.util.Log.w("MailchatSync", "attach refetch failed for ${message.messageId}", e)
                }
            }
            if (refetched > 0) {
                com.mailchat.ai.AppLogger.log(applicationContext, "ATTACH repair: $refetched messages re-fetched")
            }
        } catch (e: Exception) {
            android.util.Log.w("MailchatSync", "attach repair pass failed", e)
        }
    }

    /**
     * Finds an orphan file in the attachments directory that matches [att] by file name (or
     * content ID as a last resort). Prefers a size match, then the most recently modified file.
     */
    private fun findOrphanFile(att: com.mailchat.data.model.Attachment, orphanFiles: List<java.io.File>): java.io.File? {
        val searchName = att.fileName.ifBlank { att.contentId }
        if (searchName.isBlank()) return null
        val matches = orphanFiles.filter { it.name.endsWith(searchName, ignoreCase = true) }
        if (matches.isEmpty()) return null
        return matches.sortedWith(
            compareByDescending<java.io.File> { it.length() == att.sizeBytes }
                .thenByDescending { it.lastModified() }
        ).first()
    }

    private suspend fun embedPendingMessages() {
        try {
            if (!SettingsUtils.getAiAutoProcess(applicationContext)) return
            val embeddingModel = com.mailchat.ai.GeminiService.getEmbeddingModel()
            if (!com.mailchat.ai.GeminiService.isModelDownloaded(applicationContext, embeddingModel.id)) return
            val periodDays = SettingsUtils.getSyncPeriodDays(applicationContext)
            val since = System.currentTimeMillis() - periodDays * 24L * 60L * 60L * 1000L
            val pending = messageRepository.getMessagesNeedingEmbedding(since, 50)
            if (pending.isEmpty()) return
            com.mailchat.ai.AppLogger.log(applicationContext, "Embeddings: ${pending.size} messages to embed")
            val texts = pending.map { "${it.subject}\n${it.bodyFull.stripHtml().normalizeWhitespace()}" }
            val vectors = try {
                com.mailchat.ai.EmbeddingService.embedBatch(applicationContext, texts)
            } finally {
                com.mailchat.ai.EmbeddingService.close()
            }
            if (vectors.size != pending.size) return
            vectors.forEachIndexed { i, vec ->
                messageRepository.upsertEmbedding(
                    com.mailchat.data.model.MessageEmbedding(pending[i].id, toBytes(vec))
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MailchatSync", "embedding pass failed", e)
        }
    }

    private fun toBytes(vec: FloatArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(vec.size * 4)
        vec.forEach { buf.putFloat(it) }
        return buf.array()
    }

    /**
     * Reminds the user about open polls that were sent to them and have not been answered yet
     * (one notification per poll, deduped via [SettingsUtils.getPollReminderSentIds]). Runs on
     * every periodic sync — it is a local DB pass only, no network involved.
     */
    private suspend fun pollReminderPass(accounts: List<EmailAccount>) {
        try {
            // The channel is normally created in MainActivity; ensure it exists here too so the
            // reminder works even if the app has not been opened since the last install.
            NotificationHelper.createChannel(applicationContext)
            for (account in accounts) {
                val myEmail = account.emailAddress.trim().lowercase()
                if (myEmail.isBlank()) continue
                val openPolls = pollRepository.getOpenPolls(account.id)
                if (openPolls.isEmpty()) continue
                val sent = SettingsUtils.getPollReminderSentIds(applicationContext)
                for (poll in openPolls) {
                    // Only remind for polls addressed to this account (not the creator's own).
                    if (poll.creatorEmail.trim().lowercase() == myEmail) continue
                    if (poll.id in sent) continue
                    val votes = pollRepository.getVotesSync(poll.id, account.id)
                    if (votes.any { it.voterEmail.trim().lowercase() == myEmail }) continue
                    NotificationHelper.postPollReminder(
                        context = applicationContext,
                        chatId = poll.chatId,
                        pollId = poll.id,
                        title = poll.title,
                        question = poll.question
                    )
                    SettingsUtils.addPollReminderSentId(applicationContext, poll.id)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MailchatSync", "poll reminder pass failed", e)
        }
    }

    companion object {
        const val WORK_NAME = "mailchat_periodic_sync"

        /** If a sync ran within this window, the periodic worker skips its own syncAll (the
         *  IMAP push service already polls every 60s). 3 min is well above the 60s poll and
         *  well below the 15 min worker interval. */
        private const val RECENT_SYNC_THRESHOLD_MS = 3 * 60 * 1000L

        fun schedule(context: Context, intervalMinutes: Int) {
            val workManager = WorkManager.getInstance(context)
            if (intervalMinutes <= 0) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val effectiveInterval = intervalMinutes.coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                effectiveInterval.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            com.mailchat.ai.AppLogger.log(context, "SyncWorker scheduled: every $effectiveInterval min")
        }

        fun getPreferredInterval(context: Context): Int {
            val interval = com.mailchat.util.SettingsUtils.getSyncIntervalMinutes(context)
            return if (interval > 0) interval else 15
        }

        fun runImmediateSync(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                "mailchat_immediate_sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun resetSync(context: Context) {
            kotlinx.coroutines.MainScope().launch {
                val db = com.mailchat.data.database.MailchatDatabase.getInstance(context)
                db.syncStateDao().deleteAll()
                runImmediateSync(context)
            }
        }
    }
}
