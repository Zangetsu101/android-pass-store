package com.example.pass.keymanagement

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// CryptoObject prompts cannot combine BIOMETRIC_STRONG + DEVICE_CREDENTIAL on API 26–29
private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_STRONG

suspend fun showBiometricPromptWithCrypto(
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
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .setNegativeButtonText("Cancel")
            .build()

    cont.invokeOnCancellation { /* prompt has no cancel API; activity finish will dismiss it */ }
    prompt.authenticate(info, cryptoObject)
}

class BiometricAuthException(
    message: String,
) : Exception(message)

class BiometricNotEnrolledException : Exception("No screen lock or biometric enrolled")
