package com.mailchat

import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.junit.Assume
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Proof-of-Concept: keys.openpgp.org integration.
 *
 * Verifies the core feasibility:
 *  1. Fetch a public key by email address from keys.openpgp.org (VKS API)
 *  2. Parse it with BouncyCastle (bcpg)
 *  3. Compute the fingerprint (to identify / verify the key)
 *
 * (Encryption/decryption with the fetched key is the next step, same bcpg library.)
 */
class OpenPgpPoCTest {

    private fun fetchKeyByEmail(email: String): String? {
        return try {
            val url = URL("https://keys.openpgp.org/vks/v1/by-email/" + URLEncoder.encode(email, "UTF-8"))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Mailchat-PoC/1.0")
            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                println("  HTTP $code for $email")
                null
            }
        } catch (e: Exception) {
            println("  Fetch error: ${e.message}")
            null
        }
    }

    private fun parsePublicKeyRing(armored: String): PGPPublicKeyRing {
        val decoderStream = PGPUtil.getDecoderStream(ByteArrayInputStream(armored.toByteArray()))
        val collection = PGPPublicKeyRingCollection(decoderStream, JcaKeyFingerprintCalculator())
        val rings = collection.getKeyRings()
        check(rings.hasNext()) { "No key rings found in parsed data" }
        return rings.next()
    }

    private fun fingerprintHex(fp: ByteArray): String = fp.joinToString("") { "%02X".format(it) }

    @Test
    fun `fetch key by email from openpgp org, parse and verify fingerprint`() {
        val email = "joey@kitenet.net"
        println("=== PoC: fetch key by email ===")
        val fetched = fetchKeyByEmail(email)
        // keys.openpgp.org rate-limits (HTTP 429) or is unreachable: skip instead of failing.
        Assume.assumeTrue("keys.openpgp.org unreachable or rate-limited – skipping network test", fetched != null)
        val armored = fetched!!
        check(armored.contains("BEGIN PGP PUBLIC KEY BLOCK")) { "Response is not a PGP public key block" }
        println("  Fetched ${armored.length} chars from keys.openpgp.org")

        val keyRing = parsePublicKeyRing(armored)
        val pubKey = keyRing.publicKey
        val fp = fingerprintHex(pubKey.getFingerprint())
        val userIdIter = pubKey.rawUserIDs
        val firstUserId = if (userIdIter.hasNext()) String(userIdIter.next(), Charsets.UTF_8) else ""
        println("  User ID:     $firstUserId")
        println("  Algorithm:   ${pubKey.algorithm}")
        println("  Key ID:      ${pubKey.keyID.toUInt().toString(16).uppercase()}")
        println("  Fingerprint: $fp")

        // The API response comment lists this fingerprint; verify bcpg computes the same.
        check(fp == "E85A5F63B31D24C1EBF0D81CC910D9222512E3C7") { "Fingerprint mismatch: $fp" }
        println("  Fingerprint matches the value advertised by the API  OK")
        println("=== PoC PASSED: fetch + parse + fingerprint ===")
    }
}
