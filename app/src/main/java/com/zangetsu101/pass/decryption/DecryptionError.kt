// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

class DecryptionError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
