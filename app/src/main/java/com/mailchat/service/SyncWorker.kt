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
    private val syncEngine: SyncEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        com.mailchat.ai.AppLogger.log(applicationContext, "SyncWorker doWork: started")
        val accounts = accountRepository.getAllAccounts().first()
        if (accounts.isEmpty()) return Result.success()
        val result = syncEngine.syncAll()
        // Periodic-sync-only maintenance passes (CID image repair, embeddings).
        for (account in accounts) {
            try {
                repairCidImages(account)
            } catch (e: Exception) {
                android.util.Log.w("MailchatSync", "cid repair pass failed", e)
            }
        }
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
            if (broken.isEmpty()) return
            com.mailchat.ai.AppLogger.log(applicationContext, "CID repair: ${broken.size} messages to check")
            for (msg in broken) {
                try {
                    val fetched = imapService.fetchMessageByMessageId(account, msg.messageId) ?: continue
                    if (fetched.attachments.none { it.contentId.isNotBlank() }) continue
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

    companion object {
        const val WORK_NAME = "mailchat_periodic_sync"

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
