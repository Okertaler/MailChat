package com.mailchat.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageUtils {

    private const val PREFS = "mailchat_prefs"
    private const val KEY_LANGUAGE = "language"

    const val SYSTEM = "system"
    const val GERMAN = "de"
    const val ENGLISH = "en"

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun localeFor(language: String): Locale = when (language) {
        GERMAN -> Locale("de")
        ENGLISH -> Locale("en")
        else -> Locale.getDefault()
    }

    fun applyLanguage(context: Context): Context {
        val language = getLanguage(context)
        val locale = localeFor(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
