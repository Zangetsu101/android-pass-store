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

class CardYearDigitsTest {
    private fun chromeType(value: String) = listOf("computed-autofill-hints" to value)

    @Test
    fun `chrome field types give the year length`() {
        assertEquals(2, cardYearDigits(chromeType("CREDIT_CARD_EXP_2_DIGIT_YEAR")))
        assertEquals(4, cardYearDigits(chromeType("CREDIT_CARD_EXP_4_DIGIT_YEAR")))
        assertEquals(2, cardYearDigits(chromeType("CREDIT_CARD_EXP_DATE_2_DIGIT_YEAR")))
        assertEquals(4, cardYearDigits(chromeType("CREDIT_CARD_EXP_DATE_4_DIGIT_YEAR")))
    }

    @Test
    fun `maxlength gives the year length when chrome has no length in the type`() {
        assertEquals(2, cardYearDigits(chromeType("CREDIT_CARD_EXP_MONTH") + ("maxlength" to "2")))
        assertEquals(4, cardYearDigits(listOf("maxlength" to "4")))
        assertEquals(2, cardYearDigits(listOf("maxlength" to "5")))
        assertEquals(4, cardYearDigits(listOf("maxlength" to "7")))
    }

    @Test
    fun `chrome field type wins over maxlength`() {
        assertEquals(2, cardYearDigits(chromeType("CREDIT_CARD_EXP_2_DIGIT_YEAR") + ("maxlength" to "4")))
    }

    @Test
    fun `unknown year length gives null`() {
        assertNull(cardYearDigits(null))
        assertNull(cardYearDigits(emptyList()))
        assertNull(cardYearDigits(listOf("maxlength" to "20")))
        assertNull(cardYearDigits(listOf("maxlength" to "x")))
    }
}

class CardExpiryTest {
    @Test
    fun `parses month and year in short and long forms`() {
        assertEquals(CardExpiry("12", "2027"), parseCardExpiry("12/27"))
        assertEquals(CardExpiry("12", "2027"), parseCardExpiry("12/2027"))
        assertEquals(CardExpiry("03", "2027"), parseCardExpiry("3/27"))
        assertEquals(CardExpiry("03", "2027"), parseCardExpiry(" 03 - 2027 "))
    }

    @Test
    fun `rejects values that are not an expiry`() {
        assertNull(parseCardExpiry("1227"))
        assertNull(parseCardExpiry("13/27"))
        assertNull(parseCardExpiry("00/27"))
        assertNull(parseCardExpiry("12/123"))
        assertNull(parseCardExpiry("ab/cd"))
    }

    @Test
    fun `formats the year for the field length`() {
        val expiry = CardExpiry("03", "2027")
        assertEquals("27", expiry.yearText(2))
        assertEquals("2027", expiry.yearText(4))
        assertEquals("2027", expiry.yearText(null))
    }

    @Test
    fun `formats the date for the field length`() {
        val expiry = CardExpiry("03", "2027")
        assertEquals("03/27", expiry.dateText(2))
        assertEquals("03/2027", expiry.dateText(4))
    }
}
