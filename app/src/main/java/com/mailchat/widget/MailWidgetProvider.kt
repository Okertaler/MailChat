package com.mailchat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.mailchat.MainActivity
import com.mailchat.R
import com.mailchat.data.database.MailchatDatabase
import com.mailchat.data.model.Message
import com.mailchat.util.DateUtils
import com.mailchat.util.SettingsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home-screen widget that shows the latest (or unread) emails. The number of visible emails
 * adapts to the widget's height (the user resizes the widget to show more or fewer emails).
 * Tapping an email opens the chat (default) or the email detail (configurable in settings);
 * tapping the header or the empty view only brings the app to the foreground.
 *
 * The list is built from a STATIC set of item slots (see widget_mail_list.xml) instead of a
 * ListView + RemoteViewsService. Each slot gets its own PendingIntent via setOnClickPendingIntent
 * — the same mechanism as the (working) header. The ListView template + fill-in approach did not
 * merge the per-item extras on the test device, so a static list is used for reliability.
 */
class MailWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /**
     * Called when the widget is first bound and whenever the user resizes it. We simply rebuild
     * the widget — [buildWidget] reads the current height live from the AppWidgetManager and
     * shows as many item slots as fit.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // The widget was resized (or just bound). Rebuild it — buildWidget reads the current
        // height live from the options bundle and shows as many item slots as fit.
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        // Must match the number of item slots in widget_mail_list.xml.
        const val MAX_ITEM_SLOTS = 20

        // Base offset for per-item PendingIntent request codes so they never collide with the
        // header (1), the empty view (0) or the notification request codes (chatId).
        const val REQUEST_CODE_ITEM_BASE = 2_000_000

        // Fallback number of visible slots when the widget height is not known yet.
        const val DEFAULT_VISIBLE_COUNT = 5

        // Layout metrics (dp) used to estimate how many slots fit into the widget height.
        // OVERHEAD_DP = header (icon 22dp + 8dp bottom padding) + root padding (10dp top + 10dp bottom).
        // ITEM_HEIGHT_DP = one email slot (padding + margin + sender row + subject). Calibrated so
        // that ~5 emails fit into the default (2-row) widget height of ~355dp.
        const val OVERHEAD_DP = 50f
        const val ITEM_HEIGHT_DP = 55f

        /**
         * (Re)builds the widget with id [appWidgetId]. Public so the app can refresh it after
         * new emails arrive (e.g. after a sync).
         */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            // Always fetch the maximum number of slots; buildWidget decides how many of them
            // actually fit into the widget's current height.
            val limit = MAX_ITEM_SLOTS
            val contentMode = SettingsUtils.getWidgetContentMode(context)

            // Fetch the emails on a background thread (database access must not block the main
            // thread), then build and apply the widget on the main thread.
            CoroutineScope(Dispatchers.Main.immediate).launch {
                val messages = withContext(Dispatchers.IO) {
                    try {
                        val db = MailchatDatabase.getInstance(context)
                        if (contentMode == SettingsUtils.WIDGET_CONTENT_UNREAD) {
                            db.messageDao().getUnreadMessages(limit)
                        } else {
                            db.messageDao().getLatestMessages(limit)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                try {
                    buildWidget(context, appWidgetManager, appWidgetId, messages)
                    android.util.Log.i("MailchatWidget", "buildWidget OK: ${messages.size} messages, appWidgetId=$appWidgetId")
                } catch (e: Exception) {
                    android.util.Log.e("MailchatWidget", "buildWidget FAILED", e)
                }
            }
        }

        private fun buildWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            messages: List<Message>
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_mail_list)

            // Background transparency (user-configurable, in percent). The drawables are
            // pre-generated in 10% steps (widget_bg_0 … widget_bg_100).
            val transparency = SettingsUtils.getWidgetTransparency(context)
            val bgRes = when (transparency / 10) {
                0 -> R.drawable.widget_bg_0
                1 -> R.drawable.widget_bg_10
                2 -> R.drawable.widget_bg_20
                3 -> R.drawable.widget_bg_30
                4 -> R.drawable.widget_bg_40
                5 -> R.drawable.widget_bg_50
                6 -> R.drawable.widget_bg_60
                7 -> R.drawable.widget_bg_70
                8 -> R.drawable.widget_bg_80
                9 -> R.drawable.widget_bg_90
                else -> R.drawable.widget_bg_100
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            // User-configurable font size (small / normal / large) — a scale factor applied
            // onto the base sp sizes defined in widget_mail_list.xml.
            val fontScale = SettingsUtils.getWidgetFontScale(context)
            fun scaledSp(baseSp: Int): Float = (baseSp * fontScale).toFloat()

            // Empty view text depends on the content mode (latest vs. unread).
            val contentMode = SettingsUtils.getWidgetContentMode(context)
            views.setTextViewText(
                R.id.widget_empty,
                context.getString(
                    if (contentMode == SettingsUtils.WIDGET_CONTENT_UNREAD)
                        R.string.widget_no_unread_emails
                    else
                        R.string.widget_no_emails
                )
            )
            views.setTextViewTextSize(R.id.widget_empty, android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(13))

            // Tapping the header only brings the app to the foreground (NEW_TASK + CLEAR_TOP
            // reuse the existing task instead of starting a second instance).
            val headerIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val headerPendingIntent = PendingIntent.getActivity(
                context, 1, headerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, headerPendingIntent)

            // Tapping the empty view only brings the app to the foreground.
            val emptyIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val emptyPendingIntent = PendingIntent.getActivity(
                context, 0, emptyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_empty, emptyPendingIntent)

            // Show the empty view only when there are no emails.
            views.setViewVisibility(R.id.widget_empty, if (messages.isEmpty()) View.VISIBLE else View.GONE)

            // Populate the static item slots. Each slot gets its own PendingIntent (the same
            // mechanism as the header, which is known to work). The number of visible slots
            // depends on the widget's current height (responsive, no user setting). The height
            // is read live from the AppWidgetManager so it is correct for existing widgets too
            // (onAppWidgetOptionsChanged only fires on bind/resize).
            val openDetail = SettingsUtils.getWidgetOpenMode(context) == SettingsUtils.WIDGET_MODE_DETAIL
            // Height resolution: this launcher reports the CURRENT widget size as
            // appWidgetMaxHeight / appWidgetMinHeight (both equal to the current size) and in
            // appWidgetSizes. We read it live from the options bundle so it is always up to date
            // (no persistence needed). Fall back to a sensible default when unknown.
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            // This launcher reports the current widget size in dp (appWidgetMaxHeight /
            // appWidgetMinHeight, both equal to the current size). We work in dp directly.
            val heightDp = (options?.getInt("appWidgetMaxHeight", 0) ?: 0)
                .takeIf { it > 0 }
                ?: (options?.getInt("appWidgetMinHeight", 0) ?: 0)
            val fitCount = computeVisibleCount(heightDp)
            val showCount = minOf(messages.size, fitCount, MAX_ITEM_SLOTS)
            android.util.Log.i("MailchatWidget", "buildWidget: heightDp=$heightDp fitCount=$fitCount showCount=$showCount appWidgetId=$appWidgetId")
            for (i in 0 until MAX_ITEM_SLOTS) {
                val rootId = slotId(context, "widget_item", i)
                if (i < showCount) {
                    val message = messages[i]
                    val sender = if (message.isIncoming) message.from else context.getString(R.string.you)
                    val subject = message.subject.ifBlank { message.body.take(80) }
                    views.setTextViewText(slotId(context, "widget_sender", i), sender)
                    views.setTextViewText(slotId(context, "widget_subject", i), subject)
                    views.setTextViewText(slotId(context, "widget_time", i), DateUtils.formatRelative(context, message.timestamp))
                    views.setTextViewTextSize(slotId(context, "widget_sender", i), android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(12))
                    views.setTextViewTextSize(slotId(context, "widget_subject", i), android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(11))
                    views.setTextViewTextSize(slotId(context, "widget_time", i), android.util.TypedValue.COMPLEX_UNIT_SP, scaledSp(10))
                    views.setViewVisibility(rootId, View.VISIBLE)

                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("chat_id", message.chatId)
                        if (openDetail) putExtra("message_id", message.id)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        REQUEST_CODE_ITEM_BASE + i,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(rootId, pendingIntent)
                } else {
                    views.setViewVisibility(rootId, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Estimates how many item slots fit into a widget of [heightDp] dp. The estimate is
         * derived from the fixed item layout in widget_mail_list.xml (each slot is ~55dp tall
         * including padding and margin; the header plus the root padding take ~50dp). Returns a
         * sensible default when the height is unknown (0).
         */
        private fun computeVisibleCount(heightDp: Int): Int {
            if (heightDp <= 0) return DEFAULT_VISIBLE_COUNT
            val count = (heightDp - OVERHEAD_DP.toInt()) / ITEM_HEIGHT_DP.toInt()
            return count.coerceIn(1, MAX_ITEM_SLOTS)
        }

        /** Resolves the resource id for a generated slot view (e.g. widget_item_3). */
        private fun slotId(context: Context, prefix: String, index: Int): Int =
            context.resources.getIdentifier("${prefix}_$index", "id", context.packageName)

        /** Refreshes all instances of the widget (called after new emails are fetched). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MailWidgetProvider::class.java))
            android.util.Log.i("MailchatWidget", "refreshAll: ids=${ids.toList()}")
            if (ids.isNotEmpty()) {
                for (id in ids) updateWidget(context, manager, id)
            }
        }
    }
}
