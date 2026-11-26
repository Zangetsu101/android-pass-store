// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteChipsTest {
    @Test
    fun `a login in a folder gets the username url and email chips`() {
        assertEquals(
            listOf(NoteChip("username", false), NoteChip("url", false), NoteChip("email", false)),
            noteChips("dev/github.com/alice", "", EntryType.Login),
        )
    }

    @Test
    fun `a flat store path with a dot has no url chip`() {
        assertEquals(listOf("username", "email"), noteChips("github.com", "", EntryType.Login).map { it.key })
        assertEquals(listOf("username", "url", "email"), noteChips("github", "", EntryType.Login).map { it.key })
        assertEquals(listOf("username", "url", "email"), noteChips("web/github.com", "", EntryType.Login).map { it.key })
    }

    @Test
    fun `a card gets the cardholder cvv expiry and pin chips`() {
        assertEquals(listOf("cardholder", "cvv", "expiry", "pin"), noteChips("cards/visa", "", EntryType.Card).map { it.key })
        assertEquals(listOf("cardholder", "cvv", "expiry", "pin"), noteChips("visa.com", "", EntryType.Card).map { it.key })
    }

    @Test
    fun `a chip is used when a line starts with its key`() {
        val chips = noteChips("dev/github.com/alice", "Username: alice\nemail:\nnote about url: here", EntryType.Login)

        assertEquals(listOf(true, false, true), chips.map { it.used })
    }

    @Test
    fun `adding a key starts a new line at the end of the notes`() {
        assertEquals("username: ", addNoteKey("", "username"))
        assertEquals("note\nurl: ", addNoteKey("note", "url"))
        assertEquals("note\nurl: ", addNoteKey("note\n", "url"))
    }
}
