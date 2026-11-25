// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.generator.PasswordDraft
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.gitsync.repositoryDirectory
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.passstore.LoginEntryWriter
import com.zangetsu101.pass.passstore.LoginSaveError
import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.passstore.PasswordSource
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** The type of the entry in the new-entry form. The file never stores it: a Card Entry is an entry in a card folder. */
internal enum class EntryType(
    /** The name of the secret on line 1. */
    val line1: String,
) {
    Login("password"),
    Card("card number"),
}

@HiltViewModel(assistedFactory = NewEntryViewModel.Factory::class)
internal class NewEntryViewModel
    @AssistedInject
    constructor(
        @Assisted initialPath: String,
        @ApplicationContext private val context: Context,
        private val writer: LoginEntryWriter,
        passStore: PassStore,
        private val preferences: AppPreferences,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(initialPath: String): NewEntryViewModel
        }

        val entries = passStore.index
        val folders = passStore.folders
        private val random = SecureRandom()
        var passwordDraft by mutableStateOf(PasswordDraft.generate(random))
            private set
        val password get() = passwordDraft.password

        private val firstPassword = passwordDraft.password
        val passwordChanged get() = password != firstPassword
        var copyAfterSave by mutableStateOf(true)
            private set
        var notes by mutableStateOf("")
            private set

        private var inCardFolder = EntryPath(initialPath).inCardFolder
        var type by mutableStateOf(if (inCardFolder) EntryType.Card else EntryType.Login)
            private set

        // The form keeps the password and the card number, so a switch back to a type brings back its value.
        // Neither of them goes into saved state.
        var cardNumber by mutableStateOf("")
            private set
        var notesOpen by mutableStateOf(type == EntryType.Card)
            private set

        /** The secret that a save writes on line 1: the field of the type that the form shows. */
        val line1 get() = if (type == EntryType.Card) cardNumber else password
        var saving by mutableStateOf(false)
            private set
        var error by mutableStateOf<String?>(null)
            private set
        var savedPath by mutableStateOf<String?>(null)
            private set

        fun selectType(value: EntryType) {
            type = value
            if (value == EntryType.Card) notesOpen = true
            error = null
        }

        /** A path that enters a card folder switches the type to card. Nothing switches it back to login by itself. */
        fun updatePath(path: EntryPath) {
            if (path.inCardFolder && !inCardFolder) selectType(EntryType.Card)
            inCardFolder = path.inCardFolder
        }

        fun pathError(path: EntryPath): String? =
            path.error ?: "choose a folder for login entries".takeIf { type == EntryType.Login && path.inCardFolder }

        /** A card outside a card folder saves as a login, so the form warns about it. */
        fun cardFolderWarning(path: EntryPath) = type == EntryType.Card && !path.inCardFolder && path.value.isNotEmpty()

        fun updateCardNumber(value: String) {
            cardNumber = cardNumberDigits(value)
            error = null
        }

        fun toggleNotes() {
            notesOpen = !notesOpen
        }

        fun updatePassword(value: String) {
            passwordDraft = passwordDraft.type(value)
            error = null
        }

        fun regenerate() {
            passwordDraft = passwordDraft.regenerate(random)
            error = null
        }

        fun updateGenerator(settings: GeneratorSettings) {
            passwordDraft = passwordDraft.withSettings(settings, random)
            error = null
        }

        fun toggleCopyAfterSave() {
            copyAfterSave = !copyAfterSave
        }

        fun updateNotes(value: String) {
            notes = value
            error = null
        }

        fun clearError() {
            error = null
        }

        suspend fun checkPath(path: EntryPath): InsertPathCheck =
            withContext(Dispatchers.IO) {
                try {
                    val root = context.repositoryDirectory().canonicalFile
                    val digest = writer.checkInsertPath(root, path)
                    if (digest == null) InsertPathCheck.Available else InsertPathCheck.Existing(digest)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    InsertPathCheck.Unavailable(if (failure is LoginSaveError) failure.message else "couldn't check the entry path")
                }
            }

        fun copyPassword() {
            copySecret(password)
        }

        private fun copySecret(value: String) {
            if (value.isEmpty()) return
            viewModelScope.launch {
                val timeout = preferences.clipboardTimeoutSeconds.first() * 1000L
                copySecretToClipboard(context, "password", value, timeout)
            }
        }

        fun save(
            path: EntryPath,
            expectedDigest: ByteArray?,
        ) {
            val secret = line1
            if (saving || savedPath != null || pathError(path) != null || secret.isEmpty()) return
            saving = true
            error = null
            viewModelScope.launch {
                try {
                    val login = type == EntryType.Login
                    val source = if (login && passwordDraft.generated) PasswordSource.Generated else PasswordSource.Given
                    writer.insert(path, expectedDigest, secret, notes, source)
                    if (login && copyAfterSave) copySecret(secret)
                    passwordDraft = passwordDraft.type("")
                    cardNumber = ""
                    notes = ""
                    savedPath = path.value
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error =
                        when (failure) {
                            is LoginSaveError, is SyncError.EditRejected -> failure.message
                            else -> "couldn't save entry"
                        }
                } finally {
                    saving = false
                }
            }
        }
    }

internal sealed interface InsertPathCheck {
    data object Available : InsertPathCheck

    class Existing(
        val digest: ByteArray,
    ) : InsertPathCheck

    class Unavailable(
        val message: String,
    ) : InsertPathCheck
}
