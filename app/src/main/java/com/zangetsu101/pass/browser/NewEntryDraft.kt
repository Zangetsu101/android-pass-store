// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.generator.PasswordDraft
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.passstore.PasswordSource

/**
 * The state of the new-entry form. It keeps the password and the card number as separate fields, so a switch of type keeps both.
 * Keep this value in memory only. It holds secrets, so it must not go into saved state (ADR 0002).
 */
internal data class NewEntryDraft(
    private val initialPath: String,
    val passwordDraft: PasswordDraft,
    private val firstPassword: String = passwordDraft.password,
    private val inCardFolder: Boolean = EntryPath(initialPath).inCardFolder,
    private val autofillType: EntryType? = null,
    val type: EntryType = autofillType ?: if (inCardFolder) EntryType.Card else EntryType.Login,
    val cardNumber: String = "",
    val notes: String = "",
    val notesOpen: Boolean = type == EntryType.Card,
    val copyAfterSave: Boolean = autofillType == null,
) {
    val secret get() = if (type == EntryType.Card) cardNumber else passwordDraft.password

    fun changed(input: String) =
        input != initialPath || passwordDraft.password != firstPassword || cardNumber.isNotEmpty() || notes.isNotEmpty()

    fun canSave(path: EntryPath) = pathError(path) == null && secret.isNotEmpty()

    fun selectType(value: EntryType) =
        if (autofillType != null) this else copy(type = value, notesOpen = notesOpen || value == EntryType.Card)

    /** Nothing switches the type back to login by itself. */
    fun updatePath(path: EntryPath): NewEntryDraft {
        val entered = path.inCardFolder && !inCardFolder
        val moved = copy(inCardFolder = path.inCardFolder)
        return if (entered) moved.selectType(EntryType.Card) else moved
    }

    fun pathError(path: EntryPath): String? =
        path.error ?: when {
            type == EntryType.Login && path.inCardFolder -> "choose a folder for login entries"
            autofillType == EntryType.Card && !path.inCardFolder -> "choose a card folder"
            else -> null
        }

    /** A card outside a Card Folder saves as a login, so the form warns about it. */
    fun cardFolderWarning(path: EntryPath) = type == EntryType.Card && !path.inCardFolder && path.value.isNotEmpty()

    fun updatePassword(update: (PasswordDraft) -> PasswordDraft) = copy(passwordDraft = update(passwordDraft))

    fun updateCardNumber(value: String) = copy(cardNumber = cardNumberDigits(value))

    fun updateNotes(value: String) = copy(notes = value)

    fun toggleNotes() = copy(notesOpen = !notesOpen)

    fun toggleCopyAfterSave() = copy(copyAfterSave = !copyAfterSave)

    /** After a save, the form keeps no secret. */
    fun cleared() = copy(passwordDraft = passwordDraft.type(""), cardNumber = "", notes = "")

    fun plan(
        path: EntryPath,
        exists: Boolean,
    ): SavePlan =
        if (type == EntryType.Login && passwordDraft.generated) {
            SavePlan(PassCommand.generate(path.value, passwordDraft.settings, copyAfterSave, exists), PasswordSource.Generated, secret)
        } else {
            SavePlan(PassCommand.insert(path.value, type == EntryType.Card || notes.isNotEmpty(), exists), PasswordSource.Given, secret)
        }

    override fun toString() = "NewEntryDraft"
}
