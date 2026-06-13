package com.zangetsu101.pass.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

enum class SshKeySource { GPG_AUTH, DEVICE }

data class CloneRepoUiState(
    val remoteUrl: String = "",
    val remoteUrlError: String? = null,
    val source: SshKeySource = SshKeySource.DEVICE,
    val authSubkey: AuthSubkeyInfo? = null,
    val devicePublicKey: String? = null,
    val passphraseError: String? = null,
    val isExtracting: Boolean = false,
    val navigateTo: String? = null,
)

@HiltViewModel
class CloneRepoViewModel
    @Inject
    constructor(
        private val sshKeyStore: SshKeyStore,
        private val gpgKeyStore: GpgKeyStore,
        private val appPreferences: AppPreferences,
        @Named("IoDispatcher") private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CloneRepoUiState())
        val state: StateFlow<CloneRepoUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val authSubkey = withContext(ioDispatcher) { gpgKeyStore.findAuthSubkey() }
                if (authSubkey != null) {
                    _state.update {
                        it.copy(
                            authSubkey = authSubkey,
                            source = SshKeySource.GPG_AUTH,
                        )
                    }
                } else {
                    val pubKey = withContext(ioDispatcher) { sshKeyStore.generateSshKey() }
                    _state.update { it.copy(source = SshKeySource.DEVICE, devicePublicKey = pubKey) }
                    appPreferences.setSshPublicKey(pubKey)
                }
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
                url.startsWith("git@") || url.startsWith("ssh://") || url.startsWith("file://")
            if (!valid) {
                _state.update { it.copy(remoteUrlError = "Enter a valid git remote URL") }
                return false
            }
            _state.update { it.copy(remoteUrlError = null) }
            return true
        }

        fun setSource(source: SshKeySource) {
            if (_state.value.source == source) return
            _state.update { it.copy(source = source, passphraseError = null) }
            if (source == SshKeySource.DEVICE && _state.value.devicePublicKey == null) {
                viewModelScope.launch {
                    val pubKey = withContext(ioDispatcher) { sshKeyStore.generateSshKey() }
                    _state.update { it.copy(devicePublicKey = pubKey) }
                    appPreferences.setSshPublicKey(pubKey)
                }
            }
        }

        fun regenerateSshKey() {
            viewModelScope.launch {
                val pubKey = withContext(ioDispatcher) { sshKeyStore.generateSshKey() }
                _state.update { it.copy(devicePublicKey = pubKey) }
                if (_state.value.source == SshKeySource.DEVICE) {
                    appPreferences.setSshPublicKey(pubKey)
                }
            }
        }

        fun onClone(passphrase: String = "") {
            val url = _state.value.remoteUrl.trim()
            viewModelScope.launch {
                when (_state.value.source) {
                    SshKeySource.GPG_AUTH -> {
                        val keyId = _state.value.authSubkey?.keyId ?: return@launch
                        _state.update { it.copy(isExtracting = true, passphraseError = null) }
                        try {
                            val seed =
                                withContext(ioDispatcher) {
                                    gpgKeyStore.extractAuthSubkeySeed(passphrase, keyId)
                                }
                            val pubKey =
                                withContext(ioDispatcher) {
                                    sshKeyStore.importEd25519Seed(seed)
                                }
                            appPreferences.setSshPublicKey(pubKey)
                            appPreferences.setSshKeySource("gpg_auth")
                            _state.update { it.copy(isExtracting = false, navigateTo = url) }
                        } catch (_: SessionError.WrongPassphrase) {
                            _state.update { it.copy(isExtracting = false, passphraseError = "Wrong passphrase") }
                        }
                    }

                    SshKeySource.DEVICE -> {
                        appPreferences.setSshKeySource("device")
                        _state.update { it.copy(navigateTo = url) }
                    }
                }
            }
        }

        fun clearNavigation() {
            _state.update { it.copy(navigateTo = null) }
        }
    }
