package com.mailchat.crypto

import java.security.Provider

/**
 * A minimal JCE provider that exposes BouncyCastle's X25519/X448 key agreement and
 * key pair generation SPIs under a name of our own.
 *
 * Why: some ROMs (e.g. HarmonyOS) pre-register a stripped-down BouncyCastle provider
 * under the name "BC" (inherited from zygote) that lacks the X25519WITHSHA256CKDF
 * KeyAgreement service. [Security.addProvider] silently ignores duplicate names, so
 * the app's full BouncyCastle can never replace it.
 *
 * Instead of fighting over the global "BC" name, BC's OpenPGP operators are pinned to
 * this provider via [JcePublicKeyKeyEncryptionMethodGenerator.setProvider] when
 * encrypting/decrypting with X25519/X448 keys. The SPI classes referenced here are
 * BouncyCastle's own (already bundled in the app), so this works regardless of which
 * "BC" provider the ROM has registered.
 */
class MailChatX25519Provider : Provider(
    "MailChatX25519",
    1.0,
    "MailChat X25519/X448 KeyAgreement + KeyPairGenerator (BouncyCastle SPIs)"
) {
    init {
        put("KeyPairGenerator.X25519",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi\$X25519")
        put("KeyPairGenerator.X448",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi\$X448")
        put("KeyAgreement.X25519WITHSHA256CKDF",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyAgreementSpi\$X25519withSHA256CKDF")
        put("KeyAgreement.X25519WITHSHA256HKDF",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyAgreementSpi\$X25519withSHA256HKDF")
        put("KeyAgreement.X448WITHSHA512CKDF",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyAgreementSpi\$X448withSHA512CKDF")
        put("KeyAgreement.X448WITHSHA512HKDF",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyAgreementSpi\$X448withSHA512HKDF")
        // KeyFactory is needed by JcaPGPKeyConverter when the generator is pinned to this provider.
        // XDH is the generic algorithm for both X25519 and X448; the specific variants are also
        // registered for direct algorithm name lookups.
        put("KeyFactory.XDH",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi\$XDH")
        put("KeyFactory.X25519",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi\$X25519")
        put("KeyFactory.X448",
            "org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi\$X448")
    }
}
