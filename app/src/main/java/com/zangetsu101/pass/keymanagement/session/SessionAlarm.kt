// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Wall-clock backstop for the session timeout. Coroutine `delay()` pauses while the
 * device is in deep sleep, so the timeout is also scheduled with AlarmManager, which
 * can fire during Doze.
 */
interface SessionAlarm {
    fun schedule(triggerAtMillis: Long)

    fun cancel()
}

class AndroidSessionAlarm
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SessionAlarm {
        private val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

        override fun schedule(triggerAtMillis: Long) {
            val pi = pendingIntent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }

        override fun cancel() = alarmManager.cancel(pendingIntent())

        private fun pendingIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, SessionTimeoutReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private companion object {
            const val REQUEST_CODE = 7392
        }
    }
