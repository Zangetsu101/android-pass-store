// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.ssh

import com.zangetsu101.pass.keymanagement.SshKeyPair
import com.zangetsu101.pass.keymanagement.crypto.CryptoStore

interface SshKeyStore : CryptoStore {
    fun generateSshKey(): String

    fun getSshKey(): SshKeyPair

    fun importEd25519Seed(seed: ByteArray): String
}
