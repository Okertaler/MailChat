package com.mailchat.util

import android.content.Context
import com.mailchat.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

object DateUtils {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d. MMM yyyy", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("d. MMMM yyyy, HH:mm", Locale.getDefault())

    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

    fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

    fun formatFull(timestamp: Long): String = fullFormat.format(Date(timestamp))

    fun formatRelative(context: Context, timestamp: Long): String {
        // Compare calendar dates (not raw milliseconds) so the result is correct
        // regardless of the time of day and daylight-saving transitions. The old
        // implementation divided the millisecond difference by 24 h, which was off
        // by one day near midnight and across DST changes.
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val then = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val diffDays = ChronoUnit.DAYS.between(then, today).toInt()
        val time = formatTime(timestamp)

        return when {
            diffDays < 0 -> time
            diffDays == 0 -> "${context.getString(R.string.today)} $time"
            diffDays == 1 -> "${context.getString(R.string.yesterday)} $time"
            diffDays == 2 -> "${context.getString(R.string.day_before_yesterday)} $time"
            diffDays < 7 -> context.getString(R.string.days_ago, diffDays)
            else -> formatDate(timestamp)
        }
    }
}
