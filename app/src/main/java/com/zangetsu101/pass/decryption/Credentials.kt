// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import com.zangetsu101.pass.otp.Otp
import com.zangetsu101.pass.otp.parseOtp

/**
 * Decrypted credentials. Note: these secrets are NOT scrubbed from memory.
 * `notes` and the full decrypted plaintext exist as immutable Strings upstream
 * (see DecryptionImpl / the OpenPGP library API), so wiping only `secret`
 * would be security theater. An attacker who can read this process's heap is
 * already running with the app's identity — threat C (malware / rooted device),
 * which is explicitly out of scope (see docs/adr/0002-threat-model.md).
 */
data class Credentials(
    val secret: CharArray,
    val notes: String,
    val username: String,
    val otp: Otp? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Credentials) return false
        return secret.contentEquals(other.secret) && notes == other.notes && username == other.username && otp == other.otp
    }

    override fun hashCode(): Int {
        var result = secret.contentHashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + (otp?.hashCode() ?: 0)
        return result
    }
}

/**
 * Keep line 1 as the secret; never promote a note into the secret.
 * A username line in the notes wins over [fileUsername], which comes from the entry path.
 */
internal fun parseCredentials(
    plaintext: String,
    fileUsername: String,
): Credentials {
    val lines = plaintext.lines()
    val otpLine = lines.firstOrNull(::isOtpLine)
    val notes =
        lines
            .drop(1)
            .filterNot(::isOtpLine)
            .joinToString("\n")
            .trimEnd()
    return Credentials(
        secret =
            lines
                .firstOrNull()
                ?.takeUnless(::isOtpLine)
                .orEmpty()
                .toCharArray(),
        notes = notes,
        username = parseEntryNotes(notes).username ?: fileUsername,
        otp = otpLine?.let(::parseOtp),
    )
}

internal fun isOtpLine(line: String): Boolean = line.trimStart(' ', '\t').startsWith("otpauth://")
