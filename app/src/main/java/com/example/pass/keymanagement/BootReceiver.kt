package com.example.pass.keymanagement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pass.keymanagement.KeyManagement
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var keyManagement: KeyManagement

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            keyManagement.endSession()
        }
    }
}
