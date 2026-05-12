package com.example.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.example.pass.passstore.PassEntry

interface Decryption {
    @Throws(DecryptionError::class)
    suspend fun decrypt(
        entry: PassEntry,
        activity: FragmentActivity,
    ): Credentials

    @Throws(DecryptionError::class)
    suspend fun decryptForAutofill(
        entry: PassEntry,
        activity: FragmentActivity,
    ): AutofillCredentials
}
