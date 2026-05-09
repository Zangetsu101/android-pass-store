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
import com.example.pass.gitsync.FileCommitInfo
import com.example.pass.gitsync.GitSync
import com.example.pass.keymanagement.BiometricAuthException
import com.example.pass.keymanagement.BiometricNotEnrolledException
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.keymanagement.SessionError
import com.example.pass.passstore.PassEntry
import com.example.pass.passstore.PassStore
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

sealed class UnlockState {
    data object Idle : UnlockState()

    sealed class Authenticating : UnlockState() {
        data object Biometric : Authenticating()

        data class Passphrase(
            val input: String = "",
            val error: String? = null,
        ) : Authenticating()
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

data class EntryDetailUiState(
    val entry: PassEntry? = null,
    val unlockState: UnlockState = UnlockState.Idle,
    val commitInfo: FileCommitInfo? = null,
    val metadataLoaded: Boolean = false,
)

@HiltViewModel(assistedFactory = EntryDetailViewModel.Factory::class)
class EntryDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val entryPath: String,
        @ApplicationContext private val context: Context,
        private val passStore: PassStore,
        private val decryption: Decryption,
        private val gitSync: GitSync,
        private val keyManagement: KeyManagement,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(entryPath: String): EntryDetailViewModel
        }

        private val _state = MutableStateFlow(EntryDetailUiState())
        val state: StateFlow<EntryDetailUiState> = _state.asStateFlow()

        private var blurJob: Job? = null
        private var clipClearJob: Job? = null

        init {
            val entry = passStore.index.value.find { it.path == entryPath }
            if (entry == null) {
                _state.update { it.copy(unlockState = UnlockState.Failed("Entry not found")) }
            } else {
                _state.update { it.copy(entry = entry) }
            }
        }

        fun authenticate(activity: FragmentActivity) {
            if (_state.value.unlockState !is UnlockState.Idle) return
            val entry = _state.value.entry ?: return
            _state.update { it.copy(unlockState = UnlockState.Authenticating.Biometric) }
            viewModelScope.launch {
                try {
                    val key = keyManagement.getGpgKey(activity)
                    _state.update { it.copy(unlockState = UnlockState.Decrypting) }
                    val creds = decryption.decryptWithKey(entry, key)
                    _state.update { it.copy(unlockState = UnlockState.Decrypted(creds)) }
                } catch (e: BiometricNotEnrolledException) {
                    _state.update { it.copy(unlockState = UnlockState.Authenticating.Passphrase()) }
                } catch (e: BiometricAuthException) {
                    _state.update { it.copy(unlockState = UnlockState.Idle) }
                } catch (e: DecryptionError) {
                    _state.update { it.copy(unlockState = UnlockState.Failed(e.message ?: "Decryption failed")) }
                } catch (e: Exception) {
                    _state.update { it.copy(unlockState = UnlockState.Failed(e.message ?: "Decryption failed")) }
                }
            }
        }

        fun dismissPassphrase() {
            _state.update { it.copy(unlockState = UnlockState.Idle) }
        }

        fun setPassphraseInput(value: String) {
            val current = _state.value.unlockState as? UnlockState.Authenticating.Passphrase ?: return
            _state.update { it.copy(unlockState = current.copy(input = value, error = null)) }
        }

        fun submitPassphrase(activity: FragmentActivity) {
            val entry = _state.value.entry ?: return
            val current = _state.value.unlockState as? UnlockState.Authenticating.Passphrase ?: return
            val passphrase = current.input.ifEmpty { return }
            _state.update { it.copy(unlockState = UnlockState.Decrypting) }
            viewModelScope.launch {
                try {
                    val key = withContext(Dispatchers.IO) { keyManagement.getGpgKeyWithPassphrase(passphrase) }
                    val creds = decryption.decryptWithKey(entry, key)
                    _state.update { it.copy(unlockState = UnlockState.Decrypted(creds)) }
                } catch (e: SessionError.WrongPassphrase) {
                    _state.update {
                        it.copy(
                            unlockState = UnlockState.Authenticating.Passphrase(input = passphrase, error = "Wrong passphrase"),
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            unlockState =
                                UnlockState.Authenticating.Passphrase(
                                    input = passphrase,
                                    error =
                                        e.message ?: "Failed",
                                ),
                        )
                    }
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
                        delay(45_000)
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
                    delay(45_000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    val decrypted = _state.value.unlockState as? UnlockState.Decrypted ?: return@launch
                    _state.update { it.copy(unlockState = decrypted.copy(clipboardCopied = false)) }
                }
        }

        fun loadMetadata() {
            viewModelScope.launch {
                val info = gitSync.lastCommitForFile(entryPath)
                _state.update { it.copy(commitInfo = info, metadataLoaded = true) }
            }
        }

        override fun onCleared() {
            super.onCleared()
            (_state.value.unlockState as? UnlockState.Decrypted)?.credentials?.zero()
        }
    }
