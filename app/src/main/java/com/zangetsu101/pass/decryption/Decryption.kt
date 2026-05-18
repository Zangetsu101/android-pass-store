package com.zangetsu101.pass.decryption

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.passstore.PassEntry

interface Decryption {
    @Throws(DecryptionError::class)
    suspend fun decrypt(
        entry: PassEntry,
        activity: FragmentActivity,
    ): Credentials
}
