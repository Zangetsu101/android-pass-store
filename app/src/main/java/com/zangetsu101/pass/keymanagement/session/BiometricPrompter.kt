package com.zangetsu101.pass.keymanagement.session

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject

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
