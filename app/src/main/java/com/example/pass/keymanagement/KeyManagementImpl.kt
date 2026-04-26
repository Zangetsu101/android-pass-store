package com.example.pass.keymanagement

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.pgpainless.PGPainless
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal const val BLOB_SSH_KEY = "ssh_key"
internal const val BLOB_SSH_PUB_KEY = "ssh_pub_key"
private const val GPG_KEY_FILE = "gpg.asc"
private const val SESSION_PASSPHRASE_BLOB = "session.enc"
private const val SESSION_KEY_ALIAS = "passdroid_session_key"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12

@Singleton
class KeyManagementImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
) : KeyManagement {

    internal val blobStore = KeyBlobStore(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timeoutJob: Job? = null

    private fun keysDir(): File = File(context.filesDir, "keys").also { it.mkdirs() }

    @Throws(KeyImportError::class)
    override fun importGpgKey(armoredKey: String) {
        val keys: PGPSecretKeyRing = try {
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

    fun isGpgKeyImported(): Boolean = File(keysDir(), GPG_KEY_FILE).exists()

    @Throws(SessionError::class)
    override fun startSession(passphrase: String) {
        val gpgFile = File(keysDir(), GPG_KEY_FILE)
        check(gpgFile.exists()) { "No GPG key imported" }

        val keys = PGPainless.readKeyRing().secretKeyRing(gpgFile.readText())
            ?: error("Corrupted GPG key file")

        val decryptor = BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
            .build(passphrase.toCharArray())
        val primaryKey = keys.firstOrNull { it.isMasterKey } ?: keys.first()
        try {
            if (primaryKey.s2KUsage != 0) {
                primaryKey.extractPrivateKey(decryptor)
            }
        } catch (e: PGPException) {
            throw SessionError.WrongPassphrase()
        }

        val sessionKey = createSessionKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        File(keysDir(), SESSION_PASSPHRASE_BLOB).writeBytes(iv + ciphertext)
    }

    override fun endSession() {
        timeoutJob?.cancel()
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(SESSION_KEY_ALIAS)) {
            keyStore.deleteEntry(SESSION_KEY_ALIAS)
        }
        File(keysDir(), SESSION_PASSPHRASE_BLOB).delete()
    }

    override fun isSessionActive(): Boolean {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.containsAlias(SESSION_KEY_ALIAS)
    }

    override fun generateSshKey(): String {
        val keyGen = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider())
        val pair = keyGen.generateKeyPair()

        blobStore.encrypt(BLOB_SSH_KEY, pair.private.encoded)
        blobStore.encrypt(BLOB_SSH_PUB_KEY, pair.public.encoded)

        return openSshEd25519PublicKey(pair.public)
    }

    override fun getSshKey(): SshPrivateKey {
        val privateBytes = blobStore.decrypt(BLOB_SSH_KEY)
        val publicBytes = blobStore.decrypt(BLOB_SSH_PUB_KEY)
        val kf = KeyFactory.getInstance("Ed25519", BouncyCastleProvider())
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = kf.generatePublic(X509EncodedKeySpec(publicBytes))
        return KeyPair(publicKey, privateKey)
    }

    @Throws(SessionError::class)
    override suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey {
        if (!isSessionActive()) throw SessionError.NoActiveSession()

        showBiometricPrompt(activity)

        val passphrase = decryptSessionPassphrase()
        val armoredKey = File(keysDir(), GPG_KEY_FILE).readText()
        val keys = PGPainless.readKeyRing().secretKeyRing(armoredKey)
            ?: error("Corrupted GPG key file")

        val decryptor = BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider())
            .build(passphrase.toCharArray())
        val unlockedKeys = PGPSecretKeyRing(keys.map { secretKey ->
            if (secretKey.s2KUsage != 0) {
                PGPSecretKey.copyWithNewPassword(secretKey, decryptor, null)
            } else {
                secretKey
            }
        })

        scheduleInactivityTimeout()
        return unlockedKeys
    }

    override fun clearAllKeys() {
        endSession()
        blobStore.deleteAll()
    }

    internal fun scheduleInactivityTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            val timeoutMs = sessionManager.getTimeoutMs()
            if (timeoutMs <= 0L) return@launch  // 0 = manual lock only
            delay(timeoutMs)
            endSession()
        }
    }

    private fun createSessionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(SESSION_KEY_ALIAS)) {
            keyStore.deleteEntry(SESSION_KEY_ALIAS)
        }

        val spec = KeyGenParameterSpec.Builder(
            SESSION_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private fun decryptSessionPassphrase(): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val sessionKey = keyStore.getKey(SESSION_KEY_ALIAS, null) as SecretKey
        val blob = File(keysDir(), SESSION_PASSPHRASE_BLOB).readBytes()
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun openSshEd25519PublicKey(publicKey: java.security.PublicKey): String {
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
