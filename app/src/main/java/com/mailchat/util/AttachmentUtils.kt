package com.mailchat.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mailchat.data.model.Attachment
import java.io.File

object AttachmentUtils {
    fun openAttachment(context: Context, attachment: Attachment) {
        try {
            val originalFile = File(attachment.uri)
            if (!originalFile.exists()) return
            var mime = normalizeMime(attachment.mimeType)
            if (mime == "*/*" || mime == "application/octet-stream") {
                mime = detectMimeFromFile(originalFile)
            }
            // Dateiendung hat Vorrang vor kryptischen IMAP-MIME-Typen (z.B. x-zip-compressed)
            val finalMime = when {
                originalFile.name.endsWith(".zip", ignoreCase = true) -> "application/zip"
                originalFile.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                originalFile.name.endsWith(".txt", ignoreCase = true) -> "text/plain"
                originalFile.name.endsWith(".jpg", ignoreCase = true) || originalFile.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                originalFile.name.endsWith(".png", ignoreCase = true) -> "image/png"
                originalFile.name.endsWith(".gif", ignoreCase = true) -> "image/gif"
                originalFile.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                originalFile.name.endsWith(".doc", ignoreCase = true) -> "application/msword"
                originalFile.name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                originalFile.name.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
                originalFile.name.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                originalFile.name.endsWith(".ppt", ignoreCase = true) -> "application/vnd.ms-powerpoint"
                originalFile.name.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                originalFile.name.endsWith(".odt", ignoreCase = true) -> "application/vnd.oasis.opendocument.text"
                originalFile.name.endsWith(".ods", ignoreCase = true) -> "application/vnd.oasis.opendocument.spreadsheet"
                originalFile.name.endsWith(".odp", ignoreCase = true) -> "application/vnd.oasis.opendocument.presentation"
                originalFile.name.endsWith(".csv", ignoreCase = true) -> "text/csv"
                originalFile.name.endsWith(".html", ignoreCase = true) || originalFile.name.endsWith(".htm", ignoreCase = true) -> "text/html"
                originalFile.name.endsWith(".xml", ignoreCase = true) -> "application/xml"
                originalFile.name.endsWith(".json", ignoreCase = true) -> "application/json"
                originalFile.name.endsWith(".java", ignoreCase = true) || originalFile.name.endsWith(".kt", ignoreCase = true) ||
                originalFile.name.endsWith(".py", ignoreCase = true) || originalFile.name.endsWith(".js", ignoreCase = true) ||
                originalFile.name.endsWith(".ts", ignoreCase = true) || originalFile.name.endsWith(".c", ignoreCase = true) ||
                originalFile.name.endsWith(".cpp", ignoreCase = true) || originalFile.name.endsWith(".h", ignoreCase = true) ||
                originalFile.name.endsWith(".cs", ignoreCase = true) || originalFile.name.endsWith(".go", ignoreCase = true) ||
                originalFile.name.endsWith(".rs", ignoreCase = true) || originalFile.name.endsWith(".rb", ignoreCase = true) ||
                originalFile.name.endsWith(".php", ignoreCase = true) || originalFile.name.endsWith(".css", ignoreCase = true) ||
                originalFile.name.endsWith(".yaml", ignoreCase = true) || originalFile.name.endsWith(".yml", ignoreCase = true) ||
                originalFile.name.endsWith(".sh", ignoreCase = true) || originalFile.name.endsWith(".bat", ignoreCase = true) ||
                originalFile.name.endsWith(".sql", ignoreCase = true) || originalFile.name.endsWith(".swift", ignoreCase = true) ||
                originalFile.name.endsWith(".m", ignoreCase = true) || originalFile.name.endsWith(".r", ignoreCase = true) ||
                originalFile.name.endsWith(".lua", ignoreCase = true) || originalFile.name.endsWith(".pl", ignoreCase = true) ||
                originalFile.name.endsWith(".scala", ignoreCase = true) || originalFile.name.endsWith(".dart", ignoreCase = true) ||
                originalFile.name.endsWith(".vue", ignoreCase = true) || originalFile.name.endsWith(".svelte", ignoreCase = true) ||
                originalFile.name.endsWith(".toml", ignoreCase = true) || originalFile.name.endsWith(".ini", ignoreCase = true) ||
                originalFile.name.endsWith(".log", ignoreCase = true) -> "text/plain"
                else -> mime
            }

            val file = ensureExtension(originalFile, finalMime, attachment.fileName)
            openFile(context, file, finalMime)
        } catch (e: Exception) {
            android.util.Log.w("MailchatAttach", "open failed", e)
        }
    }

