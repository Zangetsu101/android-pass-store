package com.example.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.decryption.Credentials
import com.example.pass.decryption.Decryption
import com.example.pass.decryption.DecryptionError
import com.example.pass.gitsync.GitSync
import com.example.pass.keymanagement.BiometricAuthException
import com.example.pass.keymanagement.CryptoOperations
import com.example.pass.keymanagement.SessionError
import com.example.pass.passstore.PassEntry
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

    sealed class Authenticating : UnlockState() {
        data object Biometric : Authenticating()
    }

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
        val commitInfo: com.example.pass.gitsync.FileCommitInfo,
    ) : GitStatus()
}

data class EntryDetailUiState(
    val entry: PassEntry,
    val unlockState: UnlockState = UnlockState.Idle,
    val gitStatus: GitStatus = GitStatus.Loading,
)

@HiltViewModel(assistedFactory = EntryDetailViewModel.Factory::class)
class EntryDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val entry: PassEntry,
        @ApplicationContext private val context: Context,
        private val decryption: Decryption,
        private val gitSync: GitSync,
        private val cryptoOperations: CryptoOperations,
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

        private var blurJob: Job? = null
        private var clipClearJob: Job? = null

        init {
            viewModelScope.launch {
                val info = gitSync.lastCommitForFile(entry.path)
                val gitStatus = if (info != null) GitStatus.Tracked(info) else GitStatus.Untracked
                _state.update { it.copy(gitStatus = gitStatus) }
            }
        }

        fun authenticate(activity: FragmentActivity) {
            if (_state.value.unlockState !is UnlockState.Idle) return
            val entry = _state.value.entry
            _state.update { it.copy(unlockState = UnlockState.Authenticating.Biometric) }
            viewModelScope.launch {
                try {
                    val key = cryptoOperations.getGpgKey(activity)
                    _state.update { it.copy(unlockState = UnlockState.Decrypting) }
                    val creds = decryption.decryptWithKey(entry, key)
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
            val revealing = !current.passwordRevealed
            _state.update { it.copy(unlockState = current.copy(passwordRevealed = revealing)) }
            blurJob?.cancel()
            if (revealing) {
                blurJob =
                    viewModelScope.launch {
                        delay(45_000.milliseconds)
                        val decrypted = _state.value.unlockState as? UnlockState.Decrypted ?: return@launch
                        _state.update { it.copy(unlockState = decrypted.copy(passwordRevealed = false)) }
                    }
            }
        }

        fun copyPassword() {
            val current = _state.value.unlockState as? UnlockState.Decrypted ?: return
            val password = String(current.credentials.password)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("password", password))
            _state.update { it.copy(unlockState = current.copy(clipboardCopied = true)) }
            clipClearJob?.cancel()
            clipClearJob =
                viewModelScope.launch {
                    delay(45_000.milliseconds)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    val decrypted = _state.value.unlockState as? UnlockState.Decrypted ?: return@launch
                    _state.update { it.copy(unlockState = decrypted.copy(clipboardCopied = false)) }
                }
        }

        override fun onCleared() {
            super.onCleared()
            (_state.value.unlockState as? UnlockState.Decrypted)?.credentials?.zero()
        }
    }
