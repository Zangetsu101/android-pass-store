// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.otp

import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed interface Otp {
    class Totp internal constructor(
        private val secret: ByteArray,
        val digits: Int,
        val period: Long,
        val algorithm: String,
    ) : Otp {
        fun code(epochSeconds: Long): String {
            val mac = Mac.getInstance("Hmac$algorithm")
            mac.init(SecretKeySpec(secret, "Hmac$algorithm"))
            val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(epochSeconds / period).array())
            val offset = hash.last().toInt() and 15
            val binary = ByteBuffer.wrap(hash, offset, 4).int and Int.MAX_VALUE
            val modulus =
                when (digits) {
                    6 -> 1_000_000
                    7 -> 10_000_000
                    else -> 100_000_000
                }
            return (binary % modulus).toString().padStart(digits, '0')
        }

        fun secondsRemaining(epochSeconds: Long): Long = period - epochSeconds % period

        override fun toString(): String = "Totp(digits=$digits, period=$period, algorithm=$algorithm)"
    }

    data class Invalid(
        val reason: String,
    ) : Otp
}

/** Errors are fixed messages: parser exceptions can contain the secret URI. */
fun parseOtp(line: String): Otp =
    try {
        val uri = URI(line)
        require(uri.scheme == "otpauth")
        if (uri.host == "hotp") {
            Otp.Invalid("hotp is not supported")
        } else {
            require(uri.host == "totp" && !uri.rawPath.isNullOrEmpty() && uri.rawPath != "/")
            require(uri.fragment == null && uri.userInfo == null && uri.port == -1)
            val parameters = mutableMapOf<String, String>()
            for (part in (uri.rawQuery ?: "").split('&')) {
                val pair = part.split('=', limit = 2)
                require(pair.size == 2)
                val key = URLDecoder.decode(pair[0], "UTF-8")
                require(key !in parameters)
                parameters[key] = URLDecoder.decode(pair[1], "UTF-8")
            }
            val digits = parameters["digits"]?.toInt() ?: 6
            require(digits in 6..8)
            val period = parameters["period"]?.toLong() ?: 30L
            require(period > 0)
            val algorithm = (parameters["algorithm"] ?: "SHA1").uppercase(Locale.ROOT)
            require(algorithm in setOf("SHA1", "SHA256", "SHA512"))
            Otp.Totp(decodeBase32(parameters["secret"] ?: error("missing secret")), digits, period, algorithm)
        }
    } catch (_: Exception) {
        Otp.Invalid("invalid otpauth URI")
    }

private fun decodeBase32(encoded: String): ByteArray {
    require(encoded.all { it in 'A'..'Z' || it in 'a'..'z' || it in '2'..'7' || it == '=' })
    val value = encoded.uppercase(Locale.ROOT).trimEnd('=')
    require(value.isNotEmpty() && value.length % 8 in setOf(0, 2, 4, 5, 7))
    require(encoded.drop(value.length).all { it == '=' })
    require(encoded.length == value.length || encoded.length == ((value.length + 7) / 8) * 8)
    var buffer = 0
    var bits = 0
    val result = ArrayList<Byte>()
    for (char in value) {
        val digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(char)
        require(digit >= 0)
        buffer = (buffer shl 5) or digit
        bits += 5
        if (bits >= 8) {
            bits -= 8
            result.add((buffer shr bits).toByte())
        }
    }
    require(buffer and ((1 shl bits) - 1) == 0)
    return result.toByteArray()
}
