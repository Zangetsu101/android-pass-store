package com.zangetsu101.pass.keymanagement.ssh

import android.util.Base64
import com.zangetsu101.pass.keymanagement.SshKeyPair
import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
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
        val pair = generateTestSshKey()
        privateKeyStore.store(pair.private.encoded)
        publicKeyStore.store(pair.public.encoded)
        return openSshPublicKey(pair.public)
    }

    override fun getSshKey(): SshKeyPair {
        val privateBytes = privateKeyStore.get()
        val publicBytes = publicKeyStore.get()
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
        out.writeSshString("ecdsa-sha2-nistp256")
        out.writeSshString("nistp256")
        out.writeSshBytes(rawKeyBytes)
        out.flush()
        return "ecdsa-sha2-nistp256 ${Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)}"
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
