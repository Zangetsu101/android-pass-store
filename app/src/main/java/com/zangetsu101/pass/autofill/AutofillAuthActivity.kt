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
        val cardNumberId = intent.getParcelableExtra<AutofillId>(EXTRA_CARD_NUMBER_ID)

        if (cardNumberId != null || entry.isCard) {
            @Suppress("DEPRECATION")
            authenticateCard(
                entry,
                cardNumberId = cardNumberId,
                cvvId = intent.getParcelableExtra(EXTRA_CVV_ID),
                expiryMonthId = intent.getParcelableExtra(EXTRA_EXPIRY_MONTH_ID),
                expiryYearId = intent.getParcelableExtra(EXTRA_EXPIRY_YEAR_ID),
                expiryDateId = intent.getParcelableExtra(EXTRA_EXPIRY_DATE_ID),
                cardholderId = intent.getParcelableExtra(EXTRA_CARDHOLDER_ID),
            )
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
        const val EXTRA_CARD_NUMBER_ID = "card_number_autofill_id"
        const val EXTRA_CVV_ID = "cvv_autofill_id"
        const val EXTRA_EXPIRY_MONTH_ID = "expiry_month_autofill_id"
        const val EXTRA_EXPIRY_YEAR_ID = "expiry_year_autofill_id"
        const val EXTRA_EXPIRY_DATE_ID = "expiry_date_autofill_id"
        const val EXTRA_CARDHOLDER_ID = "cardholder_autofill_id"
    }
}
