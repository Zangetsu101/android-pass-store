package com.example.pass.keymanagement

sealed class SessionError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class WrongPassphrase : SessionError("Wrong passphrase")

    class NoActiveSession : SessionError("No active session")

    class PassphraseTooLong : SessionError("Passphrase is too long")
}
