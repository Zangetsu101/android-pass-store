// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.crypto

interface PlainCryptoStore : CryptoStore {
    fun store(data: ByteArray)

    fun get(): ByteArray
}
