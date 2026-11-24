// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

/** A Note Field: a `key: value` line from the notes of an entry. */
data class NoteField(
    val key: String,
    val value: String,
) {
    override fun toString(): String = "NoteField($key)"
}

/** The notes of an entry, split into its Note Fields and its other lines. */
data class EntryNotes(
    val fields: List<NoteField>,
    val lines: List<String>,
) {
    /** The value of the Username Line: the first `username`, `login` or `user` field, in that order of keys. */
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

/** The key of [line], also when the line has no value yet. A Note Field uses the same rule for its key. */
fun noteKey(line: String): String? = keyPattern.find(line)?.groupValues?.get(1)

private const val KEY = """[^\s:]+"""
private val keyPattern = Regex("""^($KEY):""")
private val fieldPattern = Regex("""($KEY):\s+(.*\S)\s*""")
