package com.example.pass.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.gitsync.GitSync
import com.example.pass.keymanagement.CryptoOperations
import com.example.pass.keymanagement.SessionOperations
import com.example.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

data class SettingsUiState(
    val clearing: Boolean = false,
    val lastSyncTime: Instant? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionOperations: SessionOperations,
        private val cryptoOperations: CryptoOperations,
        private val appPreferences: AppPreferences,
        private val gitSync: GitSync,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = _state.asStateFlow()

        private val _gpgKeyInfo = MutableStateFlow<Pair<String, String>?>(null)
        val gpgKeyInfo: StateFlow<Pair<String, String>?> = _gpgKeyInfo.asStateFlow()

        val sessionActive: StateFlow<Boolean> =
            sessionOperations.sessionState
                .map { it is com.example.pass.keymanagement.SessionState.Active }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val sshPublicKey: StateFlow<String?> =
            appPreferences.sshPublicKey
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val remoteUrl: StateFlow<String> =
            appPreferences.remoteUrl
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

        val sessionTimeoutMinutes: StateFlow<Int> =
            appPreferences.sessionTimeoutMinutes
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

        val clipboardTimeoutSeconds: StateFlow<Int> =
            appPreferences.clipboardTimeoutSeconds
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 45)

        val defaultViewTree: StateFlow<Boolean> =
            appPreferences.defaultViewTree
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

        init {
            viewModelScope.launch {
                val status = runCatching { gitSync.syncStatus() }.getOrNull()
                _state.update { it.copy(lastSyncTime = status?.lastSyncTime) }
            }
            viewModelScope.launch(Dispatchers.IO) {
                _gpgKeyInfo.value = cryptoOperations.getGpgKeyInfo()
            }
        }

        fun setSessionTimeout(minutes: Int) {
            viewModelScope.launch { appPreferences.setSessionTimeout(minutes) }
        }

        fun setClipboardTimeout(seconds: Int) {
            viewModelScope.launch { appPreferences.setClipboardTimeout(seconds) }
        }

        fun setDefaultView(tree: Boolean) {
            viewModelScope.launch { appPreferences.setDefaultViewTree(tree) }
        }

        fun clearSession() {
            sessionOperations.endSession()
        }

        fun clearAllData(onComplete: () -> Unit) {
            _state.update { it.copy(clearing = true) }
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    cryptoOperations.clearAllKeys()
                    File(context.filesDir, "repo").deleteRecursively()
                }
                appPreferences.clearAll()
                _state.update { it.copy(clearing = false) }
                onComplete()
            }
        }
    }
