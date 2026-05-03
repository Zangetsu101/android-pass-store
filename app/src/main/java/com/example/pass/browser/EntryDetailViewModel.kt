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
import com.example.pass.keymanagement.SessionError
import com.example.pass.passstore.PassEntry
import com.example.pass.passstore.PassStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EntryDetailUiState(
    val entry: PassEntry? = null,
    val decrypting: Boolean = false,
    val credentials: Credentials? = null,
    val decryptError: String? = null,
    val passwordRevealed: Boolean = false,
    val clipboardCopied: Boolean = false,
    val sessionStartNeeded: Boolean = false,
    val commitInfo: FileCommitInfo? = null,
    val metadataLoaded: Boolean = false,
)

@HiltViewModel
class EntryDetailViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val passStore: PassStore,
        private val decryption: Decryption,
        private val gitSync: GitSync,
    ) : ViewModel() {
        private val _state = MutableStateFlow(EntryDetailUiState())
        val state: StateFlow<EntryDetailUiState> = _state.asStateFlow()

        private var blurJob: Job? = null
        private var clipClearJob: Job? = null

        fun initForEntry(entryPath: String): PassEntry? {
            val entry = passStore.index.value.find { it.path == entryPath }
            _state.update { it.copy(entry = entry) }
            return entry
        }

        fun decrypt(
            entry: PassEntry,
            activity: FragmentActivity,
        ) {
            if (_state.value.decrypting) return
            _state.update { it.copy(decrypting = true, decryptError = null) }
            viewModelScope.launch {
                try {
                    val creds = decryption.decrypt(entry, activity)
                    _state.update { it.copy(decrypting = false, credentials = creds) }
                } catch (e: SessionError.NoActiveSession) {
                    _state.update { it.copy(decrypting = false, sessionStartNeeded = true) }
                } catch (e: DecryptionError) {
                    _state.update { it.copy(decrypting = false, decryptError = e.message ?: "Decryption failed") }
                } catch (e: Exception) {
                    _state.update { it.copy(decrypting = false, decryptError = e.message ?: "Decryption failed") }
                }
            }
        }

        fun onSessionStartNavigated() {
            _state.update { it.copy(sessionStartNeeded = false) }
        }

        fun toggleReveal() {
            val revealing = !_state.value.passwordRevealed
            _state.update { it.copy(passwordRevealed = revealing) }
            blurJob?.cancel()
            if (revealing) {
                blurJob =
                    viewModelScope.launch {
                        delay(45_000)
                        _state.update { it.copy(passwordRevealed = false) }
                    }
            }
        }

        fun copyPassword() {
            val creds = _state.value.credentials ?: return
            val password = String(creds.password)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("password", password))
            _state.update { it.copy(clipboardCopied = true) }
            clipClearJob?.cancel()
            clipClearJob =
                viewModelScope.launch {
                    delay(45_000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    _state.update { it.copy(clipboardCopied = false) }
                }
        }

        fun loadMetadata(entryPath: String) {
            viewModelScope.launch {
                val info = gitSync.lastCommitForFile(entryPath)
                _state.update { it.copy(commitInfo = info, metadataLoaded = true) }
            }
        }

        override fun onCleared() {
            super.onCleared()
            _state.value.credentials?.zero()
        }
    }