    /** Oeffnet eine lokale Datei: VIEW mit passendem MIME -> octet-stream -> Sharing-Auswahl. */
    fun openFile(context: Context, file: File, mime: String? = null) {
        try {
            val finalMime = mime ?: when {
                file.name.endsWith(".zip", true) -> "application/zip"
                file.name.endsWith(".pdf", true) -> "application/pdf"
                file.name.endsWith(".txt", true) -> "text/plain"
                else -> detectMimeFromFile(file)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            for (t in listOf(finalMime, "application/octet-stream")) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, t)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, null))
                    return
                } catch (_: android.content.ActivityNotFoundException) {
                }
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null))
        } catch (e: Exception) {
            android.util.Log.w("MailchatAttach", "open failed", e)
        }
    }

    private fun ensureExtension(file: File, mime: String, originalName: String): File {
        val ext = extensionForMime(mime)
        if (ext.isBlank()) return file
        if (file.name.endsWith(".$ext", ignoreCase = true)) return file
        if (originalName.endsWith(".$ext", ignoreCase = true)) return file
        val tempDir = file.parentFile
        val tempFile = File(tempDir, "${file.name}.$ext")
        if (!tempFile.exists()) {
            file.copyTo(tempFile, overwrite = false)
        }
        return tempFile
    }

    private fun extensionForMime(mime: String): String {
        return when (mime.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/html" -> "html"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/zip" -> "zip"
            else -> ""
        }
    }

    private fun detectMimeFromFile(file: File): String {
        return try {
            val bytes = file.inputStream().use { it.readNBytes(8) }
            when {
                bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() -> "application/pdf"
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                    bytes[2] == 0xFF.toByte() -> "image/jpeg"
                bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
                bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                    bytes[2] == 0x46.toByte() -> "image/gif"
                bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() -> "application/zip"
                bytes.size >= 2 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() -> "application/msword"
                else -> "*/*"
            }
        } catch (_: Exception) {
            "*/*"
        }
    }

    fun fileIconType(mime: String, fileName: String = ""): String {
        if (fileName.isNotBlank()) {
            val dotIndex = fileName.lastIndexOf('.')
            if (dotIndex > 0 && dotIndex < fileName.length - 1) {
                val ext = fileName.substring(dotIndex + 1).uppercase()
                if (ext.length in 1..4) return ext
            }
        }
        val lower = mime.lowercase()
        return when {
            lower.contains("pdf") -> "PDF"
            lower.contains("jpeg") || lower.contains("jpg") -> "JPG"
            lower.contains("png") -> "PNG"
            lower.contains("gif") -> "GIF"
            lower.contains("webp") -> "WEBP"
            lower.contains("msword") -> "DOC"
            lower.contains("ms-excel") -> "XLS"
            lower.contains("mspowerpoint") -> "PPT"
            lower.contains("zip") -> "ZIP"
            lower.contains("plain") -> "TXT"
            lower.contains("html") -> "HTML"
            else -> "FILE"
        }
    }

    private fun normalizeMime(mime: String): String {
        val lower = mime.lowercase()
        return when {
            lower.contains("pdf") -> "application/pdf"
            lower.contains("jpeg") || lower.contains("jpg") -> "image/jpeg"
            lower.contains("png") -> "image/png"
            lower.contains("gif") -> "image/gif"
            lower.contains("webp") -> "image/webp"
            lower.contains("zip") -> "application/zip"
            lower.isBlank() -> "*/*"
            else -> mime
        }
    }
}
