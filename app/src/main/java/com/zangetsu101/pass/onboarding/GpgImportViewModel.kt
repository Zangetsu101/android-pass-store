package com.zangetsu101.pass.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

data class GpgImportUiState(
    val gpgKeyText: String = "",
    val gpgImportError: String? = null,
    val gpgImported: Boolean = false,
)

@HiltViewModel
class GpgImportViewModel
    @Inject
    constructor(
        private val cryptoOperations: GpgKeyStore,
        private val appPreferences: AppPreferences,
        @Named("IoDispatcher") private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GpgImportUiState())
        val state: StateFlow<GpgImportUiState> = _state.asStateFlow()

        fun setGpgKeyText(text: String) {
            _state.update { it.copy(gpgKeyText = text, gpgImportError = null, gpgImported = false) }
        }

        fun setGpgKeyFromBytes(bytes: ByteArray) {
            val text = bytes.toString(Charsets.UTF_8)
            if (text.trimStart().startsWith("-----BEGIN PGP")) {
                setGpgKeyText(text)
                return
            }
            viewModelScope.launch {
                try {
                    val armored = withContext(ioDispatcher) { cryptoOperations.armorGpgKey(bytes) }
                    setGpgKeyText(armored)
                } catch (e: KeyImportError) {
                    _state.update { it.copy(gpgImportError = "Invalid or unrecognized key file") }
                }
            }
        }

        fun importGpgKey() {
            viewModelScope.launch {
                try {
                    val text = _state.value.gpgKeyText
                    withContext(ioDispatcher) { cryptoOperations.importGpgKey(text) }
                    _state.update { it.copy(gpgImported = true, gpgImportError = null) }
                    appPreferences.setGpgImported(true)
                } catch (e: KeyImportError) {
                    _state.update { it.copy(gpgImportError = e.message ?: "Import failed", gpgImported = false) }
                }
            }
        }
    }
