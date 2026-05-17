package com.zangetsu101.pass.keymanagement

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// API 30+: CryptoObject supports BIOMETRIC_STRONG or DEVICE_CREDENTIAL (PIN/pattern fallback)
// API 26–29: combining with DEVICE_CREDENTIAL throws IllegalArgumentException — biometric only
private val ALLOWED_AUTHENTICATORS =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_STRONG
    }

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
            .apply {
                // setNegativeButtonText is incompatible with DEVICE_CREDENTIAL
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText("Cancel")
            }.build()

    cont.invokeOnCancellation { /* prompt has no cancel API; activity finish will dismiss it */ }
    prompt.authenticate(info, cryptoObject)
}

class BiometricAuthException(
    message: String,
) : Exception(message)

class BiometricNotEnrolledException : Exception("No screen lock or biometric enrolled")
