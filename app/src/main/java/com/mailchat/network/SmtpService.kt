package com.mailchat.network

import android.content.Context
import com.mailchat.data.model.EmailAccount
import com.mailchat.util.SettingsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.inject.Inject
import javax.inject.Singleton

/** How the email body should be transmitted. */
enum class BodyFormat {
    /** Plain text, sent as-is (text/plain). Used for encrypted bodies and the SMTP test. */
    PLAIN,

    /** Plain text that is rendered as simple HTML (paragraphs, line breaks) before sending. */
    PLAIN_AS_HTML,

    /** The body is already HTML (rich-text editor output) — sent as-is (text/html). */
    HTML
}

@Singleton
class SmtpService @Inject constructor() {

    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context
    }

    private fun resolveSmtpHost(account: EmailAccount): Pair<String, Int> {
        if (account.smtpHost.isNotBlank() && account.smtpPort > 0) {
            return account.smtpHost to account.smtpPort
        }
        val context = appContext
        if (context != null) {
            val provider = SettingsUtils.getSmtpProvider(context)
            if (provider.isNotBlank()) {
                return when (provider) {
                    "GMX" -> "mail.gmx.net" to 465
                    "Gmail" -> "smtp.gmail.com" to 587
                    "Web.de" -> "smtp.web.de" to 465
                    "T-Online" -> "smtp.t-online.de" to 465
                    "Outlook" -> "smtp-mail.outlook.com" to 587
                    else -> account.imapHost to account.smtpPort
                }
            }
        }
        return account.smtpHost.ifBlank { account.imapHost } to account.smtpPort
    }

    suspend fun sendEmail(
        account: EmailAccount,
        to: String,
        subject: String,
        body: String,
        attachments: List<File> = emptyList(),
        extraHeaders: Map<String, String> = emptyMap(),
        cc: String = "",
        bcc: String = "",
        bodyFormat: BodyFormat = BodyFormat.PLAIN
    ): String = withContext(Dispatchers.IO) {
        val (host, port) = resolveSmtpHost(account)
        val properties = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.port", port.toString())
                put("mail.smtp.socketFactory.fallback", "false")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "30000")
        }

        val session = Session.getInstance(
            properties,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(account.username, account.passwordEncrypted)
            }
        )

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(account.emailAddress))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            if (cc.isNotBlank()) setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc))
            if (bcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc))
            setSubject(subject, "UTF-8")
            extraHeaders.forEach { (name, value) -> setHeader(name, value) }

            // Body handling depends on the format:
            //  - PLAIN: sent as-is as text/plain (encrypted bodies MUST stay plain text,
            //    as does the SMTP connection test).
            //  - PLAIN_AS_HTML: plain text rendered as simple HTML so line breaks, the
            //    signature and section spacing display cleanly in the recipient's client.
            //  - HTML: the body is already HTML (rich-text editor) — wrapped in a minimal
            //    document (if needed) and sent as text/html.
            val (bodyToSend, contentType) = when (bodyFormat) {
                BodyFormat.PLAIN -> body to "text/plain"
                BodyFormat.PLAIN_AS_HTML -> plainTextToHtml(body) to "text/html"
                BodyFormat.HTML -> wrapHtmlDocument(body) to "text/html"
            }
            if (attachments.isNotEmpty()) {
                val multipart = MimeMultipart()
                val textPart = MimeBodyPart()
                textPart.setContent(bodyToSend, "$contentType; charset=UTF-8")
                multipart.addBodyPart(textPart)
                for (file in attachments) {
                    val part = MimeBodyPart()
                    part.attachFile(file)
                    val fileName = ensureFileSuffix(file)
                    part.setFileName(fileName)
                    multipart.addBodyPart(part)
                }
                setContent(multipart)
            } else {
                setContent(bodyToSend, "$contentType; charset=UTF-8")
            }
        }

        message.saveChanges()
        Transport.send(message)
        message.messageID ?: ""
    }

    /**
     * Converts a plain-text email body into simple, clean HTML: HTML special characters are
     * escaped, blank lines become paragraph breaks and single line breaks become <br>. A minimal
     * inline style keeps the rendering consistent across clients.
     */
    private fun plainTextToHtml(text: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val paragraphs = escaped
            .split(Regex("\n{2,}"))
            .filter { it.isNotBlank() }
            .joinToString("</p>\n<p>") { it.replace("\n", "<br>") }
        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"></head>" +
            "<body style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;color:#222222;\">" +
            "<p>$paragraphs</p></body></html>"
    }

    /**
     * Wraps an HTML fragment (rich-text editor output) in a minimal document with a base style,
     * so the email renders consistently across clients. Full documents are returned unchanged.
     */
    private fun wrapHtmlDocument(fragment: String): String {
        if (fragment.contains("<html", ignoreCase = true)) return fragment
        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"></head>" +
            "<body style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;color:#222222;\">" +
            fragment + "</body></html>"
    }

    private fun ensureFileSuffix(file: File): String {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < name.length - 1) {
            name
        } else {
            val ext = extensionForFile(file)
            if (ext.isBlank()) name else "$name.$ext"
        }
    }

    private fun extensionForFile(file: File): String {
        return try {
            val type = java.net.URLConnection.guessContentTypeFromName(file.name)
            extensionForMime(type)
        } catch (e: Exception) {
            ""
        }
    }

    private fun extensionForMime(mime: String?): String = when {
        mime == null -> ""
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("png") -> "png"
        mime.contains("gif") -> "gif"
        mime.contains("webp") -> "webp"
        mime.contains("pdf") -> "pdf"
        mime.contains("msword") -> "doc"
        mime.contains("ms-excel") -> "xls"
        mime.contains("mspowerpoint") -> "ppt"
        mime.contains("zip") -> "zip"
        mime.contains("plain") -> "txt"
        mime.contains("html") -> "html"
        else -> ""
    }

    companion object {
        /** Escapes HTML special characters so plain text can be embedded safely in an HTML body. */
        fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        /**
         * Heuristic: does the text contain HTML markup produced by the rich-text editor
         * (formatting tags, lists, line breaks)? Used to decide whether a compose body is
         * already HTML or still plain text.
         */
        fun looksLikeHtml(text: String): Boolean =
            Regex("<\\s*(b|i|u|div|span|ul|ol|li|p|br|font|table|style)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
    }
}
