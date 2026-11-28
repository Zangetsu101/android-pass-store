// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.os.Bundle
import android.view.autofill.AutofillId
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.zangetsu101.pass.browser.EntryType
import com.zangetsu101.pass.browser.NewEntryScreen
import com.zangetsu101.pass.browser.NewEntryViewModel
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.ui.theme.PassTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AutofillCreateActivity : AutofillBaseActivity() {
    @Inject lateinit var passStore: PassStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cardFields = CardFormFields.from(intent)
        val isCard = cardFields.ids.isNotEmpty()
        val usernameId = IntentCompat.getParcelableExtra(intent, AutofillAuthActivity.EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordId = IntentCompat.getParcelableExtra(intent, AutofillAuthActivity.EXTRA_PASSWORD_ID, AutofillId::class.java)
        if (!isCard && usernameId == null && passwordId == null) {
            cancelAndFinish()
            return
        }

        val initialPath = intent.getStringExtra(EXTRA_INITIAL_PATH).orEmpty()
        val type = if (isCard) EntryType.Card else EntryType.Login

        fun fillEntry(filePath: String) {
            val entry = passStore.index.value.find { it.path == filePath && it.isCard == isCard }
            if (entry == null) {
                cancelAndFinish()
            } else if (isCard) {
                authenticateCard(entry, cardFields)
            } else {
                authenticate(entry, usernameId, passwordId)
            }
        }

        enableEdgeToEdge()
        setContent {
            PassTheme {
                val viewModel =
                    hiltViewModel<NewEntryViewModel, NewEntryViewModel.Factory>(
                        creationCallback = { factory -> factory.create(initialPath, type) },
                    )
                NewEntryScreen(
                    initialPath = initialPath,
                    viewModel = viewModel,
                    onBack = ::cancelAndFinish,
                    onSaved = { fillEntry(EntryPath(it).filePath) },
                )
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_PATH = "initial_path"
    }
}
