package com.mailchat.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Secrets {
    private const val SECRETS_FILE = "mailchat_secrets"

    @Volatile
    private var instance: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: run {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    SECRETS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { instance = it }
            }
        }
    }

    fun getSecret(context: Context, key: String): String? =
        runCatching { prefs(context).getString(key, null) }.getOrNull()

    fun putSecret(context: Context, key: String, value: String) {
        runCatching { prefs(context).edit().putString(key, value).apply() }
    }

    fun removeSecret(context: Context, key: String) {
        runCatching { prefs(context).edit().remove(key).apply() }
    }

    fun accountPasswordKey(accountId: Long): String = "account_pw_$accountId"
}
