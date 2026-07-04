// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.os.Bundle
import android.view.autofill.AutofillId
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.zangetsu101.pass.browser.EntryBrowserScreen
import com.zangetsu101.pass.browser.EntryBrowserViewModel
import com.zangetsu101.pass.ui.theme.PassTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AutofillSearchActivity : AutofillBaseActivity() {
    private val viewModel: EntryBrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        val usernameId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_ID)

        @Suppress("DEPRECATION")
        val passwordId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_ID)

        if (usernameId == null && passwordId == null) {
            cancelAndFinish()
            return
        }

        val initialQuery = intent.getStringExtra(EXTRA_INITIAL_QUERY) ?: ""
        viewModel.setSearchQuery(initialQuery)

        setContent {
            PassTheme {
                EntryBrowserScreen(
                    viewModel = viewModel,
                    onNavigateToEntryDetail = { entry -> authenticate(entry, usernameId, passwordId) },
                )
            }
        }
    }

    companion object {
        const val EXTRA_USERNAME_ID = "username_autofill_id"
        const val EXTRA_PASSWORD_ID = "password_autofill_id"
        const val EXTRA_INITIAL_QUERY = "initial_query"
    }
}
