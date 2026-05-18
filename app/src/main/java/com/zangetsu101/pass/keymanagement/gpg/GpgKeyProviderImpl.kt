package com.zangetsu101.pass.keymanagement.gpg

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.session.PassphraseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GpgKeyProviderImpl(
    private val passphraseProvider: PassphraseProvider,
    private val gpgKeyOperations: GpgKeyOperations,
) : GpgKeyProvider {
    override suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey {
        val passphrase = passphraseProvider.getPassphrase(activity)
        return withContext(Dispatchers.IO) { gpgKeyOperations.loadAndUnlock(passphrase) }
    }
}
