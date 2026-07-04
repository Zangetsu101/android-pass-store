// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CheckResult {
    object Loading : CheckResult()

    data class Pass(
        val value: String,
    ) : CheckResult()

    data class Fail(
        val value: String,
    ) : CheckResult()
}

sealed class PreflightFailure {
    object ApiTooLow : PreflightFailure()

    data class BiometricNotEnrolled(
        val canUseDeviceCredential: Boolean,
    ) : PreflightFailure()
}

data class PreflightUiState(
    val biometric: CheckResult = CheckResult.Loading,
    val apiLevel: CheckResult = CheckResult.Loading,
) {
    val isReady: Boolean
        get() = biometric is CheckResult.Pass && apiLevel is CheckResult.Pass

    val isLoading: Boolean
        get() = biometric is CheckResult.Loading || apiLevel is CheckResult.Loading

    val primaryFailure: PreflightFailure?
        get() =
            when {
                apiLevel is CheckResult.Fail -> {
                    PreflightFailure.ApiTooLow
                }

                biometric is CheckResult.Fail -> {
                    PreflightFailure.BiometricNotEnrolled(
                        canUseDeviceCredential = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    )
                }

                else -> {
                    null
                }
            }
}

@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PreflightUiState())
        val state: StateFlow<PreflightUiState> = _state.asStateFlow()

        init {
            runChecks()
        }

        fun recheck() = runChecks()

        private fun runChecks() {
            _state.value = PreflightUiState()

            viewModelScope.launch {
                val api = Build.VERSION.SDK_INT
                if (api >= Build.VERSION_CODES.R) {
                    _state.update { it.copy(apiLevel = CheckResult.Pass("api $api")) }
                } else {
                    _state.update { it.copy(apiLevel = CheckResult.Fail("< 30")) }
                }
            }

            viewModelScope.launch {
                val authenticators =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                    } else {
                        BIOMETRIC_STRONG
                    }
                val canAuth = BiometricManager.from(context).canAuthenticate(authenticators)
                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    _state.update { it.copy(biometric = CheckResult.Pass("enrolled")) }
                } else {
                    _state.update { it.copy(biometric = CheckResult.Fail("not enrolled")) }
                }
            }
        }
    }