// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntryNotesTest {
    @Test
    fun `key value lines become fields and the other lines keep their order`() {
        val notes =
            parseEntryNotes(
                listOf(
                    "username: alice",
                    "first note",
                    "url: https://example.com/login?a=b",
                    "",
                    "second note",
                ).joinToString("\n"),
            )

        assertEquals(
            listOf(NoteField("username", "alice"), NoteField("url", "https://example.com/login?a=b")),
            notes.fields,
        )
        assertEquals(listOf("first note", "", "second note"), notes.lines)
    }

    @Test
    fun `lines that do not match key colon value stay as lines`() {
        val lines =
            listOf(
                "pin : 1234",
                "two words: value",
                "no colon here",
                "https://example.com",
                "empty:",
                "blank:   ",
                " indented: value",
            )

        val notes = parseEntryNotes(lines.joinToString("\n"))

        assertEquals(emptyList<NoteField>(), notes.fields)
        assertEquals(lines, notes.lines)
    }

    @Test
    fun `OTP lines are in neither the fields nor the lines`() {
        val notes = parseEntryNotes("user: bob\n  otpauth://totp/x?secret=ABC\nnote")

        assertEquals(listOf(NoteField("user", "bob")), notes.fields)
        assertEquals(listOf("note"), notes.lines)
    }

    @Test
    fun `the value keeps inner spaces and colons and loses outer spaces`() {
        val notes = parseEntryNotes("answer:\tred  car: blue \nUserName: Alice")

        assertEquals(listOf(NoteField("answer", "red  car: blue"), NoteField("UserName", "Alice")), notes.fields)
    }

    @Test
    fun `secret keys are pin cvv secret answer recovery and each key with password`() {
        val secret = listOf("pin", "cvv", "secret", "answer", "recovery", "password", "wifi-password", "PasswordHint", "PIN")
        val plain = listOf("username", "url", "email", "pins", "pass", "recovery-email", "cardholder")

        assertEquals(secret, secret.filter { NoteField(it, "x").secret })
        assertEquals(emptyList<String>(), plain.filter { NoteField(it, "x").secret })
    }

    @Test
    fun `empty notes have no fields and no lines`() {
        assertEquals(EntryNotes(emptyList(), emptyList()), parseEntryNotes(""))
    }
}
