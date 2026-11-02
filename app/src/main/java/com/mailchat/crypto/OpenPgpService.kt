package com.mailchat.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec

object OpenPgpService {

    data class OpenPgpKeyPair(
        val publicKeyArmored: String,
        val privateKeyArmored: String,
        val fingerprint: String
    )

    fun generateKeyPair(email: String, passphrase: String): OpenPgpKeyPair {
        val keyPairGen = java.security.KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(4096, java.security.SecureRandom())
        val javaKeyPair = keyPairGen.generateKeyPair()

        val pubBytes = javaKeyPair.public.encoded
        val privBytes = javaKeyPair.private.encoded

        val pubArmored = armorEncode("PUBLIC KEY", pubBytes)
        val privArmored = armorEncode("PRIVATE KEY", privBytes)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val fpBytes = digest.digest(pubBytes)
        val fingerprint = fpBytes.joinToString("") { "%02X".format(it) }

        return OpenPgpKeyPair(pubArmored, privArmored, fingerprint)
    }

    fun exportPublicKeyPem(armored: String): String {
        return armored
    }

    fun exportPrivateKeyPem(armored: String): String {
        return armored
    }

    fun isEncryptedContent(content: String): Boolean {
        return content.startsWith("MAILCHAT_ENC:") || content.contains("BEGIN PGP MESSAGE")
    }

    fun computeFingerprintFromArmored(armored: String): String {
        return try {
            val content = armored
                .removePrefix("-----BEGIN PUBLIC KEY-----")
                .removePrefix("-----BEGIN PRIVATE KEY-----")
                .removeSuffix("-----END PUBLIC KEY-----")
                .removeSuffix("-----END PRIVATE KEY-----")
                .trim()
            val bytes = Base64.decode(content, Base64.NO_WRAP)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val fpBytes = digest.digest(bytes)
            fpBytes.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun armorEncode(type: String, data: ByteArray): String {
        val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
        val lines = b64.chunked(64).joinToString("\n")
        return "-----BEGIN $type-----\n$lines\n-----END $type-----"
    }
}
