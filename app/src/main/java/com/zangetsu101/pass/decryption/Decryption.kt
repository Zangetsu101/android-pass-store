// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.passstore.PassEntry

interface Decryption {
    @Throws(DecryptionError::class)
    suspend fun decryptForEntryDetail(
        entry: PassEntry,
        activity: FragmentActivity,
    ): DecryptedEntry

    @Throws(DecryptionError::class)
    suspend fun decrypt(
        entry: PassEntry,
        activity: FragmentActivity,
    ): Credentials
}

class DecryptedEntry internal constructor(
    val credentials: Credentials,
    internal val rawLoginEntry: RawLoginEntry? = null,
)

internal class RawLoginEntry(
    val plaintext: String,
    val ciphertextDigest: ByteArray,
)
