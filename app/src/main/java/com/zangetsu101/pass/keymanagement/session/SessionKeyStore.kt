package com.zangetsu101.pass.keymanagement.session

import android.security.keystore.KeyPermanentlyInvalidatedException
import javax.crypto.Cipher

interface SessionKeyStore {
    val maxPassphraseBytes: Int

    fun hasSession(): Boolean

    fun createKey()

    fun storeEncryptedPassphrase(plaintext: ByteArray)

    fun readEncryptedPassphrase(): ByteArray

    fun deleteSession()

    @Throws(KeyPermanentlyInvalidatedException::class)
    fun getDecryptCipher(): Cipher
}
