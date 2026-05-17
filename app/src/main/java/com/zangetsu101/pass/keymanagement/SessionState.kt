package com.zangetsu101.pass.keymanagement

sealed class SessionState {
    data object Active : SessionState()

    data class Inactive(
        val reason: EndReason,
    ) : SessionState()
}

enum class EndReason { TIMEOUT, MANUAL, REBOOT, BIOMETRIC_CHANGED }
