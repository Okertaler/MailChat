package com.mailchat.util

import com.mailchat.data.model.Chat

object EmailGrouping {

    const val NO_REPLY_AUTO = 0
    const val NO_REPLY_PROHIBIT = 1
    const val NO_REPLY_ALLOW = 2

    fun isNoReply(chat: Chat): Boolean = when (chat.noReply) {
        NO_REPLY_PROHIBIT -> true
        NO_REPLY_ALLOW -> false
        else -> isNoReplyEmail(chat.participantEmail)
    }

    fun normalize(email: String): String =
        email.trim().lowercase()

    fun displayName(personal: String, email: String): String = email

    fun groupDisplayName(emails: List<String>): String =
        emails.map { it.substringBefore('@') }.joinToString(", ")

    fun isGroupKey(key: String): Boolean = key.contains(";")

    private val noReplyPatterns = listOf(
        "noreply", "no-reply", "no_reply", "noresponse", "no-response", "no_response",
        "keine-antwort", "keineantwort", "keine_antwort",
        "nichtantworten", "nicht-antworten", "nicht_antworten",
        "donotreply", "do-not-reply", "do_not_reply",
        "postmaster", "mailer-daemon", "mailerdaemon"
    )

    fun isNoReplyEmail(email: String): Boolean {
        val localPart = email.substringBefore('@').lowercase()
        return noReplyPatterns.any { localPart.startsWith(it) || localPart.contains(it) }
    }
}