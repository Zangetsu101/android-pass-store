package com.example.pass.keymanagement

import android.content.Context
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
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

internal const val BLOB_SSH_KEY = "ssh_key"
internal const val BLOB_SSH_PUB_KEY = "ssh_pub_key"
private const val GPG_KEY_FILE = "gpg.asc"
private const val BIOMETRIC_CACHE_TIMEOUT_MS = 5 * 60 * 1000L

@Singleton
class CryptoService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionOperations: SessionOperations,
    ) : CryptoOperations {
        internal val blobStore = KeyBlobStore(context)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var cachedPassphrase: String? = null
        private var biometricCacheJob: Job? = null

        init {
            scope.launch {
                sessionOperations.sessionState.collect { state ->
                    if (state is SessionState.Inactive) {
                        cachedPassphrase = null
                        biometricCacheJob?.cancel()
                    }
                }
            }
        }

        private fun keysDir(): File = File(context.filesDir, "keys").also { it.mkdirs() }

        @Throws(KeyImportError::class)
        override fun importGpgKey(armoredKey: String) {
            val keys: PGPSecretKeyRing =
                try {
                    PGPainless.readKeyRing().secretKeyRing(armoredKey)
                        ?: throw KeyImportError.Malformed()
                } catch (e: KeyImportError) {
                    throw e
                } catch (e: Exception) {
                    throw KeyImportError.Malformed(e)
                }

            if (keys.any { it.s2KUsage == 0 }) {
                throw KeyImportError.NoPassphrase()
            }

            File(keysDir(), GPG_KEY_FILE).writeText(armoredKey)
        }

        @Throws(SessionError::class)
        override suspend fun startSession(passphrase: String) {
            val gpgFile = File(keysDir(), GPG_KEY_FILE)
            check(gpgFile.exists()) { "No GPG key imported" }

            val keys =
                withContext(Dispatchers.IO) {
                    PGPainless.readKeyRing().secretKeyRing(gpgFile.readText())
                        ?: error("Corrupted GPG key file")
                }

            val decryptor =
                BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
                    .build(passphrase.toCharArray())
            val primaryKey = keys.firstOrNull { it.isMasterKey } ?: keys.first()
            try {
                if (primaryKey.s2KUsage != 0) {
                    withContext(Dispatchers.IO) { primaryKey.extractPrivateKey(decryptor) }
                }
            } catch (e: PGPException) {
                throw SessionError.WrongPassphrase()
            }

            sessionOperations.createSession(passphrase)
        }

        @Throws(SessionError::class)
        override suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey {
            cachedPassphrase?.let { p ->
                sessionOperations.touchSession()
                return withContext(Dispatchers.IO) { unlockGpgKey(p) }
            }

            if (!sessionOperations.isSessionActive()) throw SessionError.NoActiveSession()

            val passphrase = sessionOperations.getPassphrase(activity)
            cachePassphrase(passphrase)
            sessionOperations.touchSession()
            return withContext(Dispatchers.IO) { unlockGpgKey(passphrase) }
        }

        private fun cachePassphrase(passphrase: String) {
            cachedPassphrase = passphrase
            biometricCacheJob?.cancel()
            biometricCacheJob =
                scope.launch {
                    delay(BIOMETRIC_CACHE_TIMEOUT_MS)
                    cachedPassphrase = null
                }
        }

        private fun unlockGpgKey(passphrase: String): GpgPrivateKey {
            val armoredKey = File(keysDir(), GPG_KEY_FILE).readText()
            val keys =
                PGPainless.readKeyRing().secretKeyRing(armoredKey)
                    ?: error("Corrupted GPG key file")
            val decryptor =
                BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
                    .build(passphrase.toCharArray())
            return PGPSecretKeyRing(
                keys.map { secretKey ->
                    if (secretKey.s2KUsage != 0) {
                        PGPSecretKey.copyWithNewPassword(secretKey, decryptor, null)
                    } else {
                        secretKey
                    }
                },
            )
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

        override fun getSshKey(): SshPrivateKey {
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
