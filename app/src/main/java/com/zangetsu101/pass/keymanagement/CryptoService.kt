package com.zangetsu101.pass.keymanagement

import android.content.Context
import android.util.Base64
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyOperations
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.ssh.SshKeyOperations
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.api.OpenPGPKey
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.pgpainless.PGPainless
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

internal const val GPG_KEY_FILENAME = "gpg.asc"
internal const val BLOB_SSH_KEY = "ssh_key"
internal const val BLOB_SSH_PUB_KEY = "ssh_pub_key"

@Singleton
class CryptoService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionOperations: SessionOperations,
    ) : GpgKeyOperations,
        SshKeyOperations,
        KeyManagement {
        internal val blobStore = KeyBlobStore(context)

        private fun keysDir(): File = File(context.filesDir, "keys").also { it.mkdirs() }

        @Throws(KeyImportError::class)
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

            File(keysDir(), GPG_KEY_FILENAME).writeText(armoredKey)
        }

        @Throws(KeyImportError::class)
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

        @Throws(SessionError::class)
        override fun validatePassphrase(passphrase: String) {
            val gpgFile = File(keysDir(), GPG_KEY_FILENAME)
            check(gpgFile.exists()) { "No GPG key imported" }
            val keys =
                PGPainless
                    .getInstance()
                    .readKey()
                    .parseKey(gpgFile.readText())
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
            val gpgFile = File(keysDir(), GPG_KEY_FILENAME)
            check(gpgFile.exists()) { "No GPG key imported" }
            val keys =
                PGPainless
                    .getInstance()
                    .readKey()
                    .parseKey(gpgFile.readText())
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
            val file = File(keysDir(), GPG_KEY_FILENAME)
            if (!file.exists()) return null
            return try {
                val ring =
                    PGPainless
                        .getInstance()
                        .readKey()
                        .parseKey(file.readText())
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

        override fun clearAllKeys() {
            sessionOperations.endSession()
            blobStore.deleteAll()
        }

        override fun generateSshKey(): String {
            val pair = generateTestSshKey()
            blobStore.encrypt(BLOB_SSH_KEY, pair.private.encoded)
            blobStore.encrypt(BLOB_SSH_PUB_KEY, pair.public.encoded)
            return openSshPublicKey(pair.public)
        }

        override fun getSshKey(): SshKeyPair {
            val privateBytes = blobStore.decrypt(BLOB_SSH_KEY)
            val publicBytes = blobStore.decrypt(BLOB_SSH_PUB_KEY)
            val kf = KeyFactory.getInstance("EC", BouncyCastleProvider())
            val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
            val publicKey = kf.generatePublic(X509EncodedKeySpec(publicBytes))
            return KeyPair(publicKey, privateKey)
        }

        private fun generateTestSshKey(): KeyPair {
            val ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1")
            val d = BigInteger(1, ByteArray(32) { (it + 1).toByte() })
            val q = ecSpec.g.multiply(d).normalize()
            val provider = BouncyCastleProvider()
            val keyFactory = KeyFactory.getInstance("EC", provider)
            val privateKey = keyFactory.generatePrivate(ECPrivateKeySpec(d, ecSpec))
            val publicKey = keyFactory.generatePublic(ECPublicKeySpec(q, ecSpec))
            return KeyPair(publicKey, privateKey)
        }

        private fun openSshPublicKey(publicKey: java.security.PublicKey): String {
            val rawKeyBytes = SubjectPublicKeyInfo.getInstance(publicKey.encoded).publicKeyData.bytes
            val buf = ByteArrayOutputStream()
            val out = DataOutputStream(buf)
            out.writeSshString("ssh-ed25519")
            out.writeSshBytes(rawKeyBytes)
            out.flush()
            return "ssh-ed25519 ${Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)}"
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
