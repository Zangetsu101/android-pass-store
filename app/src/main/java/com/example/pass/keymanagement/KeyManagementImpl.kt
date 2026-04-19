package com.example.pass.keymanagement

import android.content.Context
import android.util.Base64
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.pgpainless.PGPainless
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

internal const val BLOB_GPG_KEY = "gpg_key"
internal const val BLOB_SSH_KEY = "ssh_key"

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

    override fun generateSshKey(): String {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val asymPair = gen.generateKeyPair()
        val privateParams = asymPair.private as Ed25519PrivateKeyParameters
        val publicParams = asymPair.public as Ed25519PublicKeyParameters

        blobStore.encrypt(BLOB_SSH_KEY, privateParams.encoded)

        return openSshPublicKey(publicParams.encoded)
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
        val privateParams = Ed25519PrivateKeyParameters(privateBytes, 0)
        val publicParams = privateParams.generatePublicKey()

        val provider = BouncyCastleProvider()
        val keyFactory = KeyFactory.getInstance("Ed25519", provider)
        val privateKey = keyFactory.generatePrivate(
            PKCS8EncodedKeySpec(PrivateKeyInfoFactory.createPrivateKeyInfo(privateParams).encoded)
        )
        val publicKey = keyFactory.generatePublic(
            X509EncodedKeySpec(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicParams).encoded)
        )
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

    private fun openSshPublicKey(rawPublicBytes: ByteArray): String {
        val keyType = "ssh-ed25519"
        val keyTypeBytes = keyType.toByteArray(Charsets.UTF_8)
        val buf = ByteArrayOutputStream()
        val out = DataOutputStream(buf)
        out.writeInt(keyTypeBytes.size)
        out.write(keyTypeBytes)
        out.writeInt(rawPublicBytes.size)
        out.write(rawPublicBytes)
        out.flush()
        return "$keyType ${Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)}"
    }
}
