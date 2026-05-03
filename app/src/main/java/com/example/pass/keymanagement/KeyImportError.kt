package com.example.pass.keymanagement

sealed class KeyImportError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NoPassphrase : KeyImportError("Key must be passphrase-protected")

    class Malformed(
        cause: Throwable? = null,
    ) : KeyImportError("Malformed armored key", cause)
}
