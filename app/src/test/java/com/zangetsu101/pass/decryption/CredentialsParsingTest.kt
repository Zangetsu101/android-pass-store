// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import com.zangetsu101.pass.otp.Otp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CredentialsParsingTest {
    private val uri = "otpauth://totp/test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `password plus OTP strips every URI and first wins`() {
        val creds = parseCredentials("password\r\n$uri\r\nurl: example.com\r\notpauth://bad\r\n", "alice")
        assertEquals("password", String(creds.password))
        assertEquals("url: example.com", creds.notes)
        assertTrue(creds.otp is Otp.Totp)
        assertEquals("alice", creds.username)
    }

    @Test
    fun `OTP only has no password or notes`() {
        val creds = parseCredentials("$uri\n", "alice")
        assertTrue(creds.password.isEmpty())
        assertEquals("", creds.notes)
        assertTrue(creds.otp is Otp.Totp)
    }

    @Test
    fun `invalid first URI wins and no note becomes password`() {
        val creds = parseCredentials("otpauth://bad\nnotes: retained\n$uri", "alice")
        assertTrue(creds.password.isEmpty())
        assertEquals("notes: retained", creds.notes)
        assertTrue(creds.otp is Otp.Invalid)
    }

    @Test
    fun `ordinary entries retain original splitting`() {
        val creds = parseCredentials("password\nusername: bob\n", "alice")
        assertEquals("password", String(creds.password))
        assertEquals("username: bob", creds.notes)
        assertNull(creds.otp)
    }
}
