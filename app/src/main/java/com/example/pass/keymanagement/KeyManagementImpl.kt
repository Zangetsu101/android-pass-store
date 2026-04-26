package com.example.pass.keymanagement

import android.content.Context
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
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

    override fun generateSshKey(): String {
        val pair = generateTestSshKey()

        blobStore.encrypt(BLOB_SSH_KEY, pair.private.encoded)
        blobStore.encrypt(BLOB_SSH_PUB_KEY, pair.public.encoded)

        return openSshEcdsaPublicKey(pair.public as ECPublicKey)
    }

    override suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey {
        ensureAuthenticated(activity)
        val file = File(keysDir(), GPG_KEY_FILE)
        return PGPainless.readKeyRing().secretKeyRing(file.readText())
            ?: error("Corrupted GPG key blob")
    }

    override fun getSshKey(): SshPrivateKey {
        val privateBytes = blobStore.decrypt(BLOB_SSH_KEY)
        val publicBytes = blobStore.decrypt(BLOB_SSH_PUB_KEY)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))
        return KeyPair(publicKey, privateKey)
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

    private fun openSshEcdsaPublicKey(publicKey: ECPublicKey): String {
        val keyType = "ecdsa-sha2-nistp256"
        val curveName = "nistp256"
        val x = publicKey.w.affineX.toUnsignedBytes(32)
        val y = publicKey.w.affineY.toUnsignedBytes(32)
        val point = byteArrayOf(0x04.toByte()) + x + y

        val buf = ByteArrayOutputStream()
        val out = DataOutputStream(buf)
        out.writeSshString(keyType)
        out.writeSshString(curveName)
        out.writeSshBytes(point)
        out.flush()

        return "$keyType ${Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)}"
    }

    private fun BigInteger.toUnsignedBytes(size: Int): ByteArray {
        val raw = toByteArray()
        return when {
            raw.size > size -> raw.copyOfRange(raw.size - size, raw.size)
            raw.size < size -> ByteArray(size - raw.size) + raw
            else -> raw
        }
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
