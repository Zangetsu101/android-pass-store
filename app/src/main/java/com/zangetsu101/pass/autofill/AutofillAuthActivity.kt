package com.zangetsu101.pass.autofill

import android.os.Bundle
import android.view.autofill.AutofillId
import com.zangetsu101.pass.passstore.PassStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AutofillAuthActivity : AutofillBaseActivity() {
    @Inject lateinit var passStore: PassStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra(EXTRA_ENTRY_PATH)
        if (path == null) {
            cancelAndFinish()
            return
        }

        val entry = passStore.index.value.find { it.path == path }
        if (entry == null) {
            cancelAndFinish()
            return
        }

        @Suppress("DEPRECATION")
        val usernameId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_ID)

        @Suppress("DEPRECATION")
        val passwordId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_ID)

        if (usernameId == null && passwordId == null) {
            cancelAndFinish()
            return
        }

        authenticate(entry, usernameId, passwordId)
    }

    companion object {
        const val EXTRA_ENTRY_PATH = "entry_path"
        const val EXTRA_USERNAME_ID = "username_autofill_id"
        const val EXTRA_PASSWORD_ID = "password_autofill_id"
    }
}
