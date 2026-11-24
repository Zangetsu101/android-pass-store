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
    fun `indented OTP lines are recognized removed and first wins`() {
        val creds = parseCredentials(" \t $uri\nnotes: retained\n\totpauth://bad", "alice")
        assertTrue(creds.password.isEmpty())
        assertEquals("notes: retained", creds.notes)
        assertTrue(creds.otp is Otp.Totp)
        val withPassword = parseCredentials("password\n \t $uri\n\totpauth://bad", "alice")
        assertEquals("password", String(withPassword.password))
        assertEquals("", withPassword.notes)
        assertTrue(withPassword.otp is Otp.Totp)
    }

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
    fun `the username comes from username then login then user then the file name`() {
        assertEquals("u1", parseCredentials("pw\nuser: u3\nlogin: u2\nusername: u1", "file").username)
        assertEquals("u2", parseCredentials("pw\nuser: u3\nlogin: u2", "file").username)
        assertEquals("u3", parseCredentials("pw\nemail: e@x.com\nuser: u3", "file").username)
        assertEquals("file", parseCredentials("pw\nemail: e@x.com\nnote", "file").username)
    }

    @Test
    fun `the first username line wins and the key ignores case`() {
        assertEquals("first", parseCredentials("pw\nUsername: first\nusername: second", "file").username)
    }

    @Test
    fun `a username line that is not a key value line does not count`() {
        assertEquals("file", parseCredentials("pw\nusername :bob\nusername:", "file").username)
    }

    @Test
    fun `ordinary entries retain original splitting`() {
        val creds = parseCredentials("password\nusername: bob\n", "alice")
        assertEquals("password", String(creds.password))
        assertEquals("username: bob", creds.notes)
        assertNull(creds.otp)
    }
}
