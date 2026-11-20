// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditableLoginTest {
    @Test
    fun `editing preserves valid and invalid OTP lines and mixed endings`() {
        val original = "old\r\nuser: alice\n\totpauth://totp/alice?secret=JBSWY3DPEHPK3PXP\r\nold note\r  otpauth://invalid"
        val login = EditableLogin(original)

        assertEquals("user: alice\nold note", login.notes)
        assertEquals(
            "new\r\nuser: alice\n\totpauth://totp/alice?secret=JBSWY3DPEHPK3PXP\r\nnew note  \r  otpauth://invalid",
            login.render("new", "user: alice\nnew note  "),
        )
    }

    @Test
    fun `editing an OTP-only entry keeps its first line and final newline`() {
        val login = EditableLogin("otpauth://invalid\r\n")
        assertEquals("otpauth://invalid\r\nnote\r\n", login.render("", "note"))
        assertEquals("new\r\notpauth://invalid\r\n", login.render("new", ""))
    }

    @Test
    fun `OTP lines stay between surviving notes when earlier notes are inserted or deleted`() {
        val login = EditableLogin("password\r\nA\r\nobsolete\r\notpauth://invalid\r\nB")
        assertEquals(
            "password\r\nnew\r\nA\r\notpauth://invalid\r\nB",
            login.render("password", "new\nA\nB"),
        )
    }

    @Test
    fun `saving notes retains blank lines and preserves an unterminated final line`() {
        val login = EditableLogin("password\nnotes")
        assertEquals("password\n\nnew\n  ", login.render("password", "\nnew\n  "))
    }
}
