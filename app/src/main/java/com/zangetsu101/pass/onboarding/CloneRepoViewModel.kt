package com.zangetsu101.pass.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.keymanagement.ssh.SshKeyOperations
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CloneRepoUiState(
    val remoteUrl: String = "",
    val remoteUrlError: String? = null,
    val sshPublicKey: String? = null,
)

@HiltViewModel
class CloneRepoViewModel
    @Inject
    constructor(
        private val cryptoOperations: SshKeyOperations,
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CloneRepoUiState())
        val state: StateFlow<CloneRepoUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val publicKey = withContext(Dispatchers.IO) { cryptoOperations.generateSshKey() }
                _state.update { it.copy(sshPublicKey = publicKey) }
                appPreferences.setSshPublicKey(publicKey)
            }
        }

        fun setRemoteUrl(url: String) {
            _state.update { it.copy(remoteUrl = url, remoteUrlError = null) }
        }

        fun validateRemoteUrl(): Boolean {
            val url = _state.value.remoteUrl.trim()
            if (url.isEmpty()) {
                _state.update { it.copy(remoteUrlError = "Remote URL is required") }
                return false
            }
            val valid =
                url.startsWith("git@") || url.startsWith("ssh://") ||
                    url.startsWith("https://") || url.startsWith("file://")
            if (!valid) {
                _state.update { it.copy(remoteUrlError = "Enter a valid git remote URL") }
                return false
            }
            _state.update { it.copy(remoteUrlError = null) }
            return true
        }

        fun regenerateSshKey() {
            viewModelScope.launch {
                val publicKey = withContext(Dispatchers.IO) { cryptoOperations.generateSshKey() }
                _state.update { it.copy(sshPublicKey = publicKey) }
                appPreferences.setSshPublicKey(publicKey)
            }
        }
    }
