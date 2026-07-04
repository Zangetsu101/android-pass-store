// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.StateFlow

interface SessionOperations : PassphraseProvider {
    val sessionState: StateFlow<SessionState>

    @Throws(SessionError::class)
    suspend fun createSession(passphrase: String)

    fun endSession(reason: EndReason = EndReason.MANUAL)

    fun touchSession()
}
