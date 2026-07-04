// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import java.io.File

data class PassEntry(
    val path: String,
    val domain: String?,
    val username: String,
    val encryptedFile: File,
    val isCard: Boolean = false,
)
