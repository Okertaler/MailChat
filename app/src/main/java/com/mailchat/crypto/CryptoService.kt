package com.mailchat.crypto

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoService {

    private const val PREFS_NAME = "mailchat_crypto"
    private const val KEY_PRIVATE = "pgp_private_key"
    private const val KEY_PUBLIC = "pgp_public_key"
    private const val KEY_FINGERPRINT = "pgp_fingerprint"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN = "pin_value"
    private const val KEY_PEERS = "pgp_peers"
    private const val KEY_EMAIL = "key_email"

    data class KeyPair(
        val publicKeyArmored: String,
        val privateKeyArmored: String,
        val fingerprint: String
    )

    data class PeerKey(
        val email: String,
        val publicKeyArmored: String,
        val fingerprint: String,
        val pinVerified: Boolean
    )

    fun hasKeyPair(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PRIVATE, null) != null
    }

    fun getFingerprint(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FINGERPRINT, "") ?: ""
    }

    fun getPublicKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PUBLIC, "") ?: ""
    }

    fun getPrivateKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRIVATE, "") ?: ""
    }

    fun generateKeyPair(context: Context, email: String, pin: String): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(4096, SecureRandom())
        val javaKeyPair: java.security.KeyPair = keyPairGen.generateKeyPair()

        val pubBytes = javaKeyPair.public.encoded
        val privBytes = javaKeyPair.private.encoded

        val pubArmored = armorEncode("PUBLIC KEY BLOCK", pubBytes)
        val privArmored = armorEncode("PRIVATE KEY BLOCK", privBytes)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val fpBytes = digest.digest(pubBytes)
        val fingerprint = fpBytes.joinToString("") { "%02X".format(it) }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PUBLIC, pubArmored)
            .putString(KEY_PRIVATE, privArmored)
            .putString(KEY_FINGERPRINT, fingerprint)
            .putString(KEY_PIN_HASH, hashPin(pin))
            .putString(KEY_PIN, pin)
            .putString(KEY_EMAIL, email)
            .apply()

        return KeyPair(pubArmored, privArmored, fingerprint)
    }

    fun deleteKeyPair(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PRIVATE)
            .remove(KEY_PUBLIC)
            .remove(KEY_FINGERPRINT)
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN)
            .remove(KEY_EMAIL)
            .apply()
    }

    fun getKeyEmail(context: Context): String {
        // Use selected sender account if available
        val senderId = com.mailchat.util.SettingsUtils.getSenderAccountId(context)
        if (senderId > 0) {
            try {
                val db = com.mailchat.data.database.MailchatDatabase.getInstance(context)
                val account = kotlinx.coroutines.runBlocking { db.accountDao().getAccountById(senderId) }
                if (account != null) return account.emailAddress
            } catch (_: Exception) {
            }
        }
        // Fallback: use stored key email
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, "") ?: ""
        if (stored.isNotBlank()) return stored
        // Final fallback: first account
        return try {
            val db = com.mailchat.data.database.MailchatDatabase.getInstance(context)
            val account = kotlinx.coroutines.runBlocking { db.accountDao().getFirstAccount() }
            account?.emailAddress ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun armorEncode(type: String, data: ByteArray): String {
        val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
        val lines = b64.chunked(64).joinToString("\n")
        return "-----BEGIN $type-----\n$lines\n-----END $type-----"
    }

    private fun armorDecode(armored: String): ByteArray {
        val content = armored
            .removePrefix("-----BEGIN PUBLIC KEY BLOCK-----")
            .removePrefix("-----BEGIN PRIVATE KEY BLOCK-----")
            .removeSuffix("-----END PUBLIC KEY BLOCK-----")
            .removeSuffix("-----END PRIVATE KEY BLOCK-----")
            .trim()
        return Base64.decode(content, Base64.NO_WRAP)
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == storedHash
    }

    fun getPin(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PIN, "") ?: ""
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun encryptForRecipient(plaintext: String, recipientPublicKeyArmored: String): String {
        val pubKeyBytes = armorDecode(recipientPublicKeyArmored)
        val pubKeySpec = X509EncodedKeySpec(pubKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val pubKey = keyFactory.generatePublic(pubKeySpec)

        val aesKeyGen = KeyGenerator.getInstance("AES")
        aesKeyGen.init(256, SecureRandom())
        val aesKey: SecretKey = aesKeyGen.generateKey()

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val encryptedData = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val result = java.io.ByteArrayOutputStream()
        result.write(intToBytes(encryptedAesKey.size))
        result.write(encryptedAesKey)
        result.write(iv)
        result.write(encryptedData)

        return Base64.encodeToString(result.toByteArray(), Base64.NO_WRAP)
    }

    fun decryptFromSender(ciphertextB64: String, context: Context, pin: String): String {
        val data = Base64.decode(ciphertextB64, Base64.NO_WRAP)
        val input = java.io.ByteArrayInputStream(data)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privKeyArmored = prefs.getString(KEY_PRIVATE, "") ?: throw Exception("No private key")
        val privKeyBytes = armorDecode(privKeyArmored)
        val privKeySpec = PKCS8EncodedKeySpec(privKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privKey = keyFactory.generatePrivate(privKeySpec)

        val keyLenBytes = ByteArray(4).also { input.read(it) }
        val keyLen = bytesToInt(keyLenBytes)
        val encryptedAesKey = ByteArray(keyLen).also { input.read(it) }

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privKey)
        val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        val iv = ByteArray(12).also { input.read(it) }
        val encryptedData = input.readBytes()

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return String(aesCipher.doFinal(encryptedData), Charsets.UTF_8)
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    fun encryptFile(inputFile: java.io.File, outputFile: java.io.File, recipientPublicKeyArmored: String) {
        val pubKeyBytes = armorDecode(recipientPublicKeyArmored)
        val pubKeySpec = X509EncodedKeySpec(pubKeyBytes)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(pubKeySpec)

        val aesKeyGen = KeyGenerator.getInstance("AES")
        aesKeyGen.init(256, SecureRandom())
        val aesKey: SecretKey = aesKeyGen.generateKey()

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))

        outputFile.outputStream().use { out ->
            out.write(intToBytes(encryptedAesKey.size))
            out.write(encryptedAesKey)
            out.write(iv)
            val cipherOut = CipherOutputStream(out, aesCipher)
            inputFile.inputStream().use { input -> input.copyTo(cipherOut) }
            cipherOut.close()
        }
    }

    fun decryptFile(inputFile: java.io.File, outputFile: java.io.File, context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privKeyArmored = prefs.getString(KEY_PRIVATE, "") ?: throw Exception("No private key")
        val privKeyBytes = armorDecode(privKeyArmored)
        val privKeySpec = PKCS8EncodedKeySpec(privKeyBytes)
        val privKey = KeyFactory.getInstance("RSA").generatePrivate(privKeySpec)

        val data = inputFile.readBytes()
        val input = java.io.ByteArrayInputStream(data)

        val keyLenBytes = ByteArray(4).also { input.read(it) }
        val keyLen = bytesToInt(keyLenBytes)
        val encryptedAesKey = ByteArray(keyLen).also { input.read(it) }

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, privKey)
        val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        val iv = ByteArray(12).also { input.read(it) }
        val encryptedData = input.readBytes()

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        outputFile.outputStream().use { out -> out.write(aesCipher.doFinal(encryptedData)) }
    }

    fun isEncryptedFile(fileName: String): Boolean = fileName.endsWith(".enc", ignoreCase = true) || fileName.endsWith(".asc", ignoreCase = true) || fileName.endsWith(".gpg", ignoreCase = true)

    fun isEncryptedContent(content: String): Boolean {
        return content.startsWith("MAILCHAT_ENC:") || OpenPgpService.isEncryptedContent(content)
    }

    fun getOriginalFileName(fileName: String): String {
        return if (fileName.endsWith(".enc", ignoreCase = true)) fileName.removeSuffix(".enc") else fileName
    }

    fun getPeers(context: Context): List<PeerKey> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val peersJson = prefs.getString(KEY_PEERS, "[]") ?: "[]"
        return parsePeers(peersJson)
    }

    fun addPeer(context: Context, peer: PeerKey) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val peers = getPeers(context).toMutableList()
        peers.removeAll { it.email == peer.email }
        peers.add(peer)
        prefs.edit().putString(KEY_PEERS, serializePeers(peers)).apply()
    }

    fun removePeer(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val peers = getPeers(context).filter { it.email != email }
        prefs.edit().putString(KEY_PEERS, serializePeers(peers)).apply()
    }

    fun isPeerVerified(context: Context, email: String): Boolean {
        return getPeers(context).any { it.email == email && it.pinVerified }
    }

    fun getPeerFingerprint(context: Context, email: String): String {
        return getPeers(context).firstOrNull { it.email == email }?.fingerprint ?: ""
    }

    private fun serializePeers(peers: List<PeerKey>): String {
        val sb = StringBuilder("[")
        peers.forEachIndexed { i, p ->
            if (i > 0) sb.append(",")
            val pubEscaped = p.publicKeyArmored.replace("\"", "\\\"")
            sb.append("""{"email":"${p.email}","pub":"$pubEscaped","fp":"${p.fingerprint}","pin":${p.pinVerified}}""")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun parsePeers(json: String): List<PeerKey> {
        if (json.isBlank() || json == "[]") return emptyList()
        val result = mutableListOf<PeerKey>()
        val regex = Regex("""\{"email":"([^"]+)","pub":"((?:[^"\\]|\\.)*)","fp":"([^"]+)","pin":(true|false)\}""")
        for (match in regex.findAll(json)) {
            result.add(
                PeerKey(
                    email = match.groupValues[1],
                    publicKeyArmored = match.groupValues[2].replace("\\\"", "\""),
                    fingerprint = match.groupValues[3],
                    pinVerified = match.groupValues[4] == "true"
                )
            )
        }
        return result
    }

    fun getPublicKeyForQr(context: Context, email: String): String {
        val pubKey = getPublicKey(context)
        val fp = getFingerprint(context)
        return "MAILCHAT_PGP:$email:$fp:$pubKey"
    }

    fun parseQrData(data: String): Triple<String, String, String>? {
        if (!data.startsWith("MAILCHAT_PGP:")) return null
        val parts = data.split(":", limit = 4)
        if (parts.size >= 4) {
            return Triple(parts[1], parts[2], parts[3])
        }
        if (parts.size == 3) {
            return Triple("", parts[1], parts[2])
        }
        return null
    }
}
