// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.content.Intent
import android.view.autofill.AutofillId
import androidx.core.content.IntentCompat

/**
 * The card fields that the service found in a form.
 * The digit counts give the length of the year in the expiry fields, when the form states it.
 */
data class CardFormFields(
    val numberId: AutofillId?,
    val cvvId: AutofillId?,
    val expiryMonthId: AutofillId?,
    val expiryYearId: AutofillId?,
    val expiryDateId: AutofillId?,
    val cardholderId: AutofillId?,
    val expiryYearDigits: Int?,
    val expiryDateDigits: Int?,
) {
    val ids: List<AutofillId>
        get() = listOfNotNull(numberId, cvvId, expiryMonthId, expiryYearId, expiryDateId, cardholderId)

    fun putInto(intent: Intent) {
        numberId?.let { intent.putExtra(EXTRA_NUMBER_ID, it) }
        cvvId?.let { intent.putExtra(EXTRA_CVV_ID, it) }
        expiryMonthId?.let { intent.putExtra(EXTRA_EXPIRY_MONTH_ID, it) }
        expiryYearId?.let { intent.putExtra(EXTRA_EXPIRY_YEAR_ID, it) }
        expiryDateId?.let { intent.putExtra(EXTRA_EXPIRY_DATE_ID, it) }
        cardholderId?.let { intent.putExtra(EXTRA_CARDHOLDER_ID, it) }
        expiryYearDigits?.let { intent.putExtra(EXTRA_EXPIRY_YEAR_DIGITS, it) }
        expiryDateDigits?.let { intent.putExtra(EXTRA_EXPIRY_DATE_DIGITS, it) }
    }

    companion object {
        private const val EXTRA_NUMBER_ID = "card_number_autofill_id"
        private const val EXTRA_CVV_ID = "cvv_autofill_id"
        private const val EXTRA_EXPIRY_MONTH_ID = "expiry_month_autofill_id"
        private const val EXTRA_EXPIRY_YEAR_ID = "expiry_year_autofill_id"
        private const val EXTRA_EXPIRY_DATE_ID = "expiry_date_autofill_id"
        private const val EXTRA_CARDHOLDER_ID = "cardholder_autofill_id"
        private const val EXTRA_EXPIRY_YEAR_DIGITS = "expiry_year_digits"
        private const val EXTRA_EXPIRY_DATE_DIGITS = "expiry_date_digits"

        fun from(intent: Intent): CardFormFields =
            CardFormFields(
                numberId = intent.autofillId(EXTRA_NUMBER_ID),
                cvvId = intent.autofillId(EXTRA_CVV_ID),
                expiryMonthId = intent.autofillId(EXTRA_EXPIRY_MONTH_ID),
                expiryYearId = intent.autofillId(EXTRA_EXPIRY_YEAR_ID),
                expiryDateId = intent.autofillId(EXTRA_EXPIRY_DATE_ID),
                cardholderId = intent.autofillId(EXTRA_CARDHOLDER_ID),
                expiryYearDigits = intent.intOrNull(EXTRA_EXPIRY_YEAR_DIGITS),
                expiryDateDigits = intent.intOrNull(EXTRA_EXPIRY_DATE_DIGITS),
            )

        private fun Intent.autofillId(name: String): AutofillId? = IntentCompat.getParcelableExtra(this, name, AutofillId::class.java)

        private fun Intent.intOrNull(name: String): Int? = if (hasExtra(name)) getIntExtra(name, 0) else null
    }
}
