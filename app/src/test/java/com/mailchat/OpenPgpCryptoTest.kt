package com.mailchat

import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPKeyFlags
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
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
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPairGenerator
import java.util.Date

/**
 * Verifies the full OpenPGP crypto cycle with BouncyCastle (bcpg) 1.78:
 *  - key pair generation
 *  - encryption with a public key
 *  - decryption with the private key
 *  - round-trip integrity
 */
class OpenPgpCryptoTest {

    private fun generateKeyPair(userName: String, passphrase: CharArray): Pair<PGPSecretKeyRing, PGPPublicKeyRing> {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(3072)
        val javaKeyPair = keyPairGen.generateKeyPair()
        val pgpKeyPair = JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_SIGN, javaKeyPair, Date())

        val encKeyPairGen = KeyPairGenerator.getInstance("RSA")
        encKeyPairGen.initialize(3072)
        val encJavaKeyPair = encKeyPairGen.generateKeyPair()
        val encPgpKeyPair = JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_ENCRYPT, encJavaKeyPair, Date())

        val digestCalculatorProvider = JcaPGPDigestCalculatorProviderBuilder().build()
        val sha1Calculator: PGPDigestCalculator = digestCalculatorProvider.get(HashAlgorithmTags.SHA1)
        val contentSignerBuilder = JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_SIGN, HashAlgorithmTags.SHA256)
        val pbeEncryptor = JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calculator).build(passphrase)
        val keyRingGen = PGPKeyRingGenerator(
            PGPKeyFlags.CAN_SIGN,
            pgpKeyPair,
            userName,
            sha1Calculator,
            null,
            null,
            contentSignerBuilder,
            pbeEncryptor
        )
        keyRingGen.addSubKey(encPgpKeyPair, contentSignerBuilder)
        val secretKeyRing = keyRingGen.generateSecretKeyRing()
        val publicKeyRing = keyRingGen.generatePublicKeyRing()
        return Pair(secretKeyRing, publicKeyRing)
    }

    private fun findEncryptionKey(ring: PGPPublicKeyRing): PGPPublicKey =
        ring.publicKeys.asSequence().first { it.isEncryptionKey }

    private fun encrypt(plaintext: ByteArray, pubKey: PGPPublicKey): ByteArray {
        val innerOut = ByteArrayOutputStream()
        val compOut: OutputStream = PGPCompressedDataGenerator(CompressionAlgorithmTags.ZLIB).open(innerOut)
        val litOut: OutputStream = PGPLiteralDataGenerator()
            .open(compOut, 'b', "mailchat.txt", plaintext.size.toLong(), Date())
        litOut.write(plaintext)
        litOut.close()
        compOut.close()
        val innerData = innerOut.toByteArray()

        val messageOut = ByteArrayOutputStream()
        val dataEncryptorBuilder = JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
        val encGen = PGPEncryptedDataGenerator(dataEncryptorBuilder)
        encGen.addMethod(JcePublicKeyKeyEncryptionMethodGenerator(pubKey))
        val encOut: OutputStream = encGen.open(messageOut, innerData.size.toLong())
        encOut.write(innerData)
        encOut.close()
        return messageOut.toByteArray()
    }

    private fun decrypt(encrypted: ByteArray, secretKeyRing: PGPSecretKeyRing, passphrase: CharArray): ByteArray {
        val decoderStream = PGPUtil.getDecoderStream(ByteArrayInputStream(encrypted))
        val objectFactory = PGPObjectFactory(decoderStream, JcaKeyFingerprintCalculator())
        val obj = objectFactory.nextObject()
        val encDataList = obj as? PGPEncryptedDataList
            ?: (objectFactory.nextObject() as PGPEncryptedDataList)
        var dataStream: InputStream? = null
        for (i in 0 until encDataList.size()) {
            val ed = encDataList.get(i)
            if (ed is PGPPublicKeyEncryptedData) {
                val secretKey = secretKeyRing.getSecretKey(ed.keyID)
                val privateKey = secretKey.extractPrivateKey(JcePBESecretKeyDecryptorBuilder().build(passphrase))
                val factory = JcePublicKeyDataDecryptorFactoryBuilder().build(privateKey)
                dataStream = ed.getDataStream(factory)
                break
            }
        }
        check(dataStream != null) { "Could not decrypt: no matching key" }
        val decryptedBytes = dataStream.readBytes()
        val messageObj = PGPObjectFactory(ByteArrayInputStream(decryptedBytes), JcaKeyFingerprintCalculator()).nextObject()
        val literalData = if (messageObj is PGPCompressedData) {
            PGPObjectFactory(messageObj.dataStream, JcaKeyFingerprintCalculator()).nextObject() as PGPLiteralData
        } else {
            messageObj as PGPLiteralData
        }
        return literalData.inputStream.readBytes()
    }

    private fun fingerprintHex(fp: ByteArray): String = fp.joinToString("") { "%02X".format(it) }

    @Test
    fun `openpgp round trip generate encrypt decrypt`() {
        val passphrase = "test-passphrase-123".toCharArray()
        println("=== OpenPGP round-trip ===")

        val (secretKeyRing, publicKeyRing) = generateKeyPair("Mailchat Test <test@mailchat.local>", passphrase)
        val pubKey = findEncryptionKey(publicKeyRing)
        val fp = fingerprintHex(publicKeyRing.publicKey.getFingerprint())
        println("  Generated key, fingerprint: $fp")
        check(fp.length == 40) { "Fingerprint should be 40 hex chars, got $fp" }

        val plaintext = "Geheime Mailchat Nachricht mit Umlauten äöü".toByteArray()
        val encrypted = encrypt(plaintext, pubKey)
        println("  Encrypted ${encrypted.size} bytes")
        check(encrypted.isNotEmpty()) { "Encryption produced no output" }

        val decrypted = decrypt(encrypted, secretKeyRing, passphrase)
        val decryptedText = String(decrypted)
        println("  Decrypted: $decryptedText")
        check(decryptedText == "Geheime Mailchat Nachricht mit Umlauten äöü") { "Round-trip mismatch" }
        println("=== Round-trip PASSED ===")
    }
}
