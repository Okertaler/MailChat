package com.mailchat.util

import android.content.Context

object SettingsUtils {

    private const val PREFS = "mailchat_prefs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_MAX_EMAIL_SIZE_MB = "max_email_size_mb"
    private const val KEY_SYNC_PERIOD_DAYS = "sync_period_days"
    private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
    private const val KEY_SIGNATURE = "signature"

    const val DARK_MODE_SYSTEM = "system"
    const val DARK_MODE_LIGHT = "light"
    const val DARK_MODE_DARK = "dark"

    const val DEFAULT_MAX_EMAIL_SIZE_MB = 5
    const val DEFAULT_SYNC_PERIOD_DAYS = 30
    const val DEFAULT_SYNC_INTERVAL_MINUTES = 15

    fun getActiveChatId(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("active_chat_id", 0L)

    fun setActiveChatId(context: Context, chatId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("active_chat_id", chatId)
            .apply()
    }

    fun getDarkMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DARK_MODE, DARK_MODE_SYSTEM) ?: DARK_MODE_SYSTEM

    fun setDarkMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK_MODE, mode)
            .apply()
    }

    fun getMaxEmailSizeMb(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_EMAIL_SIZE_MB, DEFAULT_MAX_EMAIL_SIZE_MB)

    fun setMaxEmailSizeMb(context: Context, sizeMb: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MAX_EMAIL_SIZE_MB, sizeMb)
            .apply()
    }

    fun getEncryptionPin(context: Context): String =
        com.mailchat.crypto.CryptoService.getPin(context)

    fun getSyncPeriodDays(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SYNC_PERIOD_DAYS, DEFAULT_SYNC_PERIOD_DAYS)

    fun setSyncPeriodDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SYNC_PERIOD_DAYS, days)
            .apply()
    }

    fun getSyncIntervalMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SYNC_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL_MINUTES)

    fun setSyncIntervalMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SYNC_INTERVAL_MINUTES, minutes)
            .apply()
    }

    fun getSignature(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SIGNATURE, "Sent by MailChat") ?: "Sent by MailChat"

    fun setSignature(context: Context, signature: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIGNATURE, signature)
            .apply()
    }

    // Values for the key signature (second signature on encrypted emails).
    const val KEY_SIG_OFF = "off"
    const val KEY_SIG_KEY = "key"
    const val KEY_SIG_LINK = "link"

    /**
     * What to append as a second signature to encrypted emails:
     * [KEY_SIG_OFF] (default), [KEY_SIG_KEY] (own armored public key) or
     * [KEY_SIG_LINK] (link to the key on keys.openpgp.org).
     */
    fun getEncryptedKeySignature(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENCRYPTED_KEY_SIGNATURE, KEY_SIG_OFF) ?: KEY_SIG_OFF

    fun setEncryptedKeySignature(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENCRYPTED_KEY_SIGNATURE, value)
            .apply()
    }

    /**
     * Builds the second signature for encrypted emails based on the key-signature setting.
     * Returns "" when the setting is off or no key/fingerprint is available.
     */
    fun getEncryptedKeySignatureText(context: Context): String {
        return when (getEncryptedKeySignature(context)) {
            KEY_SIG_KEY -> {
                val key = com.mailchat.crypto.OpenPgpKeyService.getPublicKeyArmored(context)
                if (key.isBlank()) "" else context.getString(com.mailchat.R.string.key_sig_heading_key) + "\n" + key
            }
            KEY_SIG_LINK -> {
                val fp = com.mailchat.crypto.OpenPgpKeyService.getFingerprint(context)
                if (fp.isBlank()) "" else context.getString(com.mailchat.R.string.key_sig_heading_link) + "\n" +
                    "https://keys.openpgp.org/vks/v1/by-fingerprint/${fp.replace(" ", "")}"
            }
            else -> ""
        }
    }

    private const val KEY_DELETED_MSG_IDS = "deleted_msg_ids"
    private const val KEY_SMTP_PROVIDER = "smtp_provider"
    private const val KEY_SENDER_ACCOUNT_ID = "sender_account_id"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_PREVIOUS_COMPOSE_WORDS = "previous_compose_words"
    private const val KEY_AI_PROVIDER = "ai_provider"
    private const val KEY_OPENAI_API_KEY = "openai_api_key"
    private const val KEY_OPENAI_ENDPOINT = "openai_endpoint"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val KEY_OPENAI_CONTEXT_SIZE = "openai_context_size"
    private const val KEY_IMAP_PUSH_ENABLED = "imap_push_enabled"
    private const val KEY_ENCRYPTED_KEY_SIGNATURE = "encrypted_key_signature"

    const val DEFAULT_AI_MODEL = "gemma-4-e2b"
    const val AI_PROVIDER_ON_DEVICE = "on_device"
    const val AI_PROVIDER_OPENAI = "openai"
    const val DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1"
    const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    const val DEFAULT_OPENAI_CONTEXT_SIZE = 100_000

    fun getAiProvider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_AI_PROVIDER, AI_PROVIDER_ON_DEVICE) ?: AI_PROVIDER_ON_DEVICE

    fun setAiProvider(context: Context, provider: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AI_PROVIDER, provider)
            .apply()
    }

    fun getOpenAiApiKey(context: Context): String =
        Secrets.getSecret(context, "openai_api_key")
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_OPENAI_API_KEY, "") ?: ""

    fun setOpenAiApiKey(context: Context, key: String) {
        if (key.isBlank()) {
            Secrets.removeSecret(context, "openai_api_key")
        } else {
            Secrets.putSecret(context, "openai_api_key", key)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_OPENAI_API_KEY)
            .apply()
    }

    fun getOpenAiEndpoint(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OPENAI_ENDPOINT, DEFAULT_OPENAI_ENDPOINT) ?: DEFAULT_OPENAI_ENDPOINT

    fun setOpenAiEndpoint(context: Context, endpoint: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPENAI_ENDPOINT, endpoint)
            .apply()
    }

    fun getOpenAiModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OPENAI_MODEL, DEFAULT_OPENAI_MODEL) ?: DEFAULT_OPENAI_MODEL

    fun setOpenAiModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPENAI_MODEL, model)
            .apply()
    }

    fun getOpenAiContextSize(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_OPENAI_CONTEXT_SIZE, DEFAULT_OPENAI_CONTEXT_SIZE)

    fun setOpenAiContextSize(context: Context, size: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OPENAI_CONTEXT_SIZE, size.coerceIn(1000, 1_000_000))
            .apply()
    }

    fun getAiEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AI_ENABLED, false)

    fun setAiEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AI_ENABLED, enabled)
            .apply()
    }

    fun getAiAutoProcess(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("ai_auto_process", false)

    fun setAiAutoProcess(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("ai_auto_process", enabled)
            .apply()
    }

    /**
     * When enabled, a display name (Klarnamen) is created automatically for a new sender the
     * first time an email is received, using the AI (see [com.mailchat.ai.GeminiService.extractDisplayName]).
     * Only chats whose name is still the bare email address are affected.
     */
    fun getAiAutoRealNames(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("ai_auto_real_names", false)

    fun setAiAutoRealNames(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("ai_auto_real_names", enabled)
            .apply()
    }

    fun getAiModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL

    fun setAiModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AI_MODEL, model)
            .apply()
    }

    fun getPreviousComposeWords(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PREVIOUS_COMPOSE_WORDS, emptySet())
            ?.toList()
            ?.reversed()
            ?: emptyList()

    fun addComposeWords(context: Context, words: List<String>) {
        val current = getPreviousComposeWords(context).toMutableList()
        current.addAll(words.filter { it.isNotBlank() })
        val trimmed = current.takeLast(200)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PREVIOUS_COMPOSE_WORDS, trimmed.toSet())
            .apply()
    }

    fun getSmtpProvider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SMTP_PROVIDER, "") ?: ""

    fun setSmtpProvider(context: Context, provider: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SMTP_PROVIDER, provider)
            .apply()
    }

    /** Globaler Absender fuer alle Chats; 0 = Konto des jeweiligen Chats. */
    fun getSenderAccountId(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SENDER_ACCOUNT_ID, 0L)

    fun setSenderAccountId(context: Context, accountId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SENDER_ACCOUNT_ID, accountId)
            .apply()
    }

    fun getDeletedMessageIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DELETED_MSG_IDS, emptySet()) ?: emptySet()

    fun addDeletedMessageIds(context: Context, ids: List<String>) {
        val current = getDeletedMessageIds(context).toMutableSet()
        current.addAll(ids.filter { it.isNotBlank() })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DELETED_MSG_IDS, current)
            .apply()
    }

    fun isImapPushEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IMAP_PUSH_ENABLED, true)

    fun setImapPushEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IMAP_PUSH_ENABLED, enabled)
            .apply()
    }

    data class CustomTag(val name: String, val description: String)

    // Default tag definitions. The tag NAME is stored in the app's current language (German or
    // English) so the UI shows a single, clean name; the DESCRIPTION is always English because
    // the on-device AI classifies best in English. The descriptions are deliberately specific and
    // mutually exclusive to avoid misclassification (e.g. a marketing "Marketing" tag must not be
    // classified as "Arbeit / Work").
    private data class TagDef(val nameEn: String, val nameDe: String, val desc: String)

    private val TAG_DEFS = listOf(
        TagDef("Invoice", "Rechnung", "invoices, bills, payment requests, receipts, payment reminders, payment due dates from companies or providers"),
        TagDef("Appointment", "Termin", "appointments, meetings, deadlines, reservations, calendar invitations, booking and reservation confirmations"),
        TagDef("Shipping", "Versand", "shipping info, delivery dates, package tracking, parcel notifications, courier and delivery updates"),
        TagDef("Medical", "Medizin", "doctor reports, medical appointments, prescriptions, lab results, health insurance, pharmacy"),
        TagDef("Personal", "Privat", "private emails from known personal contacts, family and friends, personal matters"),
        TagDef("Work", "Arbeit", "work-related emails from employer, colleagues or clients; projects, internal company communication, HR, payroll — not marketing newsletters"),
        TagDef("Marketing", "Marketing", "marketing newsletters, promotional content, advertisements, product updates, sales offers and discount codes — not work or business emails"),
        TagDef("Finance", "Bank", "bank statements, credit card, loan, insurance, tax, investment and financial institution notices")
    )

    /** Whether the UI language is German (explicit setting, or the system locale when "system"). */
    fun isGermanUi(context: Context): Boolean {
        val lang = LanguageUtils.getLanguage(context)
        return when (lang) {
            LanguageUtils.GERMAN -> true
            LanguageUtils.ENGLISH -> false
            else -> java.util.Locale.getDefault().language.equals("de", ignoreCase = true)
        }
    }

    /** The default tags in the given language (name localized, description always English). */
    fun defaultTags(german: Boolean): List<CustomTag> =
        TAG_DEFS.map { if (german) CustomTag(it.nameDe, it.desc) else CustomTag(it.nameEn, it.desc) }

    /**
     * Maps the old bilingual default tag names ("Rechnung / Invoice") to the new single-language
     * names, so already-applied tags (on messages/chats and in the tag list) can be renamed
     * without wiping the user's custom tags.
     */
    fun bilingualTagRenameMapping(german: Boolean): Map<String, String> =
        TAG_DEFS.associate { def ->
            "${def.nameDe} / ${def.nameEn}" to (if (german) def.nameDe else def.nameEn)
        }

    // Bumped when the default tags are redesigned so existing installs pick up the new set
    // on the next launch.
    private const val TAGS_VERSION = 2

    fun getCustomTags(context: Context): List<CustomTag> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = defaultTags(isGermanUi(context))
        val storedVersion = prefs.getInt("custom_tags_version", 1)
        if (storedVersion < TAGS_VERSION) {
            // Tags were redesigned — reset to the new defaults (one-time migration).
            saveCustomTags(context, defaults)
            return defaults
        }
        val json = prefs.getString("custom_tags_json", null)
        if (json == null) {
            saveCustomTags(context, defaults)
            return defaults
        }
        val tags = try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CustomTag(obj.getString("name"), obj.getString("desc"))
            }
        } catch (_: Exception) {
            defaults
        }
        // One-time rename: the default tags were bilingual ("Rechnung / Invoice") and are now
        // single-language. Rename the known bilingual names, preserving any custom tags.
        val renamed = renameBilingualTagNames(tags, isGermanUi(context))
        if (renamed != tags) saveCustomTags(context, renamed)
        return renamed
    }

    private fun renameBilingualTagNames(tags: List<CustomTag>, german: Boolean): List<CustomTag> {
        val mapping = bilingualTagRenameMapping(german)
        return tags.map { tag -> mapping[tag.name]?.let { tag.copy(name = it) } ?: tag }
    }

    fun saveCustomTags(context: Context, tags: List<CustomTag>) {
        val arr = org.json.JSONArray()
        tags.forEach { tag ->
            val obj = org.json.JSONObject()
            obj.put("name", tag.name)
            obj.put("desc", tag.description)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("custom_tags_json", arr.toString())
            .putInt("custom_tags_version", TAGS_VERSION)
            .apply()
    }

    /**
     * One-time flag: the old bilingual default tag names ("Rechnung / Invoice") have been renamed
     * to single-language names on already-tagged messages and chats (see
     * [com.mailchat.ui.viewmodel.ChatListViewModel.migrateTagNames]).
     */
    fun getTagNamesMigratedV3(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("tag_names_migrated_v3", false)

    fun setTagNamesMigratedV3(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("tag_names_migrated_v3", true)
            .apply()
    }

    // ------------------------------------------------------------------
    // Auto-reply "Wo bist Du?" (per contact)
    // ------------------------------------------------------------------

    /**
     * Whether the app should automatically reply to the "Wo bist Du?" question from [email]
     * with the user's current location. Stored per contact (email address).
     */
    fun getAutoLocationReply(context: Context, email: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("auto_location_reply_" + email.trim().lowercase(), false)

    fun setAutoLocationReply(context: Context, email: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_location_reply_" + email.trim().lowercase(), enabled)
            .apply()
    }

    /**
     * Whether the auto-reply for [email] should additionally include the weather at the user's
     * location for the next 4 hours (fetched from open-meteo.com). Only relevant when
     * [getAutoLocationReply] is enabled.
     */
    fun getAutoWeather(context: Context, email: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("auto_weather_" + email.trim().lowercase(), false)

    fun setAutoWeather(context: Context, email: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_weather_" + email.trim().lowercase(), enabled)
            .apply()
    }

    /**
     * Whether the auto-reply for [email] should additionally attach a small OpenStreetMap map
     * image of the user's location (tiles fetched from tile.openstreetmap.org). Only relevant
     * when [getAutoLocationReply] is enabled.
     */
    /**
     * Message IDs for which the CID-image repair was attempted but could not fix the message
     * (the server copy has no content-ID mapping either). These are skipped on every repair
     * pass — without this, the same messages would be re-fetched from the server on every
     * periodic sync forever (a persistent network/battery drain).
     */
    fun getCidRepairFailedMessageIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("cid_repair_failed_message_ids", emptySet())!!.toSet()

    fun addCidRepairFailedMessageId(context: Context, messageId: String) {
        if (messageId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("cid_repair_failed_message_ids", emptySet())!!.toMutableSet()
        current.add(messageId)
        val trimmed = if (current.size > 500) current.toList().takeLast(500).toSet() else current
        prefs.edit().putStringSet("cid_repair_failed_message_ids", trimmed).apply()
    }

    fun getAutoMap(context: Context, email: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("auto_map_" + email.trim().lowercase(), false)

    fun setAutoMap(context: Context, email: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_map_" + email.trim().lowercase(), enabled)
            .apply()
    }

    /**
     * The set of message IDs that have already received an automatic "Wo bist Du?" reply.
     * Used to avoid replying twice to the same message (e.g. on re-sync).
     */
    fun getAutoRepliedMessageIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("auto_replied_message_ids", emptySet())!!.toSet()

    fun addAutoRepliedMessageId(context: Context, messageId: String) {
        if (messageId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("auto_replied_message_ids", emptySet())!!.toMutableSet()
        current.add(messageId)
        // Keep the set bounded so it doesn't grow without limit.
        val trimmed = if (current.size > 500) current.toList().takeLast(500).toSet() else current
        prefs.edit().putStringSet("auto_replied_message_ids", trimmed).apply()
    }

    /**
     * The set of message IDs for which the "auto-reply skipped: no location permission" notice
     * has already been shown in the chat. Used to avoid inserting the notice again on every
     * re-sync of the same message.
     */
    fun getAutoReplyErrorShownMessageIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("auto_reply_error_shown_message_ids", emptySet())!!.toSet()

    fun addAutoReplyErrorShownMessageId(context: Context, messageId: String) {
        if (messageId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("auto_reply_error_shown_message_ids", emptySet())!!.toMutableSet()
        current.add(messageId)
        val trimmed = if (current.size > 500) current.toList().takeLast(500).toSet() else current
        prefs.edit().putStringSet("auto_reply_error_shown_message_ids", trimmed).apply()
    }

    /**
     * One-time flag: the chat list previews (lastMessageSubject/timestamp) have been backfilled
     * from the latest message. Older app versions did not update lastMessageSubject when
     * messages were fetched via background sync, leaving some chats with an empty or stale
     * subject on the overview page.
     */
    fun getChatPreviewsRepaired(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("chat_previews_repaired", false)

    fun setChatPreviewsRepaired(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("chat_previews_repaired", true)
            .apply()
    }

    // ------------------------------------------------------------------
    // Phishing blacklist (persists even if the chat is deleted)
    // ------------------------------------------------------------------

    private const val KEY_PHISHING_BLACKLIST = "phishing_blacklist"

    /** All blacklisted sender addresses (lowercase). */
    fun getPhishingBlacklist(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PHISHING_BLACKLIST, emptySet())
            ?.map { it.lowercase() }?.toSet() ?: emptySet()

    fun isPhishingBlacklisted(context: Context, email: String): Boolean =
        email.isNotBlank() && email.lowercase() in getPhishingBlacklist(context)

    /** Add sender addresses to the phishing blacklist (idempotent). */
    fun addPhishingBlacklist(context: Context, emails: Collection<String>) {
        if (emails.isEmpty()) return
        val current = getPhishingBlacklist(context).toMutableSet()
        emails.forEach { current.add(it.lowercase()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PHISHING_BLACKLIST, current)
            .apply()
    }

    /** Remove sender addresses from the phishing blacklist (e.g. when marked as "ham"). */
    fun removePhishingBlacklist(context: Context, emails: Collection<String>) {
        if (emails.isEmpty()) return
        val current = getPhishingBlacklist(context).toMutableSet()
        emails.forEach { current.remove(it.lowercase()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PHISHING_BLACKLIST, current)
            .apply()
    }

    // ------------------------------------------------------------------
    // Home-screen widget
    // ------------------------------------------------------------------

    const val WIDGET_MODE_CHAT = "chat"
    const val WIDGET_MODE_DETAIL = "detail"

    /** What the widget opens when an email is tapped: [WIDGET_MODE_CHAT] (default) or [WIDGET_MODE_DETAIL]. */
    fun getWidgetOpenMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("widget_open_mode", WIDGET_MODE_CHAT) ?: WIDGET_MODE_CHAT

    fun setWidgetOpenMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("widget_open_mode", mode)
            .apply()
    }

    const val WIDGET_CONTENT_LATEST = "latest"
    const val WIDGET_CONTENT_UNREAD = "unread"

    /** What the widget lists: [WIDGET_CONTENT_UNREAD] (default) or [WIDGET_CONTENT_LATEST]. */
    fun getWidgetContentMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("widget_content_mode", WIDGET_CONTENT_UNREAD) ?: WIDGET_CONTENT_UNREAD

    fun setWidgetContentMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("widget_content_mode", mode)
            .apply()
    }

    /** Widget background transparency in percent (0 = opaque, 100 = fully transparent). */
    fun getWidgetTransparency(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("widget_transparency", 0)
            .coerceIn(0, 100)

    fun setWidgetTransparency(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("widget_transparency", percent.coerceIn(0, 100))
            .apply()
    }

    const val WIDGET_FONT_SMALL = "small"
    const val WIDGET_FONT_NORMAL = "normal"
    const val WIDGET_FONT_LARGE = "large"

    /** Widget font size: [WIDGET_FONT_SMALL], [WIDGET_FONT_NORMAL] (default) or [WIDGET_FONT_LARGE]. */
    fun getWidgetFontSize(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("widget_font_size", WIDGET_FONT_NORMAL) ?: WIDGET_FONT_NORMAL

    fun setWidgetFontSize(context: Context, size: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("widget_font_size", size)
            .apply()
    }

    /** Scale factor for the widget font size setting (multiplied onto the base sp sizes). */
    fun getWidgetFontScale(context: Context): Float = when (getWidgetFontSize(context)) {
        WIDGET_FONT_SMALL -> 0.85f
        WIDGET_FONT_LARGE -> 1.2f
        else -> 1.0f
    }
}
