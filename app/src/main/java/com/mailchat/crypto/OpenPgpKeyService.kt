package com.mailchat.crypto

import android.content.Context
import android.util.Base64
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.bcpg.sig.SignatureCreationTime
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPKeyFlags
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.PGPDigestCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Real OpenPGP (RFC 4880) crypto backed by BouncyCastle bcpg.
 *
 * Replaces the legacy RSA/PEM "MAILCHAT_ENC" scheme with standard OpenPGP so keys are
 * interoperable with keys.openpgp.org and other OpenPGP tools.
 *
 * All crypto methods are blocking (CPU-bound) and must be invoked off the main thread
 * (e.g. Dispatchers.IO).
 */
object OpenPgpKeyService {

    private const val PREFS_NAME = "mailchat_openpgp"
    private const val KEY_SECRET = "openpgp_secret_key"
    private const val KEY_PUBLIC = "openpgp_public_key"
    private const val KEY_FINGERPRINT = "openpgp_fingerprint"
    private const val KEY_PASSPHRASE = "openpgp_passphrase"
    private const val KEY_PEERS = "openpgp_peers"
    private const val KEY_AUTO_LOOKUP = "openpgp_auto_lookup"
    private const val KEY_AUTO_WRITEBACK = "openpgp_auto_writeback"
    private const val KEY_AUTO_WRITEBACK_INBOX = "openpgp_auto_writeback_inbox"

    private const val KEY_SIZE = 3072
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "mailchat_openpgp_passphrase_key"

    /**
     * Register the BouncyCastle JCE provider if not already present.
     *
     * The JCE-backed OpenPGP operators (e.g. [JcePublicKeyKeyEncryptionMethodGenerator]) perform
     * ECDH key agreement through the JCE. Android's built-in JCE handles RSA and NIST-curve ECDH,
     * but NOT X25519 (Curve25519) — that algorithm (`X25519withSHA256CKDF`) is only provided by
     * BouncyCastle. Without this registration, encrypting to a contact whose key uses an X25519
     * subkey fails with "Algorithm X25519withSHA256CKDF not available".
     */
    private fun ensureProvider() {
        ensureProviderRegistered()
    }

    /**
     * Register the BouncyCastle provider if needed and report whether it is now available.
     * Public so callers can log the provider state for diagnostics.
     */
    fun ensureProviderRegistered(): Boolean {
        val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (existing != null) {
            // Some ROMs (e.g. HarmonyOS) pre-register a stripped-down BouncyCastle provider
            // under the name "BC" that lacks X25519WITHSHA256CKDF. Check whether the existing
            // provider actually supports the X25519 key agreement we need; if not, remove it
            // so the full BouncyCastle can be registered in its place.
            if (hasX25519CKDF(existing)) {
                return true
            }
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        }
        Security.addProvider(BouncyCastleProvider())
        return Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null
    }

    private fun hasX25519CKDF(provider: Provider): Boolean {
        return provider.services.any {
            it.type == "KeyAgreement" && it.algorithm.equals("X25519WITHSHA256CKDF", ignoreCase = true)
        }
    }

    private var jceDiagnosticsDone = false

