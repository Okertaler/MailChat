package com.mailchat.ai

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var lastClearedDay: String = ""

    fun log(context: Context, message: String) {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "app_log.txt")
            val today = dayFormat.format(Date())
            if (today != lastClearedDay) {
                lastClearedDay = today
                if (file.exists()) file.delete()
            }
            val timestamp = format.format(Date())
            file.appendText("[$timestamp] $message\n")
            val lines = file.readLines()
            if (lines.size > 1000) {
                file.writeText(lines.takeLast(500).joinToString("\n"))
            }
        } catch (_: Exception) {
        }
    }

    fun getLog(context: Context): String {
        return try {
            val file = File(context.filesDir, "logs/app_log.txt")
            if (file.exists()) file.readText() else "(empty)"
        } catch (_: Exception) {
            "(error reading log)"
        }
    }

    fun getLogFile(context: Context): File? {
        return try {
            val file = File(context.filesDir, "logs/app_log.txt")
            if (file.exists() && file.length() > 0) file else null
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, "logs/app_log.txt").delete()
        } catch (_: Exception) {
        }
    }
}
