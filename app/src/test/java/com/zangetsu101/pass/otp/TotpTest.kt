// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.otp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TotpTest {
    @Test
    fun `accept spaces in label and issuer`() {
        val otp = parseOtp("otpauth://totp/Amazon Web Services:root@acme?secret=JBSWY3DPEHPK3PXP&issuer=Amazon Web Services") as Otp.Totp
        assertEquals("282760", otp.code(0))
    }

    @Test
    fun `accept spaces and hyphens in secret`() {
        for (secret in listOf("JBSW Y3DP EHPK 3PXP", "jbsw-y3dp-ehpk-3pxp")) {
            val otp = parseOtp("otpauth://totp/Acme:alice?secret=$secret") as Otp.Totp
            assertEquals("282760", otp.code(0))
        }
    }

    @Test
    fun `accept pipe in issuer parameter`() {
        val otp = parseOtp("otpauth://totp/Acme:alice?secret=JBSWY3DPEHPK3PXP&issuer=Acme|Co") as Otp.Totp
        assertEquals("282760", otp.code(0))
    }

    @Test
    fun `accept leading spaces and tabs`() {
        val otp = parseOtp(" \t otpauth://totp/Acme:alice?secret=JBSWY3DPEHPK3PXP") as Otp.Totp
        assertEquals("282760", otp.code(0))
    }

    @Test
    fun `RFC 6238 appendix B vectors for all algorithms`() {
        val times = listOf(59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L)
        val vectors =
            mapOf(
                "SHA1" to listOf("94287082", "07081804", "14050471", "89005924", "69279037", "65353130"),
                "SHA256" to listOf("46119246", "68084774", "67062674", "91819424", "90698825", "77737706"),
                "SHA512" to listOf("90693936", "25091201", "99943326", "93441116", "38618901", "47863826"),
            )
        for ((algorithm, expected) in vectors) {
            val length =
                when (algorithm) {
                    "SHA1" -> 20
                    "SHA256" -> 32
                    else -> 64
                }
            val secret = "1234567890".repeat(7).take(length).toByteArray()
            val otp = Otp.Totp(secret, 8, 30, algorithm)
            times.zip(expected).forEach { (time, code) -> assertEquals(code, otp.code(time), "$algorithm at $time") }
        }
    }

    @Test
    fun `parse defaults percent encoding lowercase and padding`() {
        for (secret in listOf("gezdgnbvgy3tqojqgezdgnbvgy3tqojq", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")) {
            val otp = parseOtp("otpauth://totp/test?secret=$secret") as Otp.Totp
            assertEquals("287082", otp.code(59))
            assertEquals(30L, otp.period)
            assertEquals("SHA1", otp.algorithm)
        }
        assertTrue(parseOtp("otpauth://totp/a%20b?secret=MY%3D%3D%3D%3D%3D%3D") is Otp.Totp)
    }

    @Test
    fun `URI selects SHA256 SHA512 and arbitrary positive periods`() {
        for (algorithm in listOf("SHA256", "SHA512")) {
            val otp =
                parseOtp(
                    "otpauth://totp/test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&algorithm=$algorithm&digits=8&period=1",
                ) as Otp.Totp
            assertEquals(algorithm, otp.algorithm)
            assertEquals(8, otp.code(59).length)
            assertEquals(1L, otp.secondsRemaining(59))
        }
        val otp = parseOtp("otpauth://totp/test?secret=MY&period=9223372036854775807") as Otp.Totp
        assertEquals(Long.MAX_VALUE - 1, otp.secondsRemaining(1))
    }

    @Test
    fun `custom period digits and rollover`() {
        val otp = parseOtp("otpauth://totp/test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&digits=7&period=17&algorithm=SHA1") as Otp.Totp
        assertEquals("755224", otp.code(0).takeLast(6))
        assertEquals(17L, otp.secondsRemaining(0))
        assertEquals(1L, otp.secondsRemaining(16))
        assertEquals(17L, otp.secondsRemaining(17))
        assertNotEquals(otp.code(16), otp.code(17))
        assertEquals(7, otp.code(17).length)
    }

    @Test
    fun `reject invalid parameters without leaking input`() {
        val base = "otpauth://totp/test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        for (line in listOf(
            "otpauth://",
            "otpauth://totp/test",
            "$base&digits=5",
            "$base&digits=9",
            "$base&period=0",
            "$base&period=-1",
            "$base&period=abc",
            "$base&algorithm=MD5",
            "$base&digits=6&digits=8",
            "otpauth://totp/test?secret=INVALID0",
            "otpauth://totp/test?secret=A",
            "otpauth://totp/test?secret=MZ",
            "$base%",
        )) {
            assertEquals(Otp.Invalid("invalid otpauth URI"), parseOtp(line), line)
        }
        assertEquals(Otp.Invalid("hotp is not supported"), parseOtp("otpauth://hotp/test?secret=MY&counter=0"))
        assertFalse(parseOtp(base).toString().contains("GEZD"))
    }
}