    /**
     * One-shot JCE diagnostics (per process) to pinpoint why
     * `KeyAgreement.getInstance("X25519withSHA256CKDF")` is reported as "not available"
     * on Android even though BouncyCastle is registered.
     *
     * The JCE framework silently skips a provider whose service class fails to load or
     * instantiate (e.g. NoClassDefFoundError) and then reports "not available", so the
     * real error is only visible when the SPI class is instantiated directly.
     *
     * Returns the diagnostic string on the first call, null afterwards.
     */
    fun jceDiagnosticsOnce(): String? {
        if (jceDiagnosticsDone) return null
        jceDiagnosticsDone = true
        val sb = StringBuilder()
        try {
            sb.append("providers: ")
            for (p in Security.getProviders()) {
                sb.append(p.name).append(" v").append(p.version).append("; ")
            }
            val bc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (bc != null) {
                val kaAlgos = bc.services.filter { it.type == "KeyAgreement" }.map { it.algorithm }.sorted()
                sb.append(" | BC provider class: ").append(bc.javaClass.name)
                sb.append(" | BC version: ").append(bc.version)
                sb.append(" | BC KeyAgreement services: ").append(kaAlgos.size)
                    .append(" (X25519WITHSHA256CKDF present: ")
                    .append(kaAlgos.any { it.equals("X25519WITHSHA256CKDF", ignoreCase = true) })
                    .append(")")
                // Check whether the app's own BC SPI classes are loadable (distinguishes shadowing
                // by a system BC on the boot classpath from a full app-bundled BC).
                try {
                    Class.forName("org.bouncycastle.jcajce.provider.asymmetric.edec.KeyAgreementSpi\$X25519withSHA256CKDF")
                    sb.append(" | app BC SPI classes: loadable")
                } catch (e: ClassNotFoundException) {
                    sb.append(" | app BC SPI classes: NOT loadable (shadowed?)")
                }
            } else {
                sb.append(" | BC provider NOT registered")
            }
            try {
                val ka = javax.crypto.KeyAgreement.getInstance("X25519withSHA256CKDF")
                sb.append(" | KeyAgreement(X25519withSHA256CKDF) OK via ").append(ka.provider.name)
            } catch (t: Throwable) {
                sb.append(" | KeyAgreement(X25519withSHA256CKDF) FAILED: ").append(t)
            }
            try {
                javax.crypto.KeyAgreement.getInstance("X25519withSHA256CKDF", "BC")
                sb.append(" | KeyAgreement(..., \"BC\") OK")
            } catch (t: Throwable) {
                sb.append(" | KeyAgreement(..., \"BC\") FAILED: ").append(t)
            }
            try {
                val kpg = KeyPairGenerator.getInstance("X25519")
                sb.append(" | KeyPairGenerator(X25519) OK via ").append(kpg.provider.name)
            } catch (t: Throwable) {
                sb.append(" | KeyPairGenerator(X25519) FAILED: ").append(t)
            }
            // Direct SPI instantiation — surfaces the real error the JCE framework swallows.
            try {
                val cls = Class.forName("org.bouncycastle.jcajce.provider.asymmetric.edec.KeyAgreementSpi\$X25519withSHA256CKDF")
                val ctor = cls.getDeclaredConstructor()
                ctor.isAccessible = true
                ctor.newInstance()
                sb.append(" | direct SPI instantiation OK")
            } catch (t: Throwable) {
                sb.append(" | direct SPI instantiation FAILED: ").append(t)
            }
        } catch (t: Throwable) {
            sb.append(" | diagnostics error: ").append(t)
        }
        return sb.toString()
    }

    data class OpenPgpIdentity(
        val publicKeyArmored: String,
        val privateKeyArmored: String,
        val fingerprint: String
    )

    data class OpenPgpPeer(
        val email: String,
        val publicKeyArmored: String,
        val fingerprint: String
    )

    // ------------------------------------------------------------------
    // Identity (own key)
    // ------------------------------------------------------------------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasIdentity(context: Context): Boolean =
        prefs(context).getString(KEY_SECRET, null) != null

    fun getFingerprint(context: Context): String =
        prefs(context).getString(KEY_FINGERPRINT, "") ?: ""

    fun getPublicKeyArmored(context: Context): String =
        prefs(context).getString(KEY_PUBLIC, "") ?: ""

    fun getSecretKeyArmored(context: Context): String =
        prefs(context).getString(KEY_SECRET, "") ?: ""

    /**
     * The passphrase protecting the secret key, decrypted from the Android Keystore.
     * Returns an empty string if no identity exists or the Keystore key is unavailable
     * (e.g. after restoring a backup on a different device).
     */
    fun getPassphrase(context: Context): String =
        decryptPassphrase(prefs(context).getString(KEY_PASSPHRASE, "") ?: "")

    /**
     * The passphrase in its Keystore-encrypted form (for backup/restore).
     */
    fun getEncryptedPassphrase(context: Context): String =
        prefs(context).getString(KEY_PASSPHRASE, "") ?: ""

