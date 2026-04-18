package com.example.pass.keymanagement

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// Authenticators: prefer strong biometric, fall back to device PIN/password
private val ALLOWED_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

suspend fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String = "Authenticate",
    subtitle: String = "Unlock PassDroid to continue",
) = suspendCancellableCoroutine<Unit> { cont ->
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (cont.isActive) cont.resume(Unit)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (cont.isActive) cont.resumeWithException(
                BiometricAuthException("Biometric error $errorCode: $errString")
            )
        }
        override fun onAuthenticationFailed() {
            // Single attempt failed — BiometricPrompt retries automatically; do nothing here
        }
    })

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .build()

    cont.invokeOnCancellation { /* prompt has no cancel API; activity finish will dismiss it */ }
    prompt.authenticate(info)
}

fun isBiometricAvailable(activity: FragmentActivity): Boolean {
    val mgr = BiometricManager.from(activity)
    return mgr.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

class BiometricAuthException(message: String) : Exception(message)
