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
import com.zangetsu101.pass.passstore.EntrySaveError
import com.zangetsu101.pass.passstore.EntryWriter
import com.zangetsu101.pass.passstore.PassStore
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

@HiltViewModel(assistedFactory = NewEntryViewModel.Factory::class)
internal class NewEntryViewModel
    @AssistedInject
    constructor(
        @Assisted initialPath: String,
        @ApplicationContext private val context: Context,
        private val writer: EntryWriter,
        passStore: PassStore,
        private val preferences: AppPreferences,
        @Assisted origin: NewEntryOrigin,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                initialPath: String,
                origin: NewEntryOrigin,
            ): NewEntryViewModel
        }

        val entries = passStore.index
        val folders = passStore.folders
        private val random = SecureRandom()

        var draft by mutableStateOf(NewEntryDraft(initialPath, PasswordDraft.generate(random), origin = origin))
            private set

        var saving by mutableStateOf(false)
            private set
        var error by mutableStateOf<String?>(null)
            private set
        var savedPath by mutableStateOf<String?>(null)
            private set

        fun selectType(value: EntryType) {
            draft = draft.selectType(value)
            error = null
        }

        fun updatePath(path: EntryPath) {
            draft = draft.updatePath(path)
            error = null
        }

        fun updateCardNumber(value: String) {
            draft = draft.updateCardNumber(value)
            error = null
        }

        fun updatePassword(value: String) {
            draft = draft.updatePassword { it.type(value) }
            error = null
        }

        fun regenerate() {
            draft = draft.updatePassword { it.regenerate(random) }
            error = null
        }

        fun updateGenerator(settings: GeneratorSettings) {
            draft = draft.updatePassword { it.withSettings(settings, random) }
            error = null
        }

        fun toggleNotes() {
            draft = draft.toggleNotes()
        }

        fun toggleCopyAfterSave() {
            draft = draft.toggleCopyAfterSave()
        }

        fun updateNotes(value: String) {
            draft = draft.updateNotes(value)
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
                    InsertPathCheck.Unavailable(if (failure is EntrySaveError) failure.message else "couldn't check the entry path")
                }
            }

        fun copyPassword() {
            copySecret(draft.passwordDraft.password)
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
            val current = draft
            if (saving || savedPath != null || !current.canSave(path, exists = expectedDigest != null)) return
            val plan = current.plan(path, exists = expectedDigest != null)
            saving = true
            error = null
            viewModelScope.launch {
                try {
                    writer.insert(path, expectedDigest, plan.secret, current.notes, plan.passwordSource)
                    if (current.type == EntryType.Login && current.copyAfterSave) copySecret(plan.secret)
                    draft = draft.cleared()
                    savedPath = path.value
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error =
                        when (failure) {
                            is EntrySaveError, is SyncError.EditRejected -> failure.message
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
