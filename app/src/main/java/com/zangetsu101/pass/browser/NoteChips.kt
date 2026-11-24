// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.decryption.noteKey

/** A chip above the notes editor that adds a `key: ` line. It is [used] when the notes have that key. */
internal data class NoteChip(
    val key: String,
    val used: Boolean,
)

/** In a Flat Store, such as `github.com`, the path already gives the URL, so there is no `url` chip. */
internal fun noteChips(
    path: String,
    notes: String,
): List<NoteChip> {
    val flatStore = '/' !in path && '.' in path
    val keys = if (flatStore) listOf("username", "email") else listOf("username", "url", "email")
    val lines = notes.lines()
    return keys.map { key -> NoteChip(key, lines.any { noteKey(it).equals(key, ignoreCase = true) }) }
}

internal fun addNoteKey(
    notes: String,
    key: String,
): String = (if (notes.isEmpty() || notes.endsWith('\n')) notes else notes + "\n") + "$key: "

/** The number of notes lines that the forms show. Blank lines do not count. */
internal fun noteLineCount(notes: String): Int = notes.lines().count { it.isNotBlank() }
