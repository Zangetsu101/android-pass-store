// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.gpg

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.api.OpenPGPKey
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.EncryptionPurpose
import org.pgpainless.algorithm.KeyFlag
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

class GpgKeyInspector
    @Inject
    constructor() {
        fun anyEncryptionFlaggedKeyIds(keys: PGPSecretKeyRing): List<String> {
            val info = PGPainless.getInstance().inspect(OpenPGPKey(keys))
            return (info.getKeysWithKeyFlag(KeyFlag.ENCRYPT_COMMS) + info.getKeysWithKeyFlag(KeyFlag.ENCRYPT_STORAGE))
                .map { it.pgpPublicKey.keyID.shortId() }
                .distinct()
        }

        /** Key IDs of valid (non-expired/revoked) encrypt-capable subkeys, regardless of private material. */
        fun validEncryptionKeyIds(keys: PGPSecretKeyRing): Set<Long> =
            PGPainless
                .getInstance()
                .inspect(OpenPGPKey(keys))
                .getEncryptionSubkeys(EncryptionPurpose.ANY)
                .map { it.pgpPublicKey.keyID }
                .toSet()

        fun findAuthSubkey(ring: PGPSecretKeyRing): AuthSubkeyInfo? {
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
            return AuthSubkeyInfo(
                keyId = pubKey.keyID,
                sshPublicKey = sshPubKey,
                sshFingerprint = computeSshFingerprint(sshPubKey),
                uid = uid,
                created = pubKey.creationTime.time / 1000L,
            )
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
