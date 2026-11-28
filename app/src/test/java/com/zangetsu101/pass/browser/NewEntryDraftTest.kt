// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.generator.CharacterSet
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.generator.PasswordDraft
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.passstore.PasswordSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class NewEntryDraftTest {
    private val path = EntryPath("dev/github.com")
    private val draft = NewEntryDraft("", PasswordDraft("generated", generated = true))

    @Test
    fun `a new Generated Password uses pass generate and copies after the save`() {
        assertPlan(draft.plan(path, exists = false), "pass generate -c dev/github.com", PasswordSource.Generated, "generated")
    }

    @Test
    fun `a Generated Password with notes keeps the generation command`() {
        val settings = GeneratorSettings().withLength(32).toggle(CharacterSet.Symbols)
        val replacement =
            draft
                .updatePassword { PasswordDraft("generated", settings, generated = true) }
                .updateNotes("user: alice")
                .toggleCopyAfterSave()
        assertPlan(replacement.plan(path, exists = true), "pass generate -n -f dev/github.com 32", PasswordSource.Generated, "generated")
    }

    @Test
    fun `a Given Password without notes uses single line insert`() {
        val typed = draft.updatePassword { it.type("typed") }
        assertPlan(typed.plan(path, exists = false), "pass insert dev/github.com", PasswordSource.Given, "typed")
    }

    @Test
    fun `a Given Password with notes replaces an existing entry with multiline insert`() {
        val typed = draft.updatePassword { it.type("typed") }.updateNotes("user: alice")
        assertPlan(typed.plan(path, exists = true), "pass insert -m -f dev/github.com", PasswordSource.Given, "typed")
    }

    @Test
    fun `a card uses multiline insert after a switch from a login with a Generated Password`() {
        val cardPath = EntryPath("cards/visa")
        val card = draft.updatePath(cardPath).updateCardNumber("4111 1111-1111 1111")
        assertPlan(card.plan(cardPath, exists = false), "pass insert -m cards/visa", PasswordSource.Given, "4111111111111111")
        assertPlan(card.plan(cardPath, exists = true), "pass insert -m -f cards/visa", PasswordSource.Given, "4111111111111111")
        val login = card.selectType(EntryType.Login)
        assertPlan(login.plan(path, exists = false), "pass generate -c dev/github.com", PasswordSource.Generated, "generated")
        assertEquals(card.cardNumber, login.selectType(EntryType.Card).secret)
    }

    @Test
    fun `the draft changes when the password differs from the first password`() {
        assertFalse(draft.toggleCopyAfterSave().changed(""))
        val settings = GeneratorSettings().withLength(28)
        val password = draft.passwordDraft.copy(settings = settings)
        val typed = draft.updatePassword { password.type("typed") }
        assertFalse(typed.passwordDraft.generated)
        assertTrue(typed.changed(""))
        assertFalse(typed.updatePassword { it.type("generated") }.changed(""))

        val generated = typed.updatePassword { it.regenerate(Random(1)) }
        assertTrue(generated.changed(""))
        assertTrue(generated.passwordDraft.generated)
        assertEquals(28, generated.secret.length)
    }

    @Test
    fun `a change in the settings makes a new password`() {
        val settings =
            draft.passwordDraft.settings
                .withLength(32)
                .toggle(CharacterSet.Symbols)
        val updated = draft.updatePassword { it.withSettings(settings, Random(1)) }
        assertTrue(updated.passwordDraft.generated)
        assertTrue(updated.changed(""))
        assertEquals(32, updated.secret.length)
        assertTrue(updated.secret.all { it.isLetterOrDigit() })
    }

    @Test
    fun `the initial path in a Card Folder starts a Card Entry`() {
        assertEquals(EntryType.Login, NewEntryDraft("dev/", draft.passwordDraft).type)
        assertEquals(EntryType.Card, NewEntryDraft("finance/credit-cards/", draft.passwordDraft).type)
    }

    @Test
    fun `entering a Card Folder selects card and leaving it keeps card selected`() {
        val login = draft.updatePath(EntryPath("finance/cards"))
        assertEquals(EntryType.Login, login.type)
        val card = login.updatePath(EntryPath("finance/cards/"))
        assertEquals(EntryType.Card, card.type)
        val moved = card.updatePath(path)
        assertEquals(EntryType.Card, moved.type)
        val returned = moved.selectType(EntryType.Login).updatePath(EntryPath("credit-cards/visa"))
        assertEquals(EntryType.Card, returned.type)
        val switched = returned.selectType(EntryType.Login).updatePath(EntryPath("credit-cards/visa-gold"))
        assertEquals(EntryType.Login, switched.type)
    }

    @Test
    fun `the warning shows only for a card outside a Card Folder`() {
        assertFalse(draft.cardFolderWarning(path))
        val card = draft.selectType(EntryType.Card)
        assertTrue(card.cardFolderWarning(path))
        assertFalse(card.cardFolderWarning(EntryPath("finance/cards/visa")))
        assertFalse(card.cardFolderWarning(EntryPath("")))
    }

    @Test
    fun `a card opens the notes and a login keeps them closed`() {
        assertFalse(NewEntryDraft("dev/", draft.passwordDraft).notesOpen)
        assertTrue(NewEntryDraft("cards/", draft.passwordDraft).notesOpen)
        assertTrue(draft.selectType(EntryType.Card).notesOpen)
        assertTrue(draft.updatePath(EntryPath("cards/visa")).notesOpen)
    }

    @Test
    fun `a login created from autofill stays a login and starts with copy after save off`() {
        val cardPath = EntryPath("cards/visa")
        val login =
            NewEntryDraft("github.com/", draft.passwordDraft, origin = NewEntryOrigin.Autofill(EntryType.Login))
                .updatePath(cardPath)
        assertEquals(EntryType.Login, login.type)
        assertFalse(login.canSave(cardPath))
        assertFalse(login.copyAfterSave)
    }

    @Test
    fun `a card created from autofill requires a Card Folder`() {
        val cardPath = EntryPath("cards/visa")
        val card =
            NewEntryDraft("cards/", draft.passwordDraft, origin = NewEntryOrigin.Autofill(EntryType.Card))
                .updateCardNumber("4111111111111111")
        assertTrue(card.canSave(cardPath))
        assertFalse(card.updatePath(path).canSave(path))
    }

    @Test
    fun `a cvv-only form needs a security code before the entry can be saved`() {
        val cardPath = EntryPath("cards/visa")
        val card =
            NewEntryDraft("cards/", draft.passwordDraft, origin = NewEntryOrigin.Autofill(EntryType.Card, requiresSecurityCode = true))
                .updateCardNumber("4111111111111111")
        assertFalse(card.canSave(cardPath))
        assertTrue(card.updateNotes("CVV: 123").canSave(cardPath))
        assertFalse(card.updateNotes("CVV: 123\ncvv: ").canSave(cardPath))
    }

    @Test
    fun `autofill cannot save over an existing entry`() {
        val login = NewEntryDraft("github.com/", draft.passwordDraft, origin = NewEntryOrigin.Autofill(EntryType.Login))
        assertFalse(login.canSave(path, exists = true))
        assertTrue(draft.canSave(path, exists = true))
    }

    @Test
    fun `a switch back to login keeps the notes open and the user can close them`() {
        val login = draft.selectType(EntryType.Card).selectType(EntryType.Login)
        assertTrue(login.notesOpen)
        assertFalse(login.toggleNotes().notesOpen)
    }

    @Test
    fun `switching to card keeps the notes and requires a card number`() {
        val cardPath = EntryPath("cards/visa")
        val card = draft.updateNotes("cvv: 123").updatePath(cardPath)
        assertEquals("cvv: 123", card.notes)
        assertFalse(card.canSave(cardPath))
        assertTrue(card.updateCardNumber("4111111111111112").canSave(cardPath))
    }
}
