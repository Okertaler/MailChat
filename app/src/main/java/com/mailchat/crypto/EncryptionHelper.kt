package com.mailchat.crypto

import android.content.Context
import com.mailchat.ai.AppLogger
import com.mailchat.util.SettingsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies the app's encryption rules to a body before sending — the same rules as the normal
 * send flow (see ChatDetailViewModel.send):
 *
 *  1. OpenPGP (primary): when the app has an identity and encryption is allowed for the chat
 *     (1:1 always, group only when the chat was created as encrypted) and at least one
 *     recipient has a public key. Supports multi-recipient encryption.
 *  2. Legacy MAILCHAT_ENC fallback for the first recipient.
 *  3. Plaintext otherwise.
 *
 * Used for poll emails (create / answer / result), which must be encrypted like regular
 * messages.
 */
object EncryptionHelper {

    /** Thrown when encryption was attempted (a peer key was found) but failed. */
    class EncryptionFailed(message: String) : Exception(message)

    data class Result(
        val body: String,
        val isEncrypted: Boolean,
        val headers: Map<String, String>,
        /** The encrypted subject (see OpenPgpKeyService.encryptSubject), or null when the
         *  subject was not encrypted (use the original subject then). */
        val subject: String? = null
    )

    /**
     * Encrypts [body] for [recipients] following the rules above.
     * @param isGroupChat true for group chats (encryption only when [chatEncrypted]).
     * @param chatEncrypted the chat's "encrypted" flag (group chats).
     * @param forceUnencrypted skip all encryption (retry after a failed attempt).
     * @param subject optional subject to encrypt as well (when the "encrypt subject" setting
     *        is enabled and the OpenPGP path is used); the encrypted value is returned in
     *        [Result.subject].
     * @throws EncryptionFailed when encryption was attempted but failed.
     */
    suspend fun encryptForRecipients(
        context: Context,
        body: String,
        recipients: List<String>,
        isGroupChat: Boolean,
        chatEncrypted: Boolean,
        forceUnencrypted: Boolean = false,
        subject: String? = null
    ): Result {
        if (!forceUnencrypted) {
            // --- OpenPGP (primary) ---
            val hasId = OpenPgpKeyService.hasIdentity(context)
            val shouldEncrypt = if (isGroupChat) chatEncrypted else true
            if (hasId && shouldEncrypt) {
                // Collect the public keys of ALL recipients who have a key (multi-recipient
                // encryption so every member with a key can decrypt the message).
                val pubKeys = withContext(Dispatchers.IO) {
                    recipients.mapNotNull { OpenPgpKeyService.currentPeerKey(context, it) }
                }
                AppLogger.log(context, "EncryptionHelper: peer keys found=${pubKeys.size}/${recipients.size}")
                if (pubKeys.isNotEmpty()) {
                    try {
                        // Optionally append the key signature (own public key or a link to it)
                        // so the recipient can reply encrypted. Only part of the sent body.
                        val keySig = SettingsUtils.getEncryptedKeySignatureText(context)
                        val bodyToEncrypt = if (keySig.isNotBlank()) "$body\n\n$keySig" else body
                        val encrypted = withContext(Dispatchers.IO) {
                            if (pubKeys.size > 1) {
                                OpenPgpKeyService.signAndEncryptForRecipients(context, bodyToEncrypt, pubKeys)
                            } else {
                                OpenPgpKeyService.signAndEncryptForRecipient(context, bodyToEncrypt, pubKeys.first())
                            }
                        }
                        // Optionally encrypt the subject as well (never fails the send).
                        val encryptedSubject = withContext(Dispatchers.IO) {
                            if (subject.isNullOrBlank() || !OpenPgpKeyService.isEncryptSubjectEnabled(context)) return@withContext null
                            try {
                                OpenPgpKeyService.encryptSubject(context, subject, pubKeys)
                            } catch (e: Exception) {
                                AppLogger.log(context, "EncryptionHelper: subject encryption failed: ${e.message}")
                                null
                            }
                        }
                        return Result(encrypted, true, mapOf("X-MailChat-Encryption" to "OpenPGP"), encryptedSubject)
                    } catch (e: Exception) {
                        AppLogger.log(context, "EncryptionHelper: OpenPGP encryption failed: ${e.message}")
                        throw EncryptionFailed("OpenPGP: ${e.message}")
                    }
                }
            }

            // --- Legacy MAILCHAT_ENC fallback (first recipient only) ---
            val firstRecipient = recipients.firstOrNull() ?: ""
            val peer = CryptoService.getPeers(context)
                .firstOrNull { it.email.lowercase() == firstRecipient && it.pinVerified }
            if (peer != null) {
                try {
                    val encrypted = CryptoService.encryptForRecipient(body, peer.publicKeyArmored)
                    return Result(
                        "MAILCHAT_ENC:$encrypted",
                        true,
                        mapOf(
                            "X-MailChat-Encryption" to "AES-256-GCM",
                            "X-MailChat-Fingerprint" to peer.fingerprint
                        )
                    )
                } catch (e: Exception) {
                    AppLogger.log(context, "EncryptionHelper: legacy encryption failed: ${e.message}")
                    throw EncryptionFailed("Legacy: ${e.message}")
                }
            }
        }
        return Result(body, false, emptyMap())
    }
}
