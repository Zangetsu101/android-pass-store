// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.decryption.noteKey
import com.zangetsu101.pass.passstore.EntryPath

/** A chip above the notes editor that adds a `key: ` line. It is [used] when the notes have that key. */
internal data class NoteChip(
    val key: String,
    val used: Boolean,
)

/** In a Flat Store, such as `github.com`, the path already gives the URL, so there is no `url` chip. */
internal fun noteChips(
    path: String,
    notes: String,
    type: EntryType = EntryType.Login,
): List<NoteChip> {
    val keys =
        when {
            type == EntryType.Card -> listOf("cardholder", "cvv", "expiry", "pin")
            EntryPath(path).flatStore -> listOf("username", "email")
            else -> listOf("username", "url", "email")
        }
    val lines = notes.lines()
    return keys.map { key -> NoteChip(key, lines.any { noteKey(it).equals(key, ignoreCase = true) }) }
}

internal fun addNoteKey(
    notes: String,
    key: String,
): String = (if (notes.isEmpty() || notes.endsWith('\n')) notes else notes + "\n") + "$key: "
