// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

sealed class SessionError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class WrongPassphrase : SessionError("Wrong passphrase")

    class NoActiveSession : SessionError("No active session")

    class PassphraseTooLong : SessionError("Passphrase is too long")
}
