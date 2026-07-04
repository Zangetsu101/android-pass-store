// SPDX-License-Identifier: GPL-3.0-or-later
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

enum class SshKeySource { GPG_AUTH, DEVICE }

/** Git remote URL input + its validation error. Always present, mutates in place. */
data class FormState(
    val remoteUrl: String = "",
    val remoteUrlError: String? = null,
)

/**
 * Outcome of the one-time GPG auth-subkey discovery. Once resolved the variant is
 * fixed; only [GpgAvailable.selectedSource] toggles afterwards.
 */
sealed interface KeyResolution {
    data object Resolving : KeyResolution

    data class GpgAvailable(
        val authKey: AuthSubkeyInfo,
        val selectedSource: SshKeySource,
    ) : KeyResolution

    data object DeviceOnly : KeyResolution
}

/** Lifecycle of the on-device SSH key. Independent of [KeyResolution]. */
sealed interface DeviceKey {
    data object None : DeviceKey

    data object Generating : DeviceKey

    data class Ready(
        val publicKey: String,
    ) : DeviceKey
}

/** GPG auth-subkey seed extraction. Only exercised on the GPG_AUTH clone path. */
sealed interface Extraction {
    data object Idle : Extraction

    data object Extracting : Extraction

    data class Failed(
        val reason: String,
    ) : Extraction
}

@HiltViewModel
class CloneRepoViewModel
    @Inject
    constructor(
        private val sshKeyStore: SshKeyStore,
        private val gpgKeyStore: GpgKeyStore,
        private val appPreferences: AppPreferences,
        @Named("IoDispatcher") private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _form = MutableStateFlow(FormState())
        val form: StateFlow<FormState> = _form.asStateFlow()

        private val _keyResolution = MutableStateFlow<KeyResolution>(KeyResolution.Resolving)
        val keyResolution: StateFlow<KeyResolution> = _keyResolution.asStateFlow()

        private val _deviceKey = MutableStateFlow<DeviceKey>(DeviceKey.None)
        val deviceKey: StateFlow<DeviceKey> = _deviceKey.asStateFlow()

        private val _extraction = MutableStateFlow<Extraction>(Extraction.Idle)
        val extraction: StateFlow<Extraction> = _extraction.asStateFlow()

        private val _navigation = Channel<String>(Channel.BUFFERED)
        val navigation: Flow<String> = _navigation.receiveAsFlow()

        init {
            viewModelScope.launch {
                val authSubkey = withContext(ioDispatcher) { gpgKeyStore.findAuthSubkey() }
                if (authSubkey != null) {
                    _keyResolution.value = KeyResolution.GpgAvailable(authSubkey, SshKeySource.GPG_AUTH)
                } else {
                    // Stay Resolving through key generation so the screen keeps showing the
                    // placeholder toggle until the device key is ready, then settle DeviceOnly.
                    generateDeviceKey(persist = true)
                    _keyResolution.value = KeyResolution.DeviceOnly
                }
            }
        }

        fun setRemoteUrl(url: String) {
            _form.update { it.copy(remoteUrl = url, remoteUrlError = null) }
        }

        fun validateRemoteUrl(): Boolean {
            val url = _form.value.remoteUrl.trim()
            if (url.isEmpty()) {
                _form.update { it.copy(remoteUrlError = "Remote URL is required") }
                return false
            }
            val valid =
                url.startsWith("git@") || url.startsWith("ssh://") || url.startsWith("file://")
            if (!valid) {
                _form.update { it.copy(remoteUrlError = "Enter a valid git remote URL") }
                return false
            }
            _form.update { it.copy(remoteUrlError = null) }
            return true
        }

        fun setSource(source: SshKeySource) {
            val current = _keyResolution.value
            if (current !is KeyResolution.GpgAvailable || current.selectedSource == source) return
            _keyResolution.value = current.copy(selectedSource = source)
            _extraction.value = Extraction.Idle
            if (source == SshKeySource.DEVICE && _deviceKey.value is DeviceKey.None) {
                viewModelScope.launch { generateDeviceKey(persist = true) }
            }
        }

        fun regenerateSshKey() {
            viewModelScope.launch {
                generateDeviceKey(persist = effectiveSource() == SshKeySource.DEVICE)
            }
        }

        fun onClone(passphrase: String = "") {
            val url = _form.value.remoteUrl.trim()
            viewModelScope.launch {
                val resolution = _keyResolution.value
                if (resolution is KeyResolution.GpgAvailable && resolution.selectedSource == SshKeySource.GPG_AUTH) {
                    _extraction.value = Extraction.Extracting
                    try {
                        val seed =
                            withContext(ioDispatcher) {
                                gpgKeyStore.extractAuthSubkeySeed(passphrase, resolution.authKey.keyId)
                            }
                        val pubKey =
                            withContext(ioDispatcher) {
                                sshKeyStore.importEd25519Seed(seed)
                            }
                        appPreferences.setSshPublicKey(pubKey)
                        appPreferences.setSshKeySource("gpg_auth")
                        _extraction.value = Extraction.Idle
                        _navigation.send(url)
                    } catch (_: SessionError.WrongPassphrase) {
                        _extraction.value = Extraction.Failed("Wrong passphrase")
                    }
                } else {
                    appPreferences.setSshKeySource("device")
                    _navigation.send(url)
                }
            }
        }

        /** The source the device key would be active under, used to decide persistence. */
        private fun effectiveSource(): SshKeySource =
            when (val k = _keyResolution.value) {
                is KeyResolution.GpgAvailable -> k.selectedSource
                else -> SshKeySource.DEVICE
            }

        private suspend fun generateDeviceKey(persist: Boolean) {
            _deviceKey.value = DeviceKey.Generating
            val pubKey = withContext(ioDispatcher) { sshKeyStore.generateSshKey() }
            _deviceKey.value = DeviceKey.Ready(pubKey)
            if (persist) appPreferences.setSshPublicKey(pubKey)
        }
    }
