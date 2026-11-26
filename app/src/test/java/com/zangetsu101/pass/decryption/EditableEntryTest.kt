// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import com.zangetsu101.pass.generator.GeneratorSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random

class EditableEntryTest {
    @Test
    fun `editing preserves valid and invalid OTP lines and mixed endings`() {
        val original = "old\r\nuser: alice\n\totpauth://totp/alice?secret=JBSWY3DPEHPK3PXP\r\nold note\r  otpauth://invalid"
        val entry = EditableEntry(original)

        assertEquals("user: alice\nold note", entry.notes)
        assertEquals(
            "new\r\nuser: alice\n\totpauth://totp/alice?secret=JBSWY3DPEHPK3PXP\r\nnew note  \r  otpauth://invalid",
            entry.render("new", "user: alice\nnew note  "),
        )
    }

    @Test
    fun `a regenerated password replaces only line 1 and keeps the rest of the file`() {
        val original = "old\r\nuser: alice\n\totpauth://totp/alice?secret=JBSWY3DPEHPK3PXP\r\n\r\nnote\r\n"
        val entry = EditableEntry(original)
        val generated = GeneratorSettings().generate(Random(5))

        assertEquals(generated + original.removePrefix("old"), entry.render(generated, entry.notes))
    }

    @Test
    fun `editing an OTP-only entry keeps its first line and final newline`() {
        val entry = EditableEntry("otpauth://invalid\r\n")
        assertEquals("otpauth://invalid\r\nnote\r\n", entry.render("", "note"))
        assertEquals("new\r\notpauth://invalid\r\n", entry.render("new", ""))
    }

    @Test
    fun `OTP lines stay between surviving notes when earlier notes are inserted or deleted`() {
        val entry = EditableEntry("password\r\nA\r\nobsolete\r\notpauth://invalid\r\nB")
        assertEquals(
            "password\r\nnew\r\nA\r\notpauth://invalid\r\nB",
            entry.render("password", "new\nA\nB"),
        )
    }

    @Test
    fun `saving notes retains blank lines and preserves an unterminated final line`() {
        val entry = EditableEntry("password\nnotes")
        assertEquals("password\n\nnew\n  ", entry.render("password", "\nnew\n  "))
    }
}
