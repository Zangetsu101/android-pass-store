// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

private val windowsDeviceName = Regex("CON|PRN|AUX|NUL|(?:COM|LPT)[1-9¹²³]", RegexOption.IGNORE_CASE)

internal class EntryPath(
    input: String,
) {
    val value = input.trim()
    val folder = value.substringBeforeLast('/', "")
    val name = value.substringAfterLast('/')
    private val segments = value.split('/')
    private val parents = segments.dropLast(1)
    val filePath get() = "$value.gpg"

    /** A Flat Store path has no `/` and has a `.`, such as `github.com`. */
    val flatStore get() = '/' !in value && '.' in value

    val error: String? =
        when {
            value.isEmpty() -> "enter an entry path"
            segments.any { it.isEmpty() } -> "path has an empty segment"
            segments.any { it.startsWith('.') } -> "a path segment cannot start with a dot"
            value.endsWith(".gpg", ignoreCase = true) -> "leave off the .gpg extension"
            value.any { it in "\\:*?\"<>|" || it.isISOControl() } -> "path has an invalid character"
            segments.any { windowsDeviceName.matches(it.substringBefore('.').trimEnd()) } -> "path uses a name reserved by Windows"
            parents.any { it.endsWith(' ') || it.endsWith('.') } -> "a folder name cannot end with a space or dot"
            parents.any { it == "cards" || it == "credit-cards" } -> "choose a folder for login entries"
            else -> null
        }
}
