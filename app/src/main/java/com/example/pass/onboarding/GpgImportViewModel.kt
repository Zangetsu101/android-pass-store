package com.example.pass.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.keymanagement.KeyImportError
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GpgImportUiState(
    val gpgKeyText: String = "",
    val gpgImportError: String? = null,
    val gpgImported: Boolean = false,
)

@HiltViewModel
class GpgImportViewModel @Inject constructor(
    private val keyManagement: KeyManagement,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(GpgImportUiState())
    val state: StateFlow<GpgImportUiState> = _state.asStateFlow()

    fun setGpgKeyText(text: String) {
        _state.update { it.copy(gpgKeyText = text, gpgImportError = null, gpgImported = false) }
    }

    fun importGpgKey() {
        viewModelScope.launch {
            try {
                val text = _state.value.gpgKeyText
                withContext(Dispatchers.IO) { keyManagement.importGpgKey(text) }
                _state.update { it.copy(gpgImported = true, gpgImportError = null) }
                appPreferences.setGpgImported(true)
            } catch (e: KeyImportError) {
                _state.update { it.copy(gpgImportError = e.message ?: "Import failed", gpgImported = false) }
            }
        }
    }
}
