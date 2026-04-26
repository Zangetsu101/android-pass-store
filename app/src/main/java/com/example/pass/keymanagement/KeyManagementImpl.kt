package com.example.pass.keymanagement

import android.content.Context
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

internal const val BLOB_SSH_KEY = "ssh_key"
internal const val BLOB_SSH_PUB_KEY = "ssh_pub_key"
private const val GPG_KEY_FILE = "gpg.asc"

@Singleton
class KeyManagementImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
) : KeyManagement {

    internal val blobStore = KeyBlobStore(context)

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

    override suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey {
        ensureAuthenticated(activity)
        val file = File(keysDir(), GPG_KEY_FILE)
        return PGPainless.readKeyRing().secretKeyRing(file.readText())
            ?: error("Corrupted GPG key blob")
    }

    override fun startSession(passphrase: String) {
        throw NotImplementedError("startSession not implemented until task 4")
    }

    override fun endSession() {
        sessionManager.invalidate()
    }

    override fun isSessionActive(): Boolean {
        throw NotImplementedError("isSessionActive not implemented until task 4")
    }

    override fun clearAllKeys() {
        sessionManager.invalidate()
        blobStore.deleteAll()
        File(keysDir(), GPG_KEY_FILE).delete()
    }

    private suspend fun ensureAuthenticated(activity: FragmentActivity) {
        if (!sessionManager.isSessionActive()) {
            showBiometricPrompt(activity)
            sessionManager.recordAuth()
        }
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
