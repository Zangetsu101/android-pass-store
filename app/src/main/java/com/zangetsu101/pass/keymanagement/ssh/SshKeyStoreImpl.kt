package com.zangetsu101.pass.keymanagement.ssh

import android.util.Base64
import com.zangetsu101.pass.BuildConfig
import com.zangetsu101.pass.keymanagement.SshKeyPair
import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class SshKeyStoreImpl(
    private val privateKeyStore: PlainCryptoStore,
    private val publicKeyStore: PlainCryptoStore,
) : SshKeyStore {
    override fun exists(): Boolean = privateKeyStore.exists() && publicKeyStore.exists()

    override fun delete() {
        privateKeyStore.delete()
        publicKeyStore.delete()
    }

    override fun generateSshKey(): String {
        val pair = if (BuildConfig.DEBUG) generateTestSshKey() else generateKeyPair()
        privateKeyStore.store(pair.private.encoded)
        publicKeyStore.store(pair.public.encoded)
        return openSshPublicKey(pair.public)
    }

    override fun getSshKey(): SshKeyPair {
        val privateBytes = privateKeyStore.get()
        val publicBytes = publicKeyStore.get()
        val kf = KeyFactory.getInstance("Ed25519", BouncyCastleProvider())
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = kf.generatePublic(X509EncodedKeySpec(publicBytes))
        return KeyPair(publicKey, privateKey)
    }

    override fun importEd25519Seed(seed: ByteArray): String {
        val privateParams = Ed25519PrivateKeyParameters(seed)
        val publicParams = privateParams.generatePublicKey()
        val kf = KeyFactory.getInstance("Ed25519", BouncyCastleProvider())
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(PrivateKeyInfoFactory.createPrivateKeyInfo(privateParams).encoded))
        val publicKey = kf.generatePublic(X509EncodedKeySpec(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicParams).encoded))
        privateKeyStore.store(privateKey.encoded)
        publicKeyStore.store(publicKey.encoded)
        return openSshPublicKey(publicKey)
    }

    private fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider())
        return kpg.generateKeyPair()
    }

    private fun generateTestSshKey(): KeyPair {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val privateParams = Ed25519PrivateKeyParameters(seed)
        val publicParams = privateParams.generatePublicKey()
        val kf = KeyFactory.getInstance("Ed25519", BouncyCastleProvider())
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(PrivateKeyInfoFactory.createPrivateKeyInfo(privateParams).encoded))
        val publicKey = kf.generatePublic(X509EncodedKeySpec(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicParams).encoded))
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
