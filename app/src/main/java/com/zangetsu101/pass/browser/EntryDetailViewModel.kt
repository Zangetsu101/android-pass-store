// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.decryption.Credentials
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.keymanagement.session.BiometricAuthException
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

sealed class UnlockState {
    data object Idle : UnlockState()

    data object Decrypting : UnlockState()

    data class Decrypted(
        val credentials: Credentials,
        val passwordRevealed: Boolean = false,
        val clipboardCopied: Boolean = false,
    ) : UnlockState()

    data class Failed(
        val message: String,
    ) : UnlockState()
}

sealed class GitStatus {
    data object Loading : GitStatus()

    data object Untracked : GitStatus()

    data class Tracked(
        val commitInfo: com.zangetsu101.pass.gitsync.FileCommitInfo,
    ) : GitStatus()
}

data class EntryDetailUiState(
    val entry: PassEntry,
    val unlockState: UnlockState = UnlockState.Idle,
    val gitStatus: GitStatus = GitStatus.Loading,
    val clipboardTimeoutSeconds: Int = 45,
)

@HiltViewModel(assistedFactory = EntryDetailViewModel.Factory::class)
class EntryDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val entry: PassEntry,
        @ApplicationContext private val context: Context,
        private val decryption: Decryption,
        private val gitSync: GitSync,
        private val appPreferences: AppPreferences,
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
        }

        fun authenticate(activity: FragmentActivity) {
            if (_state.value.unlockState !is UnlockState.Idle) return
            val entry = _state.value.entry
            _state.update { it.copy(unlockState = UnlockState.Decrypting) }
            viewModelScope.launch {
                try {
                    val creds = decryption.decrypt(entry, activity)
                    _state.update { it.copy(unlockState = UnlockState.Decrypted(creds)) }
                } catch (e: BiometricAuthException) {
                    _state.update { it.copy(unlockState = UnlockState.Idle) }
                } catch (e: SessionError.NoActiveSession) {
                    _state.update { it.copy(unlockState = UnlockState.Failed("Session expired — return to home and unlock")) }
                } catch (e: DecryptionError) {
                    _state.update { it.copy(unlockState = UnlockState.Failed(e.message ?: "Decryption failed")) }
                } catch (e: Exception) {
                    _state.update { it.copy(unlockState = UnlockState.Failed(e.message ?: "Decryption failed")) }
                }
            }
        }

        fun toggleReveal() {
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            _state.update { it.copy(unlockState = current.copy(passwordRevealed = !current.passwordRevealed)) }
        }

        fun copyPassword() {
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            val password = String(current.credentials.password)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("password", password))
            _state.update { it.copy(unlockState = current.copy(clipboardCopied = true)) }
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

        override fun onCleared() {
            super.onCleared()
            (_state.value.unlockState as? UnlockState.Decrypted)?.credentials?.zero()
        }
    }