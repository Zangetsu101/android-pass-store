// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteChipsTest {
    @Test
    fun `a login in a folder gets the username url and email chips`() {
        assertEquals(
            listOf(NoteChip("username", false), NoteChip("url", false), NoteChip("email", false)),
            noteChips("dev/github.com/alice", ""),
        )
    }

    @Test
    fun `a flat store path with a dot has no url chip`() {
        assertEquals(listOf("username", "email"), noteChips("github.com", "").map { it.key })
        assertEquals(listOf("username", "url", "email"), noteChips("github", "").map { it.key })
        assertEquals(listOf("username", "url", "email"), noteChips("web/github.com", "").map { it.key })
    }

    @Test
    fun `a chip is used when a line starts with its key`() {
        val chips = noteChips("dev/github.com/alice", "Username: alice\nemail:\nnote about url: here")

        assertEquals(listOf(true, false, true), chips.map { it.used })
    }

    @Test
    fun `adding a key starts a new line at the end of the notes`() {
        assertEquals("username: ", addNoteKey("", "username"))
        assertEquals("note\nurl: ", addNoteKey("note", "url"))
        assertEquals("note\nurl: ", addNoteKey("note\n", "url"))
    }

    @Test
    fun `the line count of the notes skips blank lines`() {
        assertEquals(0, noteLineCount(""))
        assertEquals(0, noteLineCount("\n  \n"))
        assertEquals(2, noteLineCount("username: alice\n\nnote\n"))
    }
}
