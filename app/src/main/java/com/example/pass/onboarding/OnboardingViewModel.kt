package com.example.pass.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.gitsync.GitSync
import com.example.pass.gitsync.SyncError
import com.example.pass.keymanagement.KeyImportError
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Paths
import javax.inject.Inject

data class OnboardingUiState(
    val remoteUrl: String = "",
    val remoteUrlError: String? = null,
    val sshPublicKey: String? = null,
    val gpgKeyText: String = "",
    val gpgImportError: String? = null,
    val gpgImported: Boolean = false,
    val cloning: Boolean = false,
    val cloneError: String? = null,
    val cloneComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManagement: KeyManagement,
    private val gitSync: GitSync,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setRemoteUrl(url: String) {
        _state.update { it.copy(remoteUrl = url, remoteUrlError = null) }
    }

    fun validateRemoteUrl(): Boolean {
        val url = _state.value.remoteUrl.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(remoteUrlError = "Remote URL is required") }
            return false
        }
        val valid = url.startsWith("git@") || url.startsWith("ssh://") ||
            url.startsWith("https://") || url.startsWith("file://")
        if (!valid) {
            _state.update { it.copy(remoteUrlError = "Enter a valid git remote URL") }
            return false
        }
        _state.update { it.copy(remoteUrlError = null) }
        return true
    }

    fun generateSshKeyIfNeeded() {
        if (_state.value.sshPublicKey != null) return
        viewModelScope.launch {
            val publicKey = withContext(Dispatchers.IO) { keyManagement.generateSshKey() }
            _state.update { it.copy(sshPublicKey = publicKey) }
            appPreferences.setSshPublicKey(publicKey)
        }
    }

    fun setGpgKeyText(text: String) {
        _state.update { it.copy(gpgKeyText = text, gpgImportError = null, gpgImported = false) }
    }

    fun importGpgKey() {
        viewModelScope.launch {
            try {
                val text = _state.value.gpgKeyText
                withContext(Dispatchers.IO) { keyManagement.importGpgKey(text) }
                _state.update { it.copy(gpgImported = true, gpgImportError = null) }
            } catch (e: KeyImportError) {
                _state.update { it.copy(gpgImportError = e.message ?: "Import failed", gpgImported = false) }
            }
        }
    }

    fun startClone() {
        val url = _state.value.remoteUrl.trim()
        _state.update { it.copy(cloning = true, cloneError = null, cloneComplete = false) }
        viewModelScope.launch {
            try {
                val repoDir = Paths.get(context.filesDir.absolutePath, "repo")
                val sshKeyPair = if (url.startsWith("git@") || url.startsWith("ssh://")) {
                    withContext(Dispatchers.IO) { keyManagement.getSshKey() }
                } else null
                gitSync.clone(url, repoDir, sshKeyPair)
                appPreferences.setRemoteUrl(url)
                _state.update { it.copy(cloning = false, cloneComplete = true) }
            } catch (e: SyncError) {
                _state.update { it.copy(cloning = false, cloneError = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(cloning = false, cloneError = e.message ?: "Clone failed") }
            }
        }
    }
}
