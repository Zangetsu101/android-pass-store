package com.example.pass.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.keymanagement.CryptoOperations
import com.example.pass.keymanagement.SessionError
import com.example.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionStartUiState(
    val passphrase: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val sessionTimeoutMinutes: Int = 5,
)

@HiltViewModel
class SessionStartViewModel
    @Inject
    constructor(
        private val cryptoOperations: CryptoOperations,
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SessionStartUiState())
        val state: StateFlow<SessionStartUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                appPreferences.sessionTimeoutMinutes.collect { minutes ->
                    _state.update { it.copy(sessionTimeoutMinutes = minutes) }
                }
            }
        }

        fun setPassphrase(value: String) {
            _state.update { it.copy(passphrase = value, error = null) }
        }

        fun submit() {
            val passphrase = _state.value.passphrase.ifEmpty { return }
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                try {
                    cryptoOperations.startSession(passphrase)
                    _state.update { it.copy(loading = false, success = true) }
                } catch (e: SessionError.WrongPassphrase) {
                    _state.update { it.copy(loading = false, error = "Wrong passphrase") }
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to start session") }
                }
            }
        }
    }
