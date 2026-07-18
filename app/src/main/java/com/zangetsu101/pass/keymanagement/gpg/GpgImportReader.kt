// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.gpg

import org.pgpainless.PGPainless
import javax.inject.Inject

interface GpgImportReader {
    @Throws(KeyImportError::class)
    fun parseCandidate(armoredKey: String): GpgImportCandidate

    @Throws(KeyImportError::class)
    fun armor(bytes: ByteArray): String
}

class GpgImportReaderImpl
    @Inject
    constructor() : GpgImportReader {
        override fun parseCandidate(armoredKey: String): GpgImportCandidate {
            val keys =
                try {
                    PGPainless
                        .getInstance()
                        .readKey()
                        .parseKey(armoredKey)
                        .pgpSecretKeyRing
                } catch (e: Exception) {
                    throw KeyImportError.Malformed(e)
                }
            return GpgImportCandidate(armoredKey, keys)
        }

        override fun armor(bytes: ByteArray): String {
            val ring =
                try {
                    PGPainless
                        .getInstance()
                        .readKey()
                        .parseKey(bytes.inputStream())
                        .pgpSecretKeyRing
                } catch (e: Exception) {
                    throw KeyImportError.Malformed(e)
                }
            return PGPainless.asciiArmor(ring)
        }
    }
