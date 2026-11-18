package com.mailchat.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mailchat.MainActivity
import com.mailchat.R

object NotificationHelper {
    private const val CHANNEL_ID = "new_emails"
    private const val SYNC_CHANNEL_ID = "sync_status"
    private const val SYNC_ERROR_NOTIFICATION_ID = 4712
    // Fixed ID for the "N new emails" summary notification, so repeated summaries replace each
    // other instead of stacking up in the notification shade.
    private const val SUMMARY_NOTIFICATION_ID = 9001

    /**
     * A newly received incoming email. Collected during a sync and passed to
     * [postNewEmailNotifications] so the caller can post a single notification (or a summary
     * with a count) at the end instead of one notification per email.
     */
    data class NewEmailInfo(
        val chatId: Long,
        val sender: String,
        val subject: String,
        val preview: String,
        val timestamp: Long
    )

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        val syncChannel = NotificationChannel(
            SYNC_CHANNEL_ID,
            context.getString(R.string.sync_error_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(syncChannel)
    }

    fun postSyncError(context: Context, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SYNC_ERROR_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.sync_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(SYNC_ERROR_NOTIFICATION_ID, notification)
    }

    fun postNewEmail(
        context: Context,
        chatId: Long,
        sender: String,
        subject: String,
        preview: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("chat_id", chatId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender)
            .setContentText(subject.ifBlank { preview })
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(chatId.toInt(), notification)
    }

    /**
     * Posts notifications for a batch of newly received incoming emails (Gmail-style):
     * - 0 emails: nothing
     * - 1 email: a single notification (as before)
     * - 2 or more: ONE summary notification with the count and the most recent emails, so a
     *   bulk import (e.g. selecting a new folder) does not flood the notification shade.
     */
    fun postNewEmailNotifications(context: Context, emails: List<NewEmailInfo>) {
        when {
            emails.isEmpty() -> {}
            emails.size == 1 -> {
                val e = emails[0]
                postNewEmail(context, e.chatId, e.sender, e.subject, e.preview)
            }
            else -> postNewEmailsSummary(context, emails)
        }
    }

    /**
     * A single summary notification for multiple new emails: the title shows the count, the
     * collapsed text shows the most recent email and the expanded view lists the most recent
     * few. Tapping it opens the app (chat list).
     */
    private fun postNewEmailsSummary(context: Context, emails: List<NewEmailInfo>) {
        val count = emails.size
        val sorted = emails.sortedByDescending { it.timestamp }
        val lineFor = { e: NewEmailInfo ->
            val subj = e.subject.ifBlank { e.preview.take(60) }
            "${e.sender}: $subj"
        }
        val title = context.getString(R.string.new_emails_count, count)
        val bigText = sorted.take(5).joinToString("\n") { lineFor(it) }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lineFor(sorted.first()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setNumber(count)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(SUMMARY_NOTIFICATION_ID, notification)
    }
}
