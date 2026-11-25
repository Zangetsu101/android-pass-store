// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotesLineCountTest {
    @Test
    fun `the line count of the notes skips blank lines`() {
        assertEquals(0, noteLineCount(""))
        assertEquals(0, noteLineCount("\n  \n"))
        assertEquals(2, noteLineCount("username: alice\n\nnote\n"))
    }
}
