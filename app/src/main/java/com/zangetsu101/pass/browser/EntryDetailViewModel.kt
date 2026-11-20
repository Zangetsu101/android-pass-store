// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.decryption.Credentials
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.decryption.LoginEditSource
import com.zangetsu101.pass.gitsync.FileCommitInfo
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.keymanagement.session.BiometricAuthException
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
import com.zangetsu101.pass.otp.Otp
import com.zangetsu101.pass.passstore.LoginEntryWriter
import com.zangetsu101.pass.passstore.LoginSaveError
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.preferences.AppPreferences
import com.zangetsu101.pass.util.markSensitive
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

sealed class UnlockState {
    data object Idle : UnlockState()

    data object Decrypting : UnlockState()

    data class Decrypted(
        val credentials: Credentials,
        val passwordRevealed: Boolean = false,
        val clipboardCopied: Boolean = false,
        val otpCopied: Boolean = false,
    ) : UnlockState()

    data class Failed(
        val message: String,
    ) : UnlockState()
}

sealed class GitStatus {
    data object Loading : GitStatus()

    data object Untracked : GitStatus()

    data class Tracked(
        val commitInfo: FileCommitInfo,
    ) : GitStatus()
}

data class EntryDetailUiState(
    val entry: PassEntry,
    val unlockState: UnlockState = UnlockState.Idle,
    val gitStatus: GitStatus = GitStatus.Loading,
    val clipboardTimeoutSeconds: Int = 45,
    val editor: LoginEditorState? = null,
    val showingSavedLogin: Boolean = false,
)

@ConsistentCopyVisibility
data class LoginEditorState internal constructor(
    internal val source: LoginEditSource,
    val password: String,
    val notes: String,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val changed: Boolean get() = password != source.login.password || notes != source.login.notes
    val hasOtp: Boolean get() = source.login.hasOtp

    override fun toString(): String = "LoginEditorState"
}

