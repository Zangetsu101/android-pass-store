// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFieldTest {
    @Test
    fun `native app hints map to card fields`() {
        assertEquals(CardField.Number, classifyCardField(arrayOf("creditCardNumber"), null))
        assertEquals(CardField.SecurityCode, classifyCardField(arrayOf("creditCardSecurityCode"), null))
        assertEquals(CardField.ExpiryMonth, classifyCardField(arrayOf("creditCardExpirationMonth"), null))
        assertEquals(CardField.ExpiryYear, classifyCardField(arrayOf("creditCardExpirationYear"), null))
        assertEquals(CardField.ExpiryDate, classifyCardField(arrayOf("creditCardExpirationDate"), null))
        assertEquals(CardField.Cardholder, classifyCardField(arrayOf("name"), null))
    }

    @Test
    fun `html autocomplete tokens from a browser map to card fields`() {
        assertEquals(CardField.Number, classifyCardField(arrayOf("cc-number"), null))
        assertEquals(CardField.SecurityCode, classifyCardField(arrayOf("cc-csc"), null))
        assertEquals(CardField.ExpiryMonth, classifyCardField(arrayOf("cc-exp-month"), null))
        assertEquals(CardField.ExpiryYear, classifyCardField(arrayOf("cc-exp-year"), null))
        assertEquals(CardField.ExpiryDate, classifyCardField(arrayOf("cc-exp"), null))
        assertEquals(CardField.Cardholder, classifyCardField(arrayOf("cc-name"), null))
    }

    @Test
    fun `autocomplete tokens with section and mode prefixes still match`() {
        assertEquals(CardField.Number, classifyCardField(arrayOf("section-pay", "billing", "cc-number"), null))
        assertEquals(CardField.Number, classifyCardField(arrayOf("CC-Number"), null))
    }

    @Test
    fun `chrome field types in html attributes map to card fields`() {
        fun attr(value: String) = listOf("type" to "text", "computed-autofill-hints" to value)

        assertEquals(CardField.Number, classifyCardField(null, attr("CREDIT_CARD_NUMBER")))
        assertEquals(CardField.SecurityCode, classifyCardField(null, attr("CREDIT_CARD_VERIFICATION_CODE")))
        assertEquals(CardField.ExpiryMonth, classifyCardField(null, attr("CREDIT_CARD_EXP_MONTH")))
        assertEquals(CardField.ExpiryYear, classifyCardField(null, attr("CREDIT_CARD_EXP_2_DIGIT_YEAR")))
        assertEquals(CardField.ExpiryYear, classifyCardField(null, attr("CREDIT_CARD_EXP_4_DIGIT_YEAR")))
        assertEquals(CardField.ExpiryDate, classifyCardField(null, attr("CREDIT_CARD_EXP_DATE_2_DIGIT_YEAR")))
        assertEquals(CardField.ExpiryDate, classifyCardField(null, attr("CREDIT_CARD_EXP_DATE_4_DIGIT_YEAR")))
        assertEquals(CardField.Cardholder, classifyCardField(null, attr("CREDIT_CARD_NAME_FULL")))
    }

    @Test
    fun `server and heuristic chrome field types are also read`() {
        assertEquals(
            CardField.Number,
            classifyCardField(null, listOf("crowdsourcing-autofill-hints" to "CREDIT_CARD_NUMBER")),
        )
        assertEquals(
            CardField.Number,
            classifyCardField(null, listOf("ua-autofill-hints" to "CREDIT_CARD_NUMBER")),
        )
    }

    @Test
    fun `computed chrome field type wins over the heuristic type`() {
        val attrs =
            listOf(
                "ua-autofill-hints" to "CREDIT_CARD_NAME_FULL",
                "computed-autofill-hints" to "CREDIT_CARD_NUMBER",
            )
        assertEquals(CardField.Number, classifyCardField(null, attrs))
    }

    @Test
    fun `non-card fields are not classified`() {
        assertNull(classifyCardField(null, null))
        assertNull(classifyCardField(arrayOf("username"), null))
        assertNull(classifyCardField(arrayOf("email"), listOf("computed-autofill-hints" to "EMAIL_ADDRESS")))
        assertNull(classifyCardField(null, listOf("computed-autofill-hints" to "UNKNOWN_TYPE")))
        assertNull(classifyCardField(null, listOf("name" to "cc-number")))
    }
}
