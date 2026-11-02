// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SessionTimeoutReceiver : BroadcastReceiver() {
    @Inject lateinit var sessionOperations: SessionOperations

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (sessionOperations.sessionState.value is SessionState.Active) {
            sessionOperations.endSession(EndReason.TIMEOUT)
        }
    }
}
