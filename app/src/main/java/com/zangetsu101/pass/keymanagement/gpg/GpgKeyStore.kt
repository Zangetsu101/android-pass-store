// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.gpg

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import com.zangetsu101.pass.keymanagement.session.SessionError
import org.bouncycastle.openpgp.PGPSecretKeyRing

data class GpgImportCandidate(
    val armoredKey: String,
    val secretKeyRing: PGPSecretKeyRing,
)

interface GpgKeyStore : PlainCryptoStore {
    @Throws(KeyImportError::class)
    fun importGpgKey(armoredKey: String)

    @Throws(KeyImportError::class)
    fun parseGpgKeyImportCandidate(armoredKey: String): GpgImportCandidate

    @Throws(KeyImportError.NoEncryptionKey::class)
    fun requireEncryptionSubkey(candidate: GpgImportCandidate)

    @Throws(KeyImportError.ExpiredEncryptionKey::class)
    fun requireValidEncryptionSubkey(candidate: GpgImportCandidate)

    @Throws(KeyImportError.PublicKeyOnly::class)
    fun requirePrivateEncryptionMaterial(candidate: GpgImportCandidate)

    @Throws(KeyImportError.NoPassphrase::class)
    fun requirePassphraseProtection(candidate: GpgImportCandidate)

    fun hasReusableAuthSubkey(candidate: GpgImportCandidate): Boolean

    fun storeImportedGpgKey(armoredKey: String)

    @Throws(KeyImportError::class)
    fun armorGpgKey(bytes: ByteArray): String

    @Throws(SessionError::class)
    fun validatePassphrase(passphrase: String)

    fun loadAndUnlock(passphrase: String): GpgPrivateKey

    fun getGpgKeyInfo(): Pair<String, String>?

    fun findAuthSubkey(): AuthSubkeyInfo?

    @Throws(SessionError::class)
    fun extractAuthSubkeySeed(
        passphrase: String,
        keyId: Long,
    ): ByteArray
}
