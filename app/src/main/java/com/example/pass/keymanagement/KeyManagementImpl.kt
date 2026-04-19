package com.example.pass.keymanagement

import android.content.Context
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.pgpainless.PGPainless
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

internal const val BLOB_GPG_KEY = "gpg_key"
internal const val BLOB_SSH_KEY = "ssh_key"
internal const val BLOB_SSH_PUB_KEY = "ssh_pub_key"

@Singleton
class KeyManagementImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
) : KeyManagement {

    internal val blobStore = KeyBlobStore(context)

    @Throws(KeyImportError::class)
    override fun importGpgKey(armoredKey: String, passphrase: String?) {
        val keys: PGPSecretKeyRing = try {
            PGPainless.readKeyRing().secretKeyRing(armoredKey)
                ?: throw KeyImportError("No secret key found in armored input")
        } catch (e: KeyImportError) {
            throw e
        } catch (e: Exception) {
            throw KeyImportError("Malformed armored key: ${e.message}", e)
        }

        val strippedKeys: PGPSecretKeyRing = try {
            stripPassphrase(keys, passphrase)
        } catch (e: PGPException) {
            throw KeyImportError("Wrong passphrase or unsupported key format: ${e.message}", e)
        }

        blobStore.encrypt(BLOB_GPG_KEY, strippedKeys.encoded)
    }

    private fun generateTestSshKey(): KeyPair {
        val ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val d = BigInteger(1, ByteArray(32) { (it + 1).toByte() })  // scalar 0x01..0x20
        val q = ecSpec.g.multiply(d).normalize()
        val provider = BouncyCastleProvider()
        val keyFactory = KeyFactory.getInstance("EC", provider)
        val privateKey = keyFactory.generatePrivate(ECPrivateKeySpec(d, ecSpec))
        val publicKey = keyFactory.generatePublic(ECPublicKeySpec(q, ecSpec))
        return KeyPair(publicKey, privateKey)
    }

    override fun generateSshKey(): String {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val pair = keyGen.generateKeyPair()

        blobStore.encrypt(BLOB_SSH_KEY, pair.private.encoded)
        blobStore.encrypt(BLOB_SSH_PUB_KEY, pair.public.encoded)

        return openSshEcdsaPublicKey(pair.public as ECPublicKey)
    }

    override suspend fun getGpgKey(activity: FragmentActivity): PGPSecretKeyRing {
        ensureAuthenticated(activity)
        val bytes = blobStore.decrypt(BLOB_GPG_KEY)
        return PGPainless.readKeyRing().secretKeyRing(bytes)
            ?: error("Corrupted GPG key blob")
    }

    override suspend fun getSshKey(activity: FragmentActivity): KeyPair {
        ensureAuthenticated(activity)
        val privateBytes = blobStore.decrypt(BLOB_SSH_KEY)
        val publicBytes = blobStore.decrypt(BLOB_SSH_PUB_KEY)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))
        return KeyPair(publicKey, privateKey)
    }

    override fun clearAllKeys() {
        sessionManager.invalidate()
        blobStore.deleteAll()
    }

    private suspend fun ensureAuthenticated(activity: FragmentActivity) {
        if (!sessionManager.isSessionActive()) {
            showBiometricPrompt(activity)
            sessionManager.recordAuth()
        }
    }

    private fun stripPassphrase(keys: PGPSecretKeyRing, passphrase: String?): PGPSecretKeyRing {
        val digestCalcProvider = BcPGPDigestCalculatorProvider()
        val decryptor = BcPBESecretKeyDecryptorBuilder(digestCalcProvider)
            .build(passphrase?.toCharArray() ?: charArrayOf())
        return PGPSecretKeyRing(keys.map { secretKey ->
            PGPSecretKey.copyWithNewPassword(secretKey, decryptor, null)
        })
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
