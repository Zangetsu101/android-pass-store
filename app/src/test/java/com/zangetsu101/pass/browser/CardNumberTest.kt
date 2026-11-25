// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CardNumberTest {
    @Test
    fun `the status line shows the digit count and the result of the Luhn checksum`() {
        assertEquals("type or paste the number", cardNumberStatus(""))
        assertEquals("12 digits", cardNumberStatus("411111111111"))
        assertEquals("16 digits · checksum ok", cardNumberStatus("4111111111111111"))
        assertEquals("15 digits · checksum ok", cardNumberStatus("378282246310005"))
        assertEquals("13 digits · checksum ok", cardNumberStatus("4222222222222"))
        assertEquals("checksum fails · check for a typo", cardNumberStatus("4111111111111112"))
        assertEquals("checksum fails · check for a typo", cardNumberStatus("4111111111111"))
    }

    @Test
    fun `most numbers show in groups of 4 and Amex numbers show as 4-6-5`() {
        assertEquals("4111 1111 1111 1111", groupCardNumber("4111111111111111"))
        assertEquals("4111 11", groupCardNumber("411111"))
        assertEquals("4111 1111 1111 1111 111", groupCardNumber("4111111111111111111"))
        assertEquals("3782 822463 10005", groupCardNumber("378282246310005"))
        assertEquals("3714 4963", groupCardNumber("37144963"))
        assertEquals("3412", groupCardNumber("3412"))
        assertEquals("3", groupCardNumber("3"))
        assertEquals("3567 8901", groupCardNumber("35678901"))
    }

    @Test
    fun `the field keeps only digits and at most 19 of them`() {
        assertEquals("4111111111111111", cardNumberDigits("4111 1111-1111 1111"))
        assertEquals("1234567890123456789", cardNumberDigits("12345678901234567890"))
        assertEquals("", cardNumberDigits("abc ٣"))
    }
}
