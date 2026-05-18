package com.zangetsu101.pass.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyOperations
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
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

data class SessionStartUiState(
    val title: String = "session expired",
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
        private val gpgKeyOperations: GpgKeyOperations,
        private val sessionOperations: SessionOperations,
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SessionStartUiState())
        val state: StateFlow<SessionStartUiState> = _state.asStateFlow()

        init {
            val reason = (sessionOperations.sessionState.value as? SessionState.Inactive)?.reason
            _state.update { it.copy(title = titleFor(reason)) }
            viewModelScope.launch {
                appPreferences.sessionTimeoutMinutes.collect { minutes ->
                    _state.update { it.copy(sessionTimeoutMinutes = minutes) }
                }
            }
        }

        private fun titleFor(reason: EndReason?) =
            when (reason) {
                EndReason.MANUAL, null -> "start session"
                EndReason.BIOMETRIC_CHANGED -> "security change detected"
                EndReason.TIMEOUT -> "session expired"
            }

        fun setPassphrase(value: String) {
            _state.update { it.copy(passphrase = value, error = null) }
        }

        fun submit() {
            val passphrase = _state.value.passphrase.ifEmpty { return }
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { gpgKeyOperations.validatePassphrase(passphrase) }
                    sessionOperations.createSession(passphrase)
                    _state.update { it.copy(loading = false, success = true) }
                } catch (e: SessionError) {
                    _state.update { it.copy(loading = false, error = e.message) }
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, error = e.message ?: "Failed to start session") }
                }
            }
        }
    }
