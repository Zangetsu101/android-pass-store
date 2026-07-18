// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.gpg

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import com.zangetsu101.pass.keymanagement.session.SessionError
import org.bouncycastle.bcpg.EdSecretBCPGKey
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.api.OpenPGPKey
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider

class GpgKeyStoreImpl(
    private val cryptoStore: PlainCryptoStore,
    private val importReader: GpgImportReader = GpgImportReaderImpl(),
    private val inspector: GpgKeyInspector = GpgKeyInspector(),
) : GpgKeyStore {
    override fun store(data: ByteArray) = cryptoStore.store(data)

    override fun get(): ByteArray = cryptoStore.get()

    override fun exists(): Boolean = cryptoStore.exists()

    override fun delete() = cryptoStore.delete()

    override fun storeImportedGpgKey(armoredKey: String) = store(armoredKey.toByteArray())

    override fun validatePassphrase(passphrase: String) {
        check(exists()) { "No GPG key imported" }
        val keys = storedKeyRing()
        val decryptor =
            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
                .build(passphrase.toCharArray())
        val unlockKey = passphraseUnlockKey(keys) ?: return
        try {
            unlockKey.extractPrivateKey(decryptor)
        } catch (e: PGPException) {
            throw SessionError.WrongPassphrase()
        }
    }

    override fun loadAndUnlock(passphrase: String): GpgPrivateKey {
        check(exists()) { "No GPG key imported" }
        val keys = storedKeyRing()
        val decryptor =
            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
                .build(passphrase.toCharArray())
        val encryptionKeyIds = inspector.validEncryptionKeyIds(keys)
        try {
            return OpenPGPKey(
                PGPSecretKeyRing(
                    keys.map { secretKey ->
                        // Only the encryption key is used for decryption — unlock just that
                        // one. Master/auth keys stay as-is (the master is a stub anyway under
                        // --export-secret-subkeys), so we never try to extract a stub's private key.
                        if (secretKey.publicKey.keyID in encryptionKeyIds &&
                            !secretKey.isPrivateKeyEmpty &&
                            secretKey.s2KUsage != 0
                        ) {
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
            val ring = storedKeyRing()
            val master = ring.firstOrNull { it.isMasterKey }?.publicKey ?: return null
            val shortId = master.keyID and 0xFFFFFFFFL
            val keyId = "%08X".format(shortId).chunked(4).joinToString(" ")
            val uid = master.userIDs.asSequence().firstOrNull() ?: ""
            Pair(keyId, uid)
        } catch (_: Exception) {
            null
        }
    }

    override fun findAuthSubkey(): AuthSubkeyInfo? {
        if (!exists()) return null
        return try {
            inspector.findAuthSubkey(storedKeyRing())
        } catch (_: Exception) {
            null
        }
    }

    override fun extractAuthSubkeySeed(
        passphrase: String,
        keyId: Long,
    ): ByteArray {
        check(exists()) { "No GPG key imported" }
        val keys = storedKeyRing()
        val decryptor =
            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
                .build(passphrase.toCharArray())
        val secretKey =
            keys.firstOrNull { it.publicKey.keyID == keyId }
                ?: throw IllegalArgumentException("Auth subkey $keyId not found in ring")
        return try {
            val pgpPrivKey = secretKey.extractPrivateKey(decryptor)
            when (val packet = pgpPrivKey.privateKeyDataPacket) {
                is EdSecretBCPGKey -> {
                    val bytes = packet.x.toByteArray()
                    if (bytes.size == 33 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, 33) else bytes.copyOf(32)
                }

                else -> {
                    throw IllegalStateException("Unsupported private key packet type: ${packet.javaClass.simpleName}")
                }
            }
        } catch (e: PGPException) {
            throw SessionError.WrongPassphrase()
        }
    }

    private fun storedKeyRing(): PGPSecretKeyRing = importReader.parseCandidate(String(get(), Charsets.UTF_8)).secretKeyRing

    // The passphrase protects every private-bearing key uniformly, so probe whichever key we
    // can actually unlock. Prefer the encryption key (the one decryption needs); fall back to
    // any private-bearing key. A gnu-dummy stub (the master under --export-secret-subkeys) has
    // no private material and must never be the probe target.
    private fun passphraseUnlockKey(keys: PGPSecretKeyRing): PGPSecretKey? {
        val encryptionKeyIds = inspector.validEncryptionKeyIds(keys)
        val unlockable = keys.asSequence().filter { !it.isPrivateKeyEmpty && it.s2KUsage != 0 }
        return unlockable.firstOrNull { it.publicKey.keyID in encryptionKeyIds }
            ?: unlockable.firstOrNull()
    }
}
