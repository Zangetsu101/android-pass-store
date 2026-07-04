// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.credentialprovider

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.passstore.PassStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(34)
@AndroidEntryPoint
class CredentialAuthActivity : FragmentActivity() {
    @Inject lateinit var passStore: PassStore

    @Inject lateinit var decryption: Decryption

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_ENTRY_PATH)
        if (path == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val entry = passStore.index.value.find { it.path == path }
        if (entry == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val creds = decryption.decrypt(entry, this@CredentialAuthActivity)
                val credential = PasswordCredential(creds.username, String(creds.password))
                creds.zero()

                val resultData = Intent()
                PendingIntentHandler.setGetCredentialResponse(
                    resultData,
                    GetCredentialResponse(credential),
                )
                setResult(RESULT_OK, resultData)
            } catch (e: DecryptionError) {
                setResult(RESULT_CANCELED)
            } catch (e: Exception) {
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }

    companion object {
        private const val EXTRA_ENTRY_PATH = "entry_path"

        fun createIntent(
            context: Context,
            entryPath: String,
            optionId: String,
        ): Intent =
            Intent(context, CredentialAuthActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_PATH, entryPath)
            }
    }
}
