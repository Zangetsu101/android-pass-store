// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.decryption.NoteField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SecretNoteFieldsTest {
    @Test
    fun `secret keys are pin cvv secret answer recovery and each key with password`() {
        val secret = listOf("pin", "cvv", "secret", "answer", "recovery", "password", "wifi-password", "PasswordHint", "PIN")
        val plain = listOf("username", "url", "email", "pins", "pass", "recovery-email", "cardholder")

        assertEquals(secret, secret.filter { NoteField(it, "x").secret })
        assertEquals(emptyList<String>(), plain.filter { NoteField(it, "x").secret })
    }
}
