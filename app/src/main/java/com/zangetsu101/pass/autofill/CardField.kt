// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.view.View

enum class CardField { Number, SecurityCode, ExpiryMonth, ExpiryYear, ExpiryDate, Cardholder }

/**
 * Finds which card field a view node is, from the two ways that apps label fields.
 *
 * - Native apps set `autofillHints` to the `View.AUTOFILL_HINT_*` constants.
 * - Chrome sets `autofillHints` to the raw HTML `autocomplete` tokens (for example `cc-number`).
 *   It also puts its own field types (for example `CREDIT_CARD_NUMBER`) in HtmlInfo attributes.
 *   Chrome predicts these types when the page has no `autocomplete` attribute.
 */
fun classifyCardField(
    autofillHints: Array<String>?,
    htmlAttributes: List<Pair<String, String>>?,
): CardField? {
    autofillHints?.firstNotNullOfOrNull { HINTS[it.lowercase()] }?.let { return it }
    val attrs = htmlAttributes ?: return null
    return CHROME_TYPE_ATTRIBUTES.firstNotNullOfOrNull { name ->
        attrs.firstOrNull { it.first == name }?.second?.let { CHROME_TYPES[it] }
    }
}

private val HINTS: Map<String, CardField> =
    mapOf(
        View.AUTOFILL_HINT_CREDIT_CARD_NUMBER to CardField.Number,
        View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE to CardField.SecurityCode,
        View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH to CardField.ExpiryMonth,
        View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR to CardField.ExpiryYear,
        View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE to CardField.ExpiryDate,
        View.AUTOFILL_HINT_NAME to CardField.Cardholder,
        "cc-number" to CardField.Number,
        "cc-csc" to CardField.SecurityCode,
        "cc-exp-month" to CardField.ExpiryMonth,
        "cc-exp-year" to CardField.ExpiryYear,
        "cc-exp" to CardField.ExpiryDate,
        "cc-name" to CardField.Cardholder,
    ).mapKeys { it.key.lowercase() }

// Most reliable first: the computed type merges the server and heuristic predictions.
private val CHROME_TYPE_ATTRIBUTES = listOf("computed-autofill-hints", "crowdsourcing-autofill-hints", "ua-autofill-hints")

private val CHROME_TYPES: Map<String, CardField> =
    mapOf(
        "CREDIT_CARD_NUMBER" to CardField.Number,
        "CREDIT_CARD_VERIFICATION_CODE" to CardField.SecurityCode,
        "CREDIT_CARD_EXP_MONTH" to CardField.ExpiryMonth,
        "CREDIT_CARD_EXP_2_DIGIT_YEAR" to CardField.ExpiryYear,
        "CREDIT_CARD_EXP_4_DIGIT_YEAR" to CardField.ExpiryYear,
        "CREDIT_CARD_EXP_DATE_2_DIGIT_YEAR" to CardField.ExpiryDate,
        "CREDIT_CARD_EXP_DATE_4_DIGIT_YEAR" to CardField.ExpiryDate,
        "CREDIT_CARD_NAME_FULL" to CardField.Cardholder,
    )
