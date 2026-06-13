package com.zangetsu101.pass.keymanagement.gpg

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import com.zangetsu101.pass.keymanagement.session.SessionError

interface GpgKeyStore : PlainCryptoStore {
    @Throws(KeyImportError::class)
    fun importGpgKey(armoredKey: String)

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