    /**
     * The email address to associate with the identity: the selected sender account if set,
     * otherwise the first account.
     */
    fun getKeyEmail(context: Context): String {
        val senderId = com.mailchat.util.SettingsUtils.getSenderAccountId(context)
        if (senderId > 0) {
            try {
                val db = com.mailchat.data.database.MailchatDatabase.getInstance(context)
                val account = kotlinx.coroutines.runBlocking { db.accountDao().getAccountById(senderId) }
                if (account != null) return account.emailAddress
            } catch (_: Exception) {
            }
        }
        return try {
            val db = com.mailchat.data.database.MailchatDatabase.getInstance(context)
            val account = kotlinx.coroutines.runBlocking { db.accountDao().getFirstAccount() }
            account?.emailAddress ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * The email address stored in the generated key's User ID ("" if no key exists or no
     * email can be parsed). This is the address the key was created for — it may differ
     * from [getKeyEmail] if the sender account changed after key generation.
     */
    fun getKeyEmailFromKey(context: Context): String {
        val pub = getPublicKeyArmored(context)
        if (pub.isBlank()) return ""
        return try {
            val ring = parsePublicKeyRing(pub)
            val userId = ring.publicKey.userIDs.asSequence().firstOrNull() ?: ""
            // User ID is "Name <email>" (RFC 2822) or a bare email.
            Regex("<([^>]+)>").find(userId)?.groupValues?.get(1)?.trim() ?: userId.trim()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Generate a fresh OpenPGP identity: an RSA primary key (sign) plus an RSA encryption
     * subkey, protected by [passphrase]. Pure — no persistence.
     */
    fun generate(userId: String, passphrase: String): OpenPgpIdentity {
        val (secretKeyRing, publicKeyRing) = generateKeyPair(normalizeUserId(userId), passphrase.toCharArray())
        val pubArmored = armorEncode(publicKeyRing.getEncoded())
        val secArmored = armorEncode(secretKeyRing.getEncoded())
        val fingerprint = fingerprintHex(publicKeyRing.publicKey.getFingerprint())
        return OpenPgpIdentity(pubArmored, secArmored, fingerprint)
    }

    /**
     * Generate a fresh OpenPGP identity and store it (armored key rings, fingerprint, passphrase).
     */
    fun generateIdentity(context: Context, userId: String, passphrase: String): OpenPgpIdentity {
        val identity = generate(userId, passphrase)
        prefs(context).edit()
            .putString(KEY_PUBLIC, identity.publicKeyArmored)
            .putString(KEY_SECRET, identity.privateKeyArmored)
            .putString(KEY_FINGERPRINT, identity.fingerprint)
            .putString(KEY_PASSPHRASE, encryptPassphrase(passphrase))
            .apply()
        return identity
    }

    fun deleteIdentity(context: Context) {
        prefs(context).edit()
            .remove(KEY_PUBLIC)
            .remove(KEY_SECRET)
            .remove(KEY_FINGERPRINT)
            .remove(KEY_PASSPHRASE)
            .apply()
    }

    /**
     * Imports a passphrase-protected OpenPGP secret key (armored, e.g. from a .asc file) and
     * replaces the current identity. The [passphrase] is verified by extracting the private
     * key; if it is wrong the import fails and nothing is changed. Returns true on success.
     */
    fun importSecretKey(context: Context, armored: String, passphrase: String): Boolean {
        return try {
            val secretKeyRing = parseSecretKeyRing(armored)
            // Verify the passphrase by extracting the primary key's private key. If the
            // passphrase is wrong this throws and nothing is changed.
            secretKeyRing.secretKey.extractPrivateKey(
                JcePBESecretKeyDecryptorBuilder().build(passphrase.toCharArray())
            )
            // Derive the public key and fingerprint from the secret key ring.
            val pubKey = secretKeyRing.publicKey
            val pubArmored = armorEncode(pubKey.encoded)
            val fingerprint = fingerprintHex(pubKey.getFingerprint())
            prefs(context).edit()
                .putString(KEY_PUBLIC, pubArmored)
                .putString(KEY_SECRET, armored)
                .putString(KEY_FINGERPRINT, fingerprint)
                .putString(KEY_PASSPHRASE, encryptPassphrase(passphrase))
                .apply()
            true
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(context, "OpenPGP key import failed: ${e.message}")
            false
        }
    }

    /**
     * Whether MailChat may automatically look up a recipient's public key on keys.openpgp.org
     * when sending. Enabled by default.
     */
    fun isAutoLookupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_LOOKUP, true)

    fun setAutoLookupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_LOOKUP, enabled).apply()
    }

