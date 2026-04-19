package com.example.pass.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.keymanagement.SessionManager
import com.example.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val clearing: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManagement: KeyManagement,
    private val appPreferences: AppPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val sshPublicKey: StateFlow<String?> = appPreferences.sshPublicKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessionTimeoutMinutes: StateFlow<Int> = appPreferences.sessionTimeoutMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    fun setSessionTimeout(minutes: Int) {
        viewModelScope.launch {
            appPreferences.setSessionTimeout(minutes)
            sessionManager.setTimeoutMs(minutes * 60_000L)
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        _state.update { it.copy(clearing = true) }
        viewModelScope.launch {
            keyManagement.clearAllKeys()
            appPreferences.clearAll()
            File(context.filesDir, "repo").deleteRecursively()
            _state.update { it.copy(clearing = false) }
            onComplete()
        }
    }
}
