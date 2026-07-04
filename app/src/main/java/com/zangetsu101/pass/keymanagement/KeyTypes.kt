// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement

import org.bouncycastle.openpgp.api.OpenPGPKey
import java.security.KeyPair

typealias GpgPrivateKey = OpenPGPKey
typealias SshKeyPair = KeyPair

data class AuthSubkeyInfo(
    val keyId: Long,
    val sshPublicKey: String,
    val sshFingerprint: String,
    val uid: String,
    val created: Long,
)