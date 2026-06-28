package com.zangetsu101.pass.keymanagement.gpg

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import com.zangetsu101.pass.keymanagement.session.SessionError
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.bcpg.EdSecretBCPGKey
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.api.OpenPGPKey
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.EncryptionPurpose
import org.pgpainless.algorithm.KeyFlag
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64

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
        validateUsableForDecryption(keys)
        val unprotected =
            keys
                .asSequence()
                .filter { !it.isPrivateKeyEmpty && it.s2KUsage == 0 }
                .map { it.publicKey.keyID.shortId() }
                .toList()
        if (unprotected.isNotEmpty()) {
            throw KeyImportError.NoPassphrase(unprotected)
        }
        store(armoredKey.toByteArray())
    }

    /**
     * The store can only be decrypted by a subkey that is encrypt-capable, valid (not
     * expired/revoked), AND carries private key material. A stub (gnu-dummy master,
     * smartcard-diverted subkey) advertises encrypt capability via its public flags but
     * cannot decrypt — so capability alone is not enough. Checked before the passphrase
     * requirement so structural problems are reported first.
     */
    private fun validateUsableForDecryption(keys: PGPSecretKeyRing) {
        val info = PGPainless.getInstance().inspect(OpenPGPKey(keys))
        val valid = info.getEncryptionSubkeys(EncryptionPurpose.ANY)
        val hasPrivate =
            valid.any { componentKey ->
                keys.getSecretKey(componentKey.keyIdentifier)?.isPrivateKeyEmpty == false
            }
        if (hasPrivate) return
        if (valid.isNotEmpty()) {
            throw KeyImportError.PublicKeyOnly(valid.map { it.pgpPublicKey.keyID.shortId() })
        }
        // getKeysWithKeyFlag is not validity-filtered, so anything here is expired/revoked.
        val anyEncryptFlagged =
            (info.getKeysWithKeyFlag(KeyFlag.ENCRYPT_COMMS) + info.getKeysWithKeyFlag(KeyFlag.ENCRYPT_STORAGE))
                .map { it.pgpPublicKey.keyID.shortId() }
                .distinct()
        if (anyEncryptFlagged.isNotEmpty()) {
            throw KeyImportError.ExpiredEncryptionKey(anyEncryptFlagged)
        }
        throw KeyImportError.NoEncryptionKey()
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
        val unlockKey = passphraseUnlockKey(keys) ?: return
        try {
            unlockKey.extractPrivateKey(decryptor)
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
        val encryptionKeyIds = validEncryptionKeyIds(keys)
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

    override fun findAuthSubkey(): AuthSubkeyInfo? {
        if (!exists()) return null
        return try {
            val ring =
                PGPainless
                    .getInstance()
                    .readKey()
                    .parseKey(String(get(), Charsets.UTF_8))
                    .pgpSecretKeyRing
            val uid =
                ring
                    .firstOrNull { it.isMasterKey }
                    ?.publicKey
                    ?.userIDs
                    ?.asSequence()
                    ?.firstOrNull() ?: ""
            val authSubkey =
                ring
                    .filter { !it.isMasterKey }
                    .filter { secretKey ->
                        val algo = secretKey.publicKey.algorithm
                        (algo == PublicKeyAlgorithmTags.EDDSA_LEGACY || algo == PublicKeyAlgorithmTags.Ed25519) &&
                            hasAuthFlag(secretKey.publicKey)
                    }.maxByOrNull { it.publicKey.creationTime }
                    ?: return null
            val pubKey = authSubkey.publicKey
            val sshPubKey = computeOpenSshPublicKey(pubKey)
            AuthSubkeyInfo(
                keyId = pubKey.keyID,
                sshPublicKey = sshPubKey,
                sshFingerprint = computeSshFingerprint(sshPubKey),
                uid = uid,
                created = pubKey.creationTime.time / 1000L,
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun extractAuthSubkeySeed(
        passphrase: String,
        keyId: Long,
    ): ByteArray {
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

    /** Key IDs of valid (non-expired/revoked) encrypt-capable subkeys, regardless of private material. */
    private fun validEncryptionKeyIds(keys: PGPSecretKeyRing): Set<Long> =
        PGPainless
            .getInstance()
            .inspect(OpenPGPKey(keys))
            .getEncryptionSubkeys(EncryptionPurpose.ANY)
            .map { it.pgpPublicKey.keyID }
            .toSet()

    // The passphrase protects every private-bearing key uniformly, so probe whichever key we
    // can actually unlock. Prefer the encryption key (the one decryption needs); fall back to
    // any private-bearing key. A gnu-dummy stub (the master under --export-secret-subkeys) has
    // no private material and must never be the probe target.
    private fun passphraseUnlockKey(keys: PGPSecretKeyRing): PGPSecretKey? {
        val encryptionKeyIds = validEncryptionKeyIds(keys)
        val unlockable = keys.asSequence().filter { !it.isPrivateKeyEmpty && it.s2KUsage != 0 }
        return unlockable.firstOrNull { it.publicKey.keyID in encryptionKeyIds }
            ?: unlockable.firstOrNull()
    }

    private fun Long.shortId(): String = "%08X".format(this and 0xFFFFFFFFL)

    private fun hasAuthFlag(pubKey: PGPPublicKey): Boolean {
        @Suppress("UNCHECKED_CAST")
        val bindingSigs = pubKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING) as Iterator<PGPSignature>
        for (sig in bindingSigs) {
            if (sig.hashedSubPackets.keyFlags and KeyFlags.AUTHENTICATION != 0) {
                return true
            }
        }
        return false
    }

    private fun computeOpenSshPublicKey(pgpPublicKey: PGPPublicKey): String {
        val converter = JcaPGPKeyConverter().setProvider(BouncyCastleProvider())
        val jcaKey = converter.getPublicKey(pgpPublicKey)
        val rawKeyBytes = SubjectPublicKeyInfo.getInstance(jcaKey.encoded).publicKeyData.bytes
        val buf = ByteArrayOutputStream()
        val out = DataOutputStream(buf)
        out.writeSshString("ssh-ed25519")
        out.writeSshBytes(rawKeyBytes)
        out.flush()
        return "ssh-ed25519 ${Base64.getEncoder().withoutPadding().encodeToString(buf.toByteArray())}"
    }

    private fun computeSshFingerprint(sshPublicKey: String): String {
        val wireBytes = Base64.getDecoder().decode(sshPublicKey.substringAfter(" "))
        val digest = MessageDigest.getInstance("SHA-256").digest(wireBytes)
        return "SHA256:${Base64.getEncoder().withoutPadding().encodeToString(digest)}"
    }

    private fun DataOutputStream.writeSshString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeSshBytes(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }
}
