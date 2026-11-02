package com.mailchat.util

import android.content.Context
import com.mailchat.data.database.MailchatDatabase
import com.mailchat.data.model.EmailAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ImportResult(
    val needsRestart: Boolean = false
)

object ConfigBackupUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 100_000

    suspend fun exportConfig(context: Context, password: String): File = withContext(Dispatchers.IO) {
        val json = buildJson(context)
        val zipBytes = createZip(context, json)
        val encrypted = encrypt(zipBytes, password)
        val file = File(context.filesDir, "mailchat_config_${System.currentTimeMillis()}.enc")
        file.writeBytes(encrypted)
        file
    }

    suspend fun importConfig(context: Context, file: File, password: String): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val data = file.readBytes()
            val decrypted = decrypt(data, password)
            if (isZip(decrypted)) {
                restoreFromZip(context, decrypted)
                Result.success(ImportResult(needsRestart = true))
            } else {
                val jsonStr = String(decrypted, Charsets.UTF_8)
                val json = JSONObject(jsonStr)
                restoreFromJson(context, json)
                Result.success(ImportResult(needsRestart = false))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isZip(data: ByteArray): Boolean {
        return data.size > 4 &&
            data[0] == 0x50.toByte() &&
            data[1] == 0x4B.toByte() &&
            data[2] == 0x03.toByte() &&
            data[3] == 0x04.toByte()
    }

    private suspend fun buildJson(context: Context): JSONObject {
        val root = JSONObject()
        root.put("version", 2)

        val passwords = JSONObject()
        try {
            val db = MailchatDatabase.getInstance(context)
            val accounts = db.accountDao().getAllAccounts().first()
            for (account in accounts) {
                val pw = Secrets.getSecret(context, Secrets.accountPasswordKey(account.id))
                    ?: account.passwordEncrypted
                if (pw.isNotBlank()) {
                    passwords.put(account.id.toString(), pw)
                }
            }
        } catch (_: Exception) {}
        root.put("accountPasswords", passwords)

        val settings = JSONObject()
        settings.put("darkMode", SettingsUtils.getDarkMode(context))
        settings.put("maxEmailSizeMb", SettingsUtils.getMaxEmailSizeMb(context))
        settings.put("syncPeriodDays", SettingsUtils.getSyncPeriodDays(context))
        settings.put("syncIntervalMinutes", SettingsUtils.getSyncIntervalMinutes(context))
        settings.put("signature", SettingsUtils.getSignature(context))
        settings.put("smtpProvider", SettingsUtils.getSmtpProvider(context))
        settings.put("imapPushEnabled", SettingsUtils.isImapPushEnabled(context))
        settings.put("aiEnabled", SettingsUtils.getAiEnabled(context))
        settings.put("aiAutoProcess", SettingsUtils.getAiAutoProcess(context))
        settings.put("aiProvider", SettingsUtils.getAiProvider(context))
        settings.put("aiModel", SettingsUtils.getAiModel(context))
        settings.put("openAiEndpoint", SettingsUtils.getOpenAiEndpoint(context))
        settings.put("openAiModel", SettingsUtils.getOpenAiModel(context))
        val openAiKey = SettingsUtils.getOpenAiApiKey(context)
        if (openAiKey.isNotBlank()) settings.put("openAiApiKey", openAiKey)
        settings.put("senderAccountId", SettingsUtils.getSenderAccountId(context))
        settings.put("language", LanguageUtils.getLanguage(context))
        // Additional settings (previously not backed up).
        settings.put("encryptedKeySignature", SettingsUtils.getEncryptedKeySignature(context))
        settings.put("openAiContextSize", SettingsUtils.getOpenAiContextSize(context))
        settings.put("openpgpAutoLookup", com.mailchat.crypto.OpenPgpKeyService.isAutoLookupEnabled(context))
        settings.put("openpgpWriteBackSent", com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackEnabled(context))
        settings.put("openpgpWriteBackInbox", com.mailchat.crypto.OpenPgpKeyService.isAutoWriteBackInboxEnabled(context))

        // Custom tags.
        val tagsArr = JSONArray()
        SettingsUtils.getCustomTags(context).forEach { tag ->
            val t = JSONObject()
            t.put("name", tag.name)
            t.put("desc", tag.description)
            tagsArr.put(t)
        }
        settings.put("customTags", tagsArr)

        // Per-contact auto-reply "Wo bist Du?" settings (location + weather).
        val mainPrefs = context.getSharedPreferences("mailchat_prefs", android.content.Context.MODE_PRIVATE)
        val autoLocation = JSONObject()
        val autoWeather = JSONObject()
        for ((key, value) in mainPrefs.all) {
            when {
                key.startsWith("auto_location_reply_") -> {
                    if (value is Boolean && value) autoLocation.put(key.removePrefix("auto_location_reply_"), true)
                }
                key.startsWith("auto_weather_") -> {
                    if (value is Boolean && value) autoWeather.put(key.removePrefix("auto_weather_"), true)
                }
            }
        }
        if (autoLocation.length() > 0) settings.put("autoLocationReply", autoLocation)
        if (autoWeather.length() > 0) settings.put("autoWeather", autoWeather)

        val composeWords = JSONArray()
        SettingsUtils.getPreviousComposeWords(context).forEach { composeWords.put(it) }
        settings.put("previousComposeWords", composeWords)

        val deletedIds = JSONArray()
        SettingsUtils.getDeletedMessageIds(context).forEach { deletedIds.put(it) }
        settings.put("deletedMsgIds", deletedIds)

        root.put("settings", settings)

        val prefs = context.getSharedPreferences("mailchat_crypto", android.content.Context.MODE_PRIVATE)
        val pgp = JSONObject()
        val privKey = prefs.getString("private_key", "") ?: ""
        val pubKey = prefs.getString("public_key", "") ?: ""
        val keyEmail = prefs.getString("key_email", "") ?: ""
        if (privKey.isNotBlank()) pgp.put("privateKey", privKey)
        if (pubKey.isNotBlank()) pgp.put("publicKey", pubKey)
        if (keyEmail.isNotBlank()) pgp.put("keyEmail", keyEmail)
        val peers = com.mailchat.crypto.CryptoService.getPeers(context)
        if (peers.isNotEmpty()) {
            val peersArr = JSONArray()
            peers.forEach { peer ->
                val p = JSONObject()
                p.put("email", peer.email)
                p.put("publicKey", peer.publicKeyArmored)
                p.put("fingerprint", peer.fingerprint)
                p.put("pinVerified", peer.pinVerified)
                peersArr.put(p)
            }
            pgp.put("peers", peersArr)
        }
        if (pgp.length() > 0) root.put("pgp", pgp)

        val openpgp = JSONObject()
        val ogSecret = com.mailchat.crypto.OpenPgpKeyService.getSecretKeyArmored(context)
        val ogPublic = com.mailchat.crypto.OpenPgpKeyService.getPublicKeyArmored(context)
        val ogFp = com.mailchat.crypto.OpenPgpKeyService.getFingerprint(context)
        val ogPass = com.mailchat.crypto.OpenPgpKeyService.getEncryptedPassphrase(context)
        if (ogSecret.isNotBlank()) openpgp.put("secretKey", ogSecret)
        if (ogPublic.isNotBlank()) openpgp.put("publicKey", ogPublic)
        if (ogFp.isNotBlank()) openpgp.put("fingerprint", ogFp)
        if (ogPass.isNotBlank()) openpgp.put("passphrase", ogPass)
        val ogPeers = com.mailchat.crypto.OpenPgpKeyService.getPeers(context)
        if (ogPeers.isNotEmpty()) {
            val ogPeersArr = JSONArray()
            ogPeers.forEach { peer ->
                val p = JSONObject()
                p.put("email", peer.email)
                p.put("publicKey", peer.publicKeyArmored)
                p.put("fingerprint", peer.fingerprint)
                ogPeersArr.put(p)
            }
            openpgp.put("peers", ogPeersArr)
        }
        if (openpgp.length() > 0) root.put("openpgp", openpgp)

        return root
    }

    private fun createZip(context: Context, json: JSONObject): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("config.json"))
            zos.write(json.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val dbFile = context.getDatabasePath("mailchat_database")
            if (dbFile.exists()) {
                try {
                    val db = MailchatDatabase.getInstance(context)
                    db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                } catch (_: Exception) {}
                zos.putNextEntry(ZipEntry("mailchat_database"))
                dbFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            addDirectoryToZip(zos, File(context.filesDir, "attachments"), "attachments")
            addDirectoryToZip(zos, File(context.filesDir, "compose_attachments"), "compose_attachments")
        }
        return baos.toByteArray()
    }

    private fun addDirectoryToZip(zos: ZipOutputStream, dir: File, prefix: String) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val entryName = "$prefix/${file.relativeTo(dir).path}"
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    private fun restoreFromZip(context: Context, zipData: ByteArray) {
        val tempDir = File(context.cacheDir, "import_tmp").apply { mkdirs() }
        try {
            ZipInputStream(ByteArrayInputStream(zipData)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val entryFile = File(tempDir, entry!!.name)
                    if (!entryFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                        throw SecurityException("Zip slip detected")
                    }
                    entryFile.parentFile?.mkdirs()
                    entryFile.outputStream().use { zis.copyTo(it) }
                }
            }

            val dbFile = context.getDatabasePath("mailchat_database")
            val importedDb = File(tempDir, "mailchat_database")
            if (importedDb.exists()) {
                MailchatDatabase.closeInstance()
                dbFile.parentFile?.mkdirs()
                importedDb.copyTo(dbFile, overwrite = true)
                File(dbFile.parentFile, dbFile.name + "-wal").delete()
                File(dbFile.parentFile, dbFile.name + "-shm").delete()
            }

            copyDirectory(File(tempDir, "attachments"), File(context.filesDir, "attachments"))
            copyDirectory(File(tempDir, "compose_attachments"), File(context.filesDir, "compose_attachments"))

            val configJson = File(tempDir, "config.json")
            if (configJson.exists()) {
                val json = JSONObject(configJson.readText())
                restoreSettings(context, json)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun copyDirectory(from: File, to: File) {
        if (!from.exists() || !from.isDirectory) return
        to.mkdirs()
        from.walkTopDown().filter { it.isFile }.forEach { file ->
            val target = File(to, file.relativeTo(from).path)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
    }

    private fun restoreSettings(context: Context, json: JSONObject) {
        val passwords = json.optJSONObject("accountPasswords")
        if (passwords != null) {
            val keys = passwords.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val pw = passwords.getString(key)
                if (pw.isNotBlank()) {
                    Secrets.putSecret(context, "account_pw_$key", pw)
                }
            }
        }

        val settings = json.optJSONObject("settings") ?: return
        SettingsUtils.setDarkMode(context, settings.optString("darkMode"))
        if (settings.has("maxEmailSizeMb")) SettingsUtils.setMaxEmailSizeMb(context, settings.getInt("maxEmailSizeMb"))
        if (settings.has("syncPeriodDays")) SettingsUtils.setSyncPeriodDays(context, settings.getInt("syncPeriodDays"))
        if (settings.has("syncIntervalMinutes")) SettingsUtils.setSyncIntervalMinutes(context, settings.getInt("syncIntervalMinutes"))
        SettingsUtils.setSignature(context, settings.optString("signature"))
        SettingsUtils.setSmtpProvider(context, settings.optString("smtpProvider"))
        if (settings.has("imapPushEnabled")) SettingsUtils.setImapPushEnabled(context, settings.getBoolean("imapPushEnabled"))
        if (settings.has("aiEnabled")) SettingsUtils.setAiEnabled(context, settings.getBoolean("aiEnabled"))
        if (settings.has("aiAutoProcess")) SettingsUtils.setAiAutoProcess(context, settings.getBoolean("aiAutoProcess"))
        SettingsUtils.setAiProvider(context, settings.optString("aiProvider"))
        SettingsUtils.setAiModel(context, settings.optString("aiModel"))
        SettingsUtils.setOpenAiEndpoint(context, settings.optString("openAiEndpoint"))
        SettingsUtils.setOpenAiModel(context, settings.optString("openAiModel"))
        val key = settings.optString("openAiApiKey", "")
        if (key.isNotBlank()) SettingsUtils.setOpenAiApiKey(context, key)
        if (settings.has("senderAccountId")) SettingsUtils.setSenderAccountId(context, settings.getLong("senderAccountId"))
        LanguageUtils.setLanguage(context, settings.optString("language"))
        // Additional settings (previously not backed up).
        if (settings.has("encryptedKeySignature")) {
            SettingsUtils.setEncryptedKeySignature(context, settings.optString("encryptedKeySignature"))
        }
        if (settings.has("openAiContextSize")) {
            SettingsUtils.setOpenAiContextSize(context, settings.getInt("openAiContextSize"))
        }
        if (settings.has("openpgpAutoLookup")) {
            com.mailchat.crypto.OpenPgpKeyService.setAutoLookupEnabled(context, settings.getBoolean("openpgpAutoLookup"))
        }
        if (settings.has("openpgpWriteBackSent")) {
            com.mailchat.crypto.OpenPgpKeyService.setAutoWriteBackEnabled(context, settings.getBoolean("openpgpWriteBackSent"))
        }
        if (settings.has("openpgpWriteBackInbox")) {
            com.mailchat.crypto.OpenPgpKeyService.setAutoWriteBackInboxEnabled(context, settings.getBoolean("openpgpWriteBackInbox"))
        }

        val tagsArr = settings.optJSONArray("customTags")
        if (tagsArr != null) {
            val tags = (0 until tagsArr.length()).map { i ->
                val t = tagsArr.getJSONObject(i)
                com.mailchat.util.SettingsUtils.CustomTag(t.getString("name"), t.getString("desc"))
            }
            SettingsUtils.saveCustomTags(context, tags)
        }

        val prefs = context.getSharedPreferences("mailchat_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Per-contact auto-reply "Wo bist Du?" settings (location + weather).
        val autoLocation = settings.optJSONObject("autoLocationReply")
        if (autoLocation != null) {
            val keys = autoLocation.keys()
            while (keys.hasNext()) {
                val email = keys.next()
                editor.putBoolean("auto_location_reply_" + email, autoLocation.getBoolean(email))
            }
        }
        val autoWeather = settings.optJSONObject("autoWeather")
        if (autoWeather != null) {
            val keys = autoWeather.keys()
            while (keys.hasNext()) {
                val email = keys.next()
                editor.putBoolean("auto_weather_" + email, autoWeather.getBoolean(email))
            }
        }

        val composeWords = settings.optJSONArray("previousComposeWords")
        if (composeWords != null) {
            val words = (0 until composeWords.length()).map { composeWords.getString(it) }
            editor.putStringSet("previous_compose_words", words.toSet())
        }

        val deletedIds = settings.optJSONArray("deletedMsgIds")
        if (deletedIds != null) {
            val ids = (0 until deletedIds.length()).map { deletedIds.getString(it) }
            editor.putStringSet("deleted_msg_ids", ids.toSet())
        }

        editor.apply()

        val pgp = json.optJSONObject("pgp")
        if (pgp != null) {
            val cryptoPrefs = context.getSharedPreferences("mailchat_crypto", Context.MODE_PRIVATE)
            val cryptoEditor = cryptoPrefs.edit()
            val privKey = pgp.optString("privateKey", "")
            val pubKey = pgp.optString("publicKey", "")
            val keyEmail = pgp.optString("keyEmail", "")
            if (privKey.isNotBlank()) cryptoEditor.putString("private_key", privKey)
            if (pubKey.isNotBlank()) cryptoEditor.putString("public_key", pubKey)
            if (keyEmail.isNotBlank()) cryptoEditor.putString("key_email", keyEmail)
            cryptoEditor.apply()

            val peersArr = pgp.optJSONArray("peers")
            if (peersArr != null) {
                for (i in 0 until peersArr.length()) {
                    val p = peersArr.getJSONObject(i)
                    com.mailchat.crypto.CryptoService.addPeer(
                        context,
                        com.mailchat.crypto.CryptoService.PeerKey(
                            email = p.optString("email"),
                            publicKeyArmored = p.optString("publicKey"),
                            fingerprint = p.optString("fingerprint"),
                            pinVerified = p.optBoolean("pinVerified", false)
                        )
                    )
                }
            }
        }

        val openpgp = json.optJSONObject("openpgp")
        if (openpgp != null) {
            val ogPrefs = context.getSharedPreferences("mailchat_openpgp", Context.MODE_PRIVATE)
            val ogEditor = ogPrefs.edit()
            val ogSecret = openpgp.optString("secretKey", "")
            val ogPublic = openpgp.optString("publicKey", "")
            val ogFp = openpgp.optString("fingerprint", "")
            val ogPass = openpgp.optString("passphrase", "")
            if (ogSecret.isNotBlank()) ogEditor.putString("openpgp_secret_key", ogSecret)
            if (ogPublic.isNotBlank()) ogEditor.putString("openpgp_public_key", ogPublic)
            if (ogFp.isNotBlank()) ogEditor.putString("openpgp_fingerprint", ogFp)
            if (ogPass.isNotBlank()) ogEditor.putString("openpgp_passphrase", ogPass)
            ogEditor.apply()

            val ogPeersArr = openpgp.optJSONArray("peers")
            if (ogPeersArr != null) {
                for (i in 0 until ogPeersArr.length()) {
                    val p = ogPeersArr.getJSONObject(i)
                    com.mailchat.crypto.OpenPgpKeyService.addPeer(
                        context,
                        com.mailchat.crypto.OpenPgpKeyService.OpenPgpPeer(
                            email = p.optString("email"),
                            publicKeyArmored = p.optString("publicKey"),
                            fingerprint = p.optString("fingerprint")
                        )
                    )
                }
            }
        }
    }

    private suspend fun restoreFromJson(context: Context, json: JSONObject) {
        val accountsArr = json.optJSONArray("accounts")
        if (accountsArr != null) {
            val db = MailchatDatabase.getInstance(context)
            val dao = db.accountDao()
            for (i in 0 until accountsArr.length()) {
                val accObj = accountsArr.getJSONObject(i)
                val account = EmailAccount(
                    displayName = accObj.optString("displayName", ""),
                    emailAddress = accObj.optString("emailAddress", ""),
                    imapHost = accObj.optString("imapHost", ""),
                    imapPort = accObj.optInt("imapPort", 993),
                    imapSsl = accObj.optBoolean("imapSsl", true),
                    username = accObj.optString("username", ""),
                    passwordEncrypted = "",
                    smtpHost = accObj.optString("smtpHost", ""),
                    smtpPort = accObj.optInt("smtpPort", 587),
                    smtpSsl = accObj.optBoolean("smtpSsl", true),
                    avatarColor = accObj.optInt("avatarColor", 0xFF128C7E.toInt()),
                    syncFolders = accObj.optString("syncFolders", "")
                )
                val id = dao.insertAccount(account)
                val pw = accObj.optString("password", "")
                if (pw.isNotBlank()) {
                    Secrets.putSecret(context, Secrets.accountPasswordKey(id), pw)
                }
            }
        }
        restoreSettings(context, json)
    }

    private fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext)
        return salt + iv + encrypted
    }

    private fun decrypt(data: ByteArray, password: String): ByteArray {
        val salt = data.copyOfRange(0, SALT_LENGTH)
        val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val encrypted = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
