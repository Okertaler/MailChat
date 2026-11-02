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
        bcc: String = ""
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

            if (attachments.isNotEmpty()) {
                val multipart = MimeMultipart()
                val textPart = MimeBodyPart()
                textPart.setText(body, "UTF-8")
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
                setText(body, "UTF-8")
            }
        }

        message.saveChanges()
        Transport.send(message)
        message.messageID ?: ""
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
}
