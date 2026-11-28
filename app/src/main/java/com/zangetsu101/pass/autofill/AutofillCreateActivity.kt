// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.os.Bundle
import android.view.autofill.AutofillId
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.zangetsu101.pass.browser.EntryType
import com.zangetsu101.pass.browser.NewEntryOrigin
import com.zangetsu101.pass.browser.NewEntryScreen
import com.zangetsu101.pass.browser.NewEntryViewModel
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.ui.theme.PassTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class AutofillCreateActivity : AutofillBaseActivity() {
    @Inject lateinit var passStore: PassStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cardFields = CardFormFields.from(intent)
        val type = if (cardFields.ids.isEmpty()) EntryType.Login else EntryType.Card
        val usernameId = IntentCompat.getParcelableExtra(intent, AutofillAuthActivity.EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordId = IntentCompat.getParcelableExtra(intent, AutofillAuthActivity.EXTRA_PASSWORD_ID, AutofillId::class.java)
        if (type == EntryType.Login && usernameId == null && passwordId == null) {
            cancelAndFinish()
            return
        }

        val initialPath = intent.getStringExtra(EXTRA_INITIAL_PATH).orEmpty()
        val origin =
            NewEntryOrigin.Autofill(
                type,
                requiresSecurityCode = cardFields.numberId == null && cardFields.cvvId != null,
            )

        fun fillEntry(filePath: String) {
            lifecycleScope.launch {
                val entry =
                    try {
                        passStore.readEntry(filePath)
                    } catch (_: IOException) {
                        null
                    }
                if (entry == null) {
                    Toast.makeText(this@AutofillCreateActivity, "entry saved, but couldn't fill the form", Toast.LENGTH_SHORT).show()
                    cancelAndFinish()
                } else if (type == EntryType.Card) {
                    authenticateCard(entry, cardFields)
                } else {
                    authenticate(entry, usernameId, passwordId)
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            PassTheme {
                val viewModel =
                    hiltViewModel<NewEntryViewModel, NewEntryViewModel.Factory>(
                        creationCallback = { factory -> factory.create(initialPath, origin) },
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
