package com.mailchat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "email_accounts")
data class EmailAccount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val emailAddress: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSsl: Boolean,
    val username: String,
    val passwordEncrypted: String,
    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val smtpSsl: Boolean = true,
    val avatarColor: Int = 0xFF128C7E.toInt(),
    val lastSyncTimestamp: Long = 0,
    val syncFolders: String = ""
)