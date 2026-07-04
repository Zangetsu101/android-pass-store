// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.credentialprovider

import android.app.PendingIntent
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.passstore.PassStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Note: BiometricPromptData / setBiometricPromptData require credentials >= 1.5.0-alpha01.
// Upgrade the dependency when that version stabilises to enable inline biometric on API 35+.
@RequiresApi(34)
@AndroidEntryPoint
class PassAndroidCredentialProviderService : CredentialProviderService() {
    @Inject lateinit var passStore: PassStore

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val packageName = request.callingAppInfo?.packageName ?: ""
        val candidates = passStore.resolveByPackage(packageName)

        val entries = mutableListOf<PasswordCredentialEntry>()
        for (option in request.beginGetCredentialOptions) {
            if (option !is BeginGetPasswordOption) continue
            for (entry in candidates) {
                entries += buildPasswordEntry(entry, option)
            }
        }

        if (entries.isEmpty()) {
            callback.onError(GetCredentialUnknownException("No matching credentials"))
            return
        }
        callback.onResult(BeginGetCredentialResponse(credentialEntries = entries))
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        callback.onError(CreateCredentialUnknownException("PassAndroid is read-only"))
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    private fun buildPasswordEntry(
        entry: PassEntry,
        option: BeginGetPasswordOption,
    ): PasswordCredentialEntry {
        val label = "${entry.username}${entry.domain?.let { " ($it)" } ?: ""}"
        val authIntent = CredentialAuthActivity.createIntent(this, entry.path, option.id)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                entry.path.hashCode(),
                authIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return PasswordCredentialEntry
            .Builder(
                context = this,
                username = label,
                pendingIntent = pendingIntent,
                beginGetPasswordOption = option,
            ).build()
    }
}