// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.content.Intent
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.session.SessionStartScreen
import com.zangetsu101.pass.session.SessionStartViewModel
import com.zangetsu101.pass.ui.theme.PassTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class AutofillBaseActivity : FragmentActivity() {
    @AutofillDecryption @Inject
    lateinit var decryption: Decryption

    @Inject lateinit var autofillHistory: AutofillHistory

    /**
     * Renders the unlock screen in-place. On success [onUnlocked] runs in the same activity,
     * letting the caller retry decryption and return the autofill result without a second
     * activity hop.
     */
    private fun showSessionGate(onUnlocked: () -> Unit) {
        enableEdgeToEdge()
        setContent {
            PassTheme {
                val vm: SessionStartViewModel = hiltViewModel()
                SessionStartScreen(viewModel = vm, onSuccess = onUnlocked)
            }
        }
    }

    protected fun authenticate(
        entry: PassEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
    ) {
        lifecycleScope.launch {
            try {
                val creds = decryption.decrypt(entry, this@AutofillBaseActivity)

                val label = "${entry.username}${entry.domain?.let { " ($it)" } ?: ""}"
                val presentation =
                    RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, label)
                    }

                // setValue(AutofillId, AutofillValue, RemoteViews) deprecated in API 33; replacement requires minSdk 33
                @Suppress("DEPRECATION")
                val dataset =
                    Dataset
                        .Builder()
                        .apply {
                            usernameId?.let { setValue(it, AutofillValue.forText(creds.username), presentation) }
                            passwordId?.let { setValue(it, AutofillValue.forText(String(creds.password)), presentation) }
                        }.build()

                recordFill(entry)
                val replyIntent =
                    Intent().apply {
                        putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                    }
                setResult(RESULT_OK, replyIntent)
                finish()
            } catch (e: SessionError.NoActiveSession) {
                showSessionGate { authenticate(entry, usernameId, passwordId) }
            } catch (e: DecryptionError) {
                cancelAndFinish()
            } catch (e: Exception) {
                cancelAndFinish()
            }
        }
    }

    protected fun authenticateCard(
        entry: PassEntry,
        fields: CardFormFields,
    ) {
        lifecycleScope.launch {
            try {
                val creds = decryption.decrypt(entry, this@AutofillBaseActivity)

                val cardNumber = String(creds.password)
                val notes = parseNotes(creds.notes)
                val cvv = notes["cvv"]
                val expiryRaw = notes["expiry"]
                val expiry = expiryRaw?.let(::parseCardExpiry)
                val expiryMonth = expiry?.month
                val expiryYear = expiry?.yearText(fields.expiryYearDigits)
                // Without a known field length, keep the date as the user wrote it.
                val expiryDate = fields.expiryDateDigits?.let { expiry?.dateText(it) } ?: expiryRaw
                val cardholder = notes["cardholder"]

                val label = "${entry.username}${entry.domain?.let { " ($it)" } ?: ""}"
                val presentation =
                    RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, label)
                    }

                @Suppress("DEPRECATION")
                val dataset =
                    Dataset
                        .Builder()
                        .apply {
                            fields.numberId?.let { setValue(it, AutofillValue.forText(cardNumber), presentation) }
                            fields.securityCodeId?.let { cvv?.let { v -> setValue(it, AutofillValue.forText(v), presentation) } }
                            fields.expiryMonthId?.let { expiryMonth?.let { v -> setValue(it, AutofillValue.forText(v), presentation) } }
                            fields.expiryYearId?.let { expiryYear?.let { v -> setValue(it, AutofillValue.forText(v), presentation) } }
                            fields.expiryDateId?.let { expiryDate?.let { v -> setValue(it, AutofillValue.forText(v), presentation) } }
                            fields.cardholderId?.let { cardholder?.let { v -> setValue(it, AutofillValue.forText(v), presentation) } }
                        }.build()

                recordFill(entry)
                val replyIntent =
                    Intent().apply {
                        putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                    }
                setResult(RESULT_OK, replyIntent)
                finish()
            } catch (e: SessionError.NoActiveSession) {
                showSessionGate { authenticateCard(entry, fields) }
            } catch (e: DecryptionError) {
                cancelAndFinish()
            } catch (e: Exception) {
                cancelAndFinish()
            }
        }
    }

    private suspend fun recordFill(entry: PassEntry) {
        val fillTarget = intent.getStringExtra(EXTRA_FILL_TARGET) ?: return
        autofillHistory.record(fillTarget, entry)
    }

    protected fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun parseNotes(notes: String): Map<String, String> =
        notes
            .lines()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx < 0) null else line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
            }.toMap()

    companion object {
        const val EXTRA_FILL_TARGET = "fill_target"
    }
}
