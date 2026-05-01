package com.example.pass.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.gitsync.GitSync
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.keymanagement.SessionManager
import com.example.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManagement: KeyManagement,
    private val appPreferences: AppPreferences,
    private val sessionManager: SessionManager,
    private val gitSync: GitSync,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val sshPublicKey: StateFlow<String?> = appPreferences.sshPublicKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val remoteUrl: StateFlow<String> = appPreferences.remoteUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // 0 = manual lock only; >0 = inactivity timeout in minutes
    val sessionTimeoutMinutes: StateFlow<Int> = appPreferences.sessionTimeoutMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    init {
        viewModelScope.launch {
            val status = runCatching { gitSync.syncStatus() }.getOrNull()
            _state.update { it.copy(lastSyncTime = status?.lastSyncTime) }
        }
    }

    fun setSessionTimeout(minutes: Int) {
        viewModelScope.launch {
            appPreferences.setSessionTimeout(minutes)
            sessionManager.setTimeoutMs(minutes * 60_000L)
        }
    }

    fun setSessionTimeoutEnabled(enabled: Boolean) {
        val minutes = if (enabled) {
            val current = sessionTimeoutMinutes.value
            if (current > 0) current else 5
        } else {
            0
        }
        setSessionTimeout(minutes)
    }

    fun lockSession() {
        keyManagement.endSession()
    }

    fun clearAllData(onComplete: () -> Unit) {
        _state.update { it.copy(clearing = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                keyManagement.clearAllKeys()
                File(context.filesDir, "repo").deleteRecursively()
            }
            appPreferences.clearAll()
            _state.update { it.copy(clearing = false) }
            onComplete()
        }
    }
}
