package com.mailchat

import com.mailchat.crypto.OpenPgpKeyService
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.bcpg.sig.SignatureCreationTime
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPKeyFlags
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Date

/**
 * Regression test for the "Algorithm X25519withSHA256CKDF not available" bug.
 *
 * Encrypting to a contact whose OpenPGP key uses an X25519 (Curve25519) ECDH subkey requires the
 * BouncyCastle JCE provider to be registered (Android's built-in JCE does not implement X25519).
 * This test generates a key ring with an X25519 encryption subkey and runs the real production
 * encrypt/decrypt path, which failed before the provider was registered.
 */
class OpenPgpX25519Test {

    private fun armor(ring: org.bouncycastle.openpgp.PGPPublicKeyRing): String {
        val out = ByteArrayOutputStream()
        val armored = org.bouncycastle.bcpg.ArmoredOutputStream(out)
        ring.encode(armored)
        armored.close()
        return out.toString("UTF-8")
    }

    private fun armorSecret(ring: org.bouncycastle.openpgp.PGPSecretKeyRing): String {
        val out = ByteArrayOutputStream()
        val armored = org.bouncycastle.bcpg.ArmoredOutputStream(out)
        ring.encode(armored)
        armored.close()
        return out.toString("UTF-8")
    }

    /**
     * Build a PGP key ring whose encryption subkey is X25519 (the case that broke). The primary
     * signing key is RSA so the ring is a standard, valid OpenPGP key.
     */
    private fun generateX25519KeyRing(
        userName: String,
        passphrase: CharArray
    ): Pair<org.bouncycastle.openpgp.PGPSecretKeyRing, org.bouncycastle.openpgp.PGPPublicKeyRing> {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val rsaKpg = KeyPairGenerator.getInstance("RSA")
        rsaKpg.initialize(3072)
        val signPgpKeyPair = JcaPGPKeyPair(
            PublicKeyAlgorithmTags.RSA_SIGN,
            rsaKpg.generateKeyPair(),
            Date()
        )

        val x25519Kpg = KeyPairGenerator.getInstance("X25519", "BC")
        x25519Kpg.initialize(255)
        val encPgpKeyPair = JcaPGPKeyPair(
            PublicKeyAlgorithmTags.X25519,
            x25519Kpg.generateKeyPair(),
            Date()
        )

        val digestCalculatorProvider = JcaPGPDigestCalculatorProviderBuilder().build()
        val sha1Calculator = digestCalculatorProvider.get(HashAlgorithmTags.SHA1)
        val contentSignerBuilder =
            JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_SIGN, HashAlgorithmTags.SHA256)
        val pbeEncryptor = JcePBESecretKeyEncryptorBuilder(
            SymmetricKeyAlgorithmTags.AES_256,
            sha1Calculator
        ).build(passphrase)

        val primarySubpackets = PGPSignatureSubpacketVector.fromSubpackets(
            arrayOf(
                KeyFlags(false, PGPKeyFlags.CAN_SIGN),
                SignatureCreationTime(true, Date())
            )
        )
        val subkeySubpackets = PGPSignatureSubpacketVector.fromSubpackets(
            arrayOf(
                KeyFlags(false, PGPKeyFlags.CAN_ENCRYPT_COMMS),
                SignatureCreationTime(true, Date())
            )
        )

        val keyRingGen = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            signPgpKeyPair,
            userName,
            sha1Calculator,
            primarySubpackets,
            null,
            contentSignerBuilder,
            pbeEncryptor
        )
        keyRingGen.addSubKey(encPgpKeyPair, subkeySubpackets, null)
        return Pair(keyRingGen.generateSecretKeyRing(), keyRingGen.generatePublicKeyRing())
    }

    @Test
    fun `encrypt to x25519 key and decrypt round trip`() {
        val passphrase = "x25519-test-passphrase".toCharArray()
        val (secretRing, publicRing) =
            generateX25519KeyRing("X25519 Test <x25519@mailchat.local>", passphrase)

        // Sanity: the encryption subkey really is X25519 (algorithm 25).
        val encKey = publicRing.publicKeys.asSequence().first { it.isEncryptionKey }
        check(encKey.algorithm == PublicKeyAlgorithmTags.X25519) {
            "Expected X25519 encryption key, got algorithm ${encKey.algorithm}"
        }

        val armoredPublic = armor(publicRing)
        val armoredSecret = armorSecret(secretRing)

        val plaintext = "X25519 Geheime Nachricht äöü"
        // This is the exact production path that threw
        // "unable to set up ephemeral keys: Algorithm X25519withSHA256CKDF not available"
        // before the BouncyCastle provider was registered.
        val encryptedArmored = OpenPgpKeyService.encrypt(plaintext, armoredPublic)
        check(encryptedArmored.contains("BEGIN PGP MESSAGE")) { "Encryption produced no PGP message" }

        val (decryptedBytes, _) =
            OpenPgpKeyService.decryptData(encryptedArmored.toByteArray(), armoredSecret, passphrase)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        check(decryptedText == plaintext) { "Round-trip mismatch: got '$decryptedText'" }
    }
}
