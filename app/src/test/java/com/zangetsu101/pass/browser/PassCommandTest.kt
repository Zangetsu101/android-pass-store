// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.generator.CharacterSet
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.generator.SAFE_SYMBOLS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PassCommandTest {
    private val default = GeneratorSettings()
    private val PassCommand.text get() = parts.joinToString(" ") { it.text }

    @Test
    fun `a generated password shows each flag only when it applies`() {
        assertEquals("pass generate dev/github", PassCommand.generate("dev/github", default, copy = false, force = false).text)
        assertEquals("pass generate -c dev/github", PassCommand.generate("dev/github", default, copy = true, force = false).text)
        assertEquals("pass generate -f dev/github", PassCommand.generate("dev/github", default, copy = false, force = true).text)
        val noSymbols = default.toggle(CharacterSet.Symbols)
        assertEquals(
            "pass generate -c -n -f dev/github",
            PassCommand.generate("dev/github", noSymbols, copy = true, force = true).text,
        )
        assertEquals(
            "pass generate -c dev/github 32",
            PassCommand.generate("dev/github", default.withLength(32), copy = true, force = false).text,
        )
    }

    @Test
    fun `flags and a length that is not 25 are highlighted`() {
        val command = PassCommand.generate("dev/github", default.toggle(CharacterSet.Symbols).withLength(32), copy = true, force = false)
        assertEquals(
            listOf(
                CommandPart("pass generate"),
                CommandPart("-c", highlighted = true),
                CommandPart("-n", highlighted = true),
                CommandPart("dev/github"),
                CommandPart("32", highlighted = true),
            ),
            command.parts,
        )
    }

    @Test
    fun `a custom character set shows on a line below the command`() {
        assertEquals(null, PassCommand.generate("a", default, copy = true, force = false).charset)
        assertEquals(null, PassCommand.generate("a", default.toggle(CharacterSet.Symbols), copy = true, force = false).charset)
        assertEquals(
            "A–z 0–9  !@#\$%^&*-_",
            PassCommand.generate("a", default.withSymbols(SAFE_SYMBOLS), copy = true, force = false).charset,
        )
        assertEquals(null, PassCommand.insert("a", multiline = false, force = false).charset)
    }

    @Test
    fun `a typed password shows pass insert`() {
        assertEquals("pass insert dev/github", PassCommand.insert("dev/github", multiline = false, force = false).text)
        assertEquals("pass insert -m dev/github", PassCommand.insert("dev/github", multiline = true, force = false).text)
        assertEquals("pass insert -m -f dev/github", PassCommand.insert("dev/github", multiline = true, force = true).text)
        assertEquals("pass insert -f dev/github", PassCommand.insert("dev/github", multiline = false, force = true).text)
    }

    @Test
    fun `a regenerate in the edit form replaces line 1 in place`() {
        assertEquals("pass generate -i dev/github", PassCommand.generateInPlace("dev/github", default).text)
        assertEquals(
            "pass generate -i -n dev/github 16",
            PassCommand.generateInPlace("dev/github", default.toggle(CharacterSet.Symbols).withLength(16)).text,
        )
        assertEquals(
            "A–Z 0–9  all symbols",
            PassCommand.generateInPlace("dev/github", default.toggle(CharacterSet.Lower)).charset,
        )
    }

    @Test
    fun `the line count counts the notes of a login and the number and details of a card`() {
        assertEquals(null, saveLineSummary(0, EntryType.Login))
        assertEquals("+ 1 line of notes", saveLineSummary(1, EntryType.Login))
        assertEquals("+ 2 lines of notes", saveLineSummary(2, EntryType.Login))
        assertEquals("1 line · number", saveLineSummary(0, EntryType.Card))
        assertEquals("2 lines · number + 1 detail", saveLineSummary(1, EntryType.Card))
        assertEquals("4 lines · number + 3 details", saveLineSummary(3, EntryType.Card))
    }
}
