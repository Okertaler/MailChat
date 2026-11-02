package com.mailchat.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private val contactLock = Any()

    @Volatile
    private var contactCacheTime = 0L

    @Volatile
    private var contactCacheMap: Map<String, Pair<String, String?>> = emptyMap()

    private val contactCacheTtlMs = 10 * 60 * 1000L

    fun invalidateContactCache() {
        synchronized(contactLock) {
            contactCacheTime = 0L
            contactCacheMap = emptyMap()
        }
    }

    private fun loadContactMap(): Map<String, Pair<String, String?>> {
        if (!hasPermission()) return emptyMap()
        val map = mutableMapOf<String, Pair<String, String?>>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.PHOTO_URI
                ),
                null,
                null,
                null
            )
            cursor?.use {
                val addrIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val photoIdx = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                while (it.moveToNext()) {
                    if (addrIdx < 0) continue
                    val address = it.getString(addrIdx) ?: continue
                    val key = address.lowercase()
                    if (map.containsKey(key)) continue
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val photoUri = if (photoIdx >= 0) it.getString(photoIdx) else null
                    map[key] = Pair(name ?: address.substringBefore("@"), photoUri)
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun contactMap(): Map<String, Pair<String, String?>> {
        synchronized(contactLock) {
            val now = System.currentTimeMillis()
            if (now - contactCacheTime >= contactCacheTtlMs || contactCacheMap.isEmpty()) {
                contactCacheMap = loadContactMap()
                contactCacheTime = now
            }
            return contactCacheMap
        }
    }

    private fun customPhotoDir(): File {
        val dir = File(context.filesDir, "contact_photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCustomPhotoUri(email: String): String? {
        val file = File(customPhotoDir(), "${email.lowercase()}.jpg")
        return if (file.exists() && file.length() > 0) "file://${file.absolutePath}" else null
    }

    suspend fun setCustomPhoto(email: String, sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(customPhotoDir(), "${email.lowercase()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getPhotoUriByEmail(email: String): String? = withContext(Dispatchers.IO) {
        if (email.isBlank()) null
        else getCustomPhotoUri(email) ?: contactMap()[email.lowercase()]?.second
    }

    suspend fun getPhotoUrisByEmails(emails: List<String>): Map<String, String?> =
        withContext(Dispatchers.IO) {
            if (emails.isEmpty()) return@withContext emptyMap()
            val map = contactMap()
            emails.mapNotNull { email ->
                val custom = getCustomPhotoUri(email)
                val system = map[email.lowercase()]?.second
                val photo = custom ?: system
                if (photo != null) email to photo else null
            }.toMap()
        }

    suspend fun getNamesByEmails(emails: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (emails.isEmpty()) return@withContext emptyMap()
            val map = contactMap()
            emails.mapNotNull { email ->
                val key = email.lowercase()
                val name = map[key]?.first?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                key to name
            }.toMap()
        }

    suspend fun getAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        contactMap().entries.map { (email, entry) ->
            Contact(name = entry.first, email = email, photoUri = getCustomPhotoUri(email) ?: entry.second)
        }.sortedBy { it.name.lowercase() }
    }
}

data class Contact(
    val name: String,
    val email: String,
    val photoUri: String?
)
