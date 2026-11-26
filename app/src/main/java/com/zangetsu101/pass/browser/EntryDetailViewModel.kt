// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.Context
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.decryption.Credentials
import com.zangetsu101.pass.decryption.DecryptedEntry
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.decryption.EditableEntry
import com.zangetsu101.pass.decryption.EntryEditSource
import com.zangetsu101.pass.decryption.EntryNotes
import com.zangetsu101.pass.decryption.RawEntry
import com.zangetsu101.pass.decryption.parseEntryNotes
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.generator.PasswordDraft
import com.zangetsu101.pass.gitsync.FileCommitInfo
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.keymanagement.session.BiometricAuthException
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
import com.zangetsu101.pass.otp.Otp
import com.zangetsu101.pass.passstore.EntrySaveError
import com.zangetsu101.pass.passstore.EntryWriter
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.passstore.PasswordSource
import com.zangetsu101.pass.preferences.AppPreferences
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
import java.security.SecureRandom
import kotlin.coroutines.coroutineContext

sealed class UnlockState {
    data object Idle : UnlockState()

    data object Decrypting : UnlockState()

    data class Decrypted(
        val credentials: Credentials,
        val secretRevealed: Boolean = false,
        val copied: Copied? = null,
        val canEdit: Boolean = false,
        val revealedFields: Set<Int> = emptySet(),
    ) : UnlockState() {
        val notes: EntryNotes by lazy { parseEntryNotes(credentials.notes) }
    }

    data class Failed(
        val message: String,
    ) : UnlockState()
}

/** The name in the entry detail header. After decryption, the Username Line wins over the file name. */
fun UnlockState.headerName(entry: PassEntry): String = (this as? UnlockState.Decrypted)?.credentials?.username ?: entry.username

/** The secret that the user copied last from entry detail. Entry detail marks only this one as copied. */
sealed interface Copied {
    data object Secret : Copied

    data object OtpCode : Copied

    data class Field(
        val index: Int,
    ) : Copied
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
    val mode: EntryDetailMode = EntryDetailMode.ViewEntry,
    val showingSavedEntry: Boolean = false,
)

sealed interface EntryDetailMode {
    data object ViewEntry : EntryDetailMode

    data class EditEntry(
        val draft: EntryEditorState,
    ) : EntryDetailMode
}