@HiltViewModel(assistedFactory = EntryDetailViewModel.Factory::class)
class EntryDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val entry: PassEntry,
        @ApplicationContext private val context: Context,
        private val decryption: Decryption,
        private val sessionOperations: SessionOperations,
        private val gitSync: GitSync,
        private val appPreferences: AppPreferences,
        private val loginWriter: LoginEntryWriter,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(entry: PassEntry): EntryDetailViewModel
        }

        private val _state =
            MutableStateFlow(
                EntryDetailUiState(entry),
            )
        val state: StateFlow<EntryDetailUiState> = _state.asStateFlow()

        private var clipClearJob: Job? = null
        private var otpHintJob: Job? = null
        private var autoLockJob: Job? = null
        private var decryptJob: Job? = null
        private var lockDeadline: Long? = null
        private var initialAuthenticationStarted = false

        fun onScreenEntered(activity: FragmentActivity) {
            refreshLock()
            if (initialAuthenticationStarted) return
            initialAuthenticationStarted = true
            authenticate(activity)
        }

        fun onScreenDisposed(changingConfigurations: Boolean) {
            if (!changingConfigurations) lock()
        }

        fun lock() {
            decryptJob?.cancel()
            otpHintJob?.cancel()
            autoLockJob?.cancel()
            lockDeadline = null
            _state.update { it.copy(unlockState = UnlockState.Idle, showingSavedLogin = false) }
        }

        // Read-only guard for collected snapshots; never refresh or extend the deadline here.
        fun hasUnexpiredDetails(): Boolean {
            val deadline = lockDeadline ?: return false
            return SystemClock.elapsedRealtime() < deadline
        }

        fun refreshLock() {
            val deadline = lockDeadline ?: return
            if (SystemClock.elapsedRealtime() >= deadline) lock()
        }

        init {
            viewModelScope.launch {
                gitSync.lastCommitForFile(entry.path).let { info ->
                    val gitStatus = if (info != null) GitStatus.Tracked(info) else GitStatus.Untracked
                    _state.update { it.copy(gitStatus = gitStatus) }
                }
            }
            viewModelScope.launch {
                appPreferences.clipboardTimeoutSeconds.collect { seconds ->
                    _state.update { it.copy(clipboardTimeoutSeconds = seconds) }
                }
            }
            viewModelScope.launch {
                sessionOperations.sessionState.collect { sessionState ->
                    if (sessionState is SessionState.Inactive &&
                        _state.value.unlockState is UnlockState.Decrypted &&
                        !(_state.value.showingSavedLogin && sessionState.reason == EndReason.TIMEOUT)
                    ) {
                        lock()
                        _state.update {
                            it.copy(unlockState = UnlockState.Failed("Session ended — unlock again"))
                        }
                    }
                }
            }
        }

        fun authenticate(activity: FragmentActivity) {
            if (_state.value.unlockState !is UnlockState.Idle) return
            val entry = _state.value.entry
            _state.update { it.copy(unlockState = UnlockState.Decrypting) }
            decryptJob =
                viewModelScope.launch {
                    try {
                        val creds = decryption.decrypt(entry, activity)
                        coroutineContext.ensureActive()
                        showCredentials(creds)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: BiometricAuthException) {
                        _state.update { it.copy(unlockState = UnlockState.Idle, showingSavedLogin = false) }
                    } catch (e: SessionError.NoActiveSession) {
                        _state.update { it.copy(unlockState = UnlockState.Failed("Session expired — return to home and unlock")) }
                    } catch (e: DecryptionError) {
                        _state.update { it.copy(unlockState = UnlockState.Failed(e.message ?: "Decryption failed")) }
                    } catch (e: Exception) {
                        _state.update { it.copy(unlockState = UnlockState.Failed(e.message ?: "Decryption failed")) }
                    }
                }
        }

        fun startEditing() {
            refreshLock()
            val current = _state.value
            if (current.entry.isCard || current.editor != null || sessionOperations.sessionState.value !is SessionState.Active) return
            val source = (current.unlockState as? UnlockState.Decrypted)?.credentials?.editSource ?: return
            _state.update { it.copy(editor = LoginEditorState(source, source.login.password, source.login.notes)) }
        }

        fun updateEditorPassword(password: String) {
            updateEditor { it.copy(password = password.filterNot(Char::isWhitespace), error = null) }
        }

        fun updateEditorNotes(notes: String) {
            updateEditor { it.copy(notes = notes, error = null) }
        }

        private fun updateEditor(update: (LoginEditorState) -> LoginEditorState) {
            _state.update { state ->
                val editor = state.editor ?: return@update state
                if (editor.saving) state else state.copy(editor = update(editor))
            }
        }

        fun discardEditor() {
            _state.update { if (it.editor?.saving == false) it.copy(editor = null) else it }
        }

        fun copyEditorPassword() {
            val password =
                _state.value.editor
                    ?.password
                    .orEmpty()
            if (password.isNotEmpty()) copySecret("password", password)
        }

        fun saveEditor() {
            val editor = _state.value.editor ?: return
            if (editor.saving || !editor.changed) return
            val password = editor.password
            if (password.isEmpty() &&
                editor.source.login.password
                    .isNotEmpty()
            ) {
                updateEditor { it.copy(error = "password cannot be empty") }
                return
            }
            updateEditor { it.copy(saving = true, error = null) }
            viewModelScope.launch {
                try {
                    val saved = loginWriter.save(entry, editor.source, password, editor.notes)
                    showCredentials(saved.credentials, saved = saved.commit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val message =
                        when (error) {
                            is LoginSaveError, is SyncError.EditRejected -> error.message
                            else -> "couldn't save entry"
                        }
                    _state.update { it.copy(editor = editor.copy(error = message)) }
                }
            }
        }

        private fun showCredentials(
            credentials: Credentials,
            saved: FileCommitInfo? = null,
        ) {
            val session = sessionOperations.sessionState.value
            val savedAfterTimeout = saved != null && session is SessionState.Inactive && session.reason == EndReason.TIMEOUT
            if (saved != null && session is SessionState.Inactive && !savedAfterTimeout) {
                lock()
                _state.update { it.copy(editor = null, gitStatus = GitStatus.Tracked(saved)) }
                return
            }
            autoLockJob?.cancel()
            lockDeadline = SystemClock.elapsedRealtime() + 60_000L
            _state.update {
                it.copy(
                    unlockState = UnlockState.Decrypted(credentials),
                    gitStatus = saved?.let(GitStatus::Tracked) ?: it.gitStatus,
                    editor = if (saved != null) null else it.editor,
                    showingSavedLogin = savedAfterTimeout,
                )
            }
            autoLockJob =
                viewModelScope.launch {
                    delay(60_000L)
                    refreshLock()
                }
        }

        fun toggleReveal() {
            refreshLock()
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            _state.update { it.copy(unlockState = current.copy(passwordRevealed = !current.passwordRevealed)) }
        }

        fun copyPassword() {
            refreshLock()
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            if (current.credentials.password.isEmpty()) return
            val password = String(current.credentials.password)
            copySecret("password", password)
            _state.update { it.copy(unlockState = current.copy(clipboardCopied = true)) }
        }

        fun copyOtp() {
            refreshLock()
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            val otp = current.credentials.otp as? Otp.Totp ?: return
            copySecret("otp", otp.code(System.currentTimeMillis() / 1000))
            _state.update { it.copy(unlockState = current.copy(otpCopied = true)) }
            otpHintJob?.cancel()
            otpHintJob =
                viewModelScope.launch {
                    delay(1500)
                    _state.update { state ->
                        val unlocked = state.unlockState as? UnlockState.Decrypted ?: return@update state
                        if (unlocked.credentials !== current.credentials) return@update state
                        state.copy(unlockState = unlocked.copy(otpCopied = false))
                    }
                }
        }

        private fun copySecret(
            label: String,
            value: String,
        ) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value).markSensitive())
            val timeoutMillis = _state.value.clipboardTimeoutSeconds * 1000L
            ClipboardClearReceiver.cancel(context)
            ClipboardClearReceiver.schedule(context, timeoutMillis)
            clipClearJob?.cancel()
            clipClearJob =
                viewModelScope.launch {
                    delay(timeMillis = timeoutMillis)
                    val decrypted = _state.value.unlockState as? UnlockState.Decrypted ?: return@launch
                    _state.update { it.copy(unlockState = decrypted.copy(clipboardCopied = false)) }
                }
        }
    }
