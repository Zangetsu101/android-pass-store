// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

/** A `key: value` line from the notes of an entry. */
data class NoteField(
    val key: String,
    val value: String,
) {
    /** Entry detail blurs the value of a secret field until the user taps it. */
    val secret: Boolean
        get() = key.lowercase().let { it in secretKeys || "password" in it }

    override fun toString(): String = "NoteField($key)"
}

private val secretKeys = setOf("pin", "cvv", "secret", "answer", "recovery")

/** The notes of an entry, split into its `key: value` fields and its other lines. */
data class EntryNotes(
    val fields: List<NoteField>,
    val lines: List<String>,
) {
    /** The value of the first `username`, `login` or `user` field, in that order of keys. */
    val username: String?
        get() =
            usernameKeys.firstNotNullOfOrNull { key ->
                fields.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            }
}

private val usernameKeys = listOf("username", "login", "user")

fun parseEntryNotes(notes: String): EntryNotes {
    val fields = mutableListOf<NoteField>()
    val lines = mutableListOf<String>()
    if (notes.isEmpty()) return EntryNotes(fields, lines)
    for (line in notes.lines()) {
        if (isOtpLine(line)) continue
        val match = fieldPattern.matchEntire(line)
        if (match != null) {
            fields += NoteField(match.groupValues[1], match.groupValues[2])
        } else {
            lines += line
        }
    }
    return EntryNotes(fields, lines)
}

private val fieldPattern = Regex("""([^\s:]+):\s+(.*\S)\s*""")
