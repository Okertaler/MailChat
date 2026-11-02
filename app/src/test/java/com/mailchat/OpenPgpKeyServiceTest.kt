package com.mailchat

import com.mailchat.crypto.OpenPgpKeyService
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Exercises the production OpenPgpKeyService crypto path (Context-free):
 *  - identity generation (RSA sign primary + RSA encrypt subkey)
 *  - encrypt to a public key
 *  - decrypt with the secret key
 *  - fetch a real key by email from keys.openpgp.org
 */
class OpenPgpKeyServiceTest {

    @Test
    fun `generate encrypt decrypt round trip via service`() {
        val passphrase = "service-passphrase-42"
        val identity = OpenPgpKeyService.generate("Mailchat <mail@mailchat.local>", passphrase)

        check(identity.fingerprint.length == 40) { "Bad fingerprint: ${identity.fingerprint}" }
        check(identity.publicKeyArmored.contains("BEGIN PGP PUBLIC KEY BLOCK")) { "Public key not armored" }
        check(identity.privateKeyArmored.contains("BEGIN PGP PRIVATE KEY BLOCK")) { "Secret key not armored" }

        val plaintext = "Produktionstest: OpenPGP via OpenPgpKeyService äöü"
        val encrypted = OpenPgpKeyService.encrypt(plaintext, identity.publicKeyArmored)
        check(encrypted.contains("BEGIN PGP MESSAGE")) { "Encryption did not produce a PGP MESSAGE" }
        check(!encrypted.contains(plaintext)) { "Plaintext leaked into ciphertext" }

        val decrypted = OpenPgpKeyService.decrypt(encrypted, identity.privateKeyArmored, passphrase.toCharArray())
        check(decrypted == plaintext) { "Round-trip mismatch: $decrypted" }
    }

    @Test
    fun `wrong passphrase fails to decrypt`() {
        val identity = OpenPgpKeyService.generate("Mailchat <mail@mailchat.local>", "right-pass")
        val encrypted = OpenPgpKeyService.encrypt("secret", identity.publicKeyArmored)
        var threw = false
        try {
            OpenPgpKeyService.decrypt(encrypted, identity.privateKeyArmored, "wrong-pass".toCharArray())
        } catch (e: Exception) {
            threw = true
        }
        check(threw) { "Decryption with wrong passphrase should fail" }
    }

    @Test
    fun `fetch key by email from openpgp org`() {
        val fetched = OpenPgpKeyService.fetchKeyByEmail("joey@kitenet.net")
        // keys.openpgp.org rate-limits (HTTP 429) or is unreachable: skip instead of failing.
        Assume.assumeTrue("keys.openpgp.org unreachable or rate-limited – skipping network test", fetched != null)
        val armored = fetched!!
        val ring = OpenPgpKeyService.parsePublicKeyRing(armored)
        val fp = ring.publicKey.getFingerprint().joinToString("") { "%02X".format(it) }
        check(fp == "E85A5F63B31D24C1EBF0D81CC910D9222512E3C7") { "Fingerprint mismatch: $fp" }
    }

    @Test
    fun `encrypt and decrypt file round trip preserves content and name`() {
        val identity = OpenPgpKeyService.generate("Mailchat <mail@mailchat.local>", "file-pass")
        val content = byteArrayOf(0, 1, 2, 3, 'A'.code.toByte(), 'B'.code.toByte(), 255.toByte(), 0)

        val inputFile = File.createTempFile("mailchat_test", ".bin")
        val encFile = File.createTempFile("mailchat_test", ".gpg")
        try {
            inputFile.writeBytes(content)
            OpenPgpKeyService.encryptFile(inputFile, encFile, identity.publicKeyArmored)
            check(encFile.length() > 0) { "Encrypted file is empty" }
            check(!encFile.readBytes().contentEquals(content)) { "Ciphertext equals plaintext" }

            val (decrypted, originalName) = OpenPgpKeyService.decryptData(
                encFile.readBytes(), identity.privateKeyArmored, "file-pass".toCharArray()
            )
            check(decrypted.contentEquals(content)) { "File round-trip content mismatch" }
            check(originalName == inputFile.name) { "Original name mismatch: $originalName" }
        } finally {
            inputFile.delete()
            encFile.delete()
        }
    }
}
