// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

/**
 * Decrypted credentials. Note: these secrets are NOT scrubbed from memory.
 * `notes` and the full decrypted plaintext exist as immutable Strings upstream
 * (see DecryptionImpl / the OpenPGP library API), so wiping only `password`
 * would be security theater. An attacker who can read this process's heap is
 * already running with the app's identity — threat C (malware / rooted device),
 * which is explicitly out of scope (see docs/adr/0002-threat-model.md).
 */
data class Credentials(
    val password: CharArray,
    val notes: String,
    val username: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Credentials) return false
        return password.contentEquals(other.password) && notes == other.notes && username == other.username
    }

    override fun hashCode(): Int {
        var result = password.contentHashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + username.hashCode()
        return result
    }
}
