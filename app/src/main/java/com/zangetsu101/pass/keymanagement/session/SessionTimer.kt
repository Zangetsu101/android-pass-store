// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import android.content.Context
import com.zangetsu101.pass.notification.SessionTimerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface SessionTimer {
    fun start()

    fun stop()
}

class AndroidSessionTimer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SessionTimer {
        override fun start() = SessionTimerService.start(context)

        override fun stop() = SessionTimerService.stop(context)
    }
