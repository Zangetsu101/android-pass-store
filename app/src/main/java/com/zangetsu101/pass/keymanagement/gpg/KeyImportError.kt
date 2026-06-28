package com.zangetsu101.pass.keymanagement.gpg

sealed class KeyImportError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Short heading shown on the import-screen error modal. */
    abstract val title: String

    class NoPassphrase(
        keyIds: List<String> = emptyList(),
    ) : KeyImportError(
            buildString {
                append("this key is not passphrase-protected")
                if (keyIds.isNotEmpty()) append(" (${keyIds.joinToString(", ")})")
                append(". re-export it with a passphrase — at-rest security depends on it.")
            },
        ) {
        override val title = "key not protected"
    }

    class Malformed(
        cause: Throwable? = null,
    ) : KeyImportError(
            "this doesn't look like an armored gpg secret key. check you exported a private key, not a public one.",
            cause,
        ) {
        override val title = "unrecognized key"
    }

    class NoEncryptionKey :
        KeyImportError("this key can't decrypt your store — it has no encryption [E] subkey. import a key that includes one.") {
        override val title = "no encryption key"
    }

    class ExpiredEncryptionKey(
        keyIds: List<String>,
    ) : KeyImportError(
            "your encryption key (${keyIds.joinToString(", ")}) is expired or revoked. " +
                "renew it in gpg, then re-export with --export-secret-subkeys.",
        ) {
        override val title = "encryption key expired"
    }

    class PublicKeyOnly(
        keyIds: List<String>,
    ) : KeyImportError(
            "your encryption key (${keyIds.joinToString(", ")}) has no private material — it may live on a smartcard, " +
                "or was exported public-only. re-export with the secret subkey present.",
        ) {
        override val title = "private key missing"
    }
}
