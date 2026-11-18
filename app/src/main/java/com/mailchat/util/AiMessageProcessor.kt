package com.mailchat.util

import android.content.Context
import com.mailchat.ai.GeminiService
import com.mailchat.data.model.Chat
import com.mailchat.data.model.Message

/**
 * Shared AI message processing: spam classification, custom tag detection and summarization.
 *
 * Used identically by the overview page (chat list), the chat page (chat detail) and the
 * background sync for new messages, so all of them produce the same results — in particular
 * the custom tags, which are detected with the sender and subject as additional context.
 */
object AiMessageProcessor {

    /**
     * Runs the full AI pipeline for [message] and returns the updated message (NOT persisted —
     * the caller stores it).
     *
     * @param forceSummary when true a summary is generated even for short messages (explicit
     *                     user request); otherwise a summary is only generated when one is
     *                     missing and the message is long enough.
     */
    suspend fun processMessage(context: Context, message: Message, forceSummary: Boolean = false): Message {
        // Phishing blacklist: a blacklisted sender is always phishing — skip AI classification
        // so the manual decision is never overridden.
        if (message.isIncoming && SettingsUtils.isPhishingBlacklisted(context, message.from)) {
            return message.copy(isSpam = true, spamCategory = "phishing")
        }
        val originalFull = message.bodyFull.ifBlank { message.body }
        if (originalFull.isBlank()) return message
        val lang = LanguageUtils.getLanguage(context)

        // Spam classification: heuristic + AI (the heuristic can force "phishing").
        val senderDomain = SpamHeuristics.extractDomain(message.from)
        val linkDomains = SpamHeuristics.extractLinkDomains(originalFull)
        val heuristicPhishing = SpamHeuristics.isLikelyPhishing(originalFull, senderDomain, linkDomains)
        val aiSpamCategory = GeminiService.classifySpamCategory(originalFull, senderDomain, linkDomains)
        val spamCategory = if (heuristicPhishing && aiSpamCategory == "phishing") "phishing" else aiSpamCategory

        // Custom tag detection (sender and subject as additional context).
        val customTags = SettingsUtils.getCustomTags(context).filter { it.name.isNotBlank() }
        val customTag = GeminiService.classifyCustomTag(
            originalFull,
            customTags.map { it.name to it.description },
            message.from,
            message.subject
        )

        // Summary: only when missing (or explicitly requested) and the message is long enough.
        val plainLines = originalFull.stripHtml().normalizeWhitespace().lines().count { it.isNotBlank() }
        val shouldSummarize = forceSummary || (message.summary.isBlank() && plainLines > 5)
        val newSummary = if (shouldSummarize) GeminiService.summarize(originalFull, lang) else ""
        val summary = if (newSummary.isNotBlank()) newSummary else message.summary

        return message.copy(
            isSpam = spamCategory != "ham",
            spamCategory = spamCategory,
            summary = summary,
            summaryLanguage = if (newSummary.isNotBlank()) lang else message.summaryLanguage,
            customTag = customTag
        )
    }

    /**
     * Creates a display name (Klarnamen) for [chat] if it doesn't have one yet (i.e. the chat name
     * is still the bare email address). Uses the AI to derive a name from the email address and
     * persists it via [updateChat]. Returns true if a display name was created. The caller must
     * ensure the AI engine is initialized (see [AiEngine.configureAndInitialize]).
     */
    suspend fun ensureDisplayName(chat: Chat, updateChat: suspend (Chat) -> Unit): Boolean {
        if (chat.participantName != chat.participantEmail) return false
        val base = GeminiService.extractDisplayName(chat.participantEmail)
        if (base.isBlank()) return false
        updateChat(chat.copy(participantName = base))
        return true
    }
}
