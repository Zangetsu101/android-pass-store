// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.gitsync.PasswordSource
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.gitsync.repositoryDirectory
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.passstore.LoginEntryWriter
import com.zangetsu101.pass.passstore.LoginSaveError
import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
internal class NewEntryViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val writer: LoginEntryWriter,
        passStore: PassStore,
        private val preferences: AppPreferences,
    ) : ViewModel() {
        val entries = passStore.index
        val folders = passStore.folders
        private val random = SecureRandom()
        var generator by mutableStateOf(GeneratorSettings())
            private set
        var password by mutableStateOf(generator.generate(random))
            private set

        /** True after the user types in the password field. A regenerate makes it false again. */
        var typedPassword by mutableStateOf(false)
            private set

        /** True after any change to the generated password, or after a copy of it. */
        var passwordTouched by mutableStateOf(false)
            private set
        var copyAfterSave by mutableStateOf(true)
            private set
        var notes by mutableStateOf("")
            private set
        var saving by mutableStateOf(false)
            private set
        var error by mutableStateOf<String?>(null)
            private set
        var savedPath by mutableStateOf<String?>(null)
            private set

        fun updatePassword(value: String) {
            password = value.filterNot(Char::isWhitespace)
            typedPassword = true
            passwordTouched = true
            error = null
        }

        fun regenerate() {
            password = generator.generate(random)
            typedPassword = false
            passwordTouched = true
            error = null
        }

        fun updateGenerator(settings: GeneratorSettings) {
            generator = settings
            regenerate()
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
            passwordTouched = true
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
            if (saving || savedPath != null || path.error != null || password.isEmpty()) return
            saving = true
            error = null
            viewModelScope.launch {
                try {
                    val source = if (typedPassword) PasswordSource.Given else PasswordSource.Generated
                    writer.insert(path, expectedDigest, password, notes, source)
                    if (copyAfterSave) copySecret(password)
                    password = ""
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
