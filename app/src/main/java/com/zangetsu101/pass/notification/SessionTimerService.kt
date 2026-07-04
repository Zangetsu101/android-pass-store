// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zangetsu101.pass.R
import dagger.hilt.android.AndroidEntryPoint

private const val FGS_NOTIFICATION_ID = 1001

private const val CHANNEL_ID = "session_timer"

@AndroidEntryPoint
class SessionTimerService : Service() {
    private val notifManager by lazy { getSystemService(NotificationManager::class.java) }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SessionTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionTimerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val fgsType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        try {
            ServiceCompat.startForeground(this, FGS_NOTIFICATION_ID, buildNotification(), fgsType)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val clearIntent =
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, SessionClearReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Session active")
            .setContentText("Touch to clear session")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(clearIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, "Session Timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Shows remaining session time"
                    setSound(null, null)
                    enableVibration(false)
                }
            notifManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}