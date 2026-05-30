package com.zangetsu101.pass.keymanagement.gpg

import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import com.zangetsu101.pass.keymanagement.session.SessionError
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.api.OpenPGPKey
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.pgpainless.PGPainless

class GpgKeyStoreImpl(
    private val cryptoStore: PlainCryptoStore,
) : GpgKeyStore {
    override fun store(data: ByteArray) = cryptoStore.store(data)

    override fun get(): ByteArray = cryptoStore.get()

    override fun exists(): Boolean = cryptoStore.exists()

    override fun delete() = cryptoStore.delete()

    override fun importGpgKey(armoredKey: String) {
        val keys =
            try {
                PGPainless
                    .getInstance()
                    .readKey()
                    .parseKey(armoredKey)
                    .pgpSecretKeyRing
            } catch (e: KeyImportError) {
                throw e
            } catch (e: Exception) {
                throw KeyImportError.Malformed(e)
            }
        if (keys.any { it.s2KUsage == 0 }) {
            throw KeyImportError.NoPassphrase()
        }
        store(armoredKey.toByteArray())
    }

    override fun armorGpgKey(bytes: ByteArray): String {
        val ring =
            try {
                PGPainless
                    .getInstance()
                    .readKey()
                    .parseKey(bytes.inputStream())
                    .pgpSecretKeyRing
            } catch (e: KeyImportError) {
                throw e
            } catch (e: Exception) {
                throw KeyImportError.Malformed(e)
            }
        return PGPainless.asciiArmor(ring)
    }

    override fun validatePassphrase(passphrase: String) {
        check(exists()) { "No GPG key imported" }
        val keys =
            PGPainless
                .getInstance()
                .readKey()
                .parseKey(String(get(), Charsets.UTF_8))
                .pgpSecretKeyRing
        val decryptor =
            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
                .build(passphrase.toCharArray())
        val primaryKey = keys.firstOrNull { it.isMasterKey } ?: keys.first()
        try {
            if (primaryKey.s2KUsage != 0) {
                primaryKey.extractPrivateKey(decryptor)
            }
        } catch (e: PGPException) {
            throw SessionError.WrongPassphrase()
        }
    }

    override fun loadAndUnlock(passphrase: String): GpgPrivateKey {
        check(exists()) { "No GPG key imported" }
        val keys =
            PGPainless
                .getInstance()
                .readKey()
                .parseKey(String(get(), Charsets.UTF_8))
                .pgpSecretKeyRing
        val decryptor =
            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
                .build(passphrase.toCharArray())
        try {
            return OpenPGPKey(
                PGPSecretKeyRing(
                    keys.map { secretKey ->
                        if (secretKey.s2KUsage != 0) {
                            PGPSecretKey.copyWithNewPassword(secretKey, decryptor, null)
                        } else {
                            secretKey
                        }
                    },
                ),
            )
        } catch (e: PGPException) {
            throw SessionError.WrongPassphrase()
        }
    }

    override fun getGpgKeyInfo(): Pair<String, String>? {
        if (!exists()) return null
        return try {
            val ring =
                PGPainless
                    .getInstance()
                    .readKey()
                    .parseKey(String(get(), Charsets.UTF_8))
                    .pgpSecretKeyRing
            val master = ring.firstOrNull { it.isMasterKey }?.publicKey ?: return null
            val shortId = master.keyID and 0xFFFFFFFFL
            val keyId = "%08X".format(shortId).chunked(4).joinToString(" ")
            val uid = master.userIDs.asSequence().firstOrNull() ?: ""
            Pair(keyId, uid)
        } catch (_: Exception) {
            null
        }
    }
}
