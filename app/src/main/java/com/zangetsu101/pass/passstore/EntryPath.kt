// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

internal class EntryPath(
    input: String,
) {
    val value = input.trim()
    val folder = value.substringBeforeLast('/', "")
    val name = value.substringAfterLast('/')
    private val segments = value.split('/')
    val filePath get() = "$value.gpg"

    val error: String? =
        when {
            value.isEmpty() -> "enter an entry path"
            segments.any { it.isEmpty() } -> "path has an empty segment"
            segments.any { it.startsWith('.') } -> "a path segment cannot start with a dot"
            value.endsWith(".gpg", ignoreCase = true) -> "leave off the .gpg extension"
            value.any { it in "\\:*?\"<>|" || it.isISOControl() } -> "path has an invalid character"
            segments.dropLast(1).any { it == "cards" || it == "credit-cards" } -> "choose a folder for login entries"
            else -> null
        }
}