@ConsistentCopyVisibility
data class EntryEditorState internal constructor(
    internal val source: EntryEditSource,
    internal val secretDraft: SecretDraft,
    val notes: String,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val secret: String get() = secretDraft.secret
    internal val type
        get() =
            when (secretDraft) {
                is SecretDraft.Password -> EntryType.Login
                is SecretDraft.CardNumber -> EntryType.Card
            }
    private val originalSecret get() = source.entry.secret
    val secretChanged: Boolean get() = secret != if (type == EntryType.Card) cardNumberDigits(originalSecret) else originalSecret
    val notesChanged: Boolean get() = notes != source.entry.notes

    /** The secret that a save writes. An unchanged secret keeps its original form, such as a card number with spaces. */
    internal val secretToSave: String get() = if (secretChanged) secret else originalSecret

    /** `pass generate -i` changes only line 1, so a save that also changes the notes is a `pass edit`. */
    val passwordSource: PasswordSource
        get() {
            val generated = (secretDraft as? SecretDraft.Password)?.draft?.generated == true
            return if (generated && secretChanged && !notesChanged) PasswordSource.Generated else PasswordSource.Given
        }
    val changed: Boolean get() = secretChanged || notesChanged
    val hasOtp: Boolean get() = source.entry.hasOtp

    override fun toString(): String = "EntryEditorState"
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
        private val entryWriter: EntryWriter,
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

        private val random = SecureRandom()
        private var clipClearJob: Job? = null
        private var otpHintJob: Job? = null
        private var autoLockJob: Job? = null
        private var decryptJob: Job? = null
        private var rawEntry: RawEntry? = null
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
            rawEntry = null
            _state.update { it.copy(unlockState = UnlockState.Idle, showingSavedEntry = false) }
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
                        !(_state.value.showingSavedEntry && sessionState.reason == EndReason.TIMEOUT)
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
                        val decrypted = decryption.decryptForEntryDetail(entry, activity)
                        coroutineContext.ensureActive()
                        showCredentials(decrypted)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: BiometricAuthException) {
                        _state.update { it.copy(unlockState = UnlockState.Idle, showingSavedEntry = false) }
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
            if (current.mode != EntryDetailMode.ViewEntry || sessionOperations.sessionState.value !is SessionState.Active) return
            if (current.unlockState !is UnlockState.Decrypted) return
            val original = rawEntry ?: return
            val editable = EditableEntry(original.plaintext)
            val source = EntryEditSource(editable, original.ciphertextDigest)
            val secret =
                if (current.entry.type == EntryType.Card) {
                    SecretDraft.CardNumber(cardNumberDigits(editable.secret))
                } else {
                    SecretDraft.Password(PasswordDraft(editable.secret))
                }
            _state.update { it.copy(mode = EntryDetailMode.EditEntry(EntryEditorState(source, secret, editable.notes))) }
        }

        fun updateEditorPassword(password: String) = updateEditorPasswordDraft { it.type(password) }

        fun updateEditorCardNumber(number: String) {
            updateEditor { editor ->
                if (editor.secretDraft !is SecretDraft.CardNumber) return@updateEditor editor
                editor.copy(secretDraft = SecretDraft.CardNumber(cardNumberDigits(number)), error = null)
            }
        }

        fun regenerateEditorPassword() = updateEditorPasswordDraft { it.regenerate(random) }

        internal fun updateEditorGenerator(settings: GeneratorSettings) = updateEditorPasswordDraft { it.withSettings(settings, random) }

        private fun updateEditorPasswordDraft(update: (PasswordDraft) -> PasswordDraft) {
            updateEditor { editor ->
                val secret = editor.secretDraft as? SecretDraft.Password ?: return@updateEditor editor
                editor.copy(secretDraft = SecretDraft.Password(update(secret.draft)), error = null)
            }
        }

        fun updateEditorNotes(notes: String) {
            updateEditor { it.copy(notes = notes, error = null) }
        }

        private fun updateEditor(update: (EntryEditorState) -> EntryEditorState) {
            _state.update { state ->
                val editor = (state.mode as? EntryDetailMode.EditEntry)?.draft ?: return@update state
                if (editor.saving) state else state.copy(mode = EntryDetailMode.EditEntry(update(editor)))
            }
        }

        fun discardEditor() {
            _state.update { state ->
                val mode = state.mode
                if (mode is EntryDetailMode.EditEntry && !mode.draft.saving) state.copy(mode = EntryDetailMode.ViewEntry) else state
            }
        }

        fun copyEditorPassword() {
            val secret =
                (_state.value.mode as? EntryDetailMode.EditEntry)
                    ?.draft
                    ?.secret
                    .orEmpty()
            if (secret.isNotEmpty()) copyToClipboard("password", secret)
        }

        fun saveEditor() {
            val editor = (_state.value.mode as? EntryDetailMode.EditEntry)?.draft ?: return
            if (editor.saving || !editor.changed) return
            val secret = editor.secretToSave
            if (secret.isEmpty() &&
                editor.source.entry.secret
                    .isNotEmpty()
            ) {
                updateEditor { it.copy(error = "${editor.type.secretLabel} cannot be empty") }
                return
            }
            updateEditor { it.copy(saving = true, error = null) }
            viewModelScope.launch {
                try {
                    val saved = entryWriter.save(entry, editor.source, secret, editor.notes, editor.passwordSource)
                    showCredentials(saved.entry, saved = saved.commit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val message =
                        when (error) {
                            is EntrySaveError, is SyncError.EditRejected -> error.message
                            else -> "couldn't save entry"
                        }
                    _state.update { it.copy(mode = EntryDetailMode.EditEntry(editor.copy(error = message))) }
                }
            }
        }

        private fun showCredentials(
            decrypted: DecryptedEntry,
            saved: FileCommitInfo? = null,
        ) {
            val session = sessionOperations.sessionState.value
            val savedAfterTimeout = saved != null && session is SessionState.Inactive && session.reason == EndReason.TIMEOUT
            if (saved != null && session is SessionState.Inactive && !savedAfterTimeout) {
                lock()
                _state.update { it.copy(mode = EntryDetailMode.ViewEntry, gitStatus = GitStatus.Tracked(saved)) }
                return
            }
            autoLockJob?.cancel()
            rawEntry = if (savedAfterTimeout) null else decrypted.rawEntry
            lockDeadline = SystemClock.elapsedRealtime() + 60_000L
            _state.update {
                it.copy(
                    unlockState = UnlockState.Decrypted(decrypted.credentials, canEdit = rawEntry != null),
                    gitStatus = saved?.let(GitStatus::Tracked) ?: it.gitStatus,
                    mode = if (saved != null) EntryDetailMode.ViewEntry else it.mode,
                    showingSavedEntry = savedAfterTimeout,
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
            _state.update { it.copy(unlockState = current.copy(secretRevealed = !current.secretRevealed)) }
        }

        fun copySecret() {
            refreshLock()
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            if (current.credentials.secret.isEmpty()) return
            copyToClipboard(entry.type.secretLabel, String(current.credentials.secret))
            _state.update { it.copy(unlockState = current.copy(copied = Copied.Secret)) }
        }

        fun toggleFieldReveal(index: Int) {
            refreshLock()
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            val revealed = if (index in current.revealedFields) current.revealedFields - index else current.revealedFields + index
            _state.update { it.copy(unlockState = current.copy(revealedFields = revealed)) }
        }

        fun copyField(index: Int) {
            refreshLock()
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            val field = current.notes.fields.getOrNull(index) ?: return
            copyToClipboard(field.key, field.value)
            _state.update { it.copy(unlockState = current.copy(copied = Copied.Field(index))) }
        }

        fun copyOtp() {
            refreshLock()
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            val otp = current.credentials.otp as? Otp.Totp ?: return
            copyToClipboard("otp", otp.code(System.currentTimeMillis() / 1000))
            _state.update { it.copy(unlockState = current.copy(copied = Copied.OtpCode)) }
            otpHintJob?.cancel()
            otpHintJob =
                viewModelScope.launch {
                    delay(1500)
                    _state.update { state ->
                        val unlocked = state.unlockState as? UnlockState.Decrypted ?: return@update state
                        if (unlocked.credentials !== current.credentials || unlocked.copied != Copied.OtpCode) return@update state
                        state.copy(unlockState = unlocked.copy(copied = null))
                    }
                }
        }

        private fun copyToClipboard(
            label: String,
            value: String,
        ) {
            val timeoutMillis = _state.value.clipboardTimeoutSeconds * 1000L
            copySecretToClipboard(context, label, value, timeoutMillis)
            clipClearJob?.cancel()
            clipClearJob =
                viewModelScope.launch {
                    delay(timeMillis = timeoutMillis)
                    val decrypted = _state.value.unlockState as? UnlockState.Decrypted ?: return@launch
                    _state.update { it.copy(unlockState = decrypted.copy(copied = null)) }
                }
        }
    }
