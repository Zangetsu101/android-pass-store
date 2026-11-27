// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.decryption.EditableEntry
import com.zangetsu101.pass.decryption.EntryEditSource
import com.zangetsu101.pass.generator.CharacterSet
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.generator.PasswordDraft
import com.zangetsu101.pass.passstore.PasswordSource
import org.junit.jupiter.api.Test

class EntryEditorStateTest {
    @Test
    fun `a Generated Password with the same notes replaces only line 1`() {
        val settings = GeneratorSettings().withLength(32).toggle(CharacterSet.Symbols)
        val editor = editor("old\nuser: alice", SecretDraft.Password(PasswordDraft("new", settings, generated = true)))
        assertPlan(editor.plan, "pass generate -i -n dev/github.com 32", PasswordSource.Generated, "new")
    }

    @Test
    fun `a Generated Password with changed notes uses the Given Password source`() {
        val editor = editor("old\nuser: alice", SecretDraft.Password(PasswordDraft("new", generated = true)))
        assertPlan(editor.copy(notes = "user: bob").plan, "pass insert -m -f dev/github.com", PasswordSource.Given, "new")
    }

    @Test
    fun `the same password and notes do not use in-place generation`() {
        val editor = editor("same\nuser: alice", SecretDraft.Password(PasswordDraft("same", generated = true)))
        assertPlan(editor.plan, "pass insert -m -f dev/github.com", PasswordSource.Given, "same")
    }

    @Test
    fun `a Given Password uses multiline insert`() {
        val editor = editor("old", SecretDraft.Password(PasswordDraft("typed")))
        assertPlan(editor.plan, "pass insert -m -f dev/github.com", PasswordSource.Given, "typed")
    }

    @Test
    fun `a card number keeps its spaces when only the notes change`() {
        val editor = editor("4111 1111 1111 1111", SecretDraft.CardNumber("4111111111111111"), "cards/visa")
        assertPlan(editor.copy(notes = "cvv: 123").plan, "pass insert -m -f cards/visa", PasswordSource.Given, "4111 1111 1111 1111")
    }

    @Test
    fun `a changed card number saves as digits`() {
        val editor = editor("4111 1111 1111 1111", SecretDraft.CardNumber("4111111111111112"), "cards/visa")
        assertPlan(editor.plan, "pass insert -m -f cards/visa", PasswordSource.Given, "4111111111111112")
    }

    @Test
    fun `editing notes on an OTP-only Entry keeps the secret empty`() {
        val editor = editor("otpauth://totp/test?secret=JBSWY3DPEHPK3PXP", SecretDraft.Password(PasswordDraft("")))
        assertPlan(editor.copy(notes = "user: alice").plan, "pass insert -m -f dev/github.com", PasswordSource.Given, "")
    }

    private fun editor(
        plaintext: String,
        secret: SecretDraft,
        path: String = "dev/github.com",
    ): EntryEditorState {
        val entry = EditableEntry(plaintext)
        return EntryEditorState(path, EntryEditSource(entry, byteArrayOf()), secret, entry.notes)
    }
}
