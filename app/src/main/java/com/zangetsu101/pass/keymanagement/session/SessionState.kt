// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

sealed class SessionState {
    data object Active : SessionState()

    data class Inactive(
        val reason: EndReason,
    ) : SessionState()
}

enum class EndReason { TIMEOUT, MANUAL, BIOMETRIC_CHANGED, TIMEOUT_CHANGED }