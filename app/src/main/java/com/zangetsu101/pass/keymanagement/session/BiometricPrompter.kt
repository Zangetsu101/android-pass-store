// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface BiometricPrompter {
    suspend fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
    ): Cipher
}

class SystemBiometricPrompter
    @Inject
    constructor() : BiometricPrompter {
        override suspend fun prompt(
            activity: FragmentActivity,
            cipher: Cipher,
        ): Cipher {
            val result =
                withContext(Dispatchers.Main) {
                    showBiometricPromptWithCrypto(activity, BiometricPrompt.CryptoObject(cipher))
                }
            return result.cryptoObject?.cipher
                ?: throw BiometricAuthException("No authenticated cipher returned")
        }
    }

private suspend fun showBiometricPromptWithCrypto(
    activity: FragmentActivity,
    cryptoObject: BiometricPrompt.CryptoObject,
    title: String = "Authenticate",
    subtitle: String = "Unlock pass.android to continue",
) = suspendCancellableCoroutine<BiometricPrompt.AuthenticationResult> { cont ->
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt =
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(result)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (cont.isActive) {
                        val ex =
                            if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                                BiometricNotEnrolledException()
                            } else {
                                BiometricAuthException("Biometric error $errorCode: $errString")
                            }
                        cont.resumeWithException(ex)
                    }
                }

                override fun onAuthenticationFailed() {
                    // Single attempt failed — BiometricPrompt retries automatically; do nothing here
                }
            },
        )

    val info =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()

    cont.invokeOnCancellation { /* prompt has no cancel API; activity finish will dismiss it */ }
    prompt.authenticate(info, cryptoObject)
}

class BiometricAuthException(
    message: String,
) : Exception(message)

class BiometricNotEnrolledException : Exception("No strong biometric enrolled")
