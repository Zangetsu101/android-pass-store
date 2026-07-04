// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SessionClearReceiver : BroadcastReceiver() {
    @Inject lateinit var sessionOperations: SessionOperations

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        sessionOperations.endSession(EndReason.MANUAL)
    }
}