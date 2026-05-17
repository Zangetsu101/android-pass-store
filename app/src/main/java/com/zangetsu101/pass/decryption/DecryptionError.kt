package com.zangetsu101.pass.decryption

class DecryptionError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
