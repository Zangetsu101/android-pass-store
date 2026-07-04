// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import javax.crypto.Cipher

interface BiometricCryptoStore : CryptoStore {
    val maxBytes: Int

    fun store(data: ByteArray)

    @Throws(KeyPermanentlyInvalidatedException::class)
    fun getDecryptCipher(): Cipher

    fun get(cipher: Cipher): ByteArray
}