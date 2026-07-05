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
    fun storeImportedGpgKey(armoredKey: String)

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
