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

    private val DEFAULT_TAGS = listOf(
        CustomTag("rechnung", "invoices, bills, payment requests, receipts"),
        CustomTag("termin", "appointments, meetings, deadlines, reservations"),
        CustomTag("versand", "shipping info, delivery dates, package tracking"),
        CustomTag("medizin", "doctor reports, medical appointments, prescriptions, blood values"),
        CustomTag("privat", "private emails from known contacts, personal matters"),
        CustomTag("firma", "work-related info, employer, colleagues, projects"),
        CustomTag("newsletter", "newsletters, marketing, promotional content"),
        CustomTag("bank", "bank statements, credit card, loan, insurance")
    )

    fun getCustomTags(context: Context): List<CustomTag> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString("custom_tags_json", null)
        if (json == null) {
            saveCustomTags(context, DEFAULT_TAGS)
            return DEFAULT_TAGS
        }
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CustomTag(obj.getString("name"), obj.getString("desc"))
            }
        } catch (_: Exception) {
            DEFAULT_TAGS
        }
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
}