    /**
     * Whether encrypted sent emails are automatically replaced in the Sent folder with a
     * decrypted version (so all email clients can read them). Enabled by default.
     */
    fun isAutoWriteBackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_WRITEBACK, true)

    fun setAutoWriteBackEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_WRITEBACK, enabled).apply()
    }

    /**
     * Whether encrypted incoming emails are additionally replaced in the INBOX with a
     * decrypted version (same Message-ID, date and headers, so threading keeps working).
     * Off by default: the original encrypted message is removed from the inbox, so this
     * is opt-in.
     */
    fun isAutoWriteBackInboxEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_WRITEBACK_INBOX, false)

    fun setAutoWriteBackInboxEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_WRITEBACK_INBOX, enabled).apply()
    }

    /**
     * Result of publishing the own key to keys.openpgp.org.
     */
    enum class PublishResult {
        /** Key uploaded and a verification email was requested. The key only becomes
         *  findable by email address after the user clicks the link in that email. */
        VERIFICATION_SENT,
        /** Key uploaded, but the verification email could not be requested. */
        UPLOADED,
        /** The upload itself failed. */
        FAILED
    }

    /**
     * Publish the own public key to keys.openpgp.org so others can find it by email.
     *
     * Uses the VKS API (see https://keys.openpgp.org/about/api):
     *  1. POST /vks/v1/upload         -> uploads the key, returns an opaque `token`
     *  2. POST /vks/v1/request-verify -> sends a verification email for the key's address
     *
     * The key is only findable by email address after the user clicks the link in the
     * verification email. Returns a [PublishResult]. Must be called off the main thread.
     */
    fun publishKey(context: Context): PublishResult {
        val publicKey = getPublicKeyArmored(context)
        if (publicKey.isBlank()) return PublishResult.FAILED

        // 1. Upload the key (JSON body, per the VKS API).
        val upload = postJson("https://keys.openpgp.org/vks/v1/upload", JSONObject().put("keytext", publicKey))
            ?: return PublishResult.FAILED
        val token = try { upload.optString("token") } catch (_: Exception) { "" }
        if (token.isBlank()) return PublishResult.UPLOADED

        // 2. Determine the email address(es) actually contained in the key. The upload
        //    response's "status" object is keyed by the key's email addresses. We MUST
        //    request verification for those: the server silently ignores (HTTP 200, no
        //    email sent) any address that is not part of the key. Using the account's
        //    current email here would fail whenever it differs from the key's User ID.
        val keyEmails = jsonKeyList(upload.optJSONObject("status"))
        if (keyEmails.isEmpty()) {
            com.mailchat.ai.AppLogger.log(context, "Publish: key has no email in its User ID – cannot request verification")
            return PublishResult.UPLOADED
        }
        val accountEmail = getKeyEmail(context)
        if (accountEmail.isNotBlank() && keyEmails.none { it.equals(accountEmail, ignoreCase = true) }) {
            com.mailchat.ai.AppLogger.log(
                context,
                "Publish: account email $accountEmail not in key (key has $keyEmails) – verifying the key's own address"
            )
        }

        // 3. Request a verification email for the key's address(es).
        val addresses = JSONArray()
        keyEmails.forEach { addresses.put(it) }
        val verifyBody = JSONObject()
            .put("token", token)
            .put("addresses", addresses)
            .put("locale", JSONArray().put("de_DE").put("en"))
        val verify = postJson("https://keys.openpgp.org/vks/v1/request-verify", verifyBody)
            ?: return PublishResult.UPLOADED

        // 4. Confirm the server actually queued a verification email ("pending") or the
        //    address is already findable ("published").
        val status = jsonStatusValues(verify.optJSONObject("status"))
        val ok = status.contains("pending") || status.contains("published")
        com.mailchat.ai.AppLogger.log(context, "Publish: verification for $keyEmails -> $status")
        return if (ok) PublishResult.VERIFICATION_SENT else PublishResult.UPLOADED
    }

    /**
     * Delete the own public key from keys.openpgp.org.
     *
     * Uses the VKS API (see https://keys.openpgp.org/about/api):
     *  POST /vks/v1/delete with the armored public key as `keytext`.
     *
     * Returns true if the server accepted the deletion (HTTP 2xx), false otherwise.
     * Must be called off the main thread.
     */
    fun deleteKeyFromDirectory(context: Context): Boolean {
        val publicKey = getPublicKeyArmored(context)
        if (publicKey.isBlank()) return false
        val response = postJson(
            "https://keys.openpgp.org/vks/v1/delete",
            JSONObject().put("keytext", publicKey)
        )
        com.mailchat.ai.AppLogger.log(context, "Delete key from keys.openpgp.org -> ${if (response != null) "ok" else "failed"}")
        return response != null
    }

    /** Returns the keys of a JSON object (used for the "status" map: email -> state). */
    private fun jsonKeyList(obj: JSONObject?): List<String> {
        if (obj == null) return emptyList()
        return try {
            val result = mutableListOf<String>()
            val it = obj.keys()
            while (it.hasNext()) result.add(it.next())
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Returns the values of a JSON object (used for the "status" map: email -> state). */
    private fun jsonStatusValues(obj: JSONObject?): List<String> {
        if (obj == null) return emptyList()
        return try {
            val result = mutableListOf<String>()
            val it = obj.keys()
            while (it.hasNext()) result.add(obj.optString(it.next()))
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * POST [body] as `application/json` to [url]. Returns the parsed JSON response, or null
     * on any error (non-2xx status, network failure, or unparseable body).
     */
    private fun postJson(url: String, body: JSONObject): JSONObject? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mailchat/1.0")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Key generation
    // ------------------------------------------------------------------

    /**
     * Ensure the User ID is in RFC 2822 format ("Name <email>") so keys.openpgp.org (Hagrid)
     * can extract the email address. A bare email without angle brackets is NOT recognized as
     * an email by Hagrid (the upload "status" map comes back empty and no verification email
     * can be sent). If [userId] already contains angle brackets it is returned unchanged.
     */
    private fun normalizeUserId(userId: String): String {
        val trimmed = userId.trim()
        if (trimmed.contains("<") && trimmed.contains(">")) return trimmed
        val name = trimmed.substringBefore('@').ifBlank { "MailChat" }
        return "$name <$trimmed>"
    }

    private fun generateKeyPair(
        userId: String,
        passphrase: CharArray
    ): Pair<PGPSecretKeyRing, PGPPublicKeyRing> {
        ensureProvider()
        val signKeyPair = JcaPGPKeyPair(
            PublicKeyAlgorithmTags.RSA_SIGN,
            KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair(),
            Date()
        )
        val encKeyPair = JcaPGPKeyPair(
            PublicKeyAlgorithmTags.RSA_ENCRYPT,
            KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair(),
            Date()
        )

        val digestProvider = JcaPGPDigestCalculatorProviderBuilder().build()
        // OpenPGP mandates SHA-1 for the secret-key checksum.
        val sha1: PGPDigestCalculator = digestProvider.get(HashAlgorithmTags.SHA1)
        val contentSigner = JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_SIGN, HashAlgorithmTags.SHA256)
        val pbeEncryptor = JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1).build(passphrase)

        // Hashed subpackets for the primary key's self-signature: key flags and
        // creation time. Without the key-flags subpacket keys.openpgp.org (Hagrid)
        // rejects the key and extracts no email address from the User ID.
        val primarySubpackets = PGPSignatureSubpacketVector.fromSubpackets(
            arrayOf(
                KeyFlags(false, PGPKeyFlags.CAN_SIGN),
                SignatureCreationTime(true, Date())
            )
        )

        // NOTE: the first PGPKeyRingGenerator parameter is the certification level
        // (signature class of the User ID binding signature), NOT the key flags.
        val generator = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            signKeyPair,
            userId,
            sha1,
            primarySubpackets,
            null,
            contentSigner,
            pbeEncryptor
        )

        // Hashed subpackets for the encryption subkey's binding signature.
        val subkeySubpackets = PGPSignatureSubpacketVector.fromSubpackets(
            arrayOf(
                KeyFlags(false, PGPKeyFlags.CAN_ENCRYPT_COMMS),
                SignatureCreationTime(true, Date())
            )
        )
        generator.addSubKey(encKeyPair, subkeySubpackets, null)

        return Pair(generator.generateSecretKeyRing(), generator.generatePublicKeyRing())
    }

    // ------------------------------------------------------------------
    // Encryption
    // ------------------------------------------------------------------

    /**
     * Encrypt [plaintext] for the recipient identified by [recipientPublicKeyArmored]
     * (a standard armored OpenPGP public key). Returns an armored PGP MESSAGE.
     */
    fun encrypt(plaintext: String, recipientPublicKeyArmored: String): String {
        val encrypted = encryptData(plaintext.toByteArray(Charsets.UTF_8), "mailchat.txt", recipientPublicKeyArmored)
        return armorEncode(encrypted)
    }

    /**
     * Encrypt [inputFile] for the recipient and write a binary OpenPGP message to [outputFile].
     * The original file name is stored in the literal data packet for restoration on decrypt.
     */
    fun encryptFile(inputFile: java.io.File, outputFile: java.io.File, recipientPublicKeyArmored: String): java.io.File {
        val data = inputFile.readBytes()
        val encrypted = encryptData(data, inputFile.name, recipientPublicKeyArmored)
        outputFile.writeBytes(encrypted)
        return outputFile
    }

    private fun encryptData(data: ByteArray, fileName: String, recipientPublicKeyArmored: String): ByteArray {
        ensureProvider()
        val ring = parsePublicKeyRing(recipientPublicKeyArmored)
        val encKey = findEncryptionKey(ring)

        val inner = ByteArrayOutputStream()
        val compOut: OutputStream = PGPCompressedDataGenerator(CompressionAlgorithmTags.ZLIB).open(inner)
        val litOut: OutputStream = PGPLiteralDataGenerator()
            .open(compOut, 'b', fileName, data.size.toLong(), Date())
        litOut.write(data)
        litOut.close()
        compOut.close()
        val innerData = inner.toByteArray()

        val message = ByteArrayOutputStream()
        val encGen = PGPEncryptedDataGenerator(JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256))
        val keyGen = JcePublicKeyKeyEncryptionMethodGenerator(encKey)
        // X25519/X448 key agreement is only provided by BouncyCastle. Some ROMs (e.g. HarmonyOS)
        // pre-register a stripped-down "BC" provider that lacks it, so pin the generator to our
        // own provider that exposes BC's X25519/X448 SPIs (see MailChatX25519Provider).
        if (encKey.algorithm == PublicKeyAlgorithmTags.X25519 || encKey.algorithm == PublicKeyAlgorithmTags.X448) {
            keyGen.setProvider(MailChatX25519Provider())
        }
        encGen.addMethod(keyGen)
        val encOut: OutputStream = encGen.open(message, innerData.size.toLong())
        encOut.write(innerData)
        encOut.close()

        return message.toByteArray()
    }

    // ------------------------------------------------------------------
    // Decryption
    // ------------------------------------------------------------------

    /**
     * Decrypt an armored PGP MESSAGE using the stored identity. Uses the passphrase stored at
     * key-generation time.
     */
    fun decrypt(context: Context, armoredMessage: String): String =
        decrypt(context, armoredMessage, getPassphrase(context).toCharArray())

    fun decrypt(context: Context, armoredMessage: String, passphrase: CharArray): String {
        val secretKeyArmored = prefs(context).getString(KEY_SECRET, "")
            ?: throw IllegalStateException("No OpenPGP identity")
        return decrypt(armoredMessage, secretKeyArmored, passphrase)
    }

    /**
     * Decrypt an armored PGP MESSAGE with the given secret key. Pure — no persistence.
     */
    fun decrypt(armoredMessage: String, secretKeyArmored: String, passphrase: CharArray): String =
        decryptData(armoredMessage.toByteArray(), secretKeyArmored, passphrase).first
            .let { String(it, Charsets.UTF_8) }

    /**
     * Decrypt an OpenPGP-encrypted [inputFile] using the stored identity and write the
     * plaintext to [outputFile]. Returns the file name recorded in the literal data packet
     * (the original name), or an empty string if unavailable.
     */
    fun decryptFile(inputFile: java.io.File, outputFile: java.io.File, context: Context): String {
        val secretKeyArmored = prefs(context).getString(KEY_SECRET, "")
            ?: throw IllegalStateException("No OpenPGP identity")
        val (content, originalName) = decryptData(inputFile.readBytes(), secretKeyArmored, getPassphrase(context).toCharArray())
        outputFile.writeBytes(content)
        return originalName
    }

    /**
     * Decrypt OpenPGP [data] (armored or binary) with the given secret key.
     * Returns the plaintext bytes and the file name recorded in the literal data packet.
     */
    fun decryptData(
        data: ByteArray,
        secretKeyArmored: String,
        passphrase: CharArray
    ): Pair<ByteArray, String> {
        ensureProvider()
        val secretKeyRing = parseSecretKeyRing(secretKeyArmored)

        val decoderStream = PGPUtil.getDecoderStream(ByteArrayInputStream(data))
        val objectFactory = PGPObjectFactory(decoderStream, JcaKeyFingerprintCalculator())
        val first = objectFactory.nextObject()
        val encDataList = first as? PGPEncryptedDataList
            ?: objectFactory.nextObject() as? PGPEncryptedDataList
            ?: throw IllegalArgumentException("Not an OpenPGP encrypted message")

        var dataStream: InputStream? = null
        for (i in 0 until encDataList.size()) {
            val ed = encDataList.get(i)
            if (ed is PGPPublicKeyEncryptedData) {
                val secretKey = secretKeyRing.getSecretKey(ed.keyID)
                if (secretKey == null) {
                    val wanted = "0x" + ed.keyID.toString(16).uppercase().padStart(16, '0')
                    val available = secretKeyRing.secretKeys.asSequence()
                        .joinToString(", ") { "0x" + it.keyID.toString(16).uppercase().padStart(16, '0') }
                    throw IllegalArgumentException(
                        "No matching private key. Encrypted for $wanted, but the stored key ring only has: $available"
                    )
                }
                val privateKey = secretKey.extractPrivateKey(JcePBESecretKeyDecryptorBuilder().build(passphrase))
                val factory = JcePublicKeyDataDecryptorFactoryBuilder().build(privateKey)
                dataStream = ed.getDataStream(factory)
                break
            }
        }
        check(dataStream != null) { "No matching private key for this message" }

        val decryptedBytes = dataStream.readBytes()
        val messageObj = PGPObjectFactory(ByteArrayInputStream(decryptedBytes), JcaKeyFingerprintCalculator()).nextObject()
        val literalData = if (messageObj is PGPCompressedData) {
            PGPObjectFactory(messageObj.dataStream, JcaKeyFingerprintCalculator()).nextObject() as PGPLiteralData
        } else {
            messageObj as PGPLiteralData
        }
        return Pair(literalData.inputStream.readBytes(), literalData.fileName)
    }

    // ------------------------------------------------------------------
    // Key lookup (keys.openpgp.org)
    // ------------------------------------------------------------------

    /**
     * Fetch the public key for [email] from keys.openpgp.org. Returns the armored key or null
     * if the address has no published key (404) or the request fails.
     */
    fun fetchKeyByEmail(email: String, connectTimeoutMs: Int = 15000, readTimeoutMs: Int = 30000): String? {
        return try {
            val url = URL("https://keys.openpgp.org/vks/v1/by-email/" + URLEncoder.encode(email, "UTF-8"))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("User-Agent", "Mailchat/1.0")
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                if (body.contains("BEGIN PGP PUBLIC KEY BLOCK")) body else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The public key to use when encrypting for [email].
     *
     * When auto-lookup is enabled, the freshness of the key is checked before every send:
     * keys.openpgp.org is queried and, if the published key differs from the stored peer
     * (different fingerprint), the stored peer is updated and the fresh key is used.
     * If the lookup finds nothing (no published key) or fails (network error), the stored
     * peer key is used as a fallback so sending never breaks because of the network.
     */
    fun currentPeerKey(context: Context, email: String): String? {
        jceDiagnosticsOnce()?.let { com.mailchat.ai.AppLogger.log(context, "JCE diagnostics: $it") }
        val stored = getPeer(context, email)?.publicKeyArmored
        if (!isAutoLookupEnabled(context)) return stored
        val fresh = fetchKeyByEmail(email, 5000, 8000)
        if (fresh.isNullOrBlank()) {
            com.mailchat.ai.AppLogger.log(context, "Key check: no published key for $email -> using stored key")
            return stored
        }
        return try {
            val ring = parsePublicKeyRing(fresh)
            val fp = fingerprintHex(ring.publicKey.getFingerprint())
            val storedPeer = getPeer(context, email)
            if (storedPeer == null || storedPeer.fingerprint != fp) {
                addPeer(context, OpenPgpPeer(email, fresh, fp))
                com.mailchat.ai.AppLogger.log(context, "Key check: peer key for $email updated (fp=$fp)")
            }
            fresh
        } catch (e: Exception) {
            com.mailchat.ai.AppLogger.log(context, "Key check: failed to parse fresh key for $email: ${e.message} -> using stored key")
            stored
        }
    }

    // ------------------------------------------------------------------
    // Peer storage
    // ------------------------------------------------------------------

    fun getPeers(context: Context): List<OpenPgpPeer> {
        val json = prefs(context).getString(KEY_PEERS, "[]") ?: "[]"
        return parsePeers(json)
    }

    fun getPeer(context: Context, email: String): OpenPgpPeer? =
        getPeers(context).firstOrNull { it.email.equals(email, ignoreCase = true) }

    fun addPeer(context: Context, peer: OpenPgpPeer) {
        val peers = getPeers(context).toMutableList()
        peers.removeAll { it.email.equals(peer.email, ignoreCase = true) }
        peers.add(peer)
        prefs(context).edit().putString(KEY_PEERS, serializePeers(peers)).apply()
    }

    fun removePeer(context: Context, email: String) {
        val peers = getPeers(context).filterNot { it.email.equals(email, ignoreCase = true) }
        prefs(context).edit().putString(KEY_PEERS, serializePeers(peers)).apply()
    }

    private fun serializePeers(peers: List<OpenPgpPeer>): String {
        val sb = StringBuilder("[")
        peers.forEachIndexed { i, p ->
            if (i > 0) sb.append(",")
            val pub = p.publicKeyArmored.replace("\\", "\\\\").replace("\"", "\\\"")
            sb.append("""{"email":"${p.email}","pub":"$pub","fp":"${p.fingerprint}"}""")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun parsePeers(json: String): List<OpenPgpPeer> {
        if (json.isBlank() || json == "[]") return emptyList()
        val result = mutableListOf<OpenPgpPeer>()
        val regex = Regex("""\{"email":"([^"]+)","pub":"((?:[^"\\]|\\.)*)","fp":"([^"]*)"\}""")
        for (match in regex.findAll(json)) {
            result.add(
                OpenPgpPeer(
                    email = match.groupValues[1],
                    publicKeyArmored = match.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\"),
                    fingerprint = match.groupValues[3]
                )
            )
        }
        return result
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    fun isEncryptedContent(content: String): Boolean =
        content.contains("BEGIN PGP MESSAGE")

    fun parsePublicKeyRing(armored: String): PGPPublicKeyRing {
        val decoderStream = PGPUtil.getDecoderStream(ByteArrayInputStream(armored.toByteArray()))
        val collection = PGPPublicKeyRingCollection(decoderStream, JcaKeyFingerprintCalculator())
        val rings = collection.getKeyRings()
        check(rings.hasNext()) { "No public key ring found" }
        return rings.next()
    }

    private fun parseSecretKeyRing(armored: String): PGPSecretKeyRing {
        val decoderStream = PGPUtil.getDecoderStream(ByteArrayInputStream(armored.toByteArray()))
        val collection = PGPSecretKeyRingCollection(decoderStream, JcaKeyFingerprintCalculator())
        val rings = collection.getKeyRings()
        check(rings.hasNext()) { "No secret key ring found" }
        return rings.next()
    }

    fun findEncryptionKey(ring: PGPPublicKeyRing): PGPPublicKey =
        ring.publicKeys.asSequence().first { it.isEncryptionKey }

    // ------------------------------------------------------------------
    // QR / NFC payload (carries the full armored OpenPGP public key)
    // ------------------------------------------------------------------

    fun getPublicKeyForQr(context: Context, email: String): String {
        val pubKey = getPublicKeyArmored(context)
        val fp = getFingerprint(context)
        return "MAILCHAT_OG:$email:$fp:$pubKey"
    }

    /**
     * Parse a `MAILCHAT_OG:<email>:<fingerprint>:<armoredKey>` payload.
     * Returns (email, fingerprint, armoredPublicKey) or null if not in this format.
     */
    fun parseQrData(data: String): Triple<String, String, String>? {
        if (!data.startsWith("MAILCHAT_OG:")) return null
        val parts = data.split(":", limit = 4)
        if (parts.size >= 4 && parts[3].contains("BEGIN PGP PUBLIC KEY BLOCK")) {
            return Triple(parts[1], parts[2], parts[3].trim())
        }
        return null
    }

    // ------------------------------------------------------------------
    // Passphrase protection (Android Keystore)
    // ------------------------------------------------------------------

    private fun getOrCreateAesKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        kg.init(256)
        return kg.generateKey()
    }

    private fun encryptPassphrase(passphrase: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
            val combined = cipher.iv + cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
            "ks:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Keystore unavailable — fall back to obfuscated plaintext so the key is never lost.
            // The PGP secret key itself stays passphrase-protected regardless.
            "plain:" + Base64.encodeToString(passphrase.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptPassphrase(encrypted: String): String {
        if (encrypted.isBlank()) return ""
        return try {
            when {
                encrypted.startsWith("plain:") ->
                    String(Base64.decode(encrypted.removePrefix("plain:"), Base64.NO_WRAP), Charsets.UTF_8)
                else -> {
                    val raw = if (encrypted.startsWith("ks:")) encrypted.removePrefix("ks:") else encrypted
                    val combined = Base64.decode(raw, Base64.NO_WRAP)
                    val iv = combined.copyOfRange(0, 12)
                    val ct = combined.copyOfRange(12, combined.size)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(ct), Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun armorEncode(data: ByteArray): String {
        val out = ByteArrayOutputStream()
        val armorOut = ArmoredOutputStream.builder().build(out)
        armorOut.write(data)
        armorOut.close()
        return out.toString("UTF-8")
    }

    private fun fingerprintHex(fp: ByteArray): String = fp.joinToString("") { "%02X".format(it) }
}
