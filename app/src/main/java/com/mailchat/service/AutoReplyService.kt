package com.mailchat.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.mailchat.data.model.EmailAccount
import com.mailchat.data.model.Message
import com.mailchat.data.repository.ChatRepository
import com.mailchat.data.repository.MessageRepository
import com.mailchat.network.SmtpService
import com.mailchat.network.WeatherService
import com.mailchat.util.SettingsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the automatic "Wo bist Du?" reply: when a contact with the auto-location-reply enabled
 * asks "Wo bist Du?", the app replies with the user's current location (and, if enabled, the
 * local weather for the next 4 hours fetched from open-meteo.com).
 *
 * The reply is sent only once per message (tracked by message ID) and only to the contact for
 * whom the feature was explicitly enabled.
 */
@Singleton
class AutoReplyService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val smtpService: SmtpService,
    private val weatherService: WeatherService,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository
) {

    /**
     * Checks whether [message] is a "Wo bist Du?" question from a contact with the auto-reply
     * enabled, and if so, sends the automatic reply (location + optional weather).
     * Returns true if a reply was sent.
     */
    suspend fun maybeAutoReply(account: EmailAccount, message: Message): Boolean {
        if (!message.isIncoming) return false
        val sender = message.from.trim().lowercase()
        if (sender.isBlank()) return false
        if (!SettingsUtils.getAutoLocationReply(appContext, sender)) return false
        if (!isWhereAreYouQuestion(message.bodyFull.ifBlank { message.body })) return false
        // Avoid replying twice to the same message (e.g. on re-sync).
        if (message.messageId.isNotBlank() &&
            message.messageId in SettingsUtils.getAutoRepliedMessageIds(appContext)
        ) return false

        // The location permission is required to read the current position.
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            com.mailchat.ai.AppLogger.log(appContext, "Auto-reply skipped: no location permission")
            return false
        }

        val location = getLocation()
        if (location == null) {
            com.mailchat.ai.AppLogger.log(appContext, "Auto-reply skipped: no location available")
            return false
        }
        val lat = location.latitude
        val lon = location.longitude

        // Build the reply text (location links).
        val sb = StringBuilder()
        sb.append("📍 Mein Standort:\n")
        sb.append("OpenStreetMap: https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon\n")
        sb.append("Google Maps: https://www.google.com/maps?q=$lat,$lon\n")
        sb.append("Apple Maps: https://maps.apple.com/?ll=$lat,$lon")

        // Optionally include the weather for the next 4 hours. The weather does not need
        // street-level precision, so a coarsened location (≈1 km) is used for the query and
        // the place name — more privacy-friendly than sending the exact coordinates.
        if (SettingsUtils.getAutoWeather(appContext, sender)) {
            val wLat = Math.round(lat * 100.0) / 100.0
            val wLon = Math.round(lon * 100.0) / 100.0
            val weather = weatherService.getWeatherForNext4Hours(wLat, wLon)
            if (weather != null) {
                val place = getPlaceName(wLat, wLon)
                sb.append("\n\n🌦️ Wetter in ${place.ifBlank { "deiner Region" }} für die nächsten 4 Stunden:\n")
                sb.append(weather)
                sb.append("\nhttps://open-meteo.com/")
            }
        }

        // Send the reply (encrypted when the recipient has a public key, same as a normal send).
        return try {
            val subject = if (message.subject.isNotBlank()) {
                if (message.subject.startsWith("Re:", ignoreCase = true) ||
                    message.subject.startsWith("Aw:", ignoreCase = true)
                ) message.subject else "Re: ${message.subject}"
            } else "Wo bist Du?"
            val plainBody = sb.toString()

            val extraHeaders = mutableMapOf<String, String>()
            if (message.messageId.isNotBlank()) {
                extraHeaders["In-Reply-To"] = message.messageId
                extraHeaders["References"] = message.messageId
            }

            // --- Encryption: OpenPGP (primary) + legacy MAILCHAT_ENC fallback, same as the
            // normal send flow. The local copy is always stored in plaintext; the lock icon in
            // the chat comes from the isEncrypted flag. ---
            var finalBody = plainBody
            var isEncryptedMsg = false
            if (com.mailchat.crypto.OpenPgpKeyService.hasIdentity(appContext)) {
                withContext(Dispatchers.IO) {
                    try {
                        val pubKey = com.mailchat.crypto.OpenPgpKeyService.currentPeerKey(appContext, sender)
                        if (!pubKey.isNullOrBlank()) {
                            // Optionally append the key signature (own public key or a link to
                            // it) so the recipient can reply encrypted.
                            val keySig = SettingsUtils.getEncryptedKeySignatureText(appContext)
                            val bodyToEncrypt = if (keySig.isNotBlank()) "$plainBody\n\n$keySig" else plainBody
                            finalBody = com.mailchat.crypto.OpenPgpKeyService.signAndEncryptForRecipient(appContext, bodyToEncrypt, pubKey)
                            isEncryptedMsg = true
                            extraHeaders["X-MailChat-Encryption"] = "OpenPGP"
                            com.mailchat.ai.AppLogger.log(appContext, "Auto-reply: signed+encrypted with OpenPGP")
                        } else {
                            com.mailchat.ai.AppLogger.log(appContext, "Auto-reply: no public key for $sender -> plaintext")
                        }
                    } catch (e: Exception) {
                        com.mailchat.ai.AppLogger.log(appContext, "Auto-reply: OpenPGP encryption failed: ${e.message}")
                    }
                }
            }
            if (!isEncryptedMsg) {
                val peer = com.mailchat.crypto.CryptoService.getPeers(appContext)
                    .firstOrNull { it.email.lowercase() == sender && it.pinVerified }
                if (peer != null) {
                    try {
                        val encrypted = com.mailchat.crypto.CryptoService.encryptForRecipient(plainBody, peer.publicKeyArmored)
                        finalBody = "MAILCHAT_ENC:$encrypted"
                        isEncryptedMsg = true
                        extraHeaders["X-MailChat-Encryption"] = "AES-256-GCM"
                        extraHeaders["X-MailChat-Fingerprint"] = peer.fingerprint
                        com.mailchat.ai.AppLogger.log(appContext, "Auto-reply: encrypted with legacy MAILCHAT_ENC")
                    } catch (e: Exception) {
                        com.mailchat.ai.AppLogger.log(appContext, "Auto-reply: legacy encryption failed: ${e.message}")
                    }
                }
            }

            val sentMessageId = smtpService.sendEmail(
                account = account,
                to = sender,
                subject = subject,
                body = finalBody,
                extraHeaders = extraHeaders
            )
            if (message.messageId.isNotBlank()) {
                SettingsUtils.addAutoRepliedMessageId(appContext, message.messageId)
            }

            // Store the reply locally so it shows up in this chat (with the lock icon when
            // encrypted) — the auto-reply is sent directly via SMTP and would otherwise be
            // invisible to the user.
            if (message.chatId > 0) {
                try {
                    val sentMsg = Message(
                        chatId = message.chatId,
                        accountId = account.id,
                        from = account.emailAddress,
                        subject = subject,
                        body = plainBody,
                        bodyFull = plainBody,
                        timestamp = System.currentTimeMillis(),
                        isRead = true,
                        isIncoming = false,
                        messageId = sentMessageId,
                        isEncrypted = isEncryptedMsg
                    )
                    messageRepository.insertMessage(sentMsg)
                    chatRepository.getChatById(message.chatId)?.let { chat ->
                        chatRepository.updateChat(
                            chat.copy(
                                lastMessageTimestamp = System.currentTimeMillis(),
                                lastMessageBody = plainBody,
                                lastMessageSubject = subject
                            )
                        )
                    }
                } catch (e: Exception) {
                    com.mailchat.ai.AppLogger.log(appContext, "Auto-reply: local save failed: ${e.message}")
                }
            }
            com.mailchat.ai.AppLogger.log(appContext, "Auto-reply 'Wo bist Du?' sent to $sender (encrypted=$isEncryptedMsg)")
            true
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(appContext, "Auto-reply 'Wo bist Du?' failed: ${e.message}")
            false
        }
    }

    /** Whether the text looks like a "Wo bist Du?" (where are you) question. */
    private fun isWhereAreYouQuestion(text: String): Boolean {
        val normalized = text.lowercase()
            .replace("?", " ")
            .replace("!", " ")
            .replace(".", " ")
            .trim()
        return normalized.contains("wo bist du") ||
            normalized.contains("wo bist ihr") ||
            normalized.contains("wo seid ihr") ||
            normalized.contains("where are you")
    }

    /**
     * Reverse-geocodes [lat]/[lon] to a short place name (city/town) for the weather line.
     * Returns an empty string if the lookup fails or returns nothing.
     */
    private fun getPlaceName(lat: Double, lon: Double): String {
        return try {
            val geocoder = android.location.Geocoder(appContext, java.util.Locale.getDefault())
            val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return ""
            listOfNotNull(
                address.locality?.takeIf { it.isNotBlank() },
                address.subAdminArea?.takeIf { it.isNotBlank() },
                address.adminArea?.takeIf { it.isNotBlank() }
            ).firstOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Returns the current location: first the last known location (fast path). If none is
     * available, a fresh fix is requested (GPS first, then network) with a timeout. Returns
     * null if no location can be determined (the caller logs the failure).
     */
    private suspend fun getLocation(): Location? {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        // 1) Fast path: last known location.
        val lastKnown = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) {
            null
        }
        if (lastKnown != null) return lastKnown

        // 2) No last known location: request a fresh fix.
        val fresh = requestFreshLocation(lm)
        if (fresh == null) {
            com.mailchat.ai.AppLogger.log(
                appContext,
                "Auto-reply: lastKnownLocation was null and the fresh location request failed or timed out"
            )
        }
        return fresh
    }

    /**
     * Requests a single fresh location fix (GPS first, then network). Each attempt has its own
     * timeout; returns null if all attempts fail or time out.
     */
    private suspend fun requestFreshLocation(lm: LocationManager): Location? {
        val attempts = listOf(
            LocationManager.GPS_PROVIDER to 20_000L,
            LocationManager.NETWORK_PROVIDER to 15_000L
        )
        for ((provider, timeoutMs) in attempts) {
            val available = try {
                lm.getProviders(false).contains(provider)
            } catch (e: Exception) {
                false
            }
            if (!available) continue
            val deferred = CompletableDeferred<Location>()
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    deferred.complete(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onProviderEnabled(provider: String) {}

                override fun onProviderDisabled(provider: String) {}
            }
            try {
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                continue
            }
            // requestSingleUpdate delivers at most one fix and then removes the listener
            // automatically; if the timeout fires first, a late fix is simply ignored.
            val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (result != null) return result
        }
        return null
    }
}
